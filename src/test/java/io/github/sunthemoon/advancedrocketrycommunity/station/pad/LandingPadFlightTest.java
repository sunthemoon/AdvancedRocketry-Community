package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.*;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LandingPadFlightTest {
    private static final UUID STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174713");
    private static final LandingPadReference PAD = new LandingPadReference(new UUID(2, 2), 7);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void namedPadIntentsRoundTripAndEveryTruncationRejects() {
        var packet = new RocketFlightIntentPacket(RocketFlightAction.LAUNCH, Integer.MAX_VALUE,
                new TravelTarget.Station(STATION), UUID.randomUUID(), PAD);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer); int size = buffer.readableBytes();
            for (int i = 0; i < size; i++) {
                var truncated = new FriendlyByteBuf(buffer.copy(0, i));
                try { assertThrows(RuntimeException.class, () -> RocketFlightIntentPacket.decode(truncated)); }
                finally { truncated.release(); }
            }
            assertEquals(packet, RocketFlightIntentPacket.decode(buffer));
            buffer.clear(); packet.encode(buffer); buffer.setByte(size - 25, 2);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(buffer));
            buffer.clear(); packet.encode(buffer); buffer.setLong(size - 8, -1);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(buffer));
            buffer.clear(); packet.encode(buffer); buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> RocketFlightIntentPacket.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightIntentPacket(RocketFlightAction.LAUNCH, 1,
                new TravelTarget.BodySurface(ModIdentity.id("earth")), UUID.randomUUID(), PAD));
    }
    @Test void maximumQuotesAndStationNamesCoexistWithSelectedPad() {
        var quotes = new java.util.ArrayList<RocketFlightQuotes.TargetQuote>();
        var stations = new java.util.ArrayList<RocketNavigation.Station>();
        for (int i = 0; i < 160; i++) {
            var id = net.minecraft.resources.ResourceLocation.tryBuild("a", String.format("%03d", i) + "a".repeat(123));
            quotes.add(new RocketFlightQuotes.TargetQuote(new TravelTarget.BodySurface(id), new RocketFlightQuotes.Quote(1, RocketNavigationStatus.READY)));
        }
        for (int i = 0; i < 32; i++) {
            stations.add(new RocketNavigation.Station(UUID.nameUUIDFromBytes(("station-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "界".repeat(48),
                    net.minecraft.resources.ResourceLocation.tryBuild("a", "a".repeat(126))));
        }
        var packet = new RocketFlightPlanPacket(1, 1, new RocketFlightPlanSnapshot(new TravelTarget.Station(STATION), PAD),
                new RocketNavigation(1, new RocketFlightQuotes(quotes), stations));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RocketFlightPlanPacket.encode(packet, buffer);
            assertTrue(buffer.readableBytes() <= RocketFlightPlanPacket.MAX_ENCODED_BYTES);
            assertTrue(buffer.readableBytes() <= 32768);
            assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void namedPlanPersistsItsStationAndEpochAndLegacyPlanRemainsUnchanged() {
        var legacy = new RocketFlightPlan(3, UUID.randomUUID(), ModIdentity.id("earth"), ModIdentity.id("earth"),
                net.minecraft.world.level.Level.OVERWORLD.location(), ModIdentity.id("space"), STATION, 100, 0,
                new TravelTarget.Station(STATION));
        var plan = legacy.withLandingPad(PAD);
        assertEquals(5, plan.schemaVersion()); assertNull(legacy.landingPad());
        var flight = RocketFlightData.initial(UUID.randomUUID(), 1000, 1, ModIdentity.id("earth"),
                net.minecraft.world.level.Level.OVERWORLD.location(), new RocketPosition(0, 80, 0), 0);
        flight = flight.withFuel(flight.fuel().fill(1000).state(), 0).withPlan(plan).startCountdown(0);
        var tag = RocketFlightNbtCodec.encode(flight);
        assertEquals(flight, RocketFlightNbtCodec.decode(tag).data().orElseThrow());
        var debit = new RocketPropulsionDebit(100, 20, 7, "a".repeat(64), "b".repeat(64));
        var typed = plan.withPropulsionDebit(debit);
        assertEquals(6, typed.schemaVersion());
        assertEquals(typed, legacy.withPropulsionDebit(debit).withLandingPad(PAD));
        // Exercise the schema-6 codec independently of resource-bank debit admission.
        var typedTag = tag.copy(); typedTag.getCompound("plan").putInt("schema_version", 6);
        typedTag.getCompound("plan").put("propulsion_debit",
                io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPropulsionFuelCodec.encodeDebit(debit));
        assertEquals(typed, RocketFlightNbtCodec.decode(typedTag).data().orElseThrow().plan().orElseThrow());
        typedTag.getCompound("plan").remove("landing_pad");
        assertEquals(RocketFlightDecodeResult.Status.INVALID, RocketFlightNbtCodec.decode(typedTag).status());
        var invalid = tag.copy(); invalid.getCompound("plan").getCompound("landing_pad").putLong("epoch", -1);
        assertEquals(RocketFlightDecodeResult.Status.INVALID, RocketFlightNbtCodec.decode(invalid).status());
        invalid = tag.copy(); invalid.getCompound("plan").putInt("schema_version", 7);
        assertEquals(RocketFlightDecodeResult.Status.INVALID, RocketFlightNbtCodec.decode(invalid).status());
    }
    @Test void cancellationRetainsActivePadWhileNewDestinationClearsIt() {
        var target = new TravelTarget.Station(STATION);
        var selection = new RocketFlightSelection(List.of(new StationDestinationSummary(STATION, "Station")));
        selection.selectPad(STATION, PAD);
        assertEquals(PAD, selection.target(RocketFlightAction.LAUNCH, RocketFlightPlanSnapshot.empty()).landingPad());
        var active = new RocketFlightPlanSnapshot(target, PAD);
        selection.select(RocketDestination.MOON);
        assertEquals(active, selection.target(RocketFlightAction.CANCEL, active));
        assertNull(selection.target(RocketFlightAction.LAUNCH, active).landingPad());
    }
    @Test void serverListedPadChoiceInitializesConsoleOnceWithoutOverridingLaterEdits() {
        var selection = new RocketFlightSelection(List.of(new StationDestinationSummary(STATION, "Station")));
        var chosen = new RocketFlightPlanSnapshot(new TravelTarget.Station(STATION), PAD);
        selection.initialize(chosen, new TravelTarget.BodySurface(ModIdentity.id("earth")));
        assertEquals(chosen, selection.target(RocketFlightAction.LAUNCH, chosen));
        selection.select(RocketDestination.MOON);
        selection.initialize(chosen, new TravelTarget.BodySurface(ModIdentity.id("earth")));
        assertNull(selection.target(RocketFlightAction.LAUNCH, chosen).landingPad());
        assertEquals(RocketDestination.MOON, selection.selected());
    }
}
