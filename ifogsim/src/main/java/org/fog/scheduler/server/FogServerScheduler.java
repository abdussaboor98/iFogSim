package org.fog.scheduler.server;

import org.fog.entities.FogDevice;
import org.fog.entities.FogServer;
import org.fog.entities.container.ContainerInstance;

import java.util.List;

/**
 * Policy used by a {@link FogDevice} to decide which internal server should host a container.
 */
public interface FogServerScheduler {

    /**
     * Picks the server that should host the container.
     *
     * @param device     current fog device
     * @param container  container to place
     * @param candidates list of candidate servers (already filtered)
     * @return the chosen server or {@code null} when none fits
     */
    FogServer selectServer(FogDevice device, ContainerInstance container, List<FogServer> candidates);
}
