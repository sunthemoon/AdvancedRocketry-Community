package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultPacket;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-049 section 8: grid geometry, read budget, UNKNOWN cells, ore share and the biome palette. */
final class SurveyScanJobTest {
    @Test
    void theWorstCaseScanTakes216BudgetedSteps() {
        SurveyScanJob job = new SurveyScanJob(0, 0, 48, 4, -64, 320);
        Source source = new Source((x, z) -> 1, (x, z) -> 0);
        int steps = 0;
        while (!job.step(source, ScanSettings.MAX_READS_PER_TICK)) {
            steps++;
            assertEquals((long) steps * ScanSettings.MAX_READS_PER_TICK, job.reads());
        }
        steps++;
        assertEquals(216, steps);
        assertEquals(96L * 96L * 384L, job.reads());
        SurveyScanResultPacket result = job.result();
        assertEquals(24, result.side());
        assertEquals(576, result.cells().size());
        assertTrue(result.cells().stream().allMatch(cell -> cell.ratio() == 0 && cell.biome() == 0));
    }

    @Test
    void resumingInSmallStepsGivesTheSameGrid() {
        IntBinaryOperator ores = (x, z) -> Math.floorMod(x * 31 + z * 17, 7) == 0 ? 1 : 0;
        SurveyScanJob whole = new SurveyScanJob(100, -40, 16, 4, 0, 16);
        SurveyScanJob pieces = new SurveyScanJob(100, -40, 16, 4, 0, 16);
        Source source = new Source((x, z) -> 1, ores);
        assertTrue(whole.step(source, Integer.MAX_VALUE));
        int steps = 0;
        while (!pieces.step(source, 37)) {
            steps++;
        }
        assertTrue(steps > 100);
        assertEquals(whole.result(), pieces.result());
        assertEquals(whole.reads(), pieces.reads());
    }

    @Test
    void theOreShareCountsOnlyNonAirBlocks() {
        // Column heights 0..16: the lowest 4 blocks are solid, one of them ore in every column of cell 0.
        SurveyScanJob job = new SurveyScanJob(16, 16, 16, 16, 0, 16);
        Source source = new Source((x, z) -> 1, (x, z) -> x < 16 && z < 16 ? 1 : 0) {
            @Override
            public int classify(int x, int y, int z) {
                if (y >= 4) {
                    return AIR;
                }
                return y == 0 && ores.applyAsInt(x, z) == 1 ? ORE : SOLID;
            }
        };
        assertTrue(job.step(source, Integer.MAX_VALUE));
        SurveyScanResultPacket result = job.result();
        assertEquals(65_535 / 4, result.cells().get(0).ratio());
        assertEquals(0, result.cells().get(1).ratio());
        assertEquals(4, result.cells().size());
    }

    @Test
    void anUnloadedColumnMakesItsWholeCellUnknownAndIsNotReadFurther() {
        SurveyScanJob job = new SurveyScanJob(0, 0, 16, 8, 0, 8);
        // Only the column at (-16, -16), the first of cell 0, is unloaded.
        Source source = new Source((x, z) -> x == -16 && z == -16 ? 0 : 1, (x, z) -> 1);
        assertTrue(job.step(source, Integer.MAX_VALUE));
        SurveyScanResultPacket result = job.result();
        assertTrue(result.cells().get(0).unknown());
        assertEquals(SurveyScanResultPacket.Cell.UNKNOWN_CELL, result.cells().get(0));
        assertFalse(result.cells().get(1).unknown());
        assertEquals(65_535, result.cells().get(1).ratio());
        // Cell 0 cost one read; the other 15 cells read 64 columns of 8 blocks.
        assertEquals(1L + 15L * 64L * 8L, job.reads());
    }

    @Test
    void dominantBiomesFillA16EntryPaletteThenBecomeOther() {
        // radius 48, cell 8: 144 cells, each with its own biome.
        SurveyScanJob job = new SurveyScanJob(0, 0, 48, 8, 0, 4);
        Source source = new Source((x, z) -> 1, (x, z) -> 0) {
            @Override
            public Optional<ResourceLocation> biome(int x, int y, int z) {
                int cell = Math.floorDiv(x + 48, 8) + 12 * Math.floorDiv(z + 48, 8);
                return Optional.of(ResourceLocation.tryParse("test:biome_" + cell));
            }
        };
        assertTrue(job.step(source, Integer.MAX_VALUE));
        SurveyScanResultPacket result = job.result();
        assertEquals(16, result.palette().size());
        for (int cell = 0; cell < 16; cell++) {
            assertEquals(cell, result.cells().get(cell).biome());
        }
        assertEquals(SurveyScanResultPacket.Cell.OTHER, result.cells().get(16).biome());
    }

    @Test
    void biomeTiesGoToTheSmallerId() {
        SurveyScanJob job = new SurveyScanJob(0, 0, 16, 16, 0, 8);
        Source source = new Source((x, z) -> 1, (x, z) -> 0) {
            @Override
            public Optional<ResourceLocation> biome(int x, int y, int z) {
                return Optional.of(ResourceLocation.tryParse(y == 0 ? "test:zeta" : "test:alpha"));
            }
        };
        assertTrue(job.step(source, Integer.MAX_VALUE));
        assertEquals(ResourceLocation.tryParse("test:alpha"), job.result().palette().get(0));
    }

    @Test
    void aBiomeIdTheResultCannotCarryIsShownAsOther() {
        // C7-M3: a 129-character biome ID must not make the finished scan throw.
        ResourceLocation longBiome = ResourceLocation.tryParse("test:" + "b".repeat(124));
        assertEquals(129, longBiome.toString().length());
        SurveyScanJob job = new SurveyScanJob(0, 0, 16, 16, 0, 4);
        Source source = new Source((x, z) -> 1, (x, z) -> 0) {
            @Override
            public Optional<ResourceLocation> biome(int x, int y, int z) {
                return Optional.of(x < 0 ? longBiome : ResourceLocation.tryParse("minecraft:plains"));
            }
        };
        assertTrue(job.step(source, Integer.MAX_VALUE));
        SurveyScanResultPacket result = job.result();
        assertEquals(SurveyScanResultPacket.Cell.OTHER, result.cells().get(0).biome());
        assertEquals(0, result.cells().get(1).biome());
        assertEquals(1, result.palette().size());
    }

    @Test
    void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanJob(0, 0, 15, 4, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanJob(0, 0, 52, 4, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanJob(0, 0, 20, 8, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanJob(0, 0, 16, 2, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new SurveyScanJob(0, 0, 16, 4, 16, 16));
        SurveyScanJob job = new SurveyScanJob(0, 0, 16, 4, 0, 16);
        assertThrows(IllegalStateException.class, job::result);
        assertThrows(IllegalArgumentException.class, () -> job.step(new Source((x, z) -> 1, (x, z) -> 0), 0));
    }

    /** A synthetic world: loaded columns, one solid block per position and ores where asked. */
    private static class Source implements ScanColumnSource {
        final IntBinaryOperator loaded;
        final IntBinaryOperator ores;

        Source(IntBinaryOperator loaded, IntBinaryOperator ores) {
            this.loaded = loaded;
            this.ores = ores;
        }

        @Override
        public int classify(int x, int y, int z) {
            if (loaded.applyAsInt(x, z) == 0) {
                return UNLOADED;
            }
            return ores.applyAsInt(x, z) == 1 ? ORE : SOLID;
        }

        @Override
        public Optional<ResourceLocation> biome(int x, int y, int z) {
            return Optional.of(ResourceLocation.tryParse("minecraft:plains"));
        }
    }
}
