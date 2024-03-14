/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.xpack.inference.common.AdjustableCapacityBlockingQueue;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.elasticsearch.core.Strings.format;

class RequestExecutorService2 {

    private final ConcurrentMap<Object, Object> inferenceEndpoints = new ConcurrentHashMap<>();

    RequestExecutorService2() {

    }

    // TODO add rate limiter as a member of the InferenceEndpoint
    private static class InferenceEndpointHandler {
        private final AdjustableCapacityBlockingQueue<RejectableTask> queue;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        InferenceEndpointHandler(AdjustableCapacityBlockingQueue<RejectableTask> queue, AtomicBoolean running) {
            this.queue = Objects.requireNonNull(queue);
        }

        public void handleRequests() {
            try {

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {

            }
        }

        private void requestHandlerLoop() throws InterruptedException {
            while (running.get()) {
                var task = queue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    // TODO execute task
                } else {
                    final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
                    writeLock.lock();
                    try {
                        if (queue.peek() == null) {
                            running.set(false);
                        }
                    } finally {
                        writeLock.unlock();
                    }
                }

            }
        }

        public void execute(RequestTask task) {
            final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            if (shuttingDown.get()) {
                readLock.unlock();

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

            boolean addedToQueue;
            try {
                addedToQueue = queue.offer(task);
            } finally {
                readLock.unlock();
            }

            if (addedToQueue == false) {
                EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                    format(
                        "Failed to execute task because the executor service [%s] queue is full",
                        task.getRequestCreator().getModel().getInferenceEntityId()
                    ),
                    false
                );

                task.onRejection(rejected);
                return;
            }

        }

        public void execute2(RequestTask task) {
            final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                if (shuttingDown.get()) {

                    EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                        format(
                            "Failed to enqueue task because the executor service [%s] has already shutdown",
                            task.getRequestCreator().getModel().getInferenceEntityId()
                        ),
                        true
                    );

                    // TODO do this one a separate thread
                    task.onRejection(rejected);
                    return;
                }

                var addedToQueue = queue.offer(task);

                if (addedToQueue == false) {
                    EsRejectedExecutionException rejected = new EsRejectedExecutionException(
                        format(
                            "Failed to execute task because the executor service [%s] queue is full",
                            task.getRequestCreator().getModel().getInferenceEntityId()
                        ),
                        false
                    );

                    // TODO do this one a separate thread
                    task.onRejection(rejected);
                    return;
                }

            } finally {
                readLock.unlock();
            }

            final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();
            readLock.lock();
            if (running.get() == false) {
                readLock.unlock();
                writeLock.lock();

                try {
                    // check again just in case another thread started running while we were obtaining the write lock
                    if (running.get() == false) {
                        running.set(true);
                        // TODO spin up the thread to poll the queue
                    }
                    // TODO do we need this downgrade?
                    readLock.lock();
                } finally {
                    writeLock.unlock();
                }
            }
        }

    }
}
