package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Encode-only ceiling specimen, NEVER installed as world authority. Its simultaneous operation/receipt population
 * intentionally has no admission reservation. Real admitted generations are measured separately by the writer sink.
 * All encoded fields use their actual codecs and individual byte/count limits; no unknown padding field is added.
 */
final class EscrowFlightBenchmarkCeiling {
    private EscrowFlightBenchmarkCeiling() { }
    private static UUID id(long value) { return new UUID(45, value); }
    private static String hash(long value) { return RocketNativeResources.hash("benchmark-ceiling-" + value); }

    static EscrowLedger create() {
        int low = 0, high = 8000;
        EscrowLedger best = null;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            try {
                var root = build(middle, 0); int bytes = RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root));
                best = root; low = middle + 1;
                if (bytes == EscrowLimits.ROOT_BYTES) { return root; }
            } catch (IllegalArgumentException tooLarge) { high = middle - 1; }
        }
        EscrowTags.require(best != null, "No ceiling codec specimen fits");
        int remainder = EscrowLimits.ROOT_BYTES - RocketNbtSize.uncompressedBytes(EscrowCodec.encode(best));
        EscrowTags.require(remainder < 64, "Current field limits cannot form an exact 1 MiB codec specimen; gap=" + remainder);
        var result = build(high, remainder);
        EscrowTags.require(RocketNbtSize.uncompressedBytes(EscrowCodec.encode(result)) == EscrowLimits.ROOT_BYTES,
                "Ceiling specimen is not exactly 1 MiB");
        return result;
    }
    private static EscrowLedger build(int blob, int lastExtra) {
        UUID ledger = id(1_000_000); var records = new ArrayList<EscrowOperation>();
        var receipts = new ArrayList<CompoundTag>(); var floors = new ArrayList<CompoundTag>();
        var arms = new ArrayList<EscrowFlightTables.Armed>();
        var heads = new ArrayList<EscrowFlightTables.Head>();
        for (int i = 1; i <= 64; i++) {
            var payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1000);
            var fluidTag = new CompoundTag(); fluidTag.putByteArray("blob", new byte[blob + (i == 64 ? lastExtra : 0)]); payload.put("Tag", fluidTag);
            var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", id(1000 + i), null,
                    "minecraft:chest", i * 16, 100, 0, EscrowEndpoint.Unit.TANK, 0, hash(i), hash(i + 1));
            var target = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", id(2000 + i), id(3000 + i),
                    "minecraft:armor_stand", i, 0, 0, EscrowEndpoint.Unit.PRIMARY, 0, hash(i + 2), hash(i + 3));
            source = maximumReference(source); target = maximumReference(target);
            var guard = new EscrowGuard(target.logicalRocket(), null, 1, hash(900));
            var op = new EscrowOperation(id(i), i, id(4000 + i), 0, EscrowOperation.Phase.REPAIR, EscrowReason.OTHER_STATE,
                    payload, RocketNativeResources.signature(payload), source, target, guard);
            var root = new EscrowLedger(ledger, i, 1, List.of(op), List.of(), List.of());
            var observations = List.of(new EscrowSettlementIntent.Observation(source,
                            new EscrowPrefix.State(ledger, 0, source.beforeSignature(), List.of()), true, 5000),
                    new EscrowSettlementIntent.Observation(target,
                            new EscrowPrefix.State(ledger, 0, target.beforeSignature(), List.of()), true, 0));
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    id(999), "c".repeat(128), 0, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), 1, observations);
            records.add(op.settle(intent));
            var receipt = EscrowSettlementTombstones.create(op, intent);
            receipt.putBoolean("released", true); receipt.putLong("release_generation", 1);
            receipts.add(receipt);
            heads.add(new EscrowFlightTables.Head(target.logicalRocket(), op.owner(), op.id(), 1, target.identity(), 0,
                    EscrowSignatureKind.FUEL_VECTOR_V1, hash(i), hash(i), 0, 0, EscrowFlightTables.Outcome.READY));
            arms.add(new EscrowFlightTables.Armed(ledger, id(5000 + i), id(6000 + i), id(7000 + i), 0, id(8000 + i), i,
                    1, hash(i), hash(i), hash(i), hash(i), id(9000 + i), new EscrowGuard(id(5000 + i), null, 1, hash(i)),
                    null, 0, EscrowFlightTables.Outcome.ARMED));
        }
        for (int i = 0; i < 256; i++) {
            var floor = EscrowCodec.guard(new EscrowGuard(id(10_000 + i), id(11_000 + i), 1, hash(i)));
            floor.putInt("schema_version", 2); floor.putUUID("entity_id", id(12_000 + i)); floor.putLong("resource_epoch", 1);
            floor.putString("signature_kind", EscrowSignatureKind.FIXTURE_RESOURCE_V1.name()); floor.putString("invariant_digest", hash(i));
            floor.putString("signature", hash(i)); floor.putLong("highest_seq", 1); floors.add(floor);
        }
        return new EscrowLedger(ledger, 64, 1, records, floors, receipts, 64, heads, arms);
    }
    private static EscrowEndpoint maximumReference(EscrowEndpoint endpoint) {
        var base = new EscrowEndpoint(endpoint.kind(), "x:" + "l".repeat(126), endpoint.identity(), endpoint.logicalRocket(), endpoint.type(),
                endpoint.x(), endpoint.y(), endpoint.z(), endpoint.unit(), endpoint.beforeEpoch(), endpoint.beforeSignature(), endpoint.afterSignature());
        int extra = 512 - RocketNbtSize.uncompressedBytes(EscrowCodec.endpoint(base));
        return new EscrowEndpoint(base.kind(), base.level(), base.identity(), base.logicalRocket(), base.type() + "a".repeat(extra),
                base.x(), base.y(), base.z(), base.unit(), base.beforeEpoch(), base.beforeSignature(), base.afterSignature());
    }
}
