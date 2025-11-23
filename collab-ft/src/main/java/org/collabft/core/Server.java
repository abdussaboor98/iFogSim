package org.collabft.core;

import org.collabft.model.ContainerProfile;
import org.collabft.model.ServerState;

/**
 * Convenience wrapper for a server and its capacity checks.
 */
public class Server {

    private final ServerState state;

    public Server(ServerState state) {
        this.state = state;
    }

    public boolean canHost(ContainerProfile profile) {
        return state.canHost(profile);
    }

    public ServerState getState() {
        return state;
    }
}
