package org.collabft.core;

import org.collabft.config.SimulationConfig;

import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper that builds the in-simulation fault scheduler based on config.
 */
public class FaultPredictor {

    private final SimulationConfig config;

    public FaultPredictor(SimulationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public FaultScheduler buildScheduler(List<Integer> decisionAgentIds) {
        return new FaultScheduler(config, decisionAgentIds);
    }
}
