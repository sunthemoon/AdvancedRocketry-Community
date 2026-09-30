package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SolarLinks;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Community-authored receiver panel: four chip slots with their link state, the buffer and the output rate. */
public final class MicrowaveReceiverScreen extends AbstractContainerScreen<MicrowaveReceiverMenu> {
    private static final int PANEL_TOP = 0xFF2A2412;
    private static final int PANEL_BOTTOM = 0xFF15120A;
    private static final int EDGE = 0xFFE0B04A;
    private static final int RECESS = 0xFF0E0B05;
    private static final int TEXT = 0xFFF5ECD6;
    private static final int MUTED = 0xFFB0A280;
    private static final int GOLD = 0xFFFFC857;

    public MicrowaveReceiverScreen(MicrowaveReceiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 73;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int slot = 0; slot < MicrowaveReceiverBlockEntity.SLOT_COUNT; slot++) {
            if (isHovering(44 + slot * 24, 50, 16, 4, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable(statusKey(menu.status(slot))), mouseX, mouseY);
            }
        }
        if (isHovering(8, 18, 10, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), MicrowaveReceiverBlockEntity.ENERGY_CAPACITY), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 8, y + 18, x + 18, y + 68, RECESS);
        int height = (int) Math.min(50L, (long) menu.energyStored() * 50L / MicrowaveReceiverBlockEntity.ENERGY_CAPACITY);
        graphics.fill(x + 8, y + 68 - height, x + 18, y + 68, GOLD);
        for (int slot = 0; slot < MicrowaveReceiverBlockEntity.SLOT_COUNT; slot++) {
            int slotX = x + 43 + slot * 24;
            graphics.fill(slotX, y + 29, slotX + 18, y + 47, RECESS);
            graphics.renderOutline(slotX, y + 29, 18, 18, 0xFF6B5A2E);
            graphics.fill(slotX + 1, y + 50, slotX + 17, y + 54, statusColor(menu.status(slot)));
        }
        for (int row = 0; row < 4; row++) {
            int rowY = y + (row < 3 ? 83 + row * 18 : 141);
            for (int column = 0; column < 9; column++) {
                graphics.fill(x + 7 + column * 18, rowY, x + 25 + column * 18, rowY + 18, RECESS);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.microwave_receiver.output",
                String.format(Locale.ROOT, "%,d", menu.output())), 44, 60, GOLD, false);
    }

    private static int statusColor(SolarLinks.LinkStatus status) {
        return switch (status) {
            case EMPTY -> 0xFF3A3222;
            case HOLDING -> 0xFF6DDB9C;
            case ELSEWHERE -> 0xFFFFC857;
            case UNAVAILABLE -> 0xFFFF6B66;
        };
    }

    private static String statusKey(SolarLinks.LinkStatus status) {
        return "screen.advancedrocketrycommunity.microwave_receiver.link." + status.name().toLowerCase(Locale.ROOT);
    }
}
