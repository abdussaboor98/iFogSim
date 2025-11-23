package org.collabft.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.model.FogNodeState;
import org.collabft.model.GossipMessage;
import org.collabft.model.ServerState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Periodic state dissemination agent implementing ring-based gossip and staleness filtering.
 */
public class GossipAgent extends SimEntity {

    private final int fogNodeId;
    private final SimulationConfig config;
    private final int ringSize;
    private final Map<Integer, FogNodeState> stateTable = new HashMap<>();
    private final double stalenessSeconds;
    private final Path logFile;
    private final ObjectMapper mapper;
    private int neighborAgentId = -1;
    private FogNodeState localState;
    private long sequence = 0L;

    public GossipAgent(int fogNodeId, int ringSize, SimulationConfig config) {
        super("gossip-agent-" + fogNodeId);
        this.fogNodeId = fogNodeId;
        this.ringSize = ringSize;
        this.config = Objects.requireNonNull(config, "config");
        this.stalenessSeconds = config.getGossip().getIntervalSec() * config.getGossip().getStalenessIntervals();
        this.mapper = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.logFile = Path.of("results", "collab-ft", "gossip", "fog-" + fogNodeId + ".jsonl");
        this.localState = new FogNodeState(fogNodeId);
        stateTable.put(fogNodeId, localState);
    }

    @Override
    public void startEntity() {
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create gossip log directory", e);
        }
        double interval = config.getGossip().getIntervalSec();
        send(getId(), interval, SimulationEvents.EVT_GOSSIP_TICK);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == SimulationEvents.EVT_GOSSIP_TICK) {
            onGossipTick();
        } else if (ev.getTag() == SimulationEvents.EVT_GOSSIP_MESSAGE) {
            onGossipMessage((GossipMessage) ev.getData());
        }
    }

    private void onGossipTick() {
        double now = CloudSim.clock();
        refreshLocalEntry(now);
        sendSnapshot(now);
        logSnapshot(now);
        double interval = config.getGossip().getIntervalSec();
        send(getId(), interval, SimulationEvents.EVT_GOSSIP_TICK);
    }

    private void sendSnapshot(double now) {
        if (neighborAgentId < 0) {
            return;
        }
        int nextFogId = (fogNodeId + 1) % Math.max(1, ringSize);
        GossipMessage msg = new GossipMessage();
        msg.setSenderId(fogNodeId);
        msg.setReceiverId(nextFogId);
        msg.setTimestamp(now);
        msg.setSequence(sequence++);
        msg.setStateTable(new ArrayList<>(freshEntries(now)));
        send(neighborAgentId, 0.0, SimulationEvents.EVT_GOSSIP_MESSAGE, msg);
    }

    private void onGossipMessage(GossipMessage msg) {
        double now = CloudSim.clock();
        if (msg == null) {
            return;
        }
        for (FogNodeState incoming : msg.getStateTable()) {
            if (incoming.getNodeId() == fogNodeId) {
                continue; // immutable by others
            }
            if (incoming.isStale(now, stalenessSeconds)) {
                continue;
            }
            FogNodeState existing = stateTable.get(incoming.getNodeId());
            if (existing == null || incoming.getTimestamp() > existing.getTimestamp()) {
                stateTable.put(incoming.getNodeId(), copy(incoming));
            }
        }
    }

    private void refreshLocalEntry(double now) {
        FogNodeState copy = copy(localState);
        copy.setTimestamp(now);
        copy.setVersion(localState.getVersion() + 1);
        stateTable.put(fogNodeId, copy);
        localState = copy;
    }

    private List<FogNodeState> freshEntries(double now) {
        List<FogNodeState> fresh = new ArrayList<>();
        for (FogNodeState state : stateTable.values()) {
            if (!state.isStale(now, stalenessSeconds)) {
                fresh.add(copy(state));
            }
        }
        return fresh;
    }

    private void logSnapshot(double now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fogNodeId", fogNodeId);
        payload.put("timestamp", now);
        payload.put("sequence", sequence);
        payload.put("entries", freshEntries(now));
        payload.put("wallClock", Instant.now().toString());
        try {
            Files.writeString(logFile, mapper.writeValueAsString(payload) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Logging failure should not halt simulation
        }
    }

    @Override
    public void shutdownEntity() {
        // No-op
    }

    public Map<Integer, FogNodeState> getStateTable() {
        return Collections.unmodifiableMap(stateTable);
    }

    public int getFogNodeId() {
        return fogNodeId;
    }

    public void setNeighborAgentId(int neighborAgentId) {
        this.neighborAgentId = neighborAgentId;
    }

    public void updateLocalState(FogNodeState state) {
        if (state == null || state.getNodeId() != fogNodeId) {
            return;
        }
        this.localState = copy(state);
    }

    public Map<Integer, FogNodeState> freshView() {
        double now = CloudSim.clock();
        Map<Integer, FogNodeState> fresh = new HashMap<>();
        for (FogNodeState state : stateTable.values()) {
            if (!state.isStale(now, stalenessSeconds)) {
                fresh.put(state.getNodeId(), copy(state));
            }
        }
        return fresh;
    }

    private FogNodeState copy(FogNodeState source) {
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
        List<ServerState> serverCopies = new ArrayList<>();
        for (ServerState server : source.getServers()) {
            serverCopies.add(copy(server));
        }
        dest.setServers(serverCopies);
        return dest;
    }

    private ServerState copy(ServerState server) {
        ServerState dest = new ServerState(server.getFogNodeId(), server.getServerId(),
                server.getCpuTotal(), server.getMemTotal(), server.getBwTotal());
        dest.setCpuUsed(server.getCpuUsed());
        dest.setMemUsed(server.getMemUsed());
        dest.setBwUsed(server.getBwUsed());
        dest.setCrashed(server.isCrashed());
        dest.setContainerId(server.getContainerId());
        dest.setTimestamp(server.getTimestamp());
        return dest;
    }
}
