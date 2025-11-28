package org.collabft.economy;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-fog trust scores and applies decay/recovery rules.
 */
public class TrustManager {
    private final Map<Integer, Double> trust = new HashMap<>();
    private final boolean enabled;
    private final double decayFactor;
    private final double recoveryFactor;
    private final double threshold;

    public TrustManager(boolean enabled, double decayFactor, double recoveryFactor, double threshold) {
        this.enabled = enabled;
        this.decayFactor = decayFactor;
        this.recoveryFactor = recoveryFactor;
        this.threshold = threshold;
    }

    public double current(int fogId) {
        return trust.getOrDefault(fogId, 1.0);
    }

    public double decay(int fogId) {
        if (!enabled) {
            return current(fogId);
        }
        double updated = clamp(current(fogId) * decayFactor);
        trust.put(fogId, updated);
        return updated;
    }

    public double recover(int fogId) {
        if (!enabled) {
            return current(fogId);
        }
        double updated = clamp(current(fogId) + recoveryFactor * (1 - current(fogId)));
        trust.put(fogId, updated);
        return updated;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean passesThreshold(int fogId) {
        return !enabled || current(fogId) >= threshold;
    }

    private double clamp(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }
}
