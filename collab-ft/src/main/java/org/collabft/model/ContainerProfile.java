package org.collabft.model;

/**
 * Describes the resource demand of a single containerized task.
 */
public class ContainerProfile {
    /** Total work (million instructions) the task must execute. */
    private double workMi = 500;
    /** Per-second CPU share required to run (MIPS). */
    private double demandMips = 500;
    private int ramMb = 256;
    private double bandwidth = 500;
    private double containerSizeMb = 50;
    private double deadlineSeconds = 300;

    public double getWorkMi() {
        return workMi;
    }

    public void setWorkMi(double workMi) {
        this.workMi = workMi;
    }

    public double getDemandMips() {
        return demandMips;
    }

    public void setDemandMips(double demandMips) {
        this.demandMips = demandMips;
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
