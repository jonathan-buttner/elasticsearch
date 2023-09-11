/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.registry;

import org.elasticsearch.xpack.inference.services.InferenceService;
import org.elasticsearch.xpack.inference.services.elser.ElserMlNodeService;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiServiceV1;

import java.util.Optional;

public class ServiceRegistry {

    ElserMlNodeService elserService;
    // TODO maybe we need a factory?
    OpenAiServiceV1 openAiServiceV1;

    public ServiceRegistry(ElserMlNodeService elserService, OpenAiServiceV1 openAiServiceV1) {
        this.elserService = elserService;
        this.openAiServiceV1 = openAiServiceV1;
    }

    public Optional<InferenceService> getService(String name) {
        if (name.equals(ElserMlNodeService.NAME)) {
            return Optional.of(elserService);
        } else if (name.equals(OpenAiServiceV1.NAME)) {
            return Optional.of(openAiServiceV1);
        }

        return Optional.empty();
    }

}
