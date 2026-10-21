package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Original panel drawn in code, with no GUI texture: the device's four slots, the player inventory, and the status,
 * binding and cursor from the menu's scalar data. Slot clicks are vanilla's; nothing else is sent back.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketItemCargoScreen extends AbstractContainerScreen<RocketItemCargoMenu> {
    public static final String PREFIX = "screen.advancedrocketrycommunity.rocket_item_cargo.";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.ROCKET_ITEM_CARGO.get(), RocketItemCargoScreen::new));
    }

    public RocketItemCargoScreen(RocketItemCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
        inventoryLabelY = RocketItemCargoMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF2A3036, 0xFF161B20);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF9AA8B4);
        // A recessed tray behind the device slots and a thin rule above the inventory.
        int trayLeft = x + RocketItemCargoMenu.SLOT_X - 4;
        int trayTop = y + RocketItemCargoMenu.SLOT_Y - 4;
        graphics.fill(trayLeft, trayTop, trayLeft + RocketItemCargoMenu.DEVICE_SLOTS * 18 + 6, trayTop + 24, 0xFF0D1115);
        graphics.fill(x + 7, y + RocketItemCargoMenu.INVENTORY_Y - 14, x + imageWidth - 7,
                y + RocketItemCargoMenu.INVENTORY_Y - 13, 0xFF59656F);
        for (Slot slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF3C454D);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF1E252B);
        }
        // The binding lamp.
        graphics.fill(x + 10, y + 52, x + 16, y + 58, menu.view().bound() ? 0xFF7CCF6A : 0xFF5A6570);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE4EDF4, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB8C7D3, false);
        RocketItemCargoMenu.View view = menu.view();
        int width = imageWidth - 30;
        drawLine(graphics, Component.translatable(PREFIX + (view.bound() ? "bound" : "unbound")), 20, 51, width, 0xFFACC5D7);
        Component status = view.status() == null ? Component.translatable(PREFIX + "status_unknown")
                : Component.translatable(view.status().translationKey());
        // Status text wraps onto at most two lines inside the panel.
        var lines = font.split(status, imageWidth - 20);
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), 10, 63 + index * 10, 0xFFF2C46E, false);
        }
        if (view.cursor() != null) {
            drawLine(graphics, Component.translatable(PREFIX + "cursor", view.cursor().getX(), view.cursor().getY(),
                    view.cursor().getZ(), view.cursorSlot()), 10, 41, imageWidth - 20, 0xFFACC5D7);
        }
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, colour, false);
    }
}
