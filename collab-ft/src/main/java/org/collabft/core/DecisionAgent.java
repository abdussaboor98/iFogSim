package org.collabft.core;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.model.BidRequest;
import org.collabft.model.BidResponse;
import org.collabft.model.ContainerProfile;
import org.collabft.model.FaultEvent;
import org.collabft.model.FogNodeState;
import org.collabft.model.PaymentReport;
import org.collabft.model.ServerState;
import org.collabft.model.Wallet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decision maker attached to a fog node. Handles container arrivals, bidding,
 * migration actions, and payment updates. Core heuristics arrive in later steps;
 * this class wires the event routing and keeps local server accounting.
 */
public class DecisionAgent extends SimEntity {

    private final int fogNodeId;
    private final SimulationConfig config;
    private final Wallet wallet;
    private final List<ServerState> servers;
    private final Map<Integer, FogNodeState> gossipView = new HashMap<>();
    private final FogNodeState localState;

    public DecisionAgent(int fogNodeId, List<ServerState> servers, SimulationConfig config) {
        super("decision-agent-" + fogNodeId);
        this.fogNodeId = fogNodeId;
        this.config = Objects.requireNonNull(config, "config");
        this.servers = new ArrayList<>(servers);
        this.wallet = new Wallet(fogNodeId, config.getEconomics().getTokenInitialBalance());
        this.localState = rebuildLocalState();
        gossipView.put(fogNodeId, localState);
    }

    @Override
    public void startEntity() {
        // No initial event scheduling required; reacts to incoming events.
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == SimulationEvents.EVT_FAULT_PREDICTED) {
            handleFaultPrediction((FaultEvent) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_HIT) {
            handleFaultHit((FaultEvent) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_RECOVERED) {
            handleRecovery((FaultEvent) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_CONTAINER_ARRIVAL) {
            handleContainerArrival((ContainerProfile) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_BID_REQUEST) {
            handleBidRequest((BidRequest) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_BID_RESPONSE) {
            handleBidResponse((BidResponse) ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_MIGRATION_COMPLETE) {
            handleMigrationComplete(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_PAYMENT) {
            handlePayment((PaymentReport) ev.getData());
        }
    }

    private void handleFaultPrediction(FaultEvent fault) {
        // Placeholder for phase 1-3 migration flow.
    }

    private void handleFaultHit(FaultEvent fault) {
        // Placeholder for applying crash effects to local server state.
    }

    private void handleRecovery(FaultEvent fault) {
        // Placeholder for restoring server capacity after a fault.
    }

    private void handleContainerArrival(ContainerProfile profile) {
        // Placeholder for workload placement and migration kickoff.
    }

    private void handleBidRequest(BidRequest request) {
        // Placeholder for bid computation; future steps will respond with BidResponse.
    }

    private void handleBidResponse(BidResponse response) {
        // Placeholder for evaluating received bids.
    }

    private void handleMigrationComplete(Object data) {
        // Placeholder for updating local state after migration completion or timeout.
    }

    private void handlePayment(PaymentReport payment) {
        // Placeholder for wallet settlement.
    }

    private FogNodeState rebuildLocalState() {
        FogNodeState state = new FogNodeState(fogNodeId);
        double cpuTotal = 0.0;
        double memTotal = 0.0;
        double bwTotal = 0.0;
        for (ServerState server : servers) {
            cpuTotal += server.getCpuTotal();
            memTotal += server.getMemTotal();
            bwTotal += server.getBwTotal();
        }
        state.setCpuTotal(cpuTotal);
        state.setMemTotal(memTotal);
        state.setBwTotal(bwTotal);
        state.setActiveServers(servers.size());
        state.setTimestamp(0.0);
        state.setServers(servers);
        return state;
    }

    @Override
    public void shutdownEntity() {
        // No-op
    }

    public int getFogNodeId() {
        return fogNodeId;
    }

    public List<ServerState> getServers() {
        return servers;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Map<Integer, FogNodeState> getGossipView() {
        return gossipView;
    }

    public FogNodeState getLocalState() {
        return localState;
    }
}
