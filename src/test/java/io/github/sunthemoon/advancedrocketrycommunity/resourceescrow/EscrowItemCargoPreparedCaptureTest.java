package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTicketTestFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
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

    /** W118-R1: item stage 0 reserves for the largest legal container, so every legal size prepares through the ticket. */
    @Test void chestsJustAboveTheOldEdgeAndAtTheLargestLegalPayloadReachReadyThroughTheTicket() {
        for (int target : List.of(17_000, RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES)) {
            for (boolean load : List.of(true, false)) {
                var before = padded(load, target); var delta = move(before, load);
                var expected = RocketItemCargoMutation.apply(before, delta, load);
                assertEquals(target, Math.max(containerBytes(before), containerBytes(expected)), "load=" + load);
                try (var session = new CargoTicketTestFixture(UUID.randomUUID(), before, delta, load)) {
                    long tick = 1;
                    while (!session.advance(tick++)) {
                        assertTrue(tick < 100); assertTrue(session.budget().chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES);
                    }
                    assertEquals(RocketCargoPreparation.State.READY, session.cursor().state(), target + " load=" + load);
                    assertEquals(RocketSnapshotNbtCodec.encode(expected), RocketSnapshotNbtCodec.encode(session.cursor().after()));
                }
            }
        }
    }

    /** W118-R1: a step that reports more work than it reserved ends with a status, not an exception out of the ticket. */
    @Test void aStepOverItsReservationReportsCargoTooLargeInsteadOfThrowingOutOfTheTicket() {
        var c = EscrowItemCargoEligibilityTest.sample(true, 5, EscrowItemCargoEligibilityTest.stone(-1));
        var forged = snapshot(c.before());
        // Fault injection: no codec builds a payload whose cached size is past the 256 KiB payload limit.
        UnstartedServer.set(RocketBlockEntityPayload.class, container(forged), "uncompressedBytes", RocketLimits.MAX_TOTAL_NBT_BYTES);
        try (var session = new CargoTicketTestFixture(c.op().guard().rocket(), forged, EscrowItemCargoPayload.decode(c.op().payload()), true)) {
            assertTrue(assertDoesNotThrow(() -> session.advance(1)));
            assertEquals(RocketCargoPreparation.State.DISCARDED, session.cursor().state());
            assertEquals(Status.CARGO_TOO_LARGE, session.cursor().failure().orElseThrow());
            assertFalse(session.ticket.valid());
        }
        assertEquals(Status.CARGO_TOO_LARGE, staged(c, forged, c.deviceAfter()));
    }

    /** A chest whose tagged stone in slot 1 brings the larger container payload of the move to exactly {@code target} bytes. */
    private static RocketStructureSnapshot padded(boolean load, int target) {
        int blob = 1_000;
        for (int pass = 0; pass < 2; pass++) {
            var before = chest(load, blob);
            blob += target - Math.max(containerBytes(before), containerBytes(RocketItemCargoMutation.apply(before, move(before, load), load)));
        }
        return chest(load, blob);
    }
    private static RocketStructureSnapshot chest(boolean load, int blob) {
        var padding = ItemCargoTestFixtures.stack(Items.STONE, 1); var tag = new CompoundTag();
        tag.putByteArray("blob", new byte[blob]); padding.put("tag", tag);
        var entries = new ArrayList<CompoundTag>();
        if (!load) { entries.add(ItemCargoTestFixtures.entry(0, ItemCargoTestFixtures.stack(Items.STONE, 5))); }
        entries.add(ItemCargoTestFixtures.entry(1, padding));
        return ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.CHEST, entries.toArray(CompoundTag[]::new));
    }
    private static EscrowItemCargoPayload move(RocketStructureSnapshot before, boolean load) {
        return ItemCargoTestFixtures.delta(before, 0, ItemCargoTestFixtures.stack(Items.STONE, load ? 5 : 2), 0, 0);
    }
    private static RocketBlockEntityPayload container(RocketStructureSnapshot value) {
        return value.blocks().stream().filter(block -> block.position().equals(new RocketPosition(0, 0, 0))).findFirst().orElseThrow()
                .blockEntityPayload().orElseThrow();
    }
    private static int containerBytes(RocketStructureSnapshot value) { return container(value).uncompressedBytes(); }

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
