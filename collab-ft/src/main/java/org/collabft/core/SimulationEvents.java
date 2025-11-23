package org.collabft.core;

import org.cloudbus.cloudsim.core.CloudSimTags;

/**
 * Central event tags used across collab-ft agents.
 */
public enum SimulationEvents implements CloudSimTags {
    EVT_GOSSIP_TICK,
    EVT_GOSSIP_VIEW,
    EVT_FAULT_PREDICTED,
    EVT_FAULT_HIT,
    EVT_FAULT_RECOVERED,
    EVT_CONTAINER_ARRIVAL,
    EVT_BID_REQUEST,
    EVT_BID_RESPONSE,
    EVT_PAYMENT,
    EVT_MIGRATION_COMPLETE,
    EVT_GOSSIP_MESSAGE
}
