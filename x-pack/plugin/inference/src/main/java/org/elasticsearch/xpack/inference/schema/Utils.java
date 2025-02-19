/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.schema;

import org.elasticsearch.core.Nullable;

public class Utils {

    /**
     * Returns true if the value is not null and true, false otherwise.
     */
    public static boolean parseOptionalBoolean(@Nullable Boolean value) {
        return value != null && value;
    }

    private Utils() {}
}
