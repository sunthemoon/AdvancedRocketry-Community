package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Original drawn bay panel with no imported GUI texture; the phase and reason come only from the server. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SatelliteBayScreen extends AbstractContainerScreen<SatelliteBayMenu> {
    private static final String PREFIX = "screen.advancedrocketrycommunity.rocket_satellite_bay.";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.ROCKET_SATELLITE_BAY.get(), SatelliteBayScreen::new));
    }

    public SatelliteBayScreen(SatelliteBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
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
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF2A3440, 0xFF161D26);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF9AA9B8);
        // The bay hatch around its one slot.
        graphics.fill(x + 70, y + 25, x + 106, y + 61, 0xFF0E141B);
        graphics.renderOutline(x + 70, y + 25, 36, 36, 0xFFE0A64A);
        graphics.fill(x + 79, y + 34, x + 97, y + 52, 0xFF3A4654);
        for (int column = 0; column < 9; column++) {
            for (int row = 0; row < 4; row++) {
                int slotY = row < 3 ? 83 + row * 18 : 141;
                graphics.fill(x + 7 + column * 18, y + slotY, x + 25 + column * 18, y + slotY + 18, 0xFF3A4654);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE4EDF4, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFACC5D7, false);
        SatelliteBayMenu.View view = menu.view();
        Component phase = Component.translatable(PREFIX + "phase", Component.translatable(view.phase().translationKey()));
        graphics.drawString(font, font.plainSubstrByWidth(phase.getString(), 160), 8, 63, 0xFFACC5D7, false);
        Component reason = Component.translatable(view.reason().translationKey());
        graphics.drawString(font, font.plainSubstrByWidth(reason.getString(), 160), 8, 18, 0xFFF2C46E, false);
    }
}
