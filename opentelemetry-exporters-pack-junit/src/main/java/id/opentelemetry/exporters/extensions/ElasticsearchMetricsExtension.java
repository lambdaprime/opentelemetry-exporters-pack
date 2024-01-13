/*
 * Copyright 2023 opentelemetry-exporters-pack project
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
package id.opentelemetry.exporters.extensions;

import id.opentelemetry.exporters.ElasticSearchMetricExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Configures {@link ElasticSearchMetricExporter}
 *
 * <p>Elasticsearc URL must be set via metrics.elastic.url system property OR METRICS_ELASTIC_URL
 * environment variable
 *
 * @author lambdaprime intid@protonmail.com
 */
public class ElasticsearchMetricsExtension
        implements BeforeAllCallback, AfterAllCallback, ExtensionContext.Store.CloseableResource {

    private SdkMeterProvider sdkMeterProvider;

    @Override
    public void close() {
        sdkMeterProvider.shutdown().join(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        close();
    }

    @Override
    public void beforeAll(ExtensionContext arg0) throws Exception {
        GlobalOpenTelemetry.resetForTest();
        var exporter =
                new ElasticSearchMetricExporter(
                        URI.create(
                                Optional.ofNullable(System.getProperty("metrics.elastic.url"))
                                        .or(
                                                () ->
                                                        Optional.ofNullable(
                                                                System.getenv(
                                                                        "METRICS_ELASTIC_URL")))
                                        .orElseThrow(
                                                () ->
                                                        new RuntimeException(
                                                                "No system property"
                                                                        + " metrics.elastic.url OR"
                                                                        + " METRICS_ELASTIC_URL env"
                                                                        + " variable is present"))),
                        Optional.empty(),
                        true);
        var metricReader =
                PeriodicMetricReader.builder(exporter).setInterval(Duration.ofSeconds(3)).build();
        sdkMeterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProvider).buildAndRegisterGlobal();
    }
}
