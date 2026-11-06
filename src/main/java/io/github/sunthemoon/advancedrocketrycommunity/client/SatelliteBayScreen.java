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
    private final ScreenText text = new ScreenText();
    private static final String PREFIX = "screen.advancedrocketrycommunity.rocket_satellite_bay.";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.ROCKET_SATELLITE_BAY.get(), SatelliteBayScreen::new));
    }

    public SatelliteBayScreen(SatelliteBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = SatelliteBayMenu.PANEL_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        text.clear();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        text.renderTooltip(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF2A3440, 0xFF161D26);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF9AA9B8);
        // The bay hatch around its one slot.
        graphics.fill(x + 70, y + SatelliteScreenLayout.BAY_HATCH_Y,
                x + 106, y + SatelliteScreenLayout.BAY_HATCH_BOTTOM, 0xFF0E141B);
        graphics.renderOutline(x + 70, y + SatelliteScreenLayout.BAY_HATCH_Y, 36,
                SatelliteScreenLayout.BAY_HATCH_BOTTOM - SatelliteScreenLayout.BAY_HATCH_Y, 0xFFE0A64A);
        graphics.fill(x + 79, y + 34, x + 97, y + 52, 0xFF3A4654);
        ContainerSlotBackground.draw(graphics, menu.slots, x, y, 0xFF7B8E9F, 0xFF1B2633);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        text.draw(graphics, font, title, titleLabelX, titleLabelY, 160, 0xFFE4EDF4);
        text.draw(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 160, 0xFFACC5D7);
        SatelliteBayMenu.View view = menu.view();
        Component phase = Component.translatable(PREFIX + "phase", Component.translatable(view.phase().translationKey()));
        text.draw(graphics, font, phase, 8, 63, 160, 0xFFACC5D7);
        Component reason = Component.translatable(view.reason().translationKey());
        text.draw(graphics, font, reason, 8, SatelliteScreenLayout.BAY_NOTICE_Y, 160, 0xFFF2C46E);
    }
}
