package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import net.minecraft.nbt.CompoundTag;

/** Governed record metadata. Decoding this value grants no action or acknowledgement authority. */
public final class EscrowTransferEnvelope {
    public static final int SCHEMA = 1;
    public static final int MAX_BYTES = 4_096;
    private final EscrowFlightTables.Armed armed;
    private final CompoundTag source;
    private final CompoundTag destination;
    private final String digest;
    private final java.util.function.Function<CompoundTag, String> envelopeDigest;
    // Diagnostics only (review N4): native envelope digests this metadata computed while validating records.
    private final java.util.concurrent.atomic.AtomicLong envelopeDigests = new java.util.concurrent.atomic.AtomicLong();
    // RocketTransferRecord has identity equality. Values never retain their weak keys.
    private final java.util.Map<RocketTransferRecord, Expanded> validated = new java.util.WeakHashMap<>();

    private EscrowTransferEnvelope(EscrowFlightTables.Armed armed, CompoundTag source, CompoundTag destination) {
        this(armed, source, destination, EscrowNativeEnvelope::digest);
    }

    private EscrowTransferEnvelope(EscrowFlightTables.Armed armed, CompoundTag source, CompoundTag destination,
                                  java.util.function.Function<CompoundTag, String> envelopeDigest) {
        EscrowTags.require(armed.outcome() == EscrowFlightTables.Outcome.ARMED, "Record requires its original ARMED tuple");
        this.armed = armed; this.source = source.copy(); this.destination = destination.copy();
        this.envelopeDigest = tag -> { envelopeDigests.incrementAndGet(); return envelopeDigest.apply(tag); };
        digest = CanonicalNbtDigest.of(encode(), MAX_BYTES, 16);
    }

    public static EscrowTransferEnvelope capture(EscrowFlightTables.Armed armed,
            CompoundTag sourceEnvelope, CompoundTag destinationEnvelope,
            RocketStructureSnapshot sourceSnapshot, RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlight, RocketFlightData destinationFlight) {
        var result = new EscrowTransferEnvelope(armed,
                EscrowTransferProjection.capture(sourceEnvelope, armed, false, sourceSnapshot, sourceFlight),
                EscrowTransferProjection.capture(destinationEnvelope, armed, true, destinationSnapshot, destinationFlight));
        result.validateBodies(sourceSnapshot, destinationSnapshot, sourceFlight, destinationFlight);
        return result;
    }

    public CompoundTag encode() {
        var tag = new CompoundTag(); tag.putInt("schema_version", SCHEMA); tag.put("armed", armed.encode());
        tag.put("source", source.copy()); tag.put("destination", destination.copy());
        tag.putString("signature_kind", EscrowSignatureKind.FUEL_VECTOR_V1.name());
        EscrowTags.bound(tag, MAX_BYTES, 16, 512); return tag;
    }

    public static EscrowTransferEnvelope decode(CompoundTag tag) {
        return decode(tag, EscrowNativeEnvelope::digest);
    }

    static EscrowTransferEnvelope decode(CompoundTag tag, java.util.function.Function<CompoundTag, String> envelopeDigest) {
        EscrowTags.bound(tag, MAX_BYTES, 16, 512);
        EscrowTags.fields(tag, "schema_version", "armed", "source", "destination", "signature_kind");
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == SCHEMA
                && EscrowTags.enumeration(EscrowSignatureKind.class, tag, "signature_kind") == EscrowSignatureKind.FUEL_VECTOR_V1,
                "Unsupported native transfer envelope");
        return new EscrowTransferEnvelope(EscrowFlightTables.Armed.decode(EscrowTags.compound(tag, "armed")),
                EscrowTags.compound(tag, "source"), EscrowTags.compound(tag, "destination"), envelopeDigest);
    }

    public EscrowFlightTables.Armed armed() { return armed; }
    public String digest() { return digest; }
    long envelopeDigests() { return envelopeDigests.get(); }

    public synchronized void validate(RocketTransferRecord record) {
        if (validated.containsKey(record)) { return; }
        EscrowTags.require(record.schemaVersion() == 3 && record.transferId().equals(armed.transfer())
                && record.logicalRocketId().equals(armed.logical()) && record.ownerId().equals(armed.owner())
                && record.sourceEntityId().equals(armed.source())
                && record.destinationEntityId().map(armed.destination()::equals).orElse(true), "Governed record identity changed");
        validated.put(record, validateBodies(record.sourceSnapshot(), record.destinationSnapshot(), record.sourceFlightData(), record.destinationFlightData()));
    }

    private Expanded validateBodies(RocketStructureSnapshot sourceSnapshot, RocketStructureSnapshot destinationSnapshot,
                                RocketFlightData sourceFlight, RocketFlightData destinationFlight) {
        EscrowTags.require(sourceFlight.schemaVersion() == 2 && destinationFlight.schemaVersion() == 2
                && sourceFlight.fuel().propulsion().isPresent() && destinationFlight.fuel().propulsion().isPresent(),
                "Governed record requires native schema-2 flight bodies");
        var expandedSource = EscrowGovernedEnvelope.comparison(EscrowTransferProjection.expand(source, armed, false, sourceSnapshot, sourceFlight));
        var expandedDestination = EscrowGovernedEnvelope.comparison(EscrowTransferProjection.expand(destination, armed, true, destinationSnapshot, destinationFlight));
        EscrowTags.require(armed.sourceEnvelope().equals(envelopeDigest.apply(expandedSource))
                && armed.destinationEnvelope().equals(envelopeDigest.apply(expandedDestination)),
                "Governed record envelope digest changed");
        return new Expanded(expandedSource, expandedDestination);
    }

    public synchronized CompoundTag sourceEnvelope(RocketTransferRecord record) {
        validate(record); return validated.get(record).source.copy();
    }

    public synchronized CompoundTag destinationEnvelope(RocketTransferRecord record) {
        validate(record); return validated.get(record).destination.copy();
    }
    private record Expanded(CompoundTag source, CompoundTag destination) { }
}
