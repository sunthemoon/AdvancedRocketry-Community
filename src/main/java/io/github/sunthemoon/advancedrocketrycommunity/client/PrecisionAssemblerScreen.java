package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Code-drawn panel; only the server menu supplies machine state and resources. */
public final class PrecisionAssemblerScreen extends AbstractContainerScreen<PrecisionAssemblerMenu> {
    private static final int PANEL_TOP = 0xFF253541;
    private static final int PANEL_BOTTOM = 0xFF111A22;
    private static final int PANEL_EDGE = 0xFF91AEB9;
    private static final int RECESS = 0xFF08141B;
    private static final int SLOT_EDGE = 0xFFC4D7DC;
    private static final int TEXT = 0xFFDFEBEC;
    private static final int MUTED = 0xFF9AB1B8;
    private static final int ALERT = 0xFFFF746C;
    private static final int ENERGY = 0xFFF0B15A;
    private static final int ACTIVE = 0xFF61D7D3;

    public PrecisionAssemblerScreen(PrecisionAssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 244;
        imageHeight = 225;
        titleLabelX = 9;
        titleLabelY = 7;
        inventoryLabelX = 18;
        inventoryLabelY = 133;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(151, 28, 14, 43, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), menu.energyCapacity()), mouseX, mouseY);
        } else if (isHovering(18, 71, 212, 11, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(
                    "tooltip.advancedrocketrycommunity.precision_assembler.progress",
                    menu.progress(), menu.totalProcessingTicks()), mouseX, mouseY);
        } else if (isHovering(8, 109, 228, 21, mouseX, mouseY)) {
            menu.diagnosticReason().ifPresent(reason -> graphics.renderTooltip(
                    font, diagnosticLocation(), mouseX, mouseY));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_EDGE);
        graphics.fill(x + 4, y + 18, x + imageWidth - 4, y + 131, 0xB508141B);
        graphics.renderOutline(x + 4, y + 18, imageWidth - 8, 113, 0xFF536F79);

        for (int slot = 0; slot < 5; slot++) {
            drawSlot(graphics, x + 17 + slot * 22, y + 35);
        }
        for (int slot = 0; slot < 2; slot++) {
            drawSlot(graphics, x + 186 + slot * 22, y + 35);
        }
        drawVerticalGauge(graphics, x + 151, y + 28, 14, 43,
                menu.energyStored(), menu.energyCapacity(), ENERGY);

        graphics.fill(x + 18, y + 71, x + 230, y + 82, SLOT_EDGE);
        graphics.fill(x + 19, y + 72, x + 229, y + 81, RECESS);
        int width = scale(menu.progress(), menu.totalProcessingTicks(), 208);
        if (width > 0) {
            graphics.fill(x + 20, y + 73, x + 20 + width, y + 80, ACTIVE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        graphics.drawString(font, Component.translatable(
                "screen.advancedrocketrycommunity.precision_assembler.inputs"), 18, 23, MUTED, false);
        graphics.drawString(font, Component.translatable(
                "screen.advancedrocketrycommunity.precision_assembler.outputs"), 181, 23, MUTED, false);
        graphics.drawString(font, "FE", 151, 17, MUTED, false);
        for (int slot = 0; slot < 5; slot++) {
            graphics.drawCenteredString(font, Integer.toString(slot), 26 + slot * 22, 56, MUTED);
        }
        for (int slot = 0; slot < 2; slot++) {
            graphics.drawCenteredString(font, Integer.toString(slot), 195 + slot * 22, 56, MUTED);
        }

        drawCenteredFit(graphics, Component.translatable(
                "screen.advancedrocketrycommunity.precision_assembler.formation",
                Component.translatable(statusKey("formation", menu.formationState().name()))),
                88, menu.formationState() == MultiblockFormationState.FORMED ? ACTIVE : ALERT);
        ProcessMachineState state = menu.processState();
        drawCenteredFit(graphics, Component.translatable(
                "screen.advancedrocketrycommunity.precision_assembler.process",
                Component.translatable(statusKey("process", state.name()))),
                99, processColor(state));

        menu.diagnosticReason().ifPresentOrElse(
                reason -> {
                    drawCenteredFit(graphics, Component.translatable(statusKey("diagnostic", reason.name())),
                            110, ALERT);
                    drawCenteredFit(graphics, diagnosticLocation(), 121, MUTED);
                },
                () -> {
                    ProcessFailureCode failure = menu.processFailure();
                    if (failure != ProcessFailureCode.NONE) {
                        drawCenteredFit(graphics,
                                Component.translatable(statusKey("failure", failure.name())),
                                110, ALERT);
                    }
                }
        );
    }

    private Component diagnosticLocation() {
        BlockPos world = menu.diagnosticWorldPosition().orElse(BlockPos.ZERO);
        PatternPosition local = menu.diagnosticLocalPosition().orElse(new PatternPosition(0, 0, 0));
        return Component.translatable(
                "screen.advancedrocketrycommunity.precision_assembler.diagnostic_location",
                world.getX(), world.getY(), world.getZ(), local.x(), local.y(), local.z());
    }

    private static int processColor(ProcessMachineState state) {
        return switch (state) {
            case RUNNING -> ACTIVE;
            case RECOVERY_REQUIRED, UNSUPPORTED_DATA, INVALID_RECIPE -> ALERT;
            default -> MUTED;
        };
    }

    private static String statusKey(String group, String name) {
        return "status.advancedrocketrycommunity.precision_assembler."
                + group + "." + name.toLowerCase(Locale.ROOT);
    }

    private void drawCenteredFit(GuiGraphics graphics, Component value, int y, int color) {
        int width = font.width(value);
        int maximum = imageWidth - 12;
        if (width <= maximum) {
            graphics.drawCenteredString(font, value, imageWidth / 2, y, color);
            return;
        }
        float factor = (float) maximum / width;
        graphics.pose().pushPose();
        graphics.pose().translate(imageWidth / 2.0F, y, 0.0F);
        graphics.pose().scale(factor, factor, 1.0F);
        graphics.drawCenteredString(font, value, 0, 0, color);
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
            int capacity,
            int color
    ) {
        graphics.fill(x, y, x + width, y + height, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, RECESS);
        int fillHeight = scale(value, capacity, height - 2);
        if (fillHeight > 0) {
            graphics.fill(x + 2, y + height - 1 - fillHeight,
                    x + width - 2, y + height - 1, color);
        }
    }

    private static int scale(int value, int maximum, int pixels) {
        if (maximum <= 0 || value <= 0) {
            return 0;
        }
        return Math.min(pixels, (int) ((long) value * pixels / maximum));
    }
}
