package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.*;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMonitorFlights;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Container-only intents and a frozen, bounded opening catalog. A quote token belongs to exactly this menu. */
public final class RocketMonitorMenu extends AbstractContainerMenu implements ClassicControlTarget {
    public static final int PREVIOUS = 0, NEXT = 1, SELECT = 2, QUOTE = 3, ARM = 4, DISARM = 5, LAUNCH = 6, MODE = 7;
    private static final SecureRandom TOKENS = new SecureRandom();
    private final RocketMonitorBlockEntity monitor;
    private final Player viewer;
    private final UUID device;
    private final List<RocketMonitorFlights.Target> targets;
    private final List<String> labels;
    private int selected;
    private RocketMonitorQuote displayed;
    private long token;
    private Optional<StationManagementCode> status = Optional.empty();
    private ClassicControlViewPacket lastSent;

    public RocketMonitorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.ROCKET_MONITORING_STATION.get(), id);
        monitor = null; viewer = inventory.player; device = null; targets = List.of();
        if (buffer.readableBytes() > 32768) { throw new IllegalArgumentException("Monitor opening exceeds 32 KiB"); }
        int count = buffer.readUnsignedByte();
        if (count > 160) { throw new IllegalArgumentException("Too many monitor destinations"); }
        var names = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) { names.add(buffer.readUtf(128)); }
        if (buffer.isReadable()) { throw new IllegalArgumentException("Trailing monitor opening bytes"); }
        labels = List.copyOf(names);
    }
    public RocketMonitorMenu(int id, Player player, RocketMonitorBlockEntity monitor, List<RocketMonitorFlights.Target> targets) {
        super(ModMenuTypes.ROCKET_MONITORING_STATION.get(), id);
        this.monitor = monitor; viewer = player; device = monitor.root().device(); this.targets = List.copyOf(targets);
        labels = targets.stream().map(RocketMonitorFlights.Target::label).toList();
        selected = targets.isEmpty() ? -1 : 0;
        for (int i = 0; i < targets.size(); i++) { if (targets.get(i).target().equals(monitor.root().destination())) { selected = i; } }
    }
    public static void open(ServerPlayer player, RocketMonitorBlockEntity monitor) {
        if (!monitor.admitted(player)) { return; }
        var targets = RocketRuntime.monitorFlights().map(service -> service.targets(monitor)).orElse(List.of());
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, holder) -> new RocketMonitorMenu(id, holder, monitor, targets),
                Component.translatable("block.advancedrocketrycommunity.rocket_monitoring_station")), buffer -> {
            buffer.writeByte(targets.size());
            for (var target : targets) { buffer.writeUtf(target.label(), 128); }
            if (buffer.readableBytes() > 32768) { throw new IllegalArgumentException("Monitor opening exceeds 32 KiB"); }
        });
    }
    public List<String> labels() { return labels; }
    @Override public ClassicControlKind classicControlKind() { return ClassicControlKind.ROCKET_MONITOR; }
    @Override public boolean stillValid(Player player) {
        return monitor != null && player == viewer && monitor.admitted(player) && device.equals(monitor.root().device());
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { super.removed(player); displayed = null; token = 0; }
    public Optional<ClassicControlViewPacket> lastView() { return Optional.ofNullable(lastSent); }

    @Override public void acceptClassicControl(ServerPlayer sender, ClassicControlIntentPacket intent) {
        if (sender.containerMenu != this || intent.containerId() != containerId || intent.kind() != classicControlKind()
                || !stillValid(sender)) { return; }
        if (!StationRuntime.manager().map(manager -> manager.admitControllerAction(sender)).orElse(false)) {
            status = Optional.of(StationManagementCode.CONTROL_RATE_LIMITED); send(); return;
        }
        var service = RocketRuntime.monitorFlights().orElse(null);
        if (service == null) { status = Optional.of(StationManagementCode.AUTHORITY_UNAVAILABLE); send(); return; }
        validateQuote(service);
        status = Optional.empty();
        switch (intent.action()) {
            case PREVIOUS, NEXT, SELECT -> {
                if (!targets.isEmpty()) {
                    int next = intent.action() == SELECT ? intent.value() : Math.floorMod(selected + (intent.action() == NEXT ? 1 : -1), targets.size());
                    if (next >= targets.size()) { status = Optional.of(StationManagementCode.CONFIRMATION_MISMATCH); break; }
                    selected = next; displayed = null; token = 0; monitor.disarm();
                }
            }
            case QUOTE -> {
                displayed = selected < 0 ? null : service.quote(monitor, targets.get(selected).target());
                token = displayed != null && displayed.ready() ? nextToken() : 0;
                status = Optional.of(token > 0 ? StationManagementCode.ISSUED : StationManagementCode.AUTHORITY_UNAVAILABLE);
            }
            case ARM, MODE -> {
                var configuration = displayed;
                if (intent.action() == MODE && configuration == null && selected >= 0) {
                    configuration = service.quote(monitor, targets.get(selected).target());
                }
                boolean changed = monitor.configure(configuration, intent.action() == ARM || monitor.root().armed(),
                        intent.action() == MODE ? 1 - monitor.root().mode() : monitor.root().mode());
                status = Optional.of(changed ? StationManagementCode.ISSUED : StationManagementCode.CONFIRMATION_EXPIRED);
            }
            case DISARM -> monitor.disarm();
            case LAUNCH -> {
                if (token == 0 || token != intent.token() || displayed == null) {
                    status = Optional.of(StationManagementCode.CONFIRMATION_MISMATCH); break;
                }
                var quote = displayed; displayed = null; token = 0;
                monitor.launch(quote, sender);
            }
            default -> throw new IllegalStateException("Validated monitor action is unknown");
        }
        send();
    }
    private void validateQuote(RocketMonitorFlights service) {
        if (displayed != null && !displayed.same(service.quote(monitor, displayed.target()))) { displayed = null; token = 0; }
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && player.containerMenu == this) {
            if (!stillValid(player)) { player.closeContainer(); return; }
            RocketRuntime.monitorFlights().ifPresent(this::validateQuote); send();
        }
    }
    private void send() {
        if (!(viewer instanceof ServerPlayer player) || !stillValid(player)) { return; }
        var packet = new ClassicControlViewPacket(containerId, classicControlKind(), status, token,
                RocketMonitorView.values(monitor, displayed, selected, targets.size()));
        if (!packet.equals(lastSent)) { ClassicControlsNetwork.sendView(player, packet); lastSent = packet; }
    }
    private static long nextToken() {
        long value; do { value = TOKENS.nextLong() & Long.MAX_VALUE; } while (value == 0); return value;
    }
}
