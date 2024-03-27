/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.request.openai;

import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiEmbeddingsModel;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public record OpenAiEmbeddingsRequestEntity(List<String> input, OpenAiEmbeddingsRequestEntity.Configuration configuration)
    implements
        ToXContentObject {

    private static final String INPUT_FIELD = "input";
    private static final String MODEL_FIELD = "model";
    private static final String USER_FIELD = "user";
    private static final String DIMENSIONS_FIELD = "dimensions";

    public OpenAiEmbeddingsRequestEntity {
        Objects.requireNonNull(input);
        Objects.requireNonNull(configuration);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(INPUT_FIELD, input);
        builder.field(MODEL_FIELD, configuration.modelId);

        if (configuration.user != null) {
            builder.field(USER_FIELD, configuration.user);
        }

        if (configuration.dimensions != null) {
            builder.field(DIMENSIONS_FIELD, configuration.dimensions);
        }

        builder.endObject();
        return builder;
    }

    public record Configuration(String modelId, @Nullable String user, @Nullable Integer dimensions) {
        public static Configuration of(OpenAiEmbeddingsModel model) {
            var dimensionsToUse = model.getServiceSettings().dimensionsSetByUser() ? model.getServiceSettings().dimensions() : null;
            return new Configuration(model.getServiceSettings().modelId(), model.getTaskSettings().user(), dimensionsToUse);
        }

        public Configuration {
            Objects.requireNonNull(modelId);
        }
    }
}
