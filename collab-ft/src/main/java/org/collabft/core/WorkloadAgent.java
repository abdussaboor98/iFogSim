package org.collabft.core;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.model.ContainerProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates container arrivals at a configurable rate and hands them to the
 * matching fog node decision agent. Randomizes resource requirements within
 * configured ranges to stress placement logic.
 */
public class WorkloadAgent extends SimEntity {

    private static final double EPS = 1e-9;

    private final SimulationConfig config;
    private final SimulationConfig.WorkloadConfig workload;
    private final SimulationConfig.TopologyConfig topology;
    private final List<Integer> decisionAgentIds;
    private final List<Integer> targetFogIds;
    private final Random random;
    private int containerSeq = 0;
    private int targetCursor = 0;

    public WorkloadAgent(SimulationConfig config, List<Integer> decisionAgentIds) {
        super("workload-agent");
        this.config = Objects.requireNonNull(config, "config");
        this.workload = Objects.requireNonNull(config.getWorkload(), "workload config");
        this.topology = Objects.requireNonNull(config.getTopology(), "topology config");
        this.decisionAgentIds = new ArrayList<>(Objects.requireNonNull(decisionAgentIds, "decisionAgentIds"));
        this.targetFogIds = buildTargets(topology);
        long seed = config.getRandom().getWorkloadSeed() != 0L ? config.getRandom().getWorkloadSeed()
                : config.getRandom().getMasterSeed();
        this.random = seed == 0L ? new Random() : new Random(seed);
    }

    @Override
    public void startEntity() {
        scheduleNextArrival(0.0);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == SimulationEvents.EVT_CONTAINER_ARRIVAL) {
            dispatchArrival();
        }
    }

    private void dispatchArrival() {
        if (workload.getArrivalRatePerSec() <= EPS || decisionAgentIds.isEmpty() || targetFogIds.isEmpty()) {
            return;
        }
        double now = CloudSim.clock();
        int fogId = pickTargetFog();
        int agentId = decisionAgentIds.get(fogId);
        ContainerProfile profile = nextProfile(now, fogId);
        send(agentId, 0.0, SimulationEvents.EVT_CONTAINER_ARRIVAL, profile);
        double nextDelay = nextInterArrival(workload.getArrivalRatePerSec());
        scheduleNextArrival(nextDelay);
    }

    private ContainerProfile nextProfile(double arrivalTime, int fogId) {
        double cpu = uniform(workload.getMinCpu(), workload.getMaxCpu());
        double mem = uniform(workload.getMinMem(), workload.getMaxMem());
        double bw = uniform(workload.getMinBw(), workload.getMaxBw());
        double size = uniform(workload.getMinSizeMb(), workload.getMaxSizeMb());
        double deadline = uniform(workload.getMinDeadlineSec(), workload.getMaxDeadlineSec());
        String id = "c-" + fogId + "-" + (++containerSeq);
        return new ContainerProfile(id, fogId, cpu, mem, bw, size, deadline, arrivalTime);
    }

    private double nextInterArrival(double ratePerSec) {
        double u = Math.max(EPS, 1.0 - random.nextDouble());
        return -Math.log(u) / ratePerSec;
    }

    private int pickTargetFog() {
        int fogId = targetFogIds.get(targetCursor % targetFogIds.size());
        targetCursor++;
        return fogId;
    }

    private void scheduleNextArrival(double delay) {
        send(getId(), delay, SimulationEvents.EVT_CONTAINER_ARRIVAL);
    }

    private double uniform(double min, double max) {
        if (max - min <= EPS) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    private List<Integer> buildTargets(SimulationConfig.TopologyConfig topo) {
        if (topo.getEdgeDevices() != null && !topo.getEdgeDevices().isEmpty()) {
            List<Integer> targets = new ArrayList<>();
            for (SimulationConfig.EdgeDeviceConfig edge : topo.getEdgeDevices()) {
                if (edge.getFogNodeId() >= 0) {
                    targets.add(edge.getFogNodeId());
                }
            }
            return targets.isEmpty() ? Collections.singletonList(0) : targets;
        }
        int fogCount = Math.max(1, topo.getFogNodeCount());
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < fogCount; i++) {
            targets.add(i);
        }
        return targets;
    }
}
