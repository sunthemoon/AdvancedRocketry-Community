package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlsNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Drawn instrument panel; only a completely rendered current quote enables launch. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RocketMonitorScreen extends AbstractContainerScreen<RocketMonitorMenu> {
    public static final String KEY = "screen.advancedrocketrycommunity.monitor.";
    private final List<Button> buttons = new ArrayList<>();
    private final List<HoverLine> hoverLines = new ArrayList<>();
    private ClassicControlViewPacket drawn;
    public RocketMonitorScreen(RocketMonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 300; imageHeight = 238;
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.ROCKET_MONITORING_STATION.get(), RocketMonitorScreen::new));
    }
    @Override protected void init() {
        super.init(); buttons.clear(); drawn = null;
        int[] actions = {0, 1, 3, 4, 5, 6, 7};
        String[] names = {"previous", "next", "quote", "arm", "disarm", "launch", "mode"};
        for (int i = 0; i < actions.length; i++) {
            int action = actions[i];
            buttons.add(addRenderableWidget(Button.builder(Component.translatable(KEY + names[i]), ignored -> send(action))
                    .bounds(leftPos + 10 + i % 4 * 72, topPos + 188 + i / 4 * 22, 68, 20).build()));
        }
    }
    private void send(int action) {
        var view = ClassicControlViewCache.view(menu.containerId, menu.classicControlKind()).orElse(null);
        if (view == null || action == RocketMonitorMenu.LAUNCH && (view.token() == 0 || !view.equals(drawn))) { return; }
        ClassicControlsNetwork.sendIntent(new ClassicControlIntentPacket(menu.containerId, menu.classicControlKind(),
                action, 0, action == RocketMonitorMenu.LAUNCH ? view.token() : 0));
        if (action == RocketMonitorMenu.LAUNCH) { drawn = null; }
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF263F43);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFDFE4DC);
        graphics.fill(leftPos + 8, topPos + 28, leftPos + imageWidth - 8, topPos + 184, 0xFF142F35);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 12, 0x263F43, false);
        hoverLines.clear();
        drawn = null;
        var view = ClassicControlViewCache.view(menu.containerId, menu.classicControlKind()).orElse(null);
        if (view == null) { return; }
        var v = view.values();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(RocketMonitorStatus.values()[v.get(0)].translationKey()));
        lines.add(Component.translatable(KEY + "flight", Component.translatable(KEY + "flight." + v.get(1))));
        lines.add(Component.translatable(KEY + "motion", decimal(v.get(2)), decimal(v.get(3))));
        int selected = v.get(4);
        lines.add(Component.translatable(KEY + "destination", selected >= 0 && selected < menu.labels().size()
                ? menu.labels().get(selected) : "-"));
        for (int bank = 0; bank < 2; bank++) {
            int offset = 10 + bank * 5;
            Component fluid = v.get(offset + 4) < 0 ? Component.literal("-") : BuiltInRegistries.FLUID.byId(v.get(offset + 4)).getFluidType().getDescription();
            lines.add(Component.translatable(KEY + "bank", Component.translatable(KEY + "role." + v.get(offset)), fluid));
            lines.add(Component.translatable(KEY + "bank_amount", v.get(offset + 1), v.get(offset + 2), RocketMonitorView.costText(v.get(offset + 3))));
        }
        lines.add(Component.translatable(KEY + "cost", RocketMonitorView.costText(v.get(20))));
        lines.add(Component.translatable(KEY + "armed_mode", Component.translatable(KEY + "armed." + v.get(7)),
                Component.translatable(KEY + "mode." + v.get(6))));
        if (v.get(8) > 0) { lines.add(Component.translatable(RocketFlightRequestCode.values()[v.get(8)].translationKey(), RocketMonitorView.costText(v.get(20)))); }
        view.status().ifPresent(status -> lines.add(Component.translatable(status.translationKey())));
        int y = 34;
        for (var line : lines) {
            // Names may be abbreviated, but the complete debit rows must be visible before launch.
            if (font.width(line) > imageWidth - 24) {
                graphics.drawString(font, font.substrByWidth(line, imageWidth - 32).getString(), 12, y, 0xFFF1D18A, false);
                hoverLines.add(new HoverLine(y, line));
            } else { graphics.drawString(font, line, 12, y, 0xFFE0EFE7, false); }
            y += 12;
        }
        if (java.util.stream.IntStream.of(5, 7, 8).allMatch(index -> font.width(lines.get(index)) <= imageWidth - 24)) { drawn = view; }
    }
    private static String decimal(int value) { return String.format(Locale.ROOT, "%.2f", value / 1000.0); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        var view = ClassicControlViewCache.view(menu.containerId, menu.classicControlKind()).orElse(null);
        for (int i = 0; i < buttons.size(); i++) { buttons.get(i).active = view != null && (i != 5 || view.token() > 0 && view.equals(drawn)); }
        super.render(graphics, mouseX, mouseY, partial);
        for (var hover : hoverLines) {
            if (mouseX >= leftPos + 8 && mouseX < leftPos + imageWidth - 8
                    && mouseY >= topPos + hover.y() && mouseY < topPos + hover.y() + 12) {
                graphics.renderTooltip(font, hover.text(), mouseX, mouseY); break;
            }
        }
    }
    private record HoverLine(int y, Component text) { }
    @Override public void removed() { ClassicControlViewCache.clear(menu.containerId); super.removed(); }
}
