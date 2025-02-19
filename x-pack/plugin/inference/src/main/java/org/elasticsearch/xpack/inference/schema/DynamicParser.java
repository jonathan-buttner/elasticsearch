/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DynamicParser {
    public record Result(Map<String, ParsedValue> values) {
        private <T> T cast(ParsedValue value, Class<T> clazz) {
            if (value == null) {
                return null;
            }

            if (clazz.isInstance(value) == false) {
                // TODO I would expect this to be a programmatic error, maybe we should return a different exception?
                throw new IllegalArgumentException(
                    Strings.format("Unable to convert inference inputs type: [%s] to [%s]", value.getClass(), clazz)
                );
            }

            return clazz.cast(value);
        }

        public <T> T getOrThrow(String key, Class<T> clazz) {
            // TODO I would expect this to be a programmatic error, maybe we should return a different exception?
            var parsedValue = Objects.requireNonNull(values.get(key));

            return cast(parsedValue, clazz);
        }

        public <T> T get(String key, Class<T> clazz) {
            var parsedValue = values.get(key);

            return cast(parsedValue, clazz);
        }
    }

    public record Context(ConfigurationParseContext parseContext) {}

    private final ConstructingObjectParser<ParsedValue[], Context> parser;
    private final Context context;
    private final List<ConfigField> configFields = new ArrayList<>();

    public DynamicParser(
        String parserName,
        List<ConfigurationSystem.FieldLocation> fields,
        ConfigurationSystem.ConfigServiceSettings configServiceSettings,
        ConfigurationParseContext parseContext
    ) {
        Objects.requireNonNull(fields);
        context = new Context(parseContext);
        parser = new ConstructingObjectParser<>(
            parserName,
            parseContext == ConfigurationParseContext.PERSISTENT,
            (args) -> (ParsedValue[]) args
        );

        for (ConfigurationSystem.FieldLocation location : fields) {
            var field = configServiceSettings.serviceSettings().get(location.path());
            // TODO log an error if we weren't able to find it, this means there's a problem with the yaml configuration definition
            if (field != null) {
                configFields.add(field);
                field.declareParserField(parser);
            }
        }
    }

    public DynamicParser.Result parse(XContentParser xContentParser) throws IOException {
        var parsedValues = parser.apply(xContentParser, context);

        var resultMap = new HashMap<String, ParsedValue>();
        for (int i = 0; i < parsedValues.length; i++) {
            var defaultValue = configFields.get(i).defaultValue();
            var parsedValue = parsedValues[i];

            if (parsedValue != null) {
                resultMap.put(parsedValue.fieldName(), parsedValue);
            } else if (defaultValue != null) {
                resultMap.put(defaultValue.fieldName(), defaultValue);
            }
        }

        return new Result(resultMap);
    }
}
