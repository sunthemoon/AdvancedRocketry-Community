package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Original drawn research panel. Client buttons carry only a bounded vanilla container intent. */
public final class ResearchControllerScreen extends AbstractContainerScreen<ResearchControllerMenu> {
    private static final String PREFIX = "screen.advancedrocketrycommunity.research.";
    private final List<Button> buttons = new ArrayList<>();
    public ResearchControllerScreen(ResearchControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 280; imageHeight = 224;
    }
    @Override protected void init() {
        super.init(); buttons.clear(); String[] keys = {"start", "cancel", "previous", "next", "claim", "rebind"};
        for (int i = 0; i < keys.length; i++) {
            int action = i; var button = Button.builder(Component.translatable(PREFIX + keys[i]), ignored -> {
                if (minecraft != null && minecraft.gameMode != null) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
            }).bounds(leftPos + 10 + i % 3 * 88, topPos + 176 + i / 3 * 22, 84, 20).build();
            buttons.add(addRenderableWidget(button));
        }
    }
    @Override protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < buttons.size(); i++) { buttons.get(i).active = menu.ownerRelation() == 1
                && (i != 0 && i != 1 || !menu.processor()) && (i != 4 || menu.processor()); }
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF314A50);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFE9E3D3);
        graphics.fill(leftPos + 10, topPos + 44, leftPos + 270, topPos + 54, 0xFF819492);
        int progress = menu.duration() == 0 ? 0 : (int) (258L * Math.min(menu.progress(), menu.duration()) / menu.duration());
        graphics.fill(leftPos + 11, topPos + 45, leftPos + 11 + progress, topPos + 53, 0xFF398A7C);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int ink = 0x243C42; graphics.drawString(font, title, 10, 10, ink, false);
        graphics.drawString(font, Component.translatable(PREFIX + "progress", menu.progress(), menu.duration()), 10, 29, ink, false);
        graphics.drawString(font, Component.translatable(PREFIX + "energy", menu.energy(), menu.requiredEnergy()), 10, 62, ink, false);
        graphics.drawString(font, Component.translatable(PREFIX + "selection", menu.selectionCount() == 0 ? 0 : menu.selectionIndex() + 1, menu.selectionCount()), 10, 78, ink, false);
        graphics.drawString(font, Component.translatable(PREFIX + "state", label("phase", menu.phaseCode()), label("analysis", menu.analysisCode())), 10, 94, ink, false);
        graphics.drawString(font, Component.translatable(PREFIX + "owner", label("owner", menu.ownerRelation())), 10, 110, ink, false);
        if (menu.diagnosticRole() != 0) { graphics.drawString(font, Component.translatable(PREFIX + "diagnostic", menu.diagnostic(0), menu.diagnostic(1), menu.diagnostic(2), label("role", menu.diagnosticRole())), 10, 126, ink, false); }
        graphics.drawWordWrap(font, Component.translatable(menu.status().key()), 10, 144, 260, ink);
    }
    private static Component label(String group, int code) { return Component.translatable(PREFIX + group + "." + code); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { renderBackground(graphics); super.render(graphics, mouseX, mouseY, partial); }
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ResearchScreenRegistration {
        @SubscribeEvent public static void setupResearchScreen(FMLClientSetupEvent event) { event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.GROUND_RESEARCH.get(), ResearchControllerScreen::new)); }
        private ResearchScreenRegistration() { }
    }
}
