package org.fog.entities.container;

import java.util.List;

/**
 * Unified abstraction for any entity that can execute {@link ContainerInstance} objects.
 * Both fog devices and virtual machines implement this interface so that containers
 * can be scheduled and migrated with the same logic regardless of their placement.
 */
public interface ContainerHost {

    String getHostName();

    double getTotalCpuMips();

    double getAvailableCpuMips();

    long getTotalRam();

    long getAvailableRam();

    long getTotalBw();

    long getAvailableBw();

    long getTotalStorage();

    long getAvailableStorage();

    List<ContainerInstance> getContainers();

    boolean canHost(ContainerInstance container);

    boolean allocateContainer(ContainerInstance container);

    void deallocateContainer(ContainerInstance container);

    void pauseContainer(ContainerInstance container);

    void resumeContainer(ContainerInstance container);

    /**
     * Called every simulation tick to advance the execution of the hosted containers.
     */
    void updateContainers(double currentTime);
}
