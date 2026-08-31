package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotCodec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialSnapshotTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @AfterEach
    void clearClientCache() {
        CelestialClientCache.clear();
    }

    @Test
    void canonicalSnapshotRoundTripsWithinTightSizeBudget() {
        CelestialSnapshotPacket packet = CelestialSnapshotPacket.fromCatalog(canonicalCatalog(), 7L)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });

        assertTrue(packet.payload().length < 1_024, "Three-body snapshot should remain below 1 KiB");
        assertEquals(CelestialClientCache.AcceptResult.ACCEPTED, CelestialClientCache.accept(packet));
        CelestialSnapshot snapshot = CelestialClientCache.snapshot().orElseThrow();

        assertEquals(7L, CelestialClientCache.generation());
        assertEquals(3, snapshot.entries().size());
        assertEquals(CelestialIds.EARTH_ID, snapshot.entries().get(0).bodyId());
        assertTrue(snapshot.entries().get(1).vacuum());
    }

    @Test
    void packetEnvelopeRoundTripsWithExplicitDirectionPayload() {
        CelestialSnapshotPacket original = CelestialSnapshotPacket.fromCatalog(canonicalCatalog(), 9L)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CelestialSnapshotPacket.encode(original, buffer);
            CelestialSnapshotPacket decoded = CelestialSnapshotPacket.decode(buffer);

            assertEquals(original.schemaVersion(), decoded.schemaVersion());
            assertEquals(original.catalogGeneration(), decoded.catalogGeneration());
            assertTrue(java.util.Arrays.equals(original.payload(), decoded.payload()));
        } finally {
            buffer.release();
        }
    }

    @Test
    void futureSchemaRetainsLastValidSnapshot() {
        CelestialSnapshotPacket accepted = CelestialSnapshotPacket.fromCatalog(canonicalCatalog(), 3L)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        assertEquals(CelestialClientCache.AcceptResult.ACCEPTED, CelestialClientCache.accept(accepted));
        CelestialSnapshot previous = CelestialClientCache.snapshot().orElseThrow();

        CelestialSnapshotPacket future = new CelestialSnapshotPacket(99, 4L, new byte[] {1, 2, 3});
        assertEquals(
                CelestialClientCache.AcceptResult.UNSUPPORTED_SCHEMA,
                CelestialClientCache.accept(future)
        );

        assertTrue(previous == CelestialClientCache.snapshot().orElseThrow());
        assertEquals(3L, CelestialClientCache.generation());
    }

    @Test
    void malformedPayloadRetainsLastValidSnapshot() {
        CelestialSnapshotPacket accepted = CelestialSnapshotPacket.fromCatalog(canonicalCatalog(), 5L)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        CelestialClientCache.accept(accepted);
        CelestialSnapshot previous = CelestialClientCache.snapshot().orElseThrow();

        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        byte[] payload;
        try {
            malformed.writeVarInt(CelestialCatalog.MAX_BODIES + 1);
            payload = new byte[malformed.readableBytes()];
            malformed.getBytes(0, payload);
        } finally {
            malformed.release();
        }

        assertEquals(
                CelestialClientCache.AcceptResult.INVALID_PAYLOAD,
                CelestialClientCache.accept(new CelestialSnapshotPacket(1, 6L, payload))
        );
        assertTrue(previous == CelestialClientCache.snapshot().orElseThrow());
        assertEquals(5L, CelestialClientCache.generation());
    }

    private static CelestialCatalog canonicalCatalog() {
        return CelestialCatalog.create(CelestialDefaults.definitions())
                .flatMap(CelestialCatalog::requireFixedBaseline)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
    }
}
