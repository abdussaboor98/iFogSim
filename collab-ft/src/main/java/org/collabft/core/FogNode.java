package org.collabft.core;

import org.collabft.model.ContainerProfile;
import org.collabft.model.ServerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Encapsulates scheduling decisions within a fog node across its servers.
 */
public class FogNode {

    private final int nodeId;
    private final List<ServerState> servers;
    private final Random rng;

    public FogNode(int nodeId, List<ServerState> servers, long seed) {
        this.nodeId = nodeId;
        this.servers = new ArrayList<>(Objects.requireNonNull(servers, "servers"));
        this.rng = seed == 0 ? new Random() : new Random(seed);
    }

    public ServerState place(ContainerProfile profile) {
        List<ServerState> feasible = new ArrayList<>();
        for (ServerState server : servers) {
            if (server.canHost(profile)) {
                feasible.add(server);
            }
        }
        if (feasible.isEmpty()) {
            return null;
        }
        double best = -1.0;
        List<ServerState> bestList = new ArrayList<>();
        for (ServerState server : feasible) {
            double score = server.residualScore();
            if (score > best) {
                best = score;
                bestList.clear();
                bestList.add(server);
            } else if (Math.abs(score - best) < 1e-9) {
                bestList.add(server);
            }
        }
        return bestList.get(rng.nextInt(bestList.size()));
    }

    public List<ServerState> getServers() {
        return servers;
    }

    public int getNodeId() {
        return nodeId;
    }
}
