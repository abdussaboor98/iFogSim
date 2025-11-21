package org.fog.entities.container;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

/**
 * Simulation entity that represents an executable container.
 * This class keeps track of resource demands, progress, checkpoints and migration state.
 */
public class ContainerInstance extends SimEntity {

    private final double cpuDemand;
    private final long ramDemand;
    private final long bandwidthDemand;
    private final long storageDemand;
    private final double totalExecutionTime;
    private double remainingExecutionTime;

    private ContainerState state;
    private ContainerHost currentHost;
    private double lastUpdateTime;

    private final double checkpointInterval;
    private double lastCheckpointTime;
    private final double checkpointSize;
    private double progressAtLastCheckpoint;

    public ContainerInstance(
            String name,
            double cpuDemand,
            long ramDemand,
            long bandwidthDemand,
            long storageDemand,
            double totalExecutionTime,
            double checkpointInterval,
            double checkpointSize) {
        super(name);
        this.cpuDemand = cpuDemand;
        this.ramDemand = ramDemand;
        this.bandwidthDemand = bandwidthDemand;
        this.storageDemand = storageDemand;
        this.totalExecutionTime = totalExecutionTime;
        this.remainingExecutionTime = totalExecutionTime;
        this.checkpointInterval = checkpointInterval;
        this.checkpointSize = checkpointSize;
        this.state = ContainerState.READY;
        this.lastCheckpointTime = 0;
        this.progressAtLastCheckpoint = 0;
        this.lastUpdateTime = 0;
    }

    /**
     * Called by the hosting entity every simulation tick to decrease the remaining execution time.
     *
     * @param currentTime current simulation time
     * @param allocatedCpu amount of MIPS allocated to this container
     * @param totalHostCpu host total CPU capacity
     * @return true when the container finishes its execution
     */
    public boolean updateExecution(double currentTime, double allocatedCpu, double totalHostCpu) {
        if (state != ContainerState.RUNNING) {
            return false;
        }
        double interval = currentTime - lastUpdateTime;
        if (interval <= 0) {
            return false;
        }

        double effectiveCpu = Math.min(allocatedCpu, totalHostCpu);
        double cpuFraction = cpuDemand <= 0 ? 0 : Math.min(1.0, effectiveCpu / cpuDemand);
        double workDone = interval * cpuFraction;
        remainingExecutionTime = Math.max(0, remainingExecutionTime - workDone);
        lastUpdateTime = currentTime;

        if (shouldCheckpoint(currentTime)) {
            createCheckpoint(currentTime);
        }

        if (remainingExecutionTime == 0) {
            state = ContainerState.COMPLETED;
            return true;
        }
        return false;
    }

    public void assignHost(ContainerHost host, double currentTime) {
        this.currentHost = host;
        this.state = ContainerState.RUNNING;
        this.lastUpdateTime = currentTime;
    }

    public void detachHost() {
        this.currentHost = null;
    }

    public double getCpuDemand() {
        return cpuDemand;
    }

    public long getRamDemand() {
        return ramDemand;
    }

    public long getBandwidthDemand() {
        return bandwidthDemand;
    }

    public long getStorageDemand() {
        return storageDemand;
    }

    public double getRemainingExecutionTime() {
        return remainingExecutionTime;
    }

    public double getTotalExecutionTime() {
        return totalExecutionTime;
    }

    public ContainerState getContainerState() {
        return state;
    }

    public void pause() {
        if (state == ContainerState.RUNNING) {
            state = ContainerState.PAUSED;
        }
    }

    public void resume(double currentTime) {
        if (state == ContainerState.PAUSED || state == ContainerState.READY) {
            state = ContainerState.RUNNING;
            this.lastUpdateTime = currentTime;
        }
    }

    public void markMigrating() {
        state = ContainerState.MIGRATING;
    }

    public void markFailed() {
        state = ContainerState.FAILED;
    }

    public ContainerHost getCurrentHost() {
        return currentHost;
    }

    public double getProgress() {
        return totalExecutionTime == 0 ? 1.0 : 1.0 - (remainingExecutionTime / totalExecutionTime);
    }

    public double getCheckpointInterval() {
        return checkpointInterval;
    }

    public double getCheckpointSize() {
        return checkpointSize;
    }

    public double getLastCheckpointTime() {
        return lastCheckpointTime;
    }

    public double getProgressAtLastCheckpoint() {
        return progressAtLastCheckpoint;
    }

    public double getTimeSinceLastCheckpoint(double currentTime) {
        return currentTime - lastCheckpointTime;
    }

    public boolean shouldCheckpoint(double currentTime) {
        return checkpointInterval > 0 && currentTime - lastCheckpointTime >= checkpointInterval;
    }

    public void createCheckpoint(double currentTime) {
        this.lastCheckpointTime = currentTime;
        this.progressAtLastCheckpoint = getProgress();
    }

    public void setLastUpdateTime(double lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public void startEntity() {
        // Containers are triggered by their hosts, no explicit start logic required.
    }

    @Override
    public void processEvent(SimEvent ev) {
        // Containers react to host callbacks, so no direct events need processing here.
    }

    @Override
    public void shutdownEntity() {
    }
}
