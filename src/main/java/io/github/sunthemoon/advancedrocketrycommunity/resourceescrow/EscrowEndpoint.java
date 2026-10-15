package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Objects;
import java.util.UUID;

/** Frozen placement/entity identity and one resource transition, independent of Minecraft lifecycle. */
public record EscrowEndpoint(Kind kind, String level, UUID identity, UUID logicalRocket,
                             String type, int x, int y, int z, Unit unit, long beforeEpoch,
                             String beforeSignature, String afterSignature) {
    public enum Kind { BLOCK_ENTITY, ENTITY }
    public enum Unit { PRIMARY, OXIDIZER, TANK }

    public EscrowEndpoint {
        Objects.requireNonNull(kind); Objects.requireNonNull(identity); Objects.requireNonNull(unit);
        EscrowTags.id(level); EscrowTags.id(type);
        EscrowTags.require(kind == Kind.ENTITY ? logicalRocket != null : logicalRocket == null,
                "Endpoint logical identity does not match its kind");
        EscrowTags.require(beforeEpoch >= 0 && beforeEpoch < Long.MAX_VALUE, "Invalid endpoint epoch");
        EscrowTags.digest(beforeSignature); EscrowTags.digest(afterSignature);
    }

    public long afterEpoch() { return beforeEpoch + 1; }
    public Key key() {
        // Native rockets advance one whole-vector epoch. Unit still selects each link's bank.
        Unit prefixUnit = EscrowAdapterIds.nativeRocket(this)
                ? Unit.PRIMARY : unit;
        return new Key(kind, level, identity, logicalRocket, type, x, y, z, prefixUnit);
    }
    public record Key(Kind kind, String level, UUID identity, UUID logicalRocket,
                      String type, int x, int y, int z, Unit unit) { }
}
