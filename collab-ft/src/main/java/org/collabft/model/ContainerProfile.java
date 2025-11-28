package org.collabft.model;

/**
 * Describes the resource demand of a single containerized task.
 */
public class ContainerProfile {
    /** Per-second CPU share required to run (MIPS). */
    private double demandMips = 500;
    /** Desired runtime in seconds when given the requested demand. */
    private double runtimeSeconds = 60;
    private int ramMb = 256;
    private double bandwidth = 500;
    private double containerSizeMb = 50;
    private double deadlineSeconds = 300;

    public double getDemandMips() {
        return demandMips;
    }

    public void setDemandMips(double demandMips) {
        this.demandMips = demandMips;
    }

    public double getRuntimeSeconds() {
        return runtimeSeconds;
    }

    public void setRuntimeSeconds(double runtimeSeconds) {
        this.runtimeSeconds = runtimeSeconds;
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = ramMb;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    public double getContainerSizeMb() {
        return containerSizeMb;
    }

    public void setContainerSizeMb(double containerSizeMb) {
        this.containerSizeMb = containerSizeMb;
    }

    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public void setDeadlineSeconds(double deadlineSeconds) {
        this.deadlineSeconds = deadlineSeconds;
    }
}
