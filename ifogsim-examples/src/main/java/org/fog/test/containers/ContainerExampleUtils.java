package org.fog.test.containers;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.provisioners.PeProvisioner;
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
 * Helper utilities shared by the container examples.
 */
final class ContainerExampleUtils {

    private ContainerExampleUtils() {
    }

    static FogDevice createFogDevice(
            String nodeName,
            long mips,
            int ram,
            long upBw,
            long downBw,
            int level,
            double ratePerMips,
            double busyPower,
            double idlePower) throws Exception {

        // Each fog device owns a single PowerHost configured with overbooking-friendly provisioners.
        List<Pe> peList = new ArrayList<>();
        PeProvisioner peProvisioner = new PeProvisionerOverbooking(mips);
        peList.add(new Pe(0, peProvisioner));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        Host host = new org.cloudbus.cloudsim.power.PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = Config.FOG_DEVICE_ARCH;
        String os = Config.FOG_DEVICE_OS;
        String vmm = Config.FOG_DEVICE_VMM;
        double timeZone = Config.FOG_DEVICE_TIMEZONE;
        double cost = Config.FOG_DEVICE_COST;
        double costPerMem = Config.FOG_DEVICE_COST_PER_MEMORY;
        double costPerStorage = Config.FOG_DEVICE_COST_PER_STORAGE;
        double costPerBw = Config.FOG_DEVICE_COST_PER_BW;
        LinkedList<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch,
                os,
                vmm,
                host,
                timeZone,
                cost,
                costPerMem,
                costPerStorage,
                costPerBw);

        // Wrap the host into a FogDevice so it can participate in the iFogSim control plane.
        FogDevice fogDevice = new FogDevice(
                nodeName,
                characteristics,
                new AppModuleAllocationPolicy(hostList),
                storageList,
                Config.RESOURCE_MGMT_INTERVAL,
                upBw,
                downBw,
                1,
                ratePerMips);
        fogDevice.setLevel(level);
        fogDevice.setServers(createDefaultServers(nodeName, mips, ram, storage, bw));
        return fogDevice;
    }

    private static List<FogServer> createDefaultServers(String nodeName, long totalMips, int totalRam, long totalStorage, long totalBw) {
        int serverCount;
        if (totalMips >= 8000) {
            serverCount = 4;
        } else if (totalMips >= 4000) {
            serverCount = 3;
        } else {
            serverCount = 2;
        }
        serverCount = Math.max(1, serverCount);

        List<FogServer> servers = new ArrayList<>();
        double mipsPerServer = (double) totalMips / serverCount;
        long ramPerServer = Math.max(512, totalRam / serverCount);
        long storagePerServer = Math.max(100000, totalStorage / serverCount);
        long bwPerServer = Math.max(1000, totalBw / serverCount);

        for (int i = 0; i < serverCount; i++) {
            String serverId = nodeName + "-srv-" + i;
            FogServer server = new FogServer(serverId, mipsPerServer, ramPerServer, storagePerServer, bwPerServer);
            servers.add(server);
        }
        return servers;
    }
}
