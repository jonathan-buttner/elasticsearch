/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;

public class StringHandler extends BaseTypeHandler {
    public StringHandler(HandlerConfiguration handlerConfiguration) {
        super("string", handlerConfiguration);
    }

    @Override
    protected void validate(Object value) {
        if (value instanceof String == false) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void declareParserField(ConstructingObjectParser<Object, Void> parser) {
        parser.declareField(
            constructorArgCall,
            (p, c) -> new SerializableValue(handlerConfiguration.fieldName(), p.text()),
            new ParseField(handlerConfiguration.fieldName()),
            ObjectParser.ValueType.STRING
        );
    }
}
