/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public interface ParsedValue extends Writeable {

    String fieldName();

    /**
     * This method handles serializing to {@link org.elasticsearch.xcontent.XContent} using the declared field name
     * from the configuration. This is useful for serializing the field to persistent state where the field name should match
     * the value declared in the configuration file.
     */
    XContentBuilder toXContentFragment(XContentBuilder builder, ToXContent.Params params) throws IOException;

    /**
     * Serializes fields that should be hidden from users and requests. These are fields that should only be persisted in the index but
     * never returned to the user or an external service.
     */
    XContentBuilder toXContentFragmentOfHiddenFields(XContentBuilder builder, ToXContent.Params params) throws IOException;

    /**
     * This method handles serializing to {@link org.elasticsearch.xcontent.XContent} using a new field name
     * provided. This is particularly useful for serializing for the outgoing request to the external service where
     * we don't know the field name yet while parsing certain aspects of the configuration file.
     */
    XContentBuilder toXContentFragmentWithName(XContentBuilder builder, ToXContent.Params params, String fieldName) throws IOException;
}
