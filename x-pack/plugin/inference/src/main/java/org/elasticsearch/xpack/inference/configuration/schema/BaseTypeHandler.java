/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import java.util.Objects;
import java.util.function.BiConsumer;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

public abstract class BaseTypeHandler<T> implements TypeHandler {
    private static final String ILLEGAL_ARG_MESSAGE = "Invalid value [%s], expected a %s but is a %s";

    private final String typeName;
    protected final HandlerConfiguration handlerConfiguration;
    protected final BiConsumer<Object, XContentSerializable> constructorArgCall;

    public BaseTypeHandler(String typeName, HandlerConfiguration handlerConfiguration) {
        this.typeName = Objects.requireNonNull(typeName);
        this.handlerConfiguration = Objects.requireNonNull(handlerConfiguration);
        constructorArgCall = this.handlerConfiguration.required() ? constructorArg() : optionalConstructorArg();
    }

    public SerializableValue<T> newSerializableValue(String persistentStateFieldName, Object value) {
        try {
            var validatedTypedValue = validate(value);
            return newValue(persistentStateFieldName, validatedTypedValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(ILLEGAL_ARG_MESSAGE, value, typeName, value.getClass().getSimpleName()));
        }
    }

    protected abstract SerializableValue<T> newValue(String persistentStateFieldName, T value);

    protected abstract T validate(Object value);

    public record HandlerConfiguration(GenericFieldParser.FieldType fieldType, String fieldName, boolean required) {}

    // TODO consider moving to a new class
    public static TypeHandler makeTypeHandler(HandlerConfiguration handlerConfiguration) {
        return switch (handlerConfiguration.fieldType()) {
            case STRING -> new StringHandler(handlerConfiguration);
            case INTEGER -> new IntegerHandler(handlerConfiguration);
        };
    }
}
