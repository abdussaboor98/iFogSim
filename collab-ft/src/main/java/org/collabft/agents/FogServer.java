package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.collabft.util.ResourceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single physical server within a fog node.
 */
public class FogServer extends FogDevice {
    private final ResourceCapacity capacity;
    private final List<ContainerModule> containers = new ArrayList<>();
    private double usedCpu;
    private double usedRam;
    private double usedBw;
    private boolean failed;
    private double cpuFactor = 1.0;
    private double bwFactor = 1.0;
    private boolean faultActive;
    private double predictedFailureAt = -1;
    private final int concurrencyLimit;

    public FogServer(String name, ResourceCapacity capacity, int concurrencyLimit) throws Exception {
        this(name, capacity, concurrencyLimit, FogDeviceFactory.build(capacity, new FogLinearPowerModel(100, 10)));
    }

    private FogServer(String name, ResourceCapacity capacity, int concurrencyLimit, Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.capacity = capacity;
        this.concurrencyLimit = concurrencyLimit;
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            return;
        }
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.RECOVERY_EVENT) {
            recover();
        }
    }

    public boolean canHost(ContainerProfile profile) {
        return !isFaulted() && containers.size() < concurrencyLimit
                && ResourceUtil.feasible(capacity, usedCpu, usedRam, usedBw, profile, cpuFactor, bwFactor);
    }

    public double residualScore(ContainerProfile profile) {
        if (isFaulted() || containers.size() >= concurrencyLimit) {
            return -1;
        }
        return ResourceUtil.residualScore(capacity, usedCpu, usedRam, usedBw, profile, cpuFactor, bwFactor);
    }

    public void addContainer(ContainerModule container) {
        containers.add(container);
        usedCpu += container.getProfile().getCpuMips();
        usedRam += container.getProfile().getRamMb();
        usedBw += container.getProfile().getBandwidth();
        container.setHostName(getName());
    }

    public void removeContainer(ContainerModule container) {
        containers.remove(container);
        usedCpu -= container.getProfile().getCpuMips();
        usedRam -= container.getProfile().getRamMb();
        usedBw -= container.getProfile().getBandwidth();
    }

    public List<ContainerModule> getContainers() {
        return containers;
    }

    public double getCpuLoad() {
        return usedCpu / (capacity.getCpuMips() * cpuFactor);
    }

    public double getMemLoad() {
        return usedRam / capacity.getRamMb();
    }

    public double getBwLoad() {
        return usedBw / (capacity.getBandwidth() * bwFactor);
    }

    public void markFaultActive() {
        this.faultActive = true;
    }

    public void markFailed() {
        this.failed = true;
        this.faultActive = true;
    }

    public boolean isFailed() { return failed; }

    public void markPredictedFailure(double failureTime) {
        this.predictedFailureAt = failureTime;
    }

    public boolean isPredictedToFail() { return predictedFailureAt >= 0 && CloudSim.clock() < predictedFailureAt; }

    public boolean isFaulted() { return failed || faultActive || isPredictedToFail(); }

    public void clearPrediction() {
        this.predictedFailureAt = -1;
    }

    public void degradeCpu(double factor) {
        this.faultActive = true;
        this.cpuFactor = factor;
    }

    public void degradeBandwidth(double factor) {
        this.faultActive = true;
        this.bwFactor = factor;
    }

    public void recover() {
        this.failed = false;
        this.cpuFactor = 1.0;
        this.bwFactor = 1.0;
        this.faultActive = false;
        this.predictedFailureAt = -1;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    public double getCpuFactor() {
        return cpuFactor;
    }
}
