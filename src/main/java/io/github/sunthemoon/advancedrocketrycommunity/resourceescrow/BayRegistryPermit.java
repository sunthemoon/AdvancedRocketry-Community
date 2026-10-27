package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Objects;
import java.util.UUID;

/**
 * The coordinator's registry permit for D2 (W128's one-page design): issued only by the session handle, for an
 * operation whose RAM receipt is INTENT for exactly this satellite. The registry honours it only inside one scoped
 * validation call and only for the validation previews; no public caller can construct one.
 */
public final class BayRegistryPermit {
    private final java.util.function.BiPredicate<UUID, UUID> intent;
    private final UUID op;
    private final UUID satellite;

    /** {@code intent} answers whether the operation's RAM receipt is still INTENT for the satellite in a live session. */
    BayRegistryPermit(java.util.function.BiPredicate<UUID, UUID> intent, UUID op, UUID satellite) {
        this.intent = Objects.requireNonNull(intent); this.op = Objects.requireNonNull(op);
        this.satellite = Objects.requireNonNull(satellite);
    }

    /** This permit's satellite, while the session is live and the operation's RAM receipt is still its INTENT. */
    public boolean covers(UUID candidate) {
        return satellite.equals(candidate) && intent.test(op, satellite);
    }

    public UUID op() { return op; }
    public UUID satellite() { return satellite; }
}
