package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.client.equipment.SuitWorkstationScreen;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180EquipmentData;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationMenu;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Default-font advance estimates, as in StationControllerLayoutTest; not a rendered V1 approval. */
class MachineScreenLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void allInventorySlotsHaveAnEighteenPixelFrameAndASeparateSixteenPixelRecess() {
        for (int[] layout : new int[][]{{17, 129, 187, 196, 212}, {18, 145, 203, 244, 225},
                {8, SuitWorkstationMenu.INVENTORY_Y, SuitWorkstationMenu.HOTBAR_Y, 176, SuitWorkstationScreen.PANEL_HEIGHT}}) {
            var slots = new ArrayList<Slot>();
            var inventory = new SimpleContainer(36);
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 9; column++) {
                    slots.add(new Slot(inventory, row * 9 + column, layout[0] + column * 18,
                            row == 3 ? layout[2] : layout[1] + row * 18));
                }
            }
            var fills = new ArrayList<int[]>();
            ContainerSlotBackground.draw(slots, 20, 30, 1, 2,
                    (x, y, right, bottom, color) -> fills.add(new int[]{x, y, right, bottom, color}));
            assertEquals(72, fills.size());
            for (int i = 0; i < slots.size(); i++) {
                var slot = slots.get(i);
                int[] frame = fills.get(i * 2), recess = fills.get(i * 2 + 1);
                assertArrayEquals(new int[]{20 + slot.x - 1, 30 + slot.y - 1,
                        20 + slot.x + 17, 30 + slot.y + 17, 1}, frame);
                assertArrayEquals(new int[]{20 + slot.x, 30 + slot.y,
                        20 + slot.x + 16, 30 + slot.y + 16, 2}, recess);
                assertTrue(frame[0] > 20 && frame[2] < 20 + layout[3]);
                assertTrue(frame[1] > 30 && frame[3] < 30 + layout[4]);
            }
        }
    }

    @Test void bilingualAssemblerHeadersFitInsideTheFrameAndAboveSlotsAndGauge() throws Exception {
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var path = Path.of("src/generated/v1.2/resources/assets/advancedrocketrycommunity_v120/lang", locale + ".json");
            var language = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int outputLeft = PrecisionAssemblerScreen.OUTPUT_LABEL_RIGHT
                    - width(language.get("screen.advancedrocketrycommunity.precision_assembler.outputs").getAsString());
            assertTrue(outputLeft >= 151 + width("FE") + 4, locale);
            assertTrue(PrecisionAssemblerScreen.OUTPUT_LABEL_RIGHT <= 239 - 4);
            assertTrue(18 + width(language.get("screen.advancedrocketrycommunity.precision_assembler.inputs").getAsString())
                    <= 151 - 4, locale);
            assertTrue(PrecisionAssemblerScreen.RESOURCE_LABEL_Y >= 18 + 4);
            assertTrue(PrecisionAssemblerScreen.RESOURCE_LABEL_Y + 9 + 2 <= PrecisionAssemblerScreen.ENERGY_GAUGE_Y);
            assertTrue(PrecisionAssemblerScreen.RESOURCE_LABEL_Y + 9 + 2 <= 35);
            assertEquals(71, PrecisionAssemblerScreen.ENERGY_GAUGE_Y + PrecisionAssemblerScreen.ENERGY_GAUGE_HEIGHT);
        }
    }

    @Test void suitHintButtonsAndInventoryTitleOccupySeparateRowsAtScalesTwoAndThree() {
        for (int scale : new int[]{2, 3}) {
            assertTrue(SuitWorkstationScreen.PANEL_HEIGHT * scale <= 720);
            assertTrue(75 + 16 + 2 <= SuitWorkstationScreen.HINT_Y);
            assertTrue(SuitWorkstationScreen.HINT_Y + 9 + 3 <= SuitWorkstationScreen.INVENTORY_TITLE_Y);
            assertTrue(SuitWorkstationScreen.INVENTORY_TITLE_Y + 9 + 2 <= SuitWorkstationMenu.INVENTORY_Y - 1);
            for (boolean chinese : new boolean[]{false, true}) {
                String hint = V180EquipmentData.language(chinese).get("screen.advancedrocketrycommunity.suit.remove_hint");
                assertTrue((8 + width(hint) + 8) * scale <= 176 * scale, hint);
            }
        }
    }

    private static int width(String text) { return text.codePoints().map(MachineScreenLayoutTest::advance).sum(); }

    private static int advance(int character) {
        if (character >= 0x2E80) { return 9; }
        String value = Character.toString(character);
        if ("i!.,:;'|".contains(value)) { return 2; }
        if ("l`".contains(value)) { return 3; }
        if ("tI[] ".contains(value)) { return 4; }
        return "fk(){}<>\"*".contains(value) ? 5 : 6;
    }
}
