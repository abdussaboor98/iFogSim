package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.model.FaultNotice;
import org.collabft.metrics.MetricsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates Poisson-distributed failures and notifies fog nodes.
 */
public class FaultInjector extends SimEntity {
    private final List<Target> targets = new ArrayList<>();
    private final double meanTimeBetweenFailureSeconds;
    private final double recoverySeconds;
    private final double leadSeconds;
    private final double cpuProb;
    private final double bwProb;
    private final double crashProb;
    private final Random random;

    public FaultInjector(String name, List<FogNodeController> controllers, double mtbfSeconds, double recoverySeconds, double leadSeconds, long seed, double cpuProb, double bwProb, double crashProb) {
        super(name);
        this.meanTimeBetweenFailureSeconds = mtbfSeconds;
        this.recoverySeconds = recoverySeconds;
        this.leadSeconds = leadSeconds;
        this.cpuProb = cpuProb;
        this.bwProb = bwProb;
        this.crashProb = crashProb;
        this.random = new Random(seed);
        for (FogNodeController controller : controllers) {
            for (FogServer server : controller.getServers()) {
                targets.add(new Target(controller.getId(), server));
            }
        }
    }

    @Override
    public void startEntity() {
        scheduleNext();
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.FAULT_EVENT) {
            if (ev.getData() instanceof FaultNotice notice) {
                // Prediction notice delivered to controller early
                if (notice.isPrediction()) {
                    send(notice.getControllerId(), 0, CollabSimTags.FAULT_EVENT, notice);
                    // wait for actual fault event to schedule next failure
                } else {
                    send(notice.getControllerId(), 0, CollabSimTags.FAULT_EVENT, notice);
                    send(notice.getServer().getId(), recoverySeconds, CollabSimTags.RECOVERY_EVENT);
                    MetricsRegistry.collector().recordFault(
                            notice.getServer().getName(),
                            notice.getType().name().toLowerCase(),
                            notice.getFailureTime() - leadSeconds,
                            CloudSim.clock(),
                            CloudSim.clock() + recoverySeconds,
                            true);
                    scheduleNext();
                }
            }
        }
    }

    @Override
    public void shutdownEntity() {
    }

    private void scheduleNext() {
        if (targets.isEmpty()) {
            return;
        }
        Target target = targets.get(random.nextInt(targets.size()));
        FaultNotice.FaultType type = pickType();
        double failureDelay = exponential(meanTimeBetweenFailureSeconds);
        double predictionDelay = Math.max(CloudSim.getMinTimeBetweenEvents(), failureDelay - leadSeconds);
        double failureTime = CloudSim.clock() + failureDelay;
        // Prediction event
        send(getId(), predictionDelay, CollabSimTags.FAULT_EVENT, new FaultNotice(target.server(), type, true, failureTime, target.controllerId()));
        // Actual failure event
        send(getId(), failureDelay, CollabSimTags.FAULT_EVENT, new FaultNotice(target.server(), type, false, failureTime, target.controllerId()));
    }

    private double exponential(double mean) {
        double u = random.nextDouble();
        return -mean * Math.log(1 - u);
    }

    private FaultNotice.FaultType pickType() {
        double u = random.nextDouble();
        double thresholdCpu = cpuProb;
        double thresholdBw = cpuProb + bwProb;
        double thresholdCrash = cpuProb + bwProb + crashProb;
        if (u < thresholdCpu) {
            return FaultNotice.FaultType.CPU_FAILURE;
        } else if (u < thresholdBw) {
            return FaultNotice.FaultType.BANDWIDTH_DEGRADATION;
        }
        return u < thresholdCrash ? FaultNotice.FaultType.SERVER_CRASH : FaultNotice.FaultType.SERVER_CRASH;
    }

    private record Target(int controllerId, FogServer server) {
    }
}
