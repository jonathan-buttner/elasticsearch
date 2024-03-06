/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.retry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.core.Strings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.common.TaskExecutor;

import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.elasticsearch.xpack.inference.InferencePlugin.UTILITY_THREAD_POOL_NAME;

class RetryMetrics implements Closeable {

    private static final Logger logger = LogManager.getLogger(RetryMetrics.class);
    private static final int QUEUE_LIMIT = 2000;

    private final ConcurrentMap<String, ConcurrentMap<Integer, Long>> metrics = new ConcurrentHashMap<>();
    private final TaskExecutor executor;
    private Instant timeOfLastLogCall;
    private final Clock clock;
    private final RetrySettings settings;

    RetryMetrics(ThreadPool threadPool, RetrySettings settings) {
        this(threadPool, settings, Clock.systemUTC());
    }

    RetryMetrics(ThreadPool threadPool, RetrySettings settings, Clock clock) {
        Objects.requireNonNull(threadPool);

        this.settings = Objects.requireNonNull(settings);
        this.clock = Objects.requireNonNull(clock);
        timeOfLastLogCall = Instant.now(this.clock);
        executor = new TaskExecutor(threadPool, QUEUE_LIMIT);
        threadPool.executor(UTILITY_THREAD_POOL_NAME).execute(executor::start);
    }

    public void add(String service, int statusCode) {
        executor.execute(() -> {
            var entityStatusCodeMap = metrics.computeIfAbsent(service, key -> new ConcurrentHashMap<>());
            entityStatusCodeMap.compute(statusCode, (key, value) -> {
                if (value == null) {
                    return 1L;
                }

                return value + 1;
            });
        });

        logMetrics(statusCode);
    }

    @Override
    public String toString() {
        var emptyMap = new ConcurrentHashMap<Integer, Long>();
        StringBuilder builder = new StringBuilder();
        var serviceKeys = new HashSet<>(metrics.keySet());

        for (var service : serviceKeys) {
            builder.append(Strings.format("[%s] { ", service));

            for (var inferenceEntityStatusCodeEntry : metrics.getOrDefault(service, emptyMap).entrySet()) {
                builder.append(
                    Strings.format("%s=%s ", inferenceEntityStatusCodeEntry.getKey(), inferenceEntityStatusCodeEntry.getValue())
                );
            }
            builder.append("} ");
        }

        return builder.toString();
    }

    private void logMetrics(int statusCode) {
        if (statusCode < 400
            || settings.getDebugMode() == RetrySettings.DebugFrequencyMode.OFF
            || hasDurationExpired(settings.getDebugFrequency()) == false) {
            return;
        }

        logger.debug(this::toString);
        timeOfLastLogCall = Instant.now(clock);
    }

    private boolean hasDurationExpired(Duration durationToWait) {
        Instant now = Instant.now(clock);
        return now.isAfter(timeOfLastLogCall.plus(durationToWait));
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
    }
}
