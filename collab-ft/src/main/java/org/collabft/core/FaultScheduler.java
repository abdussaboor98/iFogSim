package org.collabft.core;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.model.FaultEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Emits predicted fault events into the simulation timeline using a Poisson
 * process. Respects a cap on simultaneous active faults to avoid total collapse.
 */
public class FaultScheduler extends SimEntity {

    private static final double DEFAULT_FAULT_RATE = 1.0 / 300.0;

    private final SimulationConfig config;
    private final SimulationConfig.FaultConfig faultConfig;
    private final SimulationConfig.TopologyConfig topologyConfig;
    private final List<Integer> decisionAgentIds;
    private final List<FaultEvent> inflightFaults = new ArrayList<>();
    private final Random random;
    private final int maxActiveFaults;

    public FaultScheduler(SimulationConfig config, List<Integer> decisionAgentIds) {
        super("fault-scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.faultConfig = Objects.requireNonNull(config.getFault(), "fault config");
        this.topologyConfig = Objects.requireNonNull(config.getTopology(), "topology config");
        this.decisionAgentIds = new ArrayList<>(decisionAgentIds);
        long seed = faultConfig.getSeed();
        if (seed == 0L) {
            seed = config.getRandom().getFaultSeed() != 0L ? config.getRandom().getFaultSeed() : config.getRandom().getMasterSeed();
        }
        this.random = seed == 0L ? new Random() : new Random(seed);
        int fogCount = topologyConfig.getFogNodeCount();
        int half = fogCount > 0 ? Math.max(1, fogCount / 2) : 0;
        int configuredCap = faultConfig.getMaxActiveFaults() > 0 ? faultConfig.getMaxActiveFaults() : half;
        this.maxActiveFaults = half == 0 ? 0 : Math.min(configuredCap, half);
    }

    @Override
    public void startEntity() {
        scheduleNextSample(0.0);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == SimulationEvents.EVT_FAULT_PREDICTED) {
            if (ev.getData() instanceof FaultEvent fault) {
                dispatchPrediction(fault);
            } else {
                sampleFault();
            }
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_HIT) {
            handleFaultHit(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_RECOVERED) {
            handleRecovery(ev.getData());
        }
    }

    private void sampleFault() {
        pruneRecovered(CloudSim.clock());
        double rate = faultConfig.getPoissonRatePerSec() > 0 ? faultConfig.getPoissonRatePerSec() : DEFAULT_FAULT_RATE;
        if (rate <= 0.0 || topologyConfig.getFogNodeCount() <= 0 || topologyConfig.getServersPerFog() <= 0 || maxActiveFaults == 0) {
            scheduleNextSample(faultConfig.getPredictionLeadTimeSec());
            return;
        }
        double now = CloudSim.clock();
        double interArrival = nextInterArrival(rate);
        double faultAt = now + interArrival;
        if (activeCountAt(faultAt) >= maxActiveFaults) {
            double retryDelay = Math.max(1.0, nextRecoveryAfter(now) - now);
            scheduleNextSample(retryDelay);
            return;
        }
        FaultEvent fault = buildFault(faultAt);
        inflightFaults.add(fault);
        double predictedDelay = Math.max(0.0, fault.getPredictedAt() - now);
        send(getId(), predictedDelay, SimulationEvents.EVT_FAULT_PREDICTED, fault);
        send(getId(), fault.timeUntilFault(now), SimulationEvents.EVT_FAULT_HIT, fault);
        send(getId(), fault.timeUntilRecover(now), SimulationEvents.EVT_FAULT_RECOVERED, fault);
        scheduleNextSample(interArrival);
    }

    private void handleFaultHit(Object data) {
        if (data instanceof FaultEvent fault) {
            broadcast(0.0, SimulationEvents.EVT_FAULT_HIT, fault);
        }
    }

    private void handleRecovery(Object data) {
        if (data instanceof FaultEvent fault) {
            broadcast(0.0, SimulationEvents.EVT_FAULT_RECOVERED, fault);
            removeInflight(fault);
        }
    }

    private void broadcast(double delay, SimulationEvents tag, FaultEvent fault) {
        for (int decisionAgentId : decisionAgentIds) {
            send(decisionAgentId, delay, tag, fault);
        }
    }

    private void scheduleNextSample(double delay) {
        send(getId(), delay, SimulationEvents.EVT_FAULT_PREDICTED);
    }

    private void dispatchPrediction(FaultEvent fault) {
        double now = CloudSim.clock();
        double delay = Math.max(0.0, fault.getPredictedAt() - now);
        broadcast(delay, SimulationEvents.EVT_FAULT_PREDICTED, fault);
    }

    private double nextInterArrival(double ratePerSec) {
        double u = Math.max(1e-12, 1.0 - random.nextDouble());
        return -Math.log(u) / ratePerSec;
    }

    private FaultEvent buildFault(double faultAt) {
        int fogId = random.nextInt(Math.max(1, topologyConfig.getFogNodeCount()));
        int serverId = random.nextInt(Math.max(1, topologyConfig.getServersPerFog()));
        FaultEvent.Type type = FaultEvent.Type.values()[random.nextInt(FaultEvent.Type.values().length)];
        double predictedAt = faultAt - faultConfig.getPredictionLeadTimeSec();
        if (predictedAt < CloudSim.clock()) {
            predictedAt = CloudSim.clock();
        }
        double recoverAt = faultAt + faultConfig.getRecoveryTimeSec();
        return new FaultEvent(fogId, serverId, type, predictedAt, faultAt, recoverAt);
    }

    private int activeCountAt(double time) {
        int count = 0;
        for (FaultEvent fault : inflightFaults) {
            if (fault.getFaultAt() <= time && fault.getRecoverAt() > time) {
                count++;
            }
        }
        return count;
    }

    private void pruneRecovered(double now) {
        Iterator<FaultEvent> it = inflightFaults.iterator();
        while (it.hasNext()) {
            FaultEvent fault = it.next();
            if (fault.getRecoverAt() <= now) {
                it.remove();
            }
        }
    }

    private double nextRecoveryAfter(double now) {
        double next = Double.POSITIVE_INFINITY;
        for (FaultEvent fault : inflightFaults) {
            if (fault.getRecoverAt() > now && fault.getRecoverAt() < next) {
                next = fault.getRecoverAt();
            }
        }
        if (Double.isInfinite(next)) {
            return now + faultConfig.getPredictionLeadTimeSec();
        }
        return next;
    }

    private void removeInflight(FaultEvent target) {
        Iterator<FaultEvent> it = inflightFaults.iterator();
        while (it.hasNext()) {
            FaultEvent fault = it.next();
            if (fault == target) {
                it.remove();
                return;
            }
        }
    }

    @Override
    public void shutdownEntity() {
        // No-op
    }
}
