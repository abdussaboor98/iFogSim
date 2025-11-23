package org.collabft.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated state for a fog node that is shared via gossip.
 */
public class FogNodeState {

    private int nodeId;
    private double cpuTotal;
    private double cpuUsed;
    private double memTotal;
    private double memUsed;
    private double bwTotal;
    private double bwUsed;
    private int activeContainers;
    private int activeServers;
    private double timestamp;
    private long version;
    private final List<ServerState> servers = new ArrayList<>();

    public FogNodeState() {
        // Bean constructor
    }

    public FogNodeState(int nodeId) {
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
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

    public int getActiveContainers() {
        return activeContainers;
    }

    public void setActiveContainers(int activeContainers) {
        this.activeContainers = activeContainers;
    }

    public int getActiveServers() {
        return activeServers;
    }

    public void setActiveServers(int activeServers) {
        this.activeServers = activeServers;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<ServerState> getServers() {
        return Collections.unmodifiableList(servers);
    }

    public void setServers(List<ServerState> items) {
        servers.clear();
        if (items != null) {
            servers.addAll(items);
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

    public boolean isStale(double now, double stalenessSeconds) {
        return now - timestamp > stalenessSeconds;
    }

    private double safeRatio(double used, double total) {
        return total <= 0 ? 0.0 : used / total;
    }
}
