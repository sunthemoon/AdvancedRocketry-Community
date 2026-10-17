package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable bounded READY/ARMED values. The acknowledged root, not a decoded value, is authority. */
public final class EscrowFlightTables {
    public enum Outcome { READY, ARMED, HANDED_OFF, CANCELLED }
    private EscrowFlightTables() { }
    public record Head(UUID logical, UUID owner, UUID anchor, long releaseGeneration, UUID physical,
            long epoch, EscrowSignatureKind kind, String signature, String invariant, long issued, long completed, Outcome outcome) {
        public Head {
            java.util.Objects.requireNonNull(logical); java.util.Objects.requireNonNull(owner);
            java.util.Objects.requireNonNull(anchor); java.util.Objects.requireNonNull(physical);
            EscrowTags.require(releaseGeneration > 0 && epoch >= 0 && issued >= completed && completed >= 0
                    && kind == EscrowSignatureKind.FUEL_VECTOR_V1 && outcome != null && outcome != Outcome.ARMED, "Invalid READY head");
            EscrowTags.digest(signature); EscrowTags.digest(invariant);
        }
        public CompoundTag encode() {
            var t = new CompoundTag(); t.putInt("v", 1); t.putUUID("l", logical); t.putUUID("o", owner); t.putUUID("a", anchor);
            t.putLong("g", releaseGeneration); t.putUUID("p", physical); t.putLong("e", epoch); t.putString("k", kind.name());
            digest(t, "s", signature); digest(t, "i", invariant); t.putLong("issued", issued); t.putLong("completed", completed);
            t.putString("outcome", outcome.name()); EscrowTags.bound(t, 1_024, 8, 64); return t;
        }
        public static Head decode(CompoundTag t) {
            EscrowTags.bound(t, 1_024, 8, 64); EscrowTags.fields(t, "v", "l", "o", "a", "g", "p", "e", "k", "s", "i", "issued", "completed", "outcome");
            EscrowTags.require(EscrowTags.integer(t, "v") == 1, "Unsupported READY head");
            return new Head(EscrowTags.uuid(t, "l"), EscrowTags.uuid(t, "o"), EscrowTags.uuid(t, "a"), EscrowTags.number(t, "g"),
                    EscrowTags.uuid(t, "p"), EscrowTags.number(t, "e"), EscrowTags.enumeration(EscrowSignatureKind.class, t, "k"),
                    digest(t, "s"), digest(t, "i"), EscrowTags.number(t, "issued"), EscrowTags.number(t, "completed"), EscrowTags.enumeration(Outcome.class, t, "outcome"));
        }
    }
    public record Armed(UUID ledger, UUID logical, UUID owner, UUID source, long epoch, UUID transfer, long serial,
            long generation, String binding, String launch, String sourceEnvelope, String destinationEnvelope,
            UUID destination, EscrowGuard journal, UUID anchor, long releaseGeneration, Outcome outcome) {
        public Armed {
            for (UUID id : List.of(ledger, logical, owner, source, transfer, destination)) { java.util.Objects.requireNonNull(id); }
            EscrowTags.require(!source.equals(destination) && epoch >= 0 && serial > 0 && generation > 0 && outcome != null && outcome != Outcome.READY
                    && (anchor == null ? releaseGeneration == 0 : releaseGeneration > 0), "Invalid ARMED entry");
            for (String hash : List.of(binding, launch, sourceEnvelope, destinationEnvelope)) { EscrowTags.digest(hash); }
            EscrowTags.require(journal != null && journal.rocket().equals(logical), "Invalid launch journal guard");
        }
        public CompoundTag encode() {
            var t = new CompoundTag(); t.putInt("v", 1); t.putUUID("ledger", ledger); t.putUUID("l", logical); t.putUUID("o", owner);
            t.putUUID("src", source); t.putLong("epoch", epoch); t.putUUID("t", transfer); t.putLong("serial", serial); t.putLong("g", generation);
            digest(t, "binding", binding); digest(t, "launch", launch); digest(t, "source", sourceEnvelope); digest(t, "dest", destinationEnvelope);
            t.putUUID("d", destination); var guard = EscrowCodec.guard(journal); digest(guard, "journal_digest", journal.journalDigest()); t.put("j", guard);
            if (anchor != null) { t.putUUID("a", anchor); } t.putLong("release", releaseGeneration); t.putString("outcome", outcome.name());
            EscrowTags.bound(t, 1_024, 8, 64); return t;
        }
        public static Armed decode(CompoundTag t) {
            EscrowTags.bound(t, 1_024, 8, 64); EscrowTags.fields(t, "v", "ledger", "l", "o", "src", "epoch", "t", "serial", "g", "binding", "launch", "source", "dest", "d", "j", "a", "release", "outcome");
            EscrowTags.require(EscrowTags.integer(t, "v") == 1, "Unsupported ARMED entry");
            var guard = EscrowTags.compound(t, "j").copy(); guard.putString("journal_digest", digest(guard, "journal_digest"));
            return new Armed(EscrowTags.uuid(t, "ledger"), EscrowTags.uuid(t, "l"), EscrowTags.uuid(t, "o"), EscrowTags.uuid(t, "src"),
                    EscrowTags.number(t, "epoch"), EscrowTags.uuid(t, "t"), EscrowTags.number(t, "serial"), EscrowTags.number(t, "g"),
                    digest(t, "binding"), digest(t, "launch"), digest(t, "source"), digest(t, "dest"), EscrowTags.uuid(t, "d"), EscrowCodec.guard(guard),
                    t.contains("a") ? EscrowTags.uuid(t, "a") : null, EscrowTags.number(t, "release"), EscrowTags.enumeration(Outcome.class, t, "outcome"));
        }
    }
    static void digest(CompoundTag t, String key, String hash) { EscrowTags.digest(hash); t.putByteArray(key, HexFormat.of().parseHex(hash)); }
    static String digest(CompoundTag t, String key) {
        EscrowTags.require(t.contains(key, Tag.TAG_BYTE_ARRAY) && t.getByteArray(key).length == 32, "Invalid authority digest");
        return HexFormat.of().formatHex(t.getByteArray(key));
    }
}
