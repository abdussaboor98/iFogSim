package org.collabft.model;

/**
 * Response to a bid request with a cost breakdown.
 */
public class BidResponse {

    private long requestId;
    private int bidderFogId;
    private double costResource;
    private double costRisk;
    private double costMigration;
    private double respondedAt;
    private boolean accepted;

    public BidResponse() {
        // Bean constructor
    }

    public BidResponse(long requestId, int bidderFogId, double costResource, double costRisk,
                       double costMigration, double respondedAt) {
        this.requestId = requestId;
        this.bidderFogId = bidderFogId;
        this.costResource = costResource;
        this.costRisk = costRisk;
        this.costMigration = costMigration;
        this.respondedAt = respondedAt;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getBidderFogId() {
        return bidderFogId;
    }

    public void setBidderFogId(int bidderFogId) {
        this.bidderFogId = bidderFogId;
    }

    public double getCostResource() {
        return costResource;
    }

    public void setCostResource(double costResource) {
        this.costResource = costResource;
    }

    public double getCostRisk() {
        return costRisk;
    }

    public void setCostRisk(double costRisk) {
        this.costRisk = costRisk;
    }

    public double getCostMigration() {
        return costMigration;
    }

    public void setCostMigration(double costMigration) {
        this.costMigration = costMigration;
    }

    public double getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(double respondedAt) {
        this.respondedAt = respondedAt;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public double totalCost() {
        return costResource + costRisk + costMigration;
    }
}
