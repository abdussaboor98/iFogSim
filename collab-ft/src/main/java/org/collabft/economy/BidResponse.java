package org.collabft.economy;

/**
 * Response returned by a fog node for a migration request.
 */
public class BidResponse {
    private final int bidderId;
    private final String containerId;
    private final boolean feasible;
    private final double score;

    public BidResponse(int bidderId, String containerId, boolean feasible, double score) {
        this.bidderId = bidderId;
        this.containerId = containerId;
        this.feasible = feasible;
        this.score = score;
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

    public double getScore() {
        return score;
    }
}
