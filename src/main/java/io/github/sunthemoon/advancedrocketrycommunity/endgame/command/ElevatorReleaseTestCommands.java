package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPairs;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * C13 packaged-server hooks for the space elevator's forced-stop checks (ADR-059 S2), registered only with
 * {@code -Dadvancedrocketrycommunity.releaseTestHooks=true}. They build a station orbiting Earth with its terminal,
 * an anchor of the same owner and the rider's membership, bind the pair as an operator, put a test rider with an
 * embedded connection on an endpoint's platform and request a ride, and report the rider and the pending rides on
 * one line. Every line they print starts with {@code ARCE_RELEASE_TEST}.
 */
public final class ElevatorReleaseTestCommands {
    private static final String RIDER_NAME = "arceRider";
    private final EndgameService service;

    public ElevatorReleaseTestCommands(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("elevator")
                                .then(Commands.literal("build")
                                        .then(Commands.argument("anchor", BlockPosArgument.blockPos())
                                                .then(Commands.argument("owner", UuidArgument.uuid())
                                                        .then(Commands.argument("rider", UuidArgument.uuid())
                                                                .executes(this::build)))))
                                .then(Commands.literal("bind").then(Commands.argument("station", UuidArgument.uuid())
                                        .executes(this::bind)))
                                .then(Commands.literal("ride").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("rider", UuidArgument.uuid()).executes(this::ride))))
                                .then(Commands.literal("state").then(Commands.argument("rider", UuidArgument.uuid())
                                        .executes(this::state)))))));
    }

    /** A station orbiting Earth with its terminal (chunk forced), and the anchor at the given Overworld position. */
    private int build(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        BlockPos anchorPos = BlockPosArgument.getBlockPos(context, "anchor");
        UUID owner = UuidArgument.getUuid(context, "owner");
        UUID rider = UuidArgument.getUuid(context, "rider");
        UUID stationId = UUID.randomUUID();
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        stations.reserve(stationId, owner, "Release elevator", CelestialIds.EARTH_ID, overworld.getGameTime());
        StationState station = stations.commit(stationId);
        stations.addMember(stationId, rider);
        stations.flush(server);
        BlockPos terminalPos = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.landingPad().z() + 6);
        space.setChunkForced(terminalPos.getX() >> 4, terminalPos.getZ() >> 4, true);
        space.setBlockAndUpdate(terminalPos, ModBlocks.ELEVATOR_TERMINAL.get().defaultBlockState());
        clearAbove(space, terminalPos);
        ElevatorTerminalBlockEntity terminal = (ElevatorTerminalBlockEntity) space.getBlockEntity(terminalPos);
        terminal.assignOwner(owner);
        terminal.storage().energy().set(ElevatorEndpointBlockEntity.ENERGY_CAPACITY);
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                overworld.setBlockAndUpdate(anchorPos.offset(x, -1, z), casing);
                boolean ring = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                overworld.setBlockAndUpdate(anchorPos.offset(x, 0, z), ring ? Blocks.IRON_BLOCK.defaultBlockState()
                        : casing);
            }
        }
        overworld.setBlockAndUpdate(anchorPos, ModBlocks.ELEVATOR_ANCHOR.get().defaultBlockState());
        clearAbove(overworld, anchorPos);
        ElevatorAnchorBlockEntity anchor = (ElevatorAnchorBlockEntity) overworld.getBlockEntity(anchorPos);
        anchor.assignOwner(owner);
        anchor.storage().energy().set(ElevatorEndpointBlockEntity.ENERGY_CAPACITY);
        return report(context, "elevator built station=" + stationId + " anchor=" + anchor.deviceId().orElseThrow()
                + " terminal=" + terminal.deviceId().orElseThrow() + " anchor_pos=" + anchorPos.toShortString()
                + " terminal_pos=" + terminalPos.toShortString());
    }

    private static void clearAbove(ServerLevel level, BlockPos endpoint) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(endpoint.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** An operator's bind of the station's terminal to the anchor named by the build (both must be registered). */
    private int bind(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        UUID stationId = UuidArgument.getUuid(context, "station");
        Optional<StationState> station = StationRegistrySavedData.get(server).find(stationId);
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (station.isEmpty() || devices.isEmpty() || service.root().isEmpty()) {
            return report(context, "elevator bind unavailable");
        }
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        BlockPos terminalPos = new BlockPos(station.get().landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.get().landingPad().z() + 6);
        if (!(space.getBlockEntity(terminalPos) instanceof ElevatorTerminalBlockEntity terminal)) {
            return report(context, "elevator bind no_terminal");
        }
        Optional<UUID> anchor = service.root().get().endpoints().stream()
                .filter(record -> record.kind().equals(ElevatorAnchorBlockEntity.KIND)
                        && record.owner().equals(station.get().ownerId()))
                .map(EndpointRecord::id).findFirst();
        if (anchor.isEmpty()) {
            return report(context, "elevator bind no_anchor");
        }
        String code = ElevatorPairs.bind(server, service, devices.get(), terminal, anchor.get(),
                station.get().ownerId(), true, server.overworld().getGameTime()).code().name();
        return report(context, "elevator bind code=" + code + " pair=" + service.root().get().pairs()
                .forStation(stationId).map(pair -> pair.pairId().toString()).orElse("-"));
    }

    /**
     * Puts the rider on the platform of the endpoint at {@code pos} in the source's Level (joining it with an embedded
     * connection when it is offline) and requests a ride.
     */
    private int ride(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        UUID riderId = UuidArgument.getUuid(context, "rider");
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (devices.isEmpty() || !(level.getBlockEntity(pos) instanceof ElevatorEndpointBlockEntity endpoint)) {
            return report(context, "elevator ride missing");
        }
        ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
        if (rider == null) {
            rider = new ServerPlayer(server, level, new GameProfile(riderId, RIDER_NAME));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, rider);
        }
        Vec3 at = ElevatorEndpointBlockEntity.arrival(pos);
        rider.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        String code = devices.get().elevatorRides().request(rider, endpoint, service, devices.get()).code().name();
        return report(context, "elevator ride code=" + code + " " + describe(server, riderId, devices.get()));
    }

    private int state(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        return report(context, "elevator state " + (devices.isEmpty() ? "unavailable"
                : describe(server, UuidArgument.getUuid(context, "rider"), devices.get())));
    }

    private static String describe(MinecraftServer server, UUID riderId, EndgameDevices devices) {
        ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
        ElevatorRides rides = devices.elevatorRides();
        String where = rider == null ? "rider=offline" : "rider=" + rider.level().dimension().location() + " at="
                + rider.blockPosition().toShortString();
        return where + " pending=" + rides.pending(riderId).isPresent() + " rides=" + rides.size() + " tickets="
                + rides.tickets();
    }

    private static int report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal("ARCE_RELEASE_TEST " + line), false);
        return 1;
    }
}
