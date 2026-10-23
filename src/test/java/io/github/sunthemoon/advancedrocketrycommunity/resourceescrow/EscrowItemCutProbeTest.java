package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemCutProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void fixtureFuelPassesItsSelectedPropulsionBankRoleCheck() {
        var empty = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(EscrowItemCutProbe.PROPULSION);
        var fuel = assertDoesNotThrow(() -> EscrowItemCutProbe.cargoFuel(empty));
        var vector = fuel.propulsion().orElseThrow();
        assertEquals(RocketPropulsion.Mode.MONO, vector.descriptor().mode());
        assertEquals(RocketPropulsion.Settings.DEFAULTS, vector.descriptor().settings());
        assertEquals(ResourceLocation.tryParse("advancedrocketrycommunity:rocket_fuel"), vector.primary().fluidId());
        assertEquals(1_000, vector.primary().amount());
        assertEquals(RocketFuelBank.Branch.FLUID, vector.primary().branch());
        assertEquals(RocketFuelBank.EMPTY, vector.oxidizer());
        assertEquals(1, vector.revision());
        assertThrows(IllegalArgumentException.class, () -> empty.withFluidBank(false,
                ResourceLocation.tryParse("minecraft:water"), null, 1_000));
    }
    @Test void probeNeedsHooksExactCaseAndScenario() {
        var cases = new java.util.ArrayList<>(EscrowItemSettlementCuts.CASES.keySet()); cases.addAll(EscrowItemRecoveryCuts.CASES);
        assertEquals(18, cases.size());
        for (var name : cases) {
            for (var scenario : List.of("start", "recover")) {
                assertTrue(EscrowItemCutProbe.selected(true, scenario, name));
                assertFalse(EscrowItemCutProbe.selected(false, scenario, name));
                assertFalse(EscrowReleaseProbe.permitsCase(name));
            }
            assertFalse(EscrowItemCutProbe.selected(true, null, name));
            assertFalse(EscrowItemCutProbe.selected(true, "ordinary", name));
        }
        for (String name : new String[] {null, "", "IS_UNKNOWN", "FS_O1_AFTER", "IC_LOAD_K2"}) {
            assertFalse(EscrowItemCutProbe.selected(true, "start", name));
        }
    }
    @Test void genericConstructorExcludesOnlyItemCutsWithoutRestrictingLegacyNames() {
        var cases = new java.util.ArrayList<>(List.of("G3", "G3L2", "E2", "E2_CAPTURE", "E4L1_FAIL", "E4L1_UNKNOWN",
                "E4L2_FAIL", "E4L2_UNKNOWN", "E4L2_RESAVE"));
        for (int index = 0; index <= 10; index++) { cases.add("K" + index); }
        assertEquals(20, cases.size());
        for (String name : cases) {
            for (String scenario : List.of("start", "recover")) {
                assertTrue(EscrowReleaseProbe.permitsCase(name));
                assertFalse(EscrowItemCutProbe.selected(true, scenario, name));
            }
        }
        for (String name : new String[] {null, "", "none", "K11", "K01", "C1", "C2", "C3", "E5", "BENCH",
                "E1", "E3", "FUTURE_NATIVE_CASE", "IS_UNKNOWN", "IR_UNKNOWN", "FS_O1_AFTER", "IC_LOAD_K2", "SR_O1", "SO_O1"}) {
            for (String scenario : List.of("start", "recover")) {
                assertTrue(EscrowReleaseProbe.permitsCase(name));
                assertFalse(EscrowItemCutProbe.selected(true, scenario, name));
            }
        }
    }
    @Test void preparedBaselineRequiresTheIntendedNativeItemDirectionAndPhase() {
        for (boolean load : List.of(false, true)) {
            var op = EscrowItemReconciliationFixture.create(load).op();
            var kind = load ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD;
            assertDoesNotThrow(() -> requireBaseline(op, kind));
            assertThrows(IllegalArgumentException.class, () -> requireBaseline(op,
                    load ? EscrowOperation.Kind.ITEM_CARGO_UNLOAD : EscrowOperation.Kind.ITEM_CARGO_LOAD));
            assertThrows(IllegalArgumentException.class, () -> requireBaseline(op.withReason(EscrowReason.OTHER_STATE), kind));
            var incomplete = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                    op.payload(), op.payloadHash(), op.source(), op.destination(), op.guard(), op.kind(), op.settlement());
            assertThrows(IllegalArgumentException.class, () -> requireBaseline(incomplete, kind));
        }
        var fluid = EscrowTestFixtures.nativeModel(EscrowTestFixtures.operation(1));
        assertThrows(IllegalArgumentException.class, () -> requireBaseline(fluid, EscrowOperation.Kind.ITEM_CARGO_UNLOAD));
        assertThrows(IllegalArgumentException.class, () -> requireBaseline(fluid, fluid.kind()));
    }
    @Test void preparedBaselineRejectsEveryForeignFixtureIdentity() {
        var op = EscrowItemReconciliationFixture.create(false).op();
        var expected = List.of(op.device().identity(), op.rocket().identity(), op.rocket().logicalRocket(),
                op.owner(), op.nativeBinding().snapshot());
        for (int index = 0; index < expected.size(); index++) {
            var foreign = new java.util.ArrayList<>(expected); foreign.set(index, UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> EscrowItemCutProbe.requirePreparedItem(op, op.kind(),
                    foreign.get(0), foreign.get(1), foreign.get(2), foreign.get(3), foreign.get(4)));
        }
    }
    private static void requireBaseline(EscrowOperation op, EscrowOperation.Kind kind) {
        EscrowItemCutProbe.requirePreparedItem(op, kind, op.device().identity(), op.rocket().identity(),
                op.rocket().logicalRocket(), op.owner(), op.nativeBinding().snapshot());
    }
    @Test void journalReplacementRequiresExactStartGate() {
        assertTrue(EscrowItemCutProbe.journalCut(true, "start", "IS_O3J_AFTER"));
        assertFalse(EscrowItemCutProbe.journalCut(false, "start", "IS_O3J_AFTER"));
        assertFalse(EscrowItemCutProbe.journalCut(true, "recover", "IS_O3J_AFTER"));
        assertFalse(EscrowItemCutProbe.journalCut(true, "start", "IS_O3_AFTER"));
        assertNull(EscrowItemCutProbe.journalPolicy(null, null));
    }
    @Test void afterWriteCutsSelectReplacementNotAcknowledgement() {
        assertTrue(EscrowItemSettlementCuts.pauses("O1_AFTER", "K2", "", true, -1, -1));
        assertFalse(EscrowItemSettlementCuts.pauses("O1_AFTER", "K1", "", true, -1, -1));
        assertFalse(EscrowItemSettlementCuts.pauses("O1_AFTER", "K2", "", false, -1, -1));
        assertTrue(EscrowItemSettlementCuts.pauses("O3_AFTER", "K2", "O3", false, -1, -1));
        assertFalse(EscrowItemSettlementCuts.pauses("O3_AFTER", "K2", "O4", false, -1, -1));
        assertTrue(EscrowItemSettlementCuts.pauses("O4_AFTER", "K9", "O4", false, -1, -1));
        for (var side : List.of("ROCKET", "DEVICE")) {
            for (var when : List.of("BEFORE", "AFTER")) {
                int index = side.equals("ROCKET") ? 1 : 0;
                assertTrue(EscrowItemSettlementCuts.pauses("O2_" + side + "_" + when, "O2_" + when + "_" + index, "", false, 1, 0));
                assertFalse(EscrowItemSettlementCuts.pauses("O2_" + side + "_" + when, "O2_" + when + "_" + (1 - index), "", false, 1, 0));
            }
        }
    }
    @Test void candidatesAreCodecValidTypedAndNeverMutateTheLiveSnapshot() {
        assertNull(EscrowItemRecoveryCuts.candidate(false, "IR_LOOT", null, null));
        assertNull(EscrowItemRecoveryCuts.candidate(true, "OTHER", null, null));
        var base = EscrowCargoPreparationGameTests.snapshot(ResourceLocation.tryParse("minecraft:overworld"), BlockPos.ZERO, false, List.of());
        var stack = ItemCargoReconciliationFixtures.stack(10); stack.putByte("Slot", (byte) 0); var entries = new ListTag(); entries.add(stack);
        var payload = new CompoundTag(); payload.put("Items", entries);
        var block = new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"),
                Map.of("facing", "north", "type", "single", "waterlogged", "false")), new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, payload));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), base.sourceDimension(), base.sourceOrigin(), List.of(block),
                List.of(block.position()), new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(1, 100, 1000, 2000, 1, 1, 1, 1),
                25, base.propulsion().orElseThrow());
        var before = RocketSnapshotNbtCodec.encode(snapshot); var physical = UUID.randomUUID();
        for (var name : List.of("IR_CONTAINER", "IR_LOOT")) {
            var candidate = EscrowItemRecoveryCuts.candidate(true, name, snapshot, physical);
            assertEquals(before, candidate.getCompound("before")); assertEquals(physical, candidate.getUUID("physical"));
            var decoded = RocketSnapshotNbtCodec.decode(candidate.getCompound("after")).snapshot().orElseThrow();
            assertEquals(snapshot.snapshotId(), decoded.snapshotId()); assertEquals(snapshot.stats(), decoded.stats());
            assertEquals(snapshot.propulsion(), decoded.propulsion()); assertNotEquals(snapshot.contentHash(), decoded.contentHash());
            var changed = decoded.blocks().get(0);
            if (name.equals("IR_CONTAINER")) {
                assertEquals("minecraft:barrel", changed.state().blockId().toString()); assertEquals(block.blockEntityPayload(), changed.blockEntityPayload());
            } else {
                assertEquals(block.state(), changed.state()); var loot = changed.blockEntityPayload().orElseThrow().data();
                assertFalse(loot.contains("Items")); assertTrue(loot.contains("LootTableSeed", 4)); assertEquals(143, loot.getLong("LootTableSeed"));
            }
            assertEquals(before, RocketSnapshotNbtCodec.encode(snapshot));
        }
    }
    @Test void disassemblyReportsRefusalWithoutChangingItsMeaning() {
        String command = "/arce rocket-disassembly confirm " + UUID.randomUUID();
        var returned = new java.util.ArrayList<Integer>();
        EscrowItemDisassemblyRequest.confirm(command, value -> 1, returned::add); assertEquals(List.of(1), returned);
        assertThrows(IllegalArgumentException.class, () -> EscrowItemDisassemblyRequest.confirm(command, value -> 0, returned::add));
        assertEquals(List.of(1, 0), returned);
        assertThrows(IllegalArgumentException.class, () -> EscrowItemDisassemblyRequest.confirm(null, value -> fail("Missing offer dispatched"), returned::add));
    }
}
