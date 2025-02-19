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
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;

import java.io.IOException;

import static org.elasticsearch.xpack.inference.services.ConfigurationParseContext.isRequestContext;

public class IntegerHandler extends BaseTypeHandler<Integer> {
    public IntegerHandler(HandlerConfiguration handlerConfiguration) {
        super("integer", handlerConfiguration);
    }

    @Override
    protected SerializableValue<Integer> newValue(String persistentStateFieldName, Integer value) {
        return new IntegerValue(persistentStateFieldName, value);
    }

    @Override
    protected Integer validate(Object value) {
        if (value instanceof Integer intValue) {
            return intValue;
        }

        throw new IllegalArgumentException();
    }

    public static class IntegerValue extends SerializableValue<Integer> {

        public IntegerValue(String persistentStateFieldName, Integer value, boolean trackOrigin) {
            super(persistentStateFieldName, value, trackOrigin);
        }

        public IntegerValue(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        protected Integer readValue(StreamInput in) throws IOException {
            return in.readVInt();
        }

        @Override
        protected void writeValue(StreamOutput out, Integer value) throws IOException {
            out.writeVInt(value);
        }
    }

    @Override
    public void declareParserField(ConstructingObjectParser<ParsedValue[], DynamicParser.Context> parser) {
        parser.declareField(
            constructorArgCall,
            (p, c) -> new IntegerValue(handlerConfiguration.fieldName(), p.intValue(), isRequestContext(c.parseContext())),
            new ParseField(handlerConfiguration.fieldName()),
            ObjectParser.ValueType.INT
        );
    }
}
