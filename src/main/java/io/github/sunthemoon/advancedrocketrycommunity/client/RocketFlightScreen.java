package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact mission-control panel with server-listed station choices. */
public final class RocketFlightScreen extends AbstractContainerScreen<RocketFlightMenu> {
    private static final int FRAME = 0xFF26363B;
    private static final int PANEL = 0xFF0B1418;
    private static final int EDGE = 0xFF6E8A91;
    private static final int ACCENT = 0xFFE59D3B;
    private static final int FUEL = 0xFF5BC7A8;
    private static final int MUTED = 0xFF91A6AC;

    private final RocketFlightSelection selection;
    private Button earthButton;
    private Button moonButton;
    private Button stationButton;
    private Button launchButton;
    private Button cancelButton;
    private Button boardButton;
    private Button leaveButton;

    public RocketFlightScreen(RocketFlightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        selection = new RocketFlightSelection(menu.accessibleStations());
        imageWidth = 248;
        imageHeight = 216;
        titleLabelX = 12;
        titleLabelY = 10;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        initializeSelection();
        earthButton = addRenderableWidget(Button.builder(
                body(RocketDestination.EARTH),
                button -> selection.select(RocketDestination.EARTH)
        ).bounds(leftPos + 22, topPos + RocketFlightScreenLayout.DESTINATION_BUTTON_Y, 94, 20).build());
        moonButton = addRenderableWidget(Button.builder(
                body(RocketDestination.MOON),
                button -> selection.select(RocketDestination.MOON)
        ).bounds(leftPos + 132, topPos + RocketFlightScreenLayout.DESTINATION_BUTTON_Y, 94, 20).build());
        stationButton = addRenderableWidget(Button.builder(
                stationLabel(),
                button -> selectNextStation()
        ).bounds(leftPos + 22, topPos + 114, 204, 20).build());
        launchButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.launch"),
                button -> send(RocketFlightAction.LAUNCH)
        ).bounds(leftPos + 22, topPos + 176, 204, 24).build());
        cancelButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.cancel"),
                button -> send(RocketFlightAction.CANCEL)
        ).bounds(leftPos + 22, topPos + 176, 204, 24).build());
        boardButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.board"),
                button -> send(RocketFlightAction.BOARD)
        ).bounds(leftPos + 22, topPos + 148, 98, 20).build());
        leaveButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.advancedrocketrycommunity.rocket.leave"),
                button -> send(RocketFlightAction.LEAVE)
        ).bounds(leftPos + 128, topPos + 148, 98, 20).build());
        updateButtons();
    }

    private void send(RocketFlightAction action) {
        var target = selection.target(action, menu.activePlan());
        if (target.destination() != null && menu.rocketEntityId() >= 0) {
            RocketFlightNetwork.sendIntent(
                    action,
                    menu.rocketEntityId(),
                    target.destination(),
                    target.stationId()
            );
        }
    }

    private void selectNextStation() {
        selection.selectNextStation();
        stationButton.setMessage(stationLabel());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        initializeSelection();
        updateButtons();
    }

    private void updateButtons() {
        if (earthButton == null) {
            return;
        }
        RocketDestination current = menu.currentDestination();
        RocketDestination selected = selection.selected();
        boolean stationary = menu.state() == RocketFlightState.ASSEMBLED
                || menu.state() == RocketFlightState.FUELED
                || menu.state() == RocketFlightState.LANDED;
        earthButton.active = stationary && current != RocketDestination.EARTH;
        moonButton.active = stationary && current != RocketDestination.MOON;
        stationButton.active = stationary && current != RocketDestination.SPACE_STATION
                && !menu.accessibleStations().isEmpty();
        earthButton.setMessage(choiceLabel(RocketDestination.EARTH));
        moonButton.setMessage(choiceLabel(RocketDestination.MOON));
        stationButton.setMessage(stationLabel());
        boolean countdown = menu.state() == RocketFlightState.COUNTDOWN;
        launchButton.visible = !countdown;
        boolean quotedLaunch = menu.quotes().forDestination(selected).canLaunch();
        launchButton.active = stationary && quotedLaunch
                && selected != null
                && selected != current
                && (selected != RocketDestination.SPACE_STATION || selection.stationId() != null);
        cancelButton.visible = countdown;
        cancelButton.active = countdown && menu.activePlan().destination() != null;
        boardButton.visible = stationary;
        boardButton.active = stationary;
        leaveButton.visible = stationary;
        leaveButton.active = stationary;
    }

    private Component choiceLabel(RocketDestination destination) {
        return Component.literal(displayedDestination() == destination ? "[ " : "  ")
                .append(body(destination))
                .append(displayedDestination() == destination ? " ]" : "  ");
    }

    private Component stationLabel() {
        List<StationDestinationSummary> stations = menu.accessibleStations();
        if (menu.state() == RocketFlightState.COUNTDOWN
                && menu.activePlan().destination() == RocketDestination.SPACE_STATION) {
            var activeId = menu.activePlan().stationId();
            String name = stations.stream().filter(station -> station.stationId().equals(activeId))
                    .map(StationDestinationSummary::name).findFirst().orElse(activeId.toString());
            return Component.literal(font.plainSubstrByWidth("[ " + name + " ]", 190));
        }
        if (stations.isEmpty()) {
            return Component.translatable("screen.advancedrocketrycommunity.rocket.no_stations");
        }
        int selectedStationIndex = selection.stationIndex();
        StationDestinationSummary station = stations.get(selectedStationIndex);
        Component label = Component.translatable(
                "screen.advancedrocketrycommunity.rocket.station_choice",
                station.name(),
                selectedStationIndex + 1,
                stations.size()
        );
        return displayedDestination() == RocketDestination.SPACE_STATION
                ? Component.literal("[ ").append(label).append(" ]")
                : label;
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
        graphics.fill(x + 10, y + RocketFlightScreenLayout.PANEL_TOP,
                x + imageWidth - 10, y + RocketFlightScreenLayout.PANEL_BOTTOM, PANEL);
        graphics.renderOutline(x + 10, y + RocketFlightScreenLayout.PANEL_TOP, imageWidth - 20,
                RocketFlightScreenLayout.PANEL_BOTTOM - RocketFlightScreenLayout.PANEL_TOP, 0xFF40565D);

        int gaugeX = x + 22;
        int gaugeY = y + 60;
        int gaugeWidth = 204;
        graphics.fill(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + 9, 0xFF36474D);
        int fill = scale(menu.fuelAmount(), menu.fuelCapacity(), gaugeWidth - 2);
        if (fill > 0) {
            graphics.fill(gaugeX + 1, gaugeY + 1, gaugeX + 1 + fill, gaugeY + 8, FUEL);
        }
        if (displayedRequiredFuel() > 0 && menu.fuelCapacity() > 0) {
            int marker = scale(displayedRequiredFuel(), menu.fuelCapacity(), gaugeWidth - 2);
            graphics.fill(gaugeX + 1 + marker, gaugeY - 2, gaugeX + 2 + marker, gaugeY + 11, ACCENT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE7F0F2, false);
        RocketDestination current = menu.currentDestination();
        RocketDestination selected = displayedDestination();
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
                        displayedRequiredFuel()
                ),
                22,
                RocketFlightScreenLayout.FUEL_TEXT_Y,
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
                    140,
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
                    136,
                    MUTED
            );
        }
    }

    private static Component body(RocketDestination destination) {
        return Component.translatable(
                "body.advancedrocketrycommunity." + destination.name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private RocketDestination displayedDestination() {
        return selection.displayedDestination(menu.state(), menu.activePlan());
    }

    private void initializeSelection() {
        if (menu.hasPlanSnapshot()) {
            selection.initialize(menu.plannedDestination(), menu.currentDestination(), menu.activePlan().stationId());
        }
    }

    private int displayedRequiredFuel() {
        return menu.quotes().forDestination(displayedDestination()).requiredFuel();
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
