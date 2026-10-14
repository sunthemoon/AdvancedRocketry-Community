package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, frame-local nearest-first selection. No Level, block entity or player is cached. */
public final class ElevatorCapsuleClientState {
    public static final int MAX_ENDPOINTS = 64;
    private static final Comparator<Candidate> NEAREST = Comparator.comparingDouble(Candidate::distanceSquared)
            .thenComparingLong(Candidate::endpoint);
    private final Map<Long, Candidate> endpoints = new HashMap<>();

    public record Candidate(long endpoint, double distanceSquared, List<Entry> rides) {
        public Candidate {
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0
                    || rides.size() > ElevatorCapsuleCodec.MAX_RIDES) {
                throw new IllegalArgumentException("Invalid capsule candidate");
            }
            rides = List.copyOf(rides);
        }
    }

    public record Effect(long endpoint, Entry ride) { }

    public void offer(Candidate candidate) {
        if (candidate.rides().isEmpty()) {
            endpoints.remove(candidate.endpoint());
            return;
        }
        if (endpoints.size() == MAX_ENDPOINTS && !endpoints.containsKey(candidate.endpoint())) {
            Candidate farthest = endpoints.values().stream().max(NEAREST).orElseThrow();
            if (NEAREST.compare(candidate, farthest) >= 0) {
                return;
            }
            endpoints.remove(farthest.endpoint());
        }
        endpoints.put(candidate.endpoint(), candidate);
    }

    public List<Candidate> endpoints() {
        return endpoints.values().stream().sorted(NEAREST).toList();
    }

    public List<Effect> effects() {
        Set<Integer> riders = new HashSet<>();
        List<Effect> result = new ArrayList<>();
        for (Candidate candidate : endpoints()) {
            for (Entry ride : candidate.rides()) {
                if (ride.remainingTicks() > 0 && riders.add(ride.entityId())) {
                    result.add(new Effect(candidate.endpoint(), ride));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Frame end, cancel/commit replacement, logout, Level change and unload all discard candidates. */
    public void clear() {
        endpoints.clear();
    }
}
