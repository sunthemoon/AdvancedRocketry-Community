package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLayoutBudgetTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void fullReadyAndArmedTablesIncludingTerminalSlotsRoundTripAndRejectThe65th() {
        var receipts = new ArrayList<CompoundTag>(); var heads = new ArrayList<EscrowFlightTables.Head>(); var arms = new ArrayList<EscrowFlightTables.Armed>();
        var ledgerId = EscrowTestFixtures.id(500);
        for (int i = 1; i <= 64; i++) {
            var receipt = EscrowTestFixtures.receipt(i); receipt.putBoolean("released", true); receipt.putLong("release_generation", 1); receipts.add(receipt);
            var logical = receipt.getList("guards", 10).getCompound(0).getUUID("rocket_id");
            var owner = EscrowTestFixtures.id(700); var physical = EscrowTestFixtures.id(1_000 + i);
            var head = new EscrowFlightTables.Head(logical, owner, receipt.getUUID("op_id"), 1, physical, Long.MAX_VALUE,
                    EscrowSignatureKind.FUEL_VECTOR_V1, EscrowTestFixtures.hash(i), EscrowTestFixtures.hash(i + 1), i, i,
                    EscrowFlightTables.Outcome.HANDED_OFF); heads.add(head);
            var arm = new EscrowFlightTables.Armed(ledgerId, logical, owner, physical, Long.MAX_VALUE, EscrowTestFixtures.id(2_000 + i), i, 2,
                    EscrowTestFixtures.hash(i), EscrowTestFixtures.hash(i), EscrowTestFixtures.hash(i), EscrowTestFixtures.hash(i),
                    EscrowTestFixtures.id(3_000 + i), new EscrowGuard(logical, EscrowTestFixtures.id(4_000 + i), Long.MAX_VALUE, EscrowTestFixtures.hash(i)),
                    head.anchor(), 1, EscrowFlightTables.Outcome.CANCELLED); arms.add(arm);
            assertEquals(head, EscrowFlightTables.Head.decode(head.encode())); assertEquals(arm, EscrowFlightTables.Armed.decode(arm.encode()));
            assertTrue(RocketNbtSize.uncompressedBytes(head.encode()) <= 1_024); assertTrue(RocketNbtSize.uncompressedBytes(arm.encode()) <= 1_024);
        }
        var root = new EscrowLedger(ledgerId, 0, 2, List.of(), List.of(), receipts, 64, heads, arms);
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root))); assertEquals(64, root.armedFlights().size()); assertEquals(64, root.rocketAuthorities().size());
        assertDoesNotThrow(() -> EscrowLedgerWriter.Snapshot.of(root, java.util.Set.of()));
        var small = EscrowCodec.encode(EscrowTestFixtures.ledger());
        assertEquals(RocketNativeResources.signature(small), EscrowCodec.digest(small));
        var tooMany = new ArrayList<>(arms); tooMany.add(arms.get(0));
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(ledgerId, 0, 2, List.of(), List.of(), receipts, 64, heads, tooMany));
        var tooManyHeads = new ArrayList<>(heads); tooManyHeads.add(heads.get(0));
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(ledgerId, 0, 2, List.of(), List.of(), receipts, 64, tooManyHeads, arms));
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(ledgerId, 0, 2, List.of(), List.of(), receipts, 63, heads, arms));
        assertEquals(64, root.armedFlights().size());
        report("READY", heads.get(0).encode()); report("ARMED-terminal-with-anchor", arms.get(0).encode()); report("root-full-tables", EscrowCodec.encode(root));
    }
    @Test void maximumNativeFluidTagCannotReachL1WithoutItsCompleteEmbeddedSettlementReservation() {
        // The widest native fuel reference (120 Level characters) and a transfer guard remain encodable.
        var original = EscrowReceiptChargeTest.fill(1, 99, 0, 0, "a:" + "b".repeat(118), true, -1);
        var payload = original.payload(); var nativeTag = new CompoundTag();
        nativeTag.putByteArray("blob", new byte[0]); payload.put("Tag", nativeTag);
        nativeTag.putByteArray("blob", new byte[8_192 - RocketNbtSize.uncompressedBytes(payload)]);
        assertEquals(8_192, RocketNbtSize.uncompressedBytes(payload));
        var tooLarge = new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(), original.phase(), original.reason(),
                payload, RocketNativeResources.signature(payload), original.source(), original.destination(), original.guard(),
                original.kind(), original.settlement(), original.nativeBinding());
        var data = ResourceEscrowSavedData.create(); var before = data.ledger();
        try (var writer = new EscrowLedgerWriter(directory.resolve("escrow.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(tooLarge, RocketEscrowSection.neutral(),
                    ignored -> fail("Rejected admission must not issue a callback")));
            assertEquals(before, coordinator.ledger()); assertEquals(1, coordinator.nextSequence()); assertFalse(coordinator.commitPreparations());
            assertTrue(writer.capturedOperations().isEmpty());
        }
        assertFalse(java.nio.file.Files.exists(directory.resolve("escrow.dat")));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(EscrowTestFixtures.ledger(original)));
        report("binding", original.nativeBinding().encode()); report("operation-max-fluid-unsettled", EscrowCodec.operation(tooLarge));
    }
    @Test void connectingAnotherBankReservesTheCompleteSharedComponentBeforeAnyMutation() {
        var operations = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 8; i++) {
            operations.add(EscrowReceiptChargeTest.fill(i, 99, i - 1, i - 1, "a:" + "b".repeat(118), true, -1));
        }
        var last = EscrowTestFixtures.ledger(operations.subList(0, 5).toArray(EscrowOperation[]::new));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(last));
        var next = EscrowTestFixtures.ledger(operations.subList(0, 6).toArray(EscrowOperation[]::new));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(next));
        var root = EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new));
        var before = EscrowCodec.encode(root);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root));
        assertEquals(before, EscrowCodec.encode(root)); assertEquals(8, root.records().size());
    }
    private static void report(String name, CompoundTag tag) {
        System.out.println(name + " bytes=" + RocketNbtSize.uncompressedBytes(tag) + " nodes=" + nodes(tag) + " depth=" + depth(tag));
        for (var key : tag.getAllKeys().stream().sorted().toList()) {
            var item = new CompoundTag(); item.put(key, tag.get(key));
            // 4 bytes are the unnamed compound header and END; remaining bytes include each field's name/type/payload.
            System.out.println("  " + key + "=" + (RocketNbtSize.uncompressedBytes(item) - 4));
        }
    }
    private static int nodes(Tag tag) {
        int total = 1;
        if (tag instanceof CompoundTag compound) { for (var key : compound.getAllKeys()) { total += nodes(compound.get(key)); } }
        else if (tag instanceof net.minecraft.nbt.ListTag list) { for (var item : list) { total += nodes(item); } }
        return total;
    }
    private static int depth(Tag tag) {
        int result = 0;
        if (tag instanceof CompoundTag compound) { for (var key : compound.getAllKeys()) { result = Math.max(result, depth(compound.get(key))); } }
        else if (tag instanceof net.minecraft.nbt.ListTag list) { for (var item : list) { result = Math.max(result, depth(item)); } }
        return result + 1;
    }
}
