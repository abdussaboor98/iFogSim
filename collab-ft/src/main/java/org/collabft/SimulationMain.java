package org.collabft;

import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.config.ConfigLoader;
import org.collabft.config.SimulationConfig;
import org.collabft.core.DecisionAgent;
import org.collabft.core.FaultScheduler;
import org.collabft.core.FaultPredictor;
import org.collabft.core.GossipAgent;
import org.collabft.core.IfogBuilder;
import org.collabft.core.MetricsSink;
import org.collabft.core.WorkloadAgent;
import org.collabft.model.FogNodeState;
import org.collabft.model.ServerState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entry point for the collaborative fault tolerance simulation.
 */
public final class SimulationMain {

    private SimulationMain() {
        // Utility class
    }

    public static void main(String[] args) {
        Path configPath = args != null && args.length > 0
                ? Path.of(args[0])
                : Path.of("collab-ft", "src", "main", "resources", "collabft-config.yaml");

        SimulationConfig config = ConfigLoader.load(configPath);
        initCloudSim();

        IfogBuilder builder = new IfogBuilder(config);
        IfogBuilder.Deployment deployment = builder.build();

        List<GossipAgent> gossipAgents = new ArrayList<>();
        List<DecisionAgent> decisionAgents = new ArrayList<>();
        List<Integer> decisionIds = new ArrayList<>();

        int fogCount = config.getTopology().getFogNodeCount();
        for (int i = 0; i < fogCount; i++) {
            GossipAgent gossip = new GossipAgent(i, fogCount, config);
            gossipAgents.add(gossip);
        }
        for (int i = 0; i < fogCount; i++) {
            List<ServerState> servers = Objects.requireNonNull(deployment.getServerStates().get(i), "servers for fog " + i);
            DecisionAgent decision = new DecisionAgent(i, servers, config);
            decisionAgents.add(decision);
            decisionIds.add(decision.getId());
            decision.seedGossipView(deployment.getFogStates());
        }
        for (int i = 0; i < fogCount; i++) {
            gossipAgents.get(i).setDecisionAgentId(decisionAgents.get(i).getId());
            decisionAgents.get(i).setGossipAgentId(gossipAgents.get(i).getId());
        }
        for (int i = 0; i < fogCount; i++) {
            FogNodeState state = deployment.getFogStates().get(i);
            if (state != null) {
                gossipAgents.get(i).updateLocalState(state);
            }
        }

        wireGossipNeighbors(gossipAgents);

        WorkloadAgent workloadAgent = new WorkloadAgent(config, decisionIds);
        FaultPredictor predictor = new FaultPredictor(config);
        FaultScheduler faultScheduler = predictor.buildScheduler(decisionIds);

        double simDuration = estimateSimulationDuration(config);
        if (simDuration > 0) {
            CloudSim.terminateSimulation(simDuration);
        }

        double endTime = CloudSim.startSimulation();
        CloudSim.stopSimulation();

        MetricsSink.get().flush(endTime);
        printSummary(endTime);
    }

    private static void initCloudSim() {
        int numUsers = 1;
        Calendar calendar = new GregorianCalendar();
        boolean traceFlag = false;
        CloudSim.init(numUsers, calendar, traceFlag);
    }

    private static void wireGossipNeighbors(List<GossipAgent> gossipAgents) {
        int n = gossipAgents.size();
        for (int i = 0; i < n; i++) {
            GossipAgent current = gossipAgents.get(i);
            GossipAgent next = gossipAgents.get((i + 1) % n);
            current.setNeighborAgentId(next.getId());
        }
    }

    private static double estimateSimulationDuration(SimulationConfig config) {
        double configured = config.getSimulation() != null ? config.getSimulation().getDurationSec() : 0.0;
        if (configured > 0) {
            return configured;
        }
        double deadline = config.getWorkload().getMaxDeadlineSec();
        double lead = config.getFault().getPredictionLeadTimeSec();
        double recovery = config.getFault().getRecoveryTimeSec();
        double base = Math.max(600.0, deadline + recovery + lead);
        return base;
    }

    private static void printSummary(double endTime) {
        Map<String, Object> summary = MetricsSink.get().snapshot(endTime);
        System.out.println("=== collab-ft simulation complete ===");
        System.out.println("endTime=" + endTime);
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("Outputs written under results/collab-ft/");
    }
}
