/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.openai;

import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.ModelSecrets;
import org.elasticsearch.inference.ServiceSettings;
import org.elasticsearch.inference.TaskSettings;
import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.action.openai.OpenAiActionVisitor;
import org.elasticsearch.xpack.inference.services.ServiceUtils;
import org.elasticsearch.xpack.inference.services.settings.DefaultSecretSettings;

import java.util.Map;
import java.util.Objects;

public abstract class OpenAiModel extends Model {

    private final OpenAiCommonServiceSettingFields commonFields;
    private final SecureString apiKey;

    public OpenAiModel(
        ModelConfigurations configurations,
        ModelSecrets secrets,
        OpenAiCommonServiceSettingFields commonFields,
        @Nullable DefaultSecretSettings secretSettings
    ) {
        super(configurations, secrets);

        this.commonFields = Objects.requireNonNull(commonFields);
        this.apiKey = ServiceUtils.apiKey(secretSettings);
    }

    protected OpenAiModel(OpenAiModel model, TaskSettings taskSettings) {
        super(model, taskSettings);

        commonFields = model.commonFields;
        apiKey = model.apiKey();
    }

    protected OpenAiModel(OpenAiModel model, ServiceSettings serviceSettings) {
        super(model, serviceSettings);

        commonFields = model.commonFields;
        apiKey = model.apiKey();
    }

    public abstract ExecutableAction accept(OpenAiActionVisitor creator, Map<String, Object> taskSettings);

    public SecureString apiKey() {
        return apiKey;
    }

    public OpenAiCommonServiceSettingFields commonFields() {
        return commonFields;
    }
}
