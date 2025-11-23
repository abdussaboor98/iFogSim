package org.collabft.model;

/**
 * Describes the workload requirements and timing for a single container.
 */
public class ContainerProfile {

    private String id;
    private int homeFogId;
    private double requiredCpu;
    private double requiredMem;
    private double requiredBw;
    private double sizeMb;
    private double deadlineSec;
    private double arrivalTime;

    public ContainerProfile() {
        // Bean constructor
    }

    public ContainerProfile(String id, int homeFogId, double requiredCpu, double requiredMem, double requiredBw,
                            double sizeMb, double deadlineSec, double arrivalTime) {
        this.id = id;
        this.homeFogId = homeFogId;
        this.requiredCpu = requiredCpu;
        this.requiredMem = requiredMem;
        this.requiredBw = requiredBw;
        this.sizeMb = sizeMb;
        this.deadlineSec = deadlineSec;
        this.arrivalTime = arrivalTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getHomeFogId() {
        return homeFogId;
    }

    public void setHomeFogId(int homeFogId) {
        this.homeFogId = homeFogId;
    }

    public double getRequiredCpu() {
        return requiredCpu;
    }

    public void setRequiredCpu(double requiredCpu) {
        this.requiredCpu = requiredCpu;
    }

    public double getRequiredMem() {
        return requiredMem;
    }

    public void setRequiredMem(double requiredMem) {
        this.requiredMem = requiredMem;
    }

    public double getRequiredBw() {
        return requiredBw;
    }

    public void setRequiredBw(double requiredBw) {
        this.requiredBw = requiredBw;
    }

    public double getSizeMb() {
        return sizeMb;
    }

    public void setSizeMb(double sizeMb) {
        this.sizeMb = sizeMb;
    }

    public double getDeadlineSec() {
        return deadlineSec;
    }

    public void setDeadlineSec(double deadlineSec) {
        this.deadlineSec = deadlineSec;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(double arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public double totalRequirement() {
        return requiredCpu + requiredMem + requiredBw;
    }

    public double cpuProportion() {
        return safeRatio(requiredCpu, totalRequirement());
    }

    public double memProportion() {
        return safeRatio(requiredMem, totalRequirement());
    }

    public double bwProportion() {
        return safeRatio(requiredBw, totalRequirement());
    }

    private double safeRatio(double value, double total) {
        return total <= 0 ? 0.0 : value / total;
    }
}
