package org.collabft.economy;

import org.collabft.model.ContainerModule;

import java.util.*;

/**
 * Tracks outstanding bid responses for a migration round.
 */
public class BidManager {
    private final Map<String, PendingBid> pending = new HashMap<>();
    private final Map<String, BidResponse> winners = new HashMap<>();

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
        return selectWinner(bid);
    }

    public Optional<BidResponse> pickWinner(String containerId) {
        PendingBid bid = pending.get(containerId);
        if (bid == null) {
            return Optional.empty();
        }
        return selectWinner(bid);
    }

    public List<BidResponse> getResponses(String containerId) {
        PendingBid bid = pending.get(containerId);
        return bid == null ? List.of() : new ArrayList<>(bid.responses);
    }

    private Optional<BidResponse> selectWinner(PendingBid bid) {
        return bid.responses.stream()
                .filter(BidResponse::isFeasible)
                .min(Comparator.<BidResponse>comparingDouble(BidResponse::getCost)
                        .thenComparing(Comparator.comparingDouble(BidResponse::getScore).reversed()));
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

    public boolean isComplete(String containerId) {
        PendingBid bid = pending.get(containerId);
        return bid != null && bid.pending.isEmpty();
    }

    public void recordWinner(String containerId, BidResponse response) {
        winners.put(containerId, response);
    }

    public BidResponse getWinner(String containerId) {
        return winners.get(containerId);
    }

    public boolean hasPending(String containerId) {
        return pending.containsKey(containerId);
    }

    public Set<Integer> bidders(String containerId) {
        PendingBid bid = pending.get(containerId);
        return bid == null ? Collections.emptySet() : new HashSet<>(bid.pending);
    }

    public void clearWinner(String containerId) {
        winners.remove(containerId);
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
