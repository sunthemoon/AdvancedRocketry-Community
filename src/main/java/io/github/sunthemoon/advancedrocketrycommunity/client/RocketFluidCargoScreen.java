package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Original panel drawn in code, with no GUI texture. Every value comes from the menu's scalar data; the numeric Fluid
 * ID is resolved for display only, an unknown one gets a generic label, and nothing is sent back.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketFluidCargoScreen extends AbstractContainerScreen<RocketFluidCargoMenu> {
    public static final String PREFIX = "screen.advancedrocketrycommunity.fluid_fuel_loader.";
    private static final int GAUGE_X = 12;
    private static final int GAUGE_Y = 22;
    private static final int GAUGE_WIDTH = 16;
    private static final int GAUGE_HEIGHT = 52;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.ROCKET_FLUID_CARGO.get(), RocketFluidCargoScreen::new));
    }

    public RocketFluidCargoScreen(RocketFluidCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 108;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF263038, 0xFF141B22);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF8FA3B4);
        RocketFluidCargoMenu.View view = menu.view();
        // The tank gauge: a dark well, a fill bar in proportion and four tick marks.
        int left = x + GAUGE_X;
        int top = y + GAUGE_Y;
        graphics.fill(left - 1, top - 1, left + GAUGE_WIDTH + 1, top + GAUGE_HEIGHT + 1, 0xFF0A0F14);
        int capacity = Math.max(1, view.capacity());
        int filled = (int) ((long) GAUGE_HEIGHT * Math.min(view.amount(), capacity) / capacity);
        if (filled > 0) {
            graphics.fill(left, top + GAUGE_HEIGHT - filled, left + GAUGE_WIDTH, top + GAUGE_HEIGHT, 0xFF4FA8D8);
        }
        for (int mark = 1; mark < 4; mark++) {
            int markY = top + GAUGE_HEIGHT * mark / 4;
            graphics.fill(left + GAUGE_WIDTH - 4, markY, left + GAUGE_WIDTH, markY + 1, 0xFFB8C7D3);
        }
        // The binding lamp.
        graphics.fill(x + 36, y + 49, x + 42, y + 55, view.bound() ? 0xFF7CCF6A : 0xFF5A6570);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE4EDF4, false);
        RocketFluidCargoMenu.View view = menu.view();
        int width = imageWidth - 44;
        drawLine(graphics, contents(view), 36, 24, width, 0xFFD5E2EC);
        drawLine(graphics, Component.translatable(PREFIX + (view.bound() ? "bound" : "unbound")), 46, 48, width - 10,
                0xFFACC5D7);
        Component status = view.status() == null ? Component.translatable(PREFIX + "status_unknown")
                : Component.translatable(view.status().translationKey());
        if (view.cursor() != null) {
            drawLine(graphics, Component.literal(view.cursor().getX() + ", " + view.cursor().getY() + ", " + view.cursor().getZ()),
                    36, 88, width, 0xFFACC5D7);
        }
        // Status text wraps onto at most two lines inside the panel.
        var lines = font.split(status, width);
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), 36, 62 + index * 10, 0xFFF2C46E, false);
        }
    }

    private Component contents(RocketFluidCargoMenu.View view) {
        if (view.fluidId() == RocketFluidCargoMenu.NO_FLUID) {
            return Component.translatable(PREFIX + "empty", view.capacity());
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(view.fluidId());
        Component name = fluid == null || fluid == Fluids.EMPTY ? Component.translatable(PREFIX + "unknown_fluid")
                : fluid.getFluidType().getDescription();
        return Component.translatable(PREFIX + "contents", name, view.amount(), view.capacity());
    }

    private void drawLine(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, colour, false);
    }
}
