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
    static CompoundTag floor(long id) {
        CompoundTag tag = EscrowCodec.guard(new EscrowGuard(id(id), null, 1, hash(id)));
        tag.putInt("schema_version", 1); tag.putUUID("entity_id", id(id + 1)); tag.putLong("resource_epoch", 1);
        tag.putString("signature", hash(0)); tag.putLong("highest_seq", 1); return tag;
    }
}
