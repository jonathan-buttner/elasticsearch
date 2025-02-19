/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.xcontent.ConstructingObjectParser;

import java.util.Objects;

// TODO do we really need this class?
public class SettingsFieldParser {
    private final GenericField genericField;

    public SettingsFieldParser(GenericField genericField) {
        this.genericField = Objects.requireNonNull(genericField);
    }

    public void apply(ConstructingObjectParser<Object, Void> parser) {
        genericField.addParserField(parser);
    }
}
