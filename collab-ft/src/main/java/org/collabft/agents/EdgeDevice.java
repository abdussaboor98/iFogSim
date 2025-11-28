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
    private final int tasksToSend;
    private final double meanInterArrival;
    private final Random random;
    private int sentCount = 0;

    public EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, SimulationConfig.TaskConfig taskConfig, SimulationConfig.EdgeConfig edgeConfig, long seed) throws Exception {
        this(name, capacity, profile, taskConfig, edgeConfig, seed,
                FogDeviceFactory.build(capacity, new FogLinearPowerModel(40, 5)));
    }

    private EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, SimulationConfig.TaskConfig taskConfig, SimulationConfig.EdgeConfig edgeConfig, long seed,
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
        this.tasksToSend = taskConfig.getTasksPerEdge();
        this.meanInterArrival = taskConfig.getMeanInterArrivalSeconds();
        this.random = new Random(seed);
    }

    @Override
    public void startEntity() {
        super.startEntity();
        scheduleNext(0);
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
        double transmissionSeconds = sampled.getContainerSizeMb() / Math.max(1, edgeConfig.getBandwidthMbps());
        double latencySeconds = edgeConfig.getLatencyMs() / 1000.0;
        double totalDelay = delay + transmissionSeconds + latencySeconds;
        send(getParentId(), totalDelay, CollabSimTags.TASK_ARRIVAL_EVENT, new TaskProfile(sampled, CloudSim.clock() + totalDelay));
        sentCount++;
        double nextDelay = applyJitter(exponential(meanInterArrival));
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
        p.setWorkMi(sample(taskConfig.getWorkMiRange(), baseProfile.getWorkMi()));
        p.setRamMb((int) sample(taskConfig.getRamRange(), baseProfile.getRamMb()));
        p.setBandwidth(sample(taskConfig.getBandwidthRange(), baseProfile.getBandwidth()));
        p.setContainerSizeMb(sample(taskConfig.getContainerSizeRange(), baseProfile.getContainerSizeMb()));
        p.setDeadlineSeconds(sample(taskConfig.getDeadlineRange(), baseProfile.getDeadlineSeconds()));
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
}
