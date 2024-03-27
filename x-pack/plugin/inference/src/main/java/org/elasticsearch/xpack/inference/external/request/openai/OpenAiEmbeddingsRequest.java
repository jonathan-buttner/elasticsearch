/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.request.openai;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.common.Truncator;
import org.elasticsearch.xpack.inference.external.openai.OpenAiAccount2;
import org.elasticsearch.xpack.inference.external.request.HttpRequest;
import org.elasticsearch.xpack.inference.external.request.Request;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiEmbeddingsModel;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.external.request.RequestUtils.buildUri;
import static org.elasticsearch.xpack.inference.external.request.RequestUtils.createAuthBearerHeader;
import static org.elasticsearch.xpack.inference.external.request.openai.OpenAiUtils.createOrgHeader;

public class OpenAiEmbeddingsRequest implements OpenAiRequest {

    private final Truncator truncator;
    private final Configuration configuration;
    private final Truncator.TruncationResult truncationResult;
    private final String inferenceEntityId;

    public OpenAiEmbeddingsRequest(
        Truncator truncator,
        Configuration configuration,
        Truncator.TruncationResult input,
        String inferenceEntityId
    ) {
        this.truncator = Objects.requireNonNull(truncator);
        this.configuration = Objects.requireNonNull(configuration);
        this.truncationResult = Objects.requireNonNull(input);
        this.inferenceEntityId = Objects.requireNonNull(inferenceEntityId);
    }

    public HttpRequest createHttpRequest() {
        HttpPost httpPost = new HttpPost(getURI());

        ByteArrayEntity byteEntity = new ByteArrayEntity(
            Strings.toString(new OpenAiEmbeddingsRequestEntity(truncationResult.input(), configuration.entityConfiguration()))
                .getBytes(StandardCharsets.UTF_8)
        );
        httpPost.setEntity(byteEntity);

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, XContentType.JSON.mediaType());
        httpPost.setHeader(createAuthBearerHeader(configuration.account().apiKey()));

        var org = configuration.account().organizationId();
        if (org != null) {
            httpPost.setHeader(createOrgHeader(org));
        }

        return new HttpRequest(httpPost, getInferenceEntityId());
    }

    @Override
    public String getInferenceEntityId() {
        return inferenceEntityId;
    }

    @Override
    public URI getURI() {
        return configuration.account().uri();
    }

    @Override
    public Request truncate() {
        var truncatedInput = truncator.truncate(truncationResult.input());

        return new OpenAiEmbeddingsRequest(truncator, configuration, truncatedInput, inferenceEntityId);
    }

    @Override
    public boolean[] getTruncationInfo() {
        return truncationResult.truncated().clone();
    }

    public static URI buildDefaultUri() throws URISyntaxException {
        return new URIBuilder().setScheme("https")
            .setHost(OpenAiUtils.HOST)
            .setPathSegments(OpenAiUtils.VERSION_1, OpenAiUtils.EMBEDDINGS_PATH)
            .build();
    }

    public record Configuration(OpenAiAccount2 account, OpenAiEmbeddingsRequestEntity.Configuration entityConfiguration) {
        public static Configuration of(OpenAiEmbeddingsModel model) {
            var uri = buildUri(model.getServiceSettings().uri(), "OpenAI", OpenAiEmbeddingsRequest::buildDefaultUri);

            return new Configuration(
                new OpenAiAccount2(uri, model.getServiceSettings().organizationId(), model.getSecretSettings().apiKey()),
                OpenAiEmbeddingsRequestEntity.Configuration.of(model)
            );
        }

        public Configuration {
            Objects.requireNonNull(account);
            Objects.requireNonNull(entityConfiguration);
        }
    }
}
