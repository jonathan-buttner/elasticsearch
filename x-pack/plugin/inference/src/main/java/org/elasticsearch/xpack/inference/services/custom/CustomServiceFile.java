/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.custom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.Strings;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.external.http.sender.HttpRequestSender;
import org.elasticsearch.xpack.inference.services.ServiceComponents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CustomServiceFile extends CustomService {

    Logger logger = LogManager.getLogger(CustomServiceFile.class);

    private static final String CONFIG = """
        {
                "headers":{
                    "Authorization": "Bearer ${api_key}",
                    "Content-Type": "application/json;charset=utf-8"
                },
                "url": "https://api.mistral.ai/v1/embeddings",
                "request": {
                             "content": "{\\"input\\": ${input}, \\"model\\": ${model_id}}"
                 },
             "response": {
                 "json_parser": {
                     "text_embeddings": "$.data[*].embedding[*]"
                 },
                 "error_parser": {
                     "path": "$.error.message"
                 }
             }
        }
        """;

    public CustomServiceFile(HttpRequestSender.Factory factory, ServiceComponents serviceComponents) {
        super(factory, serviceComponents);

    }

    @Override
    public String name() {
        return "mistral";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void parseRequestConfig(
        String inferenceEntityId,
        TaskType taskType,
        Map<String, Object> config,
        ActionListener<Model> parsedModelListener
    ) {
        try {
            Map<String, Object> parsedConfig = parseConfig();
            var mergedMap = new HashMap<>(config);

            ((Map<String, Object>)mergedMap.get("service_settings")).putAll(parsedConfig);
//            Stream.of(parsedConfig, config)
//                .flatMap(map -> map.entrySet().stream())
//                .collect(Collectors.toMap(
//                    Map.Entry::getKey,
//                    e -> new ArrayList<>(e.getValue()),
//                    (left, right) -> {left.addAll(right); return left;}
//                ));

            logger.warn(Strings.format("merged map: %s", mergedMap));
            super.parseRequestConfig(inferenceEntityId, taskType, mergedMap, parsedModelListener);
        } catch (Exception e) {
            logger.warn(Strings.format("Got an exception: %s", e.getMessage()), e);
            parsedModelListener.onFailure(e);
        }
    }



//    @Override
//    @SuppressWarnings("unchecked")
//    public CustomModel parsePersistedConfigWithSecrets(
//        String inferenceEntityId,
//        TaskType taskType,
//        Map<String, Object> config,
//        Map<String, Object> secrets
//    ) {
//        try {
//            Map<String, Object> parsedConfig = parseConfig();
//            var mergedMap = new HashMap<>(config);
//
//            ((Map<String, Object>)mergedMap.get("service_settings")).putAll(parsedConfig);
////            Stream.of(parsedConfig, config)
////                .flatMap(map -> map.entrySet().stream())
////                .collect(Collectors.toMap(
////                    Map.Entry::getKey,
////                    e -> new ArrayList<>(e.getValue()),
////                    (left, right) -> {left.addAll(right); return left;}
////                ));
//
//            logger.warn(Strings.format("merged map: %s", mergedMap));
//            return super.parsePersistedConfigWithSecrets(inferenceEntityId, taskType, mergedMap, secrets);
//        } catch (IOException e) {
//            logger.warn(Strings.format("Got an exception: %s", e.getMessage()), e);
//            throw new RuntimeException("Failed to parse config", e);
//        }
//    }

    private static Map<String, Object> parseConfig() throws IOException {
        try (
            var p = XContentFactory.xContent(XContentType.JSON)
                .createParser(XContentParserConfiguration.EMPTY, CONFIG.getBytes(StandardCharsets.UTF_8))
        ) {
            return p.map();
        }
    }

}
