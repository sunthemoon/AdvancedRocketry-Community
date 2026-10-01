package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored black-hole generator panel (ADR-057 section 6): fuel slots, the energy bar, output rate,
 * remaining burn and the status with its stable code, never colour alone. It has no buttons.
 */
public final class BlackHoleGeneratorScreen extends AbstractContainerScreen<BlackHoleGeneratorMenu> {
    private static final int PANEL_TOP = 0xFF16121C;
    private static final int PANEL_BOTTOM = 0xFF07060A;
    private static final int EDGE = 0xFFFF9D5C;
    private static final int RECESS = 0xFF050407;
    private static final int TEXT = 0xFFF5EBE0;
    private static final int MUTED = 0xFFB0A090;
    private static final int GLOW = 0xFFFFB066;
    private static final int WARNING = 0xFFFFC857;

    public BlackHoleGeneratorScreen(BlackHoleGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 93;
    }

    @Override
    public void removed() {
        super.removed();
        EndgameDeviceViewCache.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 18, 10, 54, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), BlackHoleGeneratorBlockEntity.ENERGY_CAPACITY), mouseX, mouseY);
        }
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isPresent() && isHovering(8, 76, 160, 12, mouseX, mouseY) && !view.get().lines().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (EndgameDeviceView.Line line : view.get().lines()) {
                Component value = line.translated() ? Component.translatable(line.value())
                        : Component.literal(line.value());
                lines.add(Component.translatable(line.label()).append(": ").append(value));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 8, y + 18, x + 18, y + 72, RECESS);
        int height = (int) Math.min(54L, (long) menu.energyStored() * 54L
                / BlackHoleGeneratorBlockEntity.ENERGY_CAPACITY);
        graphics.fill(x + 8, y + 72 - height, x + 18, y + 72, GLOW);
        for (int slot = 0; slot < BlackHoleGeneratorBlockEntity.FUEL_SLOTS; slot++) {
            int slotX = x + 61 + (slot % 3) * 18;
            int slotY = y + 17 + (slot / 3) * 18;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, RECESS);
            graphics.renderOutline(slotX, slotY, 18, 18, 0xFF5A3A2A);
        }
        for (int row = 0; row < 4; row++) {
            int rowY = y + (row < 3 ? 103 + row * 18 : 161);
            for (int column = 0; column < 9; column++) {
                graphics.fill(x + 7 + column * 18, rowY, x + 25 + column * 18, rowY + 18, RECESS);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        graphics.drawString(font, Component.translatable("advancedrocketrycommunity.endgame.view.rate")
                .append(": " + String.format(Locale.ROOT, "%,d FE/t", menu.rate())), 120, 22, MUTED, false);
        graphics.drawString(font, Component.translatable("advancedrocketrycommunity.endgame.view.remaining")
                .append(": " + menu.remaining()), 120, 34, MUTED, false);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        graphics.drawString(font, Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY,
                Component.translatable(status.translationKey()), status.name()), 8, 78,
                status == EndgameCode.GENERATING ? GLOW : WARNING, false);
    }
}
