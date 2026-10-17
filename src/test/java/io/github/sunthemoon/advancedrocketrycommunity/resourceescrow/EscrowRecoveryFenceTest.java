package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EscrowRecoveryFenceTest {
    private static final UUID LOGICAL = EscrowTestFixtures.id(7);
    private static final UUID TRANSFER = EscrowTestFixtures.id(8);
    private static final UUID ENTITY = EscrowTestFixtures.id(9);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void allSixActionsHavePendingTombstoneFloorAndNoFloorControls() {
        var empty = ledger(List.of(), List.of(), List.of());
        var pending = ledger(List.of(EscrowTestFixtures.operation(1, 7, 0)), List.of(), List.of());
        var tombstone = ledger(List.of(), List.of(), List.of(tombstone()));
        var matching = ledger(List.of(), List.of(floor(TRANSFER)), List.of());
        var other = ledger(List.of(), List.of(floor(EscrowTestFixtures.id(100))), List.of());
        var pristine = new EscrowRecoveryFence.Rocket(ENTITY, 0, false, true);
        var credited = new EscrowRecoveryFence.Rocket(ENTITY, 1, false, true);
        for (var action : RocketTransferRecoveryAction.values()) {
            boolean destination = switch (action) {
                case KEEP_DESTINATION, REMOVE_SOURCE_KEEP_DESTINATION, REBUILD_DESTINATION -> true;
                default -> false;
            };
            boolean rebuilding = action == RocketTransferRecoveryAction.REBUILD_SOURCE || action == RocketTransferRecoveryAction.REBUILD_DESTINATION;
            var sources = destination || rebuilding ? List.<EscrowRecoveryFence.Rocket>of() : List.of(pristine);
            var destinations = !destination || rebuilding ? List.<EscrowRecoveryFence.Rocket>of() : List.of(pristine);
            assertNull(EscrowRecoveryFence.reason(empty, LOGICAL, TRANSFER, action, sources, destinations), action.name());
            assertNull(EscrowRecoveryFence.reason(other, LOGICAL, TRANSFER, action, sources, destinations), action.name());
            assertEquals("ESCROW_PENDING", EscrowRecoveryFence.reason(pending, LOGICAL, TRANSFER, action, sources, destinations));
            assertEquals("TOMBSTONE", EscrowRecoveryFence.reason(tombstone, LOGICAL, TRANSFER, action, sources, destinations));
            assertEquals("FLOOR_CONFLICT", EscrowRecoveryFence.reason(matching, LOGICAL, TRANSFER, action, sources, destinations));
            if (destination && !rebuilding) {
                assertNull(EscrowRecoveryFence.reason(matching, LOGICAL, TRANSFER, action, List.of(), List.of(credited)), action.name());
            }
        }
    }

    @Test void duplicateRemovalAndHistoricalSourceOverwriteNeverDiscardCredit() {
        var pristine = new EscrowRecoveryFence.Rocket(EscrowTestFixtures.id(20), 0, false, true);
        var credited = new EscrowRecoveryFence.Rocket(ENTITY, 1, false, true);
        var empty = ledger(List.of(), List.of(), List.of());
        assertEquals("FLOOR_CONFLICT", EscrowRecoveryFence.reason(empty, LOGICAL, TRANSFER,
                RocketTransferRecoveryAction.KEEP_DESTINATION, List.of(), List.of(pristine, credited)));
        assertEquals("FLOOR_CONFLICT", EscrowRecoveryFence.reason(empty, LOGICAL, TRANSFER,
                RocketTransferRecoveryAction.REMOVE_SOURCE_KEEP_DESTINATION, List.of(credited), List.of(pristine)));
        assertEquals("FLOOR_CONFLICT", EscrowRecoveryFence.reason(empty, LOGICAL, TRANSFER,
                RocketTransferRecoveryAction.KEEP_SOURCE, List.of(credited), List.of()));
        assertNull(EscrowRecoveryFence.reason(empty, LOGICAL, TRANSFER,
                RocketTransferRecoveryAction.KEEP_DESTINATION, List.of(), List.of(credited)));
    }

    @Test void markerInvalidEpochRegressionAndWrongProtectedIdentityStayFenced() {
        var ledger = ledger(List.of(), List.of(floor(TRANSFER)), List.of());
        for (var rocket : List.of(new EscrowRecoveryFence.Rocket(ENTITY, 1, true, true),
                new EscrowRecoveryFence.Rocket(ENTITY, 1, false, false),
                new EscrowRecoveryFence.Rocket(ENTITY, 0, false, true),
                new EscrowRecoveryFence.Rocket(UUID.randomUUID(), 1, false, true))) {
            assertNotNull(EscrowRecoveryFence.reason(ledger, LOGICAL, TRANSFER,
                    RocketTransferRecoveryAction.KEEP_DESTINATION, List.of(), List.of(rocket)));
        }
        assertThrows(IllegalArgumentException.class, () -> new EscrowRecoveryFence.Rocket(ENTITY, -1, false, true));
    }

    @Test void startupClassificationUsesOnlyFrozenLedgerIdentities() {
        assertTrue(EscrowRecoveryFence.known(ledger(List.of(), List.of(floor(TRANSFER)), List.of()), LOGICAL, TRANSFER));
        assertFalse(EscrowRecoveryFence.known(ledger(List.of(), List.of(floor(TRANSFER)), List.of()), LOGICAL, UUID.randomUUID()));
        assertTrue(EscrowRecoveryFence.known(ledger(List.of(EscrowTestFixtures.operation(1, 7, 0)), List.of(), List.of()), LOGICAL, TRANSFER));
        assertTrue(EscrowRecoveryFence.known(ledger(List.of(), List.of(), List.of(tombstone())), LOGICAL, TRANSFER));
    }

    @Test void creditedAssemblyIsFencedButIdleCreditedDisassemblyRemainsAvailable() {
        var empty = ledger(List.of(), List.of(), List.of());
        var credited = List.of(new EscrowRecoveryFence.Rocket(ENTITY, 1, false, true));
        assertTrue(EscrowRecoveryFence.transaction(empty, LOGICAL, true, credited));
        assertFalse(EscrowRecoveryFence.transaction(empty, LOGICAL, false, credited));
        var pending = ledger(List.of(EscrowTestFixtures.operation(1, 7, 0)), List.of(), List.of());
        assertTrue(EscrowRecoveryFence.transaction(pending, LOGICAL, false, credited));
        assertTrue(EscrowRecoveryFence.transaction(empty, LOGICAL, false,
                List.of(new EscrowRecoveryFence.Rocket(ENTITY, 1, true, true))));
        assertTrue(EscrowRecoveryFence.transaction(ledger(List.of(), List.of(floor(TRANSFER)), List.of()), LOGICAL, true, List.of()));
        assertFalse(EscrowRecoveryFence.transaction(ledger(List.of(), List.of(floor(TRANSFER)), List.of()), LOGICAL, false, credited));
        assertTrue(EscrowRecoveryFence.transaction(ledger(List.of(), List.of(), List.of(tombstone())), LOGICAL, false, List.of()));
    }

    private static EscrowLedger ledger(List<EscrowOperation> records, List<CompoundTag> floors, List<CompoundTag> tombstones) {
        return new EscrowLedger(EscrowTestFixtures.id(500), 1, 1, records, floors, tombstones);
    }
    private static CompoundTag floor(UUID transfer) {
        var tag = EscrowCodec.guard(new EscrowGuard(LOGICAL, transfer, 1, EscrowTestFixtures.hash(1)));
        tag.putInt("schema_version", 2); tag.putUUID("entity_id", ENTITY); tag.putLong("resource_epoch", 1);
        tag.putString("signature_kind", EscrowSignatureKind.FIXTURE_RESOURCE_V1.name()); tag.putString("invariant_digest", EscrowTestFixtures.hash(3));
        tag.putString("signature", EscrowTestFixtures.hash(2)); tag.putLong("highest_seq", 1); return tag;
    }
    private static CompoundTag tombstone() {
        var tag = EscrowTestFixtures.receipt(22);
        var guards = new ListTag(); guards.add(EscrowCodec.guard(new EscrowGuard(LOGICAL, TRANSFER, 1, EscrowTestFixtures.hash(1))));
        tag.put("guards", guards); return tag;
    }
}
