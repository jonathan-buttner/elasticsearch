/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.core.Strings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.common.AdjustableCapacityBlockingQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.elasticsearch.core.Strings.format;
import static org.elasticsearch.xpack.inference.InferencePlugin.UTILITY_THREAD_POOL_NAME;

class RequestExecutorService3 {

    private static final AdjustableCapacityBlockingQueue.QueueCreator<RejectableTask> QUEUE_CREATOR =
        new AdjustableCapacityBlockingQueue.QueueCreator<>() {
            @Override
            public BlockingQueue<RejectableTask> create(int capacity) {
                BlockingQueue<RejectableTask> queue;
                if (capacity <= 0) {
                    queue = create();
                } else {
                    queue = new LinkedBlockingQueue<>(capacity);
                }

                return queue;
            }

            @Override
            public BlockingQueue<RejectableTask> create() {
                return new LinkedBlockingQueue<>();
            }
        };

    private final ConcurrentMap<Object, RateLimitingEndpointHandler> inferenceEndpoints = new ConcurrentHashMap<>();
    private final ThreadPool threadPool;
    private final SingleRequestManager requestManager;
    private final RequestExecutorServiceSettings settings;

    RequestExecutorService3(ThreadPool threadPool, RequestExecutorServiceSettings settings, SingleRequestManager requestManager) {
        this.threadPool = Objects.requireNonNull(threadPool);
        this.requestManager = Objects.requireNonNull(requestManager);
        this.settings = Objects.requireNonNull(settings);
    }

    // TODO add rate limiter as a member of the InferenceEndpoint

    /**
     * Provides a mechanism for ensuring that only a single thread is processing tasks from the queue at a time.
     * As tasks are enqueued for execution, if a thread executing (or scheduled to execute a task in the future),
     * a new one will not be started.
     */
    private static class RateLimitingEndpointHandler {

        private static final Logger logger = LogManager.getLogger(RateLimitingEndpointHandler.class);

        private final AdjustableCapacityBlockingQueue<RejectableTask> queue;
        private final Semaphore threadRunning = new Semaphore(1);
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final Phaser phaser = new Phaser();
        private final ThreadPool threadPool;
        private final SingleRequestManager requestManager;
        private final String id;

        RateLimitingEndpointHandler(
            String id,
            ThreadPool threadPool,
            AdjustableCapacityBlockingQueue.QueueCreator<RejectableTask> createQueue,
            RequestExecutorServiceSettings settings,
            SingleRequestManager requestManager
        ) {
            this.id = Objects.requireNonNull(id);
            this.queue = new AdjustableCapacityBlockingQueue<>(createQueue, settings.getQueueCapacity());
            this.threadPool = Objects.requireNonNull(threadPool);
            this.requestManager = Objects.requireNonNull(requestManager);

            settings.registerQueueCapacityCallback(this::onCapacityChange);
        }

        private void onCapacityChange(int capacity) {
            logger.debug(() -> Strings.format("Setting queue capacity to [%s]", capacity));

            try {
                queue.setCapacity(capacity);
            } catch (Exception e) {
                logger.warn(format("Failed to set the capacity of the task queue to [%s] for [%s]", capacity, id), e);
            }
        }

        public int queueSize() {
            return queue.size();
        }

        public boolean isTerminated() {
            return phaser.isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                phaser.register();
                while (phaser.isTerminated() == false) {
                    phaser.awaitAdvanceInterruptibly(phaser.arriveAndDeregister(), timeout, unit);
                }
            } catch (TimeoutException e) {
                return false;
            }

            return true;
        }

        public void shutdown() {
            shutdown.set(true);
        }

        public boolean isShutdown() {
            return shutdown.get();
        }

        public void enqueue(RequestTask task) {
            if (isShutdown()) {
                EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                    format(
                        "Failed to enqueue task because the executor service [%s] has already shutdown",
                        task.getRequestCreator().getModel().getInferenceEntityId()
                    ),
                    true
                );

                task.onRejection(rejected);
                return;
            }

            // If we aren't shutting down then lets add the request to the queue, having the read lock ensures that the task will be
            // picked up by a thread that is already executing tasks or one that we'll start later
            // If we don't have the read lock, it'd be possible that we add an item to the queue but a thread was in the process of
            // stopping and missed the new task and a new thread wouldn't get started
            var addedToQueue = queue.offer(task);

            if (addedToQueue == false) {
                EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                    format(
                        "Failed to execute task because the executor service [%s] queue is full",
                        task.getRequestCreator().getModel().getInferenceEntityId()
                    ),
                    false
                );

                task.onRejection(rejected);
            } else if (isShutdown()) {
                notifyRequestsOfShutdown();
            } else {
                checkForTask();
            }
        }

        private TimeValue getScheduleTime() {
            // TODO return result from rate limiter
            return TimeValue.ZERO;
        }

        private void onFinishedExecutingTask() {
            threadRunning.release();
            try {
                if (isShutdown()) {
                    notifyRequestsOfShutdown();
                } else {
                    checkForTask();
                }
            } finally {
                phaser.arriveAndDeregister();
            }
        }

        private void checkForTask() {
            // There is a possibility that a request could come in, acquire the semaphore, and complete the task between when
            // we peek and when we attempt to acquire the semaphore here. We'll handle that by peeking while attempt to dequeue
            if (queue.peek() != null && threadRunning.tryAcquire()) {
                // Now that we have the exclusive lock, check again to see if we have work to do
                if (queue.peek() != null) {
                    try {
                        phaser.register();
                        threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(this::handleSingleRequest);
                    } catch (Exception e) {
                        logger.warn(Strings.format("[%s] Creating a thread to handle request failed"), id);
                        threadRunning.release();
                    }
                } else {
                    // Someone completed the work between when we checked and got the lock so we'll just finish
                    threadRunning.release();
                }
            }
        }

        private void handleSingleRequest() {
            try {
                var task = queue.poll();

                // If we have a buggy race condition it might be possible for another thread to get the task
                // if that happens we'll just finish without doing anything
                if (task != null) {
                    // TODO call request manager.execute and give it the rate limiter
                    // that call could schedule a new thread so we might still be "running" once it returns
                    executeTask(task);
                }
            } finally {
                phaser.arriveAndDeregister();
            }
        }

        private void executeTask(RejectableTask task) {
            try {
                phaser.register();
                requestManager.execute(task);
            } catch (Exception e) {
                logger.warn(
                    format(
                        "Executor service [%s] failed to execute request for inference endpoint id [%s]",
                        id,
                        task.getRequestCreator().getModel().getInferenceEntityId()
                    ),
                    e
                );
            }
        }

        private synchronized void notifyRequestsOfShutdown() {
            assert isShutdown() : "Requests should only be notified if the executor is shutting down";

            try {
                List<RejectableTask> notExecuted = new ArrayList<>();
                queue.drainTo(notExecuted);

                rejectTasks(notExecuted);
            } catch (Exception e) {
                logger.warn(format("Failed to notify tasks of queuing service [%s] shutdown", id));
            }
        }

        private void rejectTasks(List<RejectableTask> tasks) {
            for (var task : tasks) {
                rejectTask(task);
            }
        }

        private void rejectTask(RejectableTask task) {
            try {
                task.onRejection(
                    new EsRejectedExecutionException(
                        format(
                            "Failed to send request, queue service for inference entity [%s] has shutdown prior to executing request",
                            task.getRequestCreator().getModel().getInferenceEntityId()
                        ),
                        true
                    )
                );
            } catch (Exception e) {
                logger.warn(
                    format(
                        "Failed to notify request for inference endpoint [%s] of rejection after queuing service shutdown",
                        task.getRequestCreator().getModel().getInferenceEntityId()
                    )
                );
            }
        }
    }
}
