package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoCutProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

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
        assertEquals(20, EscrowCargoSettlementCuts.CASES.size());
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
}
