package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * ADR-074 item 7 and design section 5.2: the server-lifetime table of terminal actions whose dependent effect waits for
 * a registry acknowledgement. One entry per terminal (keyed by Level and position), one per satellite identity across
 * all terminals, and at most {@value #MAX_ENTRIES} in total, data-action tokens included. Every refusal happens before
 * any registry mutation. The table outlives a terminal's unload and reload, but it is RAM only: shutdown discards it
 * without effects, and after a restart ADR-049 section 6's replay applies. Owner (server) thread only.
 */
public final class TerminalActionTable {
    public static final int MAX_ENTRIES = 256, MAX_COMPLETIONS_PER_TICK = 32;

    /** LAUNCH and DECOMMISSION lock the package and chip slots; DATA locks nothing but blocks conflicting buttons. */
    public enum Kind { LAUNCH, DECOMMISSION, DATA }

    public record Key(ResourceKey<Level> level, BlockPos pos) {
        public Key {
            Objects.requireNonNull(level, "level");
            pos = Objects.requireNonNull(pos, "pos").immutable();
        }
    }

    /**
     * One waiting action. Its watch or token is fixed when the RAM change was made and is never replaced by a later
     * sequence; the RAM result is stored separately and shown once the change is covered.
     */
    public static final class Entry {
        private final Key key;
        private final Kind kind;
        private final SatelliteIdentity identity;
        private final SatelliteRegistryProof.Watch watch;
        private final SatelliteRegistryProof.Token token;
        private final SatelliteOperationCode result;
        private final UUID player;
        private boolean held;

        private Entry(Key key, Kind kind, SatelliteIdentity identity, SatelliteRegistryProof.Watch watch,
                      SatelliteRegistryProof.Token token, SatelliteOperationCode result, UUID player) {
            this.key = key; this.kind = kind; this.identity = identity; this.watch = watch; this.token = token;
            this.result = result; this.player = player;
        }

        public Key key() { return key; }
        public Kind kind() { return kind; }
        /** The package's identity for a launch, the chip's for a decommission or data action. */
        public SatelliteIdentity identity() { return identity; }
        public SatelliteOperationCode result() { return result; }
        public UUID player() { return player; }
        /** A mismatch was seen at completion: the entry stays, with its lock, until a matching effect completes. */
        public boolean held() { return held; }
        public boolean locksSlots() { return kind != Kind.DATA; }

        /** A published COMMITTED acknowledgement of this session covers the change; coverage alone releases nothing. */
        public boolean covered(SatelliteMissionSavedData registry) {
            return registry.operational() && (watch != null ? registry.proves(watch) : registry.covers(token));
        }
    }

    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Key> bySatellite = new HashMap<>();
    private int cursor;

    /** Whether an action of this satellite at this terminal may be admitted now; null when it may. */
    public SatelliteOperationCode admission(Key key, UUID satellite) {
        if (entries.containsKey(key)) { return SatelliteOperationCode.REGISTRY_PENDING; }
        if (satellite != null && bySatellite.containsKey(satellite)) { return SatelliteOperationCode.REGISTRY_BUSY; }
        return entries.size() >= MAX_ENTRIES ? SatelliteOperationCode.REGISTRY_BUSY : null;
    }

    public Entry launch(Key key, SatelliteIdentity identity, SatelliteRegistryProof.Watch watch,
                        SatelliteOperationCode result, UUID player) {
        return add(new Entry(key, Kind.LAUNCH, identity, Objects.requireNonNull(watch, "watch"), null, result, player));
    }

    public Entry decommission(Key key, SatelliteIdentity identity, SatelliteRegistryProof.Token token,
                              SatelliteOperationCode result, UUID player) {
        return add(new Entry(key, Kind.DECOMMISSION, identity, null, Objects.requireNonNull(token, "token"), result, player));
    }

    public Entry data(Key key, SatelliteIdentity identity, SatelliteRegistryProof.Token token,
                      SatelliteOperationCode result, UUID player) {
        return add(new Entry(key, Kind.DATA, identity, null, Objects.requireNonNull(token, "token"), result, player));
    }

    public Optional<Entry> at(Key key) { return Optional.ofNullable(entries.get(key)); }

    /** Whether the terminal's package and chip slots refuse insertion and extraction. */
    public boolean locked(Key key) {
        Entry entry = entries.get(key);
        return entry != null && entry.locksSlots();
    }

    public int size() { return entries.size(); }

    /** The entry completed its matching effect; only then is its terminal released. */
    public void completed(Entry entry) {
        if (entries.get(entry.key) == entry) {
            entries.remove(entry.key);
            bySatellite.remove(entry.identity.satelliteId(), entry.key);
        }
    }

    /** A completion found a mismatch; the entry keeps its lock. Returns true the first time, for one diagnostic. */
    public boolean hold(Entry entry) {
        boolean first = !entry.held;
        entry.held = true;
        return first;
    }

    /**
     * Entries to examine this tick, rotating so every entry is reached; at most {@value #MAX_COMPLETIONS_PER_TICK}
     * of them may complete per tick (the caller counts completions).
     */
    public List<Entry> rotation() {
        var all = new ArrayList<>(entries.values());
        if (all.isEmpty()) { return all; }
        cursor = Math.floorMod(cursor, all.size());
        var rotated = new ArrayList<Entry>(all.size());
        rotated.addAll(all.subList(cursor, all.size()));
        rotated.addAll(all.subList(0, cursor));
        cursor++;
        return rotated;
    }

    /** Shutdown, either path, or a server stop: every waiting effect is dropped without running. */
    public void discard() {
        entries.clear();
        bySatellite.clear();
        cursor = 0;
    }

    private Entry add(Entry entry) {
        SatelliteOperationCode refused = admission(entry.key, entry.identity.satelliteId());
        if (refused != null) { throw new IllegalStateException("Terminal action admitted without capacity: " + refused); }
        entries.put(entry.key, entry);
        bySatellite.put(entry.identity.satelliteId(), entry.key);
        return entry;
    }
}
