package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Union of durable and in-flight obligations; never treats a pending P2 as acknowledged. */
public final class RocketRejoinIndex {
    private final Set<UUID> players = new HashSet<>();
    private final List<RocketRejoinRoot> roots;
    private final List<RocketRejoinRoot.Entry> admissions;
    private boolean overflow;

    public RocketRejoinIndex(Collection<RocketRejoinRoot> roots, Collection<RocketRejoinRoot.Entry> admissions) {
        this.roots = List.copyOf(roots); this.admissions = List.copyOf(admissions);
        this.roots.forEach(r -> r.entries().forEach(this::add)); this.admissions.forEach(this::add);
    }
    private void add(RocketRejoinRoot.Entry entry) {
        if (entry.placement() != RocketRejoinRoot.Placement.PENDING || players.contains(entry.player())) { return; }
        if (players.size() == RocketRejoinRoot.MAX_ENTRIES) { overflow = true; }
        else { players.add(entry.player()); }
    }
    public boolean contains(UUID player) {
        return players.contains(player) || overflow && (admissions.stream().anyMatch(e -> names(e, player))
                || roots.stream().anyMatch(r -> r.entries().stream().anyMatch(e -> names(e, player))));
    }
    private static boolean names(RocketRejoinRoot.Entry e, UUID player) {
        return e.player().equals(player) && e.placement() == RocketRejoinRoot.Placement.PENDING;
    }
    public int size() { return players.size(); }
    public boolean overflow() { return overflow; }
}
