/**
 * Extensions for JUnit which allow to export OpenTelemetry metrics to one of the exporters
 * available in <b>opentelemetry-exporters-pack</b>
 *
 * <p>To enable any of the extensions annotate unit test class with:
 *
 * <pre>{@code
 * @ExtendWith({EXTENSION_CLASS_NAME.class})
 * }</pre>
 *
 * @see <a href="http://portal2.atwebpages.com/opentelemetry-exporters-pack">Documentation</a>
 * @see <a href= "https://github.com/lambdaprime/opentelemetry-exporters-pack/releases">Download</a>
 * @see <a href="https://github.com/lambdaprime/opentelemetry-exporters-pack">GitHub repository</a>
 * @author lambdaprime intid@protonmail.com
 */
module id.opentelemetry.exporters.pack.junit {
    exports id.opentelemetry.exporters.extensions;

    requires id.opentelemetry.exporters.pack;
    requires org.junit.jupiter.api;
    requires io.opentelemetry.sdk.metrics;
    requires io.opentelemetry.api;
    requires io.opentelemetry.sdk;
    requires java.logging;
}
