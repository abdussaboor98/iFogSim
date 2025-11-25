package org.collabft.util;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.collabft.model.ResourceCapacity;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogUtils;

import java.util.Collections;
import java.util.List;

/**
 * Builds FogDeviceCharacteristics and allocation policies using ResourceCapacity.
 */
public final class FogDeviceFactory {

    private FogDeviceFactory() {
    }

    public static Components build(ResourceCapacity capacity, PowerModel powerModel) {
        List<Pe> peList = Collections.singletonList(new Pe(0, new PeProvisionerOverbooking(capacity.getCpuMips())));
        int hostId = FogUtils.generateEntityId();
        long storage = capacity.getStorageMb();

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(capacity.getRamMb()),
                new BwProvisionerOverbooking((int) capacity.getBandwidth()),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                powerModel
        );

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                Config.FOG_DEVICE_ARCH,
                Config.FOG_DEVICE_OS,
                Config.FOG_DEVICE_VMM,
                host,
                Config.FOG_DEVICE_TIMEZONE,
                Config.FOG_DEVICE_COST,
                Config.FOG_DEVICE_COST_PER_MEMORY,
                Config.FOG_DEVICE_COST_PER_STORAGE,
                Config.FOG_DEVICE_COST_PER_BW
        );

        AppModuleAllocationPolicy allocationPolicy = new AppModuleAllocationPolicy(List.of(host));
        return new Components(characteristics, allocationPolicy);
    }

    public record Components(FogDeviceCharacteristics characteristics, AppModuleAllocationPolicy allocationPolicy) {
    }
}
