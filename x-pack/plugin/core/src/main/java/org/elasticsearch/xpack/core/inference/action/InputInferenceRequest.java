/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.inference.action;

import org.elasticsearch.core.Nullable;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

public record InputInferenceRequest(List<String> input, @Nullable Map<String, Object> taskSettings, @Nullable String query) {

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<InputInferenceRequest, Void> PARSER = new ConstructingObjectParser<>(
        InputInferenceRequest.class.getSimpleName(),
        args -> new InputInferenceRequest((List<String>) args[0], (Map<String, Object>) args[1], (String) args[2])
    );

    static {
        PARSER.declareStringArray(constructorArg(), new ParseField("input"));
        PARSER.declareObject(optionalConstructorArg(), (p, c) -> p.mapOrdered(), new ParseField("task_settings"));
        PARSER.declareString(optionalConstructorArg(), new ParseField("query"));
        // TODO I think this is a mistake, should we be parsing the timeout from the body?
        // I suppose this is a breaking change?
        // PARSER.declareString(optionalConstructorArg(), new ParseField("timeout"));
    }

    public InputInferenceRequest {
        Objects.requireNonNull(input);
    }
}
