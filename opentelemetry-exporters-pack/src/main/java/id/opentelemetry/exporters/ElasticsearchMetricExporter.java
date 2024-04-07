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
package id.opentelemetry.exporters;

import id.xfunction.XJsonStringBuilder;
import id.xfunction.logging.XLogger;
import id.xfunction.net.HttpClientBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Push Metric Exporter to <a href="https://www.elastic.co/elasticsearch/">ElasticSearch</a>.
 *
 * <p>It is based on Java {@link HttpClient} and sends all metrics using <a
 * href="https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html">ElastiSearch
 * Bulk API</a>
 *
 * <p>Measurements are aggregated with {@link AggregationTemporality#DELTA}
 *
 * <p>Currently supporter metric types:
 *
 * <ul>
 *   <li>{@link MetricDataType#LONG_SUM}
 *   <li>{@link MetricDataType#HISTOGRAM}
 * </ul>
 *
 * <p>Export schema is based on field names given in {@link ExportSchema}.
 *
 * <p>If ElasticSearch has self-signed SSL certificates by default Java will not allow to connect to
 * it. Use {@link #ElasticsearchMetricExporter(URI, Optional, Duration, boolean)} with insecure set
 * to "true".
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * var metricReader = PeriodicMetricReader
 *   .builder(new ElasticsearchMetricExporter(
 *     URI.create("https://localhost:9200/mymetrics"), Optional.of(new Credentials("elastic", "pass"))))
 *   .setInterval(Duration.ofSeconds(3))
 *   .build();
 * var sdkMeterProvider = SdkMeterProvider.builder()
 *   .registerMetricReader(metricReader).build();
 * OpenTelemetrySdk.builder()
 *   .setMeterProvider(sdkMeterProvider)
 *   .buildAndRegisterGlobal();
 * }</pre>
 *
 * @see <a
 *     href="https://opentelemetry.io/docs/reference/specification/metrics/sdk/#push-metric-exporter">Push
 *     Metric Exporter</a>
 * @author lambdaprime intid@protonmail.com
 */
public final class ElasticsearchMetricExporter implements MetricExporter {
    private static final XLogger LOGGER =
            XLogger.getLogger(ElasticsearchMetricExporter.class.getName());
    private static final Object CREATE_JSON =
            """
                    { "create": { } }
                    """.trim();

    public record Credentials(String user, String password) {
        public static Optional<Credentials> fromUri(URI uri) {
            var userInfo = uri.getUserInfo();
            if (userInfo == null) return Optional.empty();
            var a = userInfo.split(":");
            return Optional.of(new Credentials(a[0], a[1]));
        }
    }

    private HttpClient client;
    private URI addBulkApi;
    private Duration timeout = Duration.ZERO;
    private Function<String, CompletableResultCode> sendMetrics = this::sendMetrics;

    /**
     * @param elasticSearch URI to the ElasticSearch index where metrics will be exported.
     *     Credentials can be part of the URL. Example http://user:password@localhost:9200/customers
     *     They are optional to allow anonymous access if it is enabled on ElasticSearch.
     */
    public ElasticsearchMetricExporter(URI elasticSearch) {
        this(elasticSearch, Optional.empty(), Duration.ZERO, false);
    }

    /**
     * @param credentials ElasticSearch user and password. They are optional to allow anonymous
     *     access if it is enabled on ElasticSearch.
     */
    public ElasticsearchMetricExporter(URI elasticSearch, Optional<Credentials> credentials) {
        this(elasticSearch, credentials, Duration.ZERO, false);
    }

    /**
     * @param insecure allow connections to ElasticSearch with self-signed SSL certificates
     */
    public ElasticsearchMetricExporter(
            URI elasticSearch,
            Optional<Credentials> credentials,
            Duration timeout,
            boolean insecure) {
        this.timeout = timeout;
        if (insecure) {
            LOGGER.warning("Insecure connetions to ElasticSearch are enabled");
        }
        this.addBulkApi = URI.create(elasticSearch.toASCIIString() + "/_bulk");
        var builder = HttpClient.newBuilder();
        if (credentials.isEmpty() && elasticSearch.getUserInfo() != null) {
            credentials = Credentials.fromUri(elasticSearch);
        }
        if (credentials.isPresent()) {
            var creds = credentials.orElseThrow();
            builder.authenticator(
                    new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(
                                    creds.user, creds.password.toCharArray());
                        }
                    });
        }

        if (insecure) builder = new HttpClientBuilder(builder).insecure().get();
        if (timeout != Duration.ZERO) builder.connectTimeout(timeout);
        client = builder.build();
    }

    /**
     * @hidden for tests
     */
    @SuppressWarnings("exports")
    public ElasticsearchMetricExporter(
            URI addBulkApi, Function<String, CompletableResultCode> sendMetrics) {
        this.addBulkApi = addBulkApi;
        this.sendMetrics = sendMetrics;
    }

    @SuppressWarnings("exports")
    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        LOGGER.fine("Received a collection of {0} metrics for export.", metrics.size());
        var json = new StringBuilder();
        for (MetricData metricData : metrics) {
            LOGGER.fine("metric: {0}", metricData);
            var jsonDataBuilder = new XJsonStringBuilder();
            jsonDataBuilder.append(
                    ExportSchema.SCOPE_NAME, metricData.getInstrumentationScopeInfo().getName());
            jsonDataBuilder.append(
                    ExportSchema.SCOPE_VERSION,
                    metricData.getInstrumentationScopeInfo().getVersion());
            jsonDataBuilder.append(
                    ExportSchema.SCOPE_SCHEMA,
                    metricData.getInstrumentationScopeInfo().getSchemaUrl());
            switch (metricData.getType()) {
                case LONG_SUM ->
                        appendLongSumJson(
                                metricData.getName(),
                                jsonDataBuilder,
                                metricData.getLongSumData(),
                                json);
                case HISTOGRAM ->
                        appendHistogramJson(
                                metricData.getName(),
                                jsonDataBuilder,
                                metricData.getHistogramData(),
                                json);
                default -> {
                    LOGGER.warning("metric {0} not supported, ignoring...", metricData.getType());
                }
            }
        }
        return sendMetrics.apply(json.toString());
    }

    private void appendHistogramJson(
            String name,
            XJsonStringBuilder jsonDataBuilder,
            HistogramData data,
            StringBuilder json) {
        if (data.getPoints().isEmpty()) return;
        for (var p : data.getPoints()) {
            jsonDataBuilder.append(ExportSchema.METRIC_NAME, name);
            jsonDataBuilder.append(ExportSchema.METRIC_TYPE, "histogram");
            jsonDataBuilder.append(ExportSchema.START_TIME, asTimeString(p.getStartEpochNanos()));
            jsonDataBuilder.append(ExportSchema.END_TIME, asTimeString(p.getEpochNanos()));
            jsonDataBuilder.append(ExportSchema.COUNT, p.getCount());
            jsonDataBuilder.append(ExportSchema.SUM, p.getSum());
            jsonDataBuilder.append(ExportSchema.MIN, p.getMin());
            jsonDataBuilder.append(ExportSchema.MAX, p.getMax());
            jsonDataBuilder.append(ExportSchema.AVG, p.getSum() / p.getCount());
            p.getAttributes().asMap().entrySet().stream()
                    .forEach(
                            e ->
                                    jsonDataBuilder.append(
                                            ExportSchema.ATTR_PREFIX + e.getKey(), e.getValue()));
            var entry = jsonDataBuilder.build();
            json.append(CREATE_JSON).append("\n").append(entry).append("\n");
        }
    }

    private void appendLongSumJson(
            String name,
            XJsonStringBuilder jsonDataBuilder,
            SumData<LongPointData> data,
            StringBuilder json) {
        if (data.getPoints().isEmpty()) return;
        for (var p : data.getPoints()) {
            jsonDataBuilder.append(ExportSchema.METRIC_NAME, name);
            jsonDataBuilder.append(ExportSchema.METRIC_TYPE, "counter");
            jsonDataBuilder.append(ExportSchema.START_TIME, asTimeString(p.getStartEpochNanos()));
            jsonDataBuilder.append(ExportSchema.END_TIME, asTimeString(p.getEpochNanos()));
            jsonDataBuilder.append(ExportSchema.VALUE, p.getValue());
            p.getAttributes().asMap().entrySet().stream()
                    .forEach(
                            e ->
                                    jsonDataBuilder.append(
                                            ExportSchema.ATTR_PREFIX + e.getKey(), e.getValue()));
            var entry = jsonDataBuilder.build();
            json.append(CREATE_JSON).append("\n").append(entry).append("\n");
        }
    }

    private CompletableResultCode sendMetrics(String metricsJson) {
        var builder =
                HttpRequest.newBuilder(addBulkApi)
                        .POST(BodyPublishers.ofString(metricsJson.toString()))
                        .header("Content-Type", "application/json");
        if (timeout != Duration.ZERO) builder.timeout(timeout);
        var request = builder.build();
        var code = new CompletableResultCode();
        LOGGER.info("Sending metrics");
        client.sendAsync(request, BodyHandlers.ofString())
                .whenComplete(
                        (response, ex) -> {
                            LOGGER.info("Metrics sent");
                            if (ex instanceof CompletionException e) {
                                handleException(e.getCause());
                                code.fail();
                            } else if (handleException(ex)) {
                                code.fail();
                            } else if (response.statusCode() != 200) {
                                LOGGER.severe(
                                        "Failed to send metrics to ElasticSearch, response code"
                                                + " {0}: {1}",
                                        response.statusCode(), response.body());
                                code.fail();
                                return;
                            }
                            code.succeed();
                        });
        return code;
    }

    private boolean handleException(Throwable ex) {
        if (ex instanceof ConnectException e) {
            LOGGER.severe("Could not send metrics due to connection problems");
            return true;
        } else if (ex instanceof HttpConnectTimeoutException e) {
            LOGGER.severe("HTTP connection timeout: {0}", e.getMessage());
            return true;
        } else if (ex instanceof InterruptedException e) {
            LOGGER.severe("Interrupted: {0}", e.getMessage());
            return true;
        } else if (ex instanceof IOException e) {
            LOGGER.severe(e);
            return true;
        } else if (ex != null) {
            LOGGER.severe("Sending metrics error: {0}", ex.getMessage());
            return true;
        } else return false;
    }

    private String asTimeString(long epochNanos) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochNanos / 1000000), ZoneOffset.UTC)
                .toString();
    }

    @Override
    public CompletableResultCode flush() {
        LOGGER.fine("flush");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        LOGGER.fine("shutdown");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return AggregationTemporality.DELTA;
    }
}
