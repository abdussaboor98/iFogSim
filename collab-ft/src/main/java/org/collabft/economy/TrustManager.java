package org.collabft.economy;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-fog trust scores and applies decay/recovery rules.
 */
public class TrustManager {
    private final Map<Integer, Double> trust = new HashMap<>();
    private final Map<Integer, Double> lastPassiveRecovery = new HashMap<>();
    private final boolean enabled;
    private final double decayFactor;
    private final double recoveryFactor;
    private final double threshold;
    private final double passiveRecoveryRate;
    private final double passiveRecoveryInterval;

    public TrustManager(boolean enabled, double decayFactor, double recoveryFactor, double threshold,
                        double passiveRecoveryRate, double passiveRecoveryInterval) {
        this.enabled = enabled;
        this.decayFactor = decayFactor;
        this.recoveryFactor = recoveryFactor;
        this.threshold = threshold;
        this.passiveRecoveryRate = passiveRecoveryRate;
        this.passiveRecoveryInterval = passiveRecoveryInterval;
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

    /**
     * Evaluate all performance metrics (bandwidth, migration time, SLA) and adjust trust once.
     * @param fogId The fog node being evaluated
     * @param errBw Relative bandwidth error
     * @param errMig Relative migration time error
     * @param slaMargin How much time before/after deadline (negative = violation)
     * @param tauBw Bandwidth error threshold
     * @param tauMig Migration time error threshold
     * @param tauSla SLA margin threshold (as fraction of deadline)
     * @param successBonus Additive bonus for good performance
     * @param violationPenalty Additive penalty for violations
     * @return Updated trust value
     */
    public double evaluateAndAdjust(int fogId, double errBw, double errMig, double slaMargin, 
                                     double tauBw, double tauMig, double tauSla,
                                     double successBonus, double violationPenalty) {
        if (!enabled) {
            return current(fogId);
        }
        
        boolean bwDishonest = errBw > tauBw;
        boolean migDishonest = errMig > tauMig;
        boolean slaDishonest = slaMargin < -tauSla; // Negative margin means violated, allow small violations
        
        double currentTrust = current(fogId);
        double updated = currentTrust;
        
        // If any metric is dishonest, apply decay
        if (bwDishonest || migDishonest) {
            updated = currentTrust * decayFactor;
        }
        
        // Apply SLA-based adjustment (additive, after multiplicative decay)
        if (slaDishonest) {
            updated = clamp(updated - violationPenalty);
        } else {
            // Small reward for meeting SLA (only if no bandwidth/migration violations)
            if (!bwDishonest && !migDishonest) {
                updated = clamp(updated + successBonus);
            }
        }
        
        trust.put(fogId, updated);
        return updated;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean passesThreshold(int fogId) {
        return !enabled || current(fogId) >= threshold;
    }

    /**
     * Apply passive trust recovery if enough time has passed since last recovery.
     * Recovery magnitude is passiveRecoveryRate percent of the distance to 1.0.
     * @param fogId The fog node to potentially recover trust for
     * @param currentTime Current simulation time in seconds
     * @return Updated trust value (or unchanged if interval not met)
     */
    public double applyPassiveRecovery(int fogId, double currentTime) {
        if (!enabled || passiveRecoveryRate <= 0) {
            return current(fogId);
        }
        
        double lastRecovery = lastPassiveRecovery.getOrDefault(fogId, 0.0);
        if (currentTime - lastRecovery >= passiveRecoveryInterval) {
            double currentTrust = current(fogId);
            // Recovery = passiveRecoveryRate% of distance to 1.0
            double distanceTo1 = 1.0 - currentTrust;
            double increment = passiveRecoveryRate * distanceTo1;
            double updated = clamp(currentTrust + increment);
            trust.put(fogId, updated);
            lastPassiveRecovery.put(fogId, currentTime);
            return updated;
        }
        
        return current(fogId);
    }

    /**
     * Apply passive recovery to all nodes that have trust records.
     * @param currentTime Current simulation time in seconds
     */
    public void applyPassiveRecoveryAll(double currentTime) {
        if (!enabled || passiveRecoveryRate <= 0) {
            return;
        }
        
        for (int fogId : trust.keySet()) {
            applyPassiveRecovery(fogId, currentTime);
        }
    }

    private double clamp(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }
}
