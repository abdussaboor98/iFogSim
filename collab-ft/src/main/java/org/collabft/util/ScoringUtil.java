package org.collabft.util;

import org.collabft.model.ContainerProfile;

/**
 * Implements the weight redistribution logic described in APPROACH.md.
 */
public final class ScoringUtil {
    private ScoringUtil() {
    }

    public static Weights computeWeights(ContainerProfile profile, double nowSeconds) {
        double total = profile.getCpuMips() + profile.getRamMb() + profile.getBandwidth();
        double pCpu = profile.getCpuMips() / total;
        double pMem = profile.getRamMb() / total;
        double pBw = profile.getBandwidth() / total;

        double epsilon = 1e-3;
        double k = 0.1;
        double u = 1.0 / (profile.getDeadlineSeconds() - nowSeconds + epsilon);
        double uNorm = Math.min(1.0, u * k);

        double gamma = pBw + uNorm * (1 - pBw);
        double wRemaining = 1 - gamma;
        double alpha = wRemaining * (pCpu / (pCpu + pMem));
        double beta = wRemaining * (pMem / (pCpu + pMem));
        return new Weights(alpha, beta, gamma);
    }

    public record Weights(double alpha, double beta, double gamma) {
    }
}
