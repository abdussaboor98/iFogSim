package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.config.SimulationConfig;
import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;
import org.collabft.model.TaskProfile;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.ArrayList;
import java.util.Random;

/**
 * Generates tasks and offloads them to its mapped FogNodeController.
 */
public class EdgeDevice extends FogDevice {
    private final ContainerProfile baseProfile;
    private final SimulationConfig.TaskConfig taskConfig;
    private final SimulationConfig.EdgeConfig edgeConfig;
    private final SimulationConfig.BiddingConfig biddingConfig;
    private final SimulationConfig.Network networkConfig;
    private final SimulationConfig.SlaConfig slaConfig;
    private final int tasksToSend;
    private final double meanInterArrival;
    private final double simulationDuration;
    private final double scheduledMeanSpacing;
    private final double maxTaskWindow;
    private final Random random;
    private int sentCount = 0;

    public EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, SimulationConfig.TaskConfig taskConfig,
                      SimulationConfig.EdgeConfig edgeConfig, SimulationConfig.BiddingConfig biddingConfig,
                      SimulationConfig.Network networkConfig, SimulationConfig.SlaConfig slaConfig, double simulationDuration, long seed) throws Exception {
        this(name, capacity, profile, taskConfig, edgeConfig, biddingConfig, networkConfig, slaConfig, simulationDuration, seed,
                FogDeviceFactory.build(capacity, new FogLinearPowerModel(40, 5)));
    }

    private EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, SimulationConfig.TaskConfig taskConfig,
                       SimulationConfig.EdgeConfig edgeConfig, SimulationConfig.BiddingConfig biddingConfig,
                       SimulationConfig.Network networkConfig, SimulationConfig.SlaConfig slaConfig, double simulationDuration, long seed,
                       Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.baseProfile = profile;
        this.taskConfig = taskConfig;
        this.edgeConfig = edgeConfig;
        this.biddingConfig = biddingConfig;
        this.networkConfig = networkConfig;
        this.slaConfig = slaConfig;
        this.tasksToSend = taskConfig.getTasksPerEdge();
        this.meanInterArrival = taskConfig.getMeanInterArrivalSeconds();
        this.simulationDuration = simulationDuration;
        if (tasksToSend > 0 && simulationDuration > 0) {
            this.scheduledMeanSpacing = simulationDuration / tasksToSend;
        } else {
            this.scheduledMeanSpacing = -1;
        }
        this.maxTaskWindow = baseProfile.getRuntimeSeconds() * (1.0 + taskConfig.getSlaRuntimeMultiplier());
        this.random = new Random(seed);
    }

    @Override
    public void startEntity() {
        super.startEntity();
        double firstDelay = computeNextDelay();
        scheduleNext(firstDelay);
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev == null || !(ev.getTag() instanceof CollabSimTags tag)) {
            return;
        }
        if (tag == CollabSimTags.TASK_GENERATE) {
            scheduleNext(0);
        }
    }

    private void scheduleNext(double delay) {
        // Allow unlimited generation when tasksPerEdge <= 0
        if (tasksToSend > 0 && sentCount >= tasksToSend) {
            return;
        }
        ContainerProfile sampled = sampleProfile();
        double edgeBwMbPerSec = edgeConfig.getBandwidthMbps() / 8.0;
        double transmissionSeconds = sampled.getContainerSizeMb() / Math.max(1e-6, edgeBwMbPerSec);
        double latencySeconds = edgeConfig.getLatencyMs() / 1000.0;
        double totalDelay = delay + transmissionSeconds + latencySeconds;
        double arrivalTime = CloudSim.clock() + totalDelay;
        double tExec = sampled.getRuntimeSeconds();
        double tNet = slaConfig.getNetOverheadRatio() * tExec;
        double tSlack = slaConfig.getSlackRatio() * tExec;
        double interFogBwMBps = networkConfig.getInterFogBandwidthMbps() / 8.0;
        double tMig = biddingConfig.getPauseSeconds()
                + sampled.getContainerSizeMb() / Math.max(1e-6, interFogBwMBps)
                + biddingConfig.getResumeSeconds();
        double deadline = arrivalTime + tExec + tNet + tSlack + tMig;
        sampled.setDeadlineSeconds(deadline);
        send(getParentId(), totalDelay, CollabSimTags.TASK_ARRIVAL_EVENT,
                new TaskProfile(sampled, arrivalTime, deadline, tExec, tNet, tSlack, tMig, getName()));
        sentCount++;
        double nextDelay = computeNextDelay();
        send(getId(), delay + nextDelay, CollabSimTags.TASK_GENERATE);
    }

    private double exponential(double mean) {
        double u = random.nextDouble();
        return -mean * Math.log(1 - u);
    }

    private double applyJitter(double base) {
        double jitter = base * (taskConfig.getJitterPercent() / 100.0);
        double offset = (random.nextDouble() * 2 - 1) * jitter; // +/- jitter
        return Math.max(0, base + offset);
    }

    private ContainerProfile sampleProfile() {
        ContainerProfile p = new ContainerProfile();
        p.setDemandMips(sample(taskConfig.getDemandMipsRange(), baseProfile.getDemandMips()));
        double runtime = sample(taskConfig.getRuntimeRange(), baseProfile.getRuntimeSeconds());
        p.setRuntimeSeconds(runtime);
        p.setRamMb((int) sample(taskConfig.getRamRange(), baseProfile.getRamMb()));
        p.setBandwidth(sample(taskConfig.getBandwidthRange(), baseProfile.getBandwidth()));
        p.setContainerSizeMb(sample(taskConfig.getContainerSizeRange(), baseProfile.getContainerSizeMb()));
        return p;
    }

    private double sample(SimulationConfig.Range range, double fallback) {
        if (range == null || !range.isConfigured()) {
            return applyEdgeHeterogeneity(fallback);
        }
        double v = range.getMin() + random.nextDouble() * (range.getMax() - range.getMin());
        return applyEdgeHeterogeneity(v);
    }

    private double applyEdgeHeterogeneity(double v) {
        double jitter = edgeConfig.getHeterogeneityJitter();
        if (jitter <= 0) return v;
        double delta = v * (jitter / 100.0);
        double offset = (random.nextDouble() * 2 - 1) * delta;
        return Math.max(0, v + offset);
    }

    private double computeNextDelay() {
        double minSpacing = Math.max(CloudSim.getMinTimeBetweenEvents(), 1e-6);
        if (scheduledMeanSpacing <= 0) {
            double delay = exponential(meanInterArrival);
            return Math.max(minSpacing, applyJitter(delay));
        }
        double candidate = exponential(scheduledMeanSpacing);
        double jittered = Math.max(candidate, minSpacing);
        jittered = applyJitter(jittered);
        jittered = Math.max(jittered, minSpacing);
        int remainingTasks = Math.max(1, tasksToSend - sentCount);
        double remainingTime = Math.max(0, simulationDuration - CloudSim.clock());
        double maxAllowed = remainingTime - minSpacing * (remainingTasks - 1);
        if (maxTaskWindow > 0) {
            double bound = Math.max(minSpacing, remainingTime - maxTaskWindow);
            maxAllowed = Math.min(maxAllowed, bound);
        }
        if (maxAllowed < minSpacing) {
            maxAllowed = minSpacing;
        }
        return Math.min(jittered, maxAllowed);
    }
}
