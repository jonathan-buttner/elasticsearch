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

import static org.elasticsearch.xpack.inference.external.request.RequestUtils.createAuthBearerHeader;
import static org.elasticsearch.xpack.inference.external.request.openai.OpenAiUtils.createOrgHeader;

public class OpenAiEmbeddingsRequest implements OpenAiRequest {

    private final Truncator truncator;
    private final OpenAiEmbeddingsModel model;
    private final Truncator.TruncationResult truncationResult;
    private final OpenAiAccount2 account;

    public OpenAiEmbeddingsRequest(Truncator truncator, OpenAiEmbeddingsModel model, Truncator.TruncationResult input) {
        this.truncator = Objects.requireNonNull(truncator);
        this.model = Objects.requireNonNull(model);
        this.truncationResult = Objects.requireNonNull(input);
        account = OpenAiAccount2.of(model, OpenAiEmbeddingsRequest::buildDefaultUri);
    }

    @Override
    public HttpRequest createHttpRequest() {
        HttpPost httpPost = new HttpPost(getURI());

        ByteArrayEntity byteEntity = new ByteArrayEntity(
            Strings.toString(new OpenAiEmbeddingsRequestEntity(truncationResult.input(), model)).getBytes(StandardCharsets.UTF_8)
        );
        httpPost.setEntity(byteEntity);

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, XContentType.JSON.mediaType());
        httpPost.setHeader(createAuthBearerHeader(account.apiKey()));

        var org = account.organizationId();
        if (org != null) {
            httpPost.setHeader(createOrgHeader(org));
        }

        return new HttpRequest(httpPost, getInferenceEntityId());
    }

    @Override
    public String getInferenceEntityId() {
        return model.getInferenceEntityId();
    }

    @Override
    public URI getURI() {
        return account.uri();
    }

    @Override
    public Request truncate() {
        var truncatedInput = truncator.truncate(truncationResult.input());

        return new OpenAiEmbeddingsRequest(truncator, model, truncatedInput);
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

}
