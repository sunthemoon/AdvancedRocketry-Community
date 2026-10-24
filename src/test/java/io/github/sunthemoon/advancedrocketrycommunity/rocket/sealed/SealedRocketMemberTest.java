package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedRocketMemberTest {
    private static final UUID TRANSACTION = UUID.fromString("7588d536-2ab9-4f28-96f4-b29a1ecc0464");
    private static final UUID SNAPSHOT = UUID.fromString("ec81ad61-2804-4cd8-823e-7d8b7b6653bc");
    private static final UUID ENTITY = UUID.fromString("515894a2-4e81-435e-aa66-34b5a4506bcc");
    private static final RocketPosition AT = new RocketPosition(-117, 66, 223);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void plainAndOpaquePayloadRoundTripWithoutRegistryLookup() {
        var plain = member(0, null);
        assertEquals(plain, SealedRocketMember.decode(plain.encode()).orElseThrow());
        assertFalse(plain.encode().contains("block_entity"));
        var opaque = new CompoundTag(); opaque.putString("unknown_future_field", "kept exactly");
        opaque.putByteArray("opaque", new byte[] {0, -1, 127});
        var payload = new RocketBlockEntityPayload(id("missing:adapter"), opaque);
        var member = member(2047, payload);
        assertEquals(member, SealedRocketMember.decode(member.encode()).orElseThrow());
        assertEquals(opaque, member.encode().getCompound("block_entity").getCompound("data"));
    }

    @Test void everyMemberIndexIsAllowedIndependentlyOfTheOriginalPayloadCount() {
        for (int index = 0; index < 2048; index++) {
            var member = member(index, null);
            assertEquals(member, SealedRocketMember.decode(member.encode()).orElseThrow());
        }
        assertThrows(IllegalArgumentException.class, () -> member(-1, null));
        assertThrows(IllegalArgumentException.class, () -> member(2048, null));
    }

    @Test void maximalPayloadAndPropertyFramingFitWithoutReducingThePayloadAllowance() {
        var body = new CompoundTag(); body.putByteArray("bytes", new byte[0]);
        body.putByteArray("bytes", new byte[RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - RocketNbtSize.uncompressedBytes(body)]);
        var payload = new RocketBlockEntityPayload(id("a:" + "p".repeat(253)), body);
        Map<String, String> properties = new TreeMap<>();
        for (int index = 0; index < 32; index++) {
            String prefix = Integer.toString(index);
            properties.put(prefix + "\u7bb1".repeat(64 - prefix.length()), "\u7bb1".repeat(64));
        }
        var member = new SealedRocketMember(TRANSACTION, SNAPSHOT, ENTITY, "a".repeat(64),
                id("a:" + "d".repeat(253)), AT, 2047,
                new RocketBlockState(id("a:" + "b".repeat(253)), properties), payload);
        var encoded = member.encode();
        assertEquals(262_144, payload.uncompressedBytes());
        assertTrue(SealedRocketMember.fitsFullTag(encoded, AT));
        assertEquals(member, SealedRocketMember.decode(encoded).orElseThrow());
        assertEquals(body, encoded.getCompound("block_entity").getCompound("data"));
    }

    @Test void missingFutureWrongTypeAndUnknownFieldsAreNotNormalizedIntoValidMembers() {
        assertTrue(SealedRocketMember.decode(null).isEmpty());
        assertTrue(SealedRocketMember.decode(IntTag.valueOf(1)).isEmpty());
        rejected(root -> root.remove("schema"));
        rejected(root -> root.putInt("schema", 2));
        rejected(root -> root.putString("schema", "1"));
        rejected(root -> root.putInt("future_field", 1));
        rejected(root -> root.putIntArray("position", new int[] {1, 2}));
        rejected(root -> root.putLong("member_index", 1));
        rejected(root -> root.putInt("member_index", 2048));
    }

    @Test void uuidHashAndIdentifiersHaveExactEncodings() {
        rejected(root -> root.putString("transaction_id", TRANSACTION.toString()));
        rejected(root -> root.putIntArray("snapshot_id", new int[] {1, 2, 3}));
        rejected(root -> root.putIntArray("rocket_entity_id", new int[] {1, 2, 3, 4, 5}));
        rejected(root -> root.putString("content_hash", "A".repeat(64)));
        rejected(root -> root.putString("content_hash", "0".repeat(63)));
        rejected(root -> root.putString("dimension", "overworld"));
        rejected(root -> root.putString("dimension", "x:" + "a".repeat(254)));
        rejected(root -> root.getCompound("state").putString("id", "MISSING:block"));
    }

    @Test void statesUseTheExistingPropertyBoundsWithoutCoercion() {
        rejected(root -> root.getCompound("state").getCompound("properties").putInt("facing", 1));
        rejected(root -> root.getCompound("state").getCompound("properties").putString("x".repeat(65), "v"));
        rejected(root -> root.getCompound("state").getCompound("properties").putString("key", "v".repeat(65)));
        rejected(root -> root.getCompound("state").putString("extra", "kept, not interpreted"));
    }

    @Test void payloadCannotSmuggleNativeIdentityOrWrongEnvelopeTypes() {
        for (String key : new String[] {"id", "x", "y", "z"}) {
            rejected(root -> {
                var envelope = new CompoundTag();
                envelope.putString("adapter", "missing:adapter");
                var data = new CompoundTag(); data.putString(key, "invalid");
                envelope.put("data", data); root.put("block_entity", envelope);
            });
        }
        rejected(root -> root.put("block_entity", StringTag.valueOf("invalid")));
        rejected(root -> {
            var envelope = new CompoundTag(); envelope.putString("adapter", "missing:adapter");
            envelope.put("data", new CompoundTag()); envelope.putInt("extra", 1); root.put("block_entity", envelope);
        });
    }

    @Test void oversizedAndDeepInputIsInspectedBeforeAnyRecursiveCopy() {
        var large = member(0, null).encode();
        large.putByteArray("unknown", new byte[SealedRocketMember.MAX_BYTES + 1]);
        assertTrue(SealedRocketMember.decode(large).isEmpty());
        var deep = member(0, null).encode(); var cursor = deep;
        for (int depth = 0; depth < 100; depth++) {
            var child = new CompoundTag(); cursor.put("child", child); cursor = child;
        }
        assertTrue(SealedRocketMember.decode(deep).isEmpty());
        var cyclic = new CompoundTag(); cyclic.put("cycle", cyclic);
        assertTrue(SealedRocketMember.decode(cyclic).isEmpty());
    }

    @Test void decodingAndReturnedPayloadsDoNotShareMutableNbt() {
        var data = new CompoundTag(); data.putInt("value", 7);
        var root = member(0, new RocketBlockEntityPayload(id("missing:adapter"), data)).encode();
        var decoded = SealedRocketMember.decode(root).orElseThrow();
        root.getCompound("block_entity").getCompound("data").putInt("value", 9);
        assertEquals(7, decoded.payload().data().getInt("value"));
        decoded.payload().data().putInt("value", 11);
        assertEquals(7, decoded.payload().data().getInt("value"));
    }

    private static void rejected(Consumer<CompoundTag> mutation) {
        var root = member(0, null).encode(); mutation.accept(root);
        var before = root.copy();
        assertTrue(SealedRocketMember.decode(root).isEmpty(), root::toString);
        assertEquals(before, root);
    }

    private static SealedRocketMember member(int index, RocketBlockEntityPayload payload) {
        return new SealedRocketMember(TRANSACTION, SNAPSHOT, ENTITY, "a".repeat(64), id("minecraft:overworld"),
                AT, index, new RocketBlockState(id("missing:member"), Map.of("facing", "north")), payload);
    }
    private static ResourceLocation id(String id) { return ResourceLocation.tryParse(id); }
}
