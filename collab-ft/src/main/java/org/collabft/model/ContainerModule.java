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
    private double remainingWorkMi;
    private double lastStartTime = -1;
    private double expectedFinishTime = -1;
    private double lastHostShareMips = 0;
    private int runVersion = 0;
    private String hostName;
    private String ownerFog;
    private int ownerId = -1;
    private double arrivalTime;
    private double migrationStart;
    private String migrationTrigger = "";
    private boolean paused;
    private int lastBidderId = -1;
    private double lastBidCost = 0.0;

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile) {
        this(name, appId, userId, profile, profile.getDeadlineSeconds(), new TupleScheduler(profile.getCpuMips(), 1));
    }

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile, double deadlineSeconds, CloudletScheduler scheduler) {
        super(FogUtils.generateEntityId(), name, appId, userId, profile.getCpuMips(), profile.getRamMb(), Math.round(profile.getBandwidth()),
                Math.round(profile.getContainerSizeMb()), "Xen", scheduler, Collections.emptyMap());
        this.profile = profile;
        this.deadlineSeconds = deadlineSeconds;
        this.remainingWorkMi = profile.getCpuMips();
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

    public double getRemainingWorkMi() {
        return remainingWorkMi;
    }

    public int getRunVersion() {
        return runVersion;
    }

    /**
     * Record that execution has (re)started on a host so we can credit progress or ignore stale completions.
     */
    public void startRun(double startTime, double hostShareMips, double durationSeconds) {
        this.runVersion++;
        this.lastStartTime = startTime;
        this.expectedFinishTime = startTime + durationSeconds;
        this.lastHostShareMips = hostShareMips;
    }

    /**
     * Apply progress up to the given time using the last known host share, updating remaining work.
     */
    public void checkpointProgress(double now) {
        if (remainingWorkMi <= 0 || lastStartTime < 0 || lastHostShareMips <= 0) {
            return;
        }
        double elapsed = Math.max(0, Math.min(now, expectedFinishTime) - lastStartTime);
        if (elapsed <= 0) {
            return;
        }
        double completedMi = elapsed * lastHostShareMips;
        remainingWorkMi = Math.max(0, remainingWorkMi - completedMi);
        lastStartTime = now;
    }

    public void markCompleted() {
        remainingWorkMi = 0;
        lastHostShareMips = 0;
        expectedFinishTime = -1;
        lastStartTime = -1;
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

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
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

    public void recordLastBid(int bidderId, double bidCost) {
        this.lastBidderId = bidderId;
        this.lastBidCost = bidCost;
    }

    public int getLastBidderId() {
        return lastBidderId;
    }

    public double getLastBidCost() {
        return lastBidCost;
    }
}
