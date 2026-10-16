package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Per-render label bounds and full-text tooltips, in container-local coordinates. */
public final class ScreenText {
    private final List<Label> shortened = new ArrayList<>();

    public void clear() { shortened.clear(); }

    public void draw(GuiGraphics graphics, Font font, Component full, int x, int y, int width, int color) {
        draw(graphics, font, full, x, y, width, color, false);
    }

    public void centered(GuiGraphics graphics, Font font, Component full, int x, int y, int width, int color) {
        draw(graphics, font, full, x, y, width, color, true);
    }

    private void draw(GuiGraphics graphics, Font font, Component full, int x, int y, int width, int color,
                      boolean centered) {
        var line = BoundedTextLayout.fit(full.getString(), width, font::width);
        Component displayed = line.shortened() ? Component.literal(line.text()) : full;
        int measured = font.width(displayed);
        // A styled server notice can be wider than its plain string (for example bold text).
        float scale = measured == 0 ? 1 : Math.min(line.scale(), (float) width / measured);
        float offset = centered ? (width - measured * scale) / 2 : 0;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + offset, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, displayed, 0, 0, color, false);
        } finally { graphics.pose().popPose(); }
        if (line.shortened() || scale < 1) { shortened.add(new Label(full, x, y, width, font.lineHeight)); }
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY,
                                 List<Component> details) {
        for (Label label : shortened) {
            if (mouseX >= left + label.x() && mouseX < left + label.x() + label.width()
                    && mouseY >= top + label.y() && mouseY < top + label.y() + label.height()) {
                List<Component> lines = new ArrayList<>();
                lines.add(label.full());
                lines.addAll(details);
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int left, int top, int mouseX, int mouseY) {
        return renderTooltip(graphics, font, left, top, mouseX, mouseY, List.of());
    }

    public static Button fitButton(Button button, Font font) {
        Component full = button.getMessage();
        var line = BoundedTextLayout.fit(full.getString(), Math.max(1, button.getWidth() - 6), font::width);
        if (line.shortened()) {
            button.setMessage(Component.literal(line.text()));
            button.setTooltip(Tooltip.create(full));
        }
        return button;
    }

    private record Label(Component full, int x, int y, int width, int height) { }
}
