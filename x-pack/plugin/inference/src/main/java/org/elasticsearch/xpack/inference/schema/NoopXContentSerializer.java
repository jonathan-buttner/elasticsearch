/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class NoopXContentSerializer implements ParsedValue {
    public static final NoopXContentSerializer INSTANCE = new NoopXContentSerializer();

    @Override
    public XContentBuilder toXContentFragment(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return builder;
    }

    @Override
    public XContentBuilder toXContentFragmentWithName(XContentBuilder builder, ToXContent.Params params, String fieldName)
        throws IOException {
        return builder;
    }
}
