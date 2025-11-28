package org.collabft.economy;

/**
 * Response returned by a fog node for a migration request.
 */
public class BidResponse {
    private final int bidderId;
    private final String containerId;
    private final boolean feasible;
    private final double bidValue;
    private final double claimedBandwidthMbps;
    private final double claimedMigrationTimeSeconds;

    public BidResponse(int bidderId, String containerId, boolean feasible, double bidValue, double claimedBandwidthMbps, double claimedMigrationTimeSeconds) {
        this.bidderId = bidderId;
        this.containerId = containerId;
        this.feasible = feasible;
        this.bidValue = bidValue;
        this.claimedBandwidthMbps = claimedBandwidthMbps;
        this.claimedMigrationTimeSeconds = claimedMigrationTimeSeconds;
    }

    public int getBidderId() {
        return bidderId;
    }

    public String getContainerId() {
        return containerId;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public double getBidValue() {
        return bidValue;
    }

    public double getClaimedBandwidthMbps() {
        return claimedBandwidthMbps;
    }

    public double getClaimedMigrationTimeSeconds() {
        return claimedMigrationTimeSeconds;
    }
}
