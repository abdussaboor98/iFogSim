package org.fog.test.containers;

import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.container.ContainerHost;
import org.fog.entities.container.ContainerInstance;
import org.fog.entities.container.ContainerMigrationManager;
import org.fog.entities.container.ContainerMigrationRequest;
import org.fog.placement.Controller;
import org.fog.utils.FogUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Demonstrates container migration from a VM running at the edge to a cloud device.
 */
public class ContainerMigrationExample {

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            FogDevice edge = ContainerExampleUtils.createFogDevice("mobile-edge", 3000, 2048, 5000, 5000, 2, 0.02, 90, 70);
            FogDevice cloud = ContainerExampleUtils.createFogDevice("cloud", 10000, 24576, 20000, 20000, 0, 0.005, 250, 180);

            List<FogDevice> devices = new ArrayList<>();
            devices.add(edge);
            devices.add(cloud);
            new Controller("migration-controller", devices, new ArrayList<>(), new ArrayList<>());

            Vm edgeVm = new Vm(
                    FogUtils.generateEntityId(),
                    2,
                    1500,
                    1,
                    512,
                    500,
                    1000,
                    "Xen",
                    new CloudletSchedulerSpaceShared());
            edge.getVmAllocationPolicy().allocateHostForVm(edgeVm);

            ContainerInstance appContainer = new ContainerInstance(
                    "sensor-analytics",
                    1200,
                    256,
                    200,
                    150,
                    300,
                    40,
                    96);
            edgeVm.allocateContainer(appContainer);

            ContainerMigrationManager migrationManager = new ContainerMigrationManager("vm-to-cloud-migration");
            ContainerMigrationRequest request = new ContainerMigrationRequest(appContainer, edgeVm, cloud, 10, 4000);
            migrationManager.scheduleMigration(request, 200);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            ContainerHost finalHost = appContainer.getCurrentHost();
            System.out.println("Container final host: " + (finalHost != null ? finalHost.getHostName() : "none"));
            System.out.println("Container progress: " + appContainer.getProgress());
        } catch (Exception e) {
            throw new RuntimeException("ContainerMigrationExample failed", e);
        }
    }

}
