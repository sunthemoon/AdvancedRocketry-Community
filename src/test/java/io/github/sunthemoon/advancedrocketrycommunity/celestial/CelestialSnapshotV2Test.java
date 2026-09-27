package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotCodec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialSnapshotV2Test {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @AfterEach
    void clear() {
        CelestialClientCache.clear();
    }

    @Test
    void logicalBodyCarriesAllConfiguredEnvironmentAndCapabilities() {
        var values = new ArrayList<>(CelestialDefaults.definitions());
        var gas = CelestialSchemaV2Test.gasGiant();
        values.add(gas);
        var packet = packet(values, 12);
        assertEquals("2", CelestialNetwork.protocolVersion());
        assertEquals(2, packet.schemaVersion());
        assertEquals(CelestialClientCache.AcceptResult.ACCEPTED, CelestialClientCache.accept(packet));
        var entry = CelestialClientCache.snapshot().orElseThrow().entries().stream()
                .filter(body -> body.bodyId().equals(gas.id())).findFirst().orElseThrow();
        assertTrue(entry.levelId().isEmpty());
        assertEquals(gas.parentId(), entry.parentId());
        assertEquals(gas.gravityMultiplier(), entry.gravityMultiplier());
        assertEquals(gas.capabilities(), entry.capabilities());
        assertEquals(gas.atmosphere().pressure(), entry.pressure());
        assertEquals(gas.atmosphere().breathable(), entry.breathable());
        assertEquals(gas.atmosphere().temperatureKelvin(), entry.temperatureKelvin());
        assertEquals(gas.atmosphere().profile(), entry.atmosphereProfile());
        assertEquals(gas.visualProfile(), entry.visualProfile());
        assertEquals(gas.solarIntensity(), entry.solarIntensity());
        assertEquals(gas.radiation(), entry.radiation());
        assertEquals(12, CelestialClientCache.generation());
    }

    @Test
    void maximumCountAndIdentifierLengthsFitTheUnchangedPayloadBudget() {
        var bodies = new ArrayList<CelestialBodyDefinition>();
        var root = longId("root");
        for (int i = 0; i < CelestialCatalog.MAX_BODIES; i++) {
            bodies.add(new CelestialBodyDefinition(i == 0 ? root : longId("body" + i),
                    i == 0 ? Optional.empty() : Optional.of(root),
                    Optional.of(ResourceKey.create(Registries.DIMENSION, longId("level" + i))), 4,
                    new AtmosphereDefinition(10, true, 2000, longId("atmosphere")),
                    new OrbitDefinition(i == 0 ? 0 : 1_000_000_000L, i == 0 ? 0 : 10_000_000_000L, 180),
                    longId("visual"), new CelestialCapabilities(true, true, false), 16, 1));
        }
        var packet = packet(bodies, 1);
        assertTrue(packet.payload().length <= 96 * 1024);
        var snapshot = CelestialSnapshotCodec.decode(packet.payload()).result().orElseThrow();
        assertEquals(128, snapshot.entries().size());
        for (var entry : snapshot.entries()) {
            assertEquals(128, entry.bodyId().toString().length());
            assertEquals(128, entry.levelId().orElseThrow().toString().length());
            assertEquals(16, entry.solarIntensity());
            assertEquals(1, entry.radiation());
        }
    }

    @Test
    void previousSchemaAndMalformedCurrentFramesRetainTheLastGoodCache() {
        var good = packet(CelestialDefaults.definitions(), 4);
        CelestialClientCache.accept(good);
        var previous = CelestialClientCache.snapshot().orElseThrow();
        assertEquals(CelestialClientCache.AcceptResult.UNSUPPORTED_SCHEMA,
                CelestialClientCache.accept(new CelestialSnapshotPacket(1, 5, good.payload())));
        for (byte[] bad : new byte[][] {new byte[0], Arrays.copyOf(good.payload(), 8),
                Arrays.copyOf(good.payload(), good.payload().length + 1),
                bytes(buffer -> buffer.writeVarInt(-1)), bytes(buffer -> buffer.writeVarInt(129)),
                bytes(buffer -> { buffer.writeVarInt(1); buffer.writeUtf("t:root"); buffer.writeByte(2); })}) {
            assertEquals(CelestialClientCache.AcceptResult.INVALID_PAYLOAD,
                    CelestialClientCache.accept(new CelestialSnapshotPacket(2, 5, bad)));
            assertSame(previous, CelestialClientCache.snapshot().orElseThrow());
            assertEquals(4, CelestialClientCache.generation());
        }
        assertTrue(CelestialSnapshotCodec.decode(new byte[CelestialSnapshotCodec.MAX_PACKET_BYTES + 1]).error().isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialSnapshotPacket(2, 1, new byte[CelestialSnapshotCodec.MAX_PACKET_BYTES + 1]));
    }

    @Test
    void invalidCapabilitiesAndEnvironmentAreRejectedByTheWireDecoder() {
        for (int scenario = 0; scenario < 4; scenario++) {
            int invalid = scenario;
            byte[] payload = bytes(buffer -> {
                buffer.writeVarInt(1);
                buffer.writeUtf("t:root");
                buffer.writeBoolean(false);
                buffer.writeBoolean(false);
                buffer.writeDouble(invalid == 0 ? Double.NaN : 1);
                buffer.writeDouble(0);
                buffer.writeBoolean(invalid == 1);
                buffer.writeDouble(3);
                buffer.writeUtf("t:vacuum");
                buffer.writeUtf("t:visual");
                buffer.writeBoolean(invalid == 2);
                buffer.writeBoolean(true);
                buffer.writeBoolean(false);
                buffer.writeDouble(1);
                buffer.writeDouble(invalid == 3 ? 1.1 : 0);
            });
            assertTrue(CelestialSnapshotCodec.decode(payload).error().isPresent());
        }
    }

    @Test
    void envelopeRejectsTrailingBytesAndOversizedLengthPrefixes() {
        var packet = packet(CelestialDefaults.definitions(), 1);
        for (byte[] payload : new byte[][] {
                bytes(buffer -> { CelestialSnapshotPacket.encode(packet, buffer); buffer.writeByte(0); }),
                bytes(buffer -> { buffer.writeVarInt(2); buffer.writeVarLong(1); buffer.writeVarInt(CelestialSnapshotCodec.MAX_PACKET_BYTES + 1); })}) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            try {
                assertThrows(RuntimeException.class, () -> CelestialSnapshotPacket.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void replacementAndDisconnectClearOldBodiesAndGeneration() {
        var values = new ArrayList<>(CelestialDefaults.definitions());
        values.add(CelestialSchemaV2Test.gasGiant());
        CelestialClientCache.accept(packet(values, 1));
        assertEquals(4, CelestialClientCache.snapshot().orElseThrow().entries().size());
        CelestialClientCache.accept(packet(CelestialDefaults.definitions(), 2));
        assertEquals(3, CelestialClientCache.snapshot().orElseThrow().entries().size());
        assertEquals(2, CelestialClientCache.generation());
        CelestialClientCache.clear();
        assertTrue(CelestialClientCache.snapshot().isEmpty());
        assertEquals(0, CelestialClientCache.generation());
    }

    private static CelestialSnapshotPacket packet(java.util.List<CelestialBodyDefinition> bodies, long generation) {
        var catalog = CelestialCatalog.create(bodies).result().orElseThrow();
        return CelestialSnapshotPacket.fromCatalog(catalog, generation).result().orElseThrow();
    }

    private static ResourceLocation longId(String prefix) {
        return ResourceLocation.tryParse("t:" + prefix + "x".repeat(126 - prefix.length()));
    }

    private static byte[] bytes(Consumer<FriendlyByteBuf> writer) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.getBytes(0, result);
            return result;
        } finally {
            buffer.release();
        }
    }
}
