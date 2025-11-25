package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;

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
    private final Random random;

    public FaultInjector(String name, List<FogNodeController> controllers, double mtbfSeconds, double recoverySeconds, double leadSeconds, long seed) {
        super(name);
        this.meanTimeBetweenFailureSeconds = mtbfSeconds;
        this.recoverySeconds = recoverySeconds;
        this.leadSeconds = leadSeconds;
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
            if (ev.getData() instanceof Target target) {
                send(target.controllerId(), 0, CollabSimTags.FAULT_EVENT, target.server());
                send(target.server().getId(), recoverySeconds, CollabSimTags.RECOVERY_EVENT);
            }
            scheduleNext();
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
        double failureDelay = exponential(meanTimeBetweenFailureSeconds);
        double predictionDelay = Math.max(CloudSim.getMinTimeBetweenEvents(), failureDelay - leadSeconds);
        send(getId(), predictionDelay, CollabSimTags.FAULT_EVENT, target);
    }

    private double exponential(double mean) {
        double u = random.nextDouble();
        return -mean * Math.log(1 - u);
    }

    private record Target(int controllerId, FogServer server) {
    }
}
