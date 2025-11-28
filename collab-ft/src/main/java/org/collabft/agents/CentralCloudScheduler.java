package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.MigrationRequest;
import org.collabft.model.MigrationTransfer;
import org.collabft.metrics.MetricsCollector;
import org.collabft.metrics.MetricsRegistry;
import org.collabft.config.SimulationConfig;

import java.util.List;
import java.util.Optional;

/**
 * Centralized scheduler used in simulation mode 2.
 */
public class CentralCloudScheduler extends SimEntity {
    private final List<FogNodeController> controllers;
    private final CloudDevice cloud;
    private final SimulationConfig.Network network;
    private final SimulationConfig.BiddingConfig biddingConfig;

    public CentralCloudScheduler(String name, List<FogNodeController> controllers, CloudDevice cloud, SimulationConfig.Network network, SimulationConfig.BiddingConfig biddingConfig) {
        super(name);
        this.controllers = controllers;
        this.cloud = cloud;
        this.network = network;
        this.biddingConfig = biddingConfig;
    }

    @Override
    public void startEntity() {
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.MIGRATION_REQUEST
                && ev.getData() instanceof MigrationRequest request) {
            handleRequest(request);
        }
    }

    @Override
    public void shutdownEntity() {
    }

    private void handleRequest(MigrationRequest request) {
        ContainerModule container = request.getContainer();
        Optional<Placement> placement = pickTarget(container);
        if (placement.isPresent()) {
            Placement best = placement.get();
            MetricsRegistry.collector().recordCentralPlacement(
                    container.getContainerId(),
                    best.controller().getName(),
                    best.server().getName(),
                    false,
                    best.bidValue(),
                    CloudSim.clock());
            container.recordLastBid(best.controller().getId(), best.bidValue());
            send(best.controller().getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.INTER_FOG,
                            best.linkBandwidth(), network.getInterFogLatencyMs() / 1000.0, container.getMigrationTrigger()));
            MetricsRegistry.collector().markBidWinner(container.getContainerId(), best.controller().getId());
        } else {
            double linkBw = Math.min(cloud.getCapacity().getUplinkBandwidth(), network.getFogCloudBandwidthMbps());
            double migrationTime = biddingConfig.getPauseSeconds() + container.getProfile().getContainerSizeMb() / Math.max(1e-6, linkBw) + biddingConfig.getResumeSeconds();
            MetricsRegistry.collector().recordCentralPlacement(
                    container.getContainerId(),
                    cloud.getName(),
                    cloud.getName(),
                    true,
                    migrationTime,
                    CloudSim.clock());
            container.recordLastBid(cloud.getId(), migrationTime);
            send(cloud.getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.CLOUD,
                            linkBw,
                            network.getFogCloudLatencyMs() / 1000.0,
                            container.getMigrationTrigger()));
            MetricsRegistry.collector().markBidWinner(container.getContainerId(), cloud.getId());
        }
    }

    private String sourceName(ContainerModule container, int originId) {
        if (container.getHostName() != null) {
            return container.getHostName();
        }
        for (FogNodeController controller : controllers) {
            if (controller.getId() == originId) {
                return controller.getName();
            }
        }
        return "unknown";
    }

    private Optional<Placement> pickTarget(ContainerModule container) {
        double bestBid = Double.MAX_VALUE;
        Placement best = null;
        for (FogNodeController controller : controllers) {
            double linkBw = Math.min(controller.getCapacity().getUplinkBandwidth(), network.getInterFogBandwidthMbps());
            double migrationTime = biddingConfig.getPauseSeconds()
                    + container.getProfile().getContainerSizeMb() / Math.max(1e-6, linkBw)
                    + biddingConfig.getResumeSeconds();
            double timeToDeadline = Math.max(0, container.getDeadlineSeconds() - (CloudSim.clock() - container.getArrivalTime()));
            if (migrationTime >= timeToDeadline) {
                continue;
            }
            NodeCapacity cap = capacity(controller);
            double resourceImpact = container.getProfile().getDemandMips() / Math.max(1e-6, cap.cpu())
                    + container.getProfile().getRamMb() / Math.max(1e-6, cap.mem())
                    + container.getProfile().getBandwidth() / Math.max(1e-6, linkBw);
            double bidValue = migrationTime + biddingConfig.getKResourceImpact() * resourceImpact;
            for (FogServer server : controller.getServers()) {
                double projectedCpu = (server.getUsedCpu() + container.getProfile().getDemandMips()) / Math.max(1e-6, server.getCapacity().getCpuMips() * server.getCpuFactor());
                double projectedMem = (server.getUsedRam() + container.getProfile().getRamMb()) / Math.max(1e-6, server.getCapacity().getRamMb());
                double projectedBw = (server.getUsedBw() + container.getProfile().getBandwidth()) / Math.max(1e-6, server.getCapacity().getBandwidth() * server.getBwFactor());
                if (projectedCpu > 1.0 || projectedMem > 1.0 || projectedBw > 1.0 || server.isFaulted()) {
                    continue;
                }
                double headroom = Math.max(0, (1 - projectedCpu) + (1 - projectedMem) + (1 - projectedBw));
                if (bidValue < bestBid
                        || (Math.abs(bidValue - bestBid) < 1e-6 && best != null && headroom > best.headroom())) {
                    bestBid = bidValue;
                    best = new Placement(controller, server, bidValue, headroom, linkBw);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private NodeCapacity capacity(FogNodeController controller) {
        double cpu = controller.getServers().stream().mapToDouble(s -> s.getCapacity().getCpuMips() * s.getCpuFactor()).sum();
        double mem = controller.getServers().stream().mapToDouble(s -> s.getCapacity().getRamMb()).sum();
        double bw = controller.getServers().stream().mapToDouble(s -> s.getCapacity().getBandwidth() * s.getBwFactor()).sum();
        return new NodeCapacity(cpu, mem, bw);
    }

    private record Placement(FogNodeController controller, FogServer server, double bidValue, double headroom, double linkBandwidth) { }

    private record NodeCapacity(double cpu, double mem, double bw) { }
}
