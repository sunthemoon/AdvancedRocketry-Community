package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ClassicJeiLayoutTest {
    @Test void centrifugeFitsTheScaleThreeWindowIncludingEveryBoundedOutput() {
        // JEI 15.56 at 1280x720, scale 3: a 142-pixel recipe area with a 4-pixel border per side.
        assertTrue(ClassicJeiLayout.CENTRIFUGE_HEIGHT + 8 <= 142);
        assertTrue(ClassicJeiLayout.WIDTH <= 190);
        int cost = ClassicJeiLayout.costY(true);
        assertTrue(4 + 3 * 18 + 17 < cost); // All four ordinary item rows, including the slot frame.
        var rectangles = new ArrayList<int[]>();
        // The recipe codec permits sixteen independent chance outputs, not just the five built-in ones.
        for (int index = 0; index < 16; index++) {
            int x = ClassicJeiLayout.chanceX(index), y = ClassicJeiLayout.chanceY(index);
            assertTrue(x - 1 >= 0 && x + 17 <= ClassicJeiLayout.WIDTH);
            assertTrue(cost + 9 < y - 1);
            assertTrue(y + 17 < ClassicJeiLayout.CENTRIFUGE_HEIGHT);
            for (int[] other : rectangles) {
                assertFalse(x - 1 < other[0] + 17 && x + 17 > other[0] - 1
                        && y - 1 < other[1] + 17 && y + 17 > other[1] - 1);
            }
            rectangles.add(new int[]{x, y});
        }
        assertEquals(104, ClassicJeiLayout.HEIGHT);
        assertEquals(84, ClassicJeiLayout.costY(false));
    }

    @Test void optionalJeiAdapterUsesTheTestedGeometry() throws Exception {
        // JEI is compile-only: guard the adapter wiring without requiring it on the unit-test runtime.
        String source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/compat/jei/ClassicMachineJeiCategory.java"));
        assertTrue(source.contains("type == CENTRIFUGE ? ClassicJeiLayout.CENTRIFUGE_HEIGHT : ClassicJeiLayout.HEIGHT"));
        assertTrue(source.contains("addOutputSlot(ClassicJeiLayout.chanceX(index), ClassicJeiLayout.chanceY(index))"));
        assertTrue(source.contains("ClassicJeiLayout.costY(getRecipeType() == CENTRIFUGE)"));
    }
}
