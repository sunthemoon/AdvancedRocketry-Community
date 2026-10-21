package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W107C (R43 item 5): cut R1's seeded floors are valid, deterministic, counted and never collected. */
class EscrowLoaderFloorProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void seededFloorsAreValidDeterministicNeutralSectionsThatSurviveTheCodec() {
        var floors = EscrowLoaderFloorProbe.floors(256);
        assertEquals(256, floors.size()); assertEquals(floors, EscrowLoaderFloorProbe.floors(256));
        assertEquals(256, new HashSet<>(floors).size(), "Every seeded floor is distinct");
        assertEquals(256, floors.stream().map(floor -> floor.getUUID("rocket_id")).distinct().count());
        for (int i = 0; i < floors.size(); i++) {
            var floor = floors.get(i);
            assertFalse(floor.hasUUID("transfer_id")); assertEquals(2, floor.getInt("schema_version"));
            assertEquals(i + 1, floor.getLong("highest_seq"), "Each floor retires its own earlier sequence");
            assertNull(EscrowFloors.key(floor));
            EscrowCodec.validateSection(floor, false);
        }
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(EscrowTestFixtures.id(500), 254, 2, List.of(),
                floors.subList(0, 255), List.of()), "The watermark covers every seeded floor");
        var root = ledger(List.of(), floors.subList(0, 255));
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        assertEquals(new EscrowFloors.Usage(255, 0), EscrowFloors.usage(root, List.of()));
        // Neither an absent journal record nor an empty queue collects a neutral floor.
        assertEquals(255, EscrowFloors.collect(root, List.of(), ignored -> true).floors().size());
    }

    @Test void at255OneNewKeyEntersTheSecondDistinctKeyRefusesAndTheSameKeyCoalesces() {
        var root = ledger(List.of(), EscrowLoaderFloorProbe.floors(255));
        var first = named(256, 1, 0); var distinct = named(257, 2, 0); var same = named(257, 1, 1);
        EscrowSettlementCapacity.preflight(root.append(List.of(first)), first.id());
        assertEquals(new EscrowFloors.Usage(255, 1), EscrowFloors.usage(root.append(List.of(first)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root.append(List.of(first, distinct)), distinct.id()));
        EscrowSettlementCapacity.preflight(root.append(List.of(first, same)), same.id());
        assertEquals(new EscrowFloors.Usage(255, 1), EscrowFloors.usage(root.append(List.of(first, same)), List.of()));
    }

    @Test void at256ANamedKeyRefusesButANoneGuardFillNeedsNoFloor() {
        var root = ledger(List.of(), EscrowLoaderFloorProbe.floors(256));
        var named = named(257, 1, 0); var none = EscrowTestFixtures.operation(257);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root.append(List.of(named)), named.id()));
        EscrowSettlementCapacity.preflight(root.append(List.of(none)), none.id());
        assertEquals(new EscrowFloors.Usage(256, 0), EscrowFloors.usage(root.append(List.of(none)), List.of()));
    }

    @Test void floorCutsAreSelectedApartFromTheMatrixAndHoldsOnlyForTheirCuts() {
        for (String cut : List.of("LF_M_R1_FLOORS_255_DISTINCT", "LF_M_R1_FLOORS_255_COALESCED", "LF_M_R1_FLOORS_256")) {
            assertTrue(EscrowLoaderFloorProbe.selected(cut));
        }
        for (String cut : List.of("LF_M_R1_UNKNOWN_UNLOAD", "LF_M_K6", "LF_R1_FLOORS_256", "")) {
            assertFalse(EscrowLoaderFloorProbe.selected(cut));
        }
        assertFalse(EscrowLoaderFloorProbe.selected(null));
        for (String cut : List.of("LF_M_E2_EXTERNAL", "LF_M_R3_LEDGER_MISSING", "LF_M_R3_ENDPOINT_RELOCATED")) {
            assertTrue(EscrowLoaderRecoveryProbe.Hold.selected(cut));
        }
        for (String cut : List.of("LF_M_E1_OWN_STORE", "LF_M_E2_CAPTURE", "LF_M_K6", "LF_M_R1_FLOORS_256", "R3_LEDGER_MISSING")) {
            assertFalse(EscrowLoaderRecoveryProbe.Hold.selected(cut));
        }
    }

    private static EscrowOperation named(long sequence, long rocket, long epoch) {
        var op = EscrowTestFixtures.operation(sequence, rocket, epoch);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), new EscrowGuard(op.guard().rocket(), EscrowTestFixtures.id(9_000 + rocket), 1, op.guard().journalDigest()));
    }

    /** The seeded watermark, as the fixture writes it: every floor's earlier sequence is issued. */
    private static EscrowLedger ledger(List<EscrowOperation> records, List<CompoundTag> floors) {
        return new EscrowLedger(EscrowTestFixtures.id(500), floors.size(), 2, records, new ArrayList<>(floors), List.of());
    }
}
