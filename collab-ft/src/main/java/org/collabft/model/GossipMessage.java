package org.collabft.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Message used by GossipAgent to circulate fog state snapshots.
 */
public class GossipMessage {

    private int senderId;
    private int receiverId;
    private double timestamp;
    private long sequence;
    private final List<FogNodeState> stateTable = new ArrayList<>();

    public GossipMessage() {
        // Bean constructor
    }

    public GossipMessage(int senderId, int receiverId, double timestamp, List<FogNodeState> states) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.timestamp = timestamp;
        setStateTable(states);
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(int receiverId) {
        this.receiverId = receiverId;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public List<FogNodeState> getStateTable() {
        return Collections.unmodifiableList(stateTable);
    }

    public void setStateTable(List<FogNodeState> states) {
        stateTable.clear();
        if (states != null) {
            stateTable.addAll(states);
        }
    }

    public int entryCount() {
        return stateTable.size();
    }
}
