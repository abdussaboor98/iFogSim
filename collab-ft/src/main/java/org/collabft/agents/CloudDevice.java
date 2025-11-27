package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.events.CollabSimTags;
import org.collabft.economy.BidResponse;
import org.collabft.model.*;
import org.collabft.model.ResourceCapacity;
import org.collabft.metrics.MetricsRegistry;
import org.collabft.util.ResourceUtil;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.collabft.config.SimulationConfig;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloud fallback / central scheduler host.
 */
public class CloudDevice extends FogDevice {
    private final List<ContainerModule> hosted = new ArrayList<>();
    private double tokenBalance;
    private final ResourceCapacity capacity;
    private final SimulationConfig.Network network;
    private final Position location;
    private Map<Integer, Position> locations = new HashMap<>();
    private double usedCpu;
    private double usedRam;
    private double usedBw;

    public CloudDevice(String name, ResourceCapacity capacity, Position location, SimulationConfig.Network network) throws Exception {
        this(name, capacity, location, network, FogDeviceFactory.build(capacity, new FogLinearPowerModel(150, 30)));
    }

    private CloudDevice(String name, ResourceCapacity capacity, Position location, SimulationConfig.Network network, Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.capacity = capacity;
        this.network = network;
        this.location = location;
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev == null) {
            return;
        }
        if (!(ev.getTag() instanceof CollabSimTags tag)) {
            return;
        }
        switch (tag) {
            case BID_REQUEST:
            case MIGRATION_REQUEST:
                if (ev.getData() instanceof MigrationRequest request) {
                    handleBidRequest(request);
                }
                break;
            case MIGRATION_START:
                if (ev.getData() instanceof MigrationTransfer transfer) {
                    ContainerModule module = transfer.getContainer();
                    if (!canHost(module.getProfile())) {
                        send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                                new MigrationResult(module, transfer.getKind(), transfer.getSourceName(), "cloud-unplaced",
                                        module.getMigrationStart(), CloudSim.clock(), 0, 0, false, transfer.getTrigger()));
                        return;
                    }
                    addContainer(module);
                    double transferSeconds = transfer.getLinkBandwidthMbps() > 0
                            ? module.getProfile().getContainerSizeMb() / transfer.getLinkBandwidthMbps()
                            : 0;
                    double finish = CloudSim.clock() + transferSeconds + transfer.getLatencySeconds();
                    double execSeconds = computeExecutionSeconds(module);
                    double completionAt = finish + execSeconds;
                    MetricsRegistry.collector().recordNetwork("migration", transfer.getSourceName(), getName(), module.getProfile().getContainerSizeMb() * 1024 * 1024, CloudSim.clock());
                    send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                            new MigrationResult(module, transfer.getKind(), transfer.getSourceName(), getName(), module.getMigrationStart(), finish, 0, module.getProfile().getContainerSizeMb(), true, transfer.getTrigger()));
                    send(getId(), execSeconds + transferSeconds + transfer.getLatencySeconds(), CollabSimTags.TASK_COMPLETE,
                            new CompletionNotice(module, getName(), finish, completionAt));
                }
                break;
            case TASK_COMPLETE:
                if (ev.getData() instanceof CompletionNotice notice) {
                    removeContainer(notice.getContainer());
                    send(notice.getContainer().getOwnerId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.TASK_COMPLETE, notice);
                }
                break;
            default:
                break;
        }
    }

    public void credit(double amount) {
        tokenBalance += amount;
    }

    public double getTokenBalance() {
        return tokenBalance;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }

    public Position getLocation() {
        return location;
    }

    public void setLocations(Map<Integer, Position> locations) {
        this.locations = new HashMap<>(locations);
    }

    public boolean canHost(ContainerProfile profile) {
        return ResourceUtil.feasible(capacity, usedCpu, usedRam, usedBw, profile);
    }

    private double residualScore(ContainerProfile profile) {
        return ResourceUtil.residualScore(capacity, usedCpu, usedRam, usedBw, profile);
    }

    private double computeBidCost(ContainerProfile profile, int originId) {
        double cres = profile.getCpuMips() * 0.5
                + profile.getRamMb() * 0.1
                + profile.getBandwidth() * 0.05;
        double utilization = (usedCpu + profile.getCpuMips()) / Math.max(1, capacity.getCpuMips());
        double crisk = utilization * 2;
        double cmig = profile.getContainerSizeMb() / Math.max(1, capacity.getUplinkBandwidth());
        double clat = (latencyMsTo(originId) / 1000.0) * profile.getBandwidth();
        return cres + crisk + cmig + clat * 3; // amplify WAN penalty
    }

    private double latencyPenalty(int originId) {
        double latencySeconds = latencyMsTo(originId) / 1000.0;
        double norm = Math.max(1e-3, latencySeconds);
        return latencySeconds / norm;
    }

    private double computeExecutionSeconds(ContainerModule module) {
        double effectiveCpu = capacity.getCpuMips();
        double share = effectiveCpu / Math.max(1, hosted.size());
        double seconds = module.getProfile().getCpuMips() / Math.max(1, share);
        return Math.max(CloudSim.getMinTimeBetweenEvents(), seconds);
    }

    private void handleBidRequest(MigrationRequest request) {
        ContainerModule container = request.getContainer();
        double baseScore = residualScore(container.getProfile());
        double score = Math.max(0, baseScore - latencyPenalty(request.getOriginId()));
        boolean feasible = score > 0;
        double cost = feasible ? computeBidCost(container.getProfile(), request.getOriginId()) : Double.MAX_VALUE;
        send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE,
                new BidResponse(getId(), container.getContainerId(), feasible, score, cost));
    }

    private void addContainer(ContainerModule module) {
        hosted.add(module);
        usedCpu += module.getProfile().getCpuMips();
        usedRam += module.getProfile().getRamMb();
        usedBw += module.getProfile().getBandwidth();
    }

    private void removeContainer(ContainerModule module) {
        hosted.remove(module);
        usedCpu = Math.max(0, usedCpu - module.getProfile().getCpuMips());
        usedRam = Math.max(0, usedRam - module.getProfile().getRamMb());
        usedBw = Math.max(0, usedBw - module.getProfile().getBandwidth());
    }

    private double latencyMsTo(int targetId) {
        Position target = locations.get(targetId);
        if (target != null && location != null) {
            return network.getBaseLatencyMs() + location.distanceTo(target) * network.getLatencyMsPerUnit();
        }
        return network.getFogCloudLatencyMs();
    }
}
