package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketAdapterPayloadsTest {
    private static final ResourceLocation ADAPTER = ResourceLocation.tryParse("fixture:container");

    @Test
    void encodeCreatesOnlyTheVersionedEnvelopeAndPreservesAdapterIdentity() {
        CompoundTag body = body();
        RocketBlockEntityPayload payload = RocketAdapterPayloads.encode(ADAPTER, 3, body);
        assertEquals(ADAPTER, payload.adapterId());
        assertEquals(Set.of("payload_version", "data"), payload.data().getAllKeys());
        assertTrue(payload.data().contains("payload_version", Tag.TAG_INT));
        assertEquals(3, payload.data().getInt("payload_version"));
        assertTrue(payload.data().contains("data", Tag.TAG_COMPOUND));
        assertEquals(body, payload.data().getCompound("data"));
        assertEquals(body, RocketAdapterPayloads.decode(payload, 3).orElseThrow());
    }

    @Test
    void encodeAndDecodeDefensivelyCopyNestedDataInBothDirections() {
        CompoundTag body = body();
        CompoundTag expected = body.copy();
        RocketBlockEntityPayload payload = RocketAdapterPayloads.encode(ADAPTER, 1, body);
        body.putInt("count", 999);
        body.getCompound("nested").putString("name", "mutated input");
        body.getByteArray("bytes")[0] = 99;
        CompoundTag decoded = RocketAdapterPayloads.decode(payload, 1).orElseThrow();
        assertEquals(expected, decoded);
        decoded.putInt("count", 888);
        decoded.getCompound("nested").putString("name", "mutated output");
        decoded.getByteArray("bytes")[0] = 88;
        payload.data().getCompound("data").putInt("count", 777);
        assertEquals(expected, RocketAdapterPayloads.decode(payload, 1).orElseThrow());
    }

    @Test
    void versionsMustMatchExactlyAndDoNotMigrateTheBody() {
        RocketBlockEntityPayload payload = RocketAdapterPayloads.encode(ADAPTER, 2, body());
        CompoundTag before = payload.data();
        assertTrue(RocketAdapterPayloads.decode(payload, 1).isEmpty());
        assertTrue(RocketAdapterPayloads.decode(payload, 3).isEmpty());
        assertEquals(before, payload.data());
        assertEquals(body(), RocketAdapterPayloads.decode(payload, 2).orElseThrow());
        assertNotEquals(payload, RocketAdapterPayloads.encode(ADAPTER, 3, body()));
    }

    @Test
    void emptyBodyAndLargestPositiveVersionRemainValid() {
        RocketBlockEntityPayload payload = RocketAdapterPayloads.encode(
                ADAPTER, Integer.MAX_VALUE, new CompoundTag());
        assertEquals(new CompoundTag(), RocketAdapterPayloads.decode(payload, Integer.MAX_VALUE).orElseThrow());
    }

    @Test
    void internalProgrammerErrorsAreNotInterpretedAsPersistedVersions() {
        for (int version : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RocketAdapterPayloads.encode(ADAPTER, version, body()));
            assertThrows(IllegalArgumentException.class,
                    () -> RocketAdapterPayloads.decode(raw(envelope(1, body())), version));
        }
        assertThrows(NullPointerException.class, () -> RocketAdapterPayloads.encode(null, 1, body()));
        assertThrows(NullPointerException.class, () -> RocketAdapterPayloads.encode(ADAPTER, 1, null));
        assertThrows(NullPointerException.class, () -> RocketAdapterPayloads.decode(null, 1));
    }

    @Test
    void malformedEnvelopeIsRejectedWithoutChangingOpaquePayload() {
        List<Consumer<CompoundTag>> changes = List.of(
                tag -> tag.remove("payload_version"),
                tag -> tag.remove("data"),
                tag -> tag.putInt("payload_version", 0),
                tag -> tag.putInt("payload_version", -1),
                tag -> tag.putByte("payload_version", (byte) 1),
                tag -> tag.putShort("payload_version", (short) 1),
                tag -> tag.putLong("payload_version", 1L),
                tag -> tag.putFloat("payload_version", 1.0F),
                tag -> tag.putDouble("payload_version", 1.0D),
                tag -> tag.putString("payload_version", "1"),
                tag -> tag.putInt("data", 1),
                tag -> tag.putString("extra", "unrecognized envelope member"));
        for (int index = 0; index < changes.size(); index++) {
            CompoundTag envelope = envelope(1, body());
            changes.get(index).accept(envelope);
            RocketBlockEntityPayload payload = raw(envelope);
            CompoundTag before = payload.data();
            assertTrue(RocketAdapterPayloads.decode(payload, 1).isEmpty(), "Malformed case " + index);
            assertEquals(before, payload.data(), "Opaque payload changed in case " + index);
        }
        assertTrue(RocketAdapterPayloads.decode(raw(new CompoundTag()), 1).isEmpty());
    }

    @Test
    void envelopeCannotHideRootIdentityInProviderBodyOnEncodeOrDecode() {
        for (String key : new String[] {"x", "y", "z", "id"}) {
            CompoundTag body = body();
            body.putString(key, "forbidden even when not the usual NBT type");
            assertThrows(IllegalArgumentException.class,
                    () -> RocketAdapterPayloads.encode(ADAPTER, 1, body));
            RocketBlockEntityPayload opaque = raw(envelope(1, body));
            CompoundTag before = opaque.data();
            assertTrue(RocketAdapterPayloads.decode(opaque, 1).isEmpty(), "Identity field " + key);
            assertEquals(before, opaque.data());
        }
    }

    @Test
    void identityRestrictionIsForTheBodyRootNotUnrelatedNestedData() {
        CompoundTag nested = new CompoundTag();
        nested.putInt("x", 12);
        nested.putInt("y", 13);
        nested.putInt("z", 14);
        nested.putString("id", "fixture:resource");
        CompoundTag body = new CompoundTag();
        body.put("resource", nested);
        assertEquals(body, RocketAdapterPayloads.decode(
                RocketAdapterPayloads.encode(ADAPTER, 1, body), 1).orElseThrow());
    }

    @Test
    void exactPayloadByteLimitIncludesTheWholeEnvelope() {
        assertEquals(262_144, RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES);
        int overhead = RocketNbtSize.uncompressedBytes(envelope(1, bytes(0)));
        int capacity = RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - overhead;
        RocketBlockEntityPayload payload = RocketAdapterPayloads.encode(ADAPTER, 1, bytes(capacity));
        assertEquals(RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, payload.uncompressedBytes());
        assertEquals(capacity, RocketAdapterPayloads.decode(payload, 1).orElseThrow().getByteArray("bytes").length);
        RocketSnapshotException failure = assertThrows(RocketSnapshotException.class,
                () -> RocketAdapterPayloads.encode(ADAPTER, 1, bytes(capacity + 1)));
        assertEquals(RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE, failure.code());
    }

    @Test
    void bodyThatFitsBarePayloadStillFailsWhenTheEnvelopeExceedsLimit() {
        int capacity = RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - RocketNbtSize.uncompressedBytes(bytes(0));
        CompoundTag body = bytes(capacity);
        assertEquals(RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, raw(body).uncompressedBytes());
        assertThrows(RocketSnapshotException.class, () -> RocketAdapterPayloads.encode(ADAPTER, 1, body));
    }

    @Test
    void envelopeDecodingDoesNotDependOnProviderRegistration() {
        ResourceLocation unavailable = ResourceLocation.tryParse("uninstalled:container");
        RocketBlockEntityPayload payload = new RocketBlockEntityPayload(unavailable, envelope(4, body()));
        assertEquals(body(), RocketAdapterPayloads.decode(payload, 4).orElseThrow());
        assertFalse(payload.data().isEmpty());
        assertEquals(unavailable, payload.adapterId());
    }

    private static CompoundTag body() {
        CompoundTag body = new CompoundTag();
        body.putInt("count", 17);
        body.putByteArray("bytes", new byte[] {1, 2, 3});
        CompoundTag nested = new CompoundTag();
        nested.putString("name", "supplies");
        body.put("nested", nested);
        return body;
    }

    private static CompoundTag bytes(int count) {
        CompoundTag body = new CompoundTag();
        body.putByteArray("bytes", new byte[count]);
        return body;
    }

    private static CompoundTag envelope(int version, CompoundTag body) {
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("payload_version", version);
        envelope.put("data", body);
        return envelope;
    }

    private static RocketBlockEntityPayload raw(CompoundTag envelope) {
        return new RocketBlockEntityPayload(ADAPTER, envelope);
    }
}
