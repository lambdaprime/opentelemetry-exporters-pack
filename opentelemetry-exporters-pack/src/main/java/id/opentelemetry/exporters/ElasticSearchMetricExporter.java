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

import id.xfunction.XJson;
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
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;

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
 * it. Use {@link #ElasticSearchMetricExporter(URI, Optional, boolean)} with insecure set to "true".
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * var metricReader = PeriodicMetricReader
 *   .builder(new ElasticSearchMetricExporter(
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
public final class ElasticSearchMetricExporter implements MetricExporter {
    private static final XLogger logger =
            XLogger.getLogger(ElasticSearchMetricExporter.class.getName());
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

    /**
     * @param elasticSearch URI to the ElasticSearch index where metrics will be exported.
     *     Credentials can be part of the URL. Example http://user:password@localhost:9200/customers
     *     They are optional to allow anonymous access if it is enabled on ElasticSearch.
     */
    public ElasticSearchMetricExporter(URI elasticSearch) {
        this(elasticSearch, Optional.empty(), false);
    }

    /**
     * @param credentials ElasticSearch user and password. They are optional to allow anonymous
     *     access if it is enabled on ElasticSearch.
     */
    public ElasticSearchMetricExporter(URI elasticSearch, Optional<Credentials> credentials) {
        this(elasticSearch, credentials, false);
    }

    /**
     * @param insecure allow connections to ElasticSearch with self-signed SSL certificates
     */
    public ElasticSearchMetricExporter(
            URI elasticSearch, Optional<Credentials> credentials, boolean insecure) {
        if (insecure) {
            logger.warning("Insecure connetions to ElasticSearch are enabled");
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
        client = builder.build();
    }

    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        logger.fine("Received a collection of " + metrics.size() + " metrics for export.");
        for (MetricData metricData : metrics) {
            logger.fine("metric: " + metricData);
            switch (metricData.getType()) {
                case LONG_SUM -> sendLongSum(metricData.getName(), metricData.getLongSumData());
                case HISTOGRAM -> sendHistogram(
                        metricData.getName(), metricData.getHistogramData());
                default -> logger.warning(
                        "metric " + metricData.getType() + " not supported, ignoring...");
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    private void sendHistogram(String name, HistogramData data) {
        if (data.getPoints().isEmpty()) return;
        var buf = new StringBuilder();
        for (var p : data.getPoints()) {
            var entry =
                    XJson.asString(
                            ExportSchema.METRIC_NAME, name,
                            ExportSchema.METRIC_TYPE, "histogram",
                            ExportSchema.START_TIME, asTimeString(p.getStartEpochNanos()),
                            ExportSchema.END_TIME, asTimeString(p.getEpochNanos()),
                            ExportSchema.COUNT, p.getCount(),
                            ExportSchema.SUM, p.getSum(),
                            ExportSchema.MIN, p.getMin(),
                            ExportSchema.MAX, p.getMax(),
                            ExportSchema.AVG, p.getSum() / p.getCount());
            buf.append(CREATE_JSON).append("\n").append(entry).append("\n");
        }
        sendMetrics(buf.toString());
    }

    private void sendLongSum(String name, SumData<LongPointData> data) {
        if (data.getPoints().isEmpty()) return;
        var buf = new StringBuilder();
        for (var p : data.getPoints()) {
            var entry =
                    XJson.asString(
                            ExportSchema.METRIC_NAME, name,
                            ExportSchema.METRIC_TYPE, "counter",
                            ExportSchema.START_TIME, asTimeString(p.getStartEpochNanos()),
                            ExportSchema.END_TIME, asTimeString(p.getEpochNanos()),
                            ExportSchema.VALUE, p.getValue());
            buf.append(CREATE_JSON).append("\n").append(entry).append("\n");
        }
        sendMetrics(buf.toString());
    }

    private void sendMetrics(String metricsJson) {
        try {
            var request =
                    HttpRequest.newBuilder(addBulkApi)
                            .POST(BodyPublishers.ofString(metricsJson.toString()))
                            .header("Content-Type", "application/json")
                            .build();
            var response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.severe(
                        "Failed to send metrics to ElasticSearch, response code {0}: {1}",
                        response.statusCode(), response.body());
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private String asTimeString(long epochNanos) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochNanos / 1000000), ZoneOffset.UTC)
                .toString();
    }

    @Override
    public CompletableResultCode flush() {
        logger.fine("flush");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        logger.fine("shutdown");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return AggregationTemporality.DELTA;
    }
}
