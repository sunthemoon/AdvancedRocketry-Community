package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact mission-control panel with fixed, server-authoritative Earth/Moon choices. */
public final class RocketFlightScreen extends AbstractContainerScreen<RocketFlightMenu> {
    private static final int FRAME = 0xFF26363B;
    private static final int PANEL = 0xFF0B1418;
    private static final int EDGE = 0xFF6E8A91;
    private static final int ACCENT = 0xFFE59D3B;
    private static final int FUEL = 0xFF5BC7A8;
    private static final int MUTED = 0xFF91A6AC;

    private RocketDestination selected;
    private Button earthButton;
    private Button moonButton;
    private Button launchButton;
    private Button cancelButton;

    public RocketFlightScreen(RocketFlightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 248;
        imageHeight = 176;
        titleLabelX = 12;
        titleLabelY = 10;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        selected = menu.plannedDestination();
        if (selected == null && menu.currentDestination() != null) {
            selected = menu.currentDestination().opposite();
        }
        earthButton = addRenderableWidget(Button.builder(
                body(RocketDestination.EARTH),
                button -> selected = RocketDestination.EARTH
        ).bounds(leftPos + 22, topPos + 89, 94, 20).build());
        moonButton = addRenderableWidget(Button.builder(
                body(RocketDestination.MOON),
                button -> selected = RocketDestination.MOON
        ).bounds(leftPos + 132, topPos + 89, 94, 20).build());
        launchButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.launch"),
                button -> send(RocketFlightAction.LAUNCH)
        ).bounds(leftPos + 22, topPos + 132, 204, 24).build());
        cancelButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.cancel"),
                button -> send(RocketFlightAction.CANCEL)
        ).bounds(leftPos + 22, topPos + 132, 204, 24).build());
        updateButtons();
    }

    private void send(RocketFlightAction action) {
        RocketDestination destination = selected == null
                ? menu.plannedDestination()
                : selected;
        if (destination != null && menu.rocketEntityId() >= 0) {
            RocketFlightNetwork.sendIntent(action, menu.rocketEntityId(), destination);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (selected == null) {
            selected = menu.plannedDestination();
        }
        updateButtons();
    }

    private void updateButtons() {
        if (earthButton == null) {
            return;
        }
        RocketDestination current = menu.currentDestination();
        earthButton.active = current != RocketDestination.EARTH;
        moonButton.active = current != RocketDestination.MOON;
        earthButton.setMessage(choiceLabel(RocketDestination.EARTH));
        moonButton.setMessage(choiceLabel(RocketDestination.MOON));
        boolean countdown = menu.state() == RocketFlightState.COUNTDOWN;
        launchButton.visible = !countdown;
        launchButton.active = menu.canLaunch() && selected != null && selected != current;
        cancelButton.visible = countdown;
        cancelButton.active = countdown;
    }

    private Component choiceLabel(RocketDestination destination) {
        return Component.literal(selected == destination ? "[ " : "  ")
                .append(body(destination))
                .append(selected == destination ? " ]" : "  ");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, FRAME, 0xFF101A1E);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 10, y + 27, x + imageWidth - 10, y + 79, PANEL);
        graphics.renderOutline(x + 10, y + 27, imageWidth - 20, 52, 0xFF40565D);

        int gaugeX = x + 22;
        int gaugeY = y + 60;
        int gaugeWidth = 204;
        graphics.fill(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + 9, 0xFF36474D);
        int fill = scale(menu.fuelAmount(), menu.fuelCapacity(), gaugeWidth - 2);
        if (fill > 0) {
            graphics.fill(gaugeX + 1, gaugeY + 1, gaugeX + 1 + fill, gaugeY + 8, FUEL);
        }
        if (menu.requiredFuel() > 0 && menu.fuelCapacity() > 0) {
            int marker = scale(menu.requiredFuel(), menu.fuelCapacity(), gaugeWidth - 2);
            graphics.fill(gaugeX + 1 + marker, gaugeY - 2, gaugeX + 2 + marker, gaugeY + 11, ACCENT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE7F0F2, false);
        RocketDestination current = menu.currentDestination();
        Component route = Component.translatable("screen.advancedrocketrycommunity.rocket.route",
                current == null ? Component.literal("?") : body(current),
                selected == null ? Component.literal("?") : body(selected));
        graphics.drawString(font, route, 22, 33, 0xFFD6E2E5, false);
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.advancedrocketrycommunity.rocket.state",
                        Component.translatable(stateKey(menu.state()))
                ),
                22,
                46,
                MUTED,
                false
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "screen.advancedrocketrycommunity.rocket.fuel",
                        menu.fuelAmount(),
                        menu.fuelCapacity(),
                        menu.requiredFuel()
                ),
                22,
                72,
                MUTED,
                false
        );
        if (menu.state() == RocketFlightState.COUNTDOWN) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.advancedrocketrycommunity.rocket.countdown",
                            menu.countdownRemaining()
                    ),
                    imageWidth / 2,
                    116,
                    ACCENT
            );
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.advancedrocketrycommunity.rocket.passengers",
                            menu.passengerCount()
                    ),
                    imageWidth / 2,
                    116,
                    MUTED
            );
        }
    }

    private static Component body(RocketDestination destination) {
        return Component.translatable(
                "body.advancedrocketrycommunity." + destination.name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private static String stateKey(RocketFlightState state) {
        return "flight.advancedrocketrycommunity.state."
                + state.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static int scale(int value, int maximum, int pixels) {
        if (value <= 0 || maximum <= 0) {
            return 0;
        }
        return Math.min(pixels, (int) ((long) value * pixels / maximum));
    }
}
