package org.collabft.model;

/**
 * Captures the instantaneous state of a single server within a fog node.
 * All load ratios are derived from the used/total pair so consumers can
 * rely on the normalized helpers.
 */
public class ServerState {

    private int fogNodeId;
    private int serverId;
    private double cpuTotal;
    private double cpuUsed;
    private double memTotal;
    private double memUsed;
    private double bwTotal;
    private double bwUsed;
    private boolean crashed;
    private final java.util.List<String> containerIds = new java.util.ArrayList<>();
    private double timestamp;

    public ServerState() {
        // Jackson/bean usage
    }

    public ServerState(int fogNodeId, int serverId, double cpuTotal, double memTotal, double bwTotal) {
        this.fogNodeId = fogNodeId;
        this.serverId = serverId;
        this.cpuTotal = cpuTotal;
        this.memTotal = memTotal;
        this.bwTotal = bwTotal;
    }

    public int getFogNodeId() {
        return fogNodeId;
    }

    public void setFogNodeId(int fogNodeId) {
        this.fogNodeId = fogNodeId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public double getCpuTotal() {
        return cpuTotal;
    }

    public void setCpuTotal(double cpuTotal) {
        this.cpuTotal = cpuTotal;
    }

    public double getCpuUsed() {
        return cpuUsed;
    }

    public void setCpuUsed(double cpuUsed) {
        this.cpuUsed = cpuUsed;
    }

    public double getMemTotal() {
        return memTotal;
    }

    public void setMemTotal(double memTotal) {
        this.memTotal = memTotal;
    }

    public double getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(double memUsed) {
        this.memUsed = memUsed;
    }

    public double getBwTotal() {
        return bwTotal;
    }

    public void setBwTotal(double bwTotal) {
        this.bwTotal = bwTotal;
    }

    public double getBwUsed() {
        return bwUsed;
    }

    public void setBwUsed(double bwUsed) {
        this.bwUsed = bwUsed;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public void setCrashed(boolean crashed) {
        this.crashed = crashed;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public java.util.List<String> getContainerIds() {
        return java.util.Collections.unmodifiableList(containerIds);
    }

    public void setContainerIds(java.util.List<String> ids) {
        containerIds.clear();
        if (ids != null) {
            containerIds.addAll(ids);
        }
    }

    public double cpuLoad() {
        return safeRatio(cpuUsed, cpuTotal);
    }

    public double memLoad() {
        return safeRatio(memUsed, memTotal);
    }

    public double bwLoad() {
        return safeRatio(bwUsed, bwTotal);
    }

    public double cpuFree() {
        return 1.0 - cpuLoad();
    }

    public double memFree() {
        return 1.0 - memLoad();
    }

    public double bwFree() {
        return 1.0 - bwLoad();
    }

    public double residualScore() {
        return cpuFree() + memFree() + bwFree();
    }

    public boolean hasContainer() {
        return !containerIds.isEmpty();
    }

    public boolean canHost(ContainerProfile profile) {
        if (crashed || profile == null) {
            return false;
        }
        return cpuLoad() + safeRatio(profile.getRequiredCpu(), cpuTotal) <= 1.0
                && memLoad() + safeRatio(profile.getRequiredMem(), memTotal) <= 1.0
                && bwLoad() + safeRatio(profile.getRequiredBw(), bwTotal) <= 1.0;
    }

    public void addContainer(ContainerProfile profile) {
        if (profile == null) {
            return;
        }
        containerIds.add(profile.getId());
        cpuUsed += profile.getRequiredCpu();
        memUsed += profile.getRequiredMem();
        bwUsed += profile.getRequiredBw();
    }

    public void removeContainer(ContainerProfile profile) {
        if (profile == null) {
            return;
        }
        if (containerIds.remove(profile.getId())) {
            cpuUsed = Math.max(0.0, cpuUsed - profile.getRequiredCpu());
            memUsed = Math.max(0.0, memUsed - profile.getRequiredMem());
            bwUsed = Math.max(0.0, bwUsed - profile.getRequiredBw());
        }
    }

    public String peekContainerId() {
        return containerIds.isEmpty() ? null : containerIds.get(0);
    }

    private double safeRatio(double used, double total) {
        return total <= 0 ? 0.0 : used / total;
    }
}
