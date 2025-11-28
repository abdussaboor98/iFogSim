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
    private final double startDelaySeconds;
    private boolean first = true;

    public FaultInjector(String name, List<FogNodeController> controllers, double mtbfSeconds, double recoverySeconds, double leadSeconds, long seed, double cpuProb, double bwProb, double crashProb, double startDelaySeconds) {
        super(name);
        this.meanTimeBetweenFailureSeconds = mtbfSeconds;
        this.recoverySeconds = recoverySeconds;
        this.leadSeconds = leadSeconds;
        this.cpuProb = cpuProb;
        this.bwProb = bwProb;
        this.crashProb = crashProb;
        this.random = new Random(seed);
        this.startDelaySeconds = startDelaySeconds;
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
                    if (notice.getServer().isUnavailableForPrediction()) {
                        scheduleNext();
                        return;
                    }
                    send(notice.getControllerId(), 0, CollabSimTags.FAULT_EVENT, notice);
                    // wait for actual fault event to schedule next failure
                } else {
                    if (notice.getServer().isUnavailableForPrediction()) {
                        scheduleNext();
                        return;
                    }
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
        double startOffset = 0;
        if (first && CloudSim.clock() < startDelaySeconds) {
            startOffset = startDelaySeconds - CloudSim.clock();
            first = false;
        }
        List<Target> eligible = targets.stream()
                .filter(t -> !t.server().isUnavailableForPrediction())
                .toList();
        if (eligible.isEmpty()) {
            // Retry later when some servers are healthy again.
            send(getId(), Math.max(CloudSim.getMinTimeBetweenEvents(), meanTimeBetweenFailureSeconds / 10), CollabSimTags.FAULT_EVENT, null);
            return;
        }
        Target target = eligible.get(random.nextInt(eligible.size()));
        FaultNotice.FaultType type = pickType();
        double failureDelay = startOffset + exponential(meanTimeBetweenFailureSeconds);
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
