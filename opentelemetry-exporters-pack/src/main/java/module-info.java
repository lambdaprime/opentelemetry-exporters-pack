/**
 * Exporters of <a href="https://opentelemetry.io">OpenTelemetry</a> metrics.
 *
 * <p>Main idea of all supported exporters is to have Zero Dependencies on any client libraries
 * needed to interact with the backend store. Instead all exporters should rely only on Java
 * standard API. As the result adding this module to any application is cheap and does not cause any
 * overhead during application start up (some backend client dependencies may package resources
 * inside and during start up extract them which affect application start up time, this is not the
 * case with <b>opentelemetry-exporters-pack</b>).
 *
 * <p>Available exporters:
 *
 * <ul>
 *   <li>{@link id.opentelemetry.exporters.CsvMetricExporter}
 *   <li>{@link id.opentelemetry.exporters.ElasticSearchMetricExporter} - export metrics to <a
 *       href="https://www.elastic.co/elasticsearch/">ElasticSearch</a>. Optionally allows export to
 *       ElasticSearch with self-signed SSL certificates (by default it is disabled and when enabled
 *       it prints warning message to the logs)
 * </ul>
 *
 * @see <a href="https://opentelemetry.io">OpenTelemetry</a>
 * @see <a
 *     href="https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters">OpenTelemetry
 *     standard exporters</a>
 * @see <a
 *     href="https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/index.html">OpenTelemetry
 *     API javadoc</a>
 * @see <a
 *     href="https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk-metrics/latest/index.html">OpenTelemetry
 *     SDK javadoc</a>
 * @see <a href="https://opentelemetry.io/ecosystem/registry/">Registry for OpenTelemetry
 *     ecosystem</a>
 * @see <a href="http://portal2.atwebpages.com/opentelemetry-exporters-pack">Documentation</a>
 * @see <a href= "https://github.com/lambdaprime/opentelemetry-exporters-pack/releases">Download</a>
 * @see <a href="https://github.com/lambdaprime/opentelemetry-exporters-pack">GitHub repository</a>
 * @author lambdaprime intid@protonmail.com
 */
module id.opentelemetry.exporters.pack {
    exports id.opentelemetry.exporters;

    requires id.xfunction;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.metrics;
    requires java.logging;
    requires java.net.http;
}
