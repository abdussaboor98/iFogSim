package org.collabft.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationConfig {

    private TopologyConfig topology = new TopologyConfig();
    private FaultConfig fault = new FaultConfig();
    private WorkloadConfig workload = new WorkloadConfig();
    private GossipConfig gossip = new GossipConfig();
    private EconomicsConfig economics = new EconomicsConfig();
    private SlaConfig sla = new SlaConfig();
    private RandomConfig random = new RandomConfig();
    private SimulationControl simulation = new SimulationControl();

    public TopologyConfig getTopology() {
        return topology;
    }

    public void setTopology(TopologyConfig topology) {
        this.topology = topology;
    }

    public FaultConfig getFault() {
        return fault;
    }

    public void setFault(FaultConfig fault) {
        this.fault = fault;
    }

    public WorkloadConfig getWorkload() {
        return workload;
    }

    public void setWorkload(WorkloadConfig workload) {
        this.workload = workload;
    }

    public GossipConfig getGossip() {
        return gossip;
    }

    public void setGossip(GossipConfig gossip) {
        this.gossip = gossip;
    }

    public EconomicsConfig getEconomics() {
        return economics;
    }

    public void setEconomics(EconomicsConfig economics) {
        this.economics = economics;
    }

    public SlaConfig getSla() {
        return sla;
    }

    public void setSla(SlaConfig sla) {
        this.sla = sla;
    }

    public RandomConfig getRandom() {
        return random;
    }

    public void setRandom(RandomConfig random) {
        this.random = random;
    }

    public SimulationControl getSimulation() {
        return simulation;
    }

    public void setSimulation(SimulationControl simulation) {
        this.simulation = simulation;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TopologyConfig {
        private int fogNodeCount = 0;
        private int serversPerFog = 0;
        private double serverCpu = 0.0;
        private double serverMem = 0.0;
        private double serverBw = 0.0;
        private double cloudCapacityMultiplier = 5.0;
        private List<EdgeDeviceConfig> edgeDevices = new ArrayList<>();

        public int getFogNodeCount() {
            return fogNodeCount;
        }

        public void setFogNodeCount(int fogNodeCount) {
            this.fogNodeCount = fogNodeCount;
        }

        public int getServersPerFog() {
            return serversPerFog;
        }

        public void setServersPerFog(int serversPerFog) {
            this.serversPerFog = serversPerFog;
        }

        public double getServerCpu() {
            return serverCpu;
        }

        public void setServerCpu(double serverCpu) {
            this.serverCpu = serverCpu;
        }

        public double getServerMem() {
            return serverMem;
        }

        public void setServerMem(double serverMem) {
            this.serverMem = serverMem;
        }

        public double getServerBw() {
            return serverBw;
        }

        public void setServerBw(double serverBw) {
            this.serverBw = serverBw;
        }

        public double getCloudCapacityMultiplier() {
            return cloudCapacityMultiplier;
        }

        public void setCloudCapacityMultiplier(double cloudCapacityMultiplier) {
            this.cloudCapacityMultiplier = cloudCapacityMultiplier;
        }

        public List<EdgeDeviceConfig> getEdgeDevices() {
            return edgeDevices;
        }

        public void setEdgeDevices(List<EdgeDeviceConfig> edgeDevices) {
            this.edgeDevices = edgeDevices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EdgeDeviceConfig {
        private String id;
        private int fogNodeId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getFogNodeId() {
            return fogNodeId;
        }

        public void setFogNodeId(int fogNodeId) {
            this.fogNodeId = fogNodeId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FaultConfig {
        private double poissonRatePerSec = 0.0;
        private int maxActiveFaults = 0;
        private int predictionLeadTimeSec = 60;
        private int recoveryTimeSec = 600;
        private long seed = 0L;

        public double getPoissonRatePerSec() {
            return poissonRatePerSec;
        }

        public void setPoissonRatePerSec(double poissonRatePerSec) {
            this.poissonRatePerSec = poissonRatePerSec;
        }

        public int getMaxActiveFaults() {
            return maxActiveFaults;
        }

        public void setMaxActiveFaults(int maxActiveFaults) {
            this.maxActiveFaults = maxActiveFaults;
        }

        public int getPredictionLeadTimeSec() {
            return predictionLeadTimeSec;
        }

        public void setPredictionLeadTimeSec(int predictionLeadTimeSec) {
            this.predictionLeadTimeSec = predictionLeadTimeSec;
        }

        public int getRecoveryTimeSec() {
            return recoveryTimeSec;
        }

        public void setRecoveryTimeSec(int recoveryTimeSec) {
            this.recoveryTimeSec = recoveryTimeSec;
        }

        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkloadConfig {
        private double arrivalRatePerSec = 0.0;
        private double minCpu = 0.0;
        private double maxCpu = 0.0;
        private double minMem = 0.0;
        private double maxMem = 0.0;
        private double minBw = 0.0;
        private double maxBw = 0.0;
        private double minSizeMb = 0.0;
        private double maxSizeMb = 0.0;
        private double minDeadlineSec = 0.0;
        private double maxDeadlineSec = 0.0;

        public double getArrivalRatePerSec() {
            return arrivalRatePerSec;
        }

        public void setArrivalRatePerSec(double arrivalRatePerSec) {
            this.arrivalRatePerSec = arrivalRatePerSec;
        }

        public double getMinCpu() {
            return minCpu;
        }

        public void setMinCpu(double minCpu) {
            this.minCpu = minCpu;
        }

        public double getMaxCpu() {
            return maxCpu;
        }

        public void setMaxCpu(double maxCpu) {
            this.maxCpu = maxCpu;
        }

        public double getMinMem() {
            return minMem;
        }

        public void setMinMem(double minMem) {
            this.minMem = minMem;
        }

        public double getMaxMem() {
            return maxMem;
        }

        public void setMaxMem(double maxMem) {
            this.maxMem = maxMem;
        }

        public double getMinBw() {
            return minBw;
        }

        public void setMinBw(double minBw) {
            this.minBw = minBw;
        }

        public double getMaxBw() {
            return maxBw;
        }

        public void setMaxBw(double maxBw) {
            this.maxBw = maxBw;
        }

        public double getMinSizeMb() {
            return minSizeMb;
        }

        public void setMinSizeMb(double minSizeMb) {
            this.minSizeMb = minSizeMb;
        }

        public double getMaxSizeMb() {
            return maxSizeMb;
        }

        public void setMaxSizeMb(double maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
        }

        public double getMinDeadlineSec() {
            return minDeadlineSec;
        }

        public void setMinDeadlineSec(double minDeadlineSec) {
            this.minDeadlineSec = minDeadlineSec;
        }

        public double getMaxDeadlineSec() {
            return maxDeadlineSec;
        }

        public void setMaxDeadlineSec(double maxDeadlineSec) {
            this.maxDeadlineSec = maxDeadlineSec;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GossipConfig {
        private int intervalSec = 60;
        private int stalenessIntervals = 3;

        public int getIntervalSec() {
            return intervalSec;
        }

        public void setIntervalSec(int intervalSec) {
            this.intervalSec = intervalSec;
        }

        public int getStalenessIntervals() {
            return stalenessIntervals;
        }

        public void setStalenessIntervals(int stalenessIntervals) {
            this.stalenessIntervals = stalenessIntervals;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EconomicsConfig {
        private double bidTimeoutSec = 5.0;
        private int bidFanoutK = 3;
        private double preselectThreshold = 0.3;
        private double tokenInitialBalance = 100.0;
        private double cloudBandwidthFactor = 0.7;

        public double getBidTimeoutSec() {
            return bidTimeoutSec;
        }

        public void setBidTimeoutSec(double bidTimeoutSec) {
            this.bidTimeoutSec = bidTimeoutSec;
        }

        public int getBidFanoutK() {
            return bidFanoutK;
        }

        public void setBidFanoutK(int bidFanoutK) {
            this.bidFanoutK = bidFanoutK;
        }

        public double getPreselectThreshold() {
            return preselectThreshold;
        }

        public void setPreselectThreshold(double preselectThreshold) {
            this.preselectThreshold = preselectThreshold;
        }

        public double getTokenInitialBalance() {
            return tokenInitialBalance;
        }

        public void setTokenInitialBalance(double tokenInitialBalance) {
            this.tokenInitialBalance = tokenInitialBalance;
        }

        public double getCloudBandwidthFactor() {
            return cloudBandwidthFactor;
        }

        public void setCloudBandwidthFactor(double cloudBandwidthFactor) {
            this.cloudBandwidthFactor = cloudBandwidthFactor;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlaConfig {
        private double reliabilityWeight = 0.6;
        private double urgencyWeight = 0.4;
        private double penaltyEta = 1.0;
        private double reputationInit = 0.5;
        private double reputationMin = 0.0;
        private double reputationMax = 1.0;
        private double urgencyScale = 1.0;

        public double getReliabilityWeight() {
            return reliabilityWeight;
        }

        public void setReliabilityWeight(double reliabilityWeight) {
            this.reliabilityWeight = reliabilityWeight;
        }

        public double getUrgencyWeight() {
            return urgencyWeight;
        }

        public void setUrgencyWeight(double urgencyWeight) {
            this.urgencyWeight = urgencyWeight;
        }

        public double getPenaltyEta() {
            return penaltyEta;
        }

        public void setPenaltyEta(double penaltyEta) {
            this.penaltyEta = penaltyEta;
        }

        public double getReputationInit() {
            return reputationInit;
        }

        public void setReputationInit(double reputationInit) {
            this.reputationInit = reputationInit;
        }

        public double getReputationMin() {
            return reputationMin;
        }

        public void setReputationMin(double reputationMin) {
            this.reputationMin = reputationMin;
        }

        public double getReputationMax() {
            return reputationMax;
        }

        public void setReputationMax(double reputationMax) {
            this.reputationMax = reputationMax;
        }

        public double getUrgencyScale() {
            return urgencyScale;
        }

        public void setUrgencyScale(double urgencyScale) {
            this.urgencyScale = urgencyScale;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RandomConfig {
        private long masterSeed = 0L;
        private long gossipSeed = 0L;
        private long faultSeed = 0L;
        private long workloadSeed = 0L;

        public long getMasterSeed() {
            return masterSeed;
        }

        public void setMasterSeed(long masterSeed) {
            this.masterSeed = masterSeed;
        }

        public long getGossipSeed() {
            return gossipSeed;
        }

        public void setGossipSeed(long gossipSeed) {
            this.gossipSeed = gossipSeed;
        }

        public long getFaultSeed() {
            return faultSeed;
        }

        public void setFaultSeed(long faultSeed) {
            this.faultSeed = faultSeed;
        }

        public long getWorkloadSeed() {
            return workloadSeed;
        }

        public void setWorkloadSeed(long workloadSeed) {
            this.workloadSeed = workloadSeed;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SimulationControl {
        private double durationSec = 0.0;

        public double getDurationSec() {
            return durationSec;
        }

        public void setDurationSec(double durationSec) {
            this.durationSec = durationSec;
        }
    }
}
