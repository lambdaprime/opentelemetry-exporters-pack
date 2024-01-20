/**
 * @author lambdaprime intid@protonmail.com
 */
open module id.opentelemetry.exporters.pack.tests {
    exports id.opentelemetry.exporters.tests;

    requires java.net.http;
    requires org.junit.jupiter.api;
    requires io.opentelemetry.sdk.metrics;
    requires id.opentelemetry.exporters.pack;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.api;
    requires id.xfunctiontests;
}
