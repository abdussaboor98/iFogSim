package org.fog.scheduler.server;

import org.fog.entities.FogDevice;
import org.fog.entities.FogServer;
import org.fog.entities.container.ContainerInstance;

import java.util.List;

/**
 * Chooses the server with the lowest CPU utilization among the candidates.
 */
public class LeastLoadedServerScheduler implements FogServerScheduler {

    @Override
    public FogServer selectServer(FogDevice device, ContainerInstance container, List<FogServer> candidates) {
        FogServer best = null;
        double lowestLoad = Double.MAX_VALUE;
        for (FogServer server : candidates) {
            if (!server.canHost(container)) {
                continue;
            }
            double load = server.getCpuUtilizationFraction();
            if (load < lowestLoad) {
                lowestLoad = load;
                best = server;
            }
        }
        if (best == null && !candidates.isEmpty()) {
            // Fall back to the first candidate to keep the system moving when all are saturated.
            for (FogServer server : candidates) {
                if (server.canHost(container)) {
                    return server;
                }
            }
        }
        return best;
    }
}
