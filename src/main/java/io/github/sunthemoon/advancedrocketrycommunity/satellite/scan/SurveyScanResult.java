package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The finished grid of one survey scan (ADR-049 section 8), independent of how it is sent: per cell the ore share
 * of non-air blocks (0..65,535) and a palette index, {@link Cell#OTHER} or {@link Cell#UNKNOWN}.
 */
public record SurveyScanResult(int centreX, int centreZ, int radius, int cell, List<ResourceLocation> palette,
                               List<Cell> cells) {
    public static final int MAX_PALETTE = 16;
    public static final int MAX_ID_CHARS = 128;

    public SurveyScanResult {
        palette = List.copyOf(palette);
        cells = List.copyOf(cells);
        if (radius < SatelliteLimits.MIN_SCAN_RADIUS || radius > SatelliteLimits.MAX_SCAN_RADIUS
                || (cell != 4 && cell != 8 && cell != 16) || radius % cell != 0) {
            throw new IllegalArgumentException("Survey scan geometry is invalid");
        }
        int side = 2 * radius / cell;
        if (cells.size() != side * side || palette.size() > MAX_PALETTE
                || new HashSet<>(palette).size() != palette.size()
                || palette.stream().anyMatch(id -> id.toString().length() > MAX_ID_CHARS)) {
            throw new IllegalArgumentException("Survey scan result is outside its bounds");
        }
        for (Cell value : cells) {
            if (value.biome() >= palette.size() && value.biome() != Cell.OTHER && value.biome() != Cell.UNKNOWN) {
                throw new IllegalArgumentException("Survey scan cell names a missing palette entry");
            }
        }
    }

    public int side() {
        return 2 * radius / cell;
    }

    /** One grid cell: the ore share and a palette index, {@link #OTHER} or {@link #UNKNOWN}. */
    public record Cell(int ratio, int biome) {
        public static final int OTHER = 0xFE;
        public static final int UNKNOWN = 0xFF;
        public static final Cell UNKNOWN_CELL = new Cell(0, UNKNOWN);

        public Cell {
            if (ratio < 0 || ratio > 65_535 || biome < 0 || biome > UNKNOWN
                    || biome >= MAX_PALETTE && biome != OTHER && biome != UNKNOWN
                    || biome == UNKNOWN && ratio != 0) {
                throw new IllegalArgumentException("Survey scan cell is outside its bounds");
            }
        }

        public boolean unknown() {
            return biome == UNKNOWN;
        }
    }
}
