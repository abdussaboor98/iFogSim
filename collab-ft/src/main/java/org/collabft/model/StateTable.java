package org.collabft.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains gossip-learned state for neighbors.
 */
public class StateTable {
    private final Map<Integer, GossipStateEntry> entries = new HashMap<>();

    public Map<Integer, GossipStateEntry> getEntries() {
        return entries;
    }

    public void update(int nodeId, GossipStateEntry entry) {
        GossipStateEntry existing = entries.get(nodeId);
        if (existing == null || entry.getTimestamp() >= existing.getTimestamp()) {
            entries.put(nodeId, entry);
        }
    }

    public GossipStateEntry get(int nodeId) {
        return entries.get(nodeId);
    }

    public void merge(Map<Integer, GossipStateEntry> incoming) {
        if (incoming == null) {
            return;
        }
        for (Map.Entry<Integer, GossipStateEntry> e : incoming.entrySet()) {
            update(e.getKey(), e.getValue());
        }
    }
}
