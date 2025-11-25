package org.collabft.agents;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;
import org.collabft.model.TaskProfile;
import org.collabft.util.FogDeviceFactory;
import org.collabft.util.FogDeviceFactory.Components;
import org.fog.entities.FogDevice;
import org.fog.utils.FogLinearPowerModel;

import java.util.ArrayList;
import java.util.Random;

/**
 * Generates tasks and offloads them to its mapped FogNodeController.
 */
public class EdgeDevice extends FogDevice {
    private final ContainerProfile profile;
    private final int tasksToSend;
    private final double meanInterArrival;
    private final Random random;
    private int sentCount = 0;

    public EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, int tasksToSend, double meanInterArrival, long seed) throws Exception {
        this(name, capacity, profile, tasksToSend, meanInterArrival, seed,
                FogDeviceFactory.build(capacity, new FogLinearPowerModel(40, 5)));
    }

    private EdgeDevice(String name, ResourceCapacity capacity, ContainerProfile profile, int tasksToSend, double meanInterArrival, long seed,
                       Components components) throws Exception {
        super(name,
                components.characteristics(),
                components.allocationPolicy(),
                new ArrayList<>(), 0,
                capacity.getUplinkBandwidth(),
                capacity.getDownlinkBandwidth(),
                0,
                capacity.getRatePerMips());
        this.profile = profile;
        this.tasksToSend = tasksToSend;
        this.meanInterArrival = meanInterArrival;
        this.random = new Random(seed);
    }

    @Override
    public void startEntity() {
        super.startEntity();
        scheduleNext(0);
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        // edges only emit tasks; no custom inbound events
    }

    private void scheduleNext(double delay) {
        if (sentCount >= tasksToSend) {
            return;
        }
        send(getParentId(), delay, CollabSimTags.TASK_ARRIVAL_EVENT, new TaskProfile(profile, CloudSim.clock() + delay));
        sentCount++;
        double nextDelay = exponential(meanInterArrival);
        scheduleNext(delay + nextDelay);
    }

    private double exponential(double mean) {
        double u = random.nextDouble();
        return -mean * Math.log(1 - u);
    }
}
