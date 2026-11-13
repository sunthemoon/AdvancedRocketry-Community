package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EscrowCargoHarnessTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ownerSeedIsDurableAndChargesExactlySixteenDistinctRockets() {
        var owner = UUID.randomUUID(); var root = EscrowCargoOwnerCapProbe.seed(UUID.randomUUID(), owner);
        var decoded = EscrowCodec.decode(EscrowCodec.encode(root));
        assertEquals(root, decoded); assertEquals(16, root.records().size());
        assertEquals(16, root.records().stream().map(op -> op.guard().rocket()).distinct().count());
        assertTrue(root.records().stream().allMatch(op -> op.owner().equals(owner) && op.phase() == EscrowOperation.Phase.REPAIR));
        var authority = new EscrowAdmissionAuthority();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION,
                () -> new EscrowAdmissionAuthority.Index(true, root.records().stream().map(EscrowAdmissionAuthority.Pending::of).toList()));
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.BAY, () -> new EscrowAdmissionAuthority.Index(true, java.util.List.of()));
        assertEquals(16, authority.pendingCount());
        assertEquals(EscrowAdmissionAuthority.Result.OWNER_PENDING_FULL,
                authority.checkReserve(UUID.randomUUID(), owner, UUID.randomUUID(), EscrowAdmissionAuthority.Consumer.PROPULSION));
        assertEquals(EscrowAdmissionAuthority.Result.RESERVED,
                authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EscrowAdmissionAuthority.Consumer.PROPULSION));
        assertEquals(16, authority.pendingCount());
    }

    @Test void newNativeCasesRequireEveryExistingReleaseGate() {
        for (var name : EscrowCargoOwnerCapProbe.CASES.keySet()) {
            assertTrue(EscrowCargoCutProbe.selected(true, "start", name));
            assertTrue(EscrowCargoCutProbe.selected(true, "recover", name));
            assertFalse(EscrowCargoCutProbe.selected(false, "start", name));
            assertFalse(EscrowCargoCutProbe.selected(true, "", name));
        }
        assertEquals(4, EscrowCargoOwnerCapProbe.CASES.size());
    }

    @Test void everyUnloadSettlementCutHasAnEqualLoadBoundary() {
        assertEquals(40, EscrowCargoSettlementCuts.CASES.size());
        EscrowCargoSettlementCuts.CASES.forEach((name, cut) -> {
            if (!name.startsWith("FS_LOAD_")) {
                var load = name.replace("FS_", "FS_LOAD_");
                assertEquals(cut, EscrowCargoSettlementCuts.CASES.get(load));
                assertTrue(EscrowCargoCutProbe.selected(true, "start", load));
            }
        });
        assertEquals(40, EscrowItemSettlementCuts.CASES.size()); // W360 adds seven cuts in each direction.
        EscrowItemSettlementCuts.CASES.forEach((name, cut) -> {
            if (!name.startsWith("IS_LOAD_")) {
                var load = name.replace("IS_", "IS_LOAD_");
                assertEquals(cut, EscrowItemSettlementCuts.CASES.get(load));
                assertTrue(EscrowItemCutProbe.selected(true, "recover", load));
            }
        });
    }

    @Test void loadJournalCutsAreStartOnlyAndDoNotLeakIntoProduction() {
        for (var name : java.util.List.of("FS_LOAD_O3J_BEFORE", "FS_LOAD_O3J_AFTER")) {
            assertTrue(EscrowCargoCutProbe.journalCut(true, "start", name));
            assertFalse(EscrowCargoCutProbe.journalCut(true, "recover", name));
            assertFalse(EscrowCargoCutProbe.journalCut(false, "start", name));
        }
        assertTrue(EscrowItemCutProbe.journalCut(true, "start", "IS_LOAD_O3J_AFTER"));
        assertFalse(EscrowItemCutProbe.journalCut(true, "recover", "IS_LOAD_O3J_AFTER"));
        assertFalse(EscrowItemCutProbe.journalCut(false, "start", "IS_LOAD_O3J_AFTER"));
    }
}
