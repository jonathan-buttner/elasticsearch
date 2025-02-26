/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;
import org.elasticsearch.xpack.inference.services.settings.FilteredXContentObject;

import java.io.IOException;
import java.util.Objects;

import static org.elasticsearch.xpack.inference.services.ConfigurationParseContext.isRequestContext;

public abstract class SerializableValue<T> extends FilteredXContentObject implements ParsedValue {

    private final String persistentStateFieldName;
    private final T value;
    private final boolean setInCreationRequest;

    /**
     * @param persistentStateFieldName the field name used to parse the create inference entity request and to persist the values to ES
     * @param value the value parsed from a request or persistent state that needs to be serialized
     * @param setInCreationRequest specifies when this value was set
     */
    SerializableValue(String persistentStateFieldName, T value, boolean setInCreationRequest) {
        this.persistentStateFieldName = Objects.requireNonNull(persistentStateFieldName);
        this.value = Objects.requireNonNull(value);
        this.setInCreationRequest = setInCreationRequest;
    }

    /**
     * @param persistentStateFieldName the field name used to parse the create inference entity request and to persist the values to ES
     * @param value the value parsed from a request or persistent state that needs to be serialized
     * @param parseContext specifies when this value was set
     */
    SerializableValue(String persistentStateFieldName, T value, ConfigurationParseContext parseContext) {
        this(persistentStateFieldName, value, isRequestContext(parseContext));
    }

    SerializableValue(StreamInput in) throws IOException {
        persistentStateFieldName = in.readString();
        value = readValue(in);
        setInCreationRequest = in.readBoolean();
    }

    protected abstract T readValue(StreamInput in) throws IOException;

    protected abstract void writeValue(StreamOutput out, T value) throws IOException;

    @Override
    public String fieldName() {
        return persistentStateFieldName;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(persistentStateFieldName);
        writeValue(out, value);
        out.writeBoolean(setInCreationRequest);
    }

    @Override
    public XContentBuilder toXContentFragment(XContentBuilder builder, ToXContent.Params params) throws IOException {
        toXContentFragmentWithName(builder, params, persistentStateFieldName);
        return builder;
    }

    @Override
    public XContentBuilder toXContentFragmentWithName(XContentBuilder builder, ToXContent.Params params, String fieldName)
        throws IOException {
        builder.field(fieldName, value);
        return builder;
    }

    @Override
    public XContentBuilder toXContentFragmentOfHiddenFields(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return builder.field(persistentStateFieldName + "_set_in_creation_request", setInCreationRequest);
    }

    @Override
    protected XContentBuilder toXContentFragmentOfExposedFields(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return toXContentFragmentWithName(builder, params, persistentStateFieldName);
    }

    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SerializableValue<?> that = (SerializableValue<?>) o;
        return Objects.equals(persistentStateFieldName, that.persistentStateFieldName) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persistentStateFieldName, value);
    }
}
