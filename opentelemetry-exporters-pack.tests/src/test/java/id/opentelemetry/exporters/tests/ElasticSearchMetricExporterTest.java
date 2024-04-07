/*
 * Copyright 2022 opentelemetry-exporters-pack project
 * 
 * Website: https://github.com/lambdaprime/opentelemetry-exporters-pack
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package id.opentelemetry.exporters.tests;

import id.opentelemetry.exporters.ElasticsearchMetricExporter;
import id.xfunctiontests.XAsserts;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData;
import io.opentelemetry.sdk.resources.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author lambdaprime intid@protonmail.com
 */
public class ElasticSearchMetricExporterTest {

    @Test
    public void test() {
        var out = new ArrayList<String>();
        Function<String, CompletableResultCode> sendMetrics =
                json -> {
                    out.add(json);
                    return CompletableResultCode.ofSuccess();
                };
        try (var exporter =
                new ElasticsearchMetricExporter(URI.create("http://a/b/c"), sendMetrics)) {
            exporter.export(
                    List.of(
                            ImmutableMetricData.createLongSum(
                                    Resource.getDefault(),
                                    InstrumentationScopeInfo.create("scope"),
                                    "longSum",
                                    "",
                                    "ms",
                                    ImmutableSumData.create(
                                            false,
                                            AggregationTemporality.DELTA,
                                            List.of(
                                                    ImmutableLongPointData.create(
                                                            0,
                                                            0,
                                                            Attributes.builder().build(),
                                                            0)))),
                            ImmutableMetricData.createDoubleHistogram(
                                    Resource.getDefault(),
                                    InstrumentationScopeInfo.create("scope"),
                                    "hist1",
                                    "",
                                    "ms",
                                    ImmutableHistogramData.create(
                                            AggregationTemporality.DELTA,
                                            List.of(
                                                    ImmutableHistogramPointData.create(
                                                            1,
                                                            2,
                                                            Attributes.builder().build(),
                                                            4,
                                                            false,
                                                            5,
                                                            false,
                                                            6,
                                                            List.of(1., 5.),
                                                            List.of(7L, 8L, 9L)),
                                                    ImmutableHistogramPointData.create(
                                                            3,
                                                            4,
                                                            Attributes.builder().build(),
                                                            5,
                                                            false,
                                                            6,
                                                            false,
                                                            7,
                                                            List.of(1., 5.),
                                                            List.of(17L, 18L, 19L)))))));
        }
        Assertions.assertEquals(1, out.size());
        XAsserts.assertEquals(getClass(), "requests", out.toString());
    }
}
