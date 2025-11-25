package org.collabft.economy;

import java.util.HashMap;
import java.util.Map;

/**
 * Extremely lightweight accounting helper to keep track of SLA/payment.
 */
public class TokenManager {
    private final Map<Integer, Double> balances = new HashMap<>();

    public void credit(int fogId, double amount) {
        balances.put(fogId, balances.getOrDefault(fogId, 0.0) + amount);
    }

    public void debit(int fogId, double amount) {
        balances.put(fogId, balances.getOrDefault(fogId, 0.0) - amount);
    }

    public double getBalance(int fogId) {
        return balances.getOrDefault(fogId, 0.0);
    }
}
