package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One bounded survey area scan (ADR-049 section 8): a fixed grid of cells around a server-derived centre,
 * every block of every column from the minimum to the maximum build height, read in resumable steps of at
 * most a budget of block states. A cell with any column outside a loaded chunk is {@code UNKNOWN}.
 */
public final class SurveyScanJob {
    public static final int MAX_SIDE = 2 * SatelliteLimits.MAX_SCAN_RADIUS / 4;
    public static final int MAX_CELLS = MAX_SIDE * MAX_SIDE;
    public static final int MAX_RATIO = 65_535;

    private final int centreX;
    private final int centreZ;
    private final int radius;
    private final int cell;
    private final int side;
    private final int minY;
    private final int maxY;
    private final long[] ores;
    private final long[] solids;
    private final boolean[] unknown;
    private final List<Map<ResourceLocation, Integer>> biomes;
    private int cellIndex;
    private int column;
    private int y;
    private long reads;

    public SurveyScanJob(int centreX, int centreZ, int radius, int cell, int minY, int maxY) {
        if (radius < SatelliteLimits.MIN_SCAN_RADIUS || radius > SatelliteLimits.MAX_SCAN_RADIUS
                || (cell != 4 && cell != 8 && cell != 16) || radius % cell != 0 || minY >= maxY) {
            throw new IllegalArgumentException("Survey scan geometry is invalid");
        }
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.radius = radius;
        this.cell = cell;
        this.side = 2 * radius / cell;
        this.minY = minY;
        this.maxY = maxY;
        int cells = side * side;
        ores = new long[cells];
        solids = new long[cells];
        unknown = new boolean[cells];
        biomes = new ArrayList<>(cells);
        for (int index = 0; index < cells; index++) {
            biomes.add(new HashMap<>());
        }
        y = minY;
    }

    /** Reads at most {@code budget} block states; returns true when every cell is finished. */
    public boolean step(ScanColumnSource source, int budget) {
        Objects.requireNonNull(source, "source");
        if (budget < 1) {
            throw new IllegalArgumentException("A scan step needs a positive budget");
        }
        int remaining = budget;
        int cells = side * side;
        while (remaining > 0 && cellIndex < cells) {
            int x = centreX - radius + (cellIndex % side) * cell + column % cell;
            int z = centreZ - radius + (cellIndex / side) * cell + column / cell;
            int kind = source.classify(x, y, z);
            remaining--;
            reads++;
            if (kind == ScanColumnSource.UNLOADED) {
                unknown[cellIndex] = true;
                nextCell();
                continue;
            }
            if (kind == ScanColumnSource.ORE) {
                ores[cellIndex]++;
                solids[cellIndex]++;
            } else if (kind == ScanColumnSource.SOLID) {
                solids[cellIndex]++;
            } else if (kind != ScanColumnSource.AIR) {
                throw new IllegalStateException("Unknown block classification " + kind);
            }
            if (((y - minY) & 3) == 0) {
                int index = cellIndex;
                source.biome(x, y, z).ifPresent(biome -> biomes.get(index).merge(biome, 1, Integer::sum));
            }
            if (++y == maxY) {
                y = minY;
                if (++column == cell * cell) {
                    nextCell();
                }
            }
        }
        return cellIndex == cells;
    }

    private void nextCell() {
        cellIndex++;
        column = 0;
        y = minY;
    }

    public boolean finished() {
        return cellIndex == side * side;
    }

    /** Block states read so far, including the one read that found an unloaded chunk. */
    public long reads() {
        return reads;
    }

    public int centreX() {
        return centreX;
    }

    public int centreZ() {
        return centreZ;
    }

    /**
     * The finished grid: per cell the ore share of non-air blocks scaled to 0..65,535 and the dominant biome
     * (ties to the smaller ID). Up to 16 dominant biomes get palette indices in cell order; later ones are
     * {@code OTHER}.
     */
    public SurveyScanResult result() {
        if (!finished()) {
            throw new IllegalStateException("The survey scan is not finished");
        }
        List<ResourceLocation> palette = new ArrayList<>();
        List<SurveyScanResult.Cell> cells = new ArrayList<>(side * side);
        for (int index = 0; index < side * side; index++) {
            if (unknown[index]) {
                cells.add(SurveyScanResult.Cell.UNKNOWN_CELL);
                continue;
            }
            int ratio = solids[index] == 0 ? 0 : (int) (ores[index] * MAX_RATIO / solids[index]);
            ResourceLocation dominant = null;
            int best = 0;
            for (Map.Entry<ResourceLocation, Integer> entry : biomes.get(index).entrySet()) {
                if (entry.getValue() > best || entry.getValue() == best && dominant != null
                        && entry.getKey().toString().compareTo(dominant.toString()) < 0) {
                    dominant = entry.getKey();
                    best = entry.getValue();
                }
            }
            int biome = SurveyScanResult.Cell.OTHER;
            // C7-M3: an ID the result cannot carry (over 128 characters) is shown as OTHER.
            if (dominant != null && dominant.toString().length() <= SurveyScanResult.MAX_ID_CHARS) {
                int known = palette.indexOf(dominant);
                if (known >= 0) {
                    biome = known;
                } else if (palette.size() < SurveyScanResult.MAX_PALETTE) {
                    palette.add(dominant);
                    biome = palette.size() - 1;
                }
            }
            cells.add(new SurveyScanResult.Cell(ratio, biome));
        }
        return new SurveyScanResult(centreX, centreZ, radius, cell, palette, cells);
    }
}
