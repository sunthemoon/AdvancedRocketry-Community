package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.client.starmap.RocketStarMap;
import io.github.sunthemoon.advancedrocketrycommunity.client.starmap.StarMapInput;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
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
    private final RocketStarMap starMap;
    private boolean mapMode;
    private long nextCatalogRequest;
    private Button earthButton;
    private Button moonButton;
    private Button stationButton;
    private Button otherBodyButton;
    private Button launchButton;
    private Button cancelButton;
    private Button boardButton;
    private Button leaveButton;
    private int otherBodyIndex;

    public RocketFlightScreen(RocketFlightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        selection = new RocketFlightSelection(menu.accessibleStations());
        starMap = new RocketStarMap(menu, target -> { selection.select(target); showMap(false); });
        imageWidth = 248;
        imageHeight = 216;
        titleLabelX = 12;
        titleLabelY = 10;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        imageWidth = mapMode ? Math.min(520, width - 12) : 248;
        imageHeight = mapMode ? Math.min(330, height - 12) : 216;
        super.init();
        initializeSelection();
        if (mapMode) {
            starMap.init(leftPos, topPos, imageWidth, imageHeight, this::addRenderableWidget, () -> showMap(false));
            return;
        }
        addRenderableWidget(Button.builder(RocketStarMap.text("open"), b -> showMap(true))
                .bounds(leftPos + imageWidth - 74, topPos + 5, 64, 20).build());
        earthButton = addRenderableWidget(Button.builder(
                body(RocketDestination.EARTH),
                button -> selection.select(RocketDestination.EARTH)
        ).bounds(leftPos + 22, topPos + RocketFlightScreenLayout.DESTINATION_BUTTON_Y, 94, 20).build());
        moonButton = addRenderableWidget(Button.builder(
                body(RocketDestination.MOON),
                button -> selection.select(RocketDestination.MOON)
        ).bounds(leftPos + 132, topPos + RocketFlightScreenLayout.DESTINATION_BUTTON_Y, 94, 20).build());
        otherBodyButton = addRenderableWidget(Button.builder(
                otherBodyLabel(),
                button -> selectNextOtherBody()
        ).bounds(leftPos + 22, topPos + 114, 98, 20).build());
        stationButton = addRenderableWidget(Button.builder(
                stationLabel(),
                button -> selectNextStation()
        ).bounds(leftPos + 128, topPos + 114, 98, 20).build());
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

    private void showMap(boolean enabled) {
        mapMode = enabled;
        clearWidgets();
        init();
        if (enabled) { starMap.inspect(selection.selectedTarget()); starMap.update(); }
    }

    private void send(RocketFlightAction action) {
        if (action == RocketFlightAction.LAUNCH && !RocketStarMap.coherent(menu)) { return; }
        var target = selection.target(action, menu.activePlan());
        if (target.target() != null && menu.rocketEntityId() >= 0) {
            RocketFlightNetwork.sendIntent(
                    action,
                    menu.rocketEntityId(),
                    target.target()
            );
        }
    }

    private void selectNextStation() {
        selection.selectNextStation();
        stationButton.setMessage(stationLabel());
    }

    private void selectNextOtherBody() {
        List<TravelTarget.BodySurface> bodies = otherBodies();
        if (bodies.isEmpty()) {
            return;
        }
        if (selection.selectedTarget() instanceof TravelTarget.BodySurface selected) {
            int selectedIndex = bodies.indexOf(selected);
            otherBodyIndex = selectedIndex < 0 ? 0 : (selectedIndex + 1) % bodies.size();
        }
        selection.select(bodies.get(otherBodyIndex));
        otherBodyButton.setMessage(otherBodyLabel());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        initializeSelection();
        if (minecraft != null && minecraft.level != null && minecraft.gameMode != null) {
            long now = minecraft.level.getGameTime();
            if (!RocketStarMap.coherent(menu) && now >= nextCatalogRequest) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RocketNavigation.REFRESH_BUTTON);
                nextCatalogRequest = now + RocketNavigation.CATALOG_REFRESH_TICKS;
            }
        }
        if (mapMode) { starMap.update(); return; }
        updateButtons();
    }

    private void updateButtons() {
        if (earthButton == null) {
            return;
        }
        TravelTarget currentTarget = menu.currentTarget();
        boolean hasOtherBodies = !otherBodies().isEmpty();
        boolean stationary = menu.state() == RocketFlightState.ASSEMBLED
                || menu.state() == RocketFlightState.FUELED
                || menu.state() == RocketFlightState.LANDED;
        earthButton.active = stationary && !new TravelTarget.BodySurface(CelestialIds.EARTH_ID).equals(currentTarget);
        moonButton.active = stationary && !new TravelTarget.BodySurface(CelestialIds.MOON_ID).equals(currentTarget);
        otherBodyButton.visible = hasOtherBodies;
        otherBodyButton.active = stationary && hasOtherBodies;
        stationButton.active = stationary && !menu.accessibleStations().isEmpty();
        earthButton.setMessage(choiceLabel(RocketDestination.EARTH));
        moonButton.setMessage(choiceLabel(RocketDestination.MOON));
        stationButton.setMessage(stationLabel());
        otherBodyButton.setMessage(otherBodyLabel());
        boolean countdown = menu.state() == RocketFlightState.COUNTDOWN;
        launchButton.visible = !countdown;
        boolean quotedLaunch = menu.quotes().forTarget(
                selection.target(RocketFlightAction.LAUNCH, menu.activePlan()).target()
        ).canLaunch();
        launchButton.active = stationary && quotedLaunch && RocketStarMap.coherent(menu)
                && selection.selectedTarget() != null
                && !java.util.Objects.equals(selection.selectedTarget(), currentTarget)
                && (!(selection.selectedTarget() instanceof TravelTarget.Station)
                || selection.stationId() != null);
        cancelButton.visible = countdown;
        cancelButton.active = countdown && menu.activePlan().target() != null;
        boardButton.visible = stationary;
        boardButton.active = stationary;
        leaveButton.visible = stationary;
        leaveButton.active = stationary;
    }

    private Component choiceLabel(RocketDestination destination) {
        TravelTarget target = destination == RocketDestination.EARTH
                ? new TravelTarget.BodySurface(CelestialIds.EARTH_ID)
                : new TravelTarget.BodySurface(CelestialIds.MOON_ID);
        return Component.literal(java.util.Objects.equals(displayedTarget(), target) ? "[ " : "  ")
                .append(body(destination))
                .append(java.util.Objects.equals(displayedTarget(), target) ? " ]" : "  ");
    }

    private Component stationLabel() {
        List<StationDestinationSummary> stations = menu.accessibleStations();
        if (menu.state() == RocketFlightState.COUNTDOWN
                && menu.activePlan().destination() == RocketDestination.SPACE_STATION) {
            var activeId = menu.activePlan().stationId();
            String name = stations.stream().filter(station -> station.stationId().equals(activeId))
                    .map(StationDestinationSummary::name).findFirst().orElse(activeId.toString());
            return Component.literal(font.plainSubstrByWidth("[ " + name + " ]", 88));
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
        Component display = displayedDestination() == RocketDestination.SPACE_STATION
                ? Component.literal("[ ").append(label).append(" ]")
                : label;
        return Component.literal(font.plainSubstrByWidth(display.getString(), 88));
    }

    private Component otherBodyLabel() {
        List<TravelTarget.BodySurface> bodies = otherBodies();
        if (bodies.isEmpty()) {
            return Component.empty();
        }
        otherBodyIndex = Math.min(otherBodyIndex, bodies.size() - 1);
        int selectedIndex = bodies.indexOf(selection.selectedTarget());
        if (selectedIndex >= 0) { otherBodyIndex = selectedIndex; }
        TravelTarget.BodySurface target = bodies.get(otherBodyIndex);
        Component label = targetLabel(target);
        return java.util.Objects.equals(displayedTarget(), target)
                ? Component.literal("[ ").append(label).append(" ]")
                : label;
    }

    private List<TravelTarget.BodySurface> otherBodies() {
        return menu.quotes().entries().stream()
                .map(RocketFlightQuotes.TargetQuote::target)
                .filter(TravelTarget.BodySurface.class::isInstance)
                .map(TravelTarget.BodySurface.class::cast)
                .filter(target -> !target.bodyId().equals(CelestialIds.EARTH_ID)
                        && !target.bodyId().equals(CelestialIds.MOON_ID))
                .toList();
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
        if (mapMode) { starMap.render(graphics, font); return; }
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
        if (mapMode) { return; }
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 94), titleLabelX, titleLabelY, 0xFFE7F0F2, false);
        TravelTarget current = menu.currentTarget();
        TravelTarget selected = displayedTarget();
        Component route = Component.translatable("screen.advancedrocketrycommunity.rocket.route",
                current == null ? Component.literal("?") : targetLabel(current),
                selected == null ? Component.literal("?") : targetLabel(selected));
        graphics.drawString(font, font.plainSubstrByWidth(route.getString(), 204), 22, 33, 0xFFD6E2E5, false);
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
            var status = !RocketStarMap.coherent(menu) ? RocketStarMap.text("syncing")
                    : Component.translatable(menu.quotes().forTarget(selected).status().translationKey());
            graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 204), 22, 136, MUTED, false);
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

    private TravelTarget displayedTarget() {
        return selection.displayedTarget(menu.state(), menu.activePlan());
    }

    private Component targetLabel(TravelTarget target) {
        if (target instanceof TravelTarget.BodySurface surface) {
            return RocketStarMap.bodyLabel(surface.bodyId());
        }
        if (target instanceof TravelTarget.Station station) {
            return menu.accessibleStations().stream()
                    .filter(summary -> summary.stationId().equals(station.instanceId()))
                    .map(summary -> (Component) Component.literal(summary.name()))
                    .findFirst()
                    .orElseGet(() -> Component.literal(station.instanceId().toString()));
        }
        return Component.literal(target.typeId().toString());
    }

    private void initializeSelection() {
        selection.updateStations(menu.accessibleStations());
        if (menu.hasPlanSnapshot()) {
            selection.initialize(menu.activePlan().target(), menu.currentTarget());
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return StarMapInput.click(mapMode, () -> starMap.click(x, y, button), () -> super.mouseClicked(x, y, button));
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return mapMode && button == 0 && starMap.drag(dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (mapMode) { starMap.release(); }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return mapMode && starMap.scroll(x, y, amount) || super.mouseScrolled(x, y, amount);
    }

    private int displayedRequiredFuel() {
        var target = menu.state() == RocketFlightState.COUNTDOWN
                ? menu.activePlan().target()
                : selection.target(RocketFlightAction.LAUNCH, menu.activePlan()).target();
        return menu.quotes().forTarget(target).requiredFuel();
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
