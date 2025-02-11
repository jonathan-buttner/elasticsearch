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
import java.util.function.BiConsumer;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;
import static org.elasticsearch.xpack.inference.configuration.schema.Utils.parseOptionalBoolean;

public record ConfigSettingsField(
    String name,
    ConfigSettingsField.FieldType fieldType,
    boolean trackOrigin,
    boolean required,
    XContentSerializable defaultValue,
    String path
) {
    enum FieldType implements Parser.ValueFactory {
        STRING {
            @Override
            public Parser.Value newValue(Object value) {
                if (value instanceof String valueString) {
                    return new Parser.Value(valueString);
                }

                throw new IllegalArgumentException(
                    Strings.format(ILLEGAL_ARG_MESSAGE, value, name().toLowerCase(Locale.ROOT), value.getClass().getSimpleName())
                );
            }

            @Override
            @SuppressWarnings("unchecked")
            public void addParserField(
                ConstructingObjectParser<Object, Void> parser,
                BiConsumer<?, ?> constructorArgCall,
                String fieldName
            ) {
                // TODO check and make sure this is ok
                parser.declareString((BiConsumer<Object, String>) constructorArgCall, new ParseField(fieldName));
            }
        },
        INTEGER {
            @Override
            public Parser.Value newValue(Object value) {
                if (value instanceof Integer valueInt) {
                    return new Parser.Value(valueInt);
                }

                throw new IllegalArgumentException(
                    Strings.format(ILLEGAL_ARG_MESSAGE, value, name().toLowerCase(Locale.ROOT), value.getClass().getSimpleName())
                );
            }

            @Override
            @SuppressWarnings("unchecked")
            public void addParserField(
                ConstructingObjectParser<Object, Void> parser,
                BiConsumer<?, ?> constructorArgCall,
                String fieldName
            ) {
                // TODO check and make sure this is ok
                parser.declareInt((BiConsumer<Object, Integer>) constructorArgCall, new ParseField(fieldName));
            }
        };

        private static final String ILLEGAL_ARG_MESSAGE = "Invalid value [%s], expected a %s but is a %s";

        private static ConfigSettingsField.FieldType fromString(String enumAsString) {
            if (enumAsString == null) {
                throw new IllegalArgumentException("The string value must not be null");
            }

            try {
                return valueOf(enumAsString.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                var enumValues = ConfigSettingsField.FieldType.values().clone();
                Arrays.sort(enumValues);
                throw new IllegalArgumentException(
                    Strings.format("Invalid string value [%s], must be one of %s", enumAsString, enumValues),
                    e
                );
            }
        }
    }

    private static final ConstructingObjectParser<ConfigSettingsField, String> PARSER = new ConstructingObjectParser<>(
        ConfigSettingsField.class.getSimpleName(),
        false,
        (args, rootPath) -> {
            var type = ConfigSettingsField.FieldType.fromString((String) args[1]);
            var defaultValue = newTypedDefault(args[4], type);

            return new ConfigSettingsField(
                (String) args[0],
                type,
                (boolean) args[2],
                parseOptionalBoolean((Boolean) args[3]),
                defaultValue,
                rootPath
            );
        }
    );

    private static XContentSerializable newTypedDefault(@Nullable Object defaultValue, ConfigSettingsField.FieldType fieldType) {
        if (defaultValue == null) {
            return NoopXContentSerializer.INSTANCE;
        }

        return fieldType.newValue(defaultValue);
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
        PARSER.declareField(optionalConstructorArg(), (p, c) -> parseValueByToken(p), new ParseField("default"), valueType());
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

    public static ObjectParser.ValueType valueType() {
        return ObjectParser.ValueType.VALUE;
    }

    public void addParserField(ConstructingObjectParser<Object, Void> parser, BiConsumer<?, ?> constructorArgCall) {
        fieldType.addParserField(parser, constructorArgCall, name);
    }
}
