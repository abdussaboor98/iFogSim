package org.collabft.model;

/**
 * Records the outcome of a migration and its payment settlement.
 */
public class PaymentReport {

    private String containerId;
    private int payerFogId;
    private int payeeFogId;
    private double slaValue;
    private double payment;
    private boolean violated;
    private double reliabilityComponent;
    private double urgencyComponent;
    private double timestamp;

    public PaymentReport() {
        // Bean constructor
    }

    public PaymentReport(String containerId, int payerFogId, int payeeFogId, double slaValue, double payment,
                         boolean violated, double reliabilityComponent, double urgencyComponent, double timestamp) {
        this.containerId = containerId;
        this.payerFogId = payerFogId;
        this.payeeFogId = payeeFogId;
        this.slaValue = slaValue;
        this.payment = payment;
        this.violated = violated;
        this.reliabilityComponent = reliabilityComponent;
        this.urgencyComponent = urgencyComponent;
        this.timestamp = timestamp;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public int getPayerFogId() {
        return payerFogId;
    }

    public void setPayerFogId(int payerFogId) {
        this.payerFogId = payerFogId;
    }

    public int getPayeeFogId() {
        return payeeFogId;
    }

    public void setPayeeFogId(int payeeFogId) {
        this.payeeFogId = payeeFogId;
    }

    public double getSlaValue() {
        return slaValue;
    }

    public void setSlaValue(double slaValue) {
        this.slaValue = slaValue;
    }

    public double getPayment() {
        return payment;
    }

    public void setPayment(double payment) {
        this.payment = payment;
    }

    public boolean isViolated() {
        return violated;
    }

    public void setViolated(boolean violated) {
        this.violated = violated;
    }

    public double getReliabilityComponent() {
        return reliabilityComponent;
    }

    public void setReliabilityComponent(double reliabilityComponent) {
        this.reliabilityComponent = reliabilityComponent;
    }

    public double getUrgencyComponent() {
        return urgencyComponent;
    }

    public void setUrgencyComponent(double urgencyComponent) {
        this.urgencyComponent = urgencyComponent;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isReward() {
        return !violated && payment > 0;
    }
}
