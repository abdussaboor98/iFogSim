package org.collabft.core;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.Pe;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoLocation;
import org.fog.utils.distribution.DeterministicDistribution;
import org.collabft.config.SimulationConfig;
import org.collabft.model.FogNodeState;
import org.collabft.model.ServerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds iFogSim entities from the YAML configuration so the rest of the
 * collab-ft logic can schedule events against CloudSim.
 */
public class IfogBuilder {

    private final SimulationConfig config;

    public IfogBuilder(SimulationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Deployment build() {
        SimulationConfig.TopologyConfig topo = Objects.requireNonNull(config.getTopology(), "topology");
        if (topo.getFogNodeCount() <= 0) {
            throw new IllegalArgumentException("At least one fog node is required");
        }
        FogDevice cloud = createCloudDevice(topo);

        List<FogDevice> fogDevices = new ArrayList<>();
        Map<Integer, List<ServerState>> serverStates = new HashMap<>();
        Map<Integer, FogNodeState> fogStates = new HashMap<>();
        List<Integer> childIds = new ArrayList<>();

        for (int i = 0; i < topo.getFogNodeCount(); i++) {
            FogDevice fog = createFogDevice("fog-" + i, topo, cloud.getId());
            fogDevices.add(fog);
            childIds.add(fog.getId());

            List<ServerState> servers = createServerStates(i, topo);
            serverStates.put(i, servers);
            fogStates.put(i, buildFogNodeState(i, servers));
        }
        cloud.setChildrenIds(childIds);

        Application app = Application.createApplication("collabft-app", 0);
        app.addAppModule("sink", 10);

        List<Sensor> sensors = new ArrayList<>();
        List<Actuator> actuators = new ArrayList<>();
        buildEdgeEndpoints(topo, fogDevices, app, sensors, actuators);

        return new Deployment(cloud, fogDevices, sensors, actuators, app, fogStates, serverStates);
    }

    private FogDevice createCloudDevice(SimulationConfig.TopologyConfig topo) {
        double multiplier = topo.getCloudCapacityMultiplier();
        double cpu = topo.getServerCpu() * topo.getServersPerFog() * multiplier;
        double mem = topo.getServerMem() * topo.getServersPerFog() * multiplier;
        double bw = topo.getServerBw() * multiplier;
        FogDevice cloud = createDevice("cloud", cpu, mem, bw, bw, 0, 0.001, 50.0);
        cloud.setParentId(-1);
        return cloud;
    }

    private FogDevice createFogDevice(String name, SimulationConfig.TopologyConfig topo, int cloudId) {
        double cpu = topo.getServerCpu() * topo.getServersPerFog();
        double mem = topo.getServerMem() * topo.getServersPerFog();
        double bw = topo.getServerBw();
        FogDevice fog = createDevice(name, cpu, mem, bw, bw, 1, 0.01, 5.0);
        fog.setParentId(cloudId);
        return fog;
    }

    private FogDevice createDevice(String name, double cpu, double mem, double uplinkBw, double downlinkBw,
                                   int level, double ratePerMips, double uplinkLatency) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking((int) Math.round(cpu))));

        int hostId = FogUtils.generateEntityId();
        long storage = 1_000_000L;
        long bw = (long) Math.round(downlinkBw);

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple((int) Math.round(mem)),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(107.339, 83.4333)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        List<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, timeZone, cost, costPerMem, costPerStorage, costPerBw);

        try {
            FogDevice fogDevice = new FogDevice(
                    name,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    storageList,
                    10.0,
                    uplinkBw,
                    downlinkBw,
                    uplinkLatency,
                    ratePerMips
            );
            fogDevice.setLevel(level);
            return fogDevice;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create fog device " + name, e);
        }
    }

    private List<ServerState> createServerStates(int fogNodeId, SimulationConfig.TopologyConfig topo) {
        List<ServerState> servers = new ArrayList<>();
        for (int s = 0; s < topo.getServersPerFog(); s++) {
            ServerState server = new ServerState(fogNodeId, s, topo.getServerCpu(), topo.getServerMem(), topo.getServerBw());
            servers.add(server);
        }
        return servers;
    }

    private FogNodeState buildFogNodeState(int fogId, List<ServerState> servers) {
        FogNodeState state = new FogNodeState(fogId);
        double cpuTotal = 0.0;
        double memTotal = 0.0;
        double bwTotal = 0.0;
        for (ServerState server : servers) {
            cpuTotal += server.getCpuTotal();
            memTotal += server.getMemTotal();
            bwTotal += server.getBwTotal();
        }
        state.setCpuTotal(cpuTotal);
        state.setMemTotal(memTotal);
        state.setBwTotal(bwTotal);
        state.setActiveServers(servers.size());
        state.setTimestamp(0.0);
        state.setServers(servers);
        return state;
    }

    private void buildEdgeEndpoints(SimulationConfig.TopologyConfig topo, List<FogDevice> fogDevices, Application app,
                                    List<Sensor> sensors, List<Actuator> actuators) {
        if (topo.getEdgeDevices() == null) {
            return;
        }
        for (SimulationConfig.EdgeDeviceConfig edge : topo.getEdgeDevices()) {
            int fogIndex = edge.getFogNodeId();
            if (fogIndex < 0 || fogIndex >= fogDevices.size()) {
                throw new IllegalArgumentException("Edge device " + edge.getId() + " references fog node " + fogIndex
                        + " which is outside available range 0.." + (fogDevices.size() - 1));
            }
            FogDevice parentFog = fogDevices.get(fogIndex);
            Sensor sensor = new PassiveSensor("sensor-" + edge.getId(), app.getUserId(), app.getAppId(), parentFog.getId());
            sensor.setApp(app);
            sensors.add(sensor);

            Actuator actuator = new PassiveActuator("actuator-" + edge.getId(), app.getUserId(), app.getAppId(), parentFog.getId());
            actuator.setApp(app);
            actuators.add(actuator);
        }
    }

    public static final class Deployment {
        private final FogDevice cloud;
        private final List<FogDevice> fogDevices;
        private final List<Sensor> sensors;
        private final List<Actuator> actuators;
        private final Application application;
        private final Map<Integer, FogNodeState> fogStates;
        private final Map<Integer, List<ServerState>> serverStates;

        public Deployment(FogDevice cloud,
                          List<FogDevice> fogDevices,
                          List<Sensor> sensors,
                          List<Actuator> actuators,
                          Application application,
                          Map<Integer, FogNodeState> fogStates,
                          Map<Integer, List<ServerState>> serverStates) {
            this.cloud = cloud;
            this.fogDevices = Collections.unmodifiableList(new ArrayList<>(fogDevices));
            this.sensors = Collections.unmodifiableList(new ArrayList<>(sensors));
            this.actuators = Collections.unmodifiableList(new ArrayList<>(actuators));
            this.application = application;
            this.fogStates = Collections.unmodifiableMap(new HashMap<>(fogStates));
            this.serverStates = Collections.unmodifiableMap(new HashMap<>(serverStates));
        }

        public FogDevice getCloud() {
            return cloud;
        }

        public List<FogDevice> getFogDevices() {
            return fogDevices;
        }

        public List<Sensor> getSensors() {
            return sensors;
        }

        public List<Actuator> getActuators() {
            return actuators;
        }

        public Application getApplication() {
            return application;
        }

        public Map<Integer, FogNodeState> getFogStates() {
            return fogStates;
        }

        public Map<Integer, List<ServerState>> getServerStates() {
            return serverStates;
        }
    }

    private static final class PassiveSensor extends Sensor {
        PassiveSensor(String name, int userId, String appId, int gatewayDeviceId) {
            super(name, userId, appId, gatewayDeviceId, 0.0, new GeoLocation(0, 0),
                    new DeterministicDistribution(Double.MAX_VALUE), 1, 1, "noop", "sink");
        }

        @Override
        public void startEntity() {
            // Passive placeholder; no tuple emission.
        }

        @Override
        public void processEvent(SimEvent ev) {
            // Passive placeholder; ignore events.
        }
    }

    private static final class PassiveActuator extends Actuator {
        PassiveActuator(String name, int userId, String appId, int gatewayDeviceId) {
            super(name, userId, appId, "collabft-actuator");
            setGatewayDeviceId(gatewayDeviceId);
            setLatency(0.0);
        }

        @Override
        public void startEntity() {
            // Passive placeholder; no tuple handling until workload wiring is added.
        }

        @Override
        public void processEvent(SimEvent ev) {
            // Passive placeholder; ignore events.
        }
    }
}
