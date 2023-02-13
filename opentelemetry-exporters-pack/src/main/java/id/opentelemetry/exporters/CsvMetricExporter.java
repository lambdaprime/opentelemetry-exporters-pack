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

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Push Metric Exporter to CSV file.
 *
 * <p>CSV file later can be imported to:
 *
 * <ul>
 *   <li>ElastiSearch: Kibana - Stack Management - Saved Objects - Import
 *   <li>Grafana using <a
 *       href="https://grafana.com/grafana/plugins/marcusolsson-csv-datasource/">CSV plugin</a>
 * </ul>
 *
 * <p>Currently supporter metric types:
 *
 * <ul>
 *   <li>{@link MetricDataType#LONG_SUM}
 *   <li>{@link MetricDataType#HISTOGRAM}
 * </ul>
 *
 * <p>For each of supported {@link MetricDataType} it produces a separate CSV file like:
 *
 * <pre>{@code
 * counter.csv
 *
 * METRIC_NAME  START_TIME  END_TIME    VALUE
 * detected_objects_total  2023-02-12T00:53:45.663Z    2023-02-12T00:53:48.662Z    0
 * input_tensors_total 2023-02-12T00:53:45.663Z    2023-02-12T00:53:48.662Z    2
 * detected_objects_total  2023-02-12T00:53:51.662Z    2023-02-12T00:53:54.662Z    0
 * }</pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * var metricReader = PeriodicMetricReader
 *   .builder(new CsvMetricExporter(Paths.get("/tmp/mymetrics")))
 *   .setInterval(Duration.ofSeconds(3))
 *   .build();
 * var sdkMeterProvider = SdkMeterProvider.builder()
 *   .registerMetricReader(metricReader).build();
 * OpenTelemetrySdk.builder()
 *   .setMeterProvider(sdkMeterProvider)
 *   .buildAndRegisterGlobal();
 * }</pre>
 *
 * @author lambdaprime intid@protonmail.com
 */
public final class CsvMetricExporter implements MetricExporter {
    private static final Logger logger = Logger.getLogger(CsvMetricExporter.class.getName());
    private final String delimiter = "\t";
    private File counterCsvFile;
    private File histogramCsvFile;

    /**
     * @param metricsFolder path to folder where all CSV files are located
     */
    public CsvMetricExporter(Path metricsFolder) throws IOException {
        Files.createDirectories(metricsFolder);
        counterCsvFile = metricsFolder.resolve("counter.csv").toFile();
        if (!counterCsvFile.isFile())
            Files.writeString(
                    counterCsvFile.toPath(),
                    String.join(
                                    delimiter,
                                    ExportSchema.METRIC_NAME,
                                    ExportSchema.START_TIME,
                                    ExportSchema.END_TIME,
                                    ExportSchema.VALUE)
                            + "\n");
        histogramCsvFile = metricsFolder.resolve("histogram.csv").toFile();
        if (!histogramCsvFile.isFile())
            Files.writeString(
                    histogramCsvFile.toPath(),
                    String.join(
                                    delimiter,
                                    ExportSchema.METRIC_NAME,
                                    ExportSchema.START_TIME,
                                    ExportSchema.END_TIME,
                                    ExportSchema.COUNT,
                                    ExportSchema.SUM,
                                    ExportSchema.MIN,
                                    ExportSchema.MAX,
                                    ExportSchema.AVG)
                            + "\n");
    }

    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        logger.fine("Received a collection of " + metrics.size() + " metrics for export.");
        for (MetricData metricData : metrics) {
            logger.fine("metric: " + metricData);
            switch (metricData.getType()) {
                case LONG_SUM -> appendLongSum(metricData.getName(), metricData.getLongSumData());
                case HISTOGRAM -> appendHistogram(
                        metricData.getName(), metricData.getHistogramData());
                default -> logger.info(
                        "metric " + metricData.getType() + " not supported, ignoring...");
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    private void appendHistogram(String name, HistogramData data) {
        if (data.getPoints().isEmpty()) return;
        try (var out = new BufferedWriter(new FileWriter(histogramCsvFile, true))) {
            for (var p : data.getPoints()) {
                var entry =
                        String.join(
                                        delimiter,
                                        name,
                                        asTimeString(p.getStartEpochNanos()),
                                        asTimeString(p.getEpochNanos()),
                                        "" + p.getCount(),
                                        "" + p.getSum(),
                                        "" + p.getMin(),
                                        "" + p.getMax())
                                + "\n";
                out.append(entry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void appendLongSum(String name, SumData<LongPointData> data) {
        if (data.getPoints().isEmpty()) return;
        try (var out = new BufferedWriter(new FileWriter(counterCsvFile, true))) {
            for (var p : data.getPoints()) {
                var entry =
                        String.join(
                                        delimiter,
                                        name,
                                        asTimeString(p.getStartEpochNanos()),
                                        asTimeString(p.getEpochNanos()),
                                        "" + p.getValue())
                                + "\n";
                out.append(entry);
            }
        } catch (IOException e) {
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
