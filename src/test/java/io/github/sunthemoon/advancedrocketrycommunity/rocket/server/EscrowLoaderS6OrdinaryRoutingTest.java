package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** R204: exercise the unchanged launch router, not a second native flight or a player-intent simulation. */
class EscrowLoaderS6OrdinaryRoutingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void settledLoaderFundedArrivalStillSelectsGovernedOnAllThreeRetainedImages() throws Exception {
        for (var image : images()) {
            var fixture = fixture(image, true);
            var before = image.copy();
            var countdown = countdown(fixture.flight());
            var result = RocketGovernedAdmission.check(fixture.server(), fixture.rocket(), countdown,
                    new RocketDestinationWarmups());
            assertTrue(result.governed(), "Epoch zero and retired authority do not remove the ledger binding");
            assertNull(result.refusal(), "A ready bound source is not a blanket launch refusal");
            assertNull(result.kept());
            assertTrue(fixture.level().reads > 0, "The actual governed source-readiness branch must run");
            unchanged(before, fixture);
        }
    }

    @Test void unavailableBoundSourceRefusesWithoutFallingBackToOrdinary() throws Exception {
        for (var image : images()) {
            var fixture = fixture(image, false);
            var before = image.copy();
            var result = RocketGovernedAdmission.check(fixture.server(), fixture.rocket(), countdown(fixture.flight()),
                    new RocketDestinationWarmups());
            assertTrue(result.governed());
            assertEquals(RocketFlightRequestCode.LAUNCH_AREA_INACTIVE, result.refusal().code());
            assertNull(result.kept());
            unchanged(before, fixture);
        }
    }

    @Test void independentNeverBoundTypedRocketUsesOrdinaryRouting() throws Exception {
        var nativeImage = images().get(0);
        var descriptor = RocketSnapshotNbtCodec.decode(nativeImage.getCompound("snapshot")).snapshot().orElseThrow()
                .propulsion().orElseThrow();
        var fuel = RocketFuelState.empty(descriptor).withFluidBank(false,
                net.minecraft.resources.ResourceLocation.tryParse("advancedrocketrycommunity:rocket_fuel"), null, 500);
        var fresh = RocketFlightData.initial(UUID.randomUUID(), 2_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                Level.OVERWORLD.location(), new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(80, 100, 64), 0)
                .withFuel(fuel, 0);
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        // Independent unstarted object, never a mutation of the loader-funded native source.
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
        var result = RocketGovernedAdmission.check(null, rocket, countdown(fresh), new RocketDestinationWarmups());
        assertFalse(result.governed());
        assertNull(result.refusal());
        assertNull(result.kept());
        assertEquals(RocketEscrowSection.neutral(), rocket.escrowSection());
        assertEquals(500, fresh.fuel().amount());
        assertTrue(fresh.fuel().committedDebits().isEmpty());
        // No server or snapshot was supplied: ordinary routing must not query governed readiness.
    }

    private static RocketFlightData countdown(RocketFlightData flight) {
        assertTrue(flight.launchReady());
        var fuel = flight.fuel();
        long now = flight.stateStartedGameTime() + 1;
        var destination = flight.currentBody().equals(RocketFlightPlanner.EARTH.bodyId())
                ? RocketFlightPlanner.MOON.bodyId() : RocketFlightPlanner.EARTH.bodyId();
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), flight.currentBody(), destination,
                flight.currentDimension(), flight.currentDimension(), 250, now)
                .withPropulsionDebit(new RocketPropulsionDebit(250, 0, fuel.propulsion().orElseThrow().revision(),
                        fuel.resourceSignature(), RocketNativeResources.hash("W211 routing unit input")));
        var countdown = flight.withFuel(fuel, now).withPlan(plan).startCountdown(now);
        assertEquals(fuel, countdown.fuel(), "Planning cannot consume the prospective second debit");
        return countdown;
    }

    private static Fixture fixture(CompoundTag image, boolean ready) {
        var snapshot = RocketSnapshotNbtCodec.decode(image.getCompound("snapshot")).snapshot().orElseThrow();
        var flight = RocketFlightNbtCodec.decode(image.getCompound("flight")).data().orElseThrow();
        var section = RocketEscrowSection.decode(image.getCompound("wrapper"));
        assertEquals(RocketFlightState.LANDED, flight.state());
        assertEquals(500, flight.fuel().amount());
        assertEquals(2, flight.fuel().propulsion().orElseThrow().revision());
        assertEquals(1, flight.fuel().committedDebits().size());
        assertEquals(0, section.epoch());
        assertTrue(section.markers().isEmpty());
        assertNotNull(section.ledger());
        assertTrue(flight.plan().isEmpty());
        assertTrue(flight.activeTransferId().isEmpty());
        var server = UnstartedServer.owned();
        var level = UnstartedServer.allocate(ProbeLevel.class);
        level.ready = ready;
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        // A0 fixture hydration only. No live entity, lifecycle, recovery or product method is overridden.
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", snapshot);
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", flight);
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", section);
        return new Fixture(server, level, rocket, flight, snapshot);
    }

    private static void unchanged(CompoundTag before, Fixture fixture) {
        assertEquals(before.getCompound("snapshot"), RocketSnapshotNbtCodec.encode(fixture.rocket().snapshot().orElseThrow()));
        assertEquals(before.getCompound("flight"), RocketFlightNbtCodec.encode(fixture.rocket().flightData().orElseThrow()));
        assertEquals(before.getCompound("wrapper"), fixture.rocket().escrowSection().encode());
    }

    private static List<CompoundTag> images() throws Exception {
        try (var input = EscrowLoaderS6OrdinaryRoutingTest.class.getResourceAsStream("/loader-s6-settled-routing.json")) {
            assertNotNull(input);
            var document = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("PASS", document.getAsJsonObject("provenance").get("historical_status").getAsString());
            var result = new ArrayList<CompoundTag>();
            for (var entry : document.getAsJsonArray("images")) {
                var row = entry.getAsJsonObject();
                assertEquals(result.size(), row.get("restart").getAsInt());
                byte[] bytes = Base64.getDecoder().decode(row.get("nbt_base64").getAsString());
                assertEquals(row.get("nbt_sha256").getAsString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                try (var stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    result.add(NbtIo.read(stream));
                    assertEquals(0, stream.available());
                }
            }
            assertEquals(3, result.size());
            assertEquals(result.get(0), result.get(1));
            assertEquals(result.get(1), result.get(2));
            return result;
        }
    }

    private record Fixture(MinecraftServer server, ProbeLevel level, RocketEntity rocket,
                           RocketFlightData flight, RocketStructureSnapshot snapshot) { }

    static final class ProbeLevel extends ServerLevel {
        boolean ready;
        int reads;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public boolean areEntitiesLoaded(long chunk) { reads++; return ready; }
        @Override public boolean isPositionEntityTicking(BlockPos pos) { reads++; return ready; }
        @Override public LevelChunk getChunk(int x, int z) { throw new AssertionError("Routing must not load a chunk"); }
    }
}
