package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketGovernedSourceDispatcherTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void visitsEightAtMostAndEveryRecordAtThe64EntryLimit() {
        var records = new ArrayList<RocketTransferRecord>();
        for (int index = 0; index < 64; index++) { records.add(GovernedTransferFixture.create(index + 1, 1, false).record()); }
        var visited = new ArrayList<RocketTransferRecord>(); int cursor = 0;
        for (int tick = 0; tick < 8; tick++) {
            int before = visited.size(); cursor = RocketGovernedSourceDispatcher.visit(records, cursor, visited::add);
            assertEquals(8, visited.size() - before);
        }
        assertEquals(records, visited); assertEquals(0, cursor);
    }

    @Test void blockedVisitDoesNotStarveLaterSourcesAndCursorHandlesChangingCounts() {
        var records = Collections.nCopies(9, GovernedTransferFixture.create().record());
        assertEquals(8, RocketGovernedSourceDispatcher.visit(records, 0, record -> { }));
        assertEquals(7, RocketGovernedSourceDispatcher.visit(records, 8, record -> { }));
        var visited = new ArrayList<RocketTransferRecord>();
        assertEquals(0, RocketGovernedSourceDispatcher.visit(records.subList(0, 1), 8, visited::add));
        assertEquals(1, visited.size());
        assertEquals(0, RocketGovernedSourceDispatcher.visit(List.of(), Integer.MAX_VALUE, record -> fail()));
    }

    @Test void destinationAuthorityCannotEnterTheSourceExecutor() {
        var record = GovernedTransferFixture.create().record();
        var destination = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
        assertEquals(0, RocketGovernedSourceDispatcher.visit(List.of(destination), 0, value -> fail()));
        var visited = new ArrayList<RocketTransferRecord>();
        RocketGovernedSourceDispatcher.visit(List.of(destination, record), -1, visited::add);
        assertEquals(List.of(record), visited);
    }

    @Test void combinedSourceAndPublicationVisitsShareTheSameEightVisitBudget() {
        var record = GovernedTransferFixture.create().record();
        var destination = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
        var records = new ArrayList<RocketTransferRecord>();
        for (int i = 0; i < 64; i++) { records.add(i % 2 == 0 ? record : destination); }
        var visited = new ArrayList<RocketTransferRecord>(); int cursor = 0;
        for (int tick = 0; tick < 8; tick++) {
            int count = visited.size(); cursor = RocketGovernedSourceDispatcher.visit(records, cursor, true, visited::add);
            assertEquals(8, visited.size() - count);
        }
        assertEquals(records, visited);
        var later = destination.advance(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.PASSENGERS_TRANSFERRED);
        assertEquals(0, RocketGovernedSourceDispatcher.visit(List.of(later), 0, value -> fail()));
        visited.clear();
        RocketGovernedSourceDispatcher.visit(List.of(later), 0, true, visited::add);
        assertEquals(List.of(later), visited, "F5 shares the bounded governed dispatcher, never the source-only executor");
    }
}
