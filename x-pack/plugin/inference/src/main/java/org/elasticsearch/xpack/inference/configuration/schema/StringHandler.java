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

public class StringHandler extends BaseTypeHandler<String> {
    public StringHandler(HandlerConfiguration handlerConfiguration) {
        super("string", handlerConfiguration);
    }

    @Override
    protected SerializableValue<String> newValue(String persistentStateFieldName, String value) {
        return new StringValue(persistentStateFieldName, value);
    }

    @Override
    protected String validate(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }

        throw new IllegalArgumentException();
    }

    private static class StringValue extends SerializableValue<String> {
        StringValue(String persistentStateFieldName, String value) {
            super(persistentStateFieldName, value);
        }

        StringValue(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        public String readValue(StreamInput in) throws IOException {
            return in.readString();
        }

        @Override
        public void writeValue(StreamOutput out, String value) throws IOException {
            out.writeString(value);
        }
    }

    @Override
    public void declareParserField(ConstructingObjectParser<Object, Void> parser) {
        parser.declareField(
            constructorArgCall,
            (p, c) -> new StringValue(handlerConfiguration.fieldName(), p.text()),
            new ParseField(handlerConfiguration.fieldName()),
            ObjectParser.ValueType.STRING
        );
    }
}
