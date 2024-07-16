/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.inference;

import org.elasticsearch.TransportVersion;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.ml.AbstractBWCWireSerializationTestCase;

import java.io.IOException;

import static org.hamcrest.Matchers.is;

public class InferenceRequestStatsTests extends AbstractBWCWireSerializationTestCase<InferenceRequestStats> {

    public static InferenceRequestStats createRandom() {
        var modelId = randomBoolean() ? randomAlphaOfLength(10) : null;

        return new InferenceRequestStats(randomAlphaOfLength(10), randomFrom(TaskType.values()), modelId, randomInt());
    }

    public void testToXContent_DoesNotWriteModelId_WhenItIsNull() throws IOException {
        var stats = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, null, 1);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        stats.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, is("""
            {"service":"service","task_type":"text_embedding","count":1}"""));
    }

    public void testToXContent_WritesModelId_WhenItIsDefined() throws IOException {
        var stats = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", 2);

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        stats.toXContent(builder, null);
        String xContentResult = Strings.toString(builder);

        assertThat(xContentResult, is("""
            {"service":"service","task_type":"text_embedding","count":2,"model_id":"model_id"}"""));
    }

    public void testMerge_SumsCounts() {
        var stats1 = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", 1);
        var stats2 = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", 2);

        assertThat(
            InferenceRequestStats.merge(stats1, stats2),
            is(new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", 3))
        );
    }

    public void testMerge_ThrowsAssertionExceptionWhenFieldsAreDifferent() {
        // service names don't match
        {
            var stats1 = new InferenceRequestStats("service1", TaskType.TEXT_EMBEDDING, "model_id", 1);
            var stats2 = new InferenceRequestStats("service2", TaskType.TEXT_EMBEDDING, "model_id", 2);

            var thrownException = expectThrows(AssertionError.class, () -> InferenceRequestStats.merge(stats1, stats2));

            assertThat(thrownException.getMessage(), is("services do not match"));
        }
        // task types don't match
        {
            var stats1 = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id", 1);
            var stats2 = new InferenceRequestStats("service", TaskType.RERANK, "model_id", 2);

            var thrownException = expectThrows(AssertionError.class, () -> InferenceRequestStats.merge(stats1, stats2));

            assertThat(thrownException.getMessage(), is("task types do not match"));
        }
        // model ids don't match
        {
            var stats1 = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id1", 1);
            var stats2 = new InferenceRequestStats("service", TaskType.TEXT_EMBEDDING, "model_id2", 2);

            var thrownException = expectThrows(AssertionError.class, () -> InferenceRequestStats.merge(stats1, stats2));

            assertThat(thrownException.getMessage(), is("model ids do not match"));
        }
    }

    @Override
    protected InferenceRequestStats mutateInstanceForVersion(InferenceRequestStats instance, TransportVersion version) {
        return instance;
    }

    @Override
    protected Writeable.Reader<InferenceRequestStats> instanceReader() {
        return InferenceRequestStats::new;
    }

    @Override
    protected InferenceRequestStats createTestInstance() {
        return createRandom();
    }

    @Override
    protected InferenceRequestStats mutateInstance(InferenceRequestStats instance) throws IOException {
        return randomValueOtherThan(instance, this::createTestInstance);
    }
}
