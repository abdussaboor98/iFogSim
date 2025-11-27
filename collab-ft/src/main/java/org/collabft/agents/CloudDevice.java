package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.events.CollabSimTags;
import org.collabft.economy.BidResponse;
import org.collabft.model.*;
import org.collabft.metrics.MetricsRegistry;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloud fallback / central scheduler host.
 */
public class CloudDevice extends FogDevice {
    private final List<ContainerModule> hosted = new ArrayList<>();
    private double tokenBalance;
    private final ResourceCapacity capacity;
    private final SimulationConfig.BiddingConfig biddingConfig;
    private final SimulationConfig.Network network;

    public CloudDevice(String name, ResourceCapacity capacity, SimulationConfig.BiddingConfig biddingConfig, SimulationConfig.Network network) throws Exception {
        this(name, capacity, biddingConfig, network, FogDeviceFactory.build(capacity, new FogLinearPowerModel(150, 30)));
    }

    private CloudDevice(String name, ResourceCapacity capacity, SimulationConfig.BiddingConfig biddingConfig, SimulationConfig.Network network, Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.capacity = capacity;
        this.biddingConfig = biddingConfig;
        this.network = network;
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
            case MIGRATION_START:
                if (ev.getData() instanceof MigrationTransfer transfer) {
                    ContainerModule module = transfer.getContainer();
                    hosted.add(module);
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
            case MIGRATION_REQUEST:
                if (ev.getData() instanceof MigrationRequest request) {
                    BidResponse bid = buildBid(request.getContainer());
                    MetricsRegistry.collector().recordBid(request.getContainer().getContainerId(), getId(), getName(), bid.getScore(), bid.getCost(), bid.isFeasible(), CloudSim.clock());
                    send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE, bid);
                }
                break;
            case BID_REQUEST:
                if (ev.getData() instanceof MigrationRequest request) {
                    BidResponse bid = buildBid(request.getContainer());
                    MetricsRegistry.collector().recordBid(request.getContainer().getContainerId(), getId(), getName(), bid.getScore(), bid.getCost(), bid.isFeasible(), CloudSim.clock());
                    send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.BID_RESPONSE, bid);
                }
                break;
            case TASK_COMPLETE:
                if (ev.getData() instanceof CompletionNotice notice) {
                    hosted.remove(notice.getContainer());
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

    private double computeExecutionSeconds(ContainerModule module) {
        double effectiveCpu = capacity.getCpuMips();
        double share = effectiveCpu / Math.max(1, hosted.size());
        double seconds = module.getProfile().getCpuMips() / Math.max(1, share);
        return Math.max(CloudSim.getMinTimeBetweenEvents(), seconds);
    }

    private BidResponse buildBid(ContainerModule container) {
        ContainerProfile profile = container.getProfile();
        double cres = profile.getCpuMips() * biddingConfig.getCpuUnitCost()
                + profile.getRamMb() * biddingConfig.getMemUnitCost()
                + profile.getBandwidth() * biddingConfig.getBwUnitCost();
        double crisk = 0; // assume stable cloud capacity
        double linkBw = Math.max(1, Math.min(capacity.getUplinkBandwidth(), network.getFogCloudBandwidthMbps()));
        double transferSeconds = profile.getContainerSizeMb() / linkBw;
        double latencySeconds = network.getFogCloudLatencyMs() / 1000.0;
        double cmig = transferSeconds + biddingConfig.getMigrationRestoreFactor() * profile.getContainerSizeMb();
        double latencyPenaltySeconds = transferSeconds + latencySeconds;
        // Apply a strong multiplier so fog-cloud latency meaningfully increases the bid cost.
        double latencyPenalty = latencyPenaltySeconds * biddingConfig.getFailureWeight() * 100;
        double cost = cres + crisk + cmig + latencyPenalty;
        return new BidResponse(getId(), container.getContainerId(), true, 0.1, cost);
    }
}
