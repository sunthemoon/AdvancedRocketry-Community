package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealDetectorServiceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sixBlockReachIsInclusiveAndNextRepresentableValueRefuses() {
        assertTrue(SealDetectorService.inReach(Vec3.ZERO, new Vec3(6, 0, 0), new BlockPos(6, 0, 0)));
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, new Vec3(Math.nextUp(6D), 0, 0), new BlockPos(6, 0, 0)));
        assertTrue(SealDetectorService.inReach(new Vec3(-6, 0, 0), Vec3.ZERO, BlockPos.ZERO));
        assertFalse(SealDetectorService.inReach(new Vec3(Math.nextDown(-6D), 0, 0), Vec3.ZERO, BlockPos.ZERO));
    }

    @Test void nearbyHitCannotAuthorizeFarOrDiagonalTarget() {
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, Vec3.ZERO, new BlockPos(1000, 0, 0)));
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, Vec3.ZERO, new BlockPos(4, 4, 4)));
        assertEquals(0D, SealDetectorService.distanceToCellSquared(new Vec3(1, 1, 1), BlockPos.ZERO));
        assertEquals(36D, SealDetectorService.distanceToCellSquared(new Vec3(7, 0.5, 0.5), BlockPos.ZERO));
    }

    @Test void nonfiniteHitEyeAndHugeFiniteDistancesCannotPass() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(SealDetectorService.inReach(new Vec3(bad, 0, 0), Vec3.ZERO, BlockPos.ZERO));
            assertFalse(SealDetectorService.inReach(Vec3.ZERO, new Vec3(0, bad, 0), BlockPos.ZERO));
            assertFalse(SealDetectorService.validEye(new Vec3(0, 0, bad), -64, 320));
        }
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, new Vec3(Double.MAX_VALUE, 0, 0), BlockPos.ZERO));
        assertFalse(SealDetectorService.inReach(null, Vec3.ZERO, BlockPos.ZERO));
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, null, BlockPos.ZERO));
        assertFalse(SealDetectorService.inReach(Vec3.ZERO, Vec3.ZERO, null));
    }

    @Test void eyeUsesExclusiveHorizontalUpperAndBuildUpperBounds() {
        assertTrue(SealDetectorService.validEye(new Vec3(-30_000_000, -64, -30_000_000), -64, 320));
        assertTrue(SealDetectorService.validEye(new Vec3(Math.nextDown(30_000_000D), Math.nextDown(320D), 0), -64, 320));
        assertFalse(SealDetectorService.validEye(new Vec3(30_000_000, 0, 0), -64, 320));
        assertFalse(SealDetectorService.validEye(new Vec3(0, 0, 30_000_000), -64, 320));
        assertFalse(SealDetectorService.validEye(new Vec3(0, 320, 0), -64, 320));
        assertFalse(SealDetectorService.validEye(new Vec3(0, Math.nextDown(-64D), 0), -64, 320));
        assertFalse(SealDetectorService.validEye(new Vec3(Math.nextDown(-30_000_000D), 0, 0), -64, 320));
    }

    @Test void selectedFaceHasExactlyOneAdjacentCellAndChunkFlooringIsCorrect() {
        BlockPos target = new BlockPos(15, 100, 15);
        for (Direction face : Direction.values()) {
            BlockPos adjacent = target.relative(face);
            assertEquals(1, target.distManhattan(adjacent));
            assertEquals(target, adjacent.relative(face.getOpposite()));
        }
        assertFalse(SealDetectorService.sameChunk(target, target.east()));
        assertFalse(SealDetectorService.sameChunk(target, target.south()));
        assertTrue(SealDetectorService.sameChunk(target, target.above()));
        assertFalse(SealDetectorService.sameChunk(new BlockPos(-1, 0, 0), BlockPos.ZERO));
        assertTrue(SealDetectorService.sameChunk(new BlockPos(-16, 0, -16), new BlockPos(-1, 100, -1)));
    }

    @Test void stateProjectionNeverConfusesAmbientPendingOrUnloadedWithSupply() {
        assertEquals(SealDetectorReading.Boundary.SEALED, SealDetectorService.boundary(CellObservation.SEALED));
        assertEquals(SealDetectorReading.Boundary.OPEN, SealDetectorService.boundary(CellObservation.OPEN));
        assertEquals(SealDetectorReading.Boundary.OPEN, SealDetectorService.boundary(CellObservation.TRAVERSABLE));
        assertEquals(SealDetectorReading.Boundary.UNAVAILABLE, SealDetectorService.boundary(CellObservation.UNLOADED));
        assertEquals(SealDetectorReading.Supply.SUPPLIED, SealDetectorService.supply(BreathabilityState.BREATHABLE, true));
        assertEquals(SealDetectorReading.Supply.NOT_KNOWN_SUPPLIED, SealDetectorService.supply(BreathabilityState.BREATHABLE, false));
        assertEquals(SealDetectorReading.Supply.NOT_KNOWN_SUPPLIED, SealDetectorService.supply(BreathabilityState.VACUUM, true));
        assertEquals(SealDetectorReading.Supply.PENDING, SealDetectorService.supply(BreathabilityState.PENDING, true));
    }

    @Test void missingRuntimeAndNullInstallRefuseWithoutAWorld() {
        assertEquals(SealDetectorReading.unavailable(), SealDetectorRuntime.read(null));
        assertThrows(NullPointerException.class, () -> SealDetectorRuntime.install(null));
        SealDetectorRuntime.remove(null);
        assertEquals(SealDetectorReading.unavailable(), SealDetectorRuntime.read(null));
        assertThrows(NullPointerException.class, () -> new SealDetectorLifecycle(null, () -> null));
        assertThrows(NullPointerException.class, () -> new SealDetectorLifecycle(() -> null, null));
    }
}
