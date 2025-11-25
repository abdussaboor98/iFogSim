package org.collabft.model;

/**
 * Captures the most recent normalized load snapshot for a fog node.
 */
public class GossipStateEntry {
    private double cpuLoad;
    private double memLoad;
    private double bwLoad;
    private double timestamp;

    public GossipStateEntry() {
    }

    public GossipStateEntry(double cpuLoad, double memLoad, double bwLoad, double timestamp) {
        this.cpuLoad = cpuLoad;
        this.memLoad = memLoad;
        this.bwLoad = bwLoad;
        this.timestamp = timestamp;
    }

    public double getCpuLoad() {
        return cpuLoad;
    }

    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public double getMemLoad() {
        return memLoad;
    }

    public void setMemLoad(double memLoad) {
        this.memLoad = memLoad;
    }

    public double getBwLoad() {
        return bwLoad;
    }

    public void setBwLoad(double bwLoad) {
        this.bwLoad = bwLoad;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isStale(double now, double stalenessThresholdSeconds) {
        return now - timestamp > stalenessThresholdSeconds;
    }
}
