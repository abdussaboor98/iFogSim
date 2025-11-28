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
    private final Queue<ContainerModule> pendingMigrations = new ArrayDeque<>();
    private List<Integer> neighborIds = new ArrayList<>();
    private List<Integer> allFogIds = new ArrayList<>();
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
            case MIGRATION_START -> handleMigrationStart(ev);
            case MIGRATION_FINISH -> handleMigrationFinish(ev);
            case GOSSIP_EVENT -> handleGossip(ev);
            case TASK_COMPLETE -> handleTaskComplete(ev);
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
        module.setOwnerId(getId());
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
            double cpuLoad = server.getCpuLoad();
            double memLoad = server.getMemLoad();
            double bwLoad = server.getBwLoad();
            int runningContainers = server.getContainers().size();
            MetricsRegistry.collector().recordFaultPrediction(getName(), server.getName(), notice.getType().name().toLowerCase(),
                    CloudSim.clock(), cpuLoad, memLoad, bwLoad, runningContainers);
            server.markPredictedFailure(notice.getFailureTime());
            // Preemptively migrate containers before failure hits
            for (ContainerModule container : new ArrayList<>(server.getContainers())) {
                checkpointAndRemove(server, container);
                migrateContainer(container, "fault_predicted_" + notice.getType().name().toLowerCase());
            }
            return;
        }
        server.clearPrediction();
        server.markFaultActive();
        // Apply actual fault effects
        switch (notice.getType()) {
            case CPU_FAILURE -> server.degradeCpu(0.2); // retain only 20% CPU
            case BANDWIDTH_DEGRADATION -> server.degradeBandwidth(0.3); // retain 30% BW
            case SERVER_CRASH -> server.markFailed();
            default -> {
            }
        }
        for (ContainerModule container : new ArrayList<>(server.getContainers())) {
            checkpointAndRemove(server, container);
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
        double cost = feasible ? computeBidCost(container.getProfile()) : Double.MAX_VALUE;
        MetricsRegistry.collector().recordBid(container.getContainerId(), getId(), getName(), bestScore, cost, feasible, CloudSim.clock());
        send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE,
                new BidResponse(getId(), container.getContainerId(), feasible, bestScore, cost));
    }

    private void handleBidResponse(SimEvent ev) {
        if (!(ev.getData() instanceof BidResponse response)) {
            return;
        }
        bidManager.registerResponse(response);
        String containerId = response.getContainerId();
        MetricsRegistry.collector().recordBid(containerId, response.getBidderId(), CloudSim.getEntityName(response.getBidderId()), response.getScore(), response.getCost(), response.isFeasible(), CloudSim.clock());
        if (!bidManager.isComplete(containerId)) {
            return;
        }
        List<BidResponse> responses = bidManager.getResponses(containerId);
        Optional<BidResponse> winner = responses.stream()
                .filter(BidResponse::isFeasible)
                .min(Comparator.<BidResponse>comparingDouble(this::evaluatedCost)
                        .thenComparing(Comparator.comparingDouble(BidResponse::getScore).reversed()));
        ContainerModule container = bidManager.getContainer(containerId);
        if (container == null) {
            bidManager.clear(containerId);
            return;
        }
        if (winner.isPresent()) {
            BidResponse win = winner.get();
            MetricsCollector.MigrationKind kind = win.getBidderId() == cloudId ? MetricsCollector.MigrationKind.CLOUD : MetricsCollector.MigrationKind.INTER_FOG;
            double linkBw = effectiveBandwidth(kind);
            double latency = linkLatency(kind);
            container.recordLastBid(win.getBidderId(), win.getCost());
            MetricsRegistry.collector().markBidWinner(containerId, win.getBidderId());
            send(win.getBidderId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, getId(), sourceHost(container), kind, linkBw, latency, container.getMigrationTrigger()));
            bidManager.recordWinner(container.getContainerId(), win);
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
        } else if (cloudId >= 0) {
            send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, effectiveBandwidth(MetricsCollector.MigrationKind.CLOUD), linkLatency(MetricsCollector.MigrationKind.CLOUD), container.getMigrationTrigger()));
            MetricsRegistry.collector().markBidWinner(containerId, cloudId);
        } else {
            enqueuePending(container);
        }
        bidManager.clear(containerId);
        activeTransfers++;
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
        scheduleCompletion(host, container, transferSeconds + transfer.getLatencySeconds());
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
        activeTransfers = Math.max(0, activeTransfers - 1);
        if (!result.isSuccess()) {
            ContainerModule container = result.getContainer();
            container.setMigrationTrigger(result.getTrigger());
            enqueuePending(container);
            retryPendingMigrations();
        }
    }

    private void handleTaskComplete(SimEvent ev) {
        if (!(ev.getData() instanceof CompletionNotice notice)) {
            return;
        }
        ContainerModule container = notice.getContainer();
        if (notice.getRunVersion() != container.getRunVersion()) {
            return;
        }
        FogServer host = findServer(notice.getHostName());
        boolean hostedHere = host != null && host.getContainers().contains(container);
        if (hostedHere) {
            host.removeContainer(container);
            recordInstantLoad(host);
        }
        if (getId() == container.getOwnerId()) {
            container.markCompleted();
            settleSlaAndPayment(container, notice.getFinish());
            bidManager.clearWinner(container.getContainerId());
        } else if (hostedHere) {
            send(container.getOwnerId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.TASK_COMPLETE, notice);
        }
        retryPendingMigrations();
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
        retryPendingMigrations();
    }

    private void placeNewContainer(ContainerModule container) {
        List<MetricsCollector.PlacementServerLoad> loadsBefore = snapshotServerLoads();
        FogServer target = placeContainerLocally(container);
        if (target == null) {
            MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), "none", CloudSim.clock(), false, "local_infeasible", loadsBefore);
            migrateContainer(container);
            return;
        }
        MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), target.getName(), CloudSim.clock(), true, "initial_local", snapshotServerLoads());
        container.setPaused(false);
        scheduleCompletion(target, container);
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

    private List<MetricsCollector.PlacementServerLoad> snapshotServerLoads() {
        List<MetricsCollector.PlacementServerLoad> loads = new ArrayList<>();
        for (FogServer server : servers) {
            loads.add(new MetricsCollector.PlacementServerLoad(
                    server.getName(),
                    server.getCpuLoad(),
                    server.getMemLoad(),
                    server.getBwLoad(),
                    server.getContainers().size(),
                    server.isPredictedToFail()));
        }
        return loads;
    }

    private void migrateContainer(ContainerModule container) {
        migrateContainer(container, "score_fallback");
    }

    private void migrateContainer(ContainerModule container, String trigger) {
        container.setMigrationStart(CloudSim.clock());
        container.setMigrationTrigger(trigger);
        container.setPaused(true);
        checkpointIfHosted(container);
        // Phase 1: try intra-fog
        String fromHost = sourceHost(container);
        List<MetricsCollector.PlacementServerLoad> loadsBefore = snapshotServerLoads();
        FogServer localTarget = placeContainerLocally(container);
        if (localTarget != null) {
            container.setPaused(false);
            container.recordLastBid(getId(), 0.0);
            MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), localTarget.getName(), CloudSim.clock(), true, "intra_fog_local", snapshotServerLoads());
            scheduleCompletion(localTarget, container);
            double finish = CloudSim.clock();
            send(getId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                    new MigrationResult(container, MetricsCollector.MigrationKind.INTRA_FOG, fromHost, localTarget.getName(),
                            container.getMigrationStart(), finish, 0, container.getProfile().getContainerSizeMb(), true, trigger));
            return;
        }
        MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), "none", CloudSim.clock(), false, "intra_local_infeasible", loadsBefore);
        List<ScoredNode> candidates = new ArrayList<>();
        if (config.getGossip().isEnabled()) {
            ScoringUtil.Weights weights = ScoringUtil.computeWeights(container, CloudSim.clock());
            double stalenessSeconds = config.getGossip().getIntervalSeconds() * config.getGossip().getStalenessThresholdIntervals();
            double now = CloudSim.clock();
            for (Map.Entry<Integer, GossipStateEntry> entry : stateTable.getEntries().entrySet()) {
                if (entry.getKey() == getId()) {
                    continue;
                }
                if (entry.getValue().isStale(now, stalenessSeconds)) {
                    continue;
                }
                double score = weights.alpha() * (1 - entry.getValue().getCpuLoad())
                        + weights.beta() * (1 - entry.getValue().getMemLoad())
                        + weights.gamma() * (1 - entry.getValue().getBwLoad());
                candidates.add(new ScoredNode(entry.getKey(), score));
            }
            // Fallback to neighbors so we still solicit fog bids before cloud.
            if (candidates.isEmpty() && !neighborIds.isEmpty()) {
                for (Integer neighborId : neighborIds) {
                    if (neighborId != getId()) {
                        candidates.add(new ScoredNode(neighborId, 0.5)); // neutral score; final choice still by bid cost
                    }
                }
            }
        } else {
            for (Integer fogId : allFogIds) {
                if (fogId != getId()) {
                    candidates.add(new ScoredNode(fogId, 0.5));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(ScoredNode::score).reversed());
        int maxFogBidders = config.getBidding().getMaxFogBidders();
        int limit = maxFogBidders > 0 ? Math.min(maxFogBidders, candidates.size()) : candidates.size();
        Set<Integer> bidderIds = candidates.stream().limit(limit).map(ScoredNode::nodeId).collect(Collectors.toSet());
        if (cloudId >= 0) {
            bidderIds.add(cloudId);
        }
        if (bidderIds.isEmpty()) {
            // fallback to cloud immediately
            if (cloudId >= 0) {
                container.recordLastBid(cloudId, 0.0);
                send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                        new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, effectiveBandwidth(MetricsCollector.MigrationKind.CLOUD), linkLatency(MetricsCollector.MigrationKind.CLOUD), trigger));
                activeTransfers++;
            }
            enqueuePending(container);
            return;
        }
        bidManager.startBid(container, bidderIds);
        MigrationRequest request = new MigrationRequest(container, getId());
        for (Integer bidderId : bidderIds) {
            send(bidderId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_REQUEST, request);
        }
    }

    private void scheduleCompletion(FogServer host, ContainerModule container) {
        scheduleCompletion(host, container, 0);
    }

    private void scheduleCompletion(FogServer host, ContainerModule container, double startDelaySeconds) {
        double hostShare = hostShareMips(host);
        double execSeconds = computeExecutionSeconds(container, hostShare);
        double start = CloudSim.clock() + startDelaySeconds;
        double finish = start + execSeconds;
        container.startRun(start, hostShare, execSeconds);
        send(getId(), startDelaySeconds + execSeconds, CollabSimTags.TASK_COMPLETE, new CompletionNotice(container, host.getName(), start, finish, container.getRunVersion()));
    }

    private double hostShareMips(FogServer host) {
        double effectiveCpu = host.getCapacity().getCpuMips() * host.getCpuFactor();
        return effectiveCpu / Math.max(1, host.getContainers().size());
    }

    private double computeExecutionSeconds(ContainerModule container, double hostShareMips) {
        double seconds = container.getRemainingWorkMi() / Math.max(1e-6, hostShareMips);
        return Math.max(CloudSim.getMinTimeBetweenEvents(), seconds);
    }

    private double computeBidCost(ContainerProfile profile) {
        double totalCpu = servers.stream().mapToDouble(s -> s.getCapacity().getCpuMips() * s.getCpuFactor()).sum();
        double totalRam = servers.stream().mapToDouble(s -> s.getCapacity().getRamMb()).sum();
        double totalBw = servers.stream().mapToDouble(s -> s.getCapacity().getBandwidth() * s.getBwFactor()).sum();
        double usedCpu = servers.stream().mapToDouble(FogServer::getUsedCpu).sum();
        double usedRam = servers.stream().mapToDouble(FogServer::getUsedRam).sum();
        double usedBw = servers.stream().mapToDouble(FogServer::getUsedBw).sum();

        double cres = profile.getDemandMips() * config.getBidding().getCpuUnitCost()
                + profile.getRamMb() * config.getBidding().getMemUnitCost()
                + profile.getBandwidth() * config.getBidding().getBwUnitCost();
        double lCpu = totalCpu > 0 ? usedCpu / totalCpu : 1.0;
        double pfail = lCpu * 0.5;
        double crisk = pfail * config.getBidding().getFailureWeight();
        double linkBw = Math.max(1, effectiveBandwidth(MetricsCollector.MigrationKind.INTER_FOG));
        double ctransfer = profile.getContainerSizeMb() / linkBw;
        double cmig = ctransfer + config.getBidding().getMigrationRestoreFactor() * profile.getContainerSizeMb();
        double csla = slaHistory.getOrDefault(getId(), 0.0) * config.getBidding().getHistoricalPenaltyWeight();
        return cres + crisk + cmig;
    }

    private double evaluatedCost(BidResponse response) {
        double csla = slaHistory.getOrDefault(response.getBidderId(), 0.0) * config.getBidding().getHistoricalPenaltyWeight();
        return response.getCost() + csla;
    }

    private FogServer findServer(String name) {
        for (FogServer server : servers) {
            if (server.getName().equals(name)) {
                return server;
            }
        }
        return null;
    }

    public void setNeighborIds(List<Integer> neighborIds) {
        this.neighborIds = neighborIds;
    }

    public void setAllFogIds(List<Integer> allFogIds) {
        this.allFogIds = allFogIds;
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
            if (server.isFaulted()) {
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

    private void checkpointAndRemove(FogServer host, ContainerModule container) {
        container.checkpointProgress(CloudSim.clock());
        host.removeContainer(container);
        recordInstantLoad(host);
    }

    private void checkpointIfHosted(ContainerModule container) {
        FogServer host = findServer(container.getHostName());
        if (host != null && host.getContainers().contains(container)) {
            checkpointAndRemove(host, container);
        }
    }

    private void settleSlaAndPayment(ContainerModule container, double finishTime) {
        double completionLatency = finishTime - container.getArrivalTime();
        boolean violated = completionLatency > container.getDeadlineSeconds();

        double rMax = capacity.getCpuMips() + capacity.getRamMb() + capacity.getBandwidth();
        double rNorm = (container.getProfile().getDemandMips() + container.getProfile().getRamMb() + container.getProfile().getBandwidth()) / rMax;
        double epsilon = config.getSla().getEpsilon();
        double k = config.getSla().getUrgencyK();
        double u = 1.0 / (container.getDeadlineSeconds() - finishTime + epsilon);
        double uNorm = Math.min(1.0, u * k);
        double slaValue = config.getSla().getResourceWeight() * rNorm + config.getSla().getUrgencyWeight() * uNorm;

        int payeeId = container.getLastBidderId() >= 0 ? container.getLastBidderId() : getId();
        double bidCost = container.getLastBidCost();
        BidResponse winner = bidManager.getWinner(container.getContainerId());
        if (winner != null) {
            payeeId = winner.getBidderId();
            bidCost = winner.getCost();
        }
        double eta = config.getSla().getPenaltyEta();
        double amount = violated ? Math.max(0, bidCost - eta * slaValue) : bidCost + slaValue;
        MetricsRegistry.collector().recordSla(container.getContainerId(), violated, completionLatency, container.getDeadlineSeconds(), slaValue, amount);
        tokenManager.debit(container.getOwnerId(), amount);
        tokenManager.credit(payeeId, amount);
        MetricsRegistry.collector().recordPayment(container.getOwnerId(), payeeId, amount);
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

    private void enqueuePending(ContainerModule container) {
        boolean exists = pendingMigrations.stream().anyMatch(c -> c.getContainerId().equals(container.getContainerId()));
        if (!exists) {
            pendingMigrations.add(container);
        }
    }

    private void retryPendingMigrations() {
        if (pendingMigrations.isEmpty()) {
            return;
        }
        int attempts = pendingMigrations.size();
        for (int i = 0; i < attempts; i++) {
            ContainerModule container = pendingMigrations.poll();
            if (container != null) {
                migrateContainer(container, container.getMigrationTrigger().isEmpty() ? "queued_retry" : container.getMigrationTrigger());
            }
        }
    }
}
