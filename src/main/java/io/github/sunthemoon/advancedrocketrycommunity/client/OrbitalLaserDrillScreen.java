package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillStorage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored laser drill panel (ADR-055 section 5): energy, lens, output, the start, stop, redstone and mode
 * buttons, the target selection, link, unlink and the physical-mode confirmation, and the status with its stable code
 * from the device view, so no state is shown by colour alone.
 */
public final class OrbitalLaserDrillScreen extends EndgameRefusalScreen<OrbitalLaserDrillMenu> {
    private final ScreenText text = new ScreenText();
    private final ScrollableTooltip details = new ScrollableTooltip();
    private static final int PANEL_TOP = 0xFF1B1F2A;
    private static final int PANEL_BOTTOM = 0xFF0D1016;
    private static final int EDGE = 0xFF7FD3FF;
    private static final int RECESS = 0xFF07090D;
    private static final int TEXT = 0xFFE8F1FA;
    private static final int MUTED = 0xFF93A4B8;
    private static final int BEAM = 0xFF5FC8FF;
    private static final int WARNING = 0xFFFFC857;

    public OrbitalLaserDrillScreen(OrbitalLaserDrillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu::energyStored);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_START,
                "screen.advancedrocketrycommunity.orbital_laser_drill.start", 8, 76, 38));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_STOP,
                "screen.advancedrocketrycommunity.orbital_laser_drill.stop", 48, 76, 38));
        Button redstone = button(OrbitalLaserDrillMenu.BUTTON_REDSTONE,
                "screen.advancedrocketrycommunity.orbital_laser_drill.redstone_short", 88, 76, 42);
        redstone.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.advancedrocketrycommunity.orbital_laser_drill.redstone")));
        addRenderableWidget(redstone);
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_MODE,
                "screen.advancedrocketrycommunity.orbital_laser_drill.mode", 132, 76, 36));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_TARGET_PREVIOUS,
                "screen.advancedrocketrycommunity.orbital_laser_drill.previous", 8, 92, 16));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT,
                "screen.advancedrocketrycommunity.orbital_laser_drill.next", 26, 92, 16));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_LINK,
                "screen.advancedrocketrycommunity.orbital_laser_drill.link", 44, 92, 32));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_UNLINK,
                "screen.advancedrocketrycommunity.orbital_laser_drill.unlink", 78, 92, 38));
        addRenderableWidget(button(OrbitalLaserDrillMenu.BUTTON_CONFIRM,
                "screen.advancedrocketrycommunity.orbital_laser_drill.confirm", 118, 92, 50));
    }

    private Button button(int id, String key, int x, int y, int width) {
        return ScreenText.fitButton(Button.builder(Component.translatable(key),
                ignored -> sendButton(id)).bounds(leftPos + x, topPos + y, width, 14).build(), font);
    }

    @Override
    public void removed() {
        super.removed();
        EndgameDeviceViewCache.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        text.clear();
        details.beginFrame();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 18, 10, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), LaserDrillStorage.ENERGY_CAPACITY), mouseX, mouseY);
        }
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isPresent() && isHovering(8, 108, 160, 20, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(statusLine(view.get().status()));
            if (EndgameScreenLabels.showLastStop(view.get().status(), view.get().lastStop())) {
                lines.add(Component.translatable("screen.advancedrocketrycommunity.orbital_laser_drill.last_stop",
                        statusLine(view.get().lastStop())));
            }
            for (EndgameDeviceView.Line line : view.get().lines()) {
                lines.add(EndgameViewText.line(line));
            }
            if (!lines.isEmpty()) {
                details.render(graphics, font, lines, mouseX, mouseY);
            }
        }
        if (!isHovering(8, 108, 160, 20, mouseX, mouseY)) {
            text.renderTooltip(graphics, font, leftPos, topPos, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (details.scroll(delta)) {
            releaseRefusal();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 8, y + 18, x + 18, y + 68, RECESS);
        int height = (int) Math.min(50L, (long) menu.energyStored() * 50L / LaserDrillStorage.ENERGY_CAPACITY);
        graphics.fill(x + 8, y + 68 - height, x + 18, y + 68, BEAM);
        ContainerSlotBackground.draw(graphics, menu.slots, x, y, 0xFF52738B, RECESS);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        text.draw(graphics, font, title, titleLabelX, titleLabelY, 160, TEXT);
        text.draw(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 160, MUTED);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        text.wrapped(graphics, font, statusLine(status), 8, 109, 160, 2,
                status == EndgameCode.OK ? BEAM : WARNING);
    }

    /** The translated status with its stable code, never colour alone (ADR-054 section 4). */
    private static Component statusLine(EndgameCode code) {
        return Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, Component.translatable(code.translationKey()),
                code.name());
    }

}
