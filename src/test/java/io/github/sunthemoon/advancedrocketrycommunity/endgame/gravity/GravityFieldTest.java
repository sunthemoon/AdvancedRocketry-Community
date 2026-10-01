package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-058 sections 2 to 5 against the C10 reference vectors ({@code examples.json}, {@code gravity}). */
final class GravityFieldTest {
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final ResourceLocation OTHER = ResourceLocation.tryBuild("minecraft", "the_nether");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void boxesAndClippingMatchTheReferenceVectors() {
        assertEquals(Optional.of(new GravityField.Box(-8, 56, -8, 8, 72, 8)),
                GravityField.Box.of(0, 64, 0, 8, Optional.empty()));
        assertEquals(Optional.of(new GravityField.Box(90, 54, -60, 116, 86, -40)),
                GravityField.Box.of(100, 70, -50, 16, Optional.of(new GravityField.Clip(90, -60, 120, -40))));
        assertEquals(Optional.empty(), GravityField.Box.of(0, 64, 0, 4, Optional.of(new GravityField.Clip(10, 10,
                20, 20))), "an empty clip is FIELD_OUTSIDE_STATION");
        assertThrows(IllegalArgumentException.class, () -> GravityField.Box.of(0, 0, 0, 1, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> GravityField.Box.of(0, 0, 0, 17, Optional.empty()));
        assertEquals(33L * 33 * 33, GravityField.Box.of(0, 0, 0, 16, Optional.empty()).orElseThrow().volume());
    }

    @Test
    void upkeepIsFivePlusTwiceTheRadius() {
        assertEquals(9, GravityField.upkeep(2));
        assertEquals(21, GravityField.upkeep(8));
        assertEquals(37, GravityField.upkeep(16));
    }

    @Test
    void settingsHaveTheirRangesAndSteps() {
        for (int bad : new int[]{5, 205, 52}) {
            assertThrows(IllegalArgumentException.class, () -> field("aaaaaaaa-0000-0000-0000-000000000000", A, 0, 64,
                    0, 8, bad, false));
        }
        field("aaaaaaaa-0000-0000-0000-000000000000", A, 0, 64, 0, 8, 10, false);
        field("aaaaaaaa-0000-0000-0000-000000000000", A, 0, 64, 0, 8, 200, false);
    }

    @Test
    void winnersMatchTheReferenceVectors() {
        GravityField large = field("aaaaaaaa-0000-0000-0000-000000000000", A, 0, 64, 0, 8, 50, true);
        GravityField small = field("ffffffff-0000-0000-0000-000000000000", A, 0, 64, 0, 2, 50, true);
        assertEquals(small, GravityField.winner(List.of(large, small), 1, 64, 1, B, Set.of()).orElseThrow(),
                "nested: the smaller box wins");
        GravityField high = field("80000000-0000-0000-0000-000000000000", A, 0, 64, 0, 4, 50, true);
        GravityField low = field("7fffffff-0000-0000-0000-000000000000", A, 2, 64, 0, 4, 50, true);
        assertTrue(UUID.fromString("80000000-0000-0000-0000-000000000000")
                .compareTo(UUID.fromString("7fffffff-0000-0000-0000-000000000000")) < 0, "UUID.compareTo differs");
        assertEquals(low, GravityField.winner(List.of(high, low), 1, 64, 0, B, Set.of()).orElseThrow(),
                "equal volume: the lower canonical string wins");
        GravityField far = field("7fffffff-0000-0000-0000-000000000000", A, 0, 64, 0, 16, 50, true);
        assertEquals(Optional.empty(), GravityField.winner(List.of(far), 100, 64, 100, B, Set.of()));
        GravityField edge = field("80000000-0000-0000-0000-000000000000", A, 0, 64, 0, 8, 50, true);
        assertEquals(edge, GravityField.winner(List.of(edge), 8, 72, -8, B, Set.of()).orElseThrow(),
                "the boundary is inclusive");
    }

    @Test
    void consentMatchesTheReferenceVectors() {
        assertConsent(true, A, B, Set.of(), 200, true, 100);
        assertConsent(true, A, B, Set.of(), 50, true, 50);
        assertConsent(false, A, A, Set.of(), 200, true, 200);
        assertConsent(false, A, B, Set.of(), 200, false, 200);
        assertConsent(false, A, B, Set.of(A), 10, true, 10);
        assertConsent(false, A, B, Set.of(C), 10, false, 10);
    }

    /**
     * The consent rationale (review R1-M9): with vanilla jump physics (0.42 up, gravity 0.08 × g, drag 0.98) a
     * player still clears a full block at 1.35 g but not from 1.40 g.
     */
    @Test
    void jumpHeightThresholdsMatchTheReferenceVectors() {
        double[][] vectors = {{0.1, 1}, {1.0, 1}, {1.35, 1}, {1.4, 0}, {2.0, 0}};
        for (double[] vector : vectors) {
            assertEquals(vector[1] == 1, jumpPeak(vector[0]) >= 1.0D, "g = " + vector[0]);
        }
        assertTrue(jumpPeak(2.0) < 0.78 && jumpPeak(2.0) > 0.76, "2.00 g peaks near 0.77 blocks");
        assertTrue(jumpPeak(0.1) > 6.5 && jumpPeak(0.1) < 7.2, "0.10 g peaks near 6.9 blocks");
    }

    @Test
    void theIndexBucketsByChunkAndEnforcesDensityAndCaps() {
        GravityFieldIndex index = new GravityFieldIndex();
        GravityFieldLimits limits = new GravityFieldLimits(16, 4, 8, 256, 1024);
        List<GravityField> fields = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            GravityField field = field(String.format("%08x-0000-0000-0000-000000000000", i + 1), A, 8, 64, 8, 2, 50,
                    false);
            fields.add(field);
            assertEquals(EndgameCode.OK, index.add(field, limits));
        }
        assertEquals(EndgameCode.FIELD_DENSITY, index.add(field("00000009-0000-0000-0000-000000000000", A, 8, 64, 8,
                2, 50, false), limits), "a fifth field of one owner in one chunk");
        assertEquals(EndgameCode.OK, index.add(field("0000000a-0000-0000-0000-000000000000", B, 8, 64, 8, 2, 50,
                false), limits), "another owner still fits");
        GravityFieldLimits tight = new GravityFieldLimits(5, 4, 8, 256, 1024);
        assertEquals(EndgameCode.FIELD_DENSITY, index.add(field("0000000b-0000-0000-0000-000000000000", C, 8, 64, 8,
                2, 50, false), tight), "the chunk is full");
        assertEquals(EndgameCode.ACTIVE_LIMIT, index.add(field("0000000c-0000-0000-0000-000000000000", C, 100, 64,
                100, 2, 50, false), new GravityFieldLimits(16, 4, 8, 256, 5)), "the server cap");
        assertEquals(EndgameCode.ACTIVE_LIMIT, index.add(field("0000000d-0000-0000-0000-000000000000", A, 100, 64,
                100, 2, 50, false), new GravityFieldLimits(16, 4, 4, 256, 1024)), "the owner cap");
        assertEquals(5, index.size());

        assertEquals(OptionalDouble.of(0.5D), index.at(LEVEL, 8, 64, 8, A, Set.of()), "the owner is affected");
        assertEquals(OptionalDouble.empty(), index.at(LEVEL, 8, 64, 8, C, Set.of()), "a stranger is not");
        assertEquals(OptionalDouble.empty(), index.at(OTHER, 8, 64, 8, A, Set.of()), "another Level");
        assertEquals(OptionalDouble.empty(), index.at(LEVEL, 30, 64, 8, A, Set.of()), "outside every box");

        // A box across a chunk corner is listed in all four chunks.
        GravityField corner = field("0000000e-0000-0000-0000-000000000000", C, 16, 64, 16, 3, 150, false);
        assertEquals(EndgameCode.OK, index.add(corner, limits));
        assertEquals(OptionalDouble.of(1.5D), index.at(LEVEL, 13, 62, 13, C, Set.of()));
        assertEquals(OptionalDouble.of(1.5D), index.at(LEVEL, 19, 66, 19, C, Set.of()));
        index.remove(corner.id());
        assertEquals(OptionalDouble.empty(), index.at(LEVEL, 19, 66, 19, C, Set.of()));
        assertEquals(5, index.clearLevel(LEVEL).size());
        assertEquals(0, index.size());
    }

    @Test
    void readdingAFieldReplacesIt() {
        GravityFieldIndex index = new GravityFieldIndex();
        GravityField first = field("00000001-0000-0000-0000-000000000000", A, 8, 64, 8, 2, 50, false);
        assertEquals(EndgameCode.OK, index.add(first, GravityFieldLimits.DEFAULTS));
        GravityField moved = field("00000001-0000-0000-0000-000000000000", A, 40, 64, 40, 4, 150, false);
        assertEquals(EndgameCode.OK, index.add(moved, GravityFieldLimits.DEFAULTS));
        assertEquals(1, index.size());
        assertEquals(OptionalDouble.empty(), index.at(LEVEL, 8, 64, 8, A, Set.of()));
        assertEquals(OptionalDouble.of(1.5D), index.at(LEVEL, 40, 64, 40, A, Set.of()));
        assertFalse(index.contains(UUID.randomUUID()));
    }

    private static void assertConsent(boolean inStation, UUID owner, UUID player, Set<UUID> trusts, int m,
                                      boolean affected, int value) {
        GravityField field = new GravityField(UUID.randomUUID(), owner, LEVEL,
                GravityField.Box.of(0, 64, 0, 8, Optional.empty()).orElseThrow(), m, inStation);
        assertEquals(affected, field.affects(player, trusts));
        assertEquals(value, field.effective());
    }

    private static double jumpPeak(double gravity) {
        double y = 0.0D;
        double velocity = 0.42D;
        double peak = 0.0D;
        while (velocity > 0.0D) {
            y += velocity;
            peak = Math.max(peak, y);
            velocity = (velocity - 0.08D * gravity) * 0.98D;
        }
        return peak;
    }

    static GravityField field(String id, UUID owner, int x, int y, int z, int radius, int multiplier,
                              boolean inStation) {
        return new GravityField(UUID.fromString(id), owner, LEVEL,
                GravityField.Box.of(x, y, z, radius, Optional.empty()).orElseThrow(), multiplier, inStation);
    }
}
