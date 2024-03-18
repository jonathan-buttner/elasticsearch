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
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.common.AdjustableCapacityBlockingQueue;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.elasticsearch.core.Strings.format;
import static org.elasticsearch.xpack.inference.InferencePlugin.UTILITY_THREAD_POOL_NAME;

class RequestExecutorService2 {

    private final ConcurrentMap<Object, Object> inferenceEndpoints = new ConcurrentHashMap<>();

    RequestExecutorService2() {

    }

    // TODO add rate limiter as a member of the InferenceEndpoint

    /**
     * Provides a mechanism for ensuring that only a single thread is processing tasks from the queue at a time.
     * As tasks are enqueued for execution, if a thread is already processing tasks, a new one will not be started.
     * If a thread is not already processing tasks, a short-lived thread will be used to process the tasks in the queue
     * and once all the queue is empty the thread will exit.
     */
    private static class InferenceEndpointHandler {

        private static final Logger logger = LogManager.getLogger(InferenceEndpointHandler.class);
        private static final int POLLING_WAIT_TIME = 200;

        private final String id;
        private final AdjustableCapacityBlockingQueue<RejectableTask> queue;
        private volatile boolean running = false;
        private volatile boolean shutdown = false;
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        private final ThreadPool threadPool;

        InferenceEndpointHandler(String id, AdjustableCapacityBlockingQueue<RejectableTask> queue, ThreadPool threadPool) {
            this.id = Objects.requireNonNull(id);
            this.queue = Objects.requireNonNull(queue);
            this.threadPool = Objects.requireNonNull(threadPool);
        }

        public void shutdown() {
            final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                shutdown = true;
            } finally {
                writeLock.unlock();
            }

            // not strictly necessary but will break out of the poll delay immediately
            queue.offer(new NoopTask());
        }

        public void execute(RequestTask task) {
            final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
            // get a read lock to ensure that we are not shutting down
            readLock.lock();
            try {
                if (shutdown) {
                    EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                        format(
                            "Failed to enqueue task because the executor service [%s] has already shutdown",
                            task.getRequestCreator().getModel().getInferenceEntityId()
                        ),
                        true
                    );

                    // TODO do this one on a separate thread
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

                    // TODO do this one on a separate thread
                    task.onRejection(rejected);
                    return;
                }

                if (running == false) {
                    readWriteLock.readLock().unlock();
                    readWriteLock.writeLock().lock();
                    try {
                        // check again just in case another thread started running while we were obtaining the write lock
                        if (running == false) {
                            running = true;
                            threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(() -> handleRequests());
                        }
                        readWriteLock.readLock().lock();
                    } finally {
                        readWriteLock.writeLock().unlock();
                    }
                }
            } finally {
                readLock.unlock();
            }
        }

        // private void startHandlingTasks() {
        // readWriteLock.readLock().lock();
        // try {
        // if (running == false) {
        // readWriteLock.readLock().unlock();
        // readWriteLock.writeLock().lock();
        // try {
        // // check again just in case another thread started running while we were obtaining the write lock
        // if (running == false) {
        // running = true;
        // threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(() -> handleRequests());
        // }
        // readWriteLock.readLock().lock();
        // } finally {
        // readWriteLock.writeLock().unlock();
        // }
        // }
        // } finally {
        // readWriteLock.readLock().unlock();
        // }
        // }

        private void handleRequests() {
            try {
                // Using a local flag here so that if one thread finishes the loop
                // while another thread begins executing this code, they can both execute without relying on the same
                // "running" flag
                // We intentionally don't want to rely on the running flag here because we'd be accessing it without a lock
                // which could result in multiple threads executing the loop at the same time
                boolean shouldProcessTasks = true;
                while (shouldProcessTasks) {
                    shouldProcessTasks = handleTasks();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                doTerminateFromInterruption();
            }
        }

        private boolean handleTasks() throws InterruptedException {
            try {
                var task = queue.poll(POLLING_WAIT_TIME, TimeUnit.MILLISECONDS);
                if (task != null) {
                    // TODO execute task
                } else {
                    return shouldContinueProcessing();
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                logger.warn(Strings.format("Request executor service [%s] failed while retrieving task for execution", id));
            }

            return true;
        }

        private boolean shouldContinueProcessing() {
            final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
            // Getting a write lock to ensure that no additional tasks are enqueued until we check the state of the queue
            // This guarantees that a task won't be left in the queue in a situation where it is added after peek() returns null
            // but before we set running to false here. Effectively avoiding a situation where we don't see it with the peek
            // and this execution thread drops out of the loop but the original request does not spin up a new thread to process the item
            writeLock.lock();
            try {
                if (queue.peek() == null) {
                    running = false;
                    return false;
                } else {
                    return true;
                }
            } finally {
                writeLock.unlock();
            }
        }

        private void doTerminateFromInterruption() {
            final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                shutdown = true;
                running = false;
            } finally {
                writeLock.unlock();
            }
        }
    }
}
