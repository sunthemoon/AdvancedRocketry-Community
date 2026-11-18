package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCargoCutProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void secondAdmissionObservationIsGatedAndBounded() {
        assertTrue(EscrowCargoFlightCuts.observesAdmission(true, "FF_TWO_FLIGHTS", false, 500));
        assertFalse(EscrowCargoFlightCuts.observesAdmission(false, "FF_TWO_FLIGHTS", false, 500));
        assertFalse(EscrowCargoFlightCuts.observesAdmission(true, "FF_TWO_FLIGHTS", true, 500));
        assertFalse(EscrowCargoFlightCuts.observesAdmission(true, "FF_CANCEL_AFTER_CARGO", false, 500));
        assertFalse(EscrowCargoFlightCuts.observesAdmission(true, "FF_TWO_FLIGHTS", false, 501));
    }

    @Test void successorFixtureQueuesOnceAfterEnablingAndIsInertOutsideItsGate() {
        var calls = new java.util.ArrayList<String>();
        Runnable enable = () -> calls.add("enable");
        java.util.function.Supplier<RocketCargoStatus> attempt = () -> {
            assertEquals(List.of("enable"), calls);
            calls.add("attempt");
            return RocketCargoStatus.READY;
        };
        EscrowCargoFlightCuts.queueSuccessor(false, "FF_TWO_FLIGHTS", false, enable, attempt);
        EscrowCargoFlightCuts.queueSuccessor(true, "FF_TWO_FLIGHTS", true, enable, attempt);
        EscrowCargoFlightCuts.queueSuccessor(true, "FF_CANCEL_AFTER_CARGO", false, enable, attempt);
        assertTrue(calls.isEmpty());
        EscrowCargoFlightCuts.queueSuccessor(true, "FF_TWO_FLIGHTS", false, enable, attempt);
        assertEquals(List.of("enable", "attempt"), calls);
    }

    @Test void successorFixtureDoesNotAcceptAnAdmissionRefusal() {
        for (var status : RocketCargoStatus.values()) {
            if (status == RocketCargoStatus.READY) { continue; }
            assertThrows(IllegalArgumentException.class, () -> EscrowCargoFlightCuts.queueSuccessor(
                    true, "FF_TWO_FLIGHTS", false, () -> { }, () -> status), status.name());
        }
    }

    @Test void governedFixtureRequiresCommittedCurrentJournalBeforeRequestOrReadback() {
        var step = new EscrowCargoFlightCuts.GovernedBaseline();
        for (var outcome : RocketTransferSavedData.WriteOutcome.values()) {
            for (boolean current : List.of(false, true)) {
                if (outcome == RocketTransferSavedData.WriteOutcome.COMMITTED && current) { continue; }
                assertFalse(step.advance(new RocketTransferSavedData.CheckedWriteResult(outcome, 2, 1L, current),
                        callback -> { fail("Unacknowledged journal requested ledger write"); return true; },
                        () -> { fail("Unacknowledged journal reached readback"); return true; }));
            }
        }
    }

    @Test void governedFixtureRetriesRefusedRequestAndWaitsForItsCompletionAndReadback() {
        var step = new EscrowCargoFlightCuts.GovernedBaseline();
        var journal = new RocketTransferSavedData.CheckedWriteResult(RocketTransferSavedData.WriteOutcome.COMMITTED, 2, 2L, true);
        var callback = new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Boolean>>();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier readback = () -> { reads.incrementAndGet(); return true; };
        assertFalse(step.advance(journal, ignored -> false, readback)); // A cooldown/refusal is not an accepted write.
        assertFalse(step.advance(journal, accepted -> { callback.set(accepted); return true; }, readback));
        java.util.function.Function<java.util.function.Consumer<Boolean>, Boolean> noSecondRequest = ignored -> {
            fail("Accepted ledger write was requested twice"); return false;
        };
        assertFalse(step.advance(journal, noSecondRequest, readback)); // Idle/pending without COMMITTED cannot pause.
        assertEquals(0, reads.get());
        callback.get().accept(true);
        assertFalse(step.advance(new RocketTransferSavedData.CheckedWriteResult(RocketTransferSavedData.WriteOutcome.COMMITTED,
                3, 2L, false), noSecondRequest, readback));
        assertEquals(0, reads.get(), "A later journal mutation invalidates the cut readiness");
        assertFalse(step.advance(journal, noSecondRequest, () -> false), "Completion does not replace disk readback");
        assertTrue(step.advance(journal, noSecondRequest, readback));
        assertEquals(1, reads.get());
    }

    @Test void governedFixtureRejectsUnsuccessfulLedgerCompletion() {
        var step = new EscrowCargoFlightCuts.GovernedBaseline();
        var callback = new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Boolean>>();
        var journal = new RocketTransferSavedData.CheckedWriteResult(RocketTransferSavedData.WriteOutcome.COMMITTED, 1, 1L, true);
        assertFalse(step.advance(journal, accepted -> { callback.set(accepted); return true; }, () -> true));
        assertThrows(IllegalArgumentException.class, () -> callback.get().accept(false));
        assertFalse(step.advance(journal, ignored -> true, () -> { fail("Failed write reached readback"); return true; }));
    }

    @Test void governedFixtureReadsExactTerminalAuthoritySerialAndJournalFromDisk(@TempDir java.nio.file.Path directory) {
        var initial = GovernedTransferFixture.create().record();
        var record = initial.destinationSpawned(initial.nativeTransfer().orElseThrow().armed().destination())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
        var arm = record.nativeTransfer().orElseThrow().armed();
        var before = EscrowLedger.empty(arm.ledger()).generation(arm.generation() - 1);
        assertSame(before, EscrowCargoFlightCuts.governedRoot(before, record, false));
        var terminal = EscrowCargoFlightCuts.governedRoot(before, record, true).generation(arm.generation());
        assertEquals(EscrowFlightTables.Outcome.HANDED_OFF, terminal.armedFlights().get(0).outcome());
        assertEquals(arm.serial(), terminal.issuedLaunchSerial());
        assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(terminal, record).route());
        var journal = new RocketTransferSavedData(); journal.put(record);
        var expectedJournal = journal.save(new CompoundTag());
        var ledgerFile = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var journalFile = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        CheckedSavedDataFile.replace(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS, () -> expectedJournal);
        CheckedSavedDataFile.replace(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(before));
        Runnable readback = () -> EscrowCargoFlightCuts.requireGovernedReadback(terminal, expectedJournal,
                CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow(),
                CheckedSavedDataFile.readPayload(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
        assertThrows(IllegalArgumentException.class, readback::run, "The pre-terminal file cannot qualify the cut");
        CheckedSavedDataFile.replace(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(terminal));
        assertDoesNotThrow(readback::run);
        var changedSerial = EscrowCodec.encode(terminal); changedSerial.putLong("issued_launch_serial", arm.serial() + 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoFlightCuts.requireGovernedReadback(
                terminal, expectedJournal, changedSerial, expectedJournal));
        var missingArm = EscrowCodec.encode(before.generation(arm.generation()));
        assertThrows(IllegalArgumentException.class, () -> EscrowCargoFlightCuts.requireGovernedReadback(
                terminal, expectedJournal, missingArm, expectedJournal));
        CheckedSavedDataFile.replace(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS, () -> new RocketTransferSavedData().save(new CompoundTag()));
        assertThrows(IllegalArgumentException.class, readback::run, "A different journal cannot qualify the cut");
    }

    @Test void governedFixturePreservesExplicitAccountingSchemaAndRows() {
        var initial = GovernedTransferFixture.create().record();
        var arm = initial.nativeTransfer().orElseThrow().armed();
        var planned = EscrowAccountingFormatTest.planned(EscrowTestFixtures.operation(1));
        var root = new EscrowLedger(arm.ledger(), planned.issuedThrough(), 0, planned.records(), planned.floors(), planned.tombstones(),
                0, List.of(), List.of(), 3, planned.fluidAccounts());
        assertSame(root, EscrowCargoFlightCuts.governedRoot(root, initial, false));
        var terminal = EscrowCargoFlightCuts.governedRoot(root, initial, true);
        assertEquals(3, terminal.schema()); assertEquals(root.fluidAccounts(), terminal.fluidAccounts());
        assertEquals(root.records(), terminal.records()); assertEquals(arm.serial(), terminal.issuedLaunchSerial());
        assertEquals(terminal, EscrowCodec.decode(EscrowCodec.encode(terminal)));
    }

    @Test void theGateNeedsTheHooksAScenarioAndAW142Case() {
        for (String scenario : List.of("start", "recover")) {
            assertTrue(EscrowCargoCutProbe.selected(true, scenario, "FS_O1_BEFORE"));
            assertTrue(EscrowCargoCutProbe.selected(true, scenario, "FF_N1"));
            assertFalse(EscrowCargoCutProbe.selected(false, scenario, "FS_O1_BEFORE"));
        }
        for (String scenario : new String[] {null, "", "START", "other"}) {
            assertFalse(EscrowCargoCutProbe.selected(true, scenario, "FS_O1_BEFORE"));
        }
        for (String name : new String[] {null, "", "FS_", "FF_UNKNOWN", "FC_UNLOAD_L1_BEFORE", "IC_LOAD_K8", "SR_ACCEPT__O1_BEFORE", "fs_O1_BEFORE"}) {
            assertFalse(EscrowCargoCutProbe.selected(true, "start", name));
        }
        // The unit JVM sets no release-test property: nothing attaches, and the current probe is returned unchanged.
        assertNull(EscrowCargoCutProbe.attach(null, null));
    }

    @Test void theVerificationModeIsInertWithoutTheHooks() {
        for (String name : EscrowCargoRepairVerifyProbe.CASES) {
            for (String scenario : new String[] {null, "", "verify", "start", "recover", "other"}) {
                assertFalse(EscrowCargoCutProbe.selected(false, scenario, name), name + "/" + scenario);
                assertFalse(EscrowCargoRepairVerifyProbe.selected(false, scenario, name), name + "/" + scenario);
                assertFalse(EscrowCargoRepairVerifyProbe.misrouted(false, scenario, name), name + "/" + scenario);
            }
        }
        assertFalse(EscrowCargoRepairVerifyProbe.misrouted(false, "verify", "FF_REBUILD_SOURCE"));
        assertFalse(EscrowCargoRepairVerifyProbe.misrouted(false, "verify", null));
    }

    @Test void theVerificationCasesAreSelectedOnlyUnderTheVerifyScenario() {
        assertEquals(Set.of("FF_X03_OBSERVE", "FF_X03_UNLOAD", "FF_X03_LAUNCH"), EscrowCargoRepairVerifyProbe.CASES);
        for (String name : EscrowCargoRepairVerifyProbe.CASES) {
            assertTrue(EscrowCargoCutProbe.accepts(name), "The existing cargo-cut branch routes " + name);
            assertTrue(EscrowCargoCutProbe.selected(true, "verify", name));
            assertFalse(EscrowCargoRepairVerifyProbe.misrouted(true, "verify", name));
            for (String scenario : new String[] {null, "", "start", "recover", "VERIFY", "verify ", "other"}) {
                assertFalse(EscrowCargoCutProbe.selected(true, scenario, name), name + "/" + scenario);
                assertTrue(EscrowCargoRepairVerifyProbe.misrouted(true, scenario, name), name + "/" + scenario);
            }
            // No other specialized selector takes a verification case under any scenario.
            for (String scenario : List.of("verify", "start", "recover")) {
                assertFalse(EscrowItemCutProbe.selected(true, scenario, name));
                assertFalse(EscrowCargoCutProbe.journalCut(true, scenario, name));
            }
            assertFalse(EscrowCargoReconciliationProbe.accepts(name));
            assertFalse(EscrowCargoOwnerCapProbe.CASES.containsKey(name) || EscrowCargoSettlementCuts.CASES.containsKey(name)
                    || EscrowCargoFlightCuts.CASES.containsKey(name) || EscrowCargoRecoveryCuts.CASES.containsKey(name));
        }
    }

    @Test void theExistingCasesAreNeverSelectedUnderTheVerifyScenario() {
        var existing = new java.util.TreeSet<String>();
        existing.addAll(EscrowCargoOwnerCapProbe.CASES.keySet()); existing.addAll(EscrowCargoSettlementCuts.CASES.keySet());
        existing.addAll(EscrowCargoFlightCuts.CASES.keySet()); existing.addAll(EscrowCargoRecoveryCuts.CASES.keySet());
        assertTrue(existing.contains("FF_REBUILD_SOURCE") && existing.contains("FS_O1_BEFORE"));
        for (String name : existing) {
            assertFalse(EscrowCargoRepairVerifyProbe.accepts(name), name);
            assertFalse(EscrowCargoCutProbe.selected(true, "verify", name), name);
            assertTrue(EscrowCargoRepairVerifyProbe.misrouted(true, "verify", name), name);
            // Their own scenarios are unchanged and are not a misrouting.
            for (String scenario : List.of("start", "recover")) {
                assertTrue(EscrowCargoCutProbe.selected(true, scenario, name), name);
                assertFalse(EscrowCargoRepairVerifyProbe.misrouted(true, scenario, name), name);
            }
        }
        // The verify scenario accepts nothing else either: no case, an empty case, an unknown name or another family.
        for (String name : new String[] {null, "", "FF_X03_", "FF_X03_REPAIRED", "ff_x03_observe", "E1", "BENCH", "IC_LOAD_K8"}) {
            assertFalse(EscrowCargoCutProbe.selected(true, "verify", name));
            assertTrue(EscrowCargoRepairVerifyProbe.misrouted(true, "verify", name));
        }
        // Names outside both groups keep reaching the older dispatch under the older scenarios.
        for (String name : new String[] {null, "", "E1", "OLDER_RUNNER_CASE", "FF_UNKNOWN"}) {
            for (String scenario : new String[] {null, "start", "recover", "older-scenario"}) {
                assertFalse(EscrowCargoRepairVerifyProbe.misrouted(true, scenario, name));
            }
        }
    }

    @Test void aMisroutedVerificationStartIsRefusedBeforeAnyProbeIsCreated() {
        String hooks = EscrowTestEndpoints.HOOKS, scenario = EscrowCargoCutProbe.SCENARIO, selectedCase = EscrowCargoCutProbe.CASE;
        var saved = new String[] {System.getProperty(hooks), System.getProperty(scenario), System.getProperty(selectedCase)};
        try {
            System.setProperty(hooks, "true");
            for (String[] pair : new String[][] {{"start", "FF_X03_OBSERVE"}, {"recover", "FF_X03_UNLOAD"}, {"other", "FF_X03_LAUNCH"},
                    {null, "FF_X03_OBSERVE"}, {"verify", "FF_REBUILD_SOURCE"}, {"verify", "E1"}, {"verify", null}}) {
                set(scenario, pair[0]); set(selectedCase, pair[1]);
                // The server argument is null: the refusal precedes every use of it.
                assertThrows(IllegalArgumentException.class, () -> EscrowCargoCutProbe.attach(null, null), pair[0] + "/" + pair[1]);
            }
            // Without the hooks the same properties are inert, and nothing attaches.
            System.setProperty(hooks, "false");
            for (String[] pair : new String[][] {{"start", "FF_X03_OBSERVE"}, {"verify", "FF_X03_OBSERVE"}, {"verify", "FF_REBUILD_SOURCE"}}) {
                set(scenario, pair[0]); set(selectedCase, pair[1]);
                assertNull(EscrowCargoCutProbe.attach(null, null));
            }
        } finally {
            set(hooks, saved[0]); set(scenario, saved[1]); set(selectedCase, saved[2]);
        }
    }
    private static void set(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    @Test void theVerificationRouteAndLaunchAnswersAreBounded() {
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.MOON, EscrowCargoRepairVerifyProbe.route(null));
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.MOON, EscrowCargoRepairVerifyProbe.route(""));
        for (var destination : io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.values()) {
            assertEquals(destination, EscrowCargoRepairVerifyProbe.route(destination.name()));
        }
        for (String value : List.of("moon", "MARS", " MOON", "1")) {
            assertThrows(IllegalArgumentException.class, () -> EscrowCargoRepairVerifyProbe.route(value), value);
        }
        for (var code : io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.values()) {
            boolean accepted = code.name().equals("SUCCESS") || code.name().equals("JOURNAL_PENDING");
            assertEquals(accepted, EscrowCargoRepairVerifyProbe.launchAccepted(code), code.name());
            assertEquals(code.name().equals("DESTINATION_LOADING"), EscrowCargoRepairVerifyProbe.launchRepeatable(code, 1), code.name());
            assertFalse(EscrowCargoRepairVerifyProbe.launchRepeatable(code, EscrowCargoRepairVerifyProbe.REQUESTS), code.name());
        }
        assertEquals(6_000, EscrowCargoRepairVerifyProbe.WAIT_TICKS);
        assertFalse(EscrowCargoRepairVerifyProbe.expired(6_100, 100));
        assertTrue(EscrowCargoRepairVerifyProbe.expired(6_101, 100));
    }

    @Test void theVerificationProbeHoldsNoCreationRecoveryOrLedgerWrite() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoRepairVerifyProbe.java"));
        String code = source.lines().map(String::trim).filter(line -> !line.startsWith("*") && !line.startsWith("/**") && !line.startsWith("//"))
                .collect(java.util.stream.Collectors.joining("\n"));
        for (String forbidden : List.of("setDirty", "recoverTransfer", "cargoRocket(", "device(DEVICE", "addFreshEntity", "setBlock",
                "journal.put", "journal.remove", "journal.replace", "ResourceEscrowSavedData.forTests", "updateFlightData", "flushChecked",
                "pause(", "hardStop(", "ReleaseTestPlayers")) {
            assertFalse(code.contains(forbidden), "The verification mode must not use " + forbidden);
        }
        assertEquals(2, code.split("EscrowCargoSwitches\\.forTests\\(", -1).length - 1, "One naming and one withdrawal of the device switch");
        assertEquals(1, code.split("requestAdminFlight\\(", -1).length - 1);
        assertTrue(code.contains("EscrowTestEndpoints.requireEnabled(server)"));
    }

    @Test void verificationLaunchWaitsForTerminalAuthorityPruningAndQuietWriters() {
        var initial = GovernedTransferFixture.create().record();
        var arm = initial.nativeTransfer().orElseThrow().armed();
        var empty = EscrowLedger.empty(arm.ledger()).generation(arm.generation() - 1);
        var terminal = EscrowCargoFlightCuts.governedRoot(empty, initial, true);
        assertEquals(EscrowFlightTables.Outcome.HANDED_OFF, terminal.armedFlights().get(0).outcome());
        assertFalse(EscrowCargoRepairVerifyProbe.launchSettled(terminal, arm.logical(), true));
        assertFalse(EscrowCargoRepairVerifyProbe.launchSettled(empty, arm.logical(), false));
        assertTrue(EscrowCargoRepairVerifyProbe.launchSettled(empty, arm.logical(), true));
        assertTrue(EscrowCargoRepairVerifyProbe.launchSettled(terminal, UUID.randomUUID(), true));
    }

    @Test void verificationUnloadKeepsTheOrdinaryPacerRunningAfterThePlayerlessSettle() throws Exception {
        var pacer = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.RefillPacer.loadedAt(0);
        int[] emptyTime = {400};
        int attempts = 0;
        for (int tick = 400; tick <= 440; tick++) {
            EscrowCargoRepairVerifyProbe.keepUnloadTicking("FF_X03_UNLOAD", 3, () -> emptyTime[0] = 0);
            if (emptyTime[0]++ < 300 && pacer.tryStart(tick)) { attempts++; }
        }
        assertEquals(3, attempts, "The real 20-tick pacer must run after the 400-tick playerless settle");
        for (String name : List.of("FF_X03_OBSERVE", "FF_X03_LAUNCH", "FF_REBUILD_SOURCE")) {
            EscrowCargoRepairVerifyProbe.keepUnloadTicking(name, 3, () -> fail("Other cases must not reset the Level"));
        }
        for (int step : List.of(0, 1, 5)) {
            EscrowCargoRepairVerifyProbe.keepUnloadTicking("FF_X03_UNLOAD", step, () -> fail("Outside the unload interval"));
        }
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoRepairVerifyProbe.java"));
        assertTrue(source.contains("keepUnloadTicking(name, step, level::resetEmptyTime)"));
        assertTrue(source.contains("launchSettled(data.ledger(), logical, quiet(journal, coordinator))"));
    }

    @Test void theJournalSeamIsInertOutsideItsTwoStartCases() {
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        assertSame(policy, EscrowCargoCutProbe.journalPolicy(null, policy));
        assertTrue(EscrowCargoCutProbe.journalCut(true, "start", "FS_O3J_BEFORE"));
        assertTrue(EscrowCargoCutProbe.journalCut(true, "start", "FS_O3J_AFTER"));
        assertFalse(EscrowCargoCutProbe.journalCut(false, "start", "FS_O3J_BEFORE"));
        assertFalse(EscrowCargoCutProbe.journalCut(true, "recover", "FS_O3J_AFTER"));
        for (String name : new String[] {null, "FS_O3_AFTER", "FS_O3J", "FT_JOURNAL_BEFORE"}) {
            assertFalse(EscrowCargoCutProbe.journalCut(true, "start", name));
        }
    }

    @Test void onlyTheCompleteJournalRemovalIsTheO3JBoundary() {
        var empty = journal(0); var one = journal(1);
        assertTrue(EscrowCargoCutProbe.removal(empty, one));
        assertFalse(EscrowCargoCutProbe.removal(empty, null));
        assertFalse(EscrowCargoCutProbe.removal(empty, empty));
        assertFalse(EscrowCargoCutProbe.removal(one, one));
        assertFalse(EscrowCargoCutProbe.removal(one, empty));
    }

    @Test void eachSettlementBoundaryPausesAtItsOwnCheckpointOnly() {
        assertTrue(EscrowCargoSettlementCuts.pauses("O1_BEFORE", "K1", "", true, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O1_BEFORE", "K1", "", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O1_BEFORE", "K2", "", true, 0, 1));
        assertTrue(EscrowCargoSettlementCuts.pauses("O1_AFTER", "K2", "", true, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O1_AFTER", "K2", "", false, 0, 1));
        for (String bind : List.of("O_BIND_BEFORE", "O_BIND_AFTER")) {
            assertTrue(EscrowCargoSettlementCuts.pauses(bind, bind, "", false, -1, -1));
            assertFalse(EscrowCargoSettlementCuts.pauses(bind, bind + "_0", "", false, -1, -1));
        }
        assertTrue(EscrowCargoSettlementCuts.pauses("O2_ROCKET_BEFORE", "O2_BEFORE_0", "", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O2_ROCKET_BEFORE", "O2_BEFORE_1", "", false, 0, 1));
        assertTrue(EscrowCargoSettlementCuts.pauses("O2_DEVICE_AFTER", "O2_AFTER_1", "", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O2_DEVICE_AFTER", "O2_AFTER_0", "", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O2_ROCKET_AFTER", "O2_AFTER_0", "", false, -1, -1));
        assertTrue(EscrowCargoSettlementCuts.pauses("O3_BEFORE", "K1", "O3", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O3_BEFORE", "K1", "", false, 0, 1));
        assertTrue(EscrowCargoSettlementCuts.pauses("O3_AFTER", "K2", "O3", false, 0, 1));
        assertFalse(EscrowCargoSettlementCuts.pauses("O3_AFTER", "K2", "O4", false, 0, 1));
        for (String label : List.of("K1", "K8")) { assertTrue(EscrowCargoSettlementCuts.pauses("O4_BEFORE", label, "O4", false, 0, 1)); }
        for (String label : List.of("K2", "K9")) { assertTrue(EscrowCargoSettlementCuts.pauses("O4_AFTER", label, "O4", false, 0, 1)); }
        assertFalse(EscrowCargoSettlementCuts.pauses("O4_AFTER", "K9", "O3", false, 0, 1));
        // Owner-tick and journal boundaries never pause from a ledger or barrier checkpoint.
        for (String boundary : List.of("PRUNE_BEFORE", "PRUNE_AFTER", "O_VETO", "NORMALIZATION_VETO", "O3J_BEFORE", "O3J_REPLACED")) {
            for (String label : List.of("K1", "K2", "K8", "K9", "O_VETO", "O4_AFTER")) {
                assertFalse(EscrowCargoSettlementCuts.pauses(boundary, label, "O4", true, 0, 1));
            }
        }
        assertEquals(40, EscrowCargoSettlementCuts.CASES.size());
        var known = Set.of("O1_BEFORE", "O1_AFTER", "O_BIND_BEFORE", "O_BIND_AFTER", "O2_ROCKET_BEFORE", "O2_ROCKET_AFTER",
                "O2_DEVICE_BEFORE", "O2_DEVICE_AFTER", "O3_BEFORE", "O3_AFTER", "O3J_BEFORE", "O3J_REPLACED", "O4_BEFORE", "O4_AFTER",
                "PRUNE_BEFORE", "PRUNE_AFTER", "O_VETO", "NORMALIZATION_VETO");
        EscrowCargoSettlementCuts.CASES.forEach((name, cut) -> {
            assertTrue(name.startsWith("FS_") && EscrowCargoCutProbe.accepts(name), name);
            assertTrue(known.contains(cut.boundary()), name);
        });
    }

    @Test void onlyTheVetoCaseSelectsTheDestinationStore() {
        assertTrue(EscrowCargoSettlementCuts.storeAllowed(false, true, false));
        assertTrue(EscrowCargoSettlementCuts.storeAllowed(true, false, false));
        assertTrue(EscrowCargoSettlementCuts.storeAllowed(true, true, true));
        assertFalse(EscrowCargoSettlementCuts.storeAllowed(true, true, false));
    }

    private static CompoundTag journal(int records) {
        var list = new ListTag(); for (int i = 0; i < records; i++) { list.add(new CompoundTag()); }
        var tag = new CompoundTag(); tag.put("transfers", list); return tag;
    }

    @Test void flightAndDisassemblyCasesUseTheirExactBoundaries() {
        assertEquals(8, EscrowCargoFlightCuts.CASES.size());
        assertEquals(8, EscrowCargoRecoveryCuts.CASES.size());
        EscrowCargoFlightCuts.CASES.keySet().forEach(name -> {
            assertTrue(EscrowCargoCutProbe.selected(true, "start", name));
            assertFalse(EscrowCargoCutProbe.selected(false, "start", name));
        });
        EscrowCargoRecoveryCuts.CASES.forEach((name, boundary) -> {
            assertTrue(EscrowCargoCutProbe.selected(true, "recover", name));
            assertFalse(EscrowCargoCutProbe.selected(false, "recover", name));
            assertEquals(name.startsWith("FF_DISASSEMBLY_"), EscrowCargoRecoveryCuts.disassembly(boundary));
        });
        assertTrue(EscrowCargoFlightCuts.pauses("BASELINE_REPLACED", "BASELINE_REPLACED", false, false, false));
        assertTrue(EscrowCargoFlightCuts.pauses("ARMED_REPLACED", "K9", true, false, false));
        assertFalse(EscrowCargoFlightCuts.pauses("ARMED_REPLACED", "K9", false, false, false));
        assertEquals("F6_JOINT_PROVED", EscrowCargoFlightCuts.CASES.get("FF_F6_AFTER_CARGO"));
        for (String label : List.of("BARRIER_READBACK_AFTER", "F6_LANDED", "F7_ACK", "K2", "K9")) {
            assertFalse(EscrowCargoFlightCuts.pauses("F6_JOINT_PROVED", label, false, true, false));
        }
    }

    @Test void f6StopsOnlyAfterTheJointProofAndBeforeTerminalAcknowledgement() {
        var completedSlots = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier joint = () -> completedSlots.get() == 2;
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", true, false, joint));
        completedSlots.incrementAndGet(); // Source absence alone is not F6.
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", true, false, joint));
        completedSlots.incrementAndGet(); // Destination frontier plus source absence, jointly valid.
        assertTrue(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", true, false, joint));
        completedSlots.set(0); // Invalidated proofs cannot authorize a cached stop.
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", true, false, joint));
        java.util.function.BooleanSupplier unselected = () -> { fail("Unselected F6 hook inspected terminal state"); return true; };
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("ARMED_REPLACED", true, false, unselected));
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", false, false, unselected));
        assertFalse(EscrowCargoFlightCuts.jointF6Ready("F6_JOINT_PROVED", true, true, unselected));
    }

    @Test void disassemblyDispatchesTheProductionOfferAndReportsActualRefusalBeforeFailing() {
        String command = "/arce rocket-disassembly confirm " + java.util.UUID.randomUUID();
        var events = new java.util.ArrayList<String>();
        EscrowCargoDisassemblyRequest.confirm(command, offered -> { events.add(offered); return 1; }, code -> events.add("code=" + code));
        assertEquals(List.of(command, "code=1"), events);
        for (int refused : new int[] {0, -1, 2}) {
            events.clear();
            var failure = assertThrows(IllegalArgumentException.class, () -> EscrowCargoDisassemblyRequest.confirm(command,
                    offered -> { events.add(offered); return refused; }, code -> events.add("code=" + code)));
            assertEquals(List.of(command, "code=" + refused), events);
            assertTrue(failure.getMessage().endsWith(" " + refused));
        }
        for (String invalid : new String[] {null, "", "arce rocket release-test disassemble rocket discard-fuel 1000", command + " extra"}) {
            assertThrows(IllegalArgumentException.class, () -> EscrowCargoDisassemblyRequest.confirm(invalid,
                    offered -> { fail("Invalid offer dispatched"); return 1; }, code -> fail("Unexecuted command reported")));
        }
    }

    @Test void sourceRebuildReplayKeepsTheActualPreUnloadSnapshotAndPhysicalIdentity() {
        var physical = UUID.randomUUID(); var logical = UUID.randomUUID(); var owner = UUID.randomUUID();
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var before = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(68, 100, 64), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                        PressurizedTankRocketAdapter.payload(new FluidStack(Fluids.WATER, 5_000))),
                        new RocketBlock(new RocketPosition(0, 1, 0),
                                new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 1), 0, propulsion);
        var fuel = RocketFuelState.empty(propulsion).withFluidBank(false,
                ResourceLocation.tryParse("advancedrocketrycommunity:rocket_fuel"), null, 1_000);
        var record = EscrowCargoRecoveryCuts.legacyRecord("REBUILD_SOURCE", before, physical, logical, owner, fuel);
        assertSame(before, record.sourceSnapshot());
        assertEquals(physical, record.sourceEntityId());
        assertEquals(logical, record.logicalRocketId());
        assertEquals(owner, record.ownerId());
        assertEquals(RocketTransferPhase.PREPARED, record.phase());
        assertEquals(before.sourceOrigin(), record.sourceFlightData().currentOrigin());
        assertEquals(before.snapshotId(), record.sourceSnapshot().snapshotId());
        assertEquals(before.contentHash(), record.sourceSnapshot().contentHash());
        assertEquals(5_000, PressurizedTankRocketAdapter.fluid(record.sourceSnapshot().blocks().get(0)
                .blockEntityPayload().orElseThrow().data()).getAmount());
        assertNotEquals(before.snapshotId(), record.destinationSnapshot().snapshotId());
        assertEquals(new RocketPosition(88, 100, 64), record.destinationSnapshot().sourceOrigin());
        assertEquals(fuel, record.sourceFlightData().fuel());
        assertTrue(record.destinationEntityId().isEmpty());
    }
}
