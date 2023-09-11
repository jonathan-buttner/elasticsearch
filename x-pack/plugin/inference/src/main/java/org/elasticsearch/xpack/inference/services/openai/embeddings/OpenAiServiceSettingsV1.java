/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.openai.embeddings;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.inference.Model;
import org.elasticsearch.xpack.inference.ServiceSettings;
import org.elasticsearch.xpack.inference.services.MapParsingUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class OpenAiServiceSettingsV1 implements ServiceSettings {

    public static final String NAME = "openai_service_settings";
    public static final String URL = "url";
    public static final String API_TOKEN = "api_token";

    private final String url;
    private final SecureString apiToken;

    public static OpenAiServiceSettingsV1 fromMap(Map<String, Object> map) {
        ValidationException validationException = new ValidationException();

        String url = MapParsingUtils.removeAsType(map, URL, String.class);
        String apiToken = MapParsingUtils.removeAsType(map, API_TOKEN, String.class);

        if (url == null) {
            validationException.addValidationError(MapParsingUtils.missingSettingErrorMsg(URL, Model.SERVICE_SETTINGS));
        } else if (url.isEmpty()) {
            validationException.addValidationError(MapParsingUtils.mustBeNonEmptyString(URL));
        }

        if (apiToken == null) {
            validationException.addValidationError(MapParsingUtils.missingSettingErrorMsg(API_TOKEN, Model.SERVICE_SETTINGS));
        } else if (apiToken.isEmpty()) {
            validationException.addValidationError(MapParsingUtils.mustBeNonEmptyString(API_TOKEN));
        }

        SecureString secureApiToken = new SecureString(Objects.requireNonNull(apiToken).toCharArray());

        if (validationException.validationErrors().isEmpty() == false) {
            throw validationException;
        }

        return new OpenAiServiceSettingsV1(url, secureApiToken);
    }

    public OpenAiServiceSettingsV1(String url, SecureString apiToken) {
        this.url = url;
        this.apiToken = apiToken;
    }

    public OpenAiServiceSettingsV1(StreamInput in) throws IOException {
        url = in.readString();
        // TODO should this be readString?
        // TODO do we need to decrypt here?
        apiToken = in.readSecureString();
    }

    public String getUrl() {
        return url;
    }

    public SecureString getApiToken() {
        return apiToken;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(URL, url);
        // TODO encrypt here
        builder.field(API_TOKEN, apiToken.toString());
        builder.endObject();
        return builder;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.V_8_500_072;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(url);
        // TODO do we need to encrypt here?
        out.writeSecureString(apiToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, apiToken);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiServiceSettingsV1 that = (OpenAiServiceSettingsV1) o;
        return url.equals(that.url) && apiToken.equals(that.apiToken);
    }

}
