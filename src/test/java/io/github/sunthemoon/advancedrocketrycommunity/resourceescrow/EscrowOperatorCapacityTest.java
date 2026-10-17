package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowOperatorCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void everyChoiceRefusesPermanentExhaustionWithoutEviction() {
        var receipts = new ArrayList<net.minecraft.nbt.CompoundTag>();
        for (int i = 1; i <= 64; i++) { receipts.add(EscrowTestFixtures.receipt(i)); }
        var floor = EscrowTestFixtures.floor(99); floor.putUUID("transfer_id", EscrowTestFixtures.id(800));
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(op), List.of(floor), receipts);
        var before = EscrowCodec.encode(root);
        for (var choice : EscrowSettlementIntent.Choice.values()) {
            var refusal = assertThrows(IllegalArgumentException.class, () -> {
                if (choice == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE) {
                    var withoutOp = root.replace(List.of(), root.floors(), root.tombstones());
                    EscrowSettlementIntent.floorPreview(withoutOp, EscrowTestFixtures.id(701), floor,
                            EscrowTestFixtures.id(700), "operator", 0, List.of());
                } else {
                    EscrowSettlementIntent.preview(root, op.id(), choice, EscrowTestFixtures.id(700), "operator", 0,
                            EscrowSettlementIntentTest.observations(root, choice == EscrowSettlementIntent.Choice.ABANDON));
                }
            });
            assertEquals(EscrowSettlementIntent.RECEIPTS_FULL, refusal.getMessage());
            assertEquals(before, EscrowCodec.encode(root));
        }
    }
    @Test void capacityCountsEveryMemberNotOnlyEachDecisionAndDoesNotDoubleCountO3() {
        var receipts = new ArrayList<net.minecraft.nbt.CompoundTag>();
        for (int i = 1; i <= 63; i++) { receipts.add(EscrowTestFixtures.receipt(i)); }
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 0, 0, List.of(), List.of(), receipts);
        EscrowSettlementIntent.requireReceiptRoom(root, List.of(EscrowTestFixtures.id(1)));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.requireReceiptRoom(root,
                List.of(EscrowTestFixtures.id(1), EscrowTestFixtures.id(2))));
        EscrowSettlementIntent.requireReceiptRoom(root, List.of(receipts.get(0).getUUID("op_id"), EscrowTestFixtures.id(1)));
        assertEquals(0, EscrowSettlementIntent.reservedReceipts(root));
    }
}
