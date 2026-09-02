package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

/** Community-authored telemetry console with visible mission and discovery progress. */
public final class SatelliteTerminalScreen extends AbstractContainerScreen<SatelliteTerminalMenu> {
    private static final int PANEL_TOP = 0xFF18303A;
    private static final int PANEL_BOTTOM = 0xFF0B151B;
    private static final int EDGE = 0xFF5CA8B8;
    private static final int RECESS = 0xFF071014;
    private static final int TEXT = 0xFFDCECF0;
    private static final int MUTED = 0xFF87A4AC;
    private static final int CYAN = 0xFF52D7E8;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF6DDB9C;
    private static final int RED = 0xFFFF6B66;

    public SatelliteTerminalScreen(SatelliteTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 216;
        titleLabelX = 9;
        titleLabelY = 7;
        inventoryLabelX = 31;
        inventoryLabelY = 121;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(button(10, 83, 18, "<", SatelliteTerminalMenu.BUTTON_PREVIOUS));
        addRenderableWidget(button(196, 83, 18, ">", SatelliteTerminalMenu.BUTTON_NEXT));
        addRenderableWidget(button(31, 101, 47,
                "screen.advancedrocketrycommunity.satellite.assemble",
                SatelliteTerminalMenu.BUTTON_ASSEMBLE));
        addRenderableWidget(button(81, 101, 47,
                "screen.advancedrocketrycommunity.satellite.launch",
                SatelliteTerminalMenu.BUTTON_LAUNCH));
        addRenderableWidget(button(131, 101, 39,
                "screen.advancedrocketrycommunity.satellite.claim",
                SatelliteTerminalMenu.BUTTON_CLAIM));
        addRenderableWidget(button(173, 101, 41,
                "screen.advancedrocketrycommunity.satellite.cancel",
                SatelliteTerminalMenu.BUTTON_CANCEL));
    }

    private Button button(int x, int y, int width, String label, int buttonId) {
        Component text = label.length() == 1 ? Component.literal(label) : Component.translatable(label);
        return Button.builder(text, ignored -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            }
        }).bounds(leftPos + x, topPos + y, width, 16).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(181, 19, 32, 31, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "tooltip.advancedrocketrycommunity.energy",
                            menu.energyStored(),
                            menu.energyCapacity()
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 6, y + 17, x + 218, y + 79, 0xC9081116);
        graphics.renderOutline(x + 6, y + 17, 212, 62, 0xFF355D67);

        drawOrbit(graphics, x + 149, y + 34);
        drawEnergy(graphics, x + 181, y + 19);
        for (int slotX : new int[]{17, 43, 69, 107, 133, 187}) {
            drawSlot(graphics, x + slotX, y + 53);
        }
        graphics.fill(x + 96, y + 61, x + 105, y + 65, MUTED);
        graphics.fill(x + 158, y + 61, x + 178, y + 65, MUTED);
        graphics.fill(x + 176, y + 59, x + 179, y + 67, MUTED);

        int remaining = menu.remainingSeconds();
        int duration = menu.missionDurationSeconds();
        int progressWidth = duration <= 0
                ? 0
                : Math.max(2, 62 - Math.min(62, remaining * 62 / duration));
        graphics.fill(x + 82, y + 84, x + 144, y + 91, RECESS);
        if (menu.missionStatus().isPresent()) {
            graphics.fill(x + 82, y + 84, x + 82 + progressWidth, y + 91,
                    menu.missionStatus().orElseThrow() == MissionStatus.READY ? GOLD : CYAN);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.advancedrocketrycommunity.satellite.research",
                        menu.researchBalance()
                ),
                10,
                23,
                GOLD,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.advancedrocketrycommunity.satellite.power",
                        menu.energyStored()
                ),
                10,
                35,
                menu.energyStored() >= SatelliteTerminalBlockEntity.LAUNCH_POWER_THRESHOLD ? GREEN : RED,
                false
        );

        ResourceLocation target = menu.selectedTarget().orElse(null);
        Component targetName = target == null
                ? Component.translatable("screen.advancedrocketrycommunity.satellite.no_target")
                : Component.translatable("body." + target.getNamespace() + "." + target.getPath());
        graphics.drawCenteredString(font, targetName, imageWidth / 2, 84, TEXT);
        graphics.drawCenteredString(
                font,
                Component.translatable(menu.targetDiscovered()
                        ? "screen.advancedrocketrycommunity.satellite.discovered"
                        : "screen.advancedrocketrycommunity.satellite.unknown"),
                149,
                69,
                menu.targetDiscovered() ? GREEN : GOLD
        );

        Component mission = menu.missionStatus()
                .<Component>map(status -> Component.translatable(
                        "screen.advancedrocketrycommunity.satellite.mission."
                                + status.name().toLowerCase(Locale.ROOT),
                        Math.max(0, menu.remainingSeconds())
                ))
                .orElse(Component.translatable("screen.advancedrocketrycommunity.satellite.mission.none"));
        graphics.drawString(font, mission, 10, 72, MUTED, false);

        SatelliteOperationCode status = menu.status();
        graphics.drawString(
                font,
                Component.translatable(status.translationKey()),
                10,
                94,
                isError(status) || !menu.ownedByViewer() ? RED : MUTED,
                false
        );
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF7897A0);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
    }

    private static void drawOrbit(GuiGraphics graphics, int centerX, int centerY) {
        graphics.renderOutline(centerX - 23, centerY - 12, 46, 25, 0xFF325B68);
        graphics.fill(centerX - 5, centerY - 5, centerX + 6, centerY + 6, 0xFF3E9BC2);
        graphics.fill(centerX + 17, centerY - 2, centerX + 21, centerY + 2, GOLD);
        graphics.fill(centerX + 19, centerY - 5, centerX + 20, centerY + 5, 0xFFB7903D);
    }

    private void drawEnergy(GuiGraphics graphics, int x, int y) {
        graphics.renderOutline(x, y, 32, 31, 0xFF517580);
        graphics.fill(x + 3, y + 25, x + 29, y + 28, RECESS);
        int width = menu.energyCapacity() <= 0
                ? 0
                : Math.min(26, menu.energyStored() * 26 / menu.energyCapacity());
        graphics.fill(x + 3, y + 25, x + 3 + width, y + 28, CYAN);
        graphics.fill(x + 14, y + 5, x + 18, y + 14, GOLD);
        graphics.fill(x + 11, y + 11, x + 15, y + 20, GOLD);
    }

    private static boolean isError(SatelliteOperationCode code) {
        return switch (code) {
            case SUCCESS, IDEMPOTENT, PENDING_DISCOVERY, ALREADY_CLAIMED -> false;
            default -> true;
        };
    }
}
