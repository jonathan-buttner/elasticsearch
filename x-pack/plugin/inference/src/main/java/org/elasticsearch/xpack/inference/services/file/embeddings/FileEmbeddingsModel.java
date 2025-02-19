/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.file.embeddings;

import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.ChunkingSettings;
import org.elasticsearch.inference.ModelConfigurations;
import org.elasticsearch.inference.ModelSecrets;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.inference.external.action.ExecutableAction;
import org.elasticsearch.xpack.inference.external.action.openai.OpenAiActionVisitor;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;
import org.elasticsearch.xpack.inference.services.file.FileModel;
import org.elasticsearch.xpack.inference.services.settings.DefaultSecretSettings;

import java.util.Map;

public class FileEmbeddingsModel extends FileModel {

    public static FileEmbeddingsModel of(FileEmbeddingsModel model, Map<String, Object> taskSettings) {
        if (taskSettings == null || taskSettings.isEmpty()) {
            return model;
        }

        var requestTaskSettings = FileEmbeddingsRequestTaskSettings.fromMap(taskSettings);
        return new FileEmbeddingsModel(model, FileEmbeddingsTaskSettings.of(model.getTaskSettings(), requestTaskSettings));
    }

    public FileEmbeddingsModel(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        Map<String, Object> serviceSettings,
        Map<String, Object> taskSettings,
        ChunkingSettings chunkingSettings,
        @Nullable Map<String, Object> secrets,
        ConfigurationParseContext context
    ) {
        this(
            inferenceEntityId,
            taskType,
            service,
            FileEmbeddingsServiceSettings.fromMap(serviceSettings, context),
            FileEmbeddingsTaskSettings.fromMap(taskSettings, context),
            chunkingSettings,
            DefaultSecretSettings.fromMap(secrets)
        );
    }

    // Should only be used directly for testing
    FileEmbeddingsModel(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        FileEmbeddingsServiceSettings serviceSettings,
        FileEmbeddingsTaskSettings taskSettings,
        ChunkingSettings chunkingSettings,
        @Nullable DefaultSecretSettings secrets
    ) {
        super(
            new ModelConfigurations(inferenceEntityId, taskType, service, serviceSettings, taskSettings, chunkingSettings),
            new ModelSecrets(secrets),
            serviceSettings,
            secrets
        );
    }

    private FileEmbeddingsModel(FileEmbeddingsModel originalModel, FileEmbeddingsTaskSettings taskSettings) {
        super(originalModel, taskSettings);
    }

    public FileEmbeddingsModel(FileEmbeddingsModel originalModel, FileEmbeddingsServiceSettings serviceSettings) {
        super(originalModel, serviceSettings);
    }

    @Override
    public FileEmbeddingsServiceSettings getServiceSettings() {
        return (FileEmbeddingsServiceSettings) super.getServiceSettings();
    }

    @Override
    public FileEmbeddingsTaskSettings getTaskSettings() {
        return (FileEmbeddingsTaskSettings) super.getTaskSettings();
    }

    @Override
    public DefaultSecretSettings getSecretSettings() {
        return (DefaultSecretSettings) super.getSecretSettings();
    }

    @Override
    public ExecutableAction accept(OpenAiActionVisitor creator, Map<String, Object> taskSettings) {
        return creator.create(this, taskSettings);
    }
}
