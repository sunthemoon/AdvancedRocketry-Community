package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original code-drawn industrial panel; it renders only server-synchronized menu state. */
public final class RollingMachineScreen extends AbstractContainerScreen<RollingMachineMenu> {
    private static final int PANEL_TOP = 0xFF26343A;
    private static final int PANEL_BOTTOM = 0xFF131B1E;
    private static final int PANEL_EDGE = 0xFF8AA0A8;
    private static final int RECESS = 0xFF091014;
    private static final int SLOT_EDGE = 0xFFAEBCC0;
    private static final int STEEL = 0xFFCCD7DA;
    private static final int MUTED = 0xFF91A4AA;
    private static final int ALERT = 0xFFFF6B5F;
    private static final int ENERGY = 0xFFF0A342;
    private static final int WATER = 0xFF3A9BD9;
    private static final int PROGRESS = 0xFF62D7C9;

    public RollingMachineScreen(RollingMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 196;
        imageHeight = 212;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = 17;
        inventoryLabelY = 117;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(58, 27, 12, 43, mouseX, mouseY)) {
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
        } else if (isHovering(76, 27, 12, 43, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "tooltip.advancedrocketrycommunity.water",
                            menu.waterAmount(),
                            menu.waterCapacity()
                    ),
                    mouseX,
                    mouseY
            );
        } else if (isHovering(96, 43, 47, 12, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "tooltip.advancedrocketrycommunity.rolling_machine.progress",
                            menu.progress(),
                            menu.totalProcessingTicks()
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
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_EDGE);
        graphics.fill(x + 4, y + 17, x + imageWidth - 4, y + 113, 0xB5091014);
        graphics.renderOutline(x + 4, y + 17, imageWidth - 8, 96, 0xFF53676E);

        drawSlot(graphics, x + 26, y + 38);
        drawSlot(graphics, x + 150, y + 38);
        drawVerticalGauge(
                graphics,
                x + 58,
                y + 27,
                12,
                43,
                menu.energyStored(),
                menu.energyCapacity(),
                ENERGY
        );
        drawVerticalGauge(
                graphics,
                x + 76,
                y + 27,
                12,
                43,
                menu.waterAmount(),
                menu.waterCapacity(),
                WATER
        );

        graphics.fill(x + 96, y + 43, x + 143, y + 55, SLOT_EDGE);
        graphics.fill(x + 97, y + 44, x + 142, y + 54, RECESS);
        int width = scale(menu.progress(), menu.totalProcessingTicks(), 43);
        if (width > 0) {
            graphics.fill(x + 98, y + 45, x + 98 + width, y + 53, PROGRESS);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, STEEL, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);

        Component formation = Component.translatable(
                "screen.advancedrocketrycommunity.rolling_machine.formation",
                Component.translatable(enumKey("formation", menu.formationState().name()))
        );
        drawCenteredFit(graphics, formation, 74, formationColor());

        ProcessMachineState state = menu.processState();
        ProcessFailureCode failure = menu.processFailure();
        Component process = Component.translatable(
                "screen.advancedrocketrycommunity.rolling_machine.process",
                Component.translatable(enumKey("process", state.name()))
        );
        drawCenteredFit(graphics, process, 84, processColor(state));

        menu.diagnosticReason().ifPresentOrElse(
                reason -> drawDiagnostic(graphics, reason),
                () -> {
                    if (failure != ProcessFailureCode.NONE) {
                        drawCenteredFit(
                                graphics,
                                menu.recipeReason() == RecipeMenuReason.NONE
                                        ? Component.translatable(enumKey("failure", failure.name()))
                                        : Component.translatable(menu.recipeReason().translationKey()),
                                94,
                                ALERT
                        );
                    }
                }
        );
    }

    private void drawDiagnostic(GuiGraphics graphics, PatternDiagnosticReason reason) {
        BlockPos world = menu.diagnosticWorldPosition().orElse(BlockPos.ZERO);
        PatternPosition local = menu.diagnosticLocalPosition().orElse(new PatternPosition(0, 0, 0));
        drawCenteredFit(
                graphics,
                Component.translatable(enumKey("diagnostic", reason.name())),
                94,
                ALERT
        );
        Component location = Component.translatable(
                "screen.advancedrocketrycommunity.rolling_machine.diagnostic_location",
                world.getX(),
                world.getY(),
                world.getZ(),
                local.x(),
                local.y(),
                local.z()
        );
        drawCenteredFit(graphics, location, 104, MUTED);
    }

    private int formationColor() {
        return menu.formationState() == MultiblockFormationState.FORMED
                ? PROGRESS
                : ALERT;
    }

    private static int processColor(ProcessMachineState state) {
        return switch (state) {
            case RUNNING -> PROGRESS;
            case RECOVERY_REQUIRED, UNSUPPORTED_DATA, INVALID_RECIPE -> ALERT;
            default -> MUTED;
        };
    }

    private static String enumKey(String group, String name) {
        return "status.advancedrocketrycommunity.rolling_machine."
                + group
                + "."
                + name.toLowerCase(Locale.ROOT);
    }

    private void drawCenteredFit(GuiGraphics graphics, Component text, int y, int color) {
        int textWidth = font.width(text);
        int maximumWidth = imageWidth - 12;
        if (textWidth <= maximumWidth) {
            graphics.drawCenteredString(font, text, imageWidth / 2, y, color);
            return;
        }
        float scale = Math.max(0.65F, (float) maximumWidth / textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(imageWidth / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
    }

    private static void drawVerticalGauge(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int value,
            int maximum,
            int color
    ) {
        graphics.fill(x, y, x + width, y + height, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, RECESS);
        int fillHeight = scale(value, maximum, height - 2);
        if (fillHeight > 0) {
            graphics.fill(
                    x + 2,
                    y + height - 1 - fillHeight,
                    x + width - 2,
                    y + height - 1,
                    color
            );
        }
    }

    private static int scale(int value, int maximum, int pixels) {
        if (maximum <= 0 || value <= 0) {
            return 0;
        }
        return Math.min(pixels, (int) ((long) value * pixels / maximum));
    }
}
