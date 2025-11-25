package org.collabft.events;

import org.cloudbus.cloudsim.core.CloudSimTags;

/**
 * Custom CloudSim tags used by the collaborative fault tolerance workflow.
 */
public enum CollabSimTags implements CloudSimTags {
    GOSSIP_EVENT,
    FAULT_EVENT,
    RECOVERY_EVENT,
    TASK_ARRIVAL_EVENT,
    MIGRATION_REQUEST,
    BID_REQUEST,
    BID_RESPONSE,
    BID_TIMEOUT,
    MIGRATION_START,
    MIGRATION_FINISH,
    PAYMENT_EVENT,
    GOSSIP_TICK
}
