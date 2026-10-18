package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketJournalNativeProbeTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void recordsEventsAndTicksHaveIndependentFixedCaps() {
        var records = new RocketJournalNativeProbe();
        for (int i = 0; i <= RocketJournalNativeProbe.MAX_RECORDS; i++) { records.event(new UUID(0, i), "visit", i, "", 0); }
        assertEquals(128, ((Map<?, ?>) records.snapshot().get("records")).size());
        assertEquals(true, records.snapshot().get("capped"));
        var events = new RocketJournalNativeProbe(); var id = UUID.randomUUID();
        for (int i = 0; i <= RocketJournalNativeProbe.MAX_EVENTS_PER_RECORD; i++) { events.event(id, "visit", i, "", 0); }
        assertEquals(4_096, events.snapshot().get("event_count")); assertEquals(true, events.snapshot().get("capped"));
        var total = new RocketJournalNativeProbe();
        for (int i = 0; i <= RocketJournalNativeProbe.MAX_EVENTS; i++) { total.event(new UUID(0, i % 128), "visit", i, "", 0); }
        assertEquals(32_768, total.snapshot().get("event_count")); assertEquals(true, total.snapshot().get("capped"));
        var ticks = new RocketJournalNativeProbe();
        for (int i = 0; i <= RocketJournalNativeProbe.MAX_TICKS; i++) { ticks.tickStart(i); ticks.tickEnd(); }
        assertEquals(12_000, ((List<?>) ticks.snapshot().get("ticks")).size()); assertEquals(true, ticks.snapshot().get("capped"));
    }

    @Test void dumpIsSingleUseAndCannotOverwriteEvidence() throws Exception {
        var probe = new RocketJournalNativeProbe(); probe.tickStart(1); probe.event(null, "snapshot", 2, "ACCEPTED", 1); probe.tickEnd();
        probe.write(directory); var file = directory.resolve(RocketJournalNativeProbe.FILE); var original = Files.readString(file);
        probe.event(null, "snapshot", 3, "ACCEPTED", 1); probe.write(directory);
        assertEquals(original, Files.readString(file));
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> new RocketJournalNativeProbe().write(directory));
    }

    @Test void sameReservationAndSchedulingDecisionsWithObservationOffAndOn() throws Exception {
        assertEquals(schedule(false), schedule(true));
    }

    private List<String> schedule(boolean observe) throws Exception {
        var decisions = new ArrayList<String>(); var journal = new RocketTransferSavedData();
        if (observe) { journal.observeForTests(new RocketJournalNativeProbe()); }
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        Path file = directory.resolve(observe ? "on" : "off").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        Files.createDirectory(file.getParent());
        try {
            for (int i = 0; i < 3; i++) {
                var record = RocketJournalScheduleFixtures.record(i); var id = record.logicalRocketId();
                var reservation = journal.reserve(id).orElseThrow(); journal.put(record);
                decisions.add("queued=" + journal.queueEffect(reservation, false, false,
                        () -> { decisions.add("run=" + id); return true; }, () -> decisions.add("discard=" + id)));
                decisions.add("actionable=" + journal.actionable(id));
            }
            journal.pollWriter(1); journal.saveChecked(file, policy); journal.pollWriter(2);
            for (int i = 0; i < 3; i++) { decisions.add("actionable=" + journal.actionable(RocketJournalScheduleFixtures.record(i).logicalRocketId())); }
            assertEquals(3, decisions.stream().filter(s -> s.startsWith("run=")).count());
            assertEquals(0, decisions.stream().filter(s -> s.startsWith("discard=")).count());
        } finally { journal.stop(); }
        return decisions;
    }

    @Test void defaultDetachedPolicyHasNoProbeOrHold() {
        var journal = new RocketTransferSavedData(); assertNull(journal.nativeProbe());
        assertNotNull(RocketJournalFailurePolicy.checked(null));
        assertThrows(IllegalStateException.class, () -> journal.startNativeProbe(null));
    }
}
