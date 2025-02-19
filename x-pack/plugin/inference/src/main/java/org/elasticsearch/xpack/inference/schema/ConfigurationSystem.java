/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.inference.services.ConfigurationParseContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;
import static org.elasticsearch.xpack.inference.schema.RateLimitField.RATE_LIMIT_FIELD;

public record ConfigurationSystem(ServiceConfiguration serviceConfiguration) {
    private static final Logger logger = LogManager.getLogger(ConfigurationSystem.class);
    private static final ConfigurationSystem EMPTY_CONFIGURATION = new ConfigurationSystem(new ServiceConfiguration("", "", Map.of()));

    public record ServiceConfiguration(String schemaVersion, String serviceName, Map<TaskType, ConfigTaskType> taskTypes) {
        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<ServiceConfiguration, String> PARSER = new ConstructingObjectParser<>(
            ServiceConfiguration.class.getSimpleName(),
            false,
            (args) -> {
                var taskTypeList = (List<ConfigTaskType>) args[2];
                var taskTypes = new HashMap<TaskType, ConfigTaskType>();

                taskTypeList.forEach((configTaskType -> taskTypes.put(configTaskType.taskType, configTaskType)));

                return new ServiceConfiguration((String) args[0], (String) args[1], taskTypes);
            }
        );

        static {
            PARSER.declareString(constructorArg(), new ParseField("schema_version"));
            PARSER.declareString(constructorArg(), new ParseField("service"));
            PARSER.declareObjectArray(
                constructorArg(),
                (p, c) -> ConfigTaskType.PARSER.apply(p, c + ".task_types"),
                new ParseField("task_types")
            );
        }
    }

    public record ConfigTaskType(
        TaskType taskType,
        ConfigServiceSettings configServiceSettings,
        DynamicParser createEntityParser,
        DynamicParser persistentStateParser
    ) {

        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<ConfigTaskType, String> PARSER = new ConstructingObjectParser<>(
            ConfigTaskType.class.getSimpleName(),
            false,
            (args, c) -> {
                var currentPath = c + ".service_settings";
                var taskType = TaskType.fromStringOrStatusException((String) args[0]);
                var configServiceSettings = (ConfigServiceSettings) args[1];

                var serviceSettingLocations = createLocations(configServiceSettings, currentPath);

                List<FieldLocation> createEntityRequestFields = Objects.requireNonNullElse(
                    (List<FieldLocation>) args[2],
                    serviceSettingLocations
                );

                List<FieldLocation> persistentStateFields = Objects.requireNonNullElse(
                    (List<FieldLocation>) args[3],
                    serviceSettingLocations
                );

                return new ConfigTaskType(
                    taskType,
                    configServiceSettings,
                    new DynamicParser(
                        "create_entity_request",
                        createEntityRequestFields,
                        configServiceSettings,
                        ConfigurationParseContext.REQUEST
                    ),
                    new DynamicParser(
                        "persistent_state_request",
                        persistentStateFields,
                        configServiceSettings,
                        ConfigurationParseContext.REQUEST
                    )
                );
            }
        );

        private static List<FieldLocation> createLocations(ConfigServiceSettings configServiceSettings, String path) {
            return configServiceSettings.configFields.values()
                .stream()
                .map((configField) -> FieldLocation.of(configField.schemaFieldName(), path))
                .toList();
        }

        static {
            PARSER.declareString(constructorArg(), new ParseField("type"));
            PARSER.declareObject(
                constructorArg(),
                (p, c) -> ConfigServiceSettings.PARSER.apply(p, c + ".service_settings"),
                new ParseField("service_settings")
            );
            PARSER.declareObjectArray(
                optionalConstructorArg(),
                (p, c) -> FieldLocation.PARSER.apply(p, null),
                new ParseField("create_entity_request")
            );
            PARSER.declareObjectArray(
                optionalConstructorArg(),
                (p, c) -> FieldLocation.PARSER.apply(p, null),
                new ParseField("persistent_state")
            );
        }
    }

    /**
     *
     * @param configFields does not include the root path in the key, only the field name
     * @param serviceSettings
     */
    public record ConfigServiceSettings(Map<String, ConfigField> configFields, Map<String, ConfigField> serviceSettings) {
        @SuppressWarnings("unchecked")
        private static final ConstructingObjectParser<ConfigServiceSettings, String> PARSER = new ConstructingObjectParser<>(
            ConfigServiceSettings.class.getSimpleName(),
            false,
            (args, rootPath) -> {
                var serviceSettings = new HashMap<String, ConfigField>();
                var configFields = new HashMap<String, ConfigField>();
                var rateLimit = (RateLimitField) args[0];

                configFields.put(rootPath + "." + RATE_LIMIT_FIELD, rateLimit);
                serviceSettings.put(RATE_LIMIT_FIELD, rateLimit);

                var genericFields = (List<GenericField>) args[1];
                genericFields.forEach((genericField -> {
                    serviceSettings.put(rootPath + "." + genericField.schemaFieldName(), genericField);
                    configFields.put(genericField.schemaFieldName(), genericField);
                }));

                return new ConfigServiceSettings(configFields, serviceSettings);
            }
        );

        static {
            PARSER.declareObject(constructorArg(), RateLimitField::parseSchema, new ParseField(RATE_LIMIT_FIELD));
            PARSER.declareObjectArray(constructorArg(), (p, c) -> GenericField.parseSchema(p, c + ".fields"), new ParseField("fields"));
        }
    }

    public record FieldLocation(String name, String rootPath, String path) {

        public static FieldLocation of(String name, String rootPath) {
            return new FieldLocation(name, rootPath, rootPath + "." + name);
        }

        private static final ConstructingObjectParser<FieldLocation, Void> PARSER = new ConstructingObjectParser<>(
            FieldLocation.class.getSimpleName(),
            (args) -> FieldLocation.of((String) args[0], (String) args[1])
        );

        static {
            PARSER.declareString(constructorArg(), new ParseField("name"));
            PARSER.declareString(constructorArg(), new ParseField("path"));
        }
    }

    public static ConfigurationSystem load(String configuration) {
        try (
            XContentParser parser = XContentFactory.xContent(XContentType.YAML)
                .createParser(XContentParserConfiguration.EMPTY, configuration)
        ) {
            var thing = ServiceConfiguration.PARSER.apply(parser, "");

            return new ConfigurationSystem(thing);
        } catch (Exception e) {
            logger.warn("Failed to load configuration", e);
            return EMPTY_CONFIGURATION;
        }
    }
}
