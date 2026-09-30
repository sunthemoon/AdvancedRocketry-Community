package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Community-authored display of one survey scan (ADR-049 section 8): north up, one square per cell, ore share
 * as brightness, the dominant biome as a coloured corner. Transient; closing it discards the result.
 */
public final class SurveyScanScreen extends Screen {
    private static final int MAP = 192;
    private static final int PANEL = 0xF0101A1E;
    private static final int EDGE = 0xFF5CA8B8;
    private static final int TEXT = 0xFFDCECF0;
    private static final int MUTED = 0xFF87A4AC;
    private static final int UNKNOWN = 0xFF2A2A2A;
    private static final int[] BIOME_COLORS = {
            0xFF4CAF50, 0xFF2196F3, 0xFFFFC107, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4, 0xFF8BC34A, 0xFFFF5722,
            0xFF795548, 0xFF607D8B, 0xFFCDDC39, 0xFF3F51B5, 0xFFFF9800, 0xFF009688, 0xFFF44336, 0xFF9E9E9E
    };
    private static final int OTHER_COLOR = 0xFFFFFFFF;

    private final SurveyScanResultPacket result;

    public SurveyScanScreen(SurveyScanResultPacket result) {
        super(Component.translatable("screen.advancedrocketrycommunity.survey_scan.title",
                result.centreX(), result.centreZ()));
        this.result = result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int side = result.side();
        int cellPixels = MAP / side;
        int width = 2 * 8 + MAP + 120;
        int left = (this.width - width) / 2;
        int top = (this.height - (MAP + 36)) / 2;
        graphics.fill(left, top, left + width, top + MAP + 36, PANEL);
        graphics.renderOutline(left, top, width, MAP + 36, EDGE);
        graphics.drawString(font, title, left + 8, top + 8, TEXT, false);
        int mapLeft = left + 8;
        int mapTop = top + 24;
        Component hovered = null;
        for (int index = 0; index < result.cells().size(); index++) {
            SurveyScanResultPacket.Cell cell = result.cells().get(index);
            int x = mapLeft + (index % side) * cellPixels;
            int y = mapTop + (index / side) * cellPixels;
            graphics.fill(x, y, x + cellPixels, y + cellPixels, cell.unknown() ? UNKNOWN : heat(cell.ratio()));
            if (!cell.unknown()) {
                int corner = Math.max(2, cellPixels / 4);
                graphics.fill(x, y, x + corner, y + corner, biomeColor(cell.biome()));
            }
            if (mouseX >= x && mouseX < x + cellPixels && mouseY >= y && mouseY < y + cellPixels) {
                hovered = describe(cell);
            }
        }
        int middle = side / 2;
        graphics.renderOutline(mapLeft + middle * cellPixels - 1, mapTop + middle * cellPixels - 1, 3, 3, TEXT);
        graphics.renderOutline(mapLeft - 1, mapTop - 1, MAP + 2, MAP + 2, EDGE);
        int legendLeft = mapLeft + MAP + 8;
        int legendTop = mapTop;
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.survey_scan.legend"),
                legendLeft, legendTop, MUTED, false);
        List<Component> names = new ArrayList<>();
        for (ResourceLocation biome : result.palette()) {
            names.add(Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath()));
        }
        for (int index = 0; index < names.size(); index++) {
            int y = legendTop + 12 + index * 10;
            graphics.fill(legendLeft, y, legendLeft + 6, y + 6, biomeColor(index));
            graphics.drawString(font, font.plainSubstrByWidth(names.get(index).getString(), 100),
                    legendLeft + 9, y - 1, TEXT, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private Component describe(SurveyScanResultPacket.Cell cell) {
        if (cell.unknown()) {
            return Component.translatable("screen.advancedrocketrycommunity.survey_scan.unknown");
        }
        String share = String.format(Locale.ROOT, "%.2f", cell.ratio() * 100.0D / 65_535.0D);
        Component biome = cell.biome() < result.palette().size()
                ? Component.translatable("biome." + result.palette().get(cell.biome()).getNamespace() + "."
                        + result.palette().get(cell.biome()).getPath())
                : Component.translatable("screen.advancedrocketrycommunity.survey_scan.other");
        return Component.translatable("screen.advancedrocketrycommunity.survey_scan.cell", share, biome);
    }

    /** Brightness grows with the square root of the ore share, so small shares stay visible. */
    private static int heat(int ratio) {
        double t = Math.sqrt(ratio / 65_535.0D);
        int red = (int) (0x10 + t * (0xFF - 0x10));
        int green = (int) (0x30 + t * (0xC8 - 0x30));
        int blue = (int) (0x2A + t * (0x57 - 0x2A));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int biomeColor(int biome) {
        return biome < BIOME_COLORS.length ? BIOME_COLORS[biome] : OTHER_COLOR;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
