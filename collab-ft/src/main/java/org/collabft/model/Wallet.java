package org.collabft.model;

/**
 * Simple token wallet per fog node to track collaborative payments.
 */
public class Wallet {

    private int ownerFogId;
    private double initialBalance;
    private double balance;

    public Wallet() {
        // Bean constructor
    }

    public Wallet(int ownerFogId, double initialBalance) {
        this.ownerFogId = ownerFogId;
        this.initialBalance = initialBalance;
        this.balance = initialBalance;
    }

    public int getOwnerFogId() {
        return ownerFogId;
    }

    public void setOwnerFogId(int ownerFogId) {
        this.ownerFogId = ownerFogId;
    }

    public double getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(double initialBalance) {
        this.initialBalance = initialBalance;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void credit(double amount) {
        balance += amount;
    }

    public boolean debit(double amount) {
        if (amount < 0) {
            return false;
        }
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    public double delta() {
        return balance - initialBalance;
    }
}
