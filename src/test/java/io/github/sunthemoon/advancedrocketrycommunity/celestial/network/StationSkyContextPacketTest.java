package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.Context;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.SkyKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 11.1, payload schema 2: explicit schema byte, strict flags and kind, an optional canonical body,
 * distance, phases, rates, epoch and sample, at most 256 bytes, trailing bytes rejected and no station identity.
 */
final class StationSkyContextPacketTest {
    private static final StationOrbitalControls EXTREME = new StationOrbitalControls(190, 359_999, 0, 123_456,
            -60, 60, 0, Long.MAX_VALUE - 5);

    @Test
    void noneEveryKindAndTheLongestIdRoundTripWithinTheByteBound() {
        String longest = ModIdentity.MOD_ID + ":" + "a".repeat(
                BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS - ModIdentity.MOD_ID.length() - 1);
        List<StationSkyContextPacket> packets = List.of(StationSkyContextPacket.NONE,
                packet(SkyKind.PLANETARY, Optional.of(ModIdentity.id("earth")), StationOrbitalControls.initial(270_000), 0),
                packet(SkyKind.SINGULARITY, Optional.of(ModIdentity.id("cygnus_x1")), EXTREME, Long.MAX_VALUE),
                packet(SkyKind.SPACE_FALLBACK, Optional.empty(), EXTREME, Long.MAX_VALUE - 5),
                packet(SkyKind.SPACE_FALLBACK, Optional.of(new ResourceLocation(longest)), EXTREME, Long.MAX_VALUE));
        int largest = 0;
        for (StationSkyContextPacket packet : packets) {
            FriendlyByteBuf buffer = encoded(packet);
            largest = Math.max(largest, buffer.readableBytes());
            assertTrue(buffer.readableBytes() <= StationSkyContextPacket.MAX_BYTES, "Encoded size " + buffer.readableBytes());
            assertEquals(packet, StationSkyContextPacket.decode(buffer));
        }
        assertEquals(StationSkyContextPacket.ENCODED_MAXIMUM, largest, "the longest body is the exact maximum");
        assertEquals(167, StationSkyContextPacket.ENCODED_MAXIMUM);
        assertEquals(2, encoded(StationSkyContextPacket.NONE).readableBytes(), "NONE is the schema and one flag");
        assertEquals(1 + 1 + 1 + 1 + 1 + ModIdentity.MOD_ID.length() + 6 + 2 + 12 + 3 + 16,
                encoded(packet(SkyKind.PLANETARY, Optional.of(ModIdentity.id("earth")),
                        StationOrbitalControls.initial(270_000), 9)).readableBytes());
    }

    @Test
    void theWireLayoutIsExactAndCarriesNoIdentity() {
        FriendlyByteBuf buffer = encoded(packet(SkyKind.PLANETARY, Optional.of(new ResourceLocation("a:b")),
                new StationOrbitalControls(179, 1, 2, 3, -4, 5, -6, 70L), 80L));
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        assertArrayEquals(new byte[] {2, 1, 1, 1, 3, 'a', ':', 'b', 0, (byte) 179, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3,
                -4, 5, -6, 0, 0, 0, 0, 0, 0, 0, 70, 0, 0, 0, 0, 0, 0, 0, 80}, bytes);
        for (var component : Context.class.getRecordComponents()) {
            assertFalse(component.getType() == UUID.class || component.getType() == String.class,
                    "The sky context must not carry a station identity, name or owner: " + component);
        }
        assertEquals(1, StationSkyContextPacket.class.getRecordComponents().length);
    }

    @Test
    void malformedRangesFlagsKindsBodiesAndTrailingInputAreRejected() {
        List<Consumer<FriendlyByteBuf>> invalid = List.of(
                buffer -> buffer.writeByte(1),
                buffer -> buffer.writeByte(3),
                buffer -> buffer.writeByte(2).writeByte(2),
                buffer -> context(buffer, 3, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, -1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 2, 4, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 2, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 191, 0, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 4, 360_000, 0, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 4, 0, -1, 0, 0, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 4, 0, 0, 0, 61, 0, 0, 0, 0),
                buffer -> context(buffer, 0, 0, 4, 0, 0, 0, 0, 0, -61, 0, 0),
                buffer -> context(buffer, 0, 0, 4, 0, 0, 0, 0, 0, 0, 10, 9),
                buffer -> context(buffer, 0, 0, 4, 0, 0, 0, 0, 0, 0, -1, 0),
                buffer -> context(buffer, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, -1),
                buffer -> {
                    buffer.writeByte(2).writeByte(1).writeByte(1).writeByte(1);
                    buffer.writeUtf("earth");
                },
                buffer -> {
                    buffer.writeByte(2).writeByte(1).writeByte(1).writeByte(1);
                    buffer.writeUtf("Not A Valid:ID");
                },
                buffer -> {
                    buffer.writeByte(2).writeByte(1).writeByte(1).writeByte(1);
                    buffer.writeUtf(ModIdentity.MOD_ID + ":" + "a".repeat(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS));
                },
                buffer -> {
                    StationSkyContextPacket.encode(StationSkyContextPacket.NONE, buffer);
                    buffer.writeByte(7);
                },
                buffer -> {
                    StationSkyContextPacket.encode(packet(SkyKind.SPACE_FALLBACK, Optional.empty(), EXTREME,
                            Long.MAX_VALUE), buffer);
                    buffer.writeByte(0);
                },
                buffer -> buffer.writeBytes(new byte[StationSkyContextPacket.MAX_BYTES + 1]));
        // Every deliberately rejected frame is a DecoderException, as before payload schema 2 (W07 review R5).
        for (int index = 0; index < invalid.size(); index++) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            invalid.get(index).accept(buffer);
            assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(buffer), "frame " + index);
        }
        FriendlyByteBuf trailing = encoded(StationSkyContextPacket.NONE);
        trailing.writeByte(0);
        assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(trailing));
        // A truncated frame is the one separate case: Netty's own bounds check ends it before any field check,
        // with its IndexOutOfBoundsException. Every proper prefix of the longest context is cut short this way.
        for (int length = 0; length < StationSkyContextPacket.ENCODED_MAXIMUM; length++) {
            FriendlyByteBuf full = encoded(packet(SkyKind.SPACE_FALLBACK, Optional.of(new ResourceLocation(
                    ModIdentity.MOD_ID + ":" + "a".repeat(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS
                            - ModIdentity.MOD_ID.length() - 1))), EXTREME, Long.MAX_VALUE));
            FriendlyByteBuf truncated = new FriendlyByteBuf(full.readBytes(length));
            assertThrows(IndexOutOfBoundsException.class, () -> StationSkyContextPacket.decode(truncated),
                    "truncated at " + length);
        }
        // The record itself refuses what the server must never build, including an overlong body (as before
        // schema 2, now on the Context that carries the body).
        String tooLong = ModIdentity.MOD_ID + ":" + "a".repeat(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
        for (SkyKind kind : SkyKind.values()) {
            assertThrows(IllegalArgumentException.class, () -> new Context(kind, Optional.of(new ResourceLocation(
                    tooLong)), StationOrbitalControls.initial(0), 0), kind.name());
        }
        assertThrows(IllegalArgumentException.class, () -> new Context(SkyKind.PLANETARY, Optional.empty(),
                StationOrbitalControls.initial(0), 0));
        assertThrows(IllegalArgumentException.class, () -> new Context(SkyKind.SPACE_FALLBACK, Optional.empty(),
                new StationOrbitalControls(4, 0, 0, 0, 0, 0, 0, 5L), 4L));
    }

    @Test
    void theClientExtrapolatesAtMostFortyTicksAndHoldsTheLastPhase() {
        Context context = new Context(SkyKind.PLANETARY, Optional.of(ModIdentity.id("earth")),
                new StationOrbitalControls(4, 0, 100, 200, 60, -1, 0, 1_000L), 1_020L);
        double[] atSample = StationSkyContextCache.phaseDegrees(context, 0, 0.0F);
        assertEquals(20 * 300 / 1_000.0D, atSample[0], 1e-9, "the sample itself is computed from the epoch");
        assertEquals((100 - 20 * 5) / 1_000.0D, atSample[1], 1e-9);
        assertEquals(0.2D, atSample[2], 1e-9);
        double[] ten = StationSkyContextCache.phaseDegrees(context, 10, 0.5F);
        assertEquals((20 + 10.5) * 300 / 1_000.0D, ten[0], 1e-9);
        assertEquals(Math.floorMod(100 - 30 * 5 - 3, 360_000) / 1_000.0D + 0.0005D, ten[1], 1e-9);
        double[] horizon = StationSkyContextCache.phaseDegrees(context, 40, 0.9F);
        double[] later = StationSkyContextCache.phaseDegrees(context, 4_000, 0.9F);
        assertArrayEquals(horizon, later, "past 40 ticks the last bounded phase is held");
        assertEquals(60 * 300 / 1_000.0D, horizon[0], 1e-9);
        // The sample plus the local ticks saturates instead of overflowing.
        Context exhausted = new Context(SkyKind.SPACE_FALLBACK, Optional.empty(),
                new StationOrbitalControls(4, 0, 0, 0, 60, 0, 0, Long.MAX_VALUE - 3), Long.MAX_VALUE - 1);
        double[] saturated = StationSkyContextCache.phaseDegrees(exhausted, 40, 0.5F);
        assertEquals(3 * 300 / 1_000.0D, saturated[0], 1e-9);
        for (double[] phases : List.of(atSample, ten, horizon, saturated)) {
            assertTrue(Arrays.stream(phases).allMatch(value -> value >= 0 && value < 360), Arrays.toString(phases));
        }
    }

    @Test
    void theClientCacheKeepsOnlyTheLastContextUntilNoneOrCleared() {
        StationSkyContextCache.clear();
        StationSkyContextCache.accept(packet(SkyKind.PLANETARY, Optional.of(ModIdentity.id("moon")),
                StationOrbitalControls.initial(270_000), 3));
        assertEquals(Optional.of(ModIdentity.id("moon")), StationSkyContextCache.orbitBody());
        assertTrue(StationSkyContextCache.phaseDegrees(0.0F).isPresent());
        StationSkyContextCache.accept(StationSkyContextPacket.NONE);
        assertEquals(Optional.empty(), StationSkyContextCache.orbitBody());
        assertTrue(StationSkyContextCache.context().isEmpty() && StationSkyContextCache.phaseDegrees(0.0F).isEmpty());
        StationSkyContextCache.accept(packet(SkyKind.PLANETARY, Optional.of(ModIdentity.id("mars")),
                StationOrbitalControls.initial(270_000), 3));
        StationSkyContextCache.clear();
        assertEquals(Optional.empty(), StationSkyContextCache.orbitBody());
        assertTrue(StationSkyContextCache.context().isEmpty());
    }

    private static void context(FriendlyByteBuf buffer, int kind, int bodyFlag, int distance, int east, int up,
                                int north, int eastRate, int upRate, int northRate, long epoch, long sample) {
        buffer.writeByte(2).writeByte(1).writeByte(kind).writeByte(bodyFlag);
        buffer.writeShort(distance).writeInt(east).writeInt(up).writeInt(north);
        buffer.writeByte(eastRate).writeByte(upRate).writeByte(northRate);
        buffer.writeLong(epoch).writeLong(sample);
    }

    private static StationSkyContextPacket packet(SkyKind kind, Optional<ResourceLocation> body,
                                                  StationOrbitalControls controls, long sample) {
        return new StationSkyContextPacket(Optional.of(new Context(kind, body, controls, sample)));
    }

    private static FriendlyByteBuf encoded(StationSkyContextPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        StationSkyContextPacket.encode(packet, buffer);
        return buffer;
    }
}
