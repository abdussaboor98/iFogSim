package org.collabft.metrics;

/**
 * Global singleton accessor for MetricsCollector so agents can log events without wiring.
 */
public final class MetricsRegistry {
    private static final MetricsCollector INSTANCE = new MetricsCollector();

    private MetricsRegistry() {
    }

    public static MetricsCollector collector() {
        return INSTANCE;
    }

    public static MetricsRegistry get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final MetricsRegistry INSTANCE = new MetricsRegistry();
    }
}
