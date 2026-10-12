package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Original drawn panel. The client projects server scalars and sends no machine actions. */
public final class ClassicControllerScreen extends AbstractContainerScreen<ClassicControllerMenu> {
    public ClassicControllerScreen(ClassicControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 220; imageHeight = 128;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF253C40);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFE2DFD0);
        graphics.fill(leftPos + 12, topPos + 53, leftPos + 208, topPos + 64, 0xFF667F7C);
        int progress = menu.duration() == 0 ? 0 : (int) (194L * Math.min(menu.progress(), menu.duration()) / menu.duration());
        graphics.fill(leftPos + 13, topPos + 54, leftPos + 13 + progress, topPos + 63, 0xFF42A794);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 12, 0x253C40, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.classic.formed", menu.formed()), 12, 32, 0x253C40, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.classic.progress", menu.progress(), menu.duration()), 12, 73, 0x253C40, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.classic.energy", menu.energy()), 12, 88, 0x253C40, false);
        graphics.drawString(font, Component.translatable(menu.reason().key()), 12, 105, 0x253C40, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partial);
    }
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.LATHE.get(), ClassicControllerScreen::new));
        }
        private Registration() { }
    }
}
