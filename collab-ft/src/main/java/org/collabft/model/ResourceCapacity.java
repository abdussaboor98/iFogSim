package org.collabft.model;

/**
 * Simple container for resource capacities in the collab-ft simulation.
 */
public class ResourceCapacity {
    private long cpuMips = 2000;
    private int ramMb = 4096;
    private long bandwidth = 10000;
    private long storageMb = 100000;
    private double uplinkBandwidth = 10000;
    private double downlinkBandwidth = 10000;
    private double ratePerMips = 0.0;

    public ResourceCapacity() {
    }

    public ResourceCapacity(long cpuMips, int ramMb, long bandwidth) {
        this.cpuMips = cpuMips;
        this.ramMb = ramMb;
        this.bandwidth = bandwidth;
        this.storageMb = 100000;
        this.uplinkBandwidth = bandwidth;
        this.downlinkBandwidth = bandwidth;
    }

    public long getCpuMips() {
        return cpuMips;
    }

    public void setCpuMips(long cpuMips) {
        this.cpuMips = cpuMips;
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = ramMb;
    }

    public long getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(long bandwidth) {
        this.bandwidth = bandwidth;
    }

    public long getStorageMb() {
        return storageMb;
    }

    public void setStorageMb(long storageMb) {
        this.storageMb = storageMb;
    }

    public double getUplinkBandwidth() {
        return uplinkBandwidth;
    }

    public void setUplinkBandwidth(double uplinkBandwidth) {
        this.uplinkBandwidth = uplinkBandwidth;
    }

    public double getDownlinkBandwidth() {
        return downlinkBandwidth;
    }

    public void setDownlinkBandwidth(double downlinkBandwidth) {
        this.downlinkBandwidth = downlinkBandwidth;
    }

    public double getRatePerMips() {
        return ratePerMips;
    }

    public void setRatePerMips(double ratePerMips) {
        this.ratePerMips = ratePerMips;
    }
}
