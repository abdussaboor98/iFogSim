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
    private long bidSeq = 1L;

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
        } else if (ev.getTag() == SimulationEvents.EVT_MIGRATION_COMPLETE) {
            handleMigrationComplete(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_PAYMENT) {
            handlePayment((PaymentReport) ev.getData());
        }
    }

    private void handleFaultPrediction(FaultEvent fault) {
        if (fault == null || fault.getFogNodeId() != fogNodeId) {
            return;
        }
        for (ServerState server : servers) {
            if (server.getServerId() == fault.getServerId() && server.hasContainer()) {
                ContainerProfile profile = activeProfiles.get(server.getContainerId());
                if (profile != null) {
                    removeFromServer(server);
                    initiateMigration(profile, server.getServerId(), MigrationLog.Phase.LOCAL);
                }
            }
        }
    }

    private void handleFaultHit(FaultEvent fault) {
        if (fault == null || fault.getFogNodeId() != fogNodeId) {
            return;
        }
        for (ServerState server : servers) {
            if (server.getServerId() == fault.getServerId()) {
                removeFromServer(server);
                crashTimes.put(server.getServerId(), CloudSim.clock());
                server.setCrashed(true);
            }
        }
        gossipView.put(fogNodeId, rebuildLocalState());
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
        gossipView.put(fogNodeId, rebuildLocalState());
    }

    private void handleContainerArrival(ContainerProfile profile) {
        if (profile == null) {
            return;
        }
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
            double costRes = 1.0 - target.residualScore() / 3.0;
            double costMig = migrationCost(profile, MigrationLog.Phase.INTER_FOG);
            double costRisk = 1.0 - reputation.getOrDefault(request.getOriginFogId(), slaConfig.getReputationInit());
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
        if (bid.responses.size() >= bid.targetIds.size()) {
            finalizeBids(response.getRequestId());
        }
    }

    private void handleMigrationComplete(Object data) {
        // Placeholder for updating local state after migration completion or timeout.
    }

    private void handlePayment(PaymentReport payment) {
        if (payment == null) {
            return;
        }
        if (payment.getPayeeFogId() == fogNodeId) {
            wallet.credit(payment.getPayment());
        }
        if (payment.getPayerFogId() == fogNodeId) {
            wallet.debit(Math.abs(payment.getPayment()));
        }
        double delta = payment.isReward() ? 0.05 : -0.05;
        reputation.merge(payment.getPayeeFogId(), clampReputation(slaConfig.getReputationInit() + delta),
                (oldVal, newVal) -> clampReputation(oldVal + delta));
    }

    private void initiateMigration(ContainerProfile profile, int sourceServerId, MigrationLog.Phase desiredPhase) {
        ServerState target = pickBestLocal(profile);
        if (target != null && desiredPhase == MigrationLog.Phase.LOCAL) {
            assignToServer(target, profile);
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

    private void assignToServer(ServerState server, ContainerProfile profile) {
        server.setContainerId(profile.getId());
        server.setCpuUsed(server.getCpuUsed() + profile.getRequiredCpu());
        server.setMemUsed(server.getMemUsed() + profile.getRequiredMem());
        server.setBwUsed(server.getBwUsed() + profile.getRequiredBw());
        server.setTimestamp(CloudSim.clock());
        FogNodeState updated = rebuildLocalState();
        gossipView.put(fogNodeId, updated);
        activeProfiles.put(profile.getId(), profile);
        MigrationLog log = new MigrationLog(profile.getId(), fogNodeId, fogNodeId, -1, server.getServerId(),
                MigrationLog.Phase.LOCAL, CloudSim.clock());
        double migrationTime = estimateMigrationSeconds(profile, MigrationLog.Phase.LOCAL);
        log.setCompletedAt(CloudSim.clock() + migrationTime);
        log.setSuccess(true);
        log.setDeadlineMet(true);
        MetricsSink.get().recordMigration(log);
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
        }
        double cloudScore = cloudScore(profile);
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredNode> top = scored.size() > k ? scored.subList(0, k) : scored;
        boolean includeCloud = top.isEmpty() || cloudScore > preselectThreshold;
        List<Integer> targetIds = new ArrayList<>();
        for (ScoredNode node : top) {
            targetIds.add(node.nodeId());
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
            bid.cloudCost = 1.0 - cloudScore;
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
        return weights[0] * 1.0 + weights[1] * 1.0 + weights[2] * economicsConfig.getCloudBandwidthFactor();
    }

    private double[] computeWeights(ContainerProfile profile, double now) {
        double pCpu = safeRatio(profile.getRequiredCpu(), profile.totalRequirement());
        double pMem = safeRatio(profile.getRequiredMem(), profile.totalRequirement());
        double pBw = safeRatio(profile.getRequiredBw(), profile.totalRequirement());
        double urgencyNorm = computeUrgency(profile, now);
        double gamma = Math.min(1.0, slaConfig.getUrgencyScale() * urgencyNorm);
        double remaining = Math.max(0.0, 1.0 - gamma);
        double cpuMemSum = pCpu + pMem;
        double alpha = cpuMemSum > 0 ? remaining * (pCpu / cpuMemSum) : remaining / 2.0;
        double beta = cpuMemSum > 0 ? remaining * (pMem / cpuMemSum) : remaining / 2.0;
        return new double[]{alpha, beta, gamma};
    }

    private double computeUrgency(ContainerProfile profile, double now) {
        double elapsed = Math.max(0.0, now - profile.getArrivalTime());
        double timeLeft = profile.getDeadlineSec() - elapsed;
        double horizon = Math.max(1.0, profile.getDeadlineSec());
        double u = 1.0 - Math.max(0.0, timeLeft) / horizon;
        return Math.min(1.0, Math.max(0.0, u));
    }

    private double estimateMigrationSeconds(ContainerProfile profile, MigrationLog.Phase phase) {
        double bw = config.getTopology().getServerBw();
        double factor = switch (phase) {
            case LOCAL -> 1.0;
            case INTER_FOG -> 0.8;
            case CLOUD -> economicsConfig.getCloudBandwidthFactor();
        };
        double effectiveBw = Math.max(1.0, bw * factor);
        return profile.getSizeMb() / effectiveBw + 0.1 * profile.getSizeMb() / effectiveBw;
    }

    private double migrationCost(ContainerProfile profile, MigrationLog.Phase phase) {
        double bw = config.getTopology().getServerBw();
        double factor = switch (phase) {
            case LOCAL -> 1.0;
            case INTER_FOG -> 0.8;
            case CLOUD -> economicsConfig.getCloudBandwidthFactor();
        };
        double effectiveBw = Math.max(1.0, bw * factor);
        return profile.getSizeMb() / effectiveBw + 0.1 * profile.getSizeMb();
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

    private void removeFromServer(ServerState server) {
        if (server == null || server.getContainerId() == null) {
            return;
        }
        ContainerProfile profile = activeProfiles.remove(server.getContainerId());
        if (profile != null) {
            server.setCpuUsed(Math.max(0.0, server.getCpuUsed() - profile.getRequiredCpu()));
            server.setMemUsed(Math.max(0.0, server.getMemUsed() - profile.getRequiredMem()));
            server.setBwUsed(Math.max(0.0, server.getBwUsed() - profile.getRequiredBw()));
        }
        server.setContainerId(null);
    }

    private void finalizeBids(long requestId) {
        PendingBid bid = pendingBids.remove(requestId);
        if (bid == null) {
            return;
        }
        double now = CloudSim.clock();
        activeProfiles.remove(bid.profile.getId());
        double bestCost = Double.POSITIVE_INFINITY;
        Integer winner = null;
        for (BidResponse resp : bid.responses.values()) {
            double cSla = clampReputation(reputation.getOrDefault(resp.getBidderFogId(), slaConfig.getReputationInit()));
            double total = resp.totalCost() + cSla;
            if (total < bestCost) {
                bestCost = total;
                winner = resp.getBidderFogId();
            }
        }
        if (bid.includeCloud && bid.cloudCost < bestCost) {
            winner = -1;
            bestCost = bid.cloudCost;
        }
        if (winner == null) {
            winner = -1;
        }
        MigrationLog.Phase phase = winner == fogNodeId ? MigrationLog.Phase.LOCAL : (winner == -1 ? MigrationLog.Phase.CLOUD : MigrationLog.Phase.INTER_FOG);
        double duration = estimateMigrationSeconds(bid.profile, phase);
        boolean skipLog = false;
        int targetServer = -1;
        if (winner == fogNodeId) {
            ServerState srv = pickBestLocal(bid.profile);
            if (srv != null) {
                assignToServer(srv, bid.profile);
                targetServer = srv.getServerId();
                skipLog = true;
            }
        }
        MigrationLog log = new MigrationLog(bid.profile.getId(), fogNodeId, winner, bid.sourceServerId, targetServer, phase, now);
        log.setCompletedAt(now + duration);
        boolean deadlineMet = now + duration <= bid.profile.getArrivalTime() + bid.profile.getDeadlineSec();
        log.setDeadlineMet(deadlineMet);
        log.setSuccess(true);
        if (!skipLog) {
            MetricsSink.get().recordMigration(log);
        }

        PaymentReport pay = computePayment(bid.profile, winner, deadlineMet, now + duration);
        send(CloudSim.getEntityId("decision-agent-" + fogNodeId), 0.0, SimulationEvents.EVT_PAYMENT, pay);
        if (winner >= 0) {
            send(CloudSim.getEntityId("decision-agent-" + winner), 0.0, SimulationEvents.EVT_PAYMENT, pay);
        }
    }

    private PaymentReport computePayment(ContainerProfile profile, int payee, boolean deadlineMet, double ts) {
        double reliability = deadlineMet ? 1.0 : 0.0;
        double urgency = computeUrgency(profile, ts);
        double slaValue = slaConfig.getReliabilityWeight() * reliability + slaConfig.getUrgencyWeight() * urgency;
        slaValue = Math.min(1.0, Math.max(0.0, slaValue));
        double payment = deadlineMet ? slaValue : -slaConfig.getPenaltyEta() * slaValue;
        return new PaymentReport(profile.getId(), fogNodeId, payee, slaValue, payment, !deadlineMet,
                reliability, urgency, ts);
    }

    private double clampReputation(double val) {
        double min = slaConfig.getReputationMin();
        double max = slaConfig.getReputationMax();
        return Math.max(min, Math.min(max, val));
    }

    private record BidTimeout(long requestId) {
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
