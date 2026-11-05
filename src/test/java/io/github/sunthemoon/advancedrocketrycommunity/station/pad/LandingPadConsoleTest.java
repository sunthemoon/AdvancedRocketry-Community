package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180LandingPadData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightPlanPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LandingPadConsoleTest {
    private static final UUID STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174791");
    private static final LandingPadReference PAD = new LandingPadReference(new UUID(12, 1), 8);
    private static final RocketFlightPlanSnapshot PLAN = new RocketFlightPlanSnapshot(new TravelTarget.Station(STATION), PAD);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectedNameRoundTripsAndEveryTruncatedNamedFrameIsRejected() {
        for (String name : List.of("Venus Dock", "界".repeat(32), "")) {
            var packet = packet(PLAN, name);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RocketFlightPlanPacket.encode(packet, buffer);
                int size = buffer.readableBytes();
                assertTrue(size <= RocketFlightPlanPacket.MAX_ENCODED_BYTES);
                for (int length = 0; length < size; length++) {
                    var truncated = new FriendlyByteBuf(buffer.copy(0, length));
                    try { assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(truncated)); }
                    finally { truncated.release(); }
                }
                assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void malformedNamesAndNamesWithoutPadsCannotEnterTheDisplayFrame() {
        assertThrows(IllegalArgumentException.class, () -> packet(RocketFlightPlanSnapshot.empty(), "Dock"));
        for (String invalid : List.of("x".repeat(33), " trimmed ", "\n", "bad\u0000name")) {
            assertThrows(IllegalArgumentException.class, () -> packet(PLAN, invalid));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RocketFlightPlanPacket.encode(packet(PLAN, ""), buffer);
                // Replace the empty name's one-byte length with an invalid, untrusted S2C string.
                buffer.writerIndex(buffer.writerIndex() - 1);
                buffer.writeUtf(invalid);
                assertThrows(RuntimeException.class, () -> RocketFlightPlanPacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void namedRouteUsesTheSynchronizedNameInBothLocalesAndKeepsLegacyRoutesUnchanged() {
        var route = RocketFlightRoute.label(Component.literal("Venus"), Component.literal("Station"), PAD, "Venus Dock");
        var text = assertInstanceOf(TranslatableContents.class, route.getContents());
        assertEquals("screen.advancedrocketrycommunity.rocket.route_pad", text.getKey());
        assertEquals("Venus Dock", ((Component) text.getArgs()[0]).getString());
        for (boolean chinese : new boolean[]{false, true}) {
            String formatted = V180LandingPadData.language(chinese).get(text.getKey()).formatted(
                    ((Component) text.getArgs()[0]).getString(), ((Component) text.getArgs()[1]).getString(),
                    ((Component) text.getArgs()[2]).getString());
            assertTrue(formatted.contains("Venus Dock") && formatted.contains("Venus") && formatted.contains("Station"));
            assertTrue(formatted.indexOf("Venus Dock") < formatted.indexOf("Station"));
        }
        var unnamed = RocketFlightRoute.label(Component.literal("Venus"), Component.literal("Station"), PAD, "");
        var unknown = (Component) ((TranslatableContents) unnamed.getContents()).getArgs()[0];
        assertEquals("screen.advancedrocketrycommunity.rocket.pad_missing", ((TranslatableContents) unknown.getContents()).getKey());
        var legacy = RocketFlightRoute.label(Component.literal("Venus"), Component.literal("Moon"), null, "");
        assertEquals("screen.advancedrocketrycommunity.rocket.route", ((TranslatableContents) legacy.getContents()).getKey());
    }

    @Test void countdownDisplaysTheActivePadAndRevocationClearsTheEditablePad() {
        var selection = new RocketFlightSelection(List.of(new StationDestinationSummary(STATION, "Station")));
        selection.initialize(PLAN, new TravelTarget.BodySurface(ModIdentity.id("earth")));
        selection.select(new TravelTarget.BodySurface(ModIdentity.id("moon")));
        assertNull(selection.displayedPad(RocketFlightState.FUELED, PLAN));
        assertEquals(PAD, selection.displayedPad(RocketFlightState.COUNTDOWN, PLAN));
        selection.selectPad(STATION, PAD);
        selection.updateStations(List.of());
        assertNull(selection.displayedPad(RocketFlightState.FUELED, PLAN));
        assertEquals(PLAN, selection.target(RocketFlightAction.CANCEL, PLAN));
    }

    private static RocketFlightPlanPacket packet(RocketFlightPlanSnapshot plan, String name) {
        return new RocketFlightPlanPacket(7, 42, plan, RocketNavigation.empty(), name);
    }
}
