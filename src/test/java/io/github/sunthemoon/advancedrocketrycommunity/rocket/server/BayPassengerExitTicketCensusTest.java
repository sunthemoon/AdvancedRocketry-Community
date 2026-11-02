package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W266: the gated reporter drops only vanilla UNKNOWN, not timed tickets or identity fields. */
final class BayPassengerExitTicketCensusTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static Field field(String name) throws Exception {
        var field = Ticket.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static JsonArray identities(Map<Long, List<Ticket<?>>> map) throws Exception {
        String census = BayPassengerExitReleaseDrive.ticketCensus(map, field("key"));
        String encoded = census.substring(census.indexOf(" ticketIdentities=") + " ticketIdentities=".length());
        return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonArray();
    }

    private static Ticket<String> custom(String name, int level, String key, boolean force, int timeout) {
        return new Ticket<>(TicketType.create(name, Comparator.<String>naturalOrder(), timeout), level, key, force);
    }

    @Test void onlyTheActualVanillaUnknownWithTimeoutOneIsExcluded() throws Exception {
        assertEquals(1, TicketType.UNKNOWN.timeout());
        var unknown = new Ticket<>(TicketType.UNKNOWN, 31, new ChunkPos(0, 0), false);
        var player = new Ticket<>(TicketType.PLAYER, 31, new ChunkPos(0, 0), false);
        assertEquals(identities(Map.of(0L, List.of(player))), identities(Map.of(0L, List.of(player, unknown, unknown))));
        var namedUnknown = custom("unknown", 31, "custom key", false, 1);
        assertEquals(1, identities(Map.of(0L, List.of(unknown, namedUnknown))).size());
        String report = BayPassengerExitReleaseDrive.ticketCensus(Map.of(0L, List.of(player, unknown, unknown)), field("key"));
        assertTrue(report.startsWith("heldTickets=1 timedTickets=2 ticketTypes=player:1,unknown:2 "));
    }

    @Test void portalPostTeleportFlightAndCustomTimedTicketsAreAllReported() throws Exception {
        List<Ticket<?>> tickets = List.of(new Ticket<>(TicketType.PORTAL, 30, net.minecraft.core.BlockPos.ZERO, false),
                new Ticket<>(TicketType.POST_TELEPORT, 33, 42, false),
                custom("arce_rocket_flight", 31, "rocket", false, 40), custom("custom_timed", 31, "custom key", false, 7));
        var rows = identities(Map.of(0L, tickets));
        assertEquals(4, rows.size());
        for (Ticket<?> ticket : tickets) {
            assertTrue(ticket.getType().timeout() > 0);
            assertTrue(rows.asList().stream().anyMatch(row -> row.getAsJsonArray().get(1).getAsString().equals(ticket.getType().toString())));
        }
    }

    @Test void chunkTypeLevelKeyAndForceFlagRemainDistinct() throws Exception {
        var ticket = custom("test", 31, "key with spaces", false, 7);
        var expected = identities(Map.of(0L, List.of(ticket)));
        assertEquals("key with spaces", expected.get(0).getAsJsonArray().get(3).getAsString());
        assertNotEquals(expected, identities(Map.of(1L, List.of(ticket))));
        for (var changed : List.of(custom("other", 31, "key with spaces", false, 7), custom("test", 32, "key with spaces", false, 7),
                custom("test", 31, "another key", false, 7), custom("test", 31, "key with spaces", true, 7))) {
            assertNotEquals(expected, identities(Map.of(0L, List.of(changed))));
        }
    }

    @Test void refreshingACreatedTickChangesTheRawMapButNotTheIdentity() throws Exception {
        var ticket = custom("custom_timed", 31, "key", false, 7);
        var before = identities(Map.of(0L, List.of(ticket)));
        String raw = ticket.toString();
        field("createdTick").setLong(ticket, 200L);
        assertNotEquals(raw, ticket.toString());
        assertEquals(before, identities(Map.of(0L, List.of(ticket))));
    }

    @Test void sortingPreservesMultiplicityAndDoesNotDependOnIterationOrder() throws Exception {
        var first = custom("first", 31, "key", false, 7);
        var second = custom("second", 31, "key", false, 7);
        assertEquals(identities(Map.of(0L, List.of(first, second))), identities(Map.of(0L, List.of(second, first))));
        assertEquals(2, identities(Map.of(0L, List.of(first, first))).size());
        assertNotEquals(identities(Map.of(0L, List.of(first))), identities(Map.of(0L, List.of(first, first))));
    }

    @Test void anEmptyMapHasAnExplicitEmptyObservation() throws Exception {
        assertTrue(identities(Map.of()).isEmpty());
    }
}
