/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.elasticsearch.xpack.inference.external.http.retry.RetryingHttpSender;

import java.util.Objects;

/**
 * Handles executing a single inference request at a time.
 */
public class SingleRequestManager {

    protected RetryingHttpSender requestSender;

    public SingleRequestManager(RetryingHttpSender requestSender) {
        this.requestSender = Objects.requireNonNull(requestSender);
    }

    public void execute(InferenceRequest inferenceRequest) {
        if (isNoopRequest(inferenceRequest) || inferenceRequest.hasCompleted()) {
            return;
        }

        // TODO execute this one a separate thread via threadPool.execute()
        inferenceRequest.getRequestCreator()
            .create(
                inferenceRequest.getInput(),
                requestSender,
                inferenceRequest.getRequestCompletedFunction(),
                inferenceRequest.getListener()
            )
            .run();
    }

    private static boolean isNoopRequest(InferenceRequest inferenceRequest) {
        return inferenceRequest.getRequestCreator() == null
            || inferenceRequest.getInput() == null
            || inferenceRequest.getListener() == null;
    }
}
