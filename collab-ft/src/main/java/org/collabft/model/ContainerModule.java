package org.collabft.model;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.fog.application.AppModule;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;

import java.util.Collections;
import java.util.UUID;

/**
 * Lightweight container abstraction that keeps resource metadata alongside the AppModule.
 */
public class ContainerModule extends AppModule {
    private final String containerId = UUID.randomUUID().toString();
    private final ContainerProfile profile;
    private final double deadlineSeconds;
    private String hostName;
    private String ownerFog;
    private double arrivalTime;
    private double migrationStart;
    private String migrationTrigger = "";
    private boolean paused;

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile) {
        this(name, appId, userId, profile, profile.getDeadlineSeconds(), new TupleScheduler(profile.getCpuMips(), 1));
    }

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile, double deadlineSeconds, CloudletScheduler scheduler) {
        super(FogUtils.generateEntityId(), name, appId, userId, profile.getCpuMips(), profile.getRamMb(), Math.round(profile.getBandwidth()),
                Math.round(profile.getContainerSizeMb()), "Xen", scheduler, Collections.emptyMap());
        this.profile = profile;
        this.deadlineSeconds = deadlineSeconds;
    }

    public String getContainerId() {
        return containerId;
    }

    public ContainerProfile getProfile() {
        return profile;
    }

    public double getDeadlineSeconds() {
        return deadlineSeconds;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getOwnerFog() {
        return ownerFog;
    }

    public void setOwnerFog(String ownerFog) {
        this.ownerFog = ownerFog;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(double arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public double getMigrationStart() {
        return migrationStart;
    }

    public void setMigrationStart(double migrationStart) {
        this.migrationStart = migrationStart;
    }

    public String getMigrationTrigger() {
        return migrationTrigger;
    }

    public void setMigrationTrigger(String migrationTrigger) {
        this.migrationTrigger = migrationTrigger;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
