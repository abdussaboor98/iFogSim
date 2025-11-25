package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.economy.BidManager;
import org.collabft.economy.BidResponse;
import org.collabft.economy.TokenManager;
import org.collabft.events.CollabSimTags;
import org.collabft.model.*;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.collabft.util.ScoringUtil;
import org.collabft.metrics.MetricsCollector;
import org.collabft.metrics.MetricsRegistry;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordinates initial placement, migration, and bidding for a fog node.
 */
public class FogNodeController extends FogDevice {
    private final List<FogServer> servers;
    private final SimulationConfig config;
    private final int mode;
    private final StateTable stateTable = new StateTable();
    private final BidManager bidManager = new BidManager();
    private final TokenManager tokenManager;
    private List<Integer> neighborIds = new ArrayList<>();
    private int cloudId = -1;
    private int schedulerId = -1;

    public FogNodeController(String name, SimulationConfig config, ResourceCapacity capacity, List<FogServer> servers, TokenManager tokenManager) throws Exception {
        this(name, config, capacity, servers, tokenManager,
                FogDeviceFactory.build(capacity, new FogLinearPowerModel(120, 20)));
    }

    private FogNodeController(String name, SimulationConfig config, ResourceCapacity capacity, List<FogServer> servers, TokenManager tokenManager,
                              Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.config = config;
        this.mode = config.getSimulation().getMode();
        this.servers = servers;
        this.tokenManager = tokenManager;
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            return;
        }
        if (!(ev.getTag() instanceof CollabSimTags tag)) {
            return;
        }
        switch (tag) {
            case TASK_ARRIVAL_EVENT -> handleTaskArrival(ev);
            case FAULT_EVENT -> handleFault(ev);
            case MIGRATION_REQUEST -> handleMigrationRequest(ev);
            case BID_RESPONSE -> handleBidResponse(ev);
            case MIGRATION_START -> handleMigrationStart(ev);
            case GOSSIP_EVENT -> handleGossip(ev);
            default -> {
            }
        }
    }

    private void handleTaskArrival(SimEvent ev) {
        if (!(ev.getData() instanceof TaskProfile profile)) {
            return;
        }
        ContainerModule module = new ContainerModule("container-" + profile.getTaskId(), "collab-app", getId(), profile.getContainerProfile());
        module.setOwnerFog(getName());
        // Track arrival time for SLA metrics
        module.setArrivalTime(CloudSim.clock());
        placeNewContainer(module);
    }

    private void handleFault(SimEvent ev) {
        if (!(ev.getData() instanceof FogServer server)) {
            return;
        }
        server.markFailed();
        for (ContainerModule container : new ArrayList<>(server.getContainers())) {
            server.removeContainer(container);
            migrateContainer(container, "fault_triggered");
        }
    }

    private void handleMigrationRequest(SimEvent ev) {
        if (!(ev.getData() instanceof MigrationRequest request)) {
            return;
        }
        ContainerModule container = request.getContainer();
        double bestScore = -1;
        FogServer target = null;
        for (FogServer server : servers) {
            double score = server.residualScore(container.getProfile());
            if (score > bestScore) {
                bestScore = score;
                target = server;
            }
        }
        boolean feasible = target != null && bestScore > 0;
        send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE,
                new BidResponse(getId(), container.getContainerId(), feasible, bestScore));
    }

    private void handleBidResponse(SimEvent ev) {
        if (!(ev.getData() instanceof BidResponse response)) {
            return;
        }
        bidManager.registerResponse(response);
        Optional<BidResponse> winner = bidManager.pickWinner(response.getContainerId());
        if (winner.isPresent()) {
            ContainerModule container = bidManager.getContainer(response.getContainerId());
            if (container != null) {
                send(winner.get().getBidderId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START, container);
                bidManager.clear(container.getContainerId());
                MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
            }
        }
    }

    private void handleMigrationStart(SimEvent ev) {
        if (!(ev.getData() instanceof ContainerModule container)) {
            return;
        }
        placeNewContainer(container);
        tokenManager.credit(getId(), 1.0);
        // Record migration completion time for metrics
        double start = container.getMigrationStart();
        if (start > 0) {
            MetricsRegistry.collector().recordMigration(
                    MetricsCollector.MigrationKind.INTER_FOG,
                    container,
                    start,
                    CloudSim.clock(),
                    0,
                    0,
                    true,
                    container.getMigrationTrigger());
        }
    }

    private void handleGossip(SimEvent ev) {
        if (!(ev.getData() instanceof Map<?, ?> table)) {
            return;
        }
        double now = CloudSim.clock();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (entry.getKey() instanceof Integer nodeId && entry.getValue() instanceof GossipStateEntry state) {
                stateTable.update(nodeId, new GossipStateEntry(state.getCpuLoad(), state.getMemLoad(), state.getBwLoad(), now));
            }
        }
    }

    private void placeNewContainer(ContainerModule container) {
        FogServer target = selectLocalServer(container.getProfile());
        if (target != null) {
            target.addContainer(container);
            return;
        }
        migrateContainer(container);
    }

    private FogServer selectLocalServer(ContainerProfile profile) {
        double bestScore = -1;
        FogServer target = null;
        for (FogServer server : servers) {
            double score = server.residualScore(profile);
            if (score > bestScore) {
                bestScore = score;
                target = server;
            }
        }
        return bestScore > 0 ? target : null;
    }

    private void migrateContainer(ContainerModule container) {
        migrateContainer(container, "score_fallback");
    }

    private void migrateContainer(ContainerModule container, String trigger) {
        container.setMigrationStart(CloudSim.clock());
        container.setMigrationTrigger(trigger);
        if (mode == 2 && schedulerId >= 0) {
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), CloudSim.clock(), CloudSim.clock(), true);
            send(schedulerId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_REQUEST,
                    new MigrationRequest(container, getId()));
            return;
        }
        ScoringUtil.Weights weights = ScoringUtil.computeWeights(container.getProfile(), CloudSim.clock());
        double stalenessSeconds = config.getGossip().getIntervalSeconds() * config.getGossip().getStalenessThresholdIntervals();
        List<ScoredNode> candidates = new ArrayList<>();
        double now = CloudSim.clock();
        for (Map.Entry<Integer, GossipStateEntry> entry : stateTable.getEntries().entrySet()) {
            if (entry.getValue().isStale(now, stalenessSeconds)) {
                continue;
            }
            double score = weights.alpha() * (1 - entry.getValue().getCpuLoad())
                    + weights.beta() * (1 - entry.getValue().getMemLoad())
                    + weights.gamma() * (1 - entry.getValue().getBwLoad());
            if (score >= config.getBidding().getSuitabilityThreshold()) {
                candidates.add(new ScoredNode(entry.getKey(), score));
            }
        }
        candidates.sort(Comparator.comparingDouble(ScoredNode::score).reversed());
        int limit = Math.min(config.getBidding().getTopK(), candidates.size());
        Set<Integer> bidderIds = candidates.stream().limit(limit).map(ScoredNode::nodeId).collect(Collectors.toSet());
        if (cloudId >= 0) {
            bidderIds.add(cloudId);
        }
        if (bidderIds.isEmpty()) {
            return;
        }
        bidManager.startBid(container, bidderIds);
        MigrationRequest request = new MigrationRequest(container, getId());
        for (Integer bidderId : bidderIds) {
            send(bidderId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_REQUEST, request);
        }
    }

    public void setNeighborIds(List<Integer> neighborIds) {
        this.neighborIds = neighborIds;
    }

    public void setCloudId(int cloudId) {
        this.cloudId = cloudId;
    }

    public void setSchedulerId(int schedulerId) {
        this.schedulerId = schedulerId;
    }

    public Map<Integer, GossipStateEntry> snapshotLoad() {
        double cpu = 0;
        double mem = 0;
        double bw = 0;
        int activeServers = 0;
        for (FogServer server : servers) {
            if (server.isFailed()) {
                continue;
            }
            cpu += server.getCpuLoad();
            mem += server.getMemLoad();
            bw += server.getBwLoad();
            activeServers++;
        }
        if (activeServers == 0) {
            return Map.of(getId(), new GossipStateEntry(1, 1, 1, CloudSim.clock()));
        }
        double cpuAvg = cpu / activeServers;
        double memAvg = mem / activeServers;
        double bwAvg = bw / activeServers;
        return Map.of(getId(), new GossipStateEntry(cpuAvg, memAvg, bwAvg, CloudSim.clock()));
    }

    private record ScoredNode(int nodeId, double score) {
    }

    public List<FogServer> getServers() {
        return servers;
    }
}
