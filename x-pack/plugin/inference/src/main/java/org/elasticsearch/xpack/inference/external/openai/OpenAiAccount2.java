/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.openai;

import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.core.Nullable;

import java.net.URI;

public record OpenAiAccount2(URI uri, @Nullable String organizationId, SecureString apiKey) {

}
