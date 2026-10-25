package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owner-thread capacity; actual region tickets are removed with their session. */
final class RocketRejoinTicketBudget {
    private final Map<UUID, Set<String>> held = new HashMap<>();
    boolean acquire(UUID player, String slot) {
        var own = held.get(player);
        if (own != null && own.contains(slot)) { return true; }
        if (size() >= 16 || own != null && own.size() >= 2) { return false; }
        held.computeIfAbsent(player, ignored -> new HashSet<>()).add(slot); return true;
    }
    int size() { return held.values().stream().mapToInt(Set::size).sum(); }
    void release(UUID player) { held.remove(player); }
}
