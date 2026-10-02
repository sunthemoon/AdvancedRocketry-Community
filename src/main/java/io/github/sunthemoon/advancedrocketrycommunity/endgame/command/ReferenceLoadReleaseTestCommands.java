package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPairs;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillStorage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

/**
 * C13 packaged-server hooks for the ADR-054 section 7 endgame reference load, registered only with
 * {@code -Dadvancedrocketrycommunity.releaseTestHooks=true}. {@code refload build <x> <z>} builds it
 * ({@link ReferenceLoadFixture}). {@code refload drive on} starts a tick driver: it starts the drills and fields through
 * their owners' menus as connected players (at most one click per owner per 11 ticks), binds the elevator pairs as an
 * operator, launches 16 railguns every 20 ticks, requests a ride every 15 seconds, and keeps buffers, fuel and outputs
 * where every device keeps working. {@code refload status} reports it on one line. Every line printed starts with
 * {@code ARCE_RELEASE_TEST}.
 */
public final class ReferenceLoadReleaseTestCommands {
    private static final int CLICK_SPACING_TICKS = 11;
    private static final int RIDE_INTERVAL_TICKS = 300;

    private final EndgameService service;
    private final ReferenceLoadFixture fixture;
    private final ReferenceLoadFixture.Load load;
    private boolean listening;

    public ReferenceLoadReleaseTestCommands(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
        this.fixture = new ReferenceLoadFixture(service);
        this.load = fixture.load;
    }

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) {
            return;
        }
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(this::tick);
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("refload")
                                .then(Commands.literal("build")
                                        .then(Commands.argument("x", IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                                .then(Commands.argument("z",
                                                                IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                                        .executes(this::build))))
                                .then(Commands.literal("drive")
                                        .then(Commands.literal("on").executes(context -> drive(context, true)))
                                        .then(Commands.literal("off").executes(context -> drive(context, false))))
                                .then(Commands.literal("status").executes(this::status))))));
    }

    private int build(CommandContext<CommandSourceStack> context) {
        if (load.built) {
            return report(context, "refload build already");
        }
        return report(context, fixture.build(context.getSource().getServer(),
                IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z")));
    }

    // ---- Driver -----------------------------------------------------------------------------------------------

    private int drive(CommandContext<CommandSourceStack> context, boolean on) {
        if (!load.built) {
            return report(context, "refload drive unbuilt");
        }
        load.driving = on;
        return report(context, "refload drive " + (on ? "on" : "off"));
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !load.driving || event.getServer() == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        ReleaseTestPlayers.pump(server);
        clickDue(server, load.drills, now, this::drillClick);
        clickDue(server, load.fields, now, this::fieldClick);
        if (now % 20 == 0) {
            topUp();
            launchRailguns(now);
        }
        if (now % 100 == 0) {
            bindElevators(server, now);
        }
        if (now % RIDE_INTERVAL_TICKS == 0) {
            ride(server);
        }
    }

    /**
     * Each owner clicks at most once per 11 ticks, standing next to the device, as a connected player. A target
     * selection is not a state change, so the link it is for follows in the same tick.
     */
    private void clickDue(MinecraftServer server, List<ReferenceLoadFixture.Device> devices, long now, Clicker clicker) {
        for (ReferenceLoadFixture.Device device : devices) {
            if (device.steps.isEmpty() || load.nextClick.getOrDefault(device.owner, 0L) > now) {
                continue;
            }
            if (device.steps.get(0) == OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT && !markerReady(device.owner)) {
                continue;
            }
            ServerPlayer player = ReleaseTestPlayers.place(server, device.owner, "arceOwner"
                    + device.owner.toString().substring(0, 6), device.level, Vec3.atCenterOf(device.pos.south(3)));
            int step = device.steps.get(0);
            if (clicker.click(device, player, step)) {
                device.steps.remove(0);
                if (step == OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT && !device.steps.isEmpty()
                        && clicker.click(device, player, device.steps.get(0))) {
                    device.steps.remove(0);
                }
            }
            load.nextClick.put(device.owner, now + CLICK_SPACING_TICKS);
        }
    }

    @FunctionalInterface
    private interface Clicker {
        boolean click(ReferenceLoadFixture.Device device, ServerPlayer player, int button);
    }

    private boolean drillClick(ReferenceLoadFixture.Device device, ServerPlayer player, int button) {
        if (!(device.level.getBlockEntity(device.pos) instanceof OrbitalLaserDrillBlockEntity drill)) {
            return false;
        }
        if (device.menu == null) {
            device.menu = new OrbitalLaserDrillMenu(1, player.getInventory(), drill);
        }
        return device.menu.clickMenuButton(player, button);
    }

    private boolean fieldClick(ReferenceLoadFixture.Device device, ServerPlayer player, int button) {
        if (!(device.level.getBlockEntity(device.pos) instanceof GravityFieldBlockEntity field)) {
            return false;
        }
        if (device.menu == null) {
            device.menu = new GravityFieldMenu(1, player.getInventory(), field);
        }
        return device.menu.clickMenuButton(player, button);
    }

    private boolean markerReady(UUID owner) {
        return load.markers.stream().filter(marker -> marker.owner.equals(owner)).allMatch(marker ->
                marker.level.getBlockEntity(marker.pos) instanceof LaserTargetBlockEntity target
                        && target.endpointActive());
    }

    /** Keeps every device working: drill energy and free output, field energy, generator fuel and room. */
    private void topUp() {
        for (ReferenceLoadFixture.Device device : load.drills) {
            if (device.level.getBlockEntity(device.pos) instanceof OrbitalLaserDrillBlockEntity drill) {
                drill.storage().setEnergy(LaserDrillStorage.ENERGY_CAPACITY);
                for (int slot = 0; slot < drill.storage().output().getSlots(); slot++) {
                    drill.storage().output().extractItem(slot, 64, false);
                }
            }
        }
        for (ReferenceLoadFixture.Device marker : load.markers) {
            if (marker.level.getBlockEntity(marker.pos) instanceof LaserTargetBlockEntity target) {
                for (int slot = 0; slot < target.buffer().getSlots(); slot++) {
                    target.buffer().extractItem(slot, 64, false);
                }
            }
        }
        for (ReferenceLoadFixture.Device device : load.fields) {
            if (device.level.getBlockEntity(device.pos) instanceof GravityFieldBlockEntity field) {
                field.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
            }
        }
        for (ReferenceLoadFixture.Device device : load.generators) {
            if (device.level.getBlockEntity(device.pos) instanceof BlackHoleGeneratorBlockEntity generator) {
                generator.fuel().insertItem(0, new ItemStack(Items.STICK, 64), false);
                generator.setEnergyForTest(0);
            }
        }
    }

    /** Sixteen launches every 20 ticks: each source gets a stack and launches when its cadence allows. */
    private void launchRailguns(long now) {
        for (int r = 0; r < load.railgunSources.size(); r++) {
            ReferenceLoadFixture.Device source = load.railgunSources.get(r);
            ReferenceLoadFixture.Device destination = load.railgunDestinations.get(r);
            if (source.level.getBlockEntity(source.pos) instanceof RailgunBlockEntity railgun
                    && destination.level.getBlockEntity(destination.pos) instanceof RailgunBlockEntity target) {
                railgun.storage().energy().set(RailgunBlockEntity.ENERGY_CAPACITY);
                if (railgun.storage().input().getStackInSlot(0).isEmpty()) {
                    railgun.storage().input().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                }
                for (int slot = 0; slot < target.storage().receive().getSlots(); slot++) {
                    target.storage().receive().extractItem(slot, 64, false);
                }
                railgun.requestLaunch(source.owner);
                load.launches++;
            }
        }
    }

    /** Operator binds of every pair whose two endpoints are registered (exempt from the player spacing). */
    private void bindElevators(MinecraftServer server, long now) {
        EndgameDevices devices = EndgameRuntime.devices().orElse(null);
        if (devices == null || service.root().isEmpty()) {
            return;
        }
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        for (ReferenceLoadFixture.Elevator elevator : load.elevators) {
            if (service.root().get().pairs().forStation(elevator.stationId()).isPresent()) {
                continue;
            }
            if (space.getBlockEntity(elevator.terminal()) instanceof ElevatorTerminalBlockEntity terminal
                    && server.overworld().getBlockEntity(elevator.anchor()) instanceof ElevatorAnchorBlockEntity anchor
                    && terminal.endpointActive() && anchor.endpointActive()) {
                ElevatorPairs.bind(server, service, devices, terminal, anchor.endpointId(), elevator.owner(), true, now);
            }
        }
    }

    /** One ride every 15 seconds, rotating over the bound pairs, alternately up and down for each rider. */
    private void ride(MinecraftServer server) {
        EndgameDevices devices = EndgameRuntime.devices().orElse(null);
        if (devices == null || load.elevators.isEmpty()) {
            return;
        }
        ReferenceLoadFixture.Elevator elevator = load.elevators.get(load.nextRide++ % load.elevators.size());
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        ServerPlayer rider = server.getPlayerList().getPlayer(elevator.rider());
        boolean up = rider == null || rider.level() != space;
        ServerLevel level = up ? server.overworld() : space;
        BlockPos departure = up ? elevator.anchor() : elevator.terminal();
        if (!(level.getBlockEntity(departure) instanceof ElevatorEndpointBlockEntity endpoint)) {
            return;
        }
        endpoint.storage().energy().set(ElevatorEndpointBlockEntity.ENERGY_CAPACITY);
        ServerPlayer placed = ReleaseTestPlayers.place(server, elevator.rider(), "arceRider"
                + elevator.rider().toString().substring(0, 6), level, ElevatorEndpointBlockEntity.arrival(departure));
        if (devices.elevatorRides().request(placed, endpoint, service, devices).code() == EndgameCode.RIDE_COUNTDOWN) {
            load.rides++;
        }
    }

    // ---- Status -----------------------------------------------------------------------------------------------

    private int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int running = count(load.drills, device -> device.level.getBlockEntity(device.pos)
                instanceof OrbitalLaserDrillBlockEntity drill && drill.running());
        int active = count(load.fields, device -> device.level.getBlockEntity(device.pos)
                instanceof GravityFieldBlockEntity field && field.active());
        int burning = count(load.generators, device -> device.level.getBlockEntity(device.pos)
                instanceof BlackHoleGeneratorBlockEntity generator && generator.status() == EndgameCode.GENERATING);
        int bound = service.root().map(root -> (int) load.elevators.stream()
                .filter(elevator -> root.pairs().forStation(elevator.stationId()).isPresent()).count()).orElse(0);
        String fieldCodes = load.fields.stream().map(device -> device.level.getBlockEntity(device.pos)
                instanceof GravityFieldBlockEntity field ? field.status().name() : "MISSING")
                .collect(java.util.stream.Collectors.groupingBy(code -> code, java.util.TreeMap::new,
                        java.util.stream.Collectors.counting())).toString().replace(" ", "");
        String drillCodes = load.drills.stream().map(device -> device.level.getBlockEntity(device.pos)
                instanceof OrbitalLaserDrillBlockEntity drill ? drill.status().name() : "MISSING")
                .collect(java.util.stream.Collectors.groupingBy(code -> code, java.util.TreeMap::new,
                        java.util.stream.Collectors.counting())).toString().replace(" ", "");
        double mspt = server.getAverageTickTime();
        return report(context, "refload status built=" + load.built + " driving=" + load.driving + " drills_running="
                + running + " fields_active=" + active + " generators_burning=" + burning + " elevators_bound=" + bound
                + " launches_requested=" + load.launches + " rides_requested=" + load.rides + " root_records="
                + service.root().map(root -> root.transits().size()).orElse(-1) + " root_endpoints="
                + service.root().map(root -> root.endpoints().size()).orElse(-1) + " root_tombstones="
                + service.root().map(root -> root.youngTombstones().size() + root.settledTombstones().size())
                .orElse(-1) + " accounted_bytes=" + service.root().map(root -> root.accountedBytes()).orElse(-1L)
                + " field_codes=" + fieldCodes + " drill_codes=" + drillCodes + " players=" + server.getPlayerCount()
                + " mspt=" + String.format(java.util.Locale.ROOT, "%.2f",
                mspt));
    }

    private static int count(List<ReferenceLoadFixture.Device> devices, Predicate<ReferenceLoadFixture.Device> test) {
        return (int) devices.stream().filter(test).count();
    }

    private static int report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal("ARCE_RELEASE_TEST " + line), false);
        return 1;
    }
}
