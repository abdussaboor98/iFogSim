package org.collabft.core;

import org.collabft.model.FogNodeState;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable view of gossip state shared across fog nodes.
 */
public class GossipState {

    private final Map<Integer, FogNodeState> view = new HashMap<>();

    public Map<Integer, FogNodeState> getView() {
        return view;
    }

    public void update(FogNodeState state) {
        if (state == null) {
            return;
        }
        FogNodeState existing = view.get(state.getNodeId());
        if (existing == null || state.getTimestamp() > existing.getTimestamp()) {
            view.put(state.getNodeId(), state);
        }
    }
}
