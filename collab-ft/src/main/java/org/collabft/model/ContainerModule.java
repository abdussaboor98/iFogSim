package org.collabft.model;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
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
    private double deadlineTime;
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
    private final double tExecSeconds;
    private final double tNetSeconds;
    private final double tSlackSeconds;
    private final double tMigSeconds;
    private String originatingEdge = "";
    private boolean slaSuccess;
    private double makespan = -1;
    private double completionTime = -1;
    private boolean initialPlacementStarted;

    public ContainerModule(String name, String appId, int userId, TaskProfile profile) {
        this(name, appId, userId, profile.getContainerProfile(), profile.getDeadlineTime(), profile.getArrivalTime(),
                profile.getTExecSeconds(), profile.getTNetSeconds(), profile.getTSlackSeconds(), profile.getTMigSeconds(),
                profile.getOriginatingEdge(), new TupleScheduler(profile.getContainerProfile().getDemandMips(), 1));
    }

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile, double deadlineSeconds, double arrivalTime,
                           double tExecSeconds, double tNetSeconds, double tSlackSeconds, double tMigSeconds,
                           String originatingEdge, CloudletScheduler scheduler) {
        super(FogUtils.generateEntityId(), name, appId, userId, profile.getDemandMips(), profile.getRamMb(), Math.round(profile.getBandwidth()),
                Math.round(profile.getContainerSizeMb()), "Xen", scheduler, Collections.emptyMap());
        this.profile = profile;
        this.deadlineTime = deadlineSeconds;
        this.remainingWorkMi = profile.getDemandMips() * profile.getRuntimeSeconds();
        this.arrivalTime = arrivalTime;
        this.tExecSeconds = tExecSeconds;
        this.tNetSeconds = tNetSeconds;
        this.tSlackSeconds = tSlackSeconds;
        this.tMigSeconds = tMigSeconds;
        this.originatingEdge = originatingEdge;
    }

    public ContainerModule(String name, String appId, int userId, ContainerProfile profile) {
        this(name, appId, userId, profile, profile.getDeadlineSeconds(), CloudSim.clock(), profile.getRuntimeSeconds(),
                0, 0, 0, "", new TupleScheduler(profile.getDemandMips(), 1));
    }

    public String getContainerId() {
        return containerId;
    }

    public ContainerProfile getProfile() {
        return profile;
    }

    public double getDeadlineSeconds() {
        return deadlineTime;
    }

    public void setDeadlineSeconds(double deadlineSeconds) {
        this.deadlineTime = deadlineSeconds;
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

    /**
     * Reset progress to restart task from beginning (used when server crashes and work is lost).
     */
    public void resetProgress() {
        double totalWorkMi = profile.getDemandMips() * profile.getRuntimeSeconds();
        remainingWorkMi = totalWorkMi;
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

    public boolean isInitialPlacementStarted() {
        return initialPlacementStarted;
    }

    public void startInitialPlacementWindow(double startTime) {
        if (initialPlacementStarted) {
            return;
        }
        double base = tExecSeconds + tNetSeconds + tSlackSeconds + tMigSeconds;
        this.arrivalTime = startTime;
        this.deadlineTime = startTime + base;
        initialPlacementStarted = true;
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

    public double getTExecSeconds() {
        return tExecSeconds;
    }

    public double getTNetSeconds() {
        return tNetSeconds;
    }

    public double getTSlackSeconds() {
        return tSlackSeconds;
    }

    public double getTMigSeconds() {
        return tMigSeconds;
    }

    public String getOriginatingEdge() {
        return originatingEdge;
    }

    public boolean isSlaSuccess() {
        return slaSuccess;
    }

    public void setSlaSuccess(boolean slaSuccess) {
        this.slaSuccess = slaSuccess;
    }

    public double getMakespan() {
        return makespan;
    }

    public void setMakespan(double makespan) {
        this.makespan = makespan;
    }

    public double getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(double completionTime) {
        this.completionTime = completionTime;
    }
}
