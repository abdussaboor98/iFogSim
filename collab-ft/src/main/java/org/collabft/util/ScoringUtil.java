package org.collabft.util;

import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.model.ContainerModule;
import org.collabft.model.ContainerProfile;

/**
 * Implements the weight redistribution logic described in APPROACH.md.
 */
public final class ScoringUtil {
    private ScoringUtil() {
    }

    public static Weights computeWeights(ContainerModule container, double nowSeconds) {
        ContainerProfile profile = container.getProfile();
        double total = profile.getDemandMips() + profile.getRamMb() + profile.getBandwidth();
        // p_cpu = R_cpu / (R_cpu + R_mem + R_bw); same for p_mem, p_bw
        double pCpu = profile.getDemandMips() / total;
        double pMem = profile.getRamMb() / total;
        double pBw = profile.getBandwidth() / total;

        double epsilon = 1e-3;
        double k = 0.1;
        double slack = Math.max(epsilon, container.getDeadlineSeconds() - nowSeconds);
        // U = 1 / slack; U_norm = min(1, U * k)
        double u = 1.0 / slack;
        double uNorm = Math.min(1.0, u * k);

        // γ = p_bw + U_norm * (1 - p_bw)
        double gamma = pBw + uNorm * (1 - pBw);
        double wRemaining = 1 - gamma;
        // α, β redistribute remaining weight using CPU/MEM proportions:
        // α = W * p_cpu / (p_cpu + p_mem); β = W * p_mem / (p_cpu + p_mem)
        double alpha = wRemaining * (pCpu / (pCpu + pMem));
        double beta = wRemaining * (pMem / (pCpu + pMem));
        return new Weights(alpha, beta, gamma);
    }

    public record Weights(double alpha, double beta, double gamma) {
    }
}
