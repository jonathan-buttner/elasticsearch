/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.openai.embeddings;

import org.elasticsearch.xpack.inference.Model;
import org.elasticsearch.xpack.inference.TaskType;

public class OpenAiEmbeddingsModelV1 extends Model {
    public OpenAiEmbeddingsModelV1(
        String modelId,
        TaskType taskType,
        String service,
        OpenAiServiceSettingsV1 serviceSettings,
        OpenAiTaskSettingsV1 taskSettings
    ) {
        super(modelId, taskType, service, serviceSettings, taskSettings);
    }

    @Override
    public OpenAiServiceSettingsV1 getServiceSettings() {
        return (OpenAiServiceSettingsV1) super.getServiceSettings();
    }

    @Override
    public OpenAiTaskSettingsV1 getTaskSettings() {
        return (OpenAiTaskSettingsV1) super.getTaskSettings();
    }
}
