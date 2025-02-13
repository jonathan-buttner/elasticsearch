/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * @param persistentStateFieldName the field name used to parse the create inference entity request and to persist the values to ES
 * @param value the value parsed from a request or persistent state that needs to be serialized
 */
public record SerializableValue(String persistentStateFieldName, Object value) implements XContentSerializable {

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return toXContentWithName(builder, params, persistentStateFieldName);
    }

    @Override
    public XContentBuilder toXContentWithName(XContentBuilder builder, ToXContent.Params params, String fieldName) throws IOException {
        return builder.field(fieldName, value);
    }
}
