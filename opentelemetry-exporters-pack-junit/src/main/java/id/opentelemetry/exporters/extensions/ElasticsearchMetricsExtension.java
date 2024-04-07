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

import id.opentelemetry.exporters.ElasticsearchMetricExporter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Configures {@link ElasticsearchMetricExporter}
 *
 * <p>Elasticsearc URL must be set via metrics.elastic.url system property OR METRICS_ELASTIC_URL
 * environment variable
 *
 * @author lambdaprime intid@protonmail.com
 */
public class ElasticsearchMetricsExtension implements BeforeAllCallback, AfterAllCallback {
    private static final Logger logger =
            Logger.getLogger(ElasticsearchMetricsExtension.class.getName());
    private Duration timeout = Duration.ofSeconds(4);
    private Optional<SdkMeterProvider> sdkMeterProvider = Optional.empty();

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        sdkMeterProvider.ifPresent(
                provider -> {
                    provider.shutdown().join(timeout.toMillis(), TimeUnit.MILLISECONDS);
                });
    }

    @Override
    public void beforeAll(ExtensionContext arg0) throws Exception {
        GlobalOpenTelemetry.resetForTest();
        var url =
                Optional.ofNullable(System.getProperty("metrics.elastic.url"))
                        .or(() -> Optional.ofNullable(System.getenv("METRICS_ELASTIC_URL")))
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "No system property metrics.elastic.url OR"
                                                        + " METRICS_ELASTIC_URL env variable is"
                                                        + " present. To disable metrics set any of"
                                                        + " those to blank string"));
        if (url.isBlank()) {
            logger.warning("Metrics are ignored because metrics url is blank");
            return;
        }
        var exporter =
                new ElasticsearchMetricExporter(URI.create(url), Optional.empty(), timeout, true);
        var metricReader =
                PeriodicMetricReader.builder(exporter).setInterval(Duration.ofSeconds(3)).build();
        var provider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        OpenTelemetrySdk.builder().setMeterProvider(provider).buildAndRegisterGlobal();
        sdkMeterProvider = Optional.of(provider);
    }
}
