package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCombinedReservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void floorO1CannotConsumeOtherComponentsReceiptOrFlightReservations() {
        var repairs = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 63; i++) { repairs.add(EscrowTestFixtures.operation(i).withReason(EscrowReason.RELOCATED_ENDPOINT)); }
        var floors = new ArrayList<CompoundTag>(); EscrowLedger lastFitting = null;
        for (int count = 1; count <= 64; count++) {
            var floor = EscrowTestFixtures.floor(10_000 + count); floor.putUUID("transfer_id", EscrowTestFixtures.id(20_000 + count)); floors.add(floor);
            var root = new EscrowLedger(EscrowTestFixtures.id(500), 63, 0, repairs, floors, List.of());
            try { EscrowSettlementCapacity.preflight(root); lastFitting = root; }
            catch (IllegalArgumentException full) { break; }
        }
        assertNotNull(lastFitting); var root = lastFitting;
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
        var floor = root.floors().get(0);
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "advancedrocketrycommunity:rocket", 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000), floor, EscrowTestFixtures.id(700), "operator", 10, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 210, observations);
        var candidate = root.append(List.of(intent.floorOperation(64, 210)));
        assertDoesNotThrow(() -> EscrowFloors.preflight(candidate));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(candidate));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("refused"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(210);
            assertThrows(IllegalArgumentException.class, () -> coordinator.beginSettlement(intent, ignored -> fail()));
            assertEquals(root, coordinator.ledger()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
        }
        System.out.println("combined reservation last-fitting floors=" + root.floors().size());
    }
}
