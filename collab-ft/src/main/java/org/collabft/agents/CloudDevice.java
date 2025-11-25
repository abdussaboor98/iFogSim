package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.events.CollabSimTags;
import org.collabft.economy.BidResponse;
import org.collabft.model.ContainerModule;
import org.collabft.model.MigrationRequest;
import org.collabft.model.MigrationResult;
import org.collabft.model.MigrationTransfer;
import org.collabft.model.ResourceCapacity;
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

    public CloudDevice(String name, ResourceCapacity capacity) throws Exception {
        this(name, capacity, FogDeviceFactory.build(capacity, new FogLinearPowerModel(150, 30)));
    }

    private CloudDevice(String name, ResourceCapacity capacity, Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.capacity = capacity;
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
                    MetricsRegistry.collector().recordNetwork("migration", transfer.getSourceName(), getName(), module.getProfile().getContainerSizeMb() * 1024 * 1024, CloudSim.clock());
                    send(transfer.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                            new MigrationResult(module, transfer.getKind(), transfer.getSourceName(), getName(), module.getMigrationStart(), finish, 0, module.getProfile().getContainerSizeMb(), true, transfer.getTrigger()));
                }
                break;
            case MIGRATION_REQUEST:
                if (ev.getData() instanceof MigrationRequest request) {
                    hosted.add(request.getContainer());
                    send(request.getOriginId(), 0, CollabSimTags.BID_RESPONSE,
                            new BidResponse(getId(), request.getContainer().getContainerId(), true, 1.0, 0.0));
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
}
