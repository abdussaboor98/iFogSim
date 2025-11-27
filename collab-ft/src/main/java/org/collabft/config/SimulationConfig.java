package org.collabft.config;

import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration object loaded from YAML.
 */
public class SimulationConfig {
    private Simulation simulation = new Simulation();
    private GossipConfig gossip = new GossipConfig();
    private FaultConfig fault = new FaultConfig();
    private BiddingConfig bidding = new BiddingConfig();
    private SlaConfig sla = new SlaConfig();
    private TaskConfig task = new TaskConfig();
    private Topology topology = new Topology();
    private Network network = new Network();

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public GossipConfig getGossip() {
        return gossip;
    }

    public void setGossip(GossipConfig gossip) {
        this.gossip = gossip;
    }

    public FaultConfig getFault() {
        return fault;
    }

    public void setFault(FaultConfig fault) {
        this.fault = fault;
    }

    public BiddingConfig getBidding() {
        return bidding;
    }

    public void setBidding(BiddingConfig bidding) {
        this.bidding = bidding;
    }

    public TaskConfig getTask() {
        return task;
    }

    public void setTask(TaskConfig task) {
        this.task = task;
    }

    public Topology getTopology() {
        return topology;
    }

    public void setTopology(Topology topology) {
        this.topology = topology;
    }

    public SlaConfig getSla() {
        return sla;
    }

    public void setSla(SlaConfig sla) {
        this.sla = sla;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public static class Simulation {
        private int mode = 1;
        private long seed = 1;
        private double durationSeconds = 3600;

        public int getMode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public long getSeed() {
            return seed;
        }

        public void setSeed(long seed) {
            this.seed = seed;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }

    public static class GossipConfig {
        private boolean enabled = true;
        private double intervalSeconds = 60;
        private int stalenessThresholdIntervals = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public int getStalenessThresholdIntervals() {
            return stalenessThresholdIntervals;
        }

        public void setStalenessThresholdIntervals(int stalenessThresholdIntervals) {
            this.stalenessThresholdIntervals = stalenessThresholdIntervals;
        }
    }

    public static class FaultConfig {
        private double predictionLeadSeconds = 60;
        private double meanTimeBetweenFailureSeconds = 600;
        private double recoverySeconds = 120;
        private double cpuFailureProb = 0.33;
        private double bandwidthDegradationProb = 0.34;
        private double serverCrashProb = 0.33;
        private double startDelaySeconds = 0;

        public double getPredictionLeadSeconds() {
            return predictionLeadSeconds;
        }

        public void setPredictionLeadSeconds(double predictionLeadSeconds) {
            this.predictionLeadSeconds = predictionLeadSeconds;
        }

        public double getMeanTimeBetweenFailureSeconds() {
            return meanTimeBetweenFailureSeconds;
        }

        public void setMeanTimeBetweenFailureSeconds(double meanTimeBetweenFailureSeconds) {
            this.meanTimeBetweenFailureSeconds = meanTimeBetweenFailureSeconds;
        }

        public double getRecoverySeconds() {
            return recoverySeconds;
        }

        public void setRecoverySeconds(double recoverySeconds) {
            this.recoverySeconds = recoverySeconds;
        }

        public double getCpuFailureProb() {
            return cpuFailureProb;
        }

        public void setCpuFailureProb(double cpuFailureProb) {
            this.cpuFailureProb = cpuFailureProb;
        }

        public double getBandwidthDegradationProb() {
            return bandwidthDegradationProb;
        }

        public void setBandwidthDegradationProb(double bandwidthDegradationProb) {
            this.bandwidthDegradationProb = bandwidthDegradationProb;
        }

        public double getServerCrashProb() {
            return serverCrashProb;
        }

        public void setServerCrashProb(double serverCrashProb) {
            this.serverCrashProb = serverCrashProb;
        }

        public double getStartDelaySeconds() {
            return startDelaySeconds;
        }

        public void setStartDelaySeconds(double startDelaySeconds) {
            this.startDelaySeconds = startDelaySeconds;
        }
    }

    public static class BiddingConfig {
        private int topK = 3;
        private double suitabilityThreshold = 0.3;
        private double responseTimeoutSeconds = 20;
        private double cpuUnitCost = 1.0;
        private double memUnitCost = 0.5;
        private double bwUnitCost = 0.2;
        private double failureWeight = 5.0;
        private double migrationRestoreFactor = 0.1;
        private double historicalPenaltyWeight = 1.0;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSuitabilityThreshold() {
            return suitabilityThreshold;
        }

        public void setSuitabilityThreshold(double suitabilityThreshold) {
            this.suitabilityThreshold = suitabilityThreshold;
        }

        public double getResponseTimeoutSeconds() {
            return responseTimeoutSeconds;
        }

        public void setResponseTimeoutSeconds(double responseTimeoutSeconds) {
            this.responseTimeoutSeconds = responseTimeoutSeconds;
        }

        public double getCpuUnitCost() {
            return cpuUnitCost;
        }

        public void setCpuUnitCost(double cpuUnitCost) {
            this.cpuUnitCost = cpuUnitCost;
        }

        public double getMemUnitCost() {
            return memUnitCost;
        }

        public void setMemUnitCost(double memUnitCost) {
            this.memUnitCost = memUnitCost;
        }

        public double getBwUnitCost() {
            return bwUnitCost;
        }

        public void setBwUnitCost(double bwUnitCost) {
            this.bwUnitCost = bwUnitCost;
        }

        public double getFailureWeight() {
            return failureWeight;
        }

        public void setFailureWeight(double failureWeight) {
            this.failureWeight = failureWeight;
        }

        public double getMigrationRestoreFactor() {
            return migrationRestoreFactor;
        }

        public void setMigrationRestoreFactor(double migrationRestoreFactor) {
            this.migrationRestoreFactor = migrationRestoreFactor;
        }

        public double getHistoricalPenaltyWeight() {
            return historicalPenaltyWeight;
        }

        public void setHistoricalPenaltyWeight(double historicalPenaltyWeight) {
            this.historicalPenaltyWeight = historicalPenaltyWeight;
        }
    }

    public static class TaskConfig {
        private int tasksPerEdge = 5;
        private double meanInterArrivalSeconds = 120;
        private double jitterPercent = 0;
        private ContainerProfile defaultProfile = new ContainerProfile();
        private Range cpuMiRange = new Range();
        private Range ramRange = new Range();
        private Range bandwidthRange = new Range();
        private Range containerSizeRange = new Range();
        private Range deadlineRange = new Range();

        public int getTasksPerEdge() {
            return tasksPerEdge;
        }

        public void setTasksPerEdge(int tasksPerEdge) {
            this.tasksPerEdge = tasksPerEdge;
        }

        public double getMeanInterArrivalSeconds() {
            return meanInterArrivalSeconds;
        }

        public void setMeanInterArrivalSeconds(double meanInterArrivalSeconds) {
            this.meanInterArrivalSeconds = meanInterArrivalSeconds;
        }

        public double getJitterPercent() {
            return jitterPercent;
        }

        public void setJitterPercent(double jitterPercent) {
            this.jitterPercent = jitterPercent;
        }

        public ContainerProfile getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(ContainerProfile defaultProfile) {
            this.defaultProfile = defaultProfile;
        }

        public Range getCpuMiRange() {
            return cpuMiRange;
        }

        public void setCpuMiRange(Range cpuMiRange) {
            this.cpuMiRange = cpuMiRange;
        }

        public Range getRamRange() {
            return ramRange;
        }

        public void setRamRange(Range ramRange) {
            this.ramRange = ramRange;
        }

        public Range getBandwidthRange() {
            return bandwidthRange;
        }

        public void setBandwidthRange(Range bandwidthRange) {
            this.bandwidthRange = bandwidthRange;
        }

        public Range getContainerSizeRange() {
            return containerSizeRange;
        }

        public void setContainerSizeRange(Range containerSizeRange) {
            this.containerSizeRange = containerSizeRange;
        }

        public Range getDeadlineRange() {
            return deadlineRange;
        }

        public void setDeadlineRange(Range deadlineRange) {
            this.deadlineRange = deadlineRange;
        }
    }

    public static class Topology {
        private List<FogNodeConfig> fogNodes = new ArrayList<>();
        private CloudConfig cloud = new CloudConfig();
        private EdgeConfig edge = new EdgeConfig();

        public List<FogNodeConfig> getFogNodes() {
            return fogNodes;
        }

        public void setFogNodes(List<FogNodeConfig> fogNodes) {
            this.fogNodes = fogNodes;
        }

        public CloudConfig getCloud() {
            return cloud;
        }

        public void setCloud(CloudConfig cloud) {
            this.cloud = cloud;
        }

        public EdgeConfig getEdge() {
            return edge;
        }

        public void setEdge(EdgeConfig edge) {
            this.edge = edge;
        }
    }

    public static class FogNodeConfig {
        private String name = "fog-node";
        private int servers = 3;
        private ResourceCapacity serverCapacity = new ResourceCapacity();
        private double capacityMultiplier = 1.0;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getServers() {
            return servers;
        }

        public void setServers(int servers) {
            this.servers = servers;
        }

        public ResourceCapacity getServerCapacity() {
            return serverCapacity;
        }

        public void setServerCapacity(ResourceCapacity serverCapacity) {
            this.serverCapacity = serverCapacity;
        }

        public double getCapacityMultiplier() {
            return capacityMultiplier;
        }

        public void setCapacityMultiplier(double capacityMultiplier) {
            this.capacityMultiplier = capacityMultiplier;
        }
    }

    public static class CloudConfig {
        private ResourceCapacity capacity = new ResourceCapacity(10000, 65536, 100000);

        public ResourceCapacity getCapacity() {
            return capacity;
        }

        public void setCapacity(ResourceCapacity capacity) {
            this.capacity = capacity;
        }
    }

    public static class EdgeConfig {
        private int devicesPerFog = 5;
        private double latencyMs = 0;
        private double bandwidthMbps = 1000;
        private double heterogeneityJitter = 0;

        public int getDevicesPerFog() {
            return devicesPerFog;
        }

        public void setDevicesPerFog(int devicesPerFog) {
            this.devicesPerFog = devicesPerFog;
        }

        public double getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(double latencyMs) {
            this.latencyMs = latencyMs;
        }

        public double getBandwidthMbps() {
            return bandwidthMbps;
        }

        public void setBandwidthMbps(double bandwidthMbps) {
            this.bandwidthMbps = bandwidthMbps;
        }

        public double getHeterogeneityJitter() {
            return heterogeneityJitter;
        }

        public void setHeterogeneityJitter(double heterogeneityJitter) {
            this.heterogeneityJitter = heterogeneityJitter;
        }
    }

    public static class SlaConfig {
        private double epsilon = 1e-3;
        private double urgencyK = 0.1;
        private double resourceWeight = 0.6;
        private double urgencyWeight = 0.4;
        private double penaltyEta = 1.0;

        public double getEpsilon() {
            return epsilon;
        }

        public void setEpsilon(double epsilon) {
            this.epsilon = epsilon;
        }

        public double getUrgencyK() {
            return urgencyK;
        }

        public void setUrgencyK(double urgencyK) {
            this.urgencyK = urgencyK;
        }

        public double getResourceWeight() {
            return resourceWeight;
        }

        public void setResourceWeight(double resourceWeight) {
            this.resourceWeight = resourceWeight;
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
    }

    public static class Network {
        private double interFogLatencyMs = 0;
        private double fogCloudLatencyMs = 0;
        private double interFogBandwidthMbps = 10000;
        private double fogCloudBandwidthMbps = 5000;

        public double getInterFogLatencyMs() {
            return interFogLatencyMs;
        }

        public void setInterFogLatencyMs(double interFogLatencyMs) {
            this.interFogLatencyMs = interFogLatencyMs;
        }

        public double getFogCloudLatencyMs() {
            return fogCloudLatencyMs;
        }

        public void setFogCloudLatencyMs(double fogCloudLatencyMs) {
            this.fogCloudLatencyMs = fogCloudLatencyMs;
        }

        public double getInterFogBandwidthMbps() {
            return interFogBandwidthMbps;
        }

        public void setInterFogBandwidthMbps(double interFogBandwidthMbps) {
            this.interFogBandwidthMbps = interFogBandwidthMbps;
        }

        public double getFogCloudBandwidthMbps() {
            return fogCloudBandwidthMbps;
        }

        public void setFogCloudBandwidthMbps(double fogCloudBandwidthMbps) {
            this.fogCloudBandwidthMbps = fogCloudBandwidthMbps;
        }
    }

    public static class Range {
        private double min = 0;
        private double max = 0;

        public double getMin() {
            return min;
        }

        public void setMin(double min) {
            this.min = min;
        }

        public double getMax() {
            return max;
        }

        public void setMax(double max) {
            this.max = max;
        }

        public boolean isConfigured() {
            return max > min && max > 0;
        }
    }
}
