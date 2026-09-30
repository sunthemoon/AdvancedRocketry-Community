package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-047: the station sky context carries a bounded body ID or nothing, and nothing else. */
final class StationSkyContextPacketTest {
    @Test
    void noneAnOrbitAndTheLongestIdRoundTripWithinTheByteBound() {
        String longest = ModIdentity.MOD_ID + ":" + "a".repeat(
                BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS - ModIdentity.MOD_ID.length() - 1);
        for (StationSkyContextPacket packet : new StationSkyContextPacket[]{StationSkyContextPacket.NONE,
                new StationSkyContextPacket(Optional.of(ModIdentity.id("earth"))),
                new StationSkyContextPacket(Optional.of(new ResourceLocation(longest)))}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            StationSkyContextPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() <= StationSkyContextPacket.MAX_BYTES, "Encoded size " + buffer.readableBytes());
            assertEquals(packet, StationSkyContextPacket.decode(buffer));
        }
        assertEquals(1 + 1 + ModIdentity.MOD_ID.length() + 6,
                encoded(new StationSkyContextPacket(Optional.of(ModIdentity.id("earth")))).readableBytes(),
                "Only a presence flag and the ID are encoded");
    }

    @Test
    void oversizedInvalidAndTrailingInputIsRejected() {
        String tooLong = ModIdentity.MOD_ID + ":" + "a".repeat(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
        assertThrows(IllegalArgumentException.class,
                () -> new StationSkyContextPacket(Optional.of(new ResourceLocation(tooLong))));
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeBoolean(true);
        oversized.writeUtf(tooLong);
        assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(oversized));
        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        invalid.writeBoolean(true);
        invalid.writeUtf("Not A Valid:ID");
        assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(invalid));
        FriendlyByteBuf trailing = encoded(StationSkyContextPacket.NONE);
        trailing.writeByte(7);
        assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(trailing));
        FriendlyByteBuf notBoolean = new FriendlyByteBuf(Unpooled.buffer());
        notBoolean.writeByte(2);
        assertThrows(DecoderException.class, () -> StationSkyContextPacket.decode(notBoolean));
        FriendlyByteBuf missing = new FriendlyByteBuf(Unpooled.buffer());
        missing.writeBoolean(true);
        assertThrows(RuntimeException.class, () -> StationSkyContextPacket.decode(missing));
    }

    @Test
    void theClientCacheKeepsOnlyTheLastContextUntilCleared() {
        StationSkyContextCache.clear();
        StationSkyContextCache.accept(new StationSkyContextPacket(Optional.of(ModIdentity.id("moon"))));
        assertEquals(Optional.of(ModIdentity.id("moon")), StationSkyContextCache.orbitBody());
        StationSkyContextCache.accept(StationSkyContextPacket.NONE);
        assertEquals(Optional.empty(), StationSkyContextCache.orbitBody());
        StationSkyContextCache.accept(new StationSkyContextPacket(Optional.of(ModIdentity.id("mars"))));
        StationSkyContextCache.clear();
        assertEquals(Optional.empty(), StationSkyContextCache.orbitBody());
    }

    private static FriendlyByteBuf encoded(StationSkyContextPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        StationSkyContextPacket.encode(packet, buffer);
        return buffer;
    }
}
