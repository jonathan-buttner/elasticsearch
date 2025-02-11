/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.xcontent.ConstructingObjectParser;

import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

public class SettingsFieldParser {
    private final ConfigSettingsField configSettingsField;

    public SettingsFieldParser(ConfigSettingsField configSettingsField) {
        this.configSettingsField = Objects.requireNonNull(configSettingsField);
    }

    public void apply(ConstructingObjectParser<Object, Void> parser) {
        var argType = configSettingsField.required() ? constructorArg() : optionalConstructorArg();
        configSettingsField.addParserField(parser, argType);
    }
}
