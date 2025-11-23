package org.collabft.model;

/**
 * Extends a container profile with execution bookkeeping for migration lifecycle.
 */
public class ContainerTask extends ContainerProfile {

    private double bidCost;
    private int originFogId;

    public ContainerTask() {
        // Bean
    }

    public ContainerTask(ContainerProfile base) {
        super(base.getId(), base.getHomeFogId(), base.getRequiredCpu(), base.getRequiredMem(),
                base.getRequiredBw(), base.getSizeMb(), base.getDeadlineSec(), base.getArrivalTime());
        this.originFogId = base.getHomeFogId();
    }

    public double getBidCost() {
        return bidCost;
    }

    public void setBidCost(double bidCost) {
        this.bidCost = bidCost;
    }

    public int getOriginFogId() {
        return originFogId;
    }

    public void setOriginFogId(int originFogId) {
        this.originFogId = originFogId;
    }
}
