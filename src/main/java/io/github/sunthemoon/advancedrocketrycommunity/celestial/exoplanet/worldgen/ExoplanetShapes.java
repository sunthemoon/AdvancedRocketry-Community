package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * The shapes of the C15c features (ADR-063 section 6, revision 6) as plain arithmetic over a seeded random, so their
 * bounds can be tested without a world. Every shape stays within {@link #MAX_REACH} blocks of its origin
 * horizontally; numbers are the legacy ones except where the bound shortens them.
 */
public final class ExoplanetShapes {
    /** The horizontal bound of every C15c feature: inside the 3 × 3 chunks a placed feature may write. */
    public static final int MAX_REACH = 12;

    private static final int[][] DIRECTIONS = {{1, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ExoplanetShapes() {
    }

    /**
     * The lightwood tree: a 2 × 2 trunk 20–29 high (legacy), eight branches low on the trunk (4–7 long, legacy 6–10)
     * and eight near the top (2–5 long, legacy 3–7), each ending in a leaf blob of radius 3, and a crown of radius 3.5
     * on the trunk. The shortened branches keep every cell within 11 blocks of the origin.
     */
    public static TreePlan lightwoodTree(Random random) {
        int height = 20 + random.nextInt(10);
        Set<BlockPos> logs = new HashSet<>();
        List<BlockPos> leaves = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    logs.add(new BlockPos(x, y, z));
                }
            }
        }
        for (int[] direction : DIRECTIONS) {
            branch(random, logs, leaves, direction, height / 6 + random.nextInt(10), 4 + random.nextInt(4));
            branch(random, logs, leaves, direction, height - height / 3 + random.nextInt(5), 2 + random.nextInt(4));
        }
        sphere(leaves, 0.5D, height - 0.5D, 0.5D, 3.5D);
        return TreePlan.of(logs, leaves);
    }

    private static void branch(Random random, Set<BlockPos> logs, List<BlockPos> leaves, int[] direction, int y,
                               int length) {
        // Start just outside the trunk on the branch's side; a branch steps up two blocks halfway out (legacy).
        int x = direction[0] > 0 ? 2 : direction[0] < 0 ? -1 : random.nextInt(2);
        int z = direction[1] > 0 ? 2 : direction[1] < 0 ? -1 : random.nextInt(2);
        BlockPos end = null;
        for (int step = 0; step < length; step++) {
            int level = step >= length / 2 ? y + 2 : y;
            BlockPos cell = new BlockPos(x + direction[0] * step, level, z + direction[1] * step);
            if (step == length / 2) {
                logs.add(cell.below());
                logs.add(cell.below(2));
            }
            logs.add(cell);
            end = cell;
        }
        sphere(leaves, end.getX(), end.getY(), end.getZ(), 3.0D);
    }

    private static void sphere(List<BlockPos> cells, double cx, double cy, double cz, double radius) {
        int span = (int) Math.ceil(radius);
        for (int dx = -span; dx <= span; dx++) {
            for (int dy = -span; dy <= span; dy++) {
                for (int dz = -span; dz <= span; dz++) {
                    double x = Math.floor(cx) + dx;
                    double y = Math.floor(cy) + dy;
                    double z = Math.floor(cz) + dz;
                    double ox = x + 0.5D - (cx + 0.5D);
                    double oy = y + 0.5D - (cy + 0.5D);
                    double oz = z + 0.5D - (cz + 0.5D);
                    if (ox * ox + oy * oy + oz * oz <= radius * radius + 0.5D) {
                        cells.add(new BlockPos((int) x, (int) y, (int) z));
                    }
                }
            }
        }
    }

    /**
     * The giant swamp tree: 40–49 high (legacy) on a trunk that tapers from radius 4.5 at the root tips, 20 blocks
     * below the origin, to 1.5 at the top; eight branches of 8 at the top hold a flat canopy of radius 11 and height 6,
     * of which only leaves within six steps of a log are kept.
     */
    public static TreePlan giantSwampTree(Random random) {
        int height = 40 + random.nextInt(10);
        int roots = 20;
        Set<BlockPos> logs = new HashSet<>();
        List<BlockPos> leaves = new ArrayList<>();
        for (int y = -roots; y < height; y++) {
            double radius = 1.5D + 3.0D * (height - y) / (double) (height + roots);
            int span = (int) Math.floor(radius);
            for (int x = -span; x <= span; x++) {
                for (int z = -span; z <= span; z++) {
                    if (x * x + z * z <= radius * radius) {
                        logs.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (int[] direction : DIRECTIONS) {
            for (int step = 1; step <= 8; step++) {
                int length = direction[0] != 0 && direction[1] != 0 ? (step * 5 + 3) / 7 : step;
                logs.add(new BlockPos(direction[0] * length, height - 1, direction[1] * length));
            }
        }
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                for (int y = height - 2; y <= height + 3; y++) {
                    double ex = x / 11.0D;
                    double ez = z / 11.0D;
                    double ey = (y - (height + 0.5D)) / 3.0D;
                    if (ex * ex + ez * ez + ey * ey <= 1.0D) {
                        leaves.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return TreePlan.of(logs, leaves);
    }

    /** The material of a cell of an inverted pillar, by its share of the pillar's height (legacy thirds). */
    public enum PillarPart { MOSSY_COBBLESTONE, COBBLESTONE, DIRT, GRASS }

    /**
     * An inverted pillar standing on the sea floor: 20–33 high, its radius growing from 1 at the foot to 5 at the top;
     * the lower third mossy cobblestone, then cobblestone, then dirt with grass on its top layer (legacy).
     */
    public static Map<BlockPos, PillarPart> invertedPillar(Random random) {
        int height = 20 + random.nextInt(14);
        Map<BlockPos, PillarPart> cells = new HashMap<>();
        for (int y = 0; y < height; y++) {
            double share = y / (double) (height - 1);
            double radius = 1.0D + 4.0D * share * share;
            int span = (int) Math.floor(radius);
            PillarPart part = y == height - 1 ? PillarPart.GRASS : share > 0.66D ? PillarPart.DIRT
                    : share < 0.33D ? PillarPart.MOSSY_COBBLESTONE : PillarPart.COBBLESTONE;
            for (int x = -span; x <= span; x++) {
                for (int z = -span; z <= span; z++) {
                    if (x * x + z * z <= radius * radius) {
                        cells.put(new BlockPos(x, y, z), part);
                    }
                }
            }
        }
        return cells;
    }

    /** A planned crystal cluster: its cells and one of the six crystal colours (an index into the legacy order). */
    public record Crystal(Set<BlockPos> cells, int colour) {
        public Crystal {
            cells = Set.copyOf(cells);
        }
    }

    /**
     * A crystal cluster: 10–49 high (legacy), rooted three blocks into the ground, a diamond cross-section of
     * half-width 2–5 at the foot tapering to a point, leaning by up to 6 blocks at the top in each axis (legacy:
     * leaning right or left one time in six each, otherwise upright).
     */
    public static Crystal crystalCluster(Random random) {
        int height = 10 + random.nextInt(40);
        int edge = 2 + random.nextInt(4);
        int leanX = lean(random);
        int leanZ = lean(random);
        int colour = random.nextInt(6);
        Set<BlockPos> cells = new HashSet<>();
        for (int y = -3; y < height; y++) {
            double share = Math.max(0, y) / (double) height;
            int halfWidth = (int) Math.round(edge * (1.0D - share));
            int offsetX = (int) Math.round(leanX * 6.0D * share);
            int offsetZ = (int) Math.round(leanZ * 6.0D * share);
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = -halfWidth; z <= halfWidth; z++) {
                    if (Math.abs(x) + Math.abs(z) <= halfWidth) {
                        cells.add(new BlockPos(x + offsetX, y, z + offsetZ));
                    }
                }
            }
        }
        return new Crystal(cells, colour);
    }

    private static int lean(Random random) {
        return 1 - (random.nextInt(6) + 3) / 4;
    }

    /** The largest horizontal offset of any position from the origin, along either axis. */
    public static int reach(Iterable<BlockPos> cells) {
        int reach = 0;
        for (BlockPos cell : cells) {
            reach = Math.max(reach, Math.max(Math.abs(cell.getX()), Math.abs(cell.getZ())));
        }
        return reach;
    }
}
