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
import org.collabft.model.MigrationLog;
import org.collabft.core.MetricsSink;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Decision maker attached to a fog node. Handles container arrivals, bidding,
 * migration actions, and payment updates. Core heuristics arrive in later steps;
 * this class wires the event routing and keeps local server accounting.
 */
public class DecisionAgent extends SimEntity {

    private static final double URGENCY_EPS = 1e-6;
    private static final boolean DEBUG_MIG = false;

    private final int fogNodeId;
    private final SimulationConfig config;
    private final Wallet wallet;
    private final List<ServerState> servers;
    private final Map<Integer, FogNodeState> gossipView = new HashMap<>();
    private final FogNodeState localState;
    private final SimulationConfig.EconomicsConfig economicsConfig;
    private final SimulationConfig.SlaConfig slaConfig;
    private final Map<Integer, Double> crashTimes = new HashMap<>();
    private final Map<String, ContainerProfile> activeProfiles = new HashMap<>();
    private final Map<Long, PendingBid> pendingBids = new HashMap<>();
    private final Map<Integer, Double> reputation = new HashMap<>();
    private final Random rng;
    private final PaymentManager paymentManager;
    private final MigrationManager migrationManager;
    private final Map<String, MigrationPlan> activeMigrations = new HashMap<>();
    private long bidSeq = 1L;
    private int gossipAgentId = -1;

    public DecisionAgent(int fogNodeId, List<ServerState> servers, SimulationConfig config) {
        super("decision-agent-" + fogNodeId);
        this.fogNodeId = fogNodeId;
        this.config = Objects.requireNonNull(config, "config");
        this.servers = new ArrayList<>(servers);
        this.wallet = new Wallet(fogNodeId, config.getEconomics().getTokenInitialBalance());
        this.economicsConfig = config.getEconomics();
        this.slaConfig = config.getSla();
        this.localState = rebuildLocalState();
        gossipView.put(fogNodeId, localState);
        long seed = config.getRandom().getMasterSeed();
        this.rng = seed == 0 ? new Random() : new Random(seed + fogNodeId);
        reputation.put(fogNodeId, slaConfig.getReputationInit());
        this.paymentManager = new PaymentManager(slaConfig, config.getWorkload(), reputation, wallet);
        this.migrationManager = new MigrationManager(config);
    }

    private void debug(String msg) {
        System.out.println("[decision " + fogNodeId + " t=" + CloudSim.clock() + "] " + msg);
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
            handleBidResponse(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_GOSSIP_VIEW) {
            handleGossipView(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_MIGRATION_PLACE) {
            handleMigrationPlacement(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_MIGRATION_COMPLETE) {
            handleMigrationComplete(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_TASK_COMPLETE) {
            handleMigrationComplete(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_PAYMENT) {
            handlePayment((PaymentReport) ev.getData());
        }
    }

    private void handleFaultPrediction(FaultEvent fault) {
        if (fault == null || fault.getFogNodeId() != fogNodeId) {
            return;
        }
        boolean changed = false;
        for (ServerState server : servers) {
            if (server.getServerId() == fault.getServerId() && server.hasContainer()) {
                for (String cid : new ArrayList<>(server.getContainerIds())) {
                    ContainerProfile profile = activeProfiles.get(cid);
                    if (profile != null) {
                        removeFromServer(server, cid);
                        changed = true;
                        initiateMigration(profile, server.getServerId(), MigrationLog.Phase.LOCAL);
                    }
                }
            }
        }
        if (changed) {
            FogNodeState updated = rebuildLocalState();
            gossipView.put(fogNodeId, updated);
            pushLocalStateToGossip(updated);
        }
    }

    private void handleFaultHit(FaultEvent fault) {
        if (fault == null || fault.getFogNodeId() != fogNodeId) {
            return;
        }
        for (ServerState server : servers) {
            if (server.getServerId() == fault.getServerId()) {
                for (String cid : new ArrayList<>(server.getContainerIds())) {
                    removeFromServer(server, cid);
                }
                crashTimes.put(server.getServerId(), CloudSim.clock());
                server.setCrashed(true);
            }
        }
        FogNodeState updated = rebuildLocalState();
        gossipView.put(fogNodeId, updated);
        pushLocalStateToGossip(updated);
    }

    private void handleRecovery(FaultEvent fault) {
        if (fault == null || fault.getFogNodeId() != fogNodeId) {
            return;
        }
        double now = CloudSim.clock();
        for (ServerState server : servers) {
            if (server.getServerId() == fault.getServerId()) {
                server.setCrashed(false);
                Double crashedAt = crashTimes.remove(server.getServerId());
                if (crashedAt != null) {
                    MetricsSink.get().recordFaultRecovery(now - crashedAt);
                }
            }
        }
        FogNodeState updated = rebuildLocalState();
        gossipView.put(fogNodeId, updated);
        pushLocalStateToGossip(updated);
    }

    private void handleContainerArrival(ContainerProfile profile) {
        if (profile == null) {
            return;
        }
        ServerState target = pickBestLocal(profile);
        if (target != null) {
            assignToServer(target, profile, false);
            return;
        }
        // No local capacity; trigger migration workflow.
        initiateMigration(profile, -1, MigrationLog.Phase.LOCAL);
    }

    private void handleBidRequest(BidRequest request) {
        if (request == null || request.getTargetFogId() != fogNodeId) {
            return;
        }
        ContainerProfile profile = request.getContainer();
        ServerState target = pickBestLocal(profile);
        double now = CloudSim.clock();
        BidResponse response = new BidResponse();
        response.setRequestId(request.getRequestId());
        response.setBidderFogId(fogNodeId);
        if (target == null) {
            response.setCostResource(Double.POSITIVE_INFINITY);
        } else {
            double costRes = resourceCost(profile);
            double costMig = bidMigrationCost(profile, MigrationLog.Phase.INTER_FOG);
            double costRisk = riskCost(target);
            response.setCostResource(costRes);
            response.setCostMigration(costMig);
            response.setCostRisk(costRisk);
        }
        response.setRespondedAt(now);
        send(CloudSim.getEntityId("decision-agent-" + request.getOriginFogId()), 0.0,
                SimulationEvents.EVT_BID_RESPONSE, response);
        MetricsSink.get().recordBidBytes(64);
    }

    private void handleBidResponse(Object data) {
        if (data instanceof BidTimeout timeout) {
            finalizeBids(timeout.requestId());
            return;
        }
        if (!(data instanceof BidResponse response)) {
            return;
        }
        PendingBid bid = pendingBids.get(response.getRequestId());
        if (bid == null) {
            return;
        }
        bid.responses.put(response.getBidderFogId(), response);
        if (DEBUG_MIG) {
            debug("received bid response from " + response.getBidderFogId() + " totalCost=" + response.totalCost()
                    + " reqId=" + response.getRequestId());
        }
        if (bid.responses.size() >= bid.targetIds.size()) {
            finalizeBids(response.getRequestId());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleGossipView(Object data) {
        if (!(data instanceof Map<?, ?> incoming)) {
            return;
        }
        double now = CloudSim.clock();
        double stalenessLimit = config.getGossip().getIntervalSec() * config.getGossip().getStalenessIntervals();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) incoming).entrySet()) {
            if (!(entry.getKey() instanceof Integer id)) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof FogNodeState state)) {
                continue;
            }
            if (state.isStale(now, stalenessLimit) || state.getNodeId() == fogNodeId) {
                continue;
            }
            FogNodeState existing = gossipView.get(id);
            if (existing == null || state.getTimestamp() > existing.getTimestamp()) {
                gossipView.put(id, copyState(state));
            }
        }
        gossipView.put(fogNodeId, rebuildLocalState());
    }

    private void handleMigrationPlacement(Object data) {
        if (!(data instanceof MigrationInstruction instr)) {
            return;
        }
        ServerState target = pickBestLocal(instr.profile());
        double now = CloudSim.clock();
        if (target == null) {
            // Cannot host; notify origin of failure as immediate completion.
            MigrationResult result = new MigrationResult(instr.profile(), instr.sourceFogId(),
                    fogNodeId, -1, now, instr.bidCost(), instr.phase(), 0.0, false, false, true);
            send(CloudSim.getEntityId("decision-agent-" + instr.sourceFogId()), 0.0,
                    SimulationEvents.EVT_MIGRATION_COMPLETE, result);
            return;
        }
        if (DEBUG_MIG) {
            debug("placing migrated container from fog " + instr.sourceFogId() + " onto server " + target.getServerId());
        }
        assignToServer(target, instr.profile(), true);
        double runDuration = estimateRunDuration(instr.profile());
        MigrationResult result = new MigrationResult(instr.profile(), instr.sourceFogId(), fogNodeId,
                target.getServerId(), now + runDuration, instr.bidCost(), instr.phase(),
                instr.profile().totalRequirement() * runDuration,
                now + runDuration <= instr.profile().getArrivalTime() + instr.profile().getDeadlineSec(),
                true,
                true);
        send(getId(), runDuration, SimulationEvents.EVT_MIGRATION_COMPLETE, result);
    }

    private void handleMigrationComplete(Object data) {
        if (!(data instanceof MigrationResult result)) {
            return;
        }
        boolean isOrigin = result.sourceFogId() == fogNodeId;
        // Always free local resources if we host the container.
        if (result.targetFogId() == fogNodeId) {
            ContainerProfile profile = result.profile();
            for (ServerState server : servers) {
                if (server.getContainerIds().contains(profile.getId())) {
                    removeFromServer(server, profile.getId());
                    FogNodeState updated = rebuildLocalState();
                    gossipView.put(fogNodeId, updated);
                    pushLocalStateToGossip(updated);
                    break;
                }
            }
        }
        if (!isOrigin) {
            // completion for a container we hosted; free resources and inform origin
            ContainerProfile profile = result.profile();
            send(CloudSim.getEntityId("decision-agent-" + result.sourceFogId()), 0.0,
                    SimulationEvents.EVT_MIGRATION_COMPLETE, result);
            return;
        }
        MigrationPlan plan = activeMigrations.remove(result.profile().getId());
        double now = CloudSim.clock();
        if (result.isMigration()) {
            MigrationLog log = new MigrationLog(result.profile().getId(), fogNodeId, result.targetFogId(),
                    plan != null ? plan.sourceServerId() : -1, result.targetServerId(), result.phase(), plan != null ? plan.startedAt() : now);
            log.setCompletedAt(result.completedAt());
            log.setSuccess(result.success());
            boolean deadlineMet = result.deadlineMet();
            log.setDeadlineMet(deadlineMet);
            MetricsSink.get().recordMigration(log);
        }
        double bidCost = plan != null ? plan.bidCost() : cloudBidCost(result.profile());
        PaymentReport pay = paymentManager.createPayment(fogNodeId, result.targetFogId(), result.profile(),
                result.completedAt(), bidCost, result.deadlineMet());
        send(CloudSim.getEntityId("decision-agent-" + fogNodeId), 0.0, SimulationEvents.EVT_PAYMENT, pay);
        if (result.targetFogId() >= 0) {
            send(CloudSim.getEntityId("decision-agent-" + result.targetFogId()), 0.0, SimulationEvents.EVT_PAYMENT, pay);
        }
    }

    private void handlePayment(PaymentReport payment) {
        if (payment == null) {
            return;
        }
        MetricsSink.get().recordPayment(payment);
        paymentManager.applyLocalWallet(payment, fogNodeId);
        paymentManager.updateReputation(payment);
    }

    private void initiateMigration(ContainerProfile profile, int sourceServerId, MigrationLog.Phase desiredPhase) {
        ServerState target = pickBestLocal(profile);
        if (target != null && desiredPhase == MigrationLog.Phase.LOCAL) {
            assignToServer(target, profile, true);
            return;
        }
        selectInterFogTargets(profile, sourceServerId);
    }

    private ServerState pickBestLocal(ContainerProfile profile) {
        List<ServerState> feasible = new ArrayList<>();
        for (ServerState server : servers) {
            if (server.canHost(profile)) {
                feasible.add(server);
            }
        }
        if (feasible.isEmpty()) {
            return null;
        }
        double bestScore = -1.0;
        List<ServerState> best = new ArrayList<>();
        for (ServerState server : feasible) {
            double score = server.residualScore();
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(server);
            } else if (Math.abs(score - bestScore) < 1e-9) {
                best.add(server);
            }
        }
        if (best.isEmpty()) {
            return null;
        }
        int idx = rng.nextInt(best.size());
        return best.get(idx);
    }

    private void assignToServer(ServerState server, ContainerProfile profile, boolean recordMigration) {
        server.addContainer(profile);
        server.setTimestamp(CloudSim.clock());
        FogNodeState updated = rebuildLocalState();
        gossipView.put(fogNodeId, updated);
        pushLocalStateToGossip(updated);
        activeProfiles.put(profile.getId(), profile);
        if (recordMigration) {
            MigrationLog log = new MigrationLog(profile.getId(), fogNodeId, fogNodeId, -1, server.getServerId(),
                    MigrationLog.Phase.LOCAL, CloudSim.clock());
            double migrationTime = estimateMigrationSeconds(profile, MigrationLog.Phase.LOCAL);
            log.setCompletedAt(CloudSim.clock() + migrationTime);
            log.setSuccess(true);
            log.setDeadlineMet(true);
            MetricsSink.get().recordMigration(log);
        }
        scheduleLocalCompletion(server, profile);
    }

    private void selectInterFogTargets(ContainerProfile profile, int sourceServerId) {
        Map<Integer, FogNodeState> view = new HashMap<>(gossipView);
        double now = CloudSim.clock();
        int k = Math.max(1, economicsConfig.getBidFanoutK());
        double preselectThreshold = economicsConfig.getPreselectThreshold();
        List<ScoredNode> scored = new ArrayList<>();
        for (FogNodeState state : view.values()) {
            if (state.getNodeId() == fogNodeId) {
                continue;
            }
            double score = nodeScore(state, profile, now);
            if (score > preselectThreshold) {
                scored.add(new ScoredNode(state.getNodeId(), score));
            }
            if (DEBUG_MIG) {
                debug("preselect score fog=" + state.getNodeId() + " score=" + score + " threshold=" + preselectThreshold);
            }
        }
        double cloudScore = cloudScore(profile);
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredNode> top = scored.size() > k ? scored.subList(0, k) : scored;
        boolean includeCloud = top.isEmpty() || cloudScore > preselectThreshold;
        List<Integer> targetIds = new ArrayList<>();
        for (ScoredNode node : top) {
            targetIds.add(node.nodeId());
        }
        if (DEBUG_MIG) {
            debug("selected targets=" + targetIds + " includeCloud=" + includeCloud + " cloudScore=" + cloudScore);
        }
        PendingBid bid = new PendingBid(bidSeq++, profile, now, economicsConfig.getBidTimeoutSec(),
                targetIds, includeCloud, sourceServerId);
        pendingBids.put(bid.requestId, bid);
        for (int targetId : targetIds) {
            BidRequest req = new BidRequest(bid.requestId, fogNodeId, targetId, profile, now,
                    profile.getDeadlineSec(), economicsConfig.getBidTimeoutSec());
            send(CloudSim.getEntityId("decision-agent-" + targetId), 0.0, SimulationEvents.EVT_BID_REQUEST, req);
            MetricsSink.get().recordBidBytes(64);
        }
        send(getId(), economicsConfig.getBidTimeoutSec(), SimulationEvents.EVT_BID_RESPONSE, new BidTimeout(bid.requestId));
        if (includeCloud) {
            bid.cloudCost = cloudBidCost(profile);
        }
    }

    private double nodeScore(FogNodeState state, ContainerProfile profile, double now) {
        double stalenessLimit = config.getGossip().getIntervalSec() * config.getGossip().getStalenessIntervals() * 1.0;
        if (state.isStale(now, stalenessLimit)) {
            return -1.0;
        }
        double[] weights = computeWeights(profile, now);
        return weights[0] * state.cpuFree() + weights[1] * state.memFree() + weights[2] * state.bwFree();
    }

    private double cloudScore(ContainerProfile profile) {
        double[] weights = computeWeights(profile, CloudSim.clock());
        return weights[0] * economicsConfig.getCloudBandwidthFactor()
                + weights[1] * economicsConfig.getCloudBandwidthFactor()
                + weights[2] * economicsConfig.getCloudBandwidthFactor();
    }

    private double[] computeWeights(ContainerProfile profile, double now) {
        double pCpu = safeRatio(profile.getRequiredCpu(), profile.totalRequirement());
        double pMem = safeRatio(profile.getRequiredMem(), profile.totalRequirement());
        double pBw = safeRatio(profile.getRequiredBw(), profile.totalRequirement());
        double urgencyNorm = computeUrgencyNorm(profile, now);
        double gamma = pBw + urgencyNorm * (1.0 - pBw);
        double remaining = Math.max(0.0, 1.0 - gamma);
        double cpuMemSum = pCpu + pMem;
        double alpha = cpuMemSum > 0 ? remaining * (pCpu / cpuMemSum) : remaining / 2.0;
        double beta = cpuMemSum > 0 ? remaining * (pMem / cpuMemSum) : remaining / 2.0;
        return new double[]{alpha, beta, gamma};
    }

    private double computeUrgencyNorm(ContainerProfile profile, double now) {
        double elapsed = Math.max(0.0, now - profile.getArrivalTime());
        double slack = Math.max(0.0, profile.getDeadlineSec() - elapsed);
        double inv = 1.0 / Math.max(URGENCY_EPS, slack);
        double scaled = slaConfig.getUrgencyScale() * inv;
        return Math.min(1.0, Math.max(0.0, scaled));
    }

    private double estimateMigrationSeconds(ContainerProfile profile, MigrationLog.Phase phase) {
        return migrationManager.transferDuration(profile, phase);
    }

    private double migrationCost(ContainerProfile profile, MigrationLog.Phase phase) {
        return migrationManager.bidMigrationCost(profile, phase);
    }

    private double estimateRunDuration(ContainerProfile profile) {
        return migrationManager.estimateRunDuration(profile);
    }

    private double resourceCost(ContainerProfile profile) {
        return profile.getRequiredCpu() * 1.0
                + profile.getRequiredMem() * 0.5
                + profile.getRequiredBw() * 0.2;
    }

    private double cloudBidCost(ContainerProfile profile) {
        return resourceCost(profile) + bidMigrationCost(profile, MigrationLog.Phase.CLOUD);
    }

    private double riskCost(ServerState server) {
        double pFail = server.cpuLoad() * 0.5;
        return pFail * 5.0;
    }

    private double bidMigrationCost(ContainerProfile profile, MigrationLog.Phase phase) {
        return migrationManager.bidMigrationCost(profile, phase);
    }

    private double effectiveBandwidth(MigrationLog.Phase phase) {
        return migrationManager.effectiveBandwidth(phase);
    }

    private double safeRatio(double value, double total) {
        return total <= 0 ? 0.0 : value / total;
    }

    private record ScoredNode(int nodeId, double score) {
    }

    private FogNodeState rebuildLocalState() {
        FogNodeState state = new FogNodeState(fogNodeId);
        double cpuTotal = 0.0;
        double memTotal = 0.0;
        double bwTotal = 0.0;
        double cpuUsed = 0.0;
        double memUsed = 0.0;
        double bwUsed = 0.0;
        int activeServers = 0;
        for (ServerState server : servers) {
            cpuTotal += server.getCpuTotal();
            memTotal += server.getMemTotal();
            bwTotal += server.getBwTotal();
            cpuUsed += server.getCpuUsed();
            memUsed += server.getMemUsed();
            bwUsed += server.getBwUsed();
            if (!server.isCrashed()) {
                activeServers++;
            }
        }
        state.setCpuTotal(cpuTotal);
        state.setMemTotal(memTotal);
        state.setBwTotal(bwTotal);
        state.setCpuUsed(cpuUsed);
        state.setMemUsed(memUsed);
        state.setBwUsed(bwUsed);
        int activeContainers = 0;
        for (ServerState server : servers) {
            if (server.hasContainer()) {
                activeContainers++;
            }
        }
        state.setActiveContainers(activeContainers);
        state.setActiveServers(activeServers);
        state.setTimestamp(CloudSim.clock());
        state.setServers(servers);
        return state;
    }

    private void pushLocalStateToGossip(FogNodeState state) {
        if (gossipAgentId >= 0 && state != null) {
            send(gossipAgentId, 0.0, SimulationEvents.EVT_GOSSIP_VIEW, copyState(state));
        }
    }

    private FogNodeState copyState(FogNodeState source) {
        FogNodeState dest = new FogNodeState(source.getNodeId());
        dest.setCpuTotal(source.getCpuTotal());
        dest.setCpuUsed(source.getCpuUsed());
        dest.setMemTotal(source.getMemTotal());
        dest.setMemUsed(source.getMemUsed());
        dest.setBwTotal(source.getBwTotal());
        dest.setBwUsed(source.getBwUsed());
        dest.setActiveContainers(source.getActiveContainers());
        dest.setActiveServers(source.getActiveServers());
        dest.setTimestamp(source.getTimestamp());
        dest.setVersion(source.getVersion());
        List<ServerState> copies = new ArrayList<>();
        for (ServerState server : source.getServers()) {
            copies.add(copyServer(server));
        }
        dest.setServers(copies);
        return dest;
    }

    private ServerState copyServer(ServerState server) {
        ServerState dest = new ServerState(server.getFogNodeId(), server.getServerId(),
                server.getCpuTotal(), server.getMemTotal(), server.getBwTotal());
        dest.setCpuUsed(server.getCpuUsed());
        dest.setMemUsed(server.getMemUsed());
        dest.setBwUsed(server.getBwUsed());
        dest.setCrashed(server.isCrashed());
        dest.setContainerIds(server.getContainerIds());
        dest.setTimestamp(server.getTimestamp());
        return dest;
    }

    @Override
    public void shutdownEntity() {
        if (fogNodeId == 0) {
            MetricsSink.get().flush(CloudSim.clock());
        }
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

    public void setGossipAgentId(int gossipAgentId) {
        this.gossipAgentId = gossipAgentId;
    }

    public void seedGossipView(Map<Integer, FogNodeState> initial) {
        if (initial == null) {
            return;
        }
        double now = CloudSim.clock();
        for (Map.Entry<Integer, FogNodeState> entry : initial.entrySet()) {
            FogNodeState state = entry.getValue();
            if (state != null && !state.isStale(now, config.getGossip().getIntervalSec() * config.getGossip().getStalenessIntervals())) {
                gossipView.put(entry.getKey(), copyState(state));
            }
        }
        if (DEBUG_MIG) {
            debug("seeded gossip view with " + gossipView.keySet());
        }
    }

    private void removeFromServer(ServerState server) {
        // Deprecated shim; prefer removeFromServer(server, containerId)
    }

    private void removeFromServer(ServerState server, String containerId) {
        if (server == null || containerId == null) {
            return;
        }
        ContainerProfile profile = activeProfiles.remove(containerId);
        if (profile != null) {
            server.removeContainer(profile);
        }
    }

    private void scheduleLocalCompletion(ServerState server, ContainerProfile profile) {
        double runDuration = estimateRunDuration(profile);
        MigrationResult result = new MigrationResult(profile, fogNodeId, fogNodeId, server.getServerId(),
                CloudSim.clock() + runDuration, resourceCost(profile), MigrationLog.Phase.LOCAL,
                profile.totalRequirement() * runDuration,
                CloudSim.clock() + runDuration <= profile.getArrivalTime() + profile.getDeadlineSec(),
                true,
                false);
        send(getId(), runDuration, SimulationEvents.EVT_TASK_COMPLETE, result);
    }

    private void finalizeBids(long requestId) {
        PendingBid bid = pendingBids.remove(requestId);
        if (bid == null) {
            return;
        }
        double now = CloudSim.clock();
        activeProfiles.remove(bid.profile.getId());
        double bestEval = Double.POSITIVE_INFINITY;
        double winningBidCost = Double.POSITIVE_INFINITY;
        Integer winner = null;
        for (BidResponse resp : bid.responses.values()) {
            if (Double.isInfinite(resp.totalCost())) {
                continue;
            }
            double cSla = clampReputation(reputation.getOrDefault(resp.getBidderFogId(), slaConfig.getReputationInit()));
            double evaluation = resp.totalCost() + cSla;
            if (evaluation < bestEval) {
                bestEval = evaluation;
                winner = resp.getBidderFogId();
                winningBidCost = resp.totalCost();
            }
        }
        if (winner == null) {
            if (bid.includeCloud) {
                winner = -1;
                winningBidCost = bid.cloudCost;
            } else {
                winner = -1;
                winningBidCost = Double.isInfinite(winningBidCost) ? cloudBidCost(bid.profile) : winningBidCost;
            }
        }
        if (DEBUG_MIG) {
            debug("bid finalize requestId=" + requestId + " responses=" + bid.responses.size()
                    + " winner=" + winner + " winningCost=" + winningBidCost + " includeCloud=" + bid.includeCloud
                    + " cloudCost=" + bid.cloudCost);
        }
        MigrationLog.Phase phase = winner == fogNodeId ? MigrationLog.Phase.LOCAL : (winner == -1 ? MigrationLog.Phase.CLOUD : MigrationLog.Phase.INTER_FOG);
        MigrationPlan plan = new MigrationPlan(bid.profile, winner, winningBidCost, bid.sourceServerId, phase, now);
        activeMigrations.put(bid.profile.getId(), plan);

        if (winner == fogNodeId) {
            ServerState srv = pickBestLocal(bid.profile);
            if (srv != null) {
                assignToServer(srv, bid.profile, true);
                double runDuration = estimateRunDuration(bid.profile);
                MigrationResult result = new MigrationResult(bid.profile, fogNodeId, fogNodeId, srv.getServerId(),
                        now + runDuration, winningBidCost, phase,
                        bid.profile.totalRequirement() * runDuration,
                        now + runDuration <= bid.profile.getArrivalTime() + bid.profile.getDeadlineSec(),
                        true,
                        true);
                send(getId(), runDuration, SimulationEvents.EVT_MIGRATION_COMPLETE, result);
            }
            return;
        }

        if (winner == -1) {
            double duration = estimateMigrationSeconds(bid.profile, MigrationLog.Phase.CLOUD) + estimateRunDuration(bid.profile);
            MigrationResult result = new MigrationResult(bid.profile, fogNodeId, -1, -1,
                    now + duration, winningBidCost, MigrationLog.Phase.CLOUD,
                    bid.profile.totalRequirement() * duration,
                    now + duration <= bid.profile.getArrivalTime() + bid.profile.getDeadlineSec(),
                    true,
                    true);
            send(getId(), duration, SimulationEvents.EVT_MIGRATION_COMPLETE, result);
            return;
        }

        MigrationInstruction instr = new MigrationInstruction(bid.profile, fogNodeId, winningBidCost, phase, now, bid.sourceServerId);
        send(CloudSim.getEntityId("decision-agent-" + winner), 0.0, SimulationEvents.EVT_MIGRATION_PLACE, instr);
    }

    private double clampReputation(double val) {
        double min = slaConfig.getReputationMin();
        double max = slaConfig.getReputationMax();
        return Math.max(min, Math.min(max, val));
    }

    private record BidTimeout(long requestId) {
    }

    private record MigrationInstruction(ContainerProfile profile, int sourceFogId, double bidCost,
                                        MigrationLog.Phase phase, double startedAt, int sourceServerId) {
    }

    private record MigrationResult(ContainerProfile profile, int sourceFogId, int targetFogId, int targetServerId,
                                   double completedAt, double bidCost, MigrationLog.Phase phase, double executedLoad,
                                   boolean deadlineMet, boolean success, boolean isMigration) {
    }

    private record MigrationPlan(ContainerProfile profile, int winnerFogId, double bidCost, int sourceServerId,
                                 MigrationLog.Phase phase, double startedAt) {
    }

    private static final class PendingBid {
        final long requestId;
        final ContainerProfile profile;
        final double createdAt;
        final double timeout;
        final List<Integer> targetIds;
        final Map<Integer, BidResponse> responses = new HashMap<>();
        final boolean includeCloud;
        final int sourceServerId;
        double cloudCost = Double.POSITIVE_INFINITY;

        PendingBid(long requestId, ContainerProfile profile, double createdAt, double timeout,
                   List<Integer> targetIds, boolean includeCloud, int sourceServerId) {
            this.requestId = requestId;
            this.profile = profile;
            this.createdAt = createdAt;
            this.timeout = timeout;
            this.targetIds = targetIds == null ? Collections.emptyList() : new ArrayList<>(targetIds);
            this.includeCloud = includeCloud;
            this.sourceServerId = sourceServerId;
        }
    }
}
