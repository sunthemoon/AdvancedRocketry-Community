package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketI6PreparationPacingTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sixtyFourColdDemandsTicketAtMostOneRecordedPairPerTickWithoutSpendingDeferredTurns() {
        var journal = new RocketTransferSavedData();
        var records = new ArrayList<RocketTransferRecord>();
        for (int i = 0; i < 64; i++) {
            var record = RocketStationMotionRuleTest.record(new RocketPosition(1000 + i * 64, 80, 1000))
                    .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            records.add(record); journal.put(record);
        }
        var type = ManagedSavedDataType.ROCKET_TRANSFERS;
        RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), 1000);
        long[] now = {100};
        Map<UUID, List<Long>> tickets = new LinkedHashMap<>();
        int[] discarded = {0};
        var port = (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {RocketOrdinaryRetirement.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "now" -> now[0];
                    case "requestCurrent", "preparationOnly", "needsTicketStep", "captureSupported", "ticketReady", "escrowAllows" -> true;
                    case "preparationReady", "saveOff", "bayClaimed" -> false;
                    case "survivor" -> RocketOrdinaryRetirement.Presence.LOADED;
                    case "keepLoaded" -> {
                        var record = (RocketTransferRecord) args[0];
                        tickets.computeIfAbsent(record.transferId(), ignored -> new ArrayList<>()).add(now[0]); yield null;
                    }
                    case "releaseTickets" -> null;
                    default -> throw new AssertionError("Preparation reached unexpected method " + method.getName());
                });
        var coordinator = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, ignored -> port);
        for (var record : records) {
            try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
                coordinator.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_RELEASE, record,
                        record.destinationEntityId().orElseThrow(), List.of(), false, held,
                        new RocketOrdinaryRetirement.Effects(() -> { fail("Cold demand became ready"); return false; }, () -> discarded[0]++));
            }
        }
        assertEquals(64, coordinator.size()); assertTrue(tickets.isEmpty());
        for (; now[0] < 700; now[0]++) {
            long before = tickets.values().stream().mapToInt(List::size).sum();
            coordinator.tick(); coordinator.tick(); // A repeated visit in the same server tick gets no extra allowance.
            assertTrue(tickets.values().stream().mapToInt(List::size).sum() - before <= 1,
                    "Cold preparation exceeds one pair / 32 columns at tick " + now[0]);
        }
        assertEquals(64, tickets.size()); assertEquals(64, discarded[0]); assertEquals(0, coordinator.size());
        for (var ticks : tickets.values()) {
            assertEquals(3, ticks.size(), "Deferral consumed a ticket opportunity");
            assertEquals(200, ticks.get(1) - ticks.get(0)); assertEquals(200, ticks.get(2) - ticks.get(1));
        }
        assertEquals(records, journal.entries().stream().sorted(java.util.Comparator.comparingInt(records::indexOf)).toList());
        assertTrue(journal.clean());
    }

    @Test void i6PreparationOverridesTheSynchronousDestinationTicketPort() throws Exception {
        var source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketI6Retirement.java"));
        assertTrue(source.contains("@Override public void keepLoaded("), "I6 preparation must not inherit the synchronous origin load");
        assertFalse(source.contains("loadOrigin(") || source.contains(".getChunk("));
        assertTrue(source.contains("recoveryEndsAlreadyLoaded(server, predecessor)"), "Readiness still requires both complete ends");
    }

    @Test void coldAndOtherSitesStillShareFourStartsAndResetWithTheCoordinator() {
        var pacing = new RocketFillRetirementPacing();
        assertEquals(10, pacing.admit(UUID.randomUUID(), 10, false, true));
        assertEquals(11, pacing.admit(UUID.randomUUID(), 10, false, true));
        for (int i = 0; i < 3; i++) { assertEquals(10, pacing.admit(UUID.randomUUID(), 10, false)); }
        assertEquals(11, pacing.admit(UUID.randomUUID(), 10, false));
        assertEquals(11, pacing.admit(UUID.randomUUID(), 11, false, true));
        pacing.clear();
        assertEquals(11, pacing.admit(UUID.randomUUID(), 11, false, true));
        assertEquals(12, pacing.admit(UUID.randomUUID(), 11, true, true));
    }
}
