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
import org.elasticsearch.inference.InferenceServiceResults;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.external.http.retry.RequestSender;
import org.elasticsearch.xpack.inference.external.http.retry.ResponseHandler;
import org.elasticsearch.xpack.inference.external.openai.OpenAiAccount2;
import org.elasticsearch.xpack.inference.external.openai.OpenAiResponseHandler;
import org.elasticsearch.xpack.inference.external.ratelimit.RateLimitSettings;
import org.elasticsearch.xpack.inference.external.request.openai.OpenAiEmbeddingsRequest;
import org.elasticsearch.xpack.inference.external.response.openai.OpenAiEmbeddingsResponseEntity;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiEmbeddingsModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.elasticsearch.xpack.inference.InferencePlugin.UTILITY_THREAD_POOL_NAME;
import static org.elasticsearch.xpack.inference.common.Truncator.truncate;

// TODO rename these to *Executor
public class OpenAiEmbeddingsRequestManager implements RequestManager {

    private static final Logger logger = LogManager.getLogger(OpenAiEmbeddingsRequestManager.class);

    private static final ResponseHandler HANDLER = createEmbeddingsHandler();

    private static ResponseHandler createEmbeddingsHandler() {
        return new OpenAiResponseHandler("openai text embedding", OpenAiEmbeddingsResponseEntity::fromResponse);
    }

    private final Truncator truncator;
    private final Integer maxTokens;
    private final String inferenceEntityId;
    private final RateLimitSettings rateLimitSettings;
    private final ThreadPool threadPool;
    private final RateLimitGrouping rateLimitGrouping;
    private final OpenAiEmbeddingsModel model;

    public OpenAiEmbeddingsRequestManager(OpenAiEmbeddingsModel model, Truncator truncator, ThreadPool threadPool) {
        this.model = Objects.requireNonNull(model);
        maxTokens = model.getServiceSettings().maxInputTokens();
        inferenceEntityId = model.getInferenceEntityId();
        rateLimitGrouping = RateLimitGrouping.of(model);
        this.truncator = Objects.requireNonNull(truncator);
        rateLimitSettings = model.getServiceSettings().rateLimitSettings();
        this.threadPool = Objects.requireNonNull(threadPool);
    }

    @Override
    public void execute(
        List<String> input,
        RequestSender requestSender,
        Supplier<Boolean> hasRequestCompletedFunction,
        ActionListener<InferenceServiceResults> listener
    ) {
        var truncatedInput = truncate(input, maxTokens);
        OpenAiEmbeddingsRequest request = new OpenAiEmbeddingsRequest(truncator, model, truncatedInput, inferenceEntityId);

        threadPool.executor(UTILITY_THREAD_POOL_NAME)
            .execute(new ExecutableInferenceRequest(requestSender, logger, request, HANDLER, hasRequestCompletedFunction, listener));
    }

    @Override
    public String inferenceEntityId() {
        return inferenceEntityId;
    }

    @Override
    public Object rateLimitGrouping() {
        return rateLimitGrouping;
    }

    @Override
    public RateLimitSettings rateLimitSettings() {
        return rateLimitSettings;
    }

    public record RateLimitGrouping(OpenAiAccount2 account, String modelId) {
        public static RateLimitGrouping of(OpenAiEmbeddingsModel model) {
            return new RateLimitGrouping(OpenAiEmbeddingsRequest.createAccount(model), model.getServiceSettings().modelId());
        }

        public RateLimitGrouping {
            Objects.requireNonNull(account);
            Objects.requireNonNull(modelId);
        }
    }
}
