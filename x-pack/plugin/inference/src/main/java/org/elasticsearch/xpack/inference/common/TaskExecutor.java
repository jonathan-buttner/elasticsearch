/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes tasks from a queue. If the queue is full tasks are simply ignored. If a task is added after shutting down has
 * completed the task is ignored. During shutdown any tasks already queued will be executed before completing the shutdown.
 */
public class TaskExecutor {
    private static final Logger logger = LogManager.getLogger(TaskExecutor.class);
    private final ThreadPool threadPool;
    private final BlockingQueue<Runnable> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CountDownLatch terminationLatch = new CountDownLatch(1);

    public TaskExecutor(ThreadPool threadPool, int queueLimit) {
        this.threadPool = Objects.requireNonNull(threadPool);
        queue = new LinkedBlockingQueue<>(queueLimit);
    }

    public void start() {
        try {
            while (running.get()) {
                handleTasks();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
            executeRemainingTasks();
            terminationLatch.countDown();
        }
    }

    private void handleTasks() throws InterruptedException {
        try {
            var task = queue.take();
            if (running.get() == false) {
                logger.debug("Task executor service exiting");
            }

            executeTask(task);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Task executor failed while retrieving task for execution", e);
        }
    }

    private static void executeTask(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            logger.warn("Failed to execute task", e);
        }
    }

    private synchronized void executeRemainingTasks() {
        assert isShutdown() : "Draining remaining tasks from the queue should only occur during shutdown";

        try {
            List<Runnable> remainingTasks = new ArrayList<>();
            queue.drainTo(remainingTasks);

            for (var task : remainingTasks) {
                executeTask(task);
            }
        } catch (Exception e) {
            logger.warn("Failed to execute remaining tasks during shutdown", e);
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // if this fails because the queue is full, that's ok, we just want to ensure that queue.take() returns
            queue.offer(() -> {});
        }
    }

    public boolean isShutdown() {
        return running.get() == false;
    }

    public boolean isTerminated() {
        return terminationLatch.getCount() == 0;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return terminationLatch.await(timeout, unit);
    }

    /**
     * Execute the runnable sometime in the future.
     * @param task the task to execute
     */
    public void execute(Runnable task) {
        if (isShutdown()) {
            return;
        }

        var preservedContextTask = threadPool.getThreadContext().preserveContext(task);

        queue.offer(preservedContextTask);
        if (isShutdown()) {
            executeRemainingTasks();
        }
    }
}
