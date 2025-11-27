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
import org.collabft.util.ResourceUtil;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Cloud fallback / central scheduler host.
 */
public class CloudDevice extends FogDevice {
    private final List<ContainerModule> hosted = new ArrayList<>();
    private final Queue<MigrationTransfer> pending = new ArrayDeque<>();
    private double tokenBalance;
    private final ResourceCapacity capacity;
    private final SimulationConfig.BiddingConfig biddingConfig;
    private final SimulationConfig.Network network;
    private double usedCpu;
    private double usedRam;
    private double usedBw;

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
                    if (canHost(transfer.getContainer().getProfile())) {
                        startTransfer(transfer);
                    } else {
                        queueTransfer(transfer);
                    }
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
                    if (notice.getRunVersion() == notice.getContainer().getRunVersion()) {
                        release(notice.getContainer());
                        notice.getContainer().markCompleted();
                        send(notice.getContainer().getOwnerId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.TASK_COMPLETE, notice);
                        drainQueue();
                    }
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
        double share = sharePerContainer();
        double seconds = module.getRemainingWorkMi() / Math.max(1e-6, share);
        return Math.max(CloudSim.getMinTimeBetweenEvents(), seconds);
    }

    private double sharePerContainer() {
        double effectiveCpu = capacity.getCpuMips();
        return effectiveCpu / Math.max(1, hosted.size());
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

    private boolean canHost(ContainerProfile profile) {
        return ResourceUtil.feasible(capacity, usedCpu, usedRam, usedBw, profile, 1.0, 1.0);
    }

    private void startTransfer(MigrationTransfer transfer) {
        ContainerModule module = transfer.getContainer();
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
        module.startRun(finish, sharePerContainer(), execSeconds);
        send(getId(), execSeconds + transferSeconds + transfer.getLatencySeconds(), CollabSimTags.TASK_COMPLETE,
                new CompletionNotice(module, getName(), finish, completionAt, module.getRunVersion()));
    }

    private void addContainer(ContainerModule module) {
        hosted.add(module);
        usedCpu += module.getProfile().getCpuMips();
        usedRam += module.getProfile().getRamMb();
        usedBw += module.getProfile().getBandwidth();
        module.setHostName(getName());
    }

    private void release(ContainerModule module) {
        hosted.remove(module);
        usedCpu -= module.getProfile().getCpuMips();
        usedRam -= module.getProfile().getRamMb();
        usedBw -= module.getProfile().getBandwidth();
    }

    private void queueTransfer(MigrationTransfer transfer) {
        pending.add(transfer);
    }

    private void drainQueue() {
        if (pending.isEmpty()) {
            return;
        }
        int attempts = pending.size();
        for (int i = 0; i < attempts; i++) {
            MigrationTransfer transfer = pending.peek();
            if (transfer == null) {
                pending.poll();
                continue;
            }
            if (canHost(transfer.getContainer().getProfile())) {
                pending.poll();
                startTransfer(transfer);
            } else {
                break;
            }
        }
    }
}
