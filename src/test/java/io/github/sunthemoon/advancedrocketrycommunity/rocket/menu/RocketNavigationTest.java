package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightPlanPacket;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RocketNavigationTest {
    @Test
    void explicitStatusIdsAndAllPlannerResultsHaveFixedMeaning() {
        String[] meanings = {"READY", "CURRENT", "NO_ROUTE", "MISSING_COMPONENTS", "INSUFFICIENT_THRUST",
                "FUEL_STATE_MISMATCH", "INSUFFICIENT_CAPACITY", "INSUFFICIENT_FUEL", "INVALID_STATE",
                "UNAUTHORIZED", "UNAVAILABLE", "ARITHMETIC_OVERFLOW"};
        for (int index = 0; index < meanings.length; index++) {
            var status = RocketNavigationStatus.fromWire(index);
            assertEquals(meanings[index], status.name());
            assertEquals(index, status.wireId());
        }
        assertThrows(IllegalArgumentException.class, () -> RocketNavigationStatus.fromWire(-1));
        assertThrows(IllegalArgumentException.class, () -> RocketNavigationStatus.fromWire(12));
        for (var code : RocketFlightPlanCode.values()) {
            var status = RocketNavigationStatus.resolve(code, true, true);
            String expected = switch (code) {
                case SUCCESS -> "READY";
                case SAME_DESTINATION -> "CURRENT";
                case UNSUPPORTED_ROUTE -> "NO_ROUTE";
                case MISSING_FLIGHT_COMPONENTS -> "MISSING_COMPONENTS";
                default -> code.name();
            };
            assertEquals(expected, status.name());
            assertEquals(RocketNavigationStatus.UNAUTHORIZED, RocketNavigationStatus.resolve(code, false, false));
            assertEquals(RocketNavigationStatus.INVALID_STATE, RocketNavigationStatus.resolve(code, false, true));
        }
    }

    @Test
    void onlyReadyCanAdvertiseLaunchAndGenerationMustMatch() {
        for (var status : RocketNavigationStatus.values()) {
            assertEquals(status == RocketNavigationStatus.READY, new RocketFlightQuotes.Quote(42, status).canLaunch());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new RocketFlightQuotes.Quote(1, true, RocketNavigationStatus.NO_ROUTE));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlightQuotes.Quote(0, RocketNavigationStatus.READY));
        var navigation = new RocketNavigation(7, RocketFlightQuotes.empty(), List.of());
        assertTrue(navigation.coherent(7, true));
        assertFalse(navigation.coherent(6, true));
        assertFalse(navigation.coherent(7, false));
        assertFalse(RocketNavigation.empty().coherent(0, true));
        assertThrows(IllegalArgumentException.class, () -> new RocketNavigation(-1, RocketFlightQuotes.empty(), List.of()));
    }

    @Test
    void stationCountsIdsAndNamesRemainBounded() {
        var station = new RocketNavigation.Station(UUID.randomUUID(), "Station", body(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketNavigation(1, RocketFlightQuotes.empty(), List.of(station, station)));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketNavigation(1, RocketFlightQuotes.empty(), java.util.Collections.nCopies(33, station)));
        assertThrows(IllegalArgumentException.class, () -> new RocketNavigation.Station(UUID.randomUUID(), "x".repeat(49), body(0)));
        assertThrows(IllegalArgumentException.class, () -> new RocketNavigation.Station(new UUID(0, 0), "Invalid", body(0)));
        assertEquals(station.stationId(), station.summary().stationId());
    }

    @Test
    void maximumCatalogQuotesAndUnicodeStationNamesFitDeclaredFrame() {
        var entries = new ArrayList<RocketFlightQuotes.TargetQuote>();
        var stations = new ArrayList<RocketNavigation.Station>();
        for (int index = 0; index < 128; index++) {
            entries.add(new RocketFlightQuotes.TargetQuote(new TravelTarget.BodySurface(body(index)),
                    new RocketFlightQuotes.Quote(42, RocketNavigationStatus.READY)));
        }
        for (int index = 0; index < 32; index++) {
            UUID id = new UUID(0x4000L, 0x8000000000000000L | index + 1);
            stations.add(new RocketNavigation.Station(id, "\u677e".repeat(48), body(index)));
            entries.add(new RocketFlightQuotes.TargetQuote(new TravelTarget.Station(id),
                    new RocketFlightQuotes.Quote(42, RocketNavigationStatus.INSUFFICIENT_FUEL)));
        }
        var packet = new RocketFlightPlanPacket(Integer.MAX_VALUE, Integer.MAX_VALUE,
                new RocketFlightPlanSnapshot(new TravelTarget.BodySurface(body(0))),
                new RocketNavigation(Long.MAX_VALUE, new RocketFlightQuotes(entries), stations));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RocketFlightPlanPacket.encode(packet, buffer);
            assertEquals(31_419, RocketFlightPlanPacket.MAX_ENCODED_BYTES);
            assertTrue(buffer.readableBytes() <= RocketFlightPlanPacket.MAX_ENCODED_BYTES);
            assertTrue(buffer.readableBytes() < 32 * 1024);
            assertEquals(packet, RocketFlightPlanPacket.decode(buffer));
        } finally { buffer.release(); }
    }

    private static ResourceLocation body(int index) {
        return new ResourceLocation("n", "b".repeat(122) + String.format(Locale.ROOT, "%04d", index));
    }
}
