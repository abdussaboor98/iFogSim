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
        entries.put(nodeId, entry);
    }

    public GossipStateEntry get(int nodeId) {
        return entries.get(nodeId);
    }
}
