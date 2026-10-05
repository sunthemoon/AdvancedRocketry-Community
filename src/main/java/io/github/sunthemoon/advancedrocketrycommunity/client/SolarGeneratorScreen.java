package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original blue collector panel, with no imported GUI texture and no client-side energy decision. */
public final class SolarGeneratorScreen extends AbstractContainerScreen<SolarGeneratorMenu> {
    private static final String PREFIX = "screen.advancedrocketrycommunity.solar.";
    public SolarGeneratorScreen(SolarGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 142;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        var view = menu.view();
        if (isHovering(10, 24, 18, 102, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    view.energy(), SolarGeneration.CAPACITY), mouseX, mouseY);
        }
        if (isHovering(38, 108, 170, 20, mouseX, mouseY)) {
            graphics.renderTooltip(font, reason(view), mouseX, mouseY);
        }
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF263C50, 0xFF101C2B);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF8BAFC7);
        graphics.fill(x + 10, y + 24, x + 28, y + 126, 0xFF0A121C);
        int filled = menu.view().energy() * 100 / SolarGeneration.CAPACITY;
        graphics.fill(x + 11, y + 125 - filled, x + 27, y + 125, 0xFFF2C46E);
        graphics.renderOutline(x + 10, y + 24, 18, 102, 0xFF547C9A);
        graphics.fill(x + 38, y + 106, x + 210, y + 128, 0xFF142536);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var view = menu.view();
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE4EDF4, false);
        graphics.drawString(font, Component.literal(view.energy() + " FE"), 38, 25, 0xFFF2C46E, false);
        graphics.drawString(font, Component.translatable(PREFIX + "rate", view.credit()), 38, 40, 0xFFF2C46E, false);
        graphics.drawString(font, Component.translatable(PREFIX + "sky." + (view.sky() ? "open" : "blocked")), 38, 56, 0xFFACC5D7, false);
        graphics.drawString(font, Component.translatable(PREFIX + "day." + view.day()), 38, 70, 0xFFACC5D7, false);
        graphics.drawString(font, Component.translatable(PREFIX + "context." + view.context()), 38, 84, 0xFFACC5D7, false);
        graphics.drawString(font, Component.translatable(PREFIX + "weather", view.weather()), 38, 97, 0xFFACC5D7, false);
        Component status = reason(view);
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 165), 42, 113,
                view.reason() == SolarGeneration.Reason.GENERATING ? 0xFFB8DEC6 : 0xFFACC5D7, false);
    }
    private static Component reason(SolarGeneratorMenu.View view) {
        return Component.translatable(PREFIX + "reason." + view.reason().name().toLowerCase(Locale.ROOT));
    }
}
