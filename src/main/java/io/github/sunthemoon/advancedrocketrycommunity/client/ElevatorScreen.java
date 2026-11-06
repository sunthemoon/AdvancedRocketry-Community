package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored elevator panel (ADR-059 section 9): energy, the 4 input and 9 receive slots, the anchor selection
 * and bind (terminal only), unbind, ride, launch, auto and redstone buttons, and the status with its stable code from
 * the device view (hover for the pair state, the far end, settings and counts), so no state is shown by colour alone.
 */
public final class ElevatorScreen extends AbstractContainerScreen<ElevatorMenu> {
    private final ScreenText text = new ScreenText();
    private static final int PANEL_TOP = 0xFF1B1F2A;
    private static final int PANEL_BOTTOM = 0xFF0D1016;
    private static final int EDGE = 0xFF7FD3FF;
    private static final int RECESS = 0xFF07090D;
    private static final int TEXT = 0xFFE8F1FA;
    private static final int MUTED = 0xFF93A4B8;
    private static final int CHARGE = 0xFF5FC8FF;
    private static final int WARNING = 0xFFFFC857;

    public ElevatorScreen(ElevatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.terminal()) {
            addRenderableWidget(button(ElevatorMenu.BUTTON_PREVIOUS, "screen.advancedrocketrycommunity.elevator.previous",
                    8, 76, 16));
            addRenderableWidget(button(ElevatorMenu.BUTTON_NEXT, "screen.advancedrocketrycommunity.elevator.next",
                    26, 76, 16));
            addRenderableWidget(button(ElevatorMenu.BUTTON_BIND, "screen.advancedrocketrycommunity.elevator.bind",
                    44, 76, 38));
        }
        addRenderableWidget(button(ElevatorMenu.BUTTON_UNBIND, "screen.advancedrocketrycommunity.elevator.unbind",
                84, 76, 42));
        addRenderableWidget(button(ElevatorMenu.BUTTON_RIDE, "screen.advancedrocketrycommunity.elevator.ride",
                128, 76, 40));
        addRenderableWidget(button(ElevatorMenu.BUTTON_LAUNCH, "screen.advancedrocketrycommunity.elevator.launch",
                8, 92, 52));
        addRenderableWidget(button(ElevatorMenu.BUTTON_AUTO, "screen.advancedrocketrycommunity.elevator.auto",
                62, 92, 52));
        addRenderableWidget(button(ElevatorMenu.BUTTON_REDSTONE, "screen.advancedrocketrycommunity.elevator.redstone",
                116, 92, 52));
    }

    private Button button(int id, String key, int x, int y, int width) {
        return ScreenText.fitButton(Button.builder(Component.translatable(key),
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                    }
                }).bounds(leftPos + x, topPos + y, width, 14).build(), font);
    }

    @Override
    public void removed() {
        super.removed();
        EndgameDeviceViewCache.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        text.clear();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(CargoScreenLayout.ENERGY_X, CargoScreenLayout.ENERGY_Y,
                CargoScreenLayout.ENERGY_WIDTH, CargoScreenLayout.ENERGY_HEIGHT, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), ElevatorEndpointBlockEntity.ENERGY_CAPACITY), mouseX, mouseY);
        }
        text.renderTooltip(graphics, font, leftPos, topPos, mouseX, mouseY);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isPresent() && isHovering(8, 108, 160, 20, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            for (EndgameDeviceView.Line line : view.get().lines()) {
                Component value = line.translated() ? Component.translatable(line.value())
                        : Component.literal(line.value());
                lines.add(Component.translatable(line.label()).append(": ").append(value));
            }
            if (!lines.isEmpty()) {
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + CargoScreenLayout.ENERGY_X, y + CargoScreenLayout.ENERGY_Y,
                x + CargoScreenLayout.ENERGY_X + CargoScreenLayout.ENERGY_WIDTH,
                y + CargoScreenLayout.ENERGY_BOTTOM, RECESS);
        int height = CargoScreenLayout.fill(menu.energyStored(), ElevatorEndpointBlockEntity.ENERGY_CAPACITY);
        graphics.fill(x + CargoScreenLayout.ENERGY_X, y + CargoScreenLayout.ENERGY_BOTTOM - height,
                x + CargoScreenLayout.ENERGY_X + CargoScreenLayout.ENERGY_WIDTH,
                y + CargoScreenLayout.ENERGY_BOTTOM, CHARGE);
        ContainerSlotBackground.draw(graphics, menu.slots, x, y, 0xFF52738B, RECESS);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        text.draw(graphics, font, title, titleLabelX, titleLabelY, 160, TEXT);
        text.draw(graphics, font, Component.translatable("screen.advancedrocketrycommunity.elevator.received"), 8, 44,
                140, MUTED);
        text.draw(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 160, MUTED);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        text.draw(graphics, font, statusLine(status), 8, 110, 160, status == EndgameCode.OK ? CHARGE : WARNING);
        if (view.get().lastStop() != EndgameCode.OK && view.get().lastStop() != status) {
            text.draw(graphics, font, Component.translatable("screen.advancedrocketrycommunity.elevator.last",
                    statusLine(view.get().lastStop())), 8, 120, 160, MUTED);
        }
    }

    /** The translated status with its stable code, never colour alone (ADR-054 section 4). */
    private static Component statusLine(EndgameCode code) {
        return Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, Component.translatable(code.translationKey()),
                code.name());
    }
}
