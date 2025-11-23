package org.collabft.core;

import org.collabft.config.SimulationConfig;
import org.collabft.model.ContainerProfile;
import org.collabft.model.PaymentReport;
import org.collabft.model.Wallet;

import java.util.Map;
import java.util.Objects;

/**
 * Centralizes SLA-aware payment computation and reputation updates for migrations.
 */
public class PaymentManager {

    private final SimulationConfig.SlaConfig slaConfig;
    private final Map<Integer, Double> reputation;
    private final Wallet wallet;
    private final SimulationConfig.WorkloadConfig workloadConfig;

    public PaymentManager(SimulationConfig.SlaConfig slaConfig,
                          SimulationConfig.WorkloadConfig workloadConfig,
                          Map<Integer, Double> reputation,
                          Wallet wallet) {
        this.slaConfig = Objects.requireNonNull(slaConfig, "slaConfig");
        this.workloadConfig = workloadConfig;
        this.reputation = Objects.requireNonNull(reputation, "reputation");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
    }

    public PaymentReport createPayment(int payerFogId, int payeeFogId, ContainerProfile profile,
                                       double completedAt, double bidCost, boolean deadlineMet) {
        double rNorm = normalizedResources(profile);
        double uNorm = urgencyNorm(profile, completedAt);
        double slaValue = slaConfig.getReliabilityWeight() * rNorm + slaConfig.getUrgencyWeight() * uNorm;
        slaValue = clamp01(slaValue);
        double payment = deadlineMet ? bidCost + slaValue : bidCost - slaConfig.getPenaltyEta() * slaValue;
        return new PaymentReport(profile.getId(), payerFogId, payeeFogId, slaValue, payment, !deadlineMet,
                rNorm, uNorm, completedAt);
    }

    public void applyLocalWallet(PaymentReport payment, int localFogId) {
        if (payment == null) {
            return;
        }
        if (payment.getPayeeFogId() == localFogId) {
            wallet.credit(payment.getPayment());
        }
        if (payment.getPayerFogId() == localFogId) {
            wallet.debit(Math.abs(payment.getPayment()));
        }
    }

    public void updateReputation(PaymentReport payment) {
        if (payment == null || payment.getPayeeFogId() < 0) {
            return;
        }
        double delta = payment.isReward() ? 0.05 : -0.05;
        reputation.merge(payment.getPayeeFogId(), clampReputation(slaConfig.getReputationInit() + delta),
                (oldVal, newVal) -> clampReputation(oldVal + delta));
    }

    private double normalizedResources(ContainerProfile profile) {
        double rMax = workloadConfig != null
                ? workloadConfig.getMaxCpu() + workloadConfig.getMaxMem() + workloadConfig.getMaxBw()
                : 0.0;
        if (rMax <= 0) {
            rMax = profile.totalRequirement();
        }
        return rMax <= 0 ? 0.0 : profile.totalRequirement() / rMax;
    }

    private double urgencyNorm(ContainerProfile profile, double completedAt) {
        double elapsed = Math.max(0.0, completedAt - profile.getArrivalTime());
        double slack = Math.max(0.0, profile.getDeadlineSec() - elapsed);
        double inv = 1.0 / Math.max(1e-6, slack);
        double scaled = slaConfig.getUrgencyScale() * inv;
        return clamp01(scaled);
    }

    private double clampReputation(double val) {
        double min = slaConfig.getReputationMin();
        double max = slaConfig.getReputationMax();
        return Math.max(min, Math.min(max, val));
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
