package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180SuitComponentArt;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData.Gas;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

/** ADR-066 section 7.1 worn component mounts: bounded summaries, the box layout and its NEW texture. */
class SuitComponentsTest {
    /** Each part's texture offset and box size, as {@link SuitComponentLayer#mesh()} defines them. */
    private static final int[][] BOXES = {
            {0, 0, 8, 10, 2}, {0, 12, 3, 9, 3}, {12, 12, 2, 2, 2}, {20, 0, 3, 8, 3}, {0, 24, 8, 3, 1}};
    private static final List<String> PARTS = List.of("frame", "left_tank", "right_tank", "left_nozzle",
            "right_nozzle", "back_tank_left", "back_tank_right", "visor");

    @Test
    void everyCombinationStaysWithinEightAttachments() {
        List<ModuleKind> visors = new ArrayList<>();
        visors.add(null);
        visors.add(ModuleKind.ANTI_FOG);
        visors.add(ModuleKind.EARTHBRIGHT);
        for (boolean jetpack : new boolean[] {false, true}) {
            for (int tanks = 0; tanks <= SuitComponents.MAX_TANKS; tanks++) {
                for (ModuleKind visor : visors) {
                    SuitComponents components = new SuitComponents(jetpack,
                            java.util.Collections.nCopies(tanks, Gas.HYDROGEN), visor);
                    assertTrue(components.attachments() <= SuitComponents.MAX_ATTACHMENTS);
                    assertEquals(!jetpack && tanks == 0 && visor == null, components.empty());
                }
            }
        }
        assertEquals(6, new SuitComponents(true, List.of(Gas.OXYGEN, Gas.HYDROGEN), ModuleKind.ANTI_FOG)
                .attachments());
        assertTrue(SuitComponents.NONE.empty());
        assertEquals(0, SuitComponents.NONE.attachments());
    }

    @Test
    void tanksAreBoundedAndMissingPositionsAreEmpty() {
        SuitComponents one = new SuitComponents(true, List.of(Gas.OXYGEN), null);
        assertEquals(Gas.OXYGEN, one.tank(0));
        assertEquals(Gas.EMPTY, one.tank(1));
        assertThrows(IndexOutOfBoundsException.class, () -> one.tank(2));
        assertThrows(IndexOutOfBoundsException.class, () -> one.tank(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new SuitComponents(false, List.of(Gas.OXYGEN, Gas.OXYGEN, Gas.OXYGEN), null));
        assertThrows(IllegalArgumentException.class, () -> new SuitComponents(false, List.of(), ModuleKind.HOVER));
        assertThrows(NullPointerException.class, () -> new SuitComponents(false, null, null));
    }

    @Test
    void theLayerDefinesEveryPartOnTheTextureSheetSize() {
        ModelPart root = SuitComponentLayer.mesh().bakeRoot();
        for (String part : PARTS) {
            assertDoesNotThrow(() -> root.getChild(part), part);
        }
        assertEquals(V180SuitComponentArt.SIZE, SuitComponentLayer.TEXTURE_SIZE);
    }

    @Test
    void everyBoxFaceIsPaintedAndNothingElseIs() {
        int size = V180SuitComponentArt.SIZE;
        int[] pixels = V180SuitComponentArt.pixels();
        boolean[] used = new boolean[size * size];
        for (int[] box : BOXES) {
            int u = box[0];
            int v = box[1];
            int width = box[2];
            int height = box[3];
            int depth = box[4];
            // Vanilla box UVs: top and bottom faces in the first row band, the four sides below them.
            mark(used, size, u + depth, v, 2 * width, depth);
            mark(used, size, u, v + depth, 2 * (width + depth), height);
        }
        for (int i = 0; i < pixels.length; i++) {
            int alpha = pixels[i] >>> 24;
            if (used[i]) {
                assertEquals(0xFF, alpha, "face pixel " + (i % size) + "," + (i / size) + " must be opaque");
            } else {
                assertFalse(alpha != 0 && alpha != 0xFF, "no partial alpha at " + (i % size) + "," + (i / size));
            }
        }
    }

    @Test
    void theArtIsDeterministic() {
        assertArrayEquals(V180SuitComponentArt.pixels(), V180SuitComponentArt.pixels());
        assertArrayEquals(V180SuitComponentArt.png(), V180SuitComponentArt.png());
    }

    @Test
    void theCommittedTextureIsTheGeneratorsOutput() throws IOException {
        Path committed = Path.of("src", "generated", "v1.8", "resources", "assets", "advancedrocketrycommunity",
                "textures", "entity", "suit_components.png");
        assertArrayEquals(V180SuitComponentArt.png(), Files.readAllBytes(committed));
    }

    private static void mark(boolean[] used, int size, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                assertTrue(column < size && row < size, "UV outside the sheet");
                used[row * size + column] = true;
            }
        }
    }
}
