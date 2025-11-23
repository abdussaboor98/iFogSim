package org.fog.entities;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.container.ContainerHost;
import org.fog.entities.container.ContainerInstance;
import org.fog.entities.container.ContainerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a physical compute server that lives inside a {@link FogDevice}.
 * A fog node can own multiple servers to model realistic deployments and distribute workloads.
 */
public class FogServer implements ContainerHost {

    private final String id;
    private final double totalCpuMips;
    private final long totalRam;
    private final long totalStorage;
    private final long totalBandwidth;

    private final List<ContainerInstance> containers = new ArrayList<>();
    private double cpuAllocated;
    private long ramAllocated;
    private long storageAllocated;
    private long bandwidthAllocated;

    private FogServerHealthState healthState;
    private FogDevice owner;

    public FogServer(String id, double totalCpuMips, long totalRam, long totalStorage, long totalBandwidth) {
        this.id = id;
        this.totalCpuMips = totalCpuMips;
        this.totalRam = totalRam;
        this.totalStorage = totalStorage;
        this.totalBandwidth = totalBandwidth;
        this.healthState = FogServerHealthState.HEALTHY;
    }

    public String getId() {
        return id;
    }

    public FogDevice getOwner() {
        return owner;
    }

    public void setOwner(FogDevice owner) {
        this.owner = owner;
    }

    public FogServerHealthState getHealthState() {
        return healthState;
    }

    public void setHealthState(FogServerHealthState healthState) {
        this.healthState = healthState;
    }

    public boolean isOperational() {
        return healthState != FogServerHealthState.FAILED;
    }

    public double getCpuUtilizationFraction() {
        return totalCpuMips == 0 ? 0 : cpuAllocated / totalCpuMips;
    }

    public boolean contains(ContainerInstance container) {
        return containers.contains(container);
    }

    public List<ContainerInstance> snapshotContainers() {
        return new ArrayList<>(containers);
    }

    @Override
    public String getHostName() {
        return owner != null ? owner.getName() + "/" + id : id;
    }

    @Override
    public double getTotalCpuMips() {
        return totalCpuMips;
    }

    @Override
    public double getAvailableCpuMips() {
        return Math.max(0, totalCpuMips - cpuAllocated);
    }

    @Override
    public long getTotalRam() {
        return totalRam;
    }

    @Override
    public long getAvailableRam() {
        return Math.max(0, totalRam - ramAllocated);
    }

    @Override
    public long getTotalBw() {
        return totalBandwidth;
    }

    @Override
    public long getAvailableBw() {
        return Math.max(0, totalBandwidth - bandwidthAllocated);
    }

    @Override
    public long getTotalStorage() {
        return totalStorage;
    }

    @Override
    public long getAvailableStorage() {
        return Math.max(0, totalStorage - storageAllocated);
    }

    @Override
    public List<ContainerInstance> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    @Override
    public boolean canHost(ContainerInstance container) {
        if (!isOperational()) {
            return false;
        }
        return getAvailableCpuMips() >= container.getCpuDemand()
                && getAvailableRam() >= container.getRamDemand()
                && getAvailableBw() >= container.getBandwidthDemand()
                && getAvailableStorage() >= container.getStorageDemand();
    }

    @Override
    public boolean allocateContainer(ContainerInstance container) {
        if (!canHost(container)) {
            return false;
        }
        containers.add(container);
        cpuAllocated += container.getCpuDemand();
        ramAllocated += container.getRamDemand();
        bandwidthAllocated += container.getBandwidthDemand();
        storageAllocated += container.getStorageDemand();
        container.assignHost(this, CloudSim.clock());
        return true;
    }

    @Override
    public void deallocateContainer(ContainerInstance container) {
        if (containers.remove(container)) {
            releaseContainerResources(container);
        }
    }

    @Override
    public void pauseContainer(ContainerInstance container) {
        container.pause();
    }

    @Override
    public void resumeContainer(ContainerInstance container) {
        container.resume(CloudSim.clock());
    }

    @Override
    public void updateContainers(double currentTime) {
        if (!isOperational()) {
            return;
        }
        Iterator<ContainerInstance> iterator = containers.iterator();
        while (iterator.hasNext()) {
            ContainerInstance container = iterator.next();
            if (container.getContainerState() == ContainerState.MIGRATING || container.getContainerState() == ContainerState.PAUSED) {
                continue;
            }
            if (container.getContainerState() == ContainerState.COMPLETED || container.getContainerState() == ContainerState.FAILED) {
                iterator.remove();
                releaseContainerResources(container);
                continue;
            }
            boolean finished = container.updateExecution(currentTime, container.getCpuDemand(), getTotalCpuMips());
            if (finished) {
                iterator.remove();
                releaseContainerResources(container);
            }
        }
    }

    private void releaseContainerResources(ContainerInstance container) {
        cpuAllocated = Math.max(0, cpuAllocated - container.getCpuDemand());
        ramAllocated = Math.max(0, ramAllocated - container.getRamDemand());
        bandwidthAllocated = Math.max(0, bandwidthAllocated - container.getBandwidthDemand());
        storageAllocated = Math.max(0, storageAllocated - container.getStorageDemand());
        container.detachHost();
    }
}
