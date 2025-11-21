package org.fog.test.containers;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.container.ContainerInstance;
import org.fog.entities.container.ContainerMigrationManager;
import org.fog.entities.container.ContainerMigrationRequest;
import org.fog.placement.Controller;
import org.fog.utils.FogUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Minimal example showing how to run containers directly on fog devices and inside VMs.
 */
public class ContainerExecutionExample {

    public static void main(String[] args) {
        try {
            // Initialize the CloudSim core with a single user and no tracing.
            CloudSim.init(1, Calendar.getInstance(), false);

            List<FogDevice> fogDevices = new ArrayList<>();
            // Build a lightweight edge device and a more powerful cloud tier.
            FogDevice edge = ContainerExampleUtils.createFogDevice("edge-device", 4000, 4096, 10000, 1000000, 1, 0.01, 107.0, 83.0);
            FogDevice cloud = ContainerExampleUtils.createFogDevice("cloud", 8000, 16384, 20000, 2000000, 0, 0.005, 200.0, 150.0);
            fogDevices.add(edge);
            fogDevices.add(cloud);

            // Controller schedules the standard resource management events.
            new Controller("container-controller", fogDevices, new ArrayList<>(), new ArrayList<>());

            // Container running directly on the fog device.
            ContainerInstance edgeContainer = new ContainerInstance(
                    "edge-container",
                    1000,
                    512,
                    500,
                    250,
                    200,
                    50,
                    128);
            edge.allocateContainer(edgeContainer);

            // Create a VM so we can also demonstrate nested container execution inside virtualized resources.
            Vm vm = new Vm(
                    FogUtils.generateEntityId(),
                    1,
                    2000,
                    1,
                    1024,
                    1000,
                    10000,
                    "Xen",
                    new CloudletSchedulerTimeShared());
            cloud.getVmAllocationPolicy().allocateHostForVm(vm);

            ContainerInstance vmContainer = new ContainerInstance(
                    "vm-container",
                    1500,
                    256,
                    300,
                    200,
                    150,
                    30,
                    64);
            vm.allocateContainer(vmContainer);

            // Schedule a migration from the fog edge to the cloud to show the generic mechanism.
            ContainerMigrationManager migrationManager = new ContainerMigrationManager("container-migration-manager");
            migrationManager.scheduleMigration(
                    new ContainerMigrationRequest(edgeContainer, edge, cloud, 5, 5000),
                    150);

            // Start/stop the simulation clock and inspect the resulting states.
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("Edge container state: " + edgeContainer.getContainerState());
            System.out.println("VM container state: " + vmContainer.getContainerState());
        } catch (Exception e) {
            throw new RuntimeException("ContainerExecutionExample failed", e);
        }
    }

}
