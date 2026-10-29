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

class RocketI6AdmissionPacingTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aRealQueueBurstPerformsAtMostOneAdmissionPreflightPerTick() {
        var fixture = new Fixture();
        fixture.enqueue();
        assertEquals(64, fixture.coordinator.size());
        assertTrue(fixture.preflights.isEmpty());
        for (; fixture.now[0] < 165; fixture.now[0]++) {
            int before = fixture.preflights.size();
            fixture.coordinator.tick(); fixture.coordinator.tick();
            assertTrue(fixture.preflights.size() - before <= 1,
                    "A burst must not run multiple admission preflights in tick " + fixture.now[0]);
        }
        assertEquals(64, fixture.preflights.size());
        assertEquals(64, fixture.discarded);
        assertEquals(0, fixture.coordinator.size());
        assertEquals(64, fixture.journal.entries().size());
        assertTrue(fixture.journal.clean());
    }

    @Test void deferredRequestsKeepTheirOriginalAgeAndAreRecheckedBeforePreflight() {
        var fixture = new Fixture();
        fixture.enqueue();
        fixture.coordinator.tick();
        fixture.now[0] = 301; // Original request at 100, not the later admission turn.
        fixture.coordinator.tick();
        assertEquals(1, fixture.preflights.size());
        assertEquals(64, fixture.discarded);
        assertEquals(0, fixture.coordinator.size());
        assertTrue(fixture.journal.clean());
    }

    @Test void commandCallbackDefersTheExpensiveCheckButTerminalStillRechecks() throws Exception {
        var source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketI6Entry.java"));
        assertTrue(source.contains("landed.admission(rocket, record, journal, port, Optional::empty, true)"),
                "The confirmation callback still walks every snapshot");
        assertTrue(source.contains("proof.preflight("));
        var terminal = source.substring(source.indexOf("proof.handoff("), source.indexOf("try (var held", source.indexOf("proof.handoff(")));
        assertTrue(terminal.contains("preflight.get()"), "Admission is not a cached world-state authorization");
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final List<RocketTransferRecord> records = new ArrayList<>();
        final Map<UUID, Long> preflights = new LinkedHashMap<>();
        final long[] now = {100};
        int discarded;
        final RocketOrdinaryRetirement coordinator = new RocketOrdinaryRetirement(
                new HashSet<>(), new HashSet<>(), Set::of, ignored -> { throw new AssertionError("Unexpected world port"); });

        Fixture() {
            for (int i = 0; i < 64; i++) {
                var record = RocketStationMotionRuleTest.record(new RocketPosition(1000 + i * 64, 80, 1000))
                        .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                records.add(record); journal.put(record);
            }
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), 1000);
        }

        void enqueue() {
            for (var record : records) {
                var port = (RocketOrdinaryRetirement.Port) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {RocketOrdinaryRetirement.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "now" -> now[0];
                            case "requestCurrent" -> now[0] - 100 <= 200 && !preflights.containsKey(record.transferId());
                            case "needsTicketStep", "preparationOnly", "bayClaimed" -> false;
                            case "crewReady", "captureSupported", "captureAvailable", "bayAllows", "escrowAllows" -> true;
                            case "survivor" -> RocketOrdinaryRetirement.Presence.LOADED;
                            case "slot" -> RocketOrdinaryRetirement.SlotState.LOADED;
                            case "prepare" -> {
                                preflights.put(record.transferId(), now[0]);
                                yield "PREFLIGHT_REFUSED"; // Invalid terrain ends this request without any proof or mutation.
                            }
                            case "releaseTickets" -> null;
                            default -> throw new AssertionError("Preflight reached unexpected method " + method.getName());
                        });
                try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
                    coordinator.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_RELEASE, record,
                            record.destinationEntityId().orElseThrow(), List.of(), false, held,
                            new RocketOrdinaryRetirement.Effects(() -> { fail("Refused preflight gained authority"); return false; },
                                    () -> discarded++));
                }
            }
        }
    }
}
