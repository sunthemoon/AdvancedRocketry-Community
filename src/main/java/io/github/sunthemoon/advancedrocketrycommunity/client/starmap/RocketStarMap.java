package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Original code-drawn map hosted by the flight screen; it never sends a launch intent. */
public final class RocketStarMap {
    private final RocketFlightMenu menu;
    private final Consumer<TravelTarget> choose;
    private CelestialSnapshot snapshot;
    private StarMapLayout layout;
    private java.util.Map<ResourceLocation, StarMapLayout.Node> nodesById = java.util.Map.of();
    private StarMapViewport viewport;
    private ResourceLocation selectedBody;
    private UUID selectedStation;
    private List<RocketNavigation.Station> stations = List.of();
    private Button surfaceButton, stationButton, chooseStationButton;
    private int x, y, width, height, mapHeight;
    private boolean dragging;

    public RocketStarMap(RocketFlightMenu menu, Consumer<TravelTarget> choose) {
        this.menu = menu;
        this.choose = choose;
    }

    public void init(int x, int y, int width, int height, Consumer<Button> add, Runnable back) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        mapHeight = Math.max(24, height - 144);
        add.accept(Button.builder(Component.literal("<"), b -> step(-1)).bounds(x + width - 137, y + 7, 26, 20).build());
        add.accept(Button.builder(Component.literal(">"), b -> step(1)).bounds(x + width - 107, y + 7, 26, 20).build());
        add.accept(Button.builder(text("back"), b -> back.run()).bounds(x + width - 77, y + 7, 67, 20).build());
        int cell = (width - 28) / 3;
        surfaceButton = Button.builder(text("surface"), b -> {
            if (surfaceButton.active && selectedBody != null) { choose.accept(new TravelTarget.BodySurface(selectedBody)); }
        }).bounds(x + 10, y + height - 30, cell, 20).build();
        stationButton = Button.builder(text("stations"), b -> cycleStation()).bounds(x + 14 + cell, y + height - 30, cell, 20).build();
        chooseStationButton = Button.builder(text("use_station"), b -> {
            if (chooseStationButton.active && selectedStation != null) { choose.accept(new TravelTarget.Station(selectedStation)); }
        }).bounds(x + 18 + cell * 2, y + height - 30, cell, 20).build();
        add.accept(surfaceButton); add.accept(stationButton); add.accept(chooseStationButton);
        viewport = null;
        dragging = false;
        update();
    }

    public void inspect(TravelTarget target) {
        if (target instanceof TravelTarget.BodySurface surface) { selectedBody = surface.bodyId(); }
        if (target instanceof TravelTarget.Station station) {
            menu.navigation().stations().stream().filter(s -> s.stationId().equals(station.instanceId())).findFirst()
                    .ifPresent(s -> { selectedBody = s.orbitBody(); selectedStation = s.stationId(); });
        }
        focus();
    }

    public void update() {
        var latest = CelestialClientCache.snapshot().orElse(null);
        if (latest != snapshot) {
            boolean changed = !java.util.Objects.equals(latest, snapshot);
            snapshot = latest;
            if (changed) {
                layout = snapshot == null ? null : StarMapLayout.create(snapshot);
                nodesById = layout == null ? java.util.Map.of() : layout.nodes().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(StarMapLayout.Node::id, node -> node));
                viewport = null;
            }
        }
        if (layout != null && viewport == null) {
            viewport = new StarMapViewport(layout, x + 10, y + 44, width - 20, mapHeight);
            if (layout.nodes().stream().noneMatch(node -> node.id().equals(selectedBody))) {
                selectedBody = layout.nodes().isEmpty() ? null : layout.nodes().get(0).id();
            }
            focus();
        }
        stations = menu.navigation().stations().stream().filter(station -> station.orbitBody().equals(selectedBody)).toList();
        if (stations.stream().noneMatch(station -> station.stationId().equals(selectedStation))) {
            selectedStation = stations.isEmpty() ? null : stations.get(0).stationId();
        }
        if (surfaceButton == null) { return; }
        var entry = entry();
        surfaceButton.active = coherent(menu) && entry != null && entry.capabilities().landable()
                && menu.quotes().entries().stream().anyMatch(q -> q.target().equals(new TravelTarget.BodySurface(selectedBody)));
        stationButton.active = coherent(menu) && !stations.isEmpty();
        chooseStationButton.active = stationButton.active;
        int index = stationIndex();
        stationButton.setMessage(Component.translatable("starmap.advancedrocketrycommunity.station_count", index + 1, stations.size()));
        if (index < 0) { stationButton.setMessage(text("stations")); }
        var station = index < 0 ? null : stations.get(index);
        Component detail = station == null ? text("no_stations") : Component.literal(station.name()).append(" / ")
                .append(Component.translatable(menu.quotes().forTarget(new TravelTarget.Station(station.stationId())).status().translationKey()));
        stationButton.setTooltip(Tooltip.create(detail));
        chooseStationButton.setTooltip(Tooltip.create(detail));
    }

    public void render(GuiGraphics graphics, Font font) {
        graphics.fill(x + 10, y + 44, x + width - 10, y + 44 + mapHeight, 0xFF081318);
        graphics.renderOutline(x + 10, y + 44, width - 20, mapHeight, 0xFF40565D);
        line(graphics, font, text("title"), x + 12, y + 11, width - 156, 0xFFE7F0F2);
        line(graphics, font, text("hint"), x + 12, y + 31, width - 24, 0xFF91A6AC);
        if (viewport != null) {
            graphics.enableScissor(x + 11, y + 45, x + width - 11, y + 43 + mapHeight);
            for (var node : layout.nodes()) {
                if (node.parent() == null) { continue; }
                var parent = nodesById.get(node.parent());
                int px = sx(parent), py = sy(parent), nx = sx(node), ny = sy(node), mid = (px + nx) / 2;
                graphics.hLine(Math.min(px, mid), Math.max(px, mid), py, 0xFF304C54);
                graphics.vLine(mid, Math.min(py, ny), Math.max(py, ny), 0xFF304C54);
                graphics.hLine(Math.min(mid, nx), Math.max(mid, nx), ny, 0xFF304C54);
            }
            for (var node : layout.nodes()) {
                int nx = sx(node), ny = sy(node);
                int color = node.id().equals(selectedBody) ? 0xFFE59D3B : 0xFF5BC7A8;
                graphics.fill(nx - 3, ny - 3, nx + 4, ny + 4, color);
                if (node.id().equals(selectedBody)) { graphics.renderOutline(nx - 6, ny - 6, 13, 13, color); }
                line(graphics, font, bodyLabel(node.id()), nx + 8, ny - 4, 88, color);
            }
            graphics.disableScissor();
        }
        var entry = entry();
        int detailY = y + 50 + mapHeight;
        if (entry != null) {
            line(graphics, font, bodyLabel(selectedBody).copy().append(" / " + selectedBody), x + 12, detailY, width - 24, 0xFFE7F0F2);
            line(graphics, font, Component.translatable("starmap.advancedrocketrycommunity.gravity_temperature",
                    number(entry.gravityMultiplier()), number(entry.temperatureKelvin())), x + 12, detailY + 11, width - 24, 0xFF91A6AC);
            line(graphics, font, Component.translatable("starmap.advancedrocketrycommunity.atmosphere",
                    number(entry.pressure()), text(entry.breathable() ? "breathable" : "unbreathable")), x + 12, detailY + 22, width - 24, 0xFF91A6AC);
            line(graphics, font, Component.translatable("starmap.advancedrocketrycommunity.solar_radiation",
                    number(entry.solarIntensity()), number(entry.radiation())), x + 12, detailY + 33, width - 24, 0xFF91A6AC);
        }
        Component status = !coherent(menu) ? text("syncing") : entry == null || !surfaceButton.active ? text("no_surface")
                : quoteLabel(new TravelTarget.BodySurface(selectedBody));
        line(graphics, font, status, x + 12, y + height - 45, width - 24, 0xFFE59D3B);
    }

    private Component quoteLabel(TravelTarget target) {
        var quote = menu.quotes().forTarget(target);
        return Component.translatable("starmap.advancedrocketrycommunity.quote",
                Component.translatable(quote.status().translationKey()), quote.requiredFuel());
    }

    public boolean click(double mouseX, double mouseY, int button) {
        if (button != 0 || viewport == null || !viewport.contains(mouseX, mouseY)) { return false; }
        dragging = true;
        layout.nodes().stream().filter(n -> Math.abs(mouseX - sx(n)) <= 8 && Math.abs(mouseY - sy(n)) <= 8)
                .min(java.util.Comparator.comparingDouble(n -> Math.hypot(mouseX - sx(n), mouseY - sy(n))))
                .ifPresent(n -> { selectedBody = n.id(); update(); });
        return true;
    }

    public boolean drag(double dx, double dy) {
        if (!dragging || viewport == null) { return false; }
        viewport.drag(dx, dy); return true;
    }

    public void release() { dragging = false; }

    public boolean scroll(double mx, double my, double steps) {
        if (viewport == null || !viewport.contains(mx, my)) { return false; }
        viewport.zoom(steps, mx, my); return true;
    }

    private void step(int direction) {
        if (layout == null || layout.nodes().isEmpty()) { return; }
        int index = 0;
        for (int i = 0; i < layout.nodes().size(); i++) { if (layout.nodes().get(i).id().equals(selectedBody)) { index = i; break; } }
        selectedBody = layout.nodes().get(Math.floorMod(index + direction, layout.nodes().size())).id();
        focus(); update();
    }

    private void focus() {
        if (viewport != null) { layout.nodes().stream().filter(n -> n.id().equals(selectedBody)).findFirst().ifPresent(viewport::focus); }
    }

    private void cycleStation() {
        if (!stations.isEmpty()) { selectedStation = stations.get((stationIndex() + 1) % stations.size()).stationId(); update(); }
    }

    private int stationIndex() {
        for (int i = 0; i < stations.size(); i++) { if (stations.get(i).stationId().equals(selectedStation)) { return i; } }
        return -1;
    }

    private CelestialSnapshot.Entry entry() {
        return snapshot == null ? null : snapshot.entries().stream().filter(e -> e.bodyId().equals(selectedBody)).findFirst().orElse(null);
    }

    private int sx(StarMapLayout.Node node) { return (int) Math.round(viewport.screenX(node.x())); }
    private int sy(StarMapLayout.Node node) { return (int) Math.round(viewport.screenY(node.y())); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static void line(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, width)), x, y, color, false);
    }

    public static boolean coherent(RocketFlightMenu menu) {
        return menu.navigation().coherent(CelestialClientCache.generation(),
                CelestialClientCache.lastResult() == CelestialClientCache.AcceptResult.ACCEPTED);
    }

    public static Component text(String suffix) { return Component.translatable("starmap.advancedrocketrycommunity." + suffix); }
    public static Component bodyLabel(ResourceLocation id) {
        String key = "body." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id.toString());
    }
}
