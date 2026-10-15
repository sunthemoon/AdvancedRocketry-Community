package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

final class EscrowTestFixtures {
    static UUID id(long value) { return new UUID(11, value); }
    static String hash(long value) { return RocketNativeResources.hash("test-" + value); }
    static EscrowOperation operation(long sequence) { return operation(sequence, sequence, 0); }
    static EscrowOperation operation(long sequence, long rocket, long epoch) {
        CompoundTag payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
        return new EscrowOperation(id(sequence), sequence, id(99), 1, EscrowOperation.Phase.PREPARED,
                EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", id(1_000 + sequence), null,
                        "minecraft:chest", (int) sequence, 64, 0, EscrowEndpoint.Unit.TANK, 0, hash(sequence), hash(sequence + 100)),
                new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", id(2_000 + rocket), id(rocket),
                        "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, epoch, hash(epoch), hash(epoch + 1)),
                new EscrowGuard(id(rocket), null, 1, hash(400)));
    }
    static EscrowLedger ledger(EscrowOperation... records) {
        return new EscrowLedger(id(500), java.util.Arrays.stream(records).mapToLong(EscrowOperation::sequence).max().orElse(0),
                0, List.of(records), List.of(), List.of());
    }
    /** Explicit synthetic envelope hashes for codec/prefix tests, never a live native permit. */
    static EscrowOperation nativeModel(EscrowOperation op) {
        var b = op.nativeBinding();
        var binding = new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), b.owner(), id(900), hash(900), b.invariant(),
                b.sourceReference(), b.destinationReference(), b.beforeWrapper(), b.afterWrapper(), b.beforeFrontier(), b.afterFrontier(),
                b.sourceBefore(), b.sourceAfter(), b.destinationBefore(), b.destinationAfter(), true);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding);
    }
    static CompoundTag floor(long id) {
        CompoundTag tag = EscrowCodec.guard(new EscrowGuard(id(id), null, 1, hash(id)));
        tag.putInt("schema_version", 2); tag.putUUID("entity_id", id(id + 1)); tag.putLong("resource_epoch", 1);
        tag.putString("signature_kind", EscrowSignatureKind.FIXTURE_RESOURCE_V1.name()); tag.putString("invariant_digest", hash(id));
        tag.putString("signature", hash(0)); tag.putLong("highest_seq", 1); return tag;
    }
    static CompoundTag receipt(int id) {
        var op = operation(10_000 + id).withReason(EscrowReason.OTHER_STATE); var root = ledger(op);
        var observed = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        return EscrowSettlementTombstones.create(op, intent);
    }
}
