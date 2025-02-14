/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.common.Strings;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentParseException;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;
import static org.elasticsearch.xpack.inference.configuration.schema.Utils.parseOptionalBoolean;

public class GenericField implements ConfigField {

    enum Type {
        STRING,
        INTEGER;

        private static Type fromString(String enumAsString) {
            if (enumAsString == null) {
                throw new IllegalArgumentException("The string value must not be null");
            }

            try {
                return valueOf(enumAsString.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                var enumValues = Type.values().clone();
                Arrays.sort(enumValues);
                throw new IllegalArgumentException(
                    Strings.format("Invalid string value [%s], must be one of %s", enumAsString, enumValues),
                    e
                );
            }
        }
    }

    public record Schema(
        String name,
        Type type,
        boolean trackOrigin,
        boolean required,
        XContentSerializable defaultValue,
        String path,
        TypeHandler typeHandler
    ) {

        private static final ConstructingObjectParser<Schema, String> PARSER = new ConstructingObjectParser<>(
            GenericField.class.getSimpleName(),
            false,
            (args, rootPath) -> {
                var fieldName = (String) args[0];
                var type = Type.fromString((String) args[1]);
                var required = parseOptionalBoolean((Boolean) args[3]);
                var typeHandler = BaseTypeHandler.makeTypeHandler(new BaseTypeHandler.HandlerConfiguration(type, fieldName, required));
                var defaultValue = newTypedDefault(fieldName, typeHandler, args[4]);

                return new Schema(fieldName, type, (boolean) args[2], required, defaultValue, rootPath + "." + fieldName, typeHandler);
            }
        );

        private static XContentSerializable newTypedDefault(String fieldName, TypeHandler typeHandler, @Nullable Object defaultValue) {
            if (defaultValue == null) {
                return NoopXContentSerializer.INSTANCE;
            }

            return typeHandler.newSerializableValue(fieldName, defaultValue);
        }

        static {
            PARSER.declareString(constructorArg(), new ParseField("name"));
            PARSER.declareString(constructorArg(), new ParseField("type"));
            /*
             * Dictates whether we should track how the field is set, whether in a request from a user or dynamically
             * in code. An example of this is the dimensions field used in OpenAI text embeddings.
             */
            PARSER.declareBoolean(constructorArg(), new ParseField("track_origin"));
            PARSER.declareBoolean(optionalConstructorArg(), new ParseField("required"));
            PARSER.declareField(
                optionalConstructorArg(),
                (p, c) -> parseValueByToken(p),
                new ParseField("default"),
                ObjectParser.ValueType.VALUE
            );
        }

        private static Object parseValueByToken(XContentParser parser) throws IOException {
            var token = parser.currentToken();
            if (token == XContentParser.Token.VALUE_STRING) {
                return parser.text();
            } else if (token == XContentParser.Token.VALUE_NUMBER) {
                return parser.intValue();
            }

            throw new XContentParseException("Unsupported token [" + token + "]");
        }

    }

    public static GenericField parseSchema(XContentParser parser, String rootPath) throws IOException {
        var field = Schema.PARSER.apply(parser, rootPath);
        return new GenericField(field);
    }

    private final Schema schema;

    private GenericField(Schema schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    @Override
    public void declareParserField(ConstructingObjectParser<Object, Void> parser) {
        schema.typeHandler.declareParserField(parser);
    }

    @Override
    public XContentSerializable defaultValue() {
        return schema.defaultValue();
    }

    @Override
    public String schemaFieldName() {
        return schema.name();
    }
}
