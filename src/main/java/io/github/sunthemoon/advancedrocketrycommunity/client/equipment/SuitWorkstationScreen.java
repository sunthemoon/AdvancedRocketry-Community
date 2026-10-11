package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Physical-client presentation only. Fixed intents never carry item data. */
public final class SuitWorkstationScreen extends AbstractContainerScreen<SuitWorkstationMenu> {
    public SuitWorkstationScreen(SuitWorkstationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 190; inventoryLabelY = 96;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.advancedrocketrycommunity.suit.install"),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
                .bounds(leftPos + 56, topPos + 28, 64, 20).build());
        for (int i = 0; i < 3; i++) {
            final int position = i;
            addRenderableWidget(Button.builder(Component.literal("-"), button -> minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId, hasShiftDown() ? position + 4 : position + 1))
                    .bounds(leftPos + 44 + i * 40, topPos + 75, 22, 16).build());
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.advancedrocketrycommunity.suit.remove_hint"), 8, 88, 0xff243440, false);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff243440);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffd2d8cf);
        for (var slot : menu.slots) { g.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                leftPos + slot.x + 17, topPos + slot.y + 17, 0xff667c7b); }
        var modules = ArmorEquipment.modules(menu.getSlot(0).getItem());
        for (int i = 0; i < 3; i++) { g.fill(leftPos + 44 + i * 40, topPos + 55, leftPos + 62 + i * 40, topPos + 73, 0xff667c7b); }
        if (modules != null) { for (var module : modules) {
            g.renderItem(ItemStack.of(module.stack()), leftPos + 45 + module.position() * 40, topPos + 56);
        } }
    }
}
