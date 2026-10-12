package io.github.sunthemoon.advancedrocketrycommunity.client.station;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlsNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored panel for the four ADR-065 station controllers. Its fixed buttons send
 * {@code classic_controls} intents; the lines and the last action's stable code come from the server's view of
 * this container, never colour alone. A button whose intent cannot be formed from the current view is inactive.
 * Warp body IDs occupy one abbreviated row each, with full hover text. Balance, cost, countdown and status own
 * fixed rows; Confirm requires the complete cost to have been drawn for the current server view (W07G).
 */
public final class StationControllerScreen extends AbstractContainerScreen<StationControllerMenu> {
    private static final int PANEL_TOP = 0xFF1B2430;
    private static final int PANEL_BOTTOM = 0xFF0D1218;
    private static final int EDGE = 0xFF8FB8D8;
    private static final int TEXT = 0xFFE8F0F8;
    private static final int MUTED = 0xFFA6B6C6;
    private static final int GOOD = 0xFF8FD8A8;
    private static final int WARNING = 0xFFFFC857;

    private final List<Control> controls = new ArrayList<>();
    private final List<BodyTooltip> bodyTooltips = new ArrayList<>();
    private Optional<RenderedQuote> renderedQuote = Optional.empty();

    public StationControllerScreen(StationControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = StationControllerMenu.PANEL_WIDTH;
        imageHeight = StationControllerMenu.PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        controls.clear();
        renderedQuote = Optional.empty();
        bodyTooltips.clear();
        for (StationControllerMenu.Button spec : menu.buttons()) {
            Button widget = addRenderableWidget(Button.builder(Component.translatable(spec.key()),
                    ignored -> intent(spec.id(), view()).ifPresent(ClassicControlsNetwork::sendIntent))
                    .bounds(leftPos + spec.x(), topPos + spec.y(), spec.width(), 16).build());
            controls.add(new Control(spec, widget));
        }
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    private void updateControls() {
        Optional<ClassicControlViewPacket> view = view();
        controls.forEach(control -> control.widget().active = intent(control.spec().id(), view).isPresent());
    }

    /** Recheck visibility on click as well as on ticks, including a new view received since the last draw. */
    private Optional<ClassicControlIntentPacket> intent(int action, Optional<ClassicControlViewPacket> view) {
        if (menu.kind() == StationControllerKind.WARP && action == WarpControllerMenu.BUTTON_CONFIRM) {
            view = view.filter(shown -> renderedQuote.filter(drawn -> drawn.view().equals(shown)
                    && drawn.layout().confirmEnabled(shown.token())).isPresent());
        }
        return menu.intent(action, view);
    }

    private Optional<ClassicControlViewPacket> view() {
        return ClassicControlViewCache.view(menu.containerId, menu.classicControlKind());
    }

    @Override
    public void removed() {
        super.removed();
        ClassicControlViewCache.clear(menu.containerId);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        updateControls();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (BodyTooltip tooltip : bodyTooltips) {
            int x = leftPos + StationControllerMenu.TEXT_LEFT;
            int y = topPos + StationControllerMenu.LINES_TOP + tooltip.row() * StationControllerMenu.LINE_HEIGHT;
            if (mouseX >= x && mouseX < x + font.width(tooltip.line().text())
                    && mouseY >= y && mouseY < y + StationControllerMenu.LINE_HEIGHT) {
                graphics.renderTooltip(font, Component.literal(tooltip.line().tooltip()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, EDGE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        renderedQuote = Optional.empty();
        bodyTooltips.clear();
        Optional<ClassicControlViewPacket> view = view();
        if (view.isEmpty()) {
            updateControls();
            return;
        }
        ClassicControlViewPacket shown = view.orElseThrow();
        List<Component> lines = menu.lines(shown);
        if (menu.kind() == StationControllerKind.WARP) {
            drawWarp(graphics, shown, lines);
        } else {
            List<FormattedCharSequence> rows = new ArrayList<>();
            lines.forEach(line -> rows.addAll(font.split(line, StationControllerMenu.TEXT_WIDTH)));
            draw(graphics, rows, StationControllerMenu.LINES_TOP, StationControllerMenu.LINE_HEIGHT,
                    StationControllerMenu.LINE_ROWS, MUTED);
        }
        shown.status().ifPresent(code -> draw(graphics, font.split(Component.translatable(
                        "screen.advancedrocketrycommunity.station_controller.status",
                        Component.translatable(code.translationKey()), code.name()), StationControllerMenu.TEXT_WIDTH),
                StationControllerMenu.STATUS_TOP, StationControllerMenu.STATUS_LINE_HEIGHT,
                StationControllerMenu.STATUS_ROWS, code.success() ? GOOD : WARNING));
        updateControls();
    }

    private void drawWarp(GuiGraphics graphics, ClassicControlViewPacket shown, List<Component> lines) {
        WarpControllerLayout.BodyLine orbit = WarpControllerLayout.bodyLine(lines.get(0).getString(),
                StationControllerMenu.TEXT_WIDTH, font::width);
        WarpControllerLayout.BodyLine target = WarpControllerLayout.bodyLine(lines.get(1).getString(),
                StationControllerMenu.TEXT_WIDTH, font::width);
        int next = 3;
        List<FormattedCharSequence> cost = shown.value(WarpControllerMenu.COST_CLASS) > 0
                ? font.split(lines.get(next++), StationControllerMenu.TEXT_WIDTH) : List.of();
        List<FormattedCharSequence> countdown = shown.value(WarpControllerMenu.COUNTDOWN) >= 0
                ? font.split(lines.get(next), StationControllerMenu.TEXT_WIDTH) : List.of();
        WarpControllerLayout.Layout<FormattedCharSequence> layout = WarpControllerLayout.arrange(
                Component.literal(orbit.text()).getVisualOrderText(), Component.literal(target.text()).getVisualOrderText(),
                font.split(lines.get(2), StationControllerMenu.TEXT_WIDTH), cost, countdown, StationControllerMenu.LINE_ROWS);
        for (WarpControllerLayout.Row<FormattedCharSequence> row : layout.rows()) {
            graphics.drawString(font, row.text(), StationControllerMenu.TEXT_LEFT,
                    StationControllerMenu.LINES_TOP + row.index() * StationControllerMenu.LINE_HEIGHT, MUTED, false);
        }
        bodyTooltips.add(new BodyTooltip(orbit, WarpControllerLayout.ORBIT_ROW));
        bodyTooltips.add(new BodyTooltip(target, WarpControllerLayout.TARGET_ROW));
        // Only a completed draw of this exact view can enable Confirm.
        renderedQuote = Optional.of(new RenderedQuote(shown, layout));
    }

    /** At most {@code limit} rows, so nothing is drawn outside the panel. */
    private void draw(GuiGraphics graphics, List<FormattedCharSequence> rows, int top, int height, int limit,
                      int colour) {
        for (int index = 0; index < Math.min(limit, rows.size()); index++) {
            graphics.drawString(font, rows.get(index), StationControllerMenu.TEXT_LEFT, top + index * height, colour,
                    false);
        }
    }

    private record Control(StationControllerMenu.Button spec, Button widget) {
    }

    private record BodyTooltip(WarpControllerLayout.BodyLine line, int row) {
    }

    private record RenderedQuote(ClassicControlViewPacket view, WarpControllerLayout.Layout<FormattedCharSequence> layout) {
    }
}
