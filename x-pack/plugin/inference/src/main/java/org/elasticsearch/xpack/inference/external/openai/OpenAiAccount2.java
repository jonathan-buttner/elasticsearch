/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.openai;

import org.elasticsearch.common.CheckedSupplier;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xpack.inference.services.openai.OpenAiModel;

import java.net.URI;
import java.net.URISyntaxException;

import static org.elasticsearch.xpack.inference.external.request.RequestUtils.buildUri;

public record OpenAiAccount2(URI uri, @Nullable String organizationId, SecureString apiKey) {
    public static OpenAiAccount2 of(OpenAiModel model, CheckedSupplier<URI, URISyntaxException> uriBuilder) {
        var uri = buildUri(model.commonFields().uri(), "OpenAI", uriBuilder);

        return new OpenAiAccount2(uri, model.commonFields().organizationId(), model.apiKey());
    }
}
