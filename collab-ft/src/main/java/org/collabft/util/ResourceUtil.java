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
        return (usedCpu + profile.getCpuMips()) / capacity.getCpuMips() <= 1.0
                && (usedRam + profile.getRamMb()) / capacity.getRamMb() <= 1.0
                && (usedBw + profile.getBandwidth()) / capacity.getBandwidth() <= 1.0;
    }

    public static double residualScore(ResourceCapacity capacity, double usedCpu, double usedRam, double usedBw, ContainerProfile profile) {
        if (!feasible(capacity, usedCpu, usedRam, usedBw, profile)) {
            return -1;
        }
        double cpuProjected = (usedCpu + profile.getCpuMips()) / capacity.getCpuMips();
        double memProjected = (usedRam + profile.getRamMb()) / capacity.getRamMb();
        double bwProjected = (usedBw + profile.getBandwidth()) / capacity.getBandwidth();
        return (1 - cpuProjected) + (1 - memProjected) + (1 - bwProjected);
    }
}
