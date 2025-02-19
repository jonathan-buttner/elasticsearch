/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

public class OpenAiConfigurations2 {

    public static final String CONFIGURATION = """
        ---
        schema_version: 1.0.0
        service: openai2
        task_types:
          - type: text_embedding
            service_settings:
              rate_limit:
                requests_per_minute:
                  default: 3000
              fields:
                - name: dimensions
                  type: integer
                  # a user can set this in the request, or it can be set automatically by the validation request
                  # this translates to the "dimensions_set_by_user" flag we use internally
                  track_origin: true
        """;

    private OpenAiConfigurations2() {}
}
