/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ContextPreservingActionListener;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.Strings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.threadpool.Scheduler;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.common.AdjustableCapacityBlockingQueue;
import org.elasticsearch.xpack.inference.common.RateLimiter;
import org.elasticsearch.xpack.inference.common.WaitGroup;
import org.elasticsearch.xpack.inference.external.ratelimit.RateLimitSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

    private static final TimeValue DEFAULT_CLEANUP_INTERVAL = TimeValue.timeValueDays(10);
    private static final Duration DEFAULT_STALE_DURATION = Duration.ofDays(10);

    private static final Logger logger = LogManager.getLogger(RequestExecutorService3.class);

    private final ConcurrentMap<Object, RateLimitingEndpointHandler> inferenceEndpoints = new ConcurrentHashMap<>();
    private final ThreadPool threadPool;
    private final SingleRequestManager requestManager;
    private final RequestExecutorServiceSettings settings;
    private final TimeValue cleanUpInterval;
    private final Duration staleEndpointDuration;
    private final Clock clock;

    RequestExecutorService3(ThreadPool threadPool, RequestExecutorServiceSettings settings, SingleRequestManager requestManager) {
        this(threadPool, settings, requestManager, DEFAULT_CLEANUP_INTERVAL, DEFAULT_STALE_DURATION, Clock.systemUTC());
    }

    RequestExecutorService3(
        ThreadPool threadPool,
        RequestExecutorServiceSettings settings,
        SingleRequestManager requestManager,
        TimeValue cleanUpInterval,
        Duration staleEndpointDuration,
        Clock clock
    ) {
        this.threadPool = Objects.requireNonNull(threadPool);
        this.requestManager = Objects.requireNonNull(requestManager);
        this.settings = Objects.requireNonNull(settings);
        this.cleanUpInterval = Objects.requireNonNull(cleanUpInterval);
        this.staleEndpointDuration = Objects.requireNonNull(staleEndpointDuration);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Execute the request at some point in the future.
     *
     * @param requestCreator the http request to send
     * @param input the text to perform inference on
     * @param timeout the maximum time to wait for this request to complete (failing or succeeding). Once the time elapses, the
     *                listener::onFailure is called with a {@link org.elasticsearch.ElasticsearchTimeoutException}.
     *                If null, then the request will wait forever
     * @param listener an {@link ActionListener<InferenceServiceResults>} for the response or failure
     */
    public void execute(
        ExecutableRequestCreator requestCreator,
        List<String> input,
        @Nullable TimeValue timeout,
        ActionListener<InferenceServiceResults> listener
    ) {
        var task = new RequestTask(
            requestCreator,
            input,
            timeout,
            threadPool,
            // TODO when multi-tenancy (as well as batching) is implemented we need to be very careful that we preserve
            // the thread contexts correctly to avoid accidentally retrieving the credentials for the wrong user
            ContextPreservingActionListener.wrapPreservingContext(listener, threadPool.getThreadContext())
        );

        var endpoint = inferenceEndpoints.computeIfAbsent(
            requestCreator.requestConfiguration(),
            key -> new RateLimitingEndpointHandler(
                Integer.toString(requestCreator.requestConfiguration().hashCode()),
                threadPool,
                QUEUE_CREATOR,
                settings,
                requestManager,
                clock,
                requestCreator.rateLimitSettings()
            )
        );

        endpoint.executeTask(task);
    }

    // TODO schedule a cleanup thread to run on an interval and remove entries from the map that are over a day old
    private Scheduler.Cancellable startCleanUpThread(ThreadPool threadPool) {
        logger.debug(() -> Strings.format("Clean up task scheduled with interval [%s]", cleanUpInterval));

        return threadPool.scheduleWithFixedDelay(this::removeOldEndpoints, cleanUpInterval, threadPool.executor(UTILITY_THREAD_POOL_NAME));
    }

    private void removeOldEndpoints() {
        var now = Instant.now(clock);
        for (Iterator<Map.Entry<Object, RateLimitingEndpointHandler>> iterator = inferenceEndpoints.entrySet().iterator(); iterator.hasNext()) {
            var endpoint = iterator.next();
            // if the current time is after the last time the endpoint received a request + allowed stale period then we'll remove it
            if (now.isAfter(endpoint.getValue().timeOfLastEnqueue().plus(staleEndpointDuration))) {
                iterator.remove();
            }
        }
    }

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
        private final WaitGroup waitGroup = new WaitGroup();
        private final ThreadPool threadPool;
        private final SingleRequestManager requestManager;
        private final String id;
        private Instant timeOfLastEnqueue;
        private final Clock clock;
        private final RateLimiter rateLimiter;

        RateLimitingEndpointHandler(
            String id,
            ThreadPool threadPool,
            AdjustableCapacityBlockingQueue.QueueCreator<RejectableTask> createQueue,
            RequestExecutorServiceSettings settings,
            SingleRequestManager requestManager,
            Clock clock,
            RateLimitSettings rateLimitSettings
        ) {
            this.id = Objects.requireNonNull(id);
            this.queue = new AdjustableCapacityBlockingQueue<>(createQueue, settings.getQueueCapacity());
            this.threadPool = Objects.requireNonNull(threadPool);
            this.requestManager = Objects.requireNonNull(requestManager);
            this.clock = Objects.requireNonNull(clock);

            Objects.requireNonNull(rateLimitSettings);
            // TODO figure out a good limit
            rateLimiter = new RateLimiter(1, rateLimitSettings.tokensPerTimeUnit(), rateLimitSettings.timeUnit());

            settings.registerQueueCapacityCallback(this::onCapacityChange);
        }

        private void onCapacityChange(int capacity) {
            logger.debug(() -> Strings.format("Executor service [%s] setting queue capacity to [%s]", id, capacity));

            try {
                queue.setCapacity(capacity);
            } catch (Exception e) {
                logger.warn(format("Executor service [%s] failed to set the capacity of the task queue to [%s]", id, capacity), e);
            }
        }

        public int queueSize() {
            return queue.size();
        }

        public boolean isTerminated() {
            return waitGroup.isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return waitGroup.awaitTermination(timeout, unit);
        }

        public void shutdown() {
            shutdown.set(true);
        }

        public boolean isShutdown() {
            return shutdown.get();
        }

        public Instant timeOfLastEnqueue() {
            return timeOfLastEnqueue;
        }

        public void enqueue(RequestTask task) {
            timeOfLastEnqueue = Instant.now(clock);

            if (isShutdown()) {
                EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                    format(
                        "Failed to enqueue task because the executor service [%s] has already shutdown",
                        task.getRequestCreator().inferenceEntityId()
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
                        task.getRequestCreator().inferenceEntityId()
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

        private void checkForTask() {
            // There is a possibility that a request could come in, acquire the semaphore, and complete the task between when
            // we peek and when we attempt to acquire the semaphore here. We'll handle that by peeking while attempt to dequeue
            if (queue.peek() != null && threadRunning.tryAcquire()) {
                // Now that we have the exclusive lock, check again to see if we have work to do
                if (queue.peek() != null) {
                    try {
                        waitGroup.add();
                        threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(this::handleSingleRequest);
                    } catch (Exception e) {
                        logger.warn(Strings.format("Executor service [%s] failed to create a thread to handle request", id));
                        threadRunning.release();
                        waitGroup.done();
                    }
                } else {
                    // Someone completed the work between when we checked and got the lock so we'll just finish
                    threadRunning.release();
                }
            }
        }

        private void scheduleRequest(Runnable executableRequest) {
            Runnable toRun = () -> {
                executableRequest.run();
                onFinishExecutingRequest();
            };

            var timeDelay = rateLimiter.reserve(1);

            try {
                if (shouldExecuteImmediately(timeDelay)) {
                    threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(toRun);
                } else {
                    threadPool.schedule(toRun, timeDelay, threadPool.executor(UTILITY_THREAD_POOL_NAME));
                }
            } catch (Exception e) {
                logger.warn(Strings.format("Executor service [%s] failed to create a thread to handle request", id));
                handleFailureWhileInCriticalSection();
            }
        }

        private static boolean shouldExecuteImmediately(TimeValue delay) {
            return delay.duration() == 0;
        }

        private void handleFailureWhileInCriticalSection() {
            onFinishExecutingRequest();
        }

        private void onFinishExecutingRequest() {
            threadRunning.release();
            try {
                checkForTask();
            } finally {
                waitGroup.done();
            }
        }

        private void handleSingleRequest() {
            try {
                var task = queue.poll();

                // If we have a buggy race condition it might be possible for another thread to get the task
                // Another scenario where the task would be null would be if we're instructed to shut down and some other
                // thread drained the queue already
                if (task != null) {
                    // This could schedule a thread execution in the future, if the request needs to be rate limited
                    executeTask(task);
                } else {
                    // We don't have a task to run, so release the thread lock
                    threadRunning.release();
                }
            } finally {
                if (isShutdown()) {
                    notifyRequestsOfShutdown();
                }
                waitGroup.done();
            }
        }

        private void executeTask(RejectableTask task) {
            try {
                waitGroup.add();
                requestManager.execute(task, this::scheduleRequest);
            } catch (Exception e) {
                logger.warn(
                    format(
                        "Executor service [%s] failed to execute request for inference endpoint id [%s]",
                        id,
                        task.getRequestCreator().inferenceEntityId()
                    ),
                    e
                );
                handleFailureWhileInCriticalSection();
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
                            task.getRequestCreator().inferenceEntityId()
                        ),
                        true
                    )
                );
            } catch (Exception e) {
                logger.warn(
                    format(
                        "Failed to notify request for inference endpoint [%s] of rejection after queuing service shutdown",
                        task.getRequestCreator().inferenceEntityId()
                    )
                );
            }
        }

    }
}
