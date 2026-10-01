package io.github.sunthemoon.advancedrocketrycommunity.endgame.intent;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.Objects;

/**
 * ADR-054 section 4: the checks every intent passes on the server thread, first failure wins. The per-player rate
 * is checked last by the caller, so a refused intent does not use up the player's interval. A refused intent
 * changes nothing.
 */
public final class EndgameIntentRules {
    private EndgameIntentRules() {
    }

    public static EndgameCode check(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.realPlayer()) {
            return EndgameCode.NOT_A_PLAYER;
        }
        if (!snapshot.operational()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        if (!snapshot.sameLevel()) {
            return EndgameCode.WRONG_LEVEL;
        }
        if (!snapshot.chunkLoaded()) {
            return EndgameCode.CHUNK_UNLOADED;
        }
        if (!snapshot.sameDevice()) {
            return EndgameCode.DEVICE_CHANGED;
        }
        if (!snapshot.withinReach()) {
            return EndgameCode.TOO_FAR;
        }
        if (snapshot.quarantined()) {
            return EndgameCode.DEVICE_QUARANTINED;
        }
        if (!snapshot.authority().allowed()) {
            return snapshot.authority().refusal();
        }
        if (snapshot.startsOperation() && !snapshot.systemEnabled()) {
            return EndgameCode.SYSTEM_DISABLED;
        }
        return EndgameCode.OK;
    }

    /**
     * @param realPlayer      a connected {@code ServerPlayer} that is not a {@code FakePlayer}
     * @param sameDevice      the menu's block entity is still the device it opened, at the same position
     * @param withinReach     within 8 blocks of the device
     * @param startsOperation the intent starts an operation; settlement intents work while a system is disabled
     */
    public record Snapshot(boolean realPlayer, boolean operational, boolean sameLevel, boolean chunkLoaded,
                           boolean sameDevice, boolean withinReach, boolean quarantined,
                           EndgameAuthority.Decision authority, boolean startsOperation, boolean systemEnabled) {
        public Snapshot {
            Objects.requireNonNull(authority, "authority");
        }
    }
}
