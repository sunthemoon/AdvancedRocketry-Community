package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * ADR-066 section 3.3: the reverse owner/member -> station-ID index and the all-station ID index for operators.
 * It belongs to one {@link StationRegistryModel}, which updates it inside its own committed create, team and remove
 * mutations and rebuilds it from the decoded records at load; a query never rebuilds it. Each station's indexed team
 * is kept here, so a removal or an update never depends on the caller's copy. Lookups walk a stable cursor in UUID
 * order. The generation rises on every link change, so a query can tell that its candidate set moved. Not
 * synchronized: the owning model's lock guards it.
 */
public final class StationAccessIndex {
    /** One owner link and at most {@link StationLimits#MAX_MEMBERS} member links per station. */
    public static final int MAX_LINKS_PER_STATION = StationLimits.MAX_MEMBERS + 1;
    public static final int MAX_LINKS = StationLimits.MAX_STATIONS * MAX_LINKS_PER_STATION;

    private final TreeMap<UUID, Set<UUID>> teams = new TreeMap<>();
    private final Map<UUID, TreeSet<UUID>> byPlayer = new HashMap<>();
    private int links;
    private long generation;

    /** Indexes a committed station; refuses a duplicate or a set beyond the fixed bounds. */
    void add(StationState station) {
        Objects.requireNonNull(station, "station");
        if (teams.containsKey(station.stationId())) {
            throw new IllegalArgumentException("Station is already indexed");
        }
        if (teams.size() >= StationLimits.MAX_STATIONS) {
            throw new IllegalStateException("Station access index exceeds the station bound");
        }
        Set<UUID> team = team(station);
        if (links + team.size() > MAX_LINKS) {
            throw new IllegalStateException("Station access index exceeds the link bound");
        }
        teams.put(station.stationId(), team);
        team.forEach(player -> link(player, station.stationId()));
        generation++;
    }

    /** Forgets a station and all its links; nothing happens for an unknown one. */
    void remove(UUID stationId) {
        Objects.requireNonNull(stationId, "stationId");
        Set<UUID> team = teams.remove(stationId);
        if (team == null) {
            return;
        }
        team.forEach(player -> unlink(player, stationId));
        generation++;
    }

    /** Moves an indexed station's links to its updated team; an unchanged team or unknown station changes nothing. */
    void update(StationState station) {
        Objects.requireNonNull(station, "station");
        Set<UUID> before = teams.get(station.stationId());
        Set<UUID> after = team(station);
        if (before == null || before.equals(after)) {
            return;
        }
        if (links - before.size() + after.size() > MAX_LINKS) {
            throw new IllegalStateException("Station access index exceeds the link bound");
        }
        before.stream().filter(player -> !after.contains(player))
                .forEach(player -> unlink(player, station.stationId()));
        after.stream().filter(player -> !before.contains(player))
                .forEach(player -> link(player, station.stationId()));
        teams.put(station.stationId(), after);
        generation++;
    }

    /**
     * The next candidate after {@code after} in UUID order (the first when it is null): every committed station for an
     * operator, otherwise the stations this player owns or belongs to.
     */
    public Optional<UUID> next(UUID player, boolean operator, UUID after) {
        Objects.requireNonNull(player, "player");
        NavigableSet<UUID> candidates = operator ? teams.navigableKeySet() : byPlayer.get(player);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(after == null ? candidates.first() : candidates.higher(after));
    }

    /** The size of one player's candidate set. */
    public int candidates(UUID player, boolean operator) {
        Objects.requireNonNull(player, "player");
        if (operator) {
            return teams.size();
        }
        TreeSet<UUID> candidates = byPlayer.get(player);
        return candidates == null ? 0 : candidates.size();
    }

    public long generation() {
        return generation;
    }

    public int stations() {
        return teams.size();
    }

    public int links() {
        return links;
    }

    /** Players with at least one link; empty sets are never kept. */
    public int players() {
        return byPlayer.size();
    }

    private void link(UUID player, UUID station) {
        if (byPlayer.computeIfAbsent(player, ignored -> new TreeSet<>()).add(station)) {
            links++;
        }
    }

    private void unlink(UUID player, UUID station) {
        TreeSet<UUID> owned = byPlayer.get(player);
        if (owned != null && owned.remove(station)) {
            links--;
            if (owned.isEmpty()) {
                byPlayer.remove(player);
            }
        }
    }

    /** The owner and the members; a station state never holds more than 32 members. */
    private static Set<UUID> team(StationState station) {
        TreeSet<UUID> team = new TreeSet<>(station.members());
        team.add(station.ownerId());
        if (team.size() > MAX_LINKS_PER_STATION) {
            throw new IllegalStateException("Station access index exceeds the link bound");
        }
        return Set.copyOf(team);
    }
}
