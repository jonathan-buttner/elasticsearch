/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.elasticsearch.common.CheckedSupplier;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.inference.external.openai.OpenAiAccount2;
import org.elasticsearch.xpack.inference.services.openai.OpenAiModel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

abstract class OpenAiRequestManager extends BaseRequestManager {

    protected OpenAiRequestManager(ThreadPool threadPool, OpenAiModel model, CheckedSupplier<URI, URISyntaxException> uriBuilder) {
        super(threadPool, model.getInferenceEntityId(), RateLimitGrouping.of(model, uriBuilder), model.commonFields().rateLimitSettings());
    }

    record RateLimitGrouping(OpenAiAccount2 account, String modelId) {
        public static RateLimitGrouping of(OpenAiModel model, CheckedSupplier<URI, URISyntaxException> uriBuilder) {
            Objects.requireNonNull(model);

            return new RateLimitGrouping(OpenAiAccount2.of(model, uriBuilder), model.commonFields().modelId());
        }

        public RateLimitGrouping {
            Objects.requireNonNull(account);
            Objects.requireNonNull(modelId);
        }
    }
}
