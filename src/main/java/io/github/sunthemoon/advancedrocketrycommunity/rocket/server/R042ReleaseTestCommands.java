package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.R042AdmissionProbe;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * W332's gated native probes for R-042 (W332-DESIGN section 2, item 2), {@code arce bay release-test r042 ...},
 * registered with the other bay release-test commands only when the release-test property is set. Each entry passes the
 * complete {@link EscrowTestEndpoints} gate before it reads anything, and none is called by production code:
 * <ul>
 *   <li>{@code plain <pos>}: an ordinary, fully fuelled rocket without a bay, not launched;</li>
 *   <li>{@code insert <pos>}: one real package insertion into a fresh, empty bay through its item handler;</li>
 *   <li>{@code cell}, {@code attempt}, {@code report}: {@link R042AdmissionProbe};</li>
 *   <li>{@code rider board|login|sneak|state <logical>}: W254's facility rider on an ordinary rocket.</li>
 * </ul>
 */
final class R042ReleaseTestCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private R042ReleaseTestCommands() { }

    static void register(RegisterCommandsEvent event) {
        var r042 = Commands.literal("r042")
                .then(Commands.literal("plain").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                    // The gate decides before the position is resolved against a Level (W128R-R1).
                    EscrowTestEndpoints.requireEnabled(context.getSource().getServer());
                    return plain(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"));
                })))
                .then(Commands.literal("insert").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                    EscrowTestEndpoints.requireEnabled(context.getSource().getServer());
                    return insert(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"));
                })))
                .then(Commands.literal("cell").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("owner", UuidArgument.uuid()).executes(context -> reported(context.getSource(),
                                R042AdmissionProbe.cell(context.getSource().getServer(), StringArgumentType.getString(context, "name"),
                                        UuidArgument.getUuid(context, "owner")))))))
                .then(Commands.literal("attempt").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("kinds", StringArgumentType.word()).executes(context -> reported(context.getSource(),
                                R042AdmissionProbe.attempt(context.getSource().getServer(), StringArgumentType.getString(context, "name"),
                                        StringArgumentType.getString(context, "kinds")))))))
                .then(Commands.literal("report").then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> reported(context.getSource(), R042AdmissionProbe.report(context.getSource().getServer(),
                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("rider").then(Commands.argument("action", StringArgumentType.word())
                        .then(Commands.argument("logical", UuidArgument.uuid()).executes(context -> rider(context.getSource(),
                                StringArgumentType.getString(context, "action"), UuidArgument.getUuid(context, "logical"))))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2)).then(r042))));
    }

    private static int reported(CommandSourceStack source, int result) {
        source.sendSuccess(() -> Component.literal("R-042 probe reported"), false);
        return result;
    }

    /** An ordinary rocket of the fixture owner: motor, seat, guidance computer and tank, scanned, removed, fully fuelled. */
    static int plain(CommandSourceStack source, BlockPos origin) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        ServerLevel level = server.overworld();
        if (!level.isPositionEntityTicking(origin)) {
            LOGGER.info("ARCE_RELEASE_R042_WAIT origin={}", origin.toShortString());
            return 0;
        }
        var blocks = List.of(origin, origin.above(), origin.above(2), origin.west());
        level.setBlock(origin, ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        var scan = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(), new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), UUID.randomUUID(),
                level.getGameTime());
        var snapshot = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK).snapshot().orElseThrow();
        for (BlockPos position : blocks) {
            level.removeBlockEntity(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        RocketEntity rocket = ModEntities.ROCKET.get().create(level);
        UUID logical = UUID.randomUUID();
        rocket.initialize(snapshot, logical, BayReleaseTestCommands.OWNER);
        if (!level.addFreshEntity(rocket)) { throw new IllegalStateException("The plain R-042 rocket was not added"); }
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), level.getGameTime()));
        LOGGER.info("ARCE_RELEASE_R042_PLAIN logical={} rocket={} owner={} fuel={}", logical, rocket.getUUID(),
                BayReleaseTestCommands.OWNER, rocket.flightData().orElseThrow().fuel().amount());
        source.sendSuccess(() -> Component.literal("Plain R-042 rocket added"), false);
        return 1;
    }

    /** One real insertion of a fresh data package into an empty bay of the fixture owner; the bay is then removed. */
    static int insert(CommandSourceStack source, BlockPos at) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        ServerLevel level = server.overworld();
        if (!level.isPositionEntityTicking(at)) {
            LOGGER.info("ARCE_RELEASE_R042_WAIT origin={}", at.toShortString());
            return 0;
        }
        level.setBlock(at, ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState(), Block.UPDATE_ALL);
        try {
            var bay = (SatelliteBayBlockEntity) level.getBlockEntity(at);
            CompoundTag outer = new CompoundTag();
            outer.put(SatelliteBayRoot.KEY, SatelliteBayRoot.encode(SatelliteBayRoot.Contents.empty(BayReleaseTestCommands.OWNER)));
            bay.load(outer);
            var pkg = BayDeploymentGameTestSupport.dataPackage(BayReleaseTestCommands.OWNER);
            UUID satellite = BayDeploymentGameTestSupport.satellite(pkg);
            boolean blocked = BayReceipts.blocksInsertion(server, satellite);
            boolean valid = bay.itemHandler().isItemValid(0, pkg);
            var remainder = bay.itemHandler().insertItem(0, pkg.copy(), false);
            boolean held = !bay.itemHandler().getStackInSlot(0).isEmpty();
            LOGGER.info("ARCE_RELEASE_R042_INSERT satellite={} blocked={} valid={} remainder={} held={}", satellite, blocked, valid,
                    remainder.getCount(), held);
            source.sendSuccess(() -> Component.literal("R-042 insertion reported"), false);
            return held ? 1 : 0;
        } finally {
            // No removal callbacks and no drops: the inserted package is fixture data.
            level.removeBlockEntity(at);
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** W254's facility rider on an ordinary rocket: board (assign the seat and mount), log in, sneak, or report. */
    static int rider(CommandSourceStack source, String action, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        RocketEntity rocket = rocket(server, logical);
        ServerPlayer player = server.getPlayerList().getPlayer(BayPassengerExitReleaseTest.RIDER);
        switch (action) {
            case "board" -> {
                if (rocket == null) { throw new IllegalStateException("The R-042 rocket is not loaded"); }
                player = ReleaseTestPlayers.place(server, BayPassengerExitReleaseTest.RIDER, BayPassengerExitReleaseTest.RIDER_NAME,
                        (ServerLevel) rocket.level(), Vec3.atBottomCenterOf(rocket.blockPosition()).add(0, 0, 2));
                player.setGameMode(GameType.SURVIVAL);
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withPassengers(flight.passengers().assign(player.getUUID()).orElseThrow()));
                player.startRiding(rocket, true);
            }
            case "login" -> player = ReleaseTestPlayers.login(server, BayPassengerExitReleaseTest.RIDER,
                    BayPassengerExitReleaseTest.RIDER_NAME, server.overworld());
            case "sneak" -> {
                if (player == null) { throw new IllegalStateException("The R-042 rider is offline"); }
                // A riding client's sneak: the passenger tick of the next server tick detaches it (Player.rideTick).
                player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(0.0F, 0.0F, false, true));
            }
            case "state" -> { }
            default -> throw new IllegalArgumentException("Unknown R-042 rider action " + action);
        }
        LOGGER.info("ARCE_RELEASE_R042_RIDER action={} logical={} rider={} mounted={} vehicle={} seat={} rocket={}", action, logical,
                player == null ? "offline" : "online", player != null && rocket != null && player.getVehicle() == rocket,
                player == null || player.getVehicle() == null ? "NONE" : player.getVehicle().getUUID(),
                rocket == null ? -1 : BayPassengerExitReleaseTest.seat(rocket.flightData().map(data -> data.passengers()).orElse(null)),
                rocket == null ? "NONE" : rocket.getUUID());
        source.sendSuccess(() -> Component.literal("R-042 rider reported"), false);
        return 1;
    }

    @Nullable private static RocketEntity rocket(MinecraftServer server, UUID logical) {
        for (ServerLevel level : server.getAllLevels()) {
            for (RocketEntity candidate : level.getEntities(ModEntities.ROCKET.get(),
                    entity -> entity.assemblyTransactionId().filter(logical::equals).isPresent())) { return candidate; }
        }
        return null;
    }
}
