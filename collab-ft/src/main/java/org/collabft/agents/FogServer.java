package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.collabft.util.ResourceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single physical server within a fog node.
 */
public class FogServer extends FogDevice {
    private final ResourceCapacity capacity;
    private final List<ContainerModule> containers = new ArrayList<>();
    private double usedCpu;
    private double usedRam;
    private double usedBw;
    private boolean failed;

    public FogServer(String name, ResourceCapacity capacity) throws Exception {
        this(name, capacity, FogDeviceFactory.build(capacity, new FogLinearPowerModel(100, 10)));
    }

    private FogServer(String name, ResourceCapacity capacity, Components components) throws Exception {
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
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.RECOVERY_EVENT) {
            failed = false;
        }
    }

    public boolean canHost(ContainerProfile profile) {
        return !failed && ResourceUtil.feasible(capacity, usedCpu, usedRam, usedBw, profile);
    }

    public double residualScore(ContainerProfile profile) {
        return failed ? -1 : ResourceUtil.residualScore(capacity, usedCpu, usedRam, usedBw, profile);
    }

    public void addContainer(ContainerModule container) {
        containers.add(container);
        usedCpu += container.getProfile().getCpuMips();
        usedRam += container.getProfile().getRamMb();
        usedBw += container.getProfile().getBandwidth();
        container.setHostName(getName());
    }

    public void removeContainer(ContainerModule container) {
        containers.remove(container);
        usedCpu -= container.getProfile().getCpuMips();
        usedRam -= container.getProfile().getRamMb();
        usedBw -= container.getProfile().getBandwidth();
    }

    public List<ContainerModule> getContainers() {
        return containers;
    }

    public double getCpuLoad() {
        return usedCpu / capacity.getCpuMips();
    }

    public double getMemLoad() {
        return usedRam / capacity.getRamMb();
    }

    public double getBwLoad() {
        return usedBw / capacity.getBandwidth();
    }

    public void markFailed() {
        this.failed = true;
    }

    public boolean isFailed() {
        return failed;
    }

    public ResourceCapacity getCapacity() {
        return capacity;
    }
}
