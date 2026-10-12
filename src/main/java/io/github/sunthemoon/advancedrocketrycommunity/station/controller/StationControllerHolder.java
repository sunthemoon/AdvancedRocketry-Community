package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Objects;
import java.util.Optional;

/**
 * ADR-065 section 11.2: whether the holder of a station controller menu may still see and use it. The checks run
 * in a fixed order and stop at the first refusal, so the controller block is read only once its chunk is known
 * to be loaded. W07 review R3 adds the station: it must still be the one the menu was opened for, and the holder
 * must still have VISIT access to it. Pure; the menu supplies the {@link Probe} over the live server state.
 */
public final class StationControllerHolder {
    private StationControllerHolder() {
    }

    /** The first failed condition, or empty when the holder may still see and use the menu. */
    public static Optional<Refusal> check(Probe probe) {
        Objects.requireNonNull(probe, "probe");
        if (!probe.holder()) {
            return Optional.of(Refusal.NOT_HOLDER);
        }
        if (!probe.connected()) {
            return Optional.of(Refusal.DISCONNECTED);
        }
        if (!probe.alive() || probe.spectator()) {
            return Optional.of(Refusal.NOT_PLAYING);
        }
        if (!probe.sameLevel()) {
            return Optional.of(Refusal.OTHER_LEVEL);
        }
        if (!probe.controllerChunkLoaded()) {
            return Optional.of(Refusal.CHUNK_UNLOADED);
        }
        if (!probe.controllerPresent()) {
            return Optional.of(Refusal.CONTROLLER_GONE);
        }
        if (probe.distanceSquared() > StationLimits.CONTROLLER_REACH * StationLimits.CONTROLLER_REACH) {
            return Optional.of(Refusal.OUT_OF_REACH);
        }
        if (!probe.sameStation()) {
            return Optional.of(Refusal.STATION_CHANGED);
        }
        if (!probe.mayVisit()) {
            return Optional.of(Refusal.NO_VISIT_ACCESS);
        }
        return Optional.empty();
    }

    public enum Refusal {
        /** The player is not the one the menu was opened for. */
        NOT_HOLDER,
        /** The holder is no longer the server's connected player of that UUID. */
        DISCONNECTED,
        /** The holder is dead or a spectator. */
        NOT_PLAYING,
        /** The holder is in another Level. */
        OTHER_LEVEL,
        /** The controller's chunk is not loaded; nothing there is read. */
        CHUNK_UNLOADED,
        /** The exact controller block no longer stands at its position. */
        CONTROLLER_GONE,
        /** The holder is more than {@link StationLimits#CONTROLLER_REACH} blocks from the controller's centre. */
        OUT_OF_REACH,
        /** The station whose region holds the controller is gone or is not the one the menu was opened for. */
        STATION_CHANGED,
        /** The holder is no longer the station's owner, a member or an operator (W07 review R3). */
        NO_VISIT_ACCESS
    }

    /** The live server state, read lazily in the order of {@link #check}. */
    public interface Probe {
        boolean holder();

        boolean connected();

        boolean alive();

        boolean spectator();

        boolean sameLevel();

        /** Only inspects already-loaded chunks; never loads one. */
        boolean controllerChunkLoaded();

        /** Called only after {@link #controllerChunkLoaded()} returned true. */
        boolean controllerPresent();

        double distanceSquared();

        /** The operational registry's live station at the controller is the one the menu was opened for. */
        boolean sameStation();

        /** Called only for that station: the holder may still VISIT it. */
        boolean mayVisit();
    }
}
