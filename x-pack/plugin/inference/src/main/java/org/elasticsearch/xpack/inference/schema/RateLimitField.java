/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

// TODO maybe make this a record
public class RateLimitField implements ConfigField {
    private static final String REQUESTS_PER_MINUTE_FIELD = "requests_per_minute";
    public static final String RATE_LIMIT_FIELD = "rate_limit";

    public record Schema(RequestsPerMinuteSchema requestsPerMinuteSchema) {

        private static final ConstructingObjectParser<Schema, Void> PARSER = new ConstructingObjectParser<>(
            RateLimitField.class.getSimpleName() + "." + Schema.class.getSimpleName(),
            false,
            (args, c) -> new Schema((RequestsPerMinuteSchema) args[0])
        );

        static {
            PARSER.declareObject(
                constructorArg(),
                (p, c) -> RequestsPerMinuteSchema.PARSER.apply(p, null),
                new ParseField(REQUESTS_PER_MINUTE_FIELD)
            );
        }

        private record RequestsPerMinuteSchema(Long defaultRequestsPerMinute) {
            private static final ConstructingObjectParser<RequestsPerMinuteSchema, Void> PARSER = new ConstructingObjectParser<>(
                RequestsPerMinuteSchema.class.getSimpleName(),
                false,
                (args, c) -> new RequestsPerMinuteSchema((Long) args[0])
            );

            static {
                PARSER.declareLong(constructorArg(), new ParseField("default"));
            }
        }
    }

    /**
     * This class handles how to serialize the rate limit field. The field could be a default value from the configuration schema
     * or one parsed from the persistent state.
     */
    public static class RateLimitSerializable implements ParsedValue {
        private final long requestsPerMinute;

        public RateLimitSerializable(long requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public RateLimitSerializable(StreamInput in) throws IOException {
            this(in.readVLong());
        }

        @Override
        public String fieldName() {
            return RATE_LIMIT_FIELD;
        }

        @Override
        public XContentBuilder toXContentFragment(XContentBuilder builder, ToXContent.Params params) throws IOException {
            return toXContentFragmentWithName(builder, params, RATE_LIMIT_FIELD);
        }

        @Override
        public XContentBuilder toXContentFragmentOfHiddenFields(XContentBuilder builder, ToXContent.Params params) throws IOException {
            return builder;
        }

        @Override
        public XContentBuilder toXContentFragmentWithName(XContentBuilder builder, ToXContent.Params params, String fieldName)
            throws IOException {
            builder.startObject(fieldName);
            builder.field(REQUESTS_PER_MINUTE_FIELD, requestsPerMinute);
            builder.endObject();
            return builder;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVLong(requestsPerMinute);
        }

        public long getRequestsPerMinuteLimit() {
            return requestsPerMinute;
        }
    }

    /**
     * This class handles parsing a field from the request to create an inference endpoint or loading from persistent state.
     */
    public static class Value extends RateLimitSerializable {

        private final String path;

        public Value(long requestsPerMinute, String path) {
            super(requestsPerMinute);

            this.path = Objects.requireNonNull(path);
        }

        public static Value parse(XContentParser parser, String path) throws IOException {
            return PARSER.apply(parser, path);
        }

        private static final ConstructingObjectParser<Value, String> PARSER = new ConstructingObjectParser<>(
            RateLimitField.class.getSimpleName() + "." + Value.class.getSimpleName(),
            false,
            (args, path) -> new Value((Long) args[0], path)
        );

        static {
            PARSER.declareLong(constructorArg(), new ParseField(REQUESTS_PER_MINUTE_FIELD));
        }
    }

    public static RateLimitField parseSchema(XContentParser parser, String rootPath) {
        var schema = Schema.PARSER.apply(parser, null);

        return new RateLimitField(
            new RateLimitSerializable(schema.requestsPerMinuteSchema.defaultRequestsPerMinute),
            rootPath + "." + RATE_LIMIT_FIELD
        );
    }

    private final ParsedValue defaultValue;
    private final String path;

    private RateLimitField(ParsedValue defaultValue, String path) {
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.path = Objects.requireNonNull(path);
    }

    @Override
    public void declareParserField(ConstructingObjectParser<ParsedValue[], DynamicParser.Context> parser) {
        // TODO pass the right root path value here
        parser.declareObject(optionalConstructorArg(), (p, c) -> Value.parse(p, null), new ParseField(RATE_LIMIT_FIELD));
    }

    @Override
    public ParsedValue defaultValue() {
        return defaultValue;
    }

    @Override
    public String schemaFieldName() {
        return RATE_LIMIT_FIELD;
    }
}
