/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.configuration.schema;

import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;

public record Parser(List<ConfigSettingsField> fields) {

    // TODO move these somewhere else
    // TODO rename this?
    interface ValueFactory {
        Value newValue(Object value);

        // TODO maybe move this to its own interface
        void addParserField(ConstructingObjectParser<Object, Void> parser, BiConsumer<?, ?> constructorArgCall, String fieldName);
    }

    /**
     * This provides a way to encapsulate a default value and defer defining how to serialize it to
     * {@link org.elasticsearch.xcontent.XContent}. The {@link #toXContent(XContentBuilder, ToXContent.Params, String)} takes a field name
     * since we won't know the appropriate field until after parsing the rest of the configuration file.
     * This also provides a hashCode value for us for the rate limit grouping requirements.
     * @param value the value parsed from the configuration
     */
    public record Value(Object value) implements XContentSerializable {
        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params, String fieldName) throws IOException {
            return builder.field(fieldName, value);
        }
    }
}
