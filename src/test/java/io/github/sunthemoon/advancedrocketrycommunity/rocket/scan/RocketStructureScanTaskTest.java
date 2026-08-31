package io.github.sunthemoon.advancedrocketrycommunity.rocket.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class RocketStructureScanTaskTest {
    private static final RocketPosition ORIGIN = new RocketPosition(10, 64, 10);
    private static final RocketBlockMetrics STRUCTURAL = RocketBlockMetrics.structural(10);
    private static final RocketBlockMetrics ENGINE = new RocketBlockMetrics(100, 1_000, 0, true, false, false);
    private static final RocketBlockMetrics SEAT = new RocketBlockMetrics(20, 0, 0, false, true, false);
    private static final RocketBlockMetrics GUIDANCE = new RocketBlockMetrics(30, 0, 0, false, false, true);

    @Test
    void connectedLegalRocketProducesCanonicalServerSnapshot() {
        FakeWorld world = new FakeWorld();
        world.movable(ORIGIN, "test:engine", ENGINE);
        world.movable(add(0, 1, 0), "test:hull", STRUCTURAL);
        world.movable(add(0, 2, 0), "test:seat", SEAT);
        world.movable(add(0, 3, 0), "test:guidance", GUIDANCE);
        world.movable(add(1, 1, 0), "test:tank", new RocketBlockMetrics(50, 0, 500, false, false, false));
        world.movable(add(20, 0, 0), "test:disconnected", STRUCTURAL);

        RocketScanResult result = finish(world, 7);

        assertEquals(RocketScanResult.Status.SUCCESS, result.status());
        var snapshot = result.snapshot().orElseThrow();
        assertEquals(5, snapshot.blocks().size());
        assertEquals(5, snapshot.stats().blockCount());
        assertEquals(210, snapshot.stats().mass());
        assertEquals(1_000, snapshot.stats().thrust());
        assertEquals(500, snapshot.stats().fuelCapacity());
        assertEquals(List.of(new RocketPosition(0, 2, 0)), snapshot.passengerAnchors());
        assertEquals(ORIGIN, snapshot.sourceOrigin());
        assertFalse(snapshot.blocks().stream().anyMatch(block -> block.state().blockId().getPath().equals("disconnected")));
        assertTrue(world.calls.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void eachStepHonorsItsExactPositiveInspectionBudget() {
        FakeWorld world = lineWorld(20, state("test:combined"), combinedMetrics());
        RocketStructureScanTask task = task(world);
        int previousTotal = 0;
        RocketScanResult result;
        do {
            result = task.step(3);
            assertTrue(result.inspectionsThisStep() <= 3);
            assertEquals(previousTotal + result.inspectionsThisStep(), result.totalInspections());
            previousTotal = result.totalInspections();
        } while (result.status() == RocketScanResult.Status.RUNNING);

        assertEquals(RocketScanResult.Status.SUCCESS, result.status());
        assertEquals(20, result.capturedBlocks());
        assertEquals(world.calls.size(), result.totalInspections());
        assertThrows(IllegalArgumentException.class, () -> task(world).step(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> task(world).step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK + 1)
        );
    }

    @Test
    void emptyAndNonMovableSeedsHaveDifferentReadableFailures() {
        RocketScanResult empty = finish(new FakeWorld().empty(ORIGIN), 10);
        assertFailureAt(empty, RocketValidationCode.EMPTY_STRUCTURE, ORIGIN);

        RocketScanResult boundary = finish(new FakeWorld().boundary(ORIGIN, "minecraft:stone"), 10);
        assertFailureAt(boundary, RocketValidationCode.BLOCK_NOT_MOVABLE, ORIGIN);
        assertEquals("minecraft:stone", boundary.issues().get(0).parameters().get("detail"));
    }

    @Test
    void unloadedForbiddenAndUnsupportedNeighborsFailAtExactPosition() {
        RocketPosition neighbor = add(1, 0, 0);

        FakeWorld unloadedWorld = minimalSeed();
        unloadedWorld.unloaded(neighbor);
        assertFailureAt(finish(unloadedWorld, 256), RocketValidationCode.UNLOADED_CHUNK, neighbor);

        FakeWorld forbiddenWorld = minimalSeed();
        forbiddenWorld.forbidden(neighbor, "minecraft:command_block");
        RocketScanResult forbidden = finish(forbiddenWorld, 256);
        assertFailureAt(forbidden, RocketValidationCode.FORBIDDEN_BLOCK, neighbor);
        assertEquals("minecraft:command_block", forbidden.issues().get(0).parameters().get("detail"));

        FakeWorld unsupportedWorld = minimalSeed();
        unsupportedWorld.unsupported(neighbor, "examplemod:unsafe_machine");
        RocketScanResult unsupported = finish(unsupportedWorld, 256);
        assertFailureAt(unsupported, RocketValidationCode.UNSUPPORTED_BLOCK_ENTITY, neighbor);
        assertEquals("examplemod:unsafe_machine", unsupported.issues().get(0).parameters().get("detail"));
    }

    @Test
    void missingComponentsReturnEveryIndependentDiagnostic() {
        FakeWorld world = new FakeWorld();
        world.movable(ORIGIN, "test:hull", STRUCTURAL);
        RocketScanResult result = finish(world, 256);

        assertEquals(RocketScanResult.Status.FAILED, result.status());
        assertEquals(
                List.of(
                        RocketValidationCode.MISSING_ENGINE,
                        RocketValidationCode.MISSING_SEAT,
                        RocketValidationCode.MISSING_GUIDANCE,
                        RocketValidationCode.INSUFFICIENT_THRUST
                ),
                result.issues().stream().map(issue -> issue.code()).toList()
        );
        assertEquals(1, result.stats().orElseThrow().blockCount());
    }

    @Test
    void blockAndPaletteLimitsStopOnTheFirstExcessBlock() {
        FakeWorld tooManyBlocks = lineWorld(
                RocketLimits.MAX_BLOCKS + 1,
                state("test:combined"),
                combinedMetrics()
        );
        RocketScanResult blockFailure = finish(tooManyBlocks, 256);
        assertEquals(RocketValidationCode.TOO_MANY_BLOCKS, blockFailure.issues().get(0).code());
        assertEquals(RocketLimits.MAX_BLOCKS, blockFailure.capturedBlocks());

        FakeWorld tooManyPalette = new FakeWorld();
        for (int index = 0; index <= RocketLimits.MAX_PALETTE_ENTRIES; index++) {
            RocketBlockMetrics metrics = index == 0 ? combinedMetrics() : STRUCTURAL;
            tooManyPalette.movable(
                    add(index, 0, 0),
                    "test:palette_" + index,
                    metrics
            );
        }
        RocketScanResult paletteFailure = finish(tooManyPalette, 256);
        assertEquals(RocketValidationCode.TOO_MANY_PALETTE_ENTRIES, paletteFailure.issues().get(0).code());
        assertEquals(RocketLimits.MAX_PALETTE_ENTRIES, paletteFailure.capturedBlocks());
    }

    @Test
    void maximumStructureCompletesWithinFixedTickBudget() {
        FakeWorld world = lineWorld(
                RocketLimits.MAX_BLOCKS,
                state("test:combined"),
                combinedMetrics()
        );
        RocketStructureScanTask task = task(world);
        RocketScanResult result;
        int ticks = 0;
        long started = System.nanoTime();
        do {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
            ticks++;
        } while (result.status() == RocketScanResult.Status.RUNNING);
        long elapsedNanos = System.nanoTime() - started;

        assertEquals(RocketScanResult.Status.SUCCESS, result.status());
        assertEquals(RocketLimits.MAX_BLOCKS, result.capturedBlocks());
        assertTrue(result.totalInspections() <= RocketLimits.MAX_SCAN_INSPECTIONS);
        assertEquals(
                (result.totalInspections() + RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK - 1)
                        / RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK,
                ticks
        );
        System.out.printf(
                "ARCE_ROCKET_SCAN_PERF blocks=%d ticks=%d inspections=%d elapsed_nanos=%d%n",
                result.capturedBlocks(),
                ticks,
                result.totalInspections(),
                elapsedNanos
        );
    }

    @Test
    void sparseConnectedShapeCannotHideAnOversizedBoundingVolume() {
        FakeWorld world = new FakeWorld();
        for (int x = 0; x <= 31; x++) {
            world.movable(add(x, 0, 0), "test:combined", combinedMetrics());
        }
        for (int y = 1; y <= 31; y++) {
            world.movable(add(31, y, 0), "test:combined", combinedMetrics());
        }
        for (int z = 1; z <= 32; z++) {
            world.movable(add(31, 31, z), "test:combined", combinedMetrics());
        }

        RocketScanResult result = finish(world, 256);
        assertEquals(RocketValidationCode.BOUNDING_VOLUME_EXCEEDED, result.issues().get(0).code());
        assertEquals(add(31, 31, 32), result.issues().get(0).position().orElseThrow());
    }

    @Test
    void blockEntityCountAndTotalSnapshotBytesAreEnforcedDuringValidation() {
        CompoundTag small = new CompoundTag();
        small.putInt("slot", 1);
        RocketBlockEntityPayload smallPayload = payload(small);
        FakeWorld tooMany = new FakeWorld();
        for (int index = 0; index <= RocketLimits.MAX_BLOCK_ENTITIES; index++) {
            tooMany.movable(
                    add(index, 0, 0),
                    state("test:combined"),
                    combinedMetrics(),
                    smallPayload
            );
        }
        RocketScanResult countFailure = finish(tooMany, 256);
        assertEquals(RocketValidationCode.TOO_MANY_BLOCK_ENTITIES, countFailure.issues().get(0).code());
        assertEquals(RocketLimits.MAX_BLOCK_ENTITIES, countFailure.capturedBlocks());

        FakeWorld oversized = new FakeWorld();
        for (int index = 0; index < 5; index++) {
            CompoundTag data = new CompoundTag();
            data.putByteArray("bytes", new byte[220_000]);
            oversized.movable(
                    add(index, 0, 0),
                    state("test:payload_" + index),
                    index == 0 ? combinedMetrics() : STRUCTURAL,
                    payload(data)
            );
        }
        RocketScanResult byteFailure = finish(oversized, 256);
        assertEquals(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, byteFailure.issues().get(0).code());
    }

    @Test
    void inconsistentMetricsAndObservationExceptionsFailClosed() {
        FakeWorld inconsistent = new FakeWorld();
        inconsistent.movable(ORIGIN, "test:same", combinedMetrics());
        inconsistent.movable(add(1, 0, 0), "test:same", STRUCTURAL);
        assertFailureAt(
                finish(inconsistent, 256),
                RocketValidationCode.WORLD_CHANGED,
                add(1, 0, 0)
        );

        FakeWorld throwing = minimalSeed();
        RocketPosition bad = add(0, 1, 0);
        throwing.throwAt = bad;
        RocketScanResult exception = finish(throwing, 256);
        assertFailureAt(exception, RocketValidationCode.WORLD_CHANGED, bad);
        assertNotNull(exception.issues().get(0).parameters().get("detail"));
    }

    @Test
    void worldCoordinateOverflowFailsBeforeAWrappedNeighborCanBeObserved() {
        RocketPosition edge = new RocketPosition(Integer.MAX_VALUE, 64, 0);
        FakeWorld world = new FakeWorld();
        world.movable(edge, "test:combined", combinedMetrics());
        RocketStructureScanTask task = new RocketStructureScanTask(
                world,
                ResourceLocation.tryParse("minecraft:overworld"),
                edge,
                UUID.randomUUID(),
                0L
        );

        RocketScanResult result = task.step(256);
        assertFailureAt(result, RocketValidationCode.POSITION_OVERFLOW, edge);
        assertFalse(world.calls.containsKey(new RocketPosition(Integer.MIN_VALUE, 64, 0)));
    }

    @Test
    void terminalResultIsIdempotentAndDoesNotObserveAgain() {
        FakeWorld world = minimalSeed();
        RocketStructureScanTask task = task(world);
        RocketScanResult first = task.step(256);
        Map<RocketPosition, Integer> calls = Map.copyOf(world.calls);
        RocketScanResult second = task.step(1);

        assertTrue(task.terminal());
        assertEquals(first.status(), second.status());
        assertEquals(first.totalInspections(), second.totalInspections());
        assertEquals(calls, world.calls);
    }

    private static FakeWorld minimalSeed() {
        FakeWorld world = new FakeWorld();
        world.movable(ORIGIN, "test:combined", combinedMetrics());
        return world;
    }

    private static FakeWorld lineWorld(
            int length,
            RocketBlockState state,
            RocketBlockMetrics metrics
    ) {
        FakeWorld world = new FakeWorld();
        for (int index = 0; index < length; index++) {
            world.movable(add(index, 0, 0), state, metrics, null);
        }
        return world;
    }

    private static RocketScanResult finish(FakeWorld world, int budget) {
        RocketStructureScanTask task = task(world);
        RocketScanResult result;
        do {
            result = task.step(budget);
        } while (result.status() == RocketScanResult.Status.RUNNING);
        return result;
    }

    private static RocketStructureScanTask task(FakeWorld world) {
        return new RocketStructureScanTask(
                world,
                ResourceLocation.tryParse("minecraft:overworld"),
                ORIGIN,
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                20L
        );
    }

    private static RocketPosition add(int x, int y, int z) {
        return ORIGIN.add(new RocketPosition(x, y, z));
    }

    private static RocketBlockState state(String id) {
        return new RocketBlockState(ResourceLocation.tryParse(id), Map.of());
    }

    private static RocketBlockMetrics combinedMetrics() {
        return new RocketBlockMetrics(100, 1_000, 500, true, true, true);
    }

    private static RocketBlockEntityPayload payload(CompoundTag data) {
        return new RocketBlockEntityPayload(
                ResourceLocation.tryParse("advancedrocketrycommunity:test_container"),
                data
        );
    }

    private static void assertFailureAt(
            RocketScanResult result,
            RocketValidationCode code,
            RocketPosition position
    ) {
        assertEquals(RocketScanResult.Status.FAILED, result.status());
        assertEquals(code, result.issues().get(0).code());
        assertEquals(position, result.issues().get(0).position().orElseThrow());
        assertEquals("absolute", result.issues().get(0).parameters().get("coordinate_space"));
    }

    private static final class FakeWorld implements RocketScanWorld {
        private final Map<RocketPosition, RocketScanObservation> observations = new LinkedHashMap<>();
        private final Map<RocketPosition, Integer> calls = new HashMap<>();
        private RocketPosition throwAt;

        @Override
        public RocketScanObservation observe(RocketPosition absolutePosition) {
            calls.merge(absolutePosition, 1, Integer::sum);
            if (absolutePosition.equals(throwAt)) {
                throw new IllegalStateException("simulated concurrent change");
            }
            return observations.getOrDefault(
                    absolutePosition,
                    RocketScanObservation.boundary("test boundary")
            );
        }

        FakeWorld movable(
                RocketPosition position,
                String id,
                RocketBlockMetrics metrics
        ) {
            return movable(position, state(id), metrics, null);
        }

        FakeWorld movable(
                RocketPosition position,
                RocketBlockState state,
                RocketBlockMetrics metrics,
                RocketBlockEntityPayload payload
        ) {
            observations.put(position, RocketScanObservation.movable(state, metrics, payload));
            return this;
        }

        FakeWorld empty(RocketPosition position) {
            observations.put(position, RocketScanObservation.empty());
            return this;
        }

        FakeWorld boundary(RocketPosition position, String detail) {
            observations.put(position, RocketScanObservation.boundary(detail));
            return this;
        }

        FakeWorld unloaded(RocketPosition position) {
            observations.put(position, RocketScanObservation.unloaded());
            return this;
        }

        FakeWorld forbidden(RocketPosition position, String detail) {
            observations.put(position, RocketScanObservation.forbidden(detail));
            return this;
        }

        FakeWorld unsupported(RocketPosition position, String detail) {
            observations.put(position, RocketScanObservation.unsupportedBlockEntity(detail));
            return this;
        }
    }
}
