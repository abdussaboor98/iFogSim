package org.fog.test.servers;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.FogServer;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper utilities shared by the server-centric examples.
 */
final class ServerExampleUtils {

    private ServerExampleUtils() {
    }

    static FogDevice createFogDeviceWithServers(
            String nodeName,
            long hostMips,
            int hostRam,
            long upBw,
            long downBw,
            int level,
            double ratePerMips,
            double busyPower,
            double idlePower,
            List<FogServer> servers) throws Exception {

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(hostMips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        Host host = new org.cloudbus.cloudsim.power.PowerHost(
                hostId,
                new RamProvisionerSimple(hostRam),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                Config.FOG_DEVICE_ARCH,
                Config.FOG_DEVICE_OS,
                Config.FOG_DEVICE_VMM,
                host,
                Config.FOG_DEVICE_TIMEZONE,
                Config.FOG_DEVICE_COST,
                Config.FOG_DEVICE_COST_PER_MEMORY,
                Config.FOG_DEVICE_COST_PER_STORAGE,
                Config.FOG_DEVICE_COST_PER_BW);

        FogDevice device = new FogDevice(
                nodeName,
                characteristics,
                new AppModuleAllocationPolicy(hostList),
                new LinkedList<Storage>(),
                Config.RESOURCE_MGMT_INTERVAL,
                upBw,
                downBw,
                1,
                ratePerMips);
        device.setLevel(level);
        if (servers != null && !servers.isEmpty()) {
            device.setServers(servers);
        }
        return device;
    }

    static FogServer createServer(String id, double totalCpuMips, long totalRam, long totalStorage, long totalBandwidth) {
        return new FogServer(id, totalCpuMips, totalRam, totalStorage, totalBandwidth);
    }
}
