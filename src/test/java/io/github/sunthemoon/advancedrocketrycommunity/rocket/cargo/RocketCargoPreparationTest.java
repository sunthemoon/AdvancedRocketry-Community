package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotWitness;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketCargoPreparationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void productionCursorApiRequiresATicketAndDoesNotAcceptCallerBudgets() throws Exception {
        assertEquals(0, RocketCargoPreparation.class.getConstructors().length);
        for (String method : List.of("advance", "close")) {
            assertTrue(Arrays.stream(RocketCargoPreparation.class.getMethods()).noneMatch(value -> value.getName().equals(method)));
        }
        assertTrue(Arrays.stream(RocketCargoPreparations.Ticket.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes())).noneMatch(RocketCargoWorkBudget.class::equals));
    }

    @Test void ticketReservationsShareTheSessionBudgetAndExpireWithoutDriverPolling() {
        var before = CargoTestFixtures.snapshot(FluidStack.EMPTY);
        try (var session = new CargoTicketTestFixture(UUID.randomUUID(), before,
                CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1)), true)) {
            session.at(1);
            assertTrue(session.budget().charge(1, RocketCargoWorkBudget.MAX_BYTES, RocketCargoWorkBudget.MAX_VISITS, false));
            assertFalse(session.ticket.advance());
            assertFalse(session.ticket.performWork(1, 1, () -> { fail("Escaped session budget"); return null; }));
            session.at(39); assertTrue(session.ticket.valid());
            session.at(40); assertFalse(session.ticket.valid());
            assertFalse(session.ticket.performWork(1, 1, () -> { fail("Expired ticket ran work"); return null; }));
            assertEquals(RocketCargoPreparation.State.DISCARDED, session.cursor().state());
        }
    }

    @Test void stagedFullSnapshotBoundaryMatchesSynchronousRefusalAndDiscardsRam() {
        var seed = paddedSnapshot(40_000);
        int padding = 40_000 + 1_048_572 - RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(seed));
        var before = paddedSnapshot(padding);
        var encoded = RocketSnapshotNbtCodec.encode(before);
        assertEquals(1_048_572, RocketNbtSize.uncompressedBytes(encoded));
        assertTrue(RocketSnapshotNbtCodec.decode(encoded).snapshot().isPresent());
        var delta = CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1));
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                assertThrows(RocketSnapshotException.class, () -> RocketCargoMutation.apply(before, delta, true)).code());
        var cursor = new RocketCargoPreparation(UUID.randomUUID(), before, delta, true);
        var budget = new RocketCargoWorkBudget(); int tick = 0;
        while (!cursor.advance(tick++, budget)) { assertTrue(tick < 50); }
        assertEquals(RocketCargoSelection.Status.CARGO_TOO_LARGE, cursor.failure().orElseThrow());
        assertEquals(RocketCargoPreparation.State.DISCARDED, cursor.state());
        assertEquals(0, cursor.retainedNativeBytes());
        assertEquals(encoded, RocketSnapshotNbtCodec.encode(before));

        // The adjacent legal result retains the same 1 MiB ceiling, not a smaller admission limit.
        var legalBefore = paddedSnapshot(padding - 100);
        var legalDelta = CargoTestFixtures.delta(legalBefore, 0, new FluidStack(Fluids.WATER, 1));
        var after = RocketCargoMutation.apply(legalBefore, legalDelta, true);
        int afterSize = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(after));
        legalBefore = paddedSnapshot(padding - 100 + 1_048_576 - afterSize);
        legalDelta = CargoTestFixtures.delta(legalBefore, 0, new FluidStack(Fluids.WATER, 1));
        cursor = new RocketCargoPreparation(UUID.randomUUID(), legalBefore, legalDelta, true);
        budget = new RocketCargoWorkBudget(); tick = 0;
        while (!cursor.advance(tick++, budget)) { assertTrue(tick < 50); }
        assertTrue(cursor.failure().isEmpty());
        assertEquals(1_048_576, RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(cursor.after())));
        cursor.close();
    }

    @Test void malformedStagedDeltaReturnsInvalidDataAndReleasesNativeState() {
        var before = CargoTestFixtures.snapshot(FluidStack.EMPTY);
        var delta = CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1));
        var cursor = new RocketCargoPreparation(UUID.randomUUID(), before, delta, false);
        assertTrue(cursor.advance(0, new RocketCargoWorkBudget()));
        assertEquals(RocketCargoSelection.Status.INVALID_DATA, cursor.failure().orElseThrow());
        assertEquals(RocketCargoPreparation.State.DISCARDED, cursor.state());
        assertEquals(0, cursor.retainedNativeBytes());
    }

    @Test void stagedPayloadAggregateUsesTheTypedPayloadBoundFailure() {
        var before = paddedSnapshot(40_000);
        var payload = new net.minecraft.nbt.CompoundTag(); payload.putByteArray("padding", new byte[262_000]);
        var cursor = new RocketSnapshotPreparation(before, before.blocks().size() - 1,
                new RocketBlockEntityPayload(ResourceLocation.tryParse("fixture:padding"), payload));
        var failure = assertThrows(RocketSnapshotException.class, () -> {
            while (!cursor.done()) { cursor.step(); }
        });
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE,
                failure.code());
    }

    private static RocketStructureSnapshot paddedSnapshot(int tail) {
        var blocks = new ArrayList<>(CargoTestFixtures.snapshot(FluidStack.EMPTY).blocks());
        for (int i = 0; i < 5; i++) {
            var data = new net.minecraft.nbt.CompoundTag(); data.putByteArray("padding", new byte[i < 4 ? 250_000 : tail]);
            blocks.add(new RocketBlock(new RocketPosition(i + 2, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:barrel"), Map.of()),
                    new RocketBlockEntityPayload(ResourceLocation.tryParse("fixture:padding"), data)));
        }
        return CargoTestFixtures.snapshot(blocks);
    }

    static RocketStructureSnapshot large(boolean tank) {
        var properties = new TreeMap<String, String>();
        for (int i = 0; i < 32; i++) { properties.put(String.format("%02d", i) + "k".repeat(62), "v".repeat(64)); }
        var state = new RocketBlockState(ResourceLocation.tryParse("fixture:palette_shared"), properties);
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 2048; i++) {
            var pos = new RocketPosition(i % 16, i / 256, i / 16 % 16);
            blocks.add(tank && i == 0 ? new RocketBlock(pos, new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                    PressurizedTankRocketAdapter.payload(FluidStack.EMPTY)) : new RocketBlock(pos, state));
        }
        return CargoTestFixtures.snapshot(blocks);
    }

    @Test void bothW106FixturesMatchOnePassAtManySplitPoints() {
        for (boolean tank : List.of(false, true)) {
            var before = large(tank);
            for (int split : List.of(1, 2, 7, 31, 127, 511, 1023, 2048)) {
                var cursor = new RocketSnapshotPreparation(before, -1, null);
                int steps = 0;
                while (!cursor.done()) {
                    long bound = cursor.nextByteBound(); int visits = cursor.nextVisits();
                    var used = cursor.step();
                    assertTrue(used.bytes() <= bound); assertEquals(visits, used.visits());
                    if (++steps % split == 0 && !cursor.done()) {
                        assertThrows(IllegalStateException.class, cursor::result);
                        assertTrue(cursor.blockIndex() <= before.blocks().size());
                    }
                }
                assertEquals(before.contentHash(), cursor.result().contentHash());
                assertEquals(before.structureDigest(), cursor.result().structureDigest());
                assertEquals(before.structureDigestBytes(), cursor.result().structureDigestBytes());
                assertTrue(RocketSnapshotWitness.verifies(RocketSnapshotNbtCodec.encode(cursor.result()), before));
            }
        }
    }

    @Test void largeMutationResumesWithoutTickCreditAndMatchesFrozenReplay() {
        var before = large(true);
        var delta = CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1));
        var expected = RocketCargoMutation.apply(before, delta, true);
        var budget = new RocketCargoWorkBudget();
        var cursor = new RocketCargoPreparation(new UUID(0, 112), before, delta, true);
        int ticks = 0;
        while (cursor.state() != RocketCargoPreparation.State.READY && ticks < 20) {
            cursor.advance(ticks++, budget);
            assertTrue(budget.chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES);
            assertTrue(budget.chargedVisits() <= RocketCargoWorkBudget.MAX_VISITS);
            assertEquals(1, budget.reconstructions());
            assertTrue(cursor.retainedNativeBytes() <= 1_048_576 + 262_144);
            assertSame(before, cursor.before());
        }
        assertTrue(ticks > 1 && ticks < 20);
        assertEquals(expected.contentHash(), cursor.after().contentHash());
        assertEquals(expected.structureDigest(), cursor.after().structureDigest());
        assertEquals("d0fd300ea50640f1e4db2788b8900023798b4035de546b5889959d2583b987f4", cursor.after().contentHash());
        assertEquals("1cad1249a25375a3459baeef5856b617c43e40d38d96e1d6b52ae4217d92a617", cursor.after().structureDigest());
        assertEquals(RocketSnapshotNbtCodec.encode(expected), RocketSnapshotNbtCodec.encode(cursor.after()));
        System.out.println("W112 resumed cargo: ticks=" + ticks + " bytes=" + cursor.chargedBytes() + " visits=" + cursor.chargedVisits()
                + " content=" + cursor.after().contentHash() + " structure=" + cursor.after().structureDigest());
        cursor.close(); assertEquals(RocketCargoPreparation.State.DISCARDED, cursor.state());
        assertEquals(0, cursor.retainedNativeBytes());
        assertThrows(IllegalStateException.class, cursor::after);
        assertThrows(IllegalStateException.class, cursor::before);
    }

    @Test void oneRuntimeBudgetCannotGrantAnotherReconstructionDuringAContinuation() {
        var before = large(true);
        var delta = CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1));
        var large = new RocketCargoPreparation(new UUID(0, 1), before, delta, true);
        var tinySnapshot = CargoTestFixtures.snapshot(FluidStack.EMPTY);
        var small = new RocketCargoPreparation(new UUID(0, 2), tinySnapshot,
                CargoTestFixtures.delta(tinySnapshot, 0, new FluidStack(Fluids.WATER, 1)), true);
        var budget = new RocketCargoWorkBudget();
        for (int tick = 0; tick < 10 && large.state() != RocketCargoPreparation.State.READY; tick++) {
            large.advance(tick, budget);
            long bytes = budget.chargedBytes(); int visits = budget.chargedVisits();
            assertFalse(small.advance(tick, budget));
            assertEquals(bytes, budget.chargedBytes()); assertEquals(visits, budget.chargedVisits());
        }
        assertEquals(RocketCargoPreparation.State.READY, large.state());
        assertEquals(0, small.chargedBytes()); large.close(); small.close();
        assertEquals(RocketCargoPreparation.State.PREPARING,
                new RocketCargoPreparation(new UUID(0, 1), before, delta, true).state(), "Restart retains no cursor progress");
    }

    @Test void codecWitnessRejectsEveryChangedCanonicalInput() {
        var before = CargoTestFixtures.snapshot(FluidStack.EMPTY);
        var root = RocketSnapshotNbtCodec.encode(before);
        assertTrue(RocketSnapshotWitness.verifies(root, before));
        for (String key : List.of("content_hash", "snapshot_id", "created_at_game_time", "schema_version")) {
            var changed = root.copy(); changed.remove(key);
            assertFalse(RocketSnapshotWitness.verifies(changed, before), key);
        }
        var after = RocketCargoMutation.apply(before, CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1)), true);
        assertFalse(RocketSnapshotWitness.verifies(RocketSnapshotNbtCodec.encode(after), before));
        var unknown = root.copy(); unknown.getCompound("mass_inputs").putInt("extra", 1);
        assertFalse(RocketSnapshotWitness.verifies(unknown, before));
        unknown = root.copy(); unknown.getList("relative_blocks", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("extra", 1);
        assertFalse(RocketSnapshotWitness.verifies(unknown, before));
        unknown = root.copy(); unknown.getList("block_palette", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("extra", 1);
        assertFalse(RocketSnapshotWitness.verifies(unknown, before));
        unknown = root.copy(); var unused = new net.minecraft.nbt.CompoundTag(); unused.putString("id", "zzzz:unused");
        unused.put("properties", new net.minecraft.nbt.CompoundTag());
        unknown.getList("block_palette", net.minecraft.nbt.Tag.TAG_COMPOUND).add(unused);
        assertFalse(RocketSnapshotWitness.verifies(unknown, before));
    }

    @Test void nearLimitArrayPayloadDoesNotEscapeThePerRecordReservation() {
        var data = new net.minecraft.nbt.CompoundTag();
        var bytes = new byte[262_100]; Arrays.fill(bytes, (byte) -128); data.putByteArray("blob", bytes);
        var block = new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:barrel"), Map.of()),
                new RocketBlockEntityPayload(ResourceLocation.tryParse("fixture:payload"), data));
        var before = CargoTestFixtures.snapshot(List.of(block));
        var cursor = new RocketSnapshotPreparation(before, -1, null);
        while (!cursor.done()) {
            long bound = cursor.nextByteBound(); var work = cursor.step(); assertTrue(work.bytes() <= bound);
        }
        assertEquals(before.contentHash(), cursor.result().contentHash());
        assertEquals(before.structureDigest(), cursor.result().structureDigest());
    }
}
