package org.collabft.model;

/**
 * Describes the resource demand of a single containerized task.
 */
public class ContainerProfile {
    private double cpuMips = 500;
    private int ramMb = 256;
    private double bandwidth = 500;
    private double containerSizeMb = 50;
    private double deadlineSeconds = 300;

    public double getCpuMips() {
        return cpuMips;
    }

    public void setCpuMips(double cpuMips) {
        this.cpuMips = cpuMips;
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
