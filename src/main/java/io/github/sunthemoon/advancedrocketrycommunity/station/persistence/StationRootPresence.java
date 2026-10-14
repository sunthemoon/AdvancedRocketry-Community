package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import java.nio.file.Path;
import java.util.Objects;

/**
 * W44 section 3.6 for the station registry: how the root was first obtained, and the re-check before the first write
 * of a proven-absent root, both for checked writes (inside the writer, before the move) and for vanilla's in-place
 * ordinary save. Only a demonstrably successful write makes the file the session's own.
 */
final class StationRootPresence {
    private ManagedRootPresence presence;
    private boolean sessionWritten;
    private boolean appeared;

    StationRootPresence(ManagedRootPresence presence) {
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    ManagedRootPresence presence() { return presence; }

    boolean unreadable() { return presence == ManagedRootPresence.UNREADABLE; }

    /** The checked-write guard; it throws before the move is attempted, so the result is a clean WRITE_FAILED. */
    StationCheckedWriter.Guard guard() {
        return target -> {
            if (mustRecheck() && !ManagedRootSupplier.stillAbsent(target)) {
                appeared = true;
                throw new java.io.IOException("A station registry file appeared before this session's first write");
            }
        };
    }

    void committed() { sessionWritten = true; }

    /** After a failed checked write: true when an appeared entry made the root UNREADABLE. */
    boolean afterFailedWrite() {
        if (appeared && presence == ManagedRootPresence.PROVEN_ABSENT) {
            becomeUnreadable();
            return true;
        }
        return false;
    }

    /** Before vanilla's in-place ordinary save: false when the save must write nothing. */
    boolean admitsOrdinarySave(Path file) {
        if (unreadable()) {
            return false;
        }
        if (mustRecheck()) {
            if (!ManagedRootSupplier.stillAbsent(file)) {
                becomeUnreadable();
                return false;
            }
        }
        return true;
    }

    private boolean mustRecheck() {
        return presence == ManagedRootPresence.PROVEN_ABSENT && !sessionWritten;
    }

    /** Nothing was overwritten; all station gameplay is unavailable until a plain restart re-reads the file. */
    private void becomeUnreadable() {
        presence = ManagedRootPresence.UNREADABLE;
        AdvancedRocketryCommunity.LOGGER.error("ARCE_MANAGED_ROOT_UNREADABLE type={} stage=first_write",
                StationRegistrySavedData.DATA_NAME);
    }
}
