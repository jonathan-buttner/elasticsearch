/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.registry;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.inference.services.elser.ElserMlNodeService;
import org.elasticsearch.xpack.inference.services.openai.embeddings.OpenAiServiceV1;

import static org.mockito.Mockito.mock;

public class ServiceRegistryTests extends ESTestCase {

    public void testGetService() {
        ServiceRegistry registry = new ServiceRegistry(mock(ElserMlNodeService.class), mock(OpenAiServiceV1.class));
        var service = registry.getService(ElserMlNodeService.NAME);
        assertTrue(service.isPresent());

        var openAiService = registry.getService(OpenAiServiceV1.NAME);
        assertTrue(openAiService.isPresent());
    }

    public void testGetUnknownService() {
        ServiceRegistry registry = new ServiceRegistry(mock(ElserMlNodeService.class), mock(OpenAiServiceV1.class));
        var service = registry.getService("foo");
        assertFalse(service.isPresent());
    }
}
