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
    private TaskConfig task = new TaskConfig();
    private Topology topology = new Topology();

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
        private double intervalSeconds = 60;
        private int stalenessThresholdIntervals = 3;

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
    }

    public static class BiddingConfig {
        private int topK = 3;
        private double suitabilityThreshold = 0.3;
        private double responseTimeoutSeconds = 20;

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
    }

    public static class TaskConfig {
        private int tasksPerEdge = 5;
        private double meanInterArrivalSeconds = 120;
        private ContainerProfile defaultProfile = new ContainerProfile();

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

        public ContainerProfile getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(ContainerProfile defaultProfile) {
            this.defaultProfile = defaultProfile;
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

        public int getDevicesPerFog() {
            return devicesPerFog;
        }

        public void setDevicesPerFog(int devicesPerFog) {
            this.devicesPerFog = devicesPerFog;
        }
    }
}
