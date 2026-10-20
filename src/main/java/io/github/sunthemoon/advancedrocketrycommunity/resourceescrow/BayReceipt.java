package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One schema-1 bay deployment receipt (W27A-DESIGN section 2): the 24 frozen fields, with {@code schema_version}
 * written by the codec. Hashes and the package signature are lowercase SHA-256 hex, encoded as byte[32]. Only the
 * two after-hashes are optional; REGISTERED requires them. The record refuses and never normalizes.
 */
public record BayReceipt(UUID op, long seq, UUID rocket, RocketPosition bay, UUID satellite, UUID owner,
                         String definition, SatelliteKind kind, String orbit, String packageSig, UUID transfer,
                         String srcBefore, @Nullable String srcAfter, String dstBefore, @Nullable String dstAfter,
                         Phase phase, String reason, boolean preexisting, boolean released,
                         long registryEpoch, long createdTick, long updatedTick, int attempts) {
    public static final int MAX_ATTEMPTS = 1_000;
    public static final int MAX_REASON = 40;
    public enum Phase { INTENT, REGISTERED, REJECTED, REPAIR }
    /** The bay key: the logical rocket and the bay position relative to its snapshot origin. */
    public record Key(UUID rocket, RocketPosition bay) {
        public Key { Objects.requireNonNull(rocket); Objects.requireNonNull(bay); }
    }

    public BayReceipt {
        for (Object value : new Object[] {op, rocket, bay, satellite, owner, definition, kind, orbit, packageSig, transfer,
                srcBefore, dstBefore, phase, reason}) {
            Objects.requireNonNull(value, "Missing bay receipt field");
        }
        EscrowTags.require(seq > 0, "Bay receipt sequence must be positive");
        EscrowTags.id(definition); EscrowTags.id(orbit);
        for (String hash : new String[] {packageSig, srcBefore, dstBefore}) { EscrowTags.digest(hash); }
        if (srcAfter != null) { EscrowTags.digest(srcAfter); }
        if (dstAfter != null) { EscrowTags.digest(dstAfter); }
        EscrowTags.require(phase != Phase.REGISTERED || srcAfter != null && dstAfter != null,
                "A REGISTERED bay receipt requires all four hashes");
        EscrowTags.require(reason.matches("[A-Z][A-Z0-9_]{0," + (MAX_REASON - 1) + "}"), "Invalid bay receipt reason");
        EscrowTags.require(registryEpoch >= 0 && createdTick >= 0 && updatedTick >= 0, "Negative bay receipt counter");
        EscrowTags.require(attempts >= 0 && attempts <= MAX_ATTEMPTS, "Bay receipt attempts out of range");
    }

    public Key key() { return new Key(rocket, bay); }
    /** INTENT, REGISTERED and REPAIR block insertion of the satellite's package; REJECTED never blocks. */
    public boolean blocking() { return phase != Phase.REJECTED; }
    /** Counts in the shared pending union: every blocking phase, and a released receipt in any phase. */
    public boolean pending() { return blocking() || released; }

    public BayReceipt withPhase(Phase next, String nextReason, long tick) {
        return new BayReceipt(op, seq, rocket, bay, satellite, owner, definition, kind, orbit, packageSig, transfer,
                srcBefore, srcAfter, dstBefore, dstAfter, next, nextReason, preexisting, released, registryEpoch, createdTick,
                tick, attempts);
    }
    public BayReceipt withKey(UUID nextRocket, RocketPosition nextBay) {
        return new BayReceipt(op, seq, nextRocket, nextBay, satellite, owner, definition, kind, orbit, packageSig, transfer,
                srcBefore, srcAfter, dstBefore, dstAfter, phase, reason, preexisting, released, registryEpoch, createdTick,
                updatedTick, attempts);
    }
    public BayReceipt withReleased() {
        return new BayReceipt(op, seq, rocket, bay, satellite, owner, definition, kind, orbit, packageSig, transfer,
                srcBefore, srcAfter, dstBefore, dstAfter, phase, reason, preexisting, true, registryEpoch, createdTick,
                updatedTick, attempts);
    }
}
