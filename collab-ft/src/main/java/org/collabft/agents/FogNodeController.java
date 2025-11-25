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
    private final ResourceCapacity capacity;
    private final SimulationConfig.Network network;
    private final StateTable stateTable = new StateTable();
    private final BidManager bidManager = new BidManager();
    private final TokenManager tokenManager;
    private final Map<Integer, Double> slaHistory = new HashMap<>();
    private int activeTransfers = 0;
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
        this.capacity = capacity;
        this.network = config.getNetwork();
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
            case MIGRATION_REQUEST, BID_REQUEST -> handleMigrationRequest(ev);
            case BID_RESPONSE -> handleBidResponse(ev);
            case BID_TIMEOUT -> handleBidTimeout(ev);
            case MIGRATION_START -> handleMigrationStart(ev);
            case MIGRATION_FINISH -> handleMigrationFinish(ev);
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
        if (!(ev.getData() instanceof FaultNotice notice)) {
            return;
        }
        FogServer server = notice.getServer();
        if (notice.isPrediction()) {
            // Preemptively migrate containers before failure hits
            for (ContainerModule container : new ArrayList<>(server.getContainers())) {
                server.removeContainer(container);
                migrateContainer(container, "fault_predicted_" + notice.getType().name().toLowerCase());
            }
            return;
        }
        // Apply actual fault effects
        switch (notice.getType()) {
            case CPU_FAILURE -> server.degradeCpu(0.2); // retain only 20% CPU
            case BANDWIDTH_DEGRADATION -> server.degradeBandwidth(0.3); // retain 30% BW
            case SERVER_CRASH -> server.markFailed();
            default -> {
            }
        }
        for (ContainerModule container : new ArrayList<>(server.getContainers())) {
            server.removeContainer(container);
            migrateContainer(container, "fault_" + notice.getType().name().toLowerCase());
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
        double cost = feasible ? computeBidCost(target, container.getProfile()) : Double.MAX_VALUE;
        send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE,
                new BidResponse(getId(), container.getContainerId(), feasible, bestScore, cost));
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
                MetricsCollector.MigrationKind kind = winner.get().getBidderId() == cloudId ? MetricsCollector.MigrationKind.CLOUD : MetricsCollector.MigrationKind.INTER_FOG;
                double linkBw = effectiveBandwidth(kind);
                double latency = linkLatency(kind);
                send(winner.get().getBidderId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                        new MigrationTransfer(container, getId(), sourceHost(container), kind, linkBw, latency, container.getMigrationTrigger()));
                bidManager.recordWinner(container.getContainerId(), winner.get());
                MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
                bidManager.clear(container.getContainerId());
                activeTransfers++;
            }
        }
    }

    private void handleMigrationStart(SimEvent ev) {
        if (!(ev.getData() instanceof MigrationTransfer transfer)) {
            return;
        }
        ContainerModule container = transfer.getContainer();
        container.setPaused(false);
        String from = transfer.getSourceName();
        FogServer host = placeContainerLocally(container);
        if (host == null) {
            send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                    new MigrationResult(container, transfer.getKind(), from, "unplaced", container.getMigrationStart(), CloudSim.clock(), 0, 0, false, transfer.getTrigger()));
            return;
        }
        double effectiveBw = transfer.getLinkBandwidthMbps() / Math.max(1, activeTransfers);
        double transferSeconds = effectiveBw > 0
                ? container.getProfile().getContainerSizeMb() / effectiveBw
                : 0;
        double finish = CloudSim.clock() + transferSeconds + transfer.getLatencySeconds();
        double overheadBw = container.getProfile().getContainerSizeMb();
        MetricsRegistry.collector().recordNetwork("migration", from, host.getName(), container.getProfile().getContainerSizeMb() * 1024 * 1024, CloudSim.clock());
        // Send completion back to origin for logging/payment
        send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                new MigrationResult(container, transfer.getKind(), from, host.getName(), container.getMigrationStart(), finish, 0, overheadBw, true, transfer.getTrigger()));
    }

    private void handleMigrationFinish(SimEvent ev) {
        if (!(ev.getData() instanceof MigrationResult result)) {
            return;
        }
        MetricsRegistry.collector().recordMigration(
                result.getKind(),
                result.getContainer(),
                result.getFrom(),
                result.getTo(),
                result.getStart(),
                result.getFinish(),
                result.getOverheadCpu(),
                result.getOverheadBw(),
                result.isSuccess(),
                result.getTrigger());
        settleSlaAndPayment(result);
        bidManager.clearWinner(result.getContainer().getContainerId());
        activeTransfers = Math.max(0, activeTransfers - 1);
    }

    private void handleGossip(SimEvent ev) {
        if (!(ev.getData() instanceof Map<?, ?> table)) {
            return;
        }
        double now = CloudSim.clock();
        double stalenessSeconds = config.getGossip().getIntervalSeconds() * config.getGossip().getStalenessThresholdIntervals();
        for (Map.Entry<?, ?> entry : table.entrySet()) {
            if (entry.getKey() instanceof Integer nodeId && entry.getValue() instanceof GossipStateEntry state) {
                // respect incoming timestamps for staleness handling
                stateTable.update(nodeId, state);
                String fogName = CloudSim.getEntityName(nodeId);
                boolean stale = state.isStale(now, stalenessSeconds);
                MetricsRegistry.collector().recordLoad(fogName, now, state.getCpuLoad(), state.getMemLoad(), state.getBwLoad(), stale);
            }
        }
    }

    private void placeNewContainer(ContainerModule container) {
        FogServer target = placeContainerLocally(container);
        if (target == null) {
            migrateContainer(container);
        }
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

    private FogServer placeContainerLocally(ContainerModule container) {
        FogServer target = selectLocalServer(container.getProfile());
        if (target != null) {
            target.addContainer(container);
            recordInstantLoad(target);
        }
        return target;
    }

    private void migrateContainer(ContainerModule container) {
        migrateContainer(container, "score_fallback");
    }

    private void migrateContainer(ContainerModule container, String trigger) {
        container.setMigrationStart(CloudSim.clock());
        container.setMigrationTrigger(trigger);
        container.setPaused(true);
        // Phase 1: try intra-fog
        String fromHost = sourceHost(container);
        FogServer localTarget = placeContainerLocally(container);
        if (localTarget != null) {
            container.setPaused(false);
            MetricsRegistry.collector().recordMigration(
                    MetricsCollector.MigrationKind.INTRA_FOG,
                    container,
                    fromHost,
                    localTarget.getName(),
                    container.getMigrationStart(),
                    CloudSim.clock(),
                    0,
                    0,
                    true,
                    trigger);
            settleSlaAndPayment(new MigrationResult(container, MetricsCollector.MigrationKind.INTRA_FOG, fromHost, localTarget.getName(), container.getMigrationStart(), CloudSim.clock(), 0, 0, true, trigger));
            return;
        }

        if (mode == 2 && schedulerId >= 0) {
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), CloudSim.clock(), CloudSim.clock(), true);
            send(schedulerId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_REQUEST,
                    new MigrationRequest(container, getId()));
            return;
        }
        ScoringUtil.Weights weights = ScoringUtil.computeWeights(container, CloudSim.clock());
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
            // fallback to cloud immediately
            if (cloudId >= 0) {
                send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                        new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, effectiveBandwidth(MetricsCollector.MigrationKind.CLOUD), linkLatency(MetricsCollector.MigrationKind.CLOUD), trigger));
                activeTransfers++;
            }
            return;
        }
        bidManager.startBid(container, bidderIds);
        MigrationRequest request = new MigrationRequest(container, getId());
        for (Integer bidderId : bidderIds) {
            send(bidderId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_REQUEST, request);
        }
        // Timeout fallback
        send(getId(), config.getBidding().getResponseTimeoutSeconds(), CollabSimTags.BID_TIMEOUT, container.getContainerId());
    }

    private void handleBidTimeout(SimEvent ev) {
        if (!(ev.getData() instanceof String containerId)) {
            return;
        }
        if (!bidManager.hasPending(containerId)) {
            return;
        }
        Optional<BidResponse> winner = bidManager.pickWinner(containerId);
        ContainerModule container = bidManager.getContainer(containerId);
        if (container == null) {
            bidManager.clear(containerId);
            return;
        }
        if (winner.isPresent()) {
            BidResponse w = winner.get();
            MetricsCollector.MigrationKind kind = w.getBidderId() == cloudId ? MetricsCollector.MigrationKind.CLOUD : MetricsCollector.MigrationKind.INTER_FOG;
            send(w.getBidderId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, getId(), sourceHost(container), kind, effectiveBandwidth(kind), linkLatency(kind), container.getMigrationTrigger()));
            bidManager.recordWinner(containerId, w);
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
        } else if (cloudId >= 0) {
            send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, effectiveBandwidth(MetricsCollector.MigrationKind.CLOUD), linkLatency(MetricsCollector.MigrationKind.CLOUD), container.getMigrationTrigger()));
        }
        bidManager.clear(containerId);
        activeTransfers++;
    }

    private double computeBidCost(FogServer target, ContainerProfile profile) {
        double cres = profile.getCpuMips() * config.getBidding().getCpuUnitCost()
                + profile.getRamMb() * config.getBidding().getMemUnitCost()
                + profile.getBandwidth() * config.getBidding().getBwUnitCost();
        double lCpu = target.getCpuLoad();
        double pfail = lCpu * 0.5;
        double crisk = pfail * config.getBidding().getFailureWeight();
        double linkBw = Math.max(1, Math.min(target.getCapacity().getBandwidth(), capacity.getUplinkBandwidth()));
        double ctransfer = profile.getContainerSizeMb() / linkBw;
        double cmig = ctransfer + config.getBidding().getMigrationRestoreFactor() * profile.getContainerSizeMb();
        double csla = slaHistory.getOrDefault(getId(), 0.0) * config.getBidding().getHistoricalPenaltyWeight();
        return cres + crisk + cmig + csla;
    }

    public void setNeighborIds(List<Integer> neighborIds) {
        this.neighborIds = neighborIds;
    }

    public List<Integer> getNeighborIds() {
        return neighborIds;
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

    public Map<Integer, GossipStateEntry> snapshotStateTable() {
        return new HashMap<>(stateTable.getEntries());
    }

    private record ScoredNode(int nodeId, double score) {
    }

    public List<FogServer> getServers() {
        return servers;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    private void recordInstantLoad(FogServer target) {
        var self = snapshotLoad().get(getId());
        if (self != null) {
            MetricsRegistry.collector().recordLoad(getName(), CloudSim.clock(), self.getCpuLoad(), self.getMemLoad(), self.getBwLoad(), false);
        }
        MetricsRegistry.collector().recordResource(getName(), target.getName(), CloudSim.clock(), target.getCpuLoad(), target.getMemLoad(), target.getBwLoad(), target.getContainers().size());
    }

    private void settleSlaAndPayment(MigrationResult result) {
        ContainerModule container = result.getContainer();
        double completionLatency = result.getFinish() - container.getArrivalTime();
        boolean violated = completionLatency > container.getDeadlineSeconds();

        // SLA_value components
        double rMax = capacity.getCpuMips() + capacity.getRamMb() + capacity.getBandwidth();
        double rNorm = (container.getProfile().getCpuMips() + container.getProfile().getRamMb() + container.getProfile().getBandwidth()) / rMax;
        double epsilon = config.getSla().getEpsilon();
        double k = config.getSla().getUrgencyK();
        double u = 1.0 / (container.getDeadlineSeconds() - result.getFinish() + epsilon);
        double uNorm = Math.min(1.0, u * k);
        double slaValue = config.getSla().getResourceWeight() * rNorm + config.getSla().getUrgencyWeight() * uNorm;

        BidResponse winner = bidManager.getWinner(container.getContainerId());
        double bidCost = winner != null ? winner.getCost() : 0.0;
        double eta = config.getSla().getPenaltyEta();
        double amount = violated ? Math.max(0, bidCost - eta * slaValue) : bidCost + slaValue;
        int payeeId = winner != null ? winner.getBidderId() : getId();
        MetricsRegistry.collector().recordSla(container.getContainerId(), violated, completionLatency, container.getDeadlineSeconds(), slaValue, amount);
        // Simple token settlement
        tokenManager.debit(getId(), amount);
        tokenManager.credit(payeeId, amount);
        MetricsRegistry.collector().recordPayment(getId(), payeeId, amount);
        // update historical penalty
        if (violated) {
            slaHistory.put(payeeId, slaHistory.getOrDefault(payeeId, 0.0) + slaValue);
        }
    }

    private double effectiveBandwidth(MetricsCollector.MigrationKind kind) {
        return switch (kind) {
            case CLOUD -> Math.min(capacity.getUplinkBandwidth(), network.getFogCloudBandwidthMbps());
            case INTER_FOG -> Math.min(capacity.getUplinkBandwidth(), network.getInterFogBandwidthMbps());
            default -> capacity.getUplinkBandwidth();
        };
    }

    private double linkLatency(MetricsCollector.MigrationKind kind) {
        return switch (kind) {
            case CLOUD -> network.getFogCloudLatencyMs() / 1000.0;
            case INTER_FOG -> network.getInterFogLatencyMs() / 1000.0;
            default -> 0.0;
        };
    }

    private String sourceHost(ContainerModule container) {
        return container.getHostName() != null ? container.getHostName() : getName();
    }
}
