package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTicketTestFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W112's session-owned tickets, budget and staged capture with an item delta; no native restart or proof evidence. */
class EscrowItemCargoPreparedCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void stagedItemCaptureMatchesSynchronousEligibilityAndQueuesInBothDirections() {
        for (boolean load : List.of(true, false)) {
            var c = EscrowItemCargoEligibilityTest.sample(load, 37, EscrowItemCargoEligibilityTest.stone(-1));
            var root = EscrowTestFixtures.ledger();
            var expected = EscrowItemCargoEligibility.check(root, List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
            var before = snapshot(c.before()); var delta = EscrowItemCargoPayload.decode(c.op().payload());
            var session = new CargoTicketTestFixture(c.op().guard().rocket(), before, delta, load);
            long tick = 1;
            var budget = session.budget();
            while (!session.advance(tick++)) { assertTrue(tick < 100); }
            assertEquals(RocketCargoPreparation.State.READY, session.cursor().state());
            assertEquals(RocketItemCargoMutation.apply(before, delta, load).contentHash(), session.cursor().after().contentHash());
            assertEquals(before.structureDigest(), session.cursor().after().structureDigest());
            var capture = new EscrowCargoPreparedCapture(session.ticket, c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 0);
            while (!advance(session, capture, tick++, root)) {
                assertTrue(tick < 100); assertTrue(budget.chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES);
                assertTrue(budget.chargedVisits() <= RocketCargoWorkBudget.MAX_VISITS);
            }
            assertEquals(Status.ELIGIBLE, capture.result().status());
            assertEquals(expected.capture(), capture.result().capture());
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(temp.resolve("item-" + load), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                session.at(tick);
                assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareCargo(capture, () -> true, ignored -> { }));
                assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
                assertEquals(RocketCargoPreparation.State.DISCARDED, session.cursor().state());
            }
            session.close();
        }
    }

    @Test void changedItemDeviceInputIsStaleWithoutSideEffects() {
        var c = EscrowItemCargoEligibilityTest.sample(true, 5, EscrowItemCargoEligibilityTest.stone(-1));
        var root = EscrowTestFixtures.ledger();
        var session = new CargoTicketTestFixture(c.op().guard().rocket(), snapshot(c.before()),
                EscrowItemCargoPayload.decode(c.op().payload()), true);
        long tick = 1;
        while (!session.advance(tick++)) { assertTrue(tick < 100); }
        var capture = new EscrowCargoPreparedCapture(session.ticket, c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 0);
        while (!advance(session, capture, tick++, root)) { assertTrue(tick < 100); }
        assertEquals(Status.ELIGIBLE, capture.result().status());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("stale"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            c.deviceBefore().getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putInt("changed", 1);
            session.at(tick);
            assertEquals(EscrowCoordinator.Transfer.STALE, coordinator.prepareCargo(capture, () -> true, ignored -> fail()));
            assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertFalse(data.isDirty()); assertEquals(RocketCargoPreparation.State.DISCARDED, session.cursor().state());
        }
    }

    @Test void stagedRefusalsKeepTheirStatusAndAdmitNothing() {
        var c = EscrowItemCargoEligibilityTest.sample(true, 5, EscrowItemCargoEligibilityTest.stone(-1));
        var lootData = new CompoundTag(); lootData.putString("LootTable", "minecraft:chests/simple_dungeon");
        var loot = ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.block(0, ItemCargoTestFixtures.state(ItemCargoTestFixtures.CHEST),
                new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, lootData)));
        var half = ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.block(0, new RocketBlockState(ItemCargoTestFixtures.CHEST,
                Map.of("facing", "north", "type", "left", "waterlogged", "false")), ItemCargoTestFixtures.container(null, null)));
        assertEquals(Status.UNRESOLVED_LOOT, staged(c, loot, c.deviceAfter()));
        assertEquals(Status.NO_CARGO_STORAGE, staged(c, half, c.deviceAfter()));
        var oversized = c.deviceAfter().copy();
        oversized.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[32_700]);
        var before = c.deviceBefore().copy();
        before.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[32_700]);
        var root = EscrowTestFixtures.ledger();
        assertEquals(Status.CARGO_TOO_LARGE, EscrowItemCargoEligibility.check(root, List.of(), c.op(), c.before(), c.after(), before, oversized).status());
        assertEquals(Status.CARGO_TOO_LARGE, staged(new EscrowItemCargoEligibilityTest.Candidate(c.op(), c.before(), c.after(), before, oversized),
                snapshot(c.before()), oversized));
    }

    /** Runs the ticket and the staged capture to a result, then checks that final admission issues nothing. */
    private Status staged(EscrowItemCargoEligibilityTest.Candidate c, RocketStructureSnapshot before, CompoundTag deviceAfter) {
        var root = EscrowTestFixtures.ledger();
        var session = new CargoTicketTestFixture(c.op().guard().rocket(), before, EscrowItemCargoPayload.decode(c.op().payload()), true);
        long tick = 1;
        while (!session.advance(tick++)) { assertTrue(tick < 100); }
        var capture = new EscrowCargoPreparedCapture(session.ticket, c.op(), c.before(), c.after(), c.deviceBefore(), deviceAfter, 0);
        while (!advance(session, capture, tick++, root)) { assertTrue(tick < 100); }
        var status = capture.result().status();
        assertNull(capture.result().capture());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("refusal-" + status + "-" + tick), 0);
             var coordinator = new EscrowCoordinator(data, writer)) {
            session.at(tick);
            assertNotEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareCargo(capture, () -> true, ignored -> fail()));
            assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertFalse(data.isDirty()); assertEquals(RocketCargoPreparation.State.DISCARDED, session.cursor().state());
        }
        session.close();
        return status;
    }
    private static RocketStructureSnapshot snapshot(CompoundTag envelope) {
        return RocketSnapshotNbtCodec.decode(envelope.getCompound("snapshot")).snapshot().orElseThrow();
    }
    private static boolean advance(CargoTicketTestFixture session, EscrowCargoPreparedCapture capture, long tick, EscrowLedger root) {
        session.at(tick); return capture.advance(root, List.of());
    }
}
