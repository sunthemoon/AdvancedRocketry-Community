package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ScreenLayoutLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Logical-pixel geometry and estimated default-font advances, not a real-client visual approval. */
class RemainingScreenLayoutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cargoGaugeCannotIntersectAnyInputOrReceiveSlotAtAnyCharge() {
        for (int slot = 0; slot < 9; slot++) {
            int x = 8 + slot * 18;
            assertFalse(intersects(CargoScreenLayout.ENERGY_X, CargoScreenLayout.ENERGY_Y,
                    CargoScreenLayout.ENERGY_WIDTH, CargoScreenLayout.ENERGY_HEIGHT, x - 1, 53, 18, 18));
            if (slot < 4) {
                assertFalse(intersects(CargoScreenLayout.ENERGY_X, CargoScreenLayout.ENERGY_Y,
                        CargoScreenLayout.ENERGY_WIDTH, CargoScreenLayout.ENERGY_HEIGHT, x - 1, 17, 18, 18));
            }
        }
        assertTrue(CargoScreenLayout.ENERGY_BOTTOM + 3 <= 44); // Received label.
        for (int capacity : new int[]{500_000, 1_000_000}) {
            assertEquals(0, CargoScreenLayout.fill(0, capacity));
            assertEquals(1, CargoScreenLayout.fill(1, capacity));
            for (int percent = 1; percent <= 100; percent++) {
                int height = CargoScreenLayout.fill(capacity * percent / 100, capacity);
                assertTrue(height >= 1 && height <= CargoScreenLayout.ENERGY_HEIGHT);
            }
            assertEquals(CargoScreenLayout.ENERGY_HEIGHT, CargoScreenLayout.fill(capacity, capacity));
        }
    }

    @Test void electrolyzerChargeFitsWithinItsPanelAndAboveTheStatus() {
        assertTrue(ElectrolyzerMenu.CHARGE_X - 1 > 4);
        assertTrue(ElectrolyzerMenu.CHARGE_Y - 1 > 17);
        assertTrue(ElectrolyzerMenu.CHARGE_Y + 17 < 71);
        assertTrue(ElectrolyzerMenu.CHARGE_Y + 17 + 3 < 72);
        assertFalse(intersects(ElectrolyzerMenu.CHARGE_X - 1, ElectrolyzerMenu.CHARGE_Y - 1, 18, 18,
                43, 34, 18, 18));
    }

    @Test void bayPhaseInventoryTitleAndSlotsHaveSeparateRows() {
        int titleY = SatelliteBayMenu.PANEL_HEIGHT - 94;
        assertTrue(63 + 9 + 3 <= titleY);
        assertTrue(titleY + 9 + 2 <= SatelliteBayMenu.INVENTORY_Y - 1);
        assertTrue(SatelliteBayMenu.HOTBAR_Y + 17 < SatelliteBayMenu.PANEL_HEIGHT - 4);
    }

    @Test void allRemainingInventoryLayoutsHaveSeparateFramesForAllThirtySixSlots() {
        for (int[] layout : new int[][]{{31, 152, 210, 352, 234}, {31, 118, 176, 224, 202},
                {8, SatelliteBayMenu.INVENTORY_Y, SatelliteBayMenu.HOTBAR_Y, 176, SatelliteBayMenu.PANEL_HEIGHT},
                {8, 140, 198, 176, 222}, {8, 104, 162, 236, 186}, {8, 84, 142, 176, 166},
                {8, 94, 152, 176, 176}}) {
            var slots = new ArrayList<Slot>();
            var inventory = new SimpleContainer(36);
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 9; column++) {
                    slots.add(new Slot(inventory, row * 9 + column, layout[0] + column * 18,
                            row == 3 ? layout[2] : layout[1] + row * 18));
                }
            }
            var fills = new ArrayList<int[]>();
            ContainerSlotBackground.draw(slots, 0, 0, 1, 2,
                    (x, y, right, bottom, color) -> fills.add(new int[]{x, y, right, bottom, color}));
            assertEquals(72, fills.size());
            for (int index = 0; index < slots.size(); index++) {
                var slot = slots.get(index);
                assertArrayEquals(new int[]{slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, 1}, fills.get(index * 2));
                assertArrayEquals(new int[]{slot.x, slot.y, slot.x + 16, slot.y + 16, 2}, fills.get(index * 2 + 1));
                assertTrue(slot.x + 17 < layout[3] && slot.y + 17 < layout[4]);
            }
        }
    }

    @Test void builderUsesItsOwnSuccessLabelAndDoesNotTreatACompletedOutputAsAFailure() {
        var preview = SatelliteOperationCode.OUTPUT_BLOCKED;
        var code = SatelliteScreenLabels.builderStatus(SatelliteOperationCode.SUCCESS, preview);
        assertEquals(SatelliteOperationCode.SUCCESS, code);
        assertEquals("screen.advancedrocketrycommunity.satellite_builder.output_ready",
                SatelliteScreenLabels.builderKey(code, preview));
        assertEquals("screen.advancedrocketrycommunity.satellite_builder.ready",
                SatelliteScreenLabels.builderKey(SatelliteOperationCode.SUCCESS, SatelliteOperationCode.SUCCESS));
        for (var failure : SatelliteOperationCode.values()) {
            if (failure != SatelliteOperationCode.SUCCESS) {
                assertEquals(failure, SatelliteScreenLabels.builderStatus(failure, preview));
                assertEquals(failure.translationKey(), SatelliteScreenLabels.builderKey(failure, preview));
            }
        }
        assertEquals(SatelliteOperationCode.INVALID_COMPONENTS,
                SatelliteScreenLabels.builderStatus(SatelliteOperationCode.SUCCESS, SatelliteOperationCode.INVALID_COMPONENTS));
    }

    @Test void successfulCurrentViewsDoNotDisplayOldStopsOrCompletedConfirmations() {
        for (var last : EndgameCode.values()) { assertFalse(EndgameScreenLabels.showLastStop(EndgameCode.OK, last)); }
        assertFalse(EndgameScreenLabels.showLastStop(EndgameCode.STOPPED, EndgameCode.CONFIRM_REQUIRED));
        assertFalse(EndgameScreenLabels.showLastStop(EndgameCode.STOPPED, EndgameCode.STOPPED));
        assertTrue(EndgameScreenLabels.showLastStop(EndgameCode.STOPPED, EndgameCode.INSUFFICIENT_ENERGY));
    }

    @Test void diagnosticPagesStayInsideTheScreenAndCanReachEveryWrappedRow() {
        for (int screenHeight : new int[]{180, 240, 360, 540, 720}) {
            int capacity = TooltipPageLayout.capacity(screenHeight);
            assertTrue((capacity + 1) * 10 + 16 <= screenHeight);
            assertEquals(0, TooltipPageLayout.offset(-50, 90, capacity));
            int last = TooltipPageLayout.offset(Integer.MAX_VALUE, 90, capacity);
            assertEquals(90, last + capacity);
            boolean[] visited = new boolean[90];
            for (int start = 0; start <= last; start++) {
                int offset = TooltipPageLayout.offset(start, 90, capacity);
                for (int row = offset; row < offset + capacity; row++) { visited[row] = true; }
            }
            for (boolean row : visited) { assertTrue(row); }
            assertEquals(0, TooltipPageLayout.offset(last, 1, capacity));
        }
    }

    @Test void shortLabelsAndTerminalButtonsFitInBothLanguages() {
        for (boolean chinese : new boolean[]{false, true}) {
            var language = V180ScreenLayoutLanguage.entries(chinese);
            assertTrue(width(language.get("screen.advancedrocketrycommunity.satellite.launch_short")) <= 46 - 6);
            assertTrue(width(language.get("screen.advancedrocketrycommunity.satellite.power_short").formatted(10_000)) <= 112);
            assertTrue(width(chinese ? "取消" : "CANCEL") <= 66 - 6);
            assertTrue(width(chinese ? "领取" : "CLAIM") <= 46 - 6);
            for (var entry : language.entrySet()) { assertFalse(entry.getValue().isBlank(), entry.getKey()); }
            String status = language.get("screen.advancedrocketrycommunity.satellite_builder.output_ready") + " [SUCCESS]";
            assertTrue(width(status) <= 180, status);
            var fit = BoundedTextLayout.fit(status, 180, RemainingScreenLayoutTest::width);
            assertTrue(width(fit.text()) * fit.scale() <= 180);
        }
    }

    @Test void stationChoicesAndRoutesFitWithoutDiscardingTheOriginalHoverLabel() {
        for (String station : new String[]{"ArceTester1 Station", "A".repeat(32), "空间站".repeat(10)}) {
            String choice = "STATION " + station + " (1 / 32)";
            var fit = BoundedTextLayout.fit(choice, 98 - 6, RemainingScreenLayoutTest::width);
            assertTrue(fit.shortened());
            assertTrue(fit.text().endsWith("..."));
            assertTrue(width(fit.text()) <= 92);
            String route = station + " -> Tau Ceti f";
            var routeFit = BoundedTextLayout.fit(route, 204, RemainingScreenLayoutTest::width);
            assertTrue(width(routeFit.text()) <= 204);
            if (width(route) <= 204) { assertEquals(route, routeFit.text()); }
            else { assertTrue(routeFit.shortened()); }
            assertTrue(route.endsWith("Tau Ceti f"));
        }
    }

    private static int width(String text) {
        return text.codePoints().map(c -> c >= 0x2E80 ? 9 : "il.,:;!".indexOf(c) >= 0 ? 2 : c == ' ' ? 4 : 6).sum();
    }

    private static boolean intersects(int x, int y, int width, int height, int sx, int sy, int sw, int sh) {
        return x < sx + sw && sx < x + width && y < sy + sh && sy < y + height;
    }
}
