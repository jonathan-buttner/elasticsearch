/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.huggingface;

import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.ModelSecrets;
import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.action.huggingface.HuggingFaceActionVisitor;
import org.elasticsearch.xpack.inference.services.ServiceUtils;
import org.elasticsearch.xpack.inference.services.settings.ApiKeySecrets;

import java.util.Objects;

public abstract class HuggingFaceModel extends Model {

    private final HuggingFaceCommonServiceSettingFields commonFields;
    private final SecureString apiKey;

    public HuggingFaceModel(
        ModelConfigurations configurations,
        ModelSecrets secrets,
        HuggingFaceCommonServiceSettingFields commonFields,
        @Nullable ApiKeySecrets secretSettings
    ) {
        super(configurations, secrets);

        this.commonFields = Objects.requireNonNull(commonFields);
        apiKey = ServiceUtils.apiKey(secretSettings);
    }

    public abstract ExecutableAction accept(HuggingFaceActionVisitor creator);

    public SecureString apiKey() {
        return apiKey;
    }

    public HuggingFaceCommonServiceSettingFields commonFields() {
        return commonFields;
    }
}
