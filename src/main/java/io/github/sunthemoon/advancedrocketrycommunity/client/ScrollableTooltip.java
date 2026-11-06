package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A viewport-sized diagnostic tooltip; scroll while hovering its source to read every wrapped row. */
final class ScrollableTooltip {
    private int offset;
    private int maximum;
    private boolean hovering;

    void beginFrame() { hovering = false; }

    void render(GuiGraphics graphics, Font font, List<Component> details, int mouseX, int mouseY) {
        hovering = true;
        List<FormattedCharSequence> rows = new ArrayList<>();
        int width = Math.max(1, Math.min(320, graphics.guiWidth() - 24));
        details.forEach(line -> rows.addAll(font.split(line, width)));
        int capacity = TooltipPageLayout.capacity(graphics.guiHeight());
        maximum = Math.max(0, rows.size() - capacity);
        offset = TooltipPageLayout.offset(offset, rows.size(), capacity);
        List<FormattedCharSequence> shown = new ArrayList<>(rows.subList(offset, Math.min(rows.size(), offset + capacity)));
        if (maximum > 0) {
            shown.add(Component.translatable("screen.advancedrocketrycommunity.tooltip.scroll",
                    offset + 1, Math.min(rows.size(), offset + capacity), rows.size()).getVisualOrderText());
        }
        graphics.renderTooltip(font, shown, mouseX, mouseY);
    }

    boolean scroll(double delta) {
        if (!hovering || maximum == 0 || delta == 0) { return false; }
        offset = Math.max(0, Math.min(maximum, offset + (delta > 0 ? -3 : 3)));
        return true;
    }
}
