package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded transient identities, not authority. Ambiguity or overflow disables fallback until resolved/reloaded. */
public final class LoadedRocketIdentities {
    public static final int MAX_ENTITIES = 1024;
    private final Map<UUID, UUID> physical = new HashMap<>();
    private final Map<UUID, Set<UUID>> logical = new HashMap<>();
    private boolean overflow;

    public void joined(UUID identity, UUID assembly) {
        var previous = physical.get(identity);
        if (assembly.equals(previous)) { return; }
        if (previous != null) { left(identity); }
        if (physical.size() == MAX_ENTITIES) { overflow = true; return; }
        physical.put(identity, assembly);
        logical.computeIfAbsent(assembly, ignored -> new HashSet<>()).add(identity);
    }

    public void left(UUID identity) {
        var assembly = physical.remove(identity);
        if (assembly == null) { return; }
        var copies = logical.get(assembly);
        copies.remove(identity);
        if (copies.isEmpty()) { logical.remove(assembly); }
    }

    public UUID unique(UUID assembly) {
        var copies = logical.get(assembly);
        return !overflow && copies != null && copies.size() == 1 ? copies.iterator().next() : null;
    }

    public boolean ambiguous(UUID assembly) {
        var copies = logical.get(assembly);
        return overflow || copies != null && copies.size() > 1;
    }

    public int size() { return physical.size(); }
    public void clear() { physical.clear(); logical.clear(); overflow = false; }
}
