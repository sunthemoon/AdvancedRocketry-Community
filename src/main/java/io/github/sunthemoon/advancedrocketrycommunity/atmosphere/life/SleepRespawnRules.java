package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * ADR-066 section 3.3 pure decisions for sleep on a non-Overworld mapped surface or in Space, and for the two
 * planet-respawn options. The Forge adapter supplies facts read from already-loaded state; nothing here reads a world.
 */
public final class SleepRespawnRules {
    private SleepRespawnRules() {
    }

    /** Station authority over one cell of the shared Space Level (owner decision 05). */
    public enum StationAccess {
        /** Not in Space: no station rule applies. */
        NOT_APPLICABLE,
        /** In Space, outside every committed station region. */
        OUTSIDE,
        /** Inside a committed station region, with live VISIT permission. */
        VISIT,
        /** Inside a committed station region, without VISIT permission. */
        DENIED,
        /** The registry is unavailable or blocked, so it cannot show that a cell is outside every station. */
        UNAVAILABLE
    }

    /** One cell the sleeper occupies; an unloaded cell carries no air or station fact. */
    public record Cell(boolean loaded, BreathabilityState air, StationAccess station) {
        public Cell {
            Objects.requireNonNull(air, "air");
            Objects.requireNonNull(station, "station");
        }

        public static Cell unloaded() {
            return new Cell(false, BreathabilityState.VACUUM, StationAccess.UNAVAILABLE);
        }
    }

    /** Why sleep is refused; {@link #NONE} admits the sleeper to the vanilla bed rules. */
    public enum SleepRefusal {
        NONE,
        UNAVAILABLE,
        STATION_UNAVAILABLE,
        STATION_DENIED,
        PENDING,
        UNBREATHABLE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Sleep needs every occupied cell loaded, permitted and known breathable. The first refusal in declaration order
     * across all cells wins, so the reason does not depend on the order of the cells.
     */
    public static SleepRefusal admitSleep(List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Sleep admission needs at least one occupied cell");
        }
        SleepRefusal worst = SleepRefusal.NONE;
        for (Cell cell : cells) {
            SleepRefusal refusal = refusal(Objects.requireNonNull(cell, "cell"));
            if (refusal != SleepRefusal.NONE && (worst == SleepRefusal.NONE || refusal.ordinal() < worst.ordinal())) {
                worst = refusal;
            }
        }
        return worst;
    }

    private static SleepRefusal refusal(Cell cell) {
        if (!cell.loaded()) {
            return SleepRefusal.UNAVAILABLE;
        }
        if (cell.station() == StationAccess.UNAVAILABLE) {
            return SleepRefusal.STATION_UNAVAILABLE;
        }
        if (cell.station() == StationAccess.DENIED) {
            return SleepRefusal.STATION_DENIED;
        }
        return switch (cell.air()) {
            case BREATHABLE -> SleepRefusal.NONE;
            case PENDING -> SleepRefusal.PENDING;
            case VACUUM -> SleepRefusal.UNBREATHABLE;
        };
    }

    /** The COMMON {@code PLANET} options; force matters only while allow is on. */
    public record Options(boolean allow, boolean force) {
        public static final Options DEFAULTS = new Options(false, false);
    }

    /** The outcome for a saved off-world respawn record; every outcome except {@link #ACCEPT} uses the Overworld. */
    public enum RespawnOutcome {
        ACCEPT,
        DISABLED,
        MISSING_BODY,
        UNLOADED,
        NO_SPAWN_BLOCK,
        OBSTRUCTED,
        HAZARD,
        STATION_UNAVAILABLE,
        STATION_DENIED,
        PENDING,
        UNBREATHABLE;

        public boolean accepted() {
            return this == ACCEPT;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Facts about one saved record, asked lazily in the order of {@link #decideRespawn}. A fact is asked only after
     * every earlier fact passed, so the adapter reads a block only once its chunks are known to be loaded.
     */
    public interface RespawnProbe {
        /** The record's Level exists and the catalog maps exactly one body to it. */
        boolean bodyPresent();

        /** Every chunk the vanilla stand-up search may read is already loaded. */
        boolean loaded();

        /** The saved block is a real bed, or a respawn anchor that vanilla would use, in a Level where it works. */
        boolean spawnBlock();

        /** Vanilla's stand-up search finds an arrival position. */
        boolean arrivalFound();

        /** The arrival's body overlaps a solid collision, a liquid or a burning block. */
        boolean hazard();

        /** The worst station authority over the saved block and the arrival. */
        StationAccess station();

        /** The worst breathability over the arrival's body cells. */
        BreathabilityState air();
    }

    /**
     * ADR-066 section 3.3. Allow off: always the Overworld. Allow on: a present body, a loaded arrival, a real bed or
     * anchor, a found arrival without solid or liquid hazards, and station permission are always required; force
     * skips only the final air check, which otherwise needs known breathable air.
     */
    public static RespawnOutcome decideRespawn(Options options, RespawnProbe probe) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(probe, "probe");
        if (!options.allow()) {
            return RespawnOutcome.DISABLED;
        }
        if (!probe.bodyPresent()) {
            return RespawnOutcome.MISSING_BODY;
        }
        if (!probe.loaded()) {
            return RespawnOutcome.UNLOADED;
        }
        if (!probe.spawnBlock()) {
            return RespawnOutcome.NO_SPAWN_BLOCK;
        }
        if (!probe.arrivalFound()) {
            return RespawnOutcome.OBSTRUCTED;
        }
        if (probe.hazard()) {
            return RespawnOutcome.HAZARD;
        }
        StationAccess station = Objects.requireNonNull(probe.station(), "station");
        if (station == StationAccess.UNAVAILABLE) {
            return RespawnOutcome.STATION_UNAVAILABLE;
        }
        if (station == StationAccess.DENIED) {
            return RespawnOutcome.STATION_DENIED;
        }
        if (options.force()) {
            return RespawnOutcome.ACCEPT;
        }
        return switch (Objects.requireNonNull(probe.air(), "air")) {
            case BREATHABLE -> RespawnOutcome.ACCEPT;
            case PENDING -> RespawnOutcome.PENDING;
            case VACUUM -> RespawnOutcome.UNBREATHABLE;
        };
    }

    /** The worse of two station facts for one decision. */
    public static StationAccess worse(StationAccess first, StationAccess second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return rank(second) > rank(first) ? second : first;
    }

    private static int rank(StationAccess access) {
        return switch (access) {
            case NOT_APPLICABLE -> 0;
            case OUTSIDE -> 1;
            case VISIT -> 2;
            case DENIED -> 3;
            case UNAVAILABLE -> 4;
        };
    }

    /** The worse of two air facts: vacuum, then pending, then breathable. */
    public static BreathabilityState worse(BreathabilityState first, BreathabilityState second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first == BreathabilityState.VACUUM || second == BreathabilityState.VACUUM) {
            return BreathabilityState.VACUUM;
        }
        return first == BreathabilityState.PENDING || second == BreathabilityState.PENDING
                ? BreathabilityState.PENDING : BreathabilityState.BREATHABLE;
    }
}
