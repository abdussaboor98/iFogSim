package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.metrics.MetricsRegistry;

import java.util.List;

/**
 * Periodically sends gossip snapshots in a ring.
 */
public class GossipAgent extends SimEntity {
    private final List<FogNodeController> nodes;
    private final double intervalSeconds;

    public GossipAgent(String name, List<FogNodeController> nodes, double intervalSeconds) {
        super(name);
        this.nodes = nodes;
        this.intervalSeconds = intervalSeconds;
    }

    @Override
    public void startEntity() {
        send(getId(), intervalSeconds, CollabSimTags.GOSSIP_TICK);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.GOSSIP_TICK) {
            propagate();
            send(getId(), intervalSeconds, CollabSimTags.GOSSIP_TICK);
        }
    }

    @Override
    public void shutdownEntity() {
    }

    private void propagate() {
        if (nodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            FogNodeController current = nodes.get(i);
            FogNodeController next = nodes.get((i + 1) % nodes.size());
            send(next.getId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.GOSSIP_EVENT, current.snapshotLoad());
            // Control-plane overhead per gossip hop
            MetricsRegistry.collector().recordGossip(current.getId(), next.getId(), current.snapshotLoad().size(), CloudSim.clock());
        }
    }
}
