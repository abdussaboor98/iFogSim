package org.collabft.economy;

import org.collabft.model.ContainerModule;

import java.util.*;

/**
 * Tracks outstanding bid responses for a migration round.
 */
public class BidManager {
    private final Map<String, PendingBid> pending = new HashMap<>();

    public void startBid(ContainerModule container, Set<Integer> bidders) {
        pending.put(container.getContainerId(), new PendingBid(container, bidders));
    }

    public void registerResponse(ContainerModule container, BidResponse response) {
        PendingBid bid = pending.get(container.getContainerId());
        if (bid != null) {
            bid.responses.add(response);
            bid.pending.remove(response.getBidderId());
        }
    }

    public void registerResponse(BidResponse response) {
        PendingBid bid = pending.get(response.getContainerId());
        if (bid != null) {
            bid.responses.add(response);
            bid.pending.remove(response.getBidderId());
        }
    }

    public Optional<BidResponse> pickWinner(ContainerModule container) {
        PendingBid bid = pending.get(container.getContainerId());
        if (bid == null) {
            return Optional.empty();
        }
        return bid.responses.stream()
                .filter(BidResponse::isFeasible)
                .max(Comparator.comparingDouble(BidResponse::getScore));
    }

    public Optional<BidResponse> pickWinner(String containerId) {
        PendingBid bid = pending.get(containerId);
        if (bid == null) {
            return Optional.empty();
        }
        return bid.responses.stream()
                .filter(BidResponse::isFeasible)
                .max(Comparator.comparingDouble(BidResponse::getScore));
    }

    public ContainerModule getContainer(String containerId) {
        PendingBid bid = pending.get(containerId);
        return bid == null ? null : bid.container;
    }

    public void clear(String containerId) {
        pending.remove(containerId);
    }

    public Set<Integer> remaining(ContainerModule container) {
        PendingBid bid = pending.get(container.getContainerId());
        return bid == null ? Collections.emptySet() : new HashSet<>(bid.pending);
    }

    private static class PendingBid {
        private final ContainerModule container;
        private final Set<Integer> pending;
        private final List<BidResponse> responses = new ArrayList<>();

        PendingBid(ContainerModule container, Set<Integer> bidders) {
            this.container = container;
            this.pending = new HashSet<>(bidders);
        }
    }
}
