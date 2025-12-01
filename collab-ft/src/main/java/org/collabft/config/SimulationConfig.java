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
    private TrustConfig trust = new TrustConfig();
    private TaskConfig task = new TaskConfig();
    private Topology topology = new Topology();
    private Network network = new Network();
    private SlaConfig sla = new SlaConfig();
    private MetricsConfig metrics = new MetricsConfig();

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

    public TrustConfig getTrust() {
        return trust;
    }

    public void setTrust(TrustConfig trust) {
        this.trust = trust;
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

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public SlaConfig getSla() {
        return sla;
    }

    public void setSla(SlaConfig sla) {
        this.sla = sla;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
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
        private double pauseSeconds = 1.0;
        private double resumeSeconds = 1.0;
        private double resourceImpactK = 0.5;
        private int maxFogBidders = 3;
        private double bidTimeoutSeconds = 30.0;
        private double retryMigrationSeconds = 1.0;
        private int maxMigrationRetries = 10;

        public double getPauseSeconds() {
            return pauseSeconds;
        }

        public void setPauseSeconds(double pauseSeconds) {
            this.pauseSeconds = pauseSeconds;
        }

        public double getResumeSeconds() {
            return resumeSeconds;
        }

        public void setResumeSeconds(double resumeSeconds) {
            this.resumeSeconds = resumeSeconds;
        }

        public double getResourceImpactK() {
            return resourceImpactK;
        }

        public void setResourceImpactK(double resourceImpactK) {
            this.resourceImpactK = resourceImpactK;
        }

        /**
         * Backwards-compatibility for configs that still use the old key.
         */
        public void setkResourceImpact(double kResourceImpact) {
            this.resourceImpactK = kResourceImpact;
        }

        public int getMaxFogBidders() {
            return maxFogBidders;
        }

        public void setMaxFogBidders(int maxFogBidders) {
            this.maxFogBidders = maxFogBidders;
        }

        public double getBidTimeoutSeconds() {
            return bidTimeoutSeconds;
        }

        public void setBidTimeoutSeconds(double bidTimeoutSeconds) {
            this.bidTimeoutSeconds = bidTimeoutSeconds;
        }

        public double getRetryMigrationSeconds() {
            return retryMigrationSeconds;
        }

        public void setRetryMigrationSeconds(double retryMigrationSeconds) {
            this.retryMigrationSeconds = retryMigrationSeconds;
        }

        public int getMaxMigrationRetries() {
            return maxMigrationRetries;
        }

        public void setMaxMigrationRetries(int maxMigrationRetries) {
            this.maxMigrationRetries = maxMigrationRetries;
        }
    }

    public static class SlaConfig {
        private double slackRatio = 0.3;
        private double netOverheadRatio = 0.10;

        public double getSlackRatio() {
            return slackRatio;
        }

        public void setSlackRatio(double slackRatio) {
            this.slackRatio = slackRatio;
        }

        public double getNetOverheadRatio() {
            return netOverheadRatio;
        }

        public void setNetOverheadRatio(double netOverheadRatio) {
            this.netOverheadRatio = netOverheadRatio;
        }
    }

    public static class TrustConfig {
        private boolean enableTrust = true;
        private double trustDecayFactor = 0.5;
        private double trustRecoveryFactor = 0.05;
        private double trustThreshold = 0.5;
        private double tauBw = 0.2;
        private double tauMig = 0.5;
        private double tauSla = 0.1;
        private double slaSuccessBonus = 0.02;
        private double slaViolationPenalty = 0.10;
        private double paymentPenaltyLambda = 0.25;
        private double paymentPenaltyMu = 0.25;
        private double passiveRecoveryRate = 0.02;
        private double passiveRecoveryInterval = 21;

        public boolean isEnableTrust() {
            return enableTrust;
        }

        public void setEnableTrust(boolean enableTrust) {
            this.enableTrust = enableTrust;
        }

        public double getTrustDecayFactor() {
            return trustDecayFactor;
        }

        public void setTrustDecayFactor(double trustDecayFactor) {
            this.trustDecayFactor = trustDecayFactor;
        }

        public double getTrustRecoveryFactor() {
            return trustRecoveryFactor;
        }

        public void setTrustRecoveryFactor(double trustRecoveryFactor) {
            this.trustRecoveryFactor = trustRecoveryFactor;
        }

        public double getTrustThreshold() {
            return trustThreshold;
        }

        public void setTrustThreshold(double trustThreshold) {
            this.trustThreshold = trustThreshold;
        }

        public double getTauBw() {
            return tauBw;
        }

        public void setTauBw(double tauBw) {
            this.tauBw = tauBw;
        }

        public double getTauMig() {
            return tauMig;
        }

        public void setTauMig(double tauMig) {
            this.tauMig = tauMig;
        }

        public double getTauSla() {
            return tauSla;
        }

        public void setTauSla(double tauSla) {
            this.tauSla = tauSla;
        }

        public double getSlaSuccessBonus() {
            return slaSuccessBonus;
        }

        public void setSlaSuccessBonus(double slaSuccessBonus) {
            this.slaSuccessBonus = slaSuccessBonus;
        }

        public double getSlaViolationPenalty() {
            return slaViolationPenalty;
        }

        public void setSlaViolationPenalty(double slaViolationPenalty) {
            this.slaViolationPenalty = slaViolationPenalty;
        }

        public double getPaymentPenaltyLambda() {
            return paymentPenaltyLambda;
        }

        public void setPaymentPenaltyLambda(double paymentPenaltyLambda) {
            this.paymentPenaltyLambda = paymentPenaltyLambda;
        }

        public double getPaymentPenaltyMu() {
            return paymentPenaltyMu;
        }

        public void setPaymentPenaltyMu(double paymentPenaltyMu) {
            this.paymentPenaltyMu = paymentPenaltyMu;
        }

        public double getPassiveRecoveryRate() {
            return passiveRecoveryRate;
        }

        public void setPassiveRecoveryRate(double passiveRecoveryRate) {
            this.passiveRecoveryRate = passiveRecoveryRate;
        }

        public double getPassiveRecoveryInterval() {
            return passiveRecoveryInterval;
        }

        public void setPassiveRecoveryInterval(double passiveRecoveryInterval) {
            this.passiveRecoveryInterval = passiveRecoveryInterval;
        }
    }

    public static class TaskConfig {
        private int tasksPerEdge = 20;
        private double meanInterArrivalSeconds = 180;
        private double jitterPercent = 20;
        private ContainerProfile defaultProfile = new ContainerProfile();
        private Range runtimeRange = new Range();
        private Range demandMipsRange = new Range();
        private Range ramRange = new Range();
        private Range bandwidthRange = new Range();
        private Range containerSizeRange = new Range();
        private double slaRuntimeMultiplier = 0.2;
        private double taskGenerationCutoffBufferRatio = 1.5;

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

        public Range getDemandMipsRange() {
            return demandMipsRange;
        }

        public void setDemandMipsRange(Range demandMipsRange) {
            this.demandMipsRange = demandMipsRange;
        }

        public Range getRuntimeRange() {
            return runtimeRange;
        }

        public void setRuntimeRange(Range runtimeRange) {
            this.runtimeRange = runtimeRange;
        }

        public double getSlaRuntimeMultiplier() {
            return slaRuntimeMultiplier;
        }

        public void setSlaRuntimeMultiplier(double slaRuntimeMultiplier) {
            this.slaRuntimeMultiplier = slaRuntimeMultiplier;
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

        public double getTaskGenerationCutoffBufferRatio() {
            return taskGenerationCutoffBufferRatio;
        }

        public void setTaskGenerationCutoffBufferRatio(double taskGenerationCutoffBufferRatio) {
            this.taskGenerationCutoffBufferRatio = taskGenerationCutoffBufferRatio;
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
        private int edgeDevices = 0;

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

        public int getEdgeDevices() {
            return edgeDevices;
        }

        public void setEdgeDevices(int edgeDevices) {
            this.edgeDevices = edgeDevices;
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
        private double retryInitialPlacementSeconds = 1.0;
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

        public double getRetryInitialPlacementSeconds() {
            return retryInitialPlacementSeconds;
        }

        public void setRetryInitialPlacementSeconds(double retryInitialPlacementSeconds) {
            this.retryInitialPlacementSeconds = retryInitialPlacementSeconds;
        }

        public void setHeterogeneityJitter(double heterogeneityJitter) {
            this.heterogeneityJitter = heterogeneityJitter;
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

    public static class MetricsConfig {
        /** Write per-task tracking CSV files by default (task_status/dropped_tasks). */
        private boolean perTaskCsv = true;

        public boolean isPerTaskCsv() {
            return perTaskCsv;
        }

        public void setPerTaskCsv(boolean perTaskCsv) {
            this.perTaskCsv = perTaskCsv;
        }
    }
}
