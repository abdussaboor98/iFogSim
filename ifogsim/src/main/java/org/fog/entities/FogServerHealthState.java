package org.fog.entities;

/**
 * Represents the high-level health of an internal fog server.
 */
public enum FogServerHealthState {
    HEALTHY,
    PREDICTED_FAIL,
    FAILED
}
