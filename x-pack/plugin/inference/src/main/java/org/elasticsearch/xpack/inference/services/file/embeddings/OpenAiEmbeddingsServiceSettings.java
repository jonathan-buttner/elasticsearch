/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.file.embeddings;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.index.mapper.vectors.DenseVectorFieldMapper;
import org.elasticsearch.inference.ParsableObject;
import org.elasticsearch.inference.ServiceSettings;
import org.elasticsearch.inference.SimilarityMeasure;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.schema.ConfigurationSystem;
import org.elasticsearch.xpack.inference.schema.IntegerHandler;
import org.elasticsearch.xpack.inference.schema.RateLimitField;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;
import org.elasticsearch.xpack.inference.services.file.OpenAiRateLimitServiceSettings;
import org.elasticsearch.xpack.inference.services.settings.FilteredXContentObject;
import org.elasticsearch.xpack.inference.services.settings.RateLimitSettings;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.services.ServiceFields.DIMENSIONS;

/**
 * Defines the service settings for interacting with OpenAI's text embedding models.
 */
public class OpenAiEmbeddingsServiceSettings extends FilteredXContentObject implements ServiceSettings, OpenAiRateLimitServiceSettings {

    public static final String NAME = "openai_service_settings2";

    static final String DIMENSIONS_SET_BY_USER = "dimensions_set_by_user";
    // The rate limit for usage tier 1 is 3000 request per minute for the text embedding models
    // To find this information you need to access your account's limits https://platform.openai.com/account/limits
    // 3000 requests per minute
    private static final RateLimitSettings DEFAULT_RATE_LIMIT_SETTINGS = new RateLimitSettings(3000);

    public static OpenAiEmbeddingsServiceSettings fromMap(
        ConfigurationSystem system,
        ParsableObject config,
        ConfigurationParseContext context
    ) {
        try (
            XContentParser jsonParser = XContentHelper.createParser(
                XContentParserConfiguration.EMPTY,
                config.configRef(),
                XContentType.JSON
            )
        ) {
            var taskTypeConfig = Objects.requireNonNull(system.serviceConfiguration().taskTypes().get(TaskType.TEXT_EMBEDDING));

            return switch (context) {
                case REQUEST -> fromRequest(taskTypeConfig, jsonParser);
                case PERSISTENT -> fromPersistent(taskTypeConfig, jsonParser);
            };
        } catch (IOException e) {
            throw new ValidationException(e);
        }

    }

    private static OpenAiEmbeddingsServiceSettings fromPersistent(ConfigurationSystem.ConfigTaskType taskTypeConfig, XContentParser parser)
        throws IOException {
        var parsedResults = taskTypeConfig.persistentStateParser().parse(parser);
        var dimensions = parsedResults.get(DIMENSIONS, IntegerHandler.IntegerValue.class);
        var rateLimit = parsedResults.getOrThrow(RateLimitSettings.FIELD_NAME, RateLimitField.Value.class);

        return new OpenAiEmbeddingsServiceSettings(dimensions, new RateLimitSettings(rateLimit.getRequestsPerMinuteLimit()));
    }

    private static OpenAiEmbeddingsServiceSettings fromRequest(ConfigurationSystem.ConfigTaskType taskTypeConfig, XContentParser parser)
        throws IOException {
        var parsedResults = taskTypeConfig.createEntityParser().parse(parser);
        var dimensions = parsedResults.get(DIMENSIONS, IntegerHandler.IntegerValue.class);
        var rateLimit = parsedResults.getOrThrow(RateLimitSettings.FIELD_NAME, RateLimitField.Value.class);

        return new OpenAiEmbeddingsServiceSettings(dimensions, new RateLimitSettings(rateLimit.getRequestsPerMinuteLimit()));
    }

    private final IntegerHandler.IntegerValue dimensions;
    private final RateLimitSettings rateLimitSettings;

    public OpenAiEmbeddingsServiceSettings(@Nullable IntegerHandler.IntegerValue dimensions, RateLimitSettings rateLimitSettings) {
        this.dimensions = dimensions;
        this.rateLimitSettings = Objects.requireNonNullElse(rateLimitSettings, DEFAULT_RATE_LIMIT_SETTINGS);
    }

    public OpenAiEmbeddingsServiceSettings(StreamInput in) throws IOException {
        dimensions = in.readOptionalWriteable(IntegerHandler.IntegerValue::new);
        rateLimitSettings = new RateLimitSettings(in);
    }

    @Override
    public RateLimitSettings rateLimitSettings() {
        return rateLimitSettings;
    }

    @Override
    public URI uri() {
        return null;
    }

    @Override
    public String organizationId() {
        return "";
    }

    @Override
    public SimilarityMeasure similarity() {
        return SimilarityMeasure.L2_NORM;
    }

    @Override
    public Integer dimensions() {
        if (dimensions != null) {
            return dimensions.getValue();
        }

        return null;
    }

    public Integer maxInputTokens() {
        return null;
    }

    @Override
    public String modelId() {
        return "";
    }

    @Override
    public DenseVectorFieldMapper.ElementType elementType() {
        return DenseVectorFieldMapper.ElementType.FLOAT;
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        toXContentFragmentOfExposedFields(builder, params);
        // TODO if we returned a noop we wouldn't need the null check here
        if (dimensions != null) {
            dimensions.toXContentFragmentOfHiddenFields(builder, params);
        }

        builder.endObject();
        return builder;
    }

    @Override
    protected XContentBuilder toXContentFragmentOfExposedFields(XContentBuilder builder, Params params) throws IOException {
        if (dimensions != null) {
            dimensions.toXContentFragment(builder, params);
        }
        rateLimitSettings.toXContent(builder, params);

        return builder;
    }

    @Override
    public TransportVersion getMinimalSupportedVersion() {
        return TransportVersions.V_8_12_0;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalWriteable(dimensions);

        rateLimitSettings.writeTo(out);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiEmbeddingsServiceSettings that = (OpenAiEmbeddingsServiceSettings) o;
        return Objects.equals(dimensions, that.dimensions) && Objects.equals(rateLimitSettings, that.rateLimitSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, rateLimitSettings);
    }
}
