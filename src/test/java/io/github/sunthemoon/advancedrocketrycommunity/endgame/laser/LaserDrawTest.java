package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.SplitMix64;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-055 section 2 {@code laser-v1} against the C10 reference vectors ({@code examples.json}, {@code laser_v1}). */
final class LaserDrawTest {
    /** The reference table: 900 of 1,000 weight is five cobblestone, the rest ore entries. */
    static final LaserDrillTable REFERENCE = new LaserDrillTable(ResourceLocation.tryBuild("test", "reference"), List.of(),
            true, List.of(
            entry("cobblestone", 5, 900),
            entry("raw_iron", 5, 30),
            entry("raw_copper", 5, 30),
            entry("raw_gold", 5, 15),
            entry("redstone", 5, 20),
            entry("diamond", 1, 5)), "0000000000000000");

    @Test
    void theDomainConstantIsTheAsciiOfItsName() {
        assertEquals(0x4C4153455244524CL, LaserDraw.LASERDRL);
        StringBuilder ascii = new StringBuilder();
        for (int shift = 56; shift >= 0; shift -= 8) {
            ascii.append((char) ((LaserDraw.LASERDRL >>> shift) & 0xFF));
        }
        assertEquals("LASERDRL", ascii.toString());
    }

    @Test
    void theClosedFormIsTheNthOutputOfTheGenerator() {
        for (long seed : new long[]{0L, 0x0123456789ABCDEFL, -1L, 0x9E3779B97F4A7C15L}) {
            SplitMix64 generator = new SplitMix64(seed ^ LaserDraw.LASERDRL);
            for (long n = 0; n < 256; n++) {
                assertEquals(generator.next(), LaserDraw.output(seed, n), "seed " + Long.toHexString(seed) + " n " + n);
            }
        }
    }

    @Test
    void theDrawsMatchTheReferenceVectors() {
        assertEquals(List.of("cobblestone5", "cobblestone5", "cobblestone5", "cobblestone5", "cobblestone5",
                        "cobblestone5", "raw_gold5", "cobblestone5", "diamond1", "cobblestone5", "cobblestone5",
                        "cobblestone5"),
                draws(0L, 0));
        assertEquals(List.of("cobblestone5", "cobblestone5", "cobblestone5", "raw_iron5", "cobblestone5",
                        "cobblestone5", "raw_copper5", "cobblestone5", "cobblestone5", "cobblestone5", "cobblestone5",
                        "cobblestone5"),
                draws(0x0123456789ABCDEFL, 0));
        assertEquals(List.of("cobblestone5", "cobblestone5", "cobblestone5", "cobblestone5", "cobblestone5",
                        "cobblestone5", "cobblestone5", "raw_copper5", "cobblestone5", "cobblestone5", "cobblestone5",
                        "raw_copper5"),
                draws(0xFEDCBA9876543210L, 1000));
    }

    @Test
    void theSameSeedAndIndexAlwaysGiveTheSameStack() {
        for (long n = 0; n < 64; n++) {
            assertEquals(LaserDraw.draw(REFERENCE, 42L, n), LaserDraw.draw(REFERENCE, 42L, n));
        }
    }

    @Test
    void theEntryIsTheFirstWhoseCumulativeWeightExceedsTheRemainder() {
        LaserDrillTable table = new LaserDrillTable(ResourceLocation.tryBuild("test", "split"), List.of(), false,
                List.of(entry("cobblestone", 1, 3), entry("diamond", 1, 1)), "0000000000000000");
        for (long n = 0; n < 512; n++) {
            long r = (LaserDraw.output(7L, n) >>> 1) % 4;
            assertEquals(r < 3 ? "cobblestone" : "diamond", LaserDraw.draw(table, 7L, n).item().getPath());
        }
    }

    @Test
    void aNegativeIndexIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LaserDraw.draw(REFERENCE, 0L, -1L));
    }

    private static List<String> draws(long seed, long from) {
        List<String> out = new ArrayList<>();
        for (long n = from; n < from + 12; n++) {
            LaserDrillTable.Entry entry = LaserDraw.draw(REFERENCE, seed, n);
            out.add(entry.item().getPath() + entry.count());
        }
        return out;
    }

    static LaserDrillTable.Entry entry(String item, int count, int weight) {
        return new LaserDrillTable.Entry(ResourceLocation.tryBuild("minecraft", item), count, weight);
    }
}
