/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;

import java.io.IOException;

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

    private static class IntegerValue extends SerializableValue<Integer> {

        IntegerValue(String persistentStateFieldName, Integer value) {
            super(persistentStateFieldName, value);
        }

        @Override
        public Integer readValue(StreamInput in) throws IOException {
            return in.readVInt();
        }

        @Override
        public void writeValue(StreamOutput out, Integer value) throws IOException {
            out.writeVInt(value);
        }
    }

    @Override
    public void declareParserField(ConstructingObjectParser<Object, Void> parser) {
        parser.declareField(
            constructorArgCall,
            (p, c) -> new IntegerValue(handlerConfiguration.fieldName(), p.intValue()),
            new ParseField(handlerConfiguration.fieldName()),
            ObjectParser.ValueType.INT
        );
    }
}
