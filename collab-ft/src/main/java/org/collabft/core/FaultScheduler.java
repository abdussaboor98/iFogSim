package org.collabft.core;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.config.SimulationConfig;
import org.collabft.model.FaultEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Emits predicted fault events into the simulation timeline. Fault sampling,
 * fault hits, and recoveries will be fleshed out in later steps; this class
 * currently wires CloudSim scheduling and fan-out to decision agents.
 */
public class FaultScheduler extends SimEntity {

    private final SimulationConfig config;
    private final SimulationConfig.FaultConfig faultConfig;
    private final List<Integer> decisionAgentIds;

    public FaultScheduler(SimulationConfig config, List<Integer> decisionAgentIds) {
        super("fault-scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.faultConfig = Objects.requireNonNull(config.getFault(), "fault config");
        this.decisionAgentIds = new ArrayList<>(decisionAgentIds);
    }

    @Override
    public void startEntity() {
        scheduleNextSample(0.0);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == SimulationEvents.EVT_FAULT_PREDICTED) {
            handlePrediction(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_HIT) {
            handleFaultHit(ev.getData());
        } else if (ev.getTag() == SimulationEvents.EVT_FAULT_RECOVERED) {
            handleRecovery(ev.getData());
        }
    }

    private void handlePrediction(Object data) {
        if (data instanceof FaultEvent fault) {
            broadcast(fault.getPredictedAt(), SimulationEvents.EVT_FAULT_PREDICTED, fault);
            send(getId(), fault.timeUntilFault(fault.getPredictedAt()), SimulationEvents.EVT_FAULT_HIT, fault);
            send(getId(), fault.timeUntilRecover(fault.getPredictedAt()), SimulationEvents.EVT_FAULT_RECOVERED, fault);
        }
        scheduleNextSample(faultConfig.getPredictionLeadTimeSec());
    }

    private void handleFaultHit(Object data) {
        if (data instanceof FaultEvent fault) {
            broadcast(0.0, SimulationEvents.EVT_FAULT_HIT, fault);
        }
    }

    private void handleRecovery(Object data) {
        if (data instanceof FaultEvent fault) {
            broadcast(0.0, SimulationEvents.EVT_FAULT_RECOVERED, fault);
        }
    }

    private void broadcast(double delay, SimulationEvents tag, FaultEvent fault) {
        for (int decisionAgentId : decisionAgentIds) {
            send(decisionAgentId, delay, tag, fault);
        }
    }

    private void scheduleNextSample(double delay) {
        send(getId(), delay, SimulationEvents.EVT_FAULT_PREDICTED);
    }

    @Override
    public void shutdownEntity() {
        // No-op
    }
}
