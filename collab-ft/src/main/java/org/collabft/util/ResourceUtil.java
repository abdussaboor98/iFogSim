package org.collabft.util;

import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;

/**
 * Utility methods for feasibility and scoring calculations.
 */
public final class ResourceUtil {
    private ResourceUtil() {
    }

    public static boolean feasible(ResourceCapacity capacity, double usedCpu, double usedRam, double usedBw, ContainerProfile profile) {
        return feasible(capacity, usedCpu, usedRam, usedBw, profile, 1.0, 1.0);
    }

    public static boolean feasible(ResourceCapacity capacity, double usedCpu, double usedRam, double usedBw, ContainerProfile profile, double cpuFactor, double bwFactor) {
        // Feasibility: (used + demand) / capacity <= 1  for CPU, RAM, and bandwidth
        return (usedCpu + profile.getCpuMips()) / (capacity.getCpuMips() * cpuFactor) <= 1.0
                && (usedRam + profile.getRamMb()) / capacity.getRamMb() <= 1.0
                && (usedBw + profile.getBandwidth()) / (capacity.getBandwidth() * bwFactor) <= 1.0;
    }

    public static double residualScore(ResourceCapacity capacity, double usedCpu, double usedRam, double usedBw, ContainerProfile profile) {
        return residualScore(capacity, usedCpu, usedRam, usedBw, profile, 1.0, 1.0);
    }

    public static double residualScore(ResourceCapacity capacity, double usedCpu, double usedRam, double usedBw, ContainerProfile profile, double cpuFactor, double bwFactor) {
        if (!feasible(capacity, usedCpu, usedRam, usedBw, profile, cpuFactor, bwFactor)) {
            return -1;
        }
        // residual_score = (1 - L_cpu) + (1 - L_mem) + (1 - L_bw) after placement
        double cpuProjected = (usedCpu + profile.getCpuMips()) / (capacity.getCpuMips() * cpuFactor);
        double memProjected = (usedRam + profile.getRamMb()) / capacity.getRamMb();
        double bwProjected = (usedBw + profile.getBandwidth()) / (capacity.getBandwidth() * bwFactor);
        return (1 - cpuProjected) + (1 - memProjected) + (1 - bwProjected);
    }
}
