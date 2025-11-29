package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.economy.BidManager;
import org.collabft.economy.BidResponse;
import org.collabft.economy.TokenManager;
import org.collabft.economy.TrustManager;
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
    private final TrustManager trustManager;
    private final Map<String, BidContext> winnerContexts = new HashMap<>();
    private final Map<String, Double> transferStarts = new HashMap<>();
    private int activeTransfers = 0;
    private final Queue<ContainerModule> pendingMigrations = new ArrayDeque<>();
    private List<Integer> neighborIds = new ArrayList<>();
    private List<Integer> allFogIds = new ArrayList<>();
    private int cloudId = -1;
    private int schedulerId = -1;
    private static final Map<String, NodeCapacity> CAPACITY_BY_NAME = new HashMap<>();

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
        this.trustManager = new TrustManager(
                config.getTrust().isEnableTrust(),
                config.getTrust().getTrustDecayFactor(),
                config.getTrust().getTrustRecoveryFactor(),
                config.getTrust().getTrustThreshold());
        registerCapacity();
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
        ContainerModule module = new ContainerModule("container-" + profile.getTaskId(), "collab-app", getId(), profile);
        module.setOwnerFog(getName());
        module.setOwnerId(getId());
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
        // Handle containers on the faulty server
        for (ContainerModule container : new ArrayList<>(server.getContainers())) {
            if (notice.getType() == FaultNotice.FaultType.SERVER_CRASH) {
                // REALISTIC: Cannot migrate from crashed server - containers are LOST
                // Remove container without checkpointing (work progress is lost)
                server.removeContainer(container);
                recordInstantLoad(server);
                // Restart task from beginning with full work remaining
                container.resetProgress();
                container.setPaused(true);
                migrateContainer(container, "fault_" + notice.getType().name().toLowerCase());
            } else {
                // CPU_FAILURE and BANDWIDTH_DEGRADATION: Server still accessible, can checkpoint
                checkpointAndRemove(server, container);
                migrateContainer(container, "fault_" + notice.getType().name().toLowerCase());
            }
        }
    }

    private void handleMigrationRequest(SimEvent ev) {
        if (!(ev.getData() instanceof MigrationRequest request)) {
            return;
        }
        ContainerModule container = request.getContainer();
        ContainerProfile profile = container.getProfile();
        double claimedBw = claimedBandwidthTo(request.getOriginId());
        double migrationTime = computeMigrationTime(profile.getContainerSizeMb(), claimedBw);
        NodeCapacity cap = capacityByName(getName());
        LoadSnapshot load = localLoadSnapshot();
        double projectedCpu = load.cpuLoad() + profile.getDemandMips() / Math.max(1e-6, cap.cpu());
        double projectedMem = load.memLoad() + profile.getRamMb() / Math.max(1e-6, cap.mem());
        double projectedBw = load.bwLoad() + profile.getBandwidth() / Math.max(1e-6, cap.bw());
        double timeToDeadline = Math.max(0, container.getDeadlineSeconds() - CloudSim.clock());
        // Only check if we have ANY healthy server with capacity (not whether any server has predicted fault)
        boolean serverFeasible = servers.stream().anyMatch(s -> !s.isPredictedToFail() && !s.isFaulted() && s.residualScore(profile) > 0);
        boolean feasible = serverFeasible
                && projectedCpu <= 1.0 && projectedMem <= 1.0 && projectedBw <= 1.0
                && migrationTime <= timeToDeadline;
        double resourceImpact = resourceImpact(profile, cap, claimedBw);
        double bidValue = migrationTime + config.getBidding().getResourceImpactK() * resourceImpact;
        MetricsRegistry.collector().recordBid(container.getContainerId(), getId(), getName(), claimedBw, migrationTime, bidValue, bidValue, feasible, false, trustManager.current(getId()), CloudSim.clock());
        send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE,
                new BidResponse(getId(), container.getContainerId(), feasible, bidValue, claimedBw, migrationTime));
    }

    private void handleBidResponse(SimEvent ev) {
        if (!(ev.getData() instanceof BidResponse response)) {
            return;
        }
        bidManager.registerResponse(response);
        String containerId = response.getContainerId();
        if (!bidManager.isComplete(containerId)) {
            return;
        }
        List<BidResponse> responses = bidManager.getResponses(containerId);
        ContainerModule container = bidManager.getContainer(containerId);
        if (container == null) {
            bidManager.clear(containerId);
            return;
        }
        List<BidEvaluation> evaluated = new ArrayList<>();
        for (BidResponse resp : responses) {
            BidEvaluation eval = evaluateBid(container, resp);
            if (eval != null) {
                evaluated.add(eval);
            }
        }
        Optional<BidEvaluation> winner = selectWinner(evaluated);
        if (winner.isPresent()) {
            BidEvaluation win = winner.get();
            MetricsCollector.MigrationKind kind = win.response().getBidderId() == cloudId ? MetricsCollector.MigrationKind.CLOUD : MetricsCollector.MigrationKind.INTER_FOG;
            double linkBw = actualLinkBandwidth(win.response().getBidderId());
            double latency = linkLatencyFor(win.response().getBidderId());
            container.recordLastBid(win.response().getBidderId(), win.bidValue());
            MetricsRegistry.collector().markBidWinner(containerId, win.response().getBidderId());
            BidContext context = new BidContext(win);
            winnerContexts.put(containerId, context);
            double transferStart = CloudSim.clock();
            transferStarts.put(containerId, transferStart);
            send(win.response().getBidderId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, getId(), sourceHost(container), kind, linkBw, latency, container.getMigrationTrigger()));
            bidManager.recordWinner(container.getContainerId(), win.response());
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
            activeTransfers++;
        } else if (cloudId >= 0) {
            double claimedBw = actualLinkBandwidth(cloudId);
            double claimedMig = computeMigrationTime(container.getProfile().getContainerSizeMb(), claimedBw);
            BidResponse synthetic = new BidResponse(cloudId, container.getContainerId(), true, 0, claimedBw, claimedMig);
            BidEvaluation eval = evaluateBid(container, synthetic);
            if (eval != null && eval.feasible()) {
                container.recordLastBid(cloudId, eval.bidValue());
                winnerContexts.put(containerId, new BidContext(eval));
                double transferStart = CloudSim.clock();
                transferStarts.put(containerId, transferStart);
                send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                        new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, actualLinkBandwidth(cloudId), linkLatencyFor(cloudId), container.getMigrationTrigger()));
                MetricsRegistry.collector().markBidWinner(containerId, cloudId);
                activeTransfers++;
            } else {
                enqueuePending(container);
            }
        } else {
            enqueuePending(container);
        }
        bidManager.clear(containerId);
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
            double transferStart = CloudSim.clock();
            send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                    new MigrationResult(container, transfer.getKind(), from, "unplaced", transferStart, CloudSim.clock(), 0, 0, false, transfer.getTrigger()));
            return;
        }
        double effectiveBw = transfer.getLinkBandwidthMbps() / Math.max(1, activeTransfers);
        double effectiveBwMbPerSec = effectiveBw / 8.0;
        double transferStart = CloudSim.clock();
        double transferSeconds = effectiveBwMbPerSec > 0
                ? container.getProfile().getContainerSizeMb() / effectiveBwMbPerSec
                : 0;
        double finish = transferStart + transferSeconds + transfer.getLatencySeconds();
        double overheadBw = container.getProfile().getContainerSizeMb();
        scheduleCompletion(host, container, transferSeconds + transfer.getLatencySeconds());
        MetricsRegistry.collector().recordNetwork("migration", from, host.getName(), container.getProfile().getContainerSizeMb() * 1024 * 1024, CloudSim.clock());
        // Send completion back to origin for logging/payment
        send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                new MigrationResult(container, transfer.getKind(), from, host.getName(), transferStart, finish, 0, overheadBw, true, transfer.getTrigger()));
    }

    private void handleMigrationFinish(SimEvent ev) {
        if (!(ev.getData() instanceof MigrationResult result)) {
            return;
        }
        clearPending(result.getContainer().getContainerId());
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
        BidContext context = winnerContexts.get(result.getContainer().getContainerId());
        Double start = transferStarts.remove(result.getContainer().getContainerId());
        if (context != null) {
            double transferStart = start != null ? start : result.getStart();
            double actualTransfer = Math.max(CloudSim.getMinTimeBetweenEvents(), result.getFinish() - transferStart);
            double actualMigTime = config.getBidding().getPauseSeconds() + actualTransfer + config.getBidding().getResumeSeconds();
            double actualBw = result.getContainer().getProfile().getContainerSizeMb() / Math.max(1e-6, actualTransfer) * 8.0;
            double claimedBw = context.evaluation.response().getClaimedBandwidthMbps();
            double claimedMig = context.evaluation.response().getClaimedMigrationTimeSeconds();
            double errBw = Math.abs(claimedBw - actualBw) / Math.max(1e-6, claimedBw);
            double errMig = Math.abs(claimedMig - actualMigTime) / Math.max(1e-6, claimedMig);
            boolean dishonest = config.getTrust().isEnableTrust()
                    && (errBw > config.getTrust().getTauBw() || errMig > config.getTrust().getTauMig());
            double trustAfter = context.evaluation.trustBefore();
            if (config.getTrust().isEnableTrust()) {
                trustAfter = dishonest ? trustManager.decay(context.evaluation.response().getBidderId())
                        : trustManager.recover(context.evaluation.response().getBidderId());
                
                // Record trust evaluation for all migrations with bid context
                MetricsRegistry.collector().recordTrustEvaluation(
                        result.getContainer().getContainerId(),
                        context.evaluation.response().getBidderId(),
                        CloudSim.getEntityName(context.evaluation.response().getBidderId()),
                        claimedBw, actualBw, claimedMig, actualMigTime,
                        errBw, errMig,
                        context.evaluation.trustBefore(), trustAfter,
                        dishonest,
                        CloudSim.clock());
            }
            context.actualBw = actualBw;
            context.actualMigTime = actualMigTime;
            context.errBw = errBw;
            context.errMig = errMig;
            context.trustAfter = trustAfter;
        }
        activeTransfers = Math.max(0, activeTransfers - 1);
        if (!result.isSuccess()) {
            ContainerModule container = result.getContainer();
            container.setMigrationTrigger(result.getTrigger());
            enqueuePending(container);
            winnerContexts.remove(container.getContainerId());
            transferStarts.remove(container.getContainerId());
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
        clearPending(container.getContainerId());
        FogServer host = findServer(notice.getHostName());
        boolean hostedHere = host != null && host.getContainers().contains(container);
        if (hostedHere) {
            host.removeContainer(container);
            recordInstantLoad(host);
        }
        if (getId() == container.getOwnerId()) {
            container.markCompleted();
            String finishFog = hostedHere ? getName() : notice.getHostName();
            settleSlaAndPayment(container, notice.getFinish(), finishFog);
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
            boolean faultPresent = servers.stream().anyMatch(FogServer::isFaulted);
            String reason = faultPresent ? "local_infeasible_fault" : "local_infeasible";
            MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), "none", CloudSim.clock(), false, reason, loadsBefore);
            migrateContainer(container, reason);
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
            // Skip servers with predicted faults - they should not accept new containers
            if (server.isPredictedToFail()) {
                continue;
            }
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
        boolean faultPresent = servers.stream().anyMatch(FogServer::isFaulted);
        String reason = faultPresent ? "intra_local_infeasible_fault" : "intra_local_infeasible";
        MetricsRegistry.collector().recordPlacement(container.getContainerId(), getName(), "none", CloudSim.clock(), false, reason, loadsBefore);
        if (mode == 2 && schedulerId >= 0) {
            MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), true);
            send(schedulerId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_REQUEST,
                    new MigrationRequest(container, getId()));
            return;
        }
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
        // Don't filter by trust here - let evaluateBid() handle trust filtering after bids come in
        // This allows us to get bids from more nodes and apply trust as a penalty via effectiveBid
        Set<Integer> bidderIds = candidates.stream()
                .limit(limit)
                .map(ScoredNode::nodeId)
                .collect(Collectors.toSet());
        if (cloudId >= 0) {
            bidderIds.add(cloudId);
        }
        if (bidderIds.isEmpty()) {
            // fallback to cloud immediately
            if (cloudId >= 0) {
                double claimedBw = actualLinkBandwidth(cloudId);
                double claimedMig = computeMigrationTime(container.getProfile().getContainerSizeMb(), claimedBw);
                BidResponse synthetic = new BidResponse(cloudId, container.getContainerId(), true, 0, claimedBw, claimedMig);
                BidEvaluation eval = evaluateBid(container, synthetic);
                if (eval != null && eval.feasible()) {
                    container.recordLastBid(cloudId, eval.bidValue());
                    winnerContexts.put(container.getContainerId(), new BidContext(eval));
                    double transferStart = CloudSim.clock();
                    transferStarts.put(container.getContainerId(), transferStart);
                    send(cloudId, CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_START,
                            new MigrationTransfer(container, getId(), sourceHost(container), MetricsCollector.MigrationKind.CLOUD, actualLinkBandwidth(cloudId), linkLatencyFor(cloudId), trigger));
                    MetricsRegistry.collector().markBidWinner(container.getContainerId(), cloudId);
                    MetricsRegistry.collector().recordDecisionLatency(container.getContainerId(), container.getMigrationStart(), CloudSim.clock(), mode == 2);
                    activeTransfers++;
                }
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

    private void settleSlaAndPayment(ContainerModule container, double finishTime, String finishFog) {
        double completionTime = finishTime;
        double makespan = completionTime - container.getArrivalTime();
        boolean slaMet = completionTime <= container.getDeadlineSeconds();
        container.setSlaSuccess(slaMet);
        container.setMakespan(makespan);
        container.setCompletionTime(completionTime);

        BidContext context = winnerContexts.remove(container.getContainerId());
        BidEvaluation eval = context != null ? context.evaluation : null;
        int payeeId = eval != null ? eval.response().getBidderId() : container.getLastBidderId();
        double bidValue = eval != null ? eval.bidValue() : container.getLastBidCost();
        double effectiveBid = eval != null ? eval.effectiveBid() : bidValue;
        double claimedBw = eval != null ? eval.response().getClaimedBandwidthMbps() : 0;
        double claimedMig = eval != null ? eval.response().getClaimedMigrationTimeSeconds() : 0;
        double actualBw = context != null && context.actualBw > 0 ? context.actualBw : 0;
        double actualMig = context != null && context.actualMigTime > 0 ? context.actualMigTime : 0;
        double errBw = context != null ? context.errBw : 0;
        double errMig = context != null ? context.errMig : 0;
        double trustBefore = eval != null ? eval.trustBefore() : trustManager.current(payeeId);
        double trustAfter = context != null && context.trustAfter >= 0 ? context.trustAfter : trustBefore;

        double payment = (payeeId >= 0 && payeeId != container.getOwnerId()) ? bidValue : 0;
        MetricsRegistry.collector().recordSla(
                container.getContainerId(),
                container.getOriginatingEdge(),
                finishFog,
                container.getArrivalTime(),
                completionTime,
                container.getDeadlineSeconds(),
                slaMet,
                container.getTExecSeconds(),
                container.getTNetSeconds(),
                container.getTSlackSeconds(),
                container.getTMigSeconds(),
                makespan,
                mode);
        if (payment > 0) {
            tokenManager.debit(container.getOwnerId(), payment);
            tokenManager.credit(payeeId, payment);
        }
        MetricsRegistry.collector().recordPayment(container.getOwnerId(), payeeId, payment);
        String payeeName = payeeId >= 0 ? CloudSim.getEntityName(payeeId) : "unknown";
        MetricsRegistry.collector().recordSettlement(new MetricsCollector.SettlementRecord(
                container.getContainerId(),
                payeeId,
                payeeName,
                claimedBw,
                claimedMig,
                actualBw,
                actualMig,
                errBw,
                errMig,
                trustBefore,
                trustAfter,
                bidValue,
                effectiveBid,
                payment,
                slaMet,
                finishTime));
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

    private void clearPending(String containerId) {
        pendingMigrations.removeIf(c -> c.getContainerId().equals(containerId));
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

    private void registerCapacity() {
        double cpu = servers.stream().mapToDouble(s -> s.getCapacity().getCpuMips()).sum();
        double mem = servers.stream().mapToDouble(s -> s.getCapacity().getRamMb()).sum();
        double bw = servers.stream().mapToDouble(s -> s.getCapacity().getBandwidth()).sum();
        CAPACITY_BY_NAME.put(getName(), new NodeCapacity(cpu, mem, bw));
    }

    private NodeCapacity capacityFor(int bidderId) {
        String name = CloudSim.getEntityName(bidderId);
        NodeCapacity cap = CAPACITY_BY_NAME.get(name);
        if (cap != null) {
            return cap;
        }
        if (bidderId == cloudId) {
            ResourceCapacity cloudCap = config.getTopology().getCloud().getCapacity();
            return new NodeCapacity(cloudCap.getCpuMips(), cloudCap.getRamMb(), cloudCap.getBandwidth());
        }
        return capacityByName(getName());
    }

    private NodeCapacity capacityByName(String name) {
        return CAPACITY_BY_NAME.getOrDefault(name, aggregateCapacitySnapshot());
    }

    private NodeCapacity aggregateCapacitySnapshot() {
        double cpu = servers.stream().mapToDouble(s -> s.getCapacity().getCpuMips() * s.getCpuFactor()).sum();
        double mem = servers.stream().mapToDouble(s -> s.getCapacity().getRamMb()).sum();
        double bw = servers.stream().mapToDouble(s -> s.getCapacity().getBandwidth() * s.getBwFactor()).sum();
        return new NodeCapacity(cpu, mem, bw);
    }

    private double timeToNextPredictedFault() {
        double now = CloudSim.clock();
        double soonest = Double.MAX_VALUE;
        for (FogServer server : servers) {
            double predicted = server.getPredictedFailureAt();
            if (predicted > now) {
                soonest = Math.min(soonest, predicted - now);
            }
        }
        return soonest;
    }

    private LoadSnapshot localLoadSnapshot() {
        NodeCapacity cap = aggregateCapacitySnapshot();
        double usedCpu = servers.stream().mapToDouble(FogServer::getUsedCpu).sum();
        double usedMem = servers.stream().mapToDouble(FogServer::getUsedRam).sum();
        double usedBw = servers.stream().mapToDouble(FogServer::getUsedBw).sum();
        double cpuLoad = cap.cpu() > 0 ? usedCpu / cap.cpu() : 1.0;
        double memLoad = cap.mem() > 0 ? usedMem / cap.mem() : 1.0;
        double bwLoad = cap.bw() > 0 ? usedBw / cap.bw() : 1.0;
        return new LoadSnapshot(cpuLoad, memLoad, bwLoad);
    }

    private LoadSnapshot loadSnapshot(int bidderId) {
        if (bidderId == getId()) {
            return localLoadSnapshot();
        }
        if (bidderId == cloudId) {
            return new LoadSnapshot(0, 0, 0);
        }
        return new LoadSnapshot(0, 0, 0);
    }

    private double claimedBandwidthTo(int originId) {
        double bwFactor = servers.stream().mapToDouble(FogServer::getBwFactor).average().orElse(1.0);
        double uplink = capacity.getUplinkBandwidth() * bwFactor;
        double interLink = originId == cloudId ? network.getFogCloudBandwidthMbps() : network.getInterFogBandwidthMbps();
        return Math.max(1e-6, Math.min(uplink, interLink));
    }

    private double actualLinkBandwidth(int bidderId) {
        double base = bidderId == cloudId ? network.getFogCloudBandwidthMbps() : network.getInterFogBandwidthMbps();
        return Math.max(1e-6, Math.min(capacity.getUplinkBandwidth(), base));
    }

    private double linkLatencyFor(int bidderId) {
        if (bidderId == cloudId) {
            return network.getFogCloudLatencyMs() / 1000.0;
        }
        if (bidderId == getId()) {
            return 0.0;
        }
        return network.getInterFogLatencyMs() / 1000.0;
    }

    private double computeMigrationTime(double containerSizeMb, double claimedBw) {
        double bwMbPerSec = Math.max(1e-6, claimedBw / 8.0);
        return config.getBidding().getPauseSeconds() + containerSizeMb / bwMbPerSec + config.getBidding().getResumeSeconds();
    }

    private double resourceImpact(ContainerProfile profile, NodeCapacity cap, double linkCapacity) {
        double deltaCpu = profile.getDemandMips() / Math.max(1e-6, cap.cpu());
        double deltaMem = profile.getRamMb() / Math.max(1e-6, cap.mem());
        double deltaBw = profile.getBandwidth() / Math.max(1e-6, linkCapacity);
        return deltaCpu + deltaMem + deltaBw;
    }

    private BidEvaluation evaluateBid(ContainerModule container, BidResponse response) {
        int bidderId = response.getBidderId();
        double trustBefore = trustManager.current(bidderId);
        if (trustManager.enabled() && trustBefore < config.getTrust().getTrustThreshold()) {
            MetricsRegistry.collector().recordBid(container.getContainerId(), bidderId, CloudSim.getEntityName(bidderId),
                    response.getClaimedBandwidthMbps(), response.getClaimedMigrationTimeSeconds(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, false, false, trustBefore, CloudSim.clock());
            return null;
        }
        NodeCapacity cap = capacityFor(bidderId);
        LoadSnapshot load = loadSnapshot(bidderId);
        ContainerProfile profile = container.getProfile();
        double linkCapacity = actualLinkBandwidth(bidderId);
        double resourceImpact = resourceImpact(profile, cap, linkCapacity);
        double migrationTime = computeMigrationTime(profile.getContainerSizeMb(), response.getClaimedBandwidthMbps());
        double projectedCpu = load.cpuLoad() + profile.getDemandMips() / Math.max(1e-6, cap.cpu());
        double projectedMem = load.memLoad() + profile.getRamMb() / Math.max(1e-6, cap.mem());
        double projectedBw = load.bwLoad() + profile.getBandwidth() / Math.max(1e-6, cap.bw());
        double timeToDeadline = Math.max(0, container.getDeadlineSeconds() - CloudSim.clock());
        double timeToFault = bidderId == getId() ? timeToNextPredictedFault() : Double.MAX_VALUE;
        boolean feasible = response.isFeasible()
                && projectedCpu <= 1.0 && projectedMem <= 1.0 && projectedBw <= 1.0
                && migrationTime <= timeToDeadline
                && migrationTime <= timeToFault;
        double bidValue = migrationTime + config.getBidding().getResourceImpactK() * resourceImpact;
        double effectiveBid = trustManager.enabled() ? bidValue / Math.max(1e-6, trustBefore) : bidValue;
        double headroom = headroom(projectedCpu, projectedMem, projectedBw);
        double latency = linkLatencyFor(bidderId);
        MetricsRegistry.collector().recordBid(container.getContainerId(), bidderId, CloudSim.getEntityName(bidderId),
                response.getClaimedBandwidthMbps(), response.getClaimedMigrationTimeSeconds(), bidValue, effectiveBid, feasible, false, trustBefore, CloudSim.clock());
        return new BidEvaluation(response, migrationTime, resourceImpact, bidValue, effectiveBid, projectedCpu, projectedMem, projectedBw, headroom, latency, trustBefore, feasible);
    }

    private Optional<BidEvaluation> selectWinner(List<BidEvaluation> bids) {
        return bids.stream()
                .filter(BidEvaluation::feasible)
                .min(Comparator
                        .comparingDouble(BidEvaluation::effectiveBid)
                        .thenComparing((BidEvaluation b) -> b.headroom(), Comparator.reverseOrder())
                        .thenComparingDouble(BidEvaluation::latencySeconds));
    }

    private double headroom(double cpuProjected, double memProjected, double bwProjected) {
        double cpuRemaining = Math.max(0, 1 - cpuProjected);
        double memRemaining = Math.max(0, 1 - memProjected);
        double bwRemaining = Math.max(0, 1 - bwProjected);
        return cpuRemaining + memRemaining + bwRemaining;
    }

    private record LoadSnapshot(double cpuLoad, double memLoad, double bwLoad) { }

    private record NodeCapacity(double cpu, double mem, double bw) { }

    private record BidEvaluation(BidResponse response, double migrationTime, double resourceImpact, double bidValue, double effectiveBid,
                                 double projectedCpu, double projectedMem, double projectedBw, double headroom, double latencySeconds,
                                 double trustBefore, boolean feasible) { }

    private static class BidContext {
        private final BidEvaluation evaluation;
        private double actualBw = -1;
        private double actualMigTime = -1;
        private double errBw = 0;
        private double errMig = 0;
        private double trustAfter = -1;

        BidContext(BidEvaluation evaluation) {
            this.evaluation = evaluation;
        }
    }
}
