/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.ratelimit.RateLimitable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A contract for constructing a {@link Runnable} to handle sending an inference request to a 3rd party service.
 */
public interface ExecutableRequestCreator extends RateLimitable {
    Runnable create(
        List<String> input,
        RequestSender requestSender,
        Supplier<Boolean> hasRequestCompletedFunction,
        ActionListener<InferenceServiceResults> listener
    );

    String inferenceEntityId();

    /**
     * Returns an object responsible for containing the all the fields that will make up the request
     * except any input fields (fields that can be batched together). In practice the class should contain things like
     * api key, url, model, or any headers that make the request unique. The class must implement hashcode such that
     * these fields are taken into account.
     */
    Object requestConfiguration();
}
