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
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.external.openai.OpenAiResponseHandler;
import org.elasticsearch.xpack.inference.external.ratelimit.RateLimitSettings;
import org.elasticsearch.xpack.inference.external.request.openai.OpenAiEmbeddingsRequest;
import org.elasticsearch.xpack.inference.external.response.openai.OpenAiEmbeddingsResponseEntity;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiEmbeddingsModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.inference.common.Truncator.truncate;

public class OpenAiEmbeddingsExecutableRequestCreator implements ExecutableRequestCreator {

    private static final Logger logger = LogManager.getLogger(OpenAiEmbeddingsExecutableRequestCreator.class);

    private static final ResponseHandler HANDLER = createEmbeddingsHandler();

    private static ResponseHandler createEmbeddingsHandler() {
        return new OpenAiResponseHandler("openai text embedding", OpenAiEmbeddingsResponseEntity::fromResponse);
    }

    private final Truncator truncator;
    private final Integer maxTokens;
    private final String inferenceEntityId;
    private final RateLimitSettings rateLimitSettings;
    private final OpenAiEmbeddingsRequest.Configuration configuration;

    public OpenAiEmbeddingsExecutableRequestCreator(OpenAiEmbeddingsModel model, Truncator truncator) {
        Objects.requireNonNull(model);
        maxTokens = model.getServiceSettings().maxInputTokens();
        inferenceEntityId = model.getInferenceEntityId();
        configuration = OpenAiEmbeddingsRequest.Configuration.of(model);
        this.truncator = Objects.requireNonNull(truncator);
        // TODO get this from the model
        rateLimitSettings = new RateLimitSettings(TimeValue.timeValueMinutes(3000));
    }

    @Override
    public Runnable create(
        List<String> input,
        RequestSender requestSender,
        Supplier<Boolean> hasRequestCompletedFunction,
        ActionListener<InferenceServiceResults> listener
    ) {
        var truncatedInput = truncate(input, maxTokens);
        OpenAiEmbeddingsRequest request = new OpenAiEmbeddingsRequest(truncator, configuration, truncatedInput, inferenceEntityId);

        return new ExecutableInferenceRequest(requestSender, logger, request, HANDLER, hasRequestCompletedFunction, listener);
    }

    @Override
    public String inferenceEntityId() {
        return inferenceEntityId;
    }

    @Override
    public Object requestConfiguration() {
        return configuration;
    }

    @Override
    public RateLimitSettings rateLimitSettings() {
        return null;
    }
}
