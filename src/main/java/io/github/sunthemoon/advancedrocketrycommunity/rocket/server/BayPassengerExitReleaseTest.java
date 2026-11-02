package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * W254 (W230-S3) gated native fixture for {@code scripts/run_v180_bay_exit_cuts.py}: OP-only, behind the complete
 * release-test gate ({@link EscrowTestEndpoints#requireEnabled}) on a dedicated server whose players are all
 * facility-owned. A survival test rider boards the bay forced-stop fixture before its launch; these commands then make one
 * physical exit the way a player does (vanilla dismount, the sneak input packet or the held menu LEAVE), place a stone pad,
 * drive the client's move packets, make one same-Level teleport or one trusted passenger move, and report the exit state.
 * They change no exit decision, budget, deadline, admission property, receipt, journal or seat.
 */
final class BayPassengerExitReleaseTest {
    static final UUID RIDER = UUID.fromString("00000000-0000-4000-8000-000000254001");
    static final String RIDER_NAME = "W254Rider";
    /** A pad below the rider puts the landing this many blocks under the exit, inside W230's eight-block radius. */
    static final int LANDING_DROP = 10;
    private static final Logger LOGGER = LogUtils.getLogger();

    private BayPassengerExitReleaseTest() { }

    static void register(RegisterCommandsEvent event) {
        var exit = Commands.literal("exit")
                .then(Commands.literal("fixture").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                    // The gate decides before the position is resolved against a Level, as the bay fixture does (W128R-R1).
                    EscrowTestEndpoints.requireEnabled(context.getSource().getServer());
                    return fixture(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"));
                })))
                .then(logical("ready", (view, context) -> ready(view)))
                .then(Commands.literal("leave").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(context -> leave(context, false))
                                .then(Commands.literal("freeze").executes(context -> leave(context, true))))))
                .then(Commands.literal("pad").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("kind", StringArgumentType.word()).executes(context ->
                                pad(view(context), StringArgumentType.getString(context, "kind"))))))
                .then(Commands.literal("descend").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("steps", IntegerArgumentType.integer(0, BayPassengerExitReleaseDrive.MAX_PACKETS))
                                .executes(context -> descend(view(context), IntegerArgumentType.getInteger(context, "steps"))))))
                .then(logical("teleport", (view, context) -> teleport(view)))
                .then(logical("move", (view, context) -> move(view)))
                .then(logical("state", (view, context) -> state(view, "STATE")))
                .then(logical("login", (view, context) -> login(view)));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2)).then(exit))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> logical(String name,
            BiFunction<View, CommandContext<CommandSourceStack>, Integer> action) {
        return Commands.literal(name).then(Commands.argument("logical", UuidArgument.uuid())
                .executes(context -> action.apply(view(context), context)));
    }

    /** Every entry passes the gate before it reads anything. */
    private static View view(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        EscrowTestEndpoints.requireEnabled(server);
        BayPassengerExitReleaseDrive.listen();
        return View.of(server, UuidArgument.getUuid(context, "logical"));
    }

    /** The bay forced-stop fixture with a facility-owned survival rider assigned the seat and aboard before the launch. */
    private static int fixture(CommandSourceStack source, BlockPos origin) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        BayPassengerExitReleaseDrive.listen();
        ServerLevel level = server.overworld();
        if (!level.isPositionEntityTicking(origin)) {
            LOGGER.info("ARCE_RELEASE_BAY_FIXTURE_WAIT origin={}", origin.toShortString());
            return 0;
        }
        ServerPlayer rider = ReleaseTestPlayers.place(server, RIDER, RIDER_NAME, level, Vec3.atBottomCenterOf(origin).add(0, 0, 2));
        rider.setGameMode(GameType.SURVIVAL);
        int launched = BayReleaseTestCommands.fixture(source, origin, false, rider);
        var rocket = rider.getVehicle() instanceof RocketEntity boarded ? boarded : null;
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_FIXTURE rider={} name={} mounted={} seat={} mode={}", RIDER, RIDER_NAME, rocket != null,
                rocket == null ? -1 : seat(rocket.flightData().map(data -> data.passengers()).orElse(null)),
                rider.gameMode.getGameModeForPlayer().getName());
        return launched;
    }

    /** True once the in-session hold gives the rider a checked exit: claim, acknowledged T, lease and both observers. */
    private static int ready(View view) {
        boolean ready = view.exits() != null && view.rider() != null && view.rocket() != null
                && view.rider().getVehicle() == view.rocket() && view.record() != null && view.claim() != null
                && view.journal().actionable(view.logical()) && view.lease() && view.counter() && view.landing();
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_READY logical={} ready={} lease={} {}", view.logical(), ready, view.lease(), view.describe());
        return ready ? 1 : 0;
    }

    private static int leave(CommandContext<CommandSourceStack> context, boolean freeze) {
        View view = view(context);
        String mode = StringArgumentType.getString(context, "mode").toLowerCase(Locale.ROOT);
        ServerPlayer player = view.rider();
        RocketEntity rocket = view.rocket();
        if (player == null || rocket == null || player.getVehicle() != rocket) { throw new IllegalStateException("The rider is not aboard"); }
        var before = BayPassengerExitReleaseDrive.Measure.take(view);
        switch (mode) {
            case "sneak" -> player.stopRiding();
            case "menu" -> {
                RocketRuntime.openFlightMenu(player, rocket);
                RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.LEAVE,
                        view.record().sourceFlightData().currentTarget().orElseThrow(), UUID.randomUUID());
            }
            case "input" -> {
                if (freeze) { throw new IllegalArgumentException("The input exit happens later in this tick"); }
                // A riding client's sneak: the passenger tick of this server tick detaches it (Player.rideTick).
                player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(0.0F, 0.0F, false, true));
                BayPassengerExitReleaseDrive.awaitInput(view, before);
                return 1;
            }
            default -> throw new IllegalArgumentException("Unknown exit mode " + mode);
        }
        var after = View.of(view.server(), view.logical());
        BayPassengerExitReleaseDrive.left(after, mode, before, BayPassengerExitReleaseDrive.Measure.take(after), freeze);
        return 1;
    }

    /** A 3x3 stone pad: under T's source origin ({@code origin}), or {@value #LANDING_DROP} blocks under the rider. */
    private static int pad(View view, String kind) {
        if (view.record() == null || view.rider() == null) { throw new IllegalStateException("No transfer or rider"); }
        var origin = view.record().sourceSnapshot().sourceOrigin();
        BlockPos center = switch (kind) {
            case "origin" -> new BlockPos(origin.x(), origin.y() - 1, origin.z());
            case "below" -> BlockPos.containing(view.rider().getX(), Math.floor(view.rider().getY()) - LANDING_DROP - 1, view.rider().getZ());
            default -> throw new IllegalArgumentException("Unknown pad " + kind);
        };
        ServerLevel level = view.server().overworld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) { level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState()); }
        }
        if (kind.equals("below")) { BayPassengerExitReleaseDrive.landAt(view.rider(), view.logical(), center.getY() + 1); }
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_PAD logical={} kind={} x={} top={} z={} {}", view.logical(), kind, center.getX(),
                center.getY() + 1, center.getZ(), view.describe());
        return 1;
    }

    private static int descend(View view, int steps) {
        if (view.rider() == null || view.rider().getVehicle() != null) { throw new IllegalStateException("The rider is not detached"); }
        BayPassengerExitReleaseDrive.descend(view.rider(), view.logical(), steps);
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_DESCEND logical={} steps={} {}", view.logical(), steps, view.describe());
        return 1;
    }

    /** One same-Level connection teleport a tenth of a block east: inside W230's radius, so only the counter observes it. */
    private static int teleport(View view) {
        ServerPlayer player = view.rider();
        if (player == null || view.exits() == null) { throw new IllegalStateException("No rider"); }
        int before = counter(view);
        player.connection.teleport(player.getX() + .1D, player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_TELEPORTED logical={} counter={}>{} {}", view.logical(), before, counter(view), view.describe());
        return 1;
    }

    /** One real trusted passenger move toward the held source; the hold refuses it, after the move scope revoked the exit. */
    private static int move(View view) {
        ServerPlayer player = view.rider();
        if (player == null || view.rocket() == null || view.record() == null) { throw new IllegalStateException("No rider or source"); }
        int before = counter(view);
        double y = player.getY();
        RocketTransferEntities.movePassenger(player, view.rocket(), view.record().sourceSnapshot().sourceOrigin());
        var after = View.of(view.server(), view.logical());
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_MOVED logical={} remounted={} counter={}>{} riderY={}>{} {}", view.logical(),
                player.getVehicle() != null, before, counter(after), y, player.getY(), after.describe());
        return 1;
    }

    private static int state(View view, String label) {
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_{} logical={} {}", label, view.logical(), view.describe());
        return 1;
    }

    /** A restart's login of the saved rider through the facility, without a fixture teleport or game-mode change. */
    private static int login(View view) {
        ReleaseTestPlayers.login(view.server(), RIDER, RIDER_NAME, view.server().overworld());
        return state(View.of(view.server(), view.logical()), "LOGIN");
    }

    private static int counter(View view) {
        var generation = view.exits() == null || view.rider() == null ? null : view.exits().motion.generation(view.rider());
        return generation == null ? -1 : generation.counter();
    }

    static int seat(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest manifest) {
        return manifest == null ? -1 : manifest.assignment(RIDER).map(seat -> seat.seatIndex()).orElse(-1);
    }

    /** Read-only view of one logical rocket, its T, claim, rider and exit state on the server thread. */
    record View(MinecraftServer server, UUID logical, RocketTransferSavedData journal, RocketTransferRecord record, BayClaim claim,
                BayPassengerExitService exits, ServerPlayer rider, RocketEntity rocket) {
        static View of(MinecraftServer server, UUID logical) {
            var journal = RocketTransferSavedData.existing(server);
            var record = journal == null ? null : journal.findByLogicalRocket(logical).orElse(null);
            var claim = RocketRuntime.installedBays().flatMap(bays -> bays.claimForTests(logical)).orElse(null);
            RocketEntity rocket = null;
            for (ServerLevel level : server.getAllLevels()) {
                for (RocketEntity candidate : level.getEntities(ModEntities.ROCKET.get(),
                        entity -> entity.assemblyTransactionId().filter(logical::equals).isPresent())) { rocket = candidate; }
            }
            return new View(server, logical, journal, record, claim, RocketRuntime.passengerExits().orElse(null),
                    server.getPlayerList().getPlayer(RIDER), rocket);
        }
        boolean lease() {
            return record != null && RocketRuntime.installedBays().map(bays -> bays.exitLease(record).getAsBoolean()).orElse(false);
        }
        boolean counter() { return exits != null && rider != null && exits.motion.generation(rider) != null; }
        boolean landing() { return exits != null && exits.landing.available(); }

        /** The fields every report shares: the RAM exit state, the rider, T's seat and the rocket's seat. */
        String describe() {
            var slot = exits == null || rider == null ? null : exits.state.live.get(rider);
            var history = exits == null || record == null ? null : exits.state.history(RIDER, record.transferId());
            String offer = slot == null || slot.descent == null ? "none" : slot.descent.closed ? "closed" : "live";
            return String.format(Locale.ROOT, "tick=%d service=%s live=%d refusals=%d histories=%d rider=%s mounted=%s y=%s "
                            + "health=%s gameMode=%s slot=%s ordinal=%d offer=%s accepted=%s history=%s first=%d deadline=%d transfer=%s seat=%d "
                            + "rocket=%s rocketY=%s rocketSeat=%d claim=%s hold=%s reason=%s observer=%s landing=%s",
                    server.getTickCount(), exits != null, exits == null ? -1 : exits.state.live.size(),
                    exits == null ? -1 : exits.state.refusals.size(), exits == null ? -1 : exits.state.histories(),
                    rider == null ? "offline" : "online", rider != null && rocket != null && rider.getVehicle() == rocket,
                    rider == null ? "NONE" : rider.getY(), rider == null ? "NONE" : rider.getHealth(),
                    rider == null ? "NONE" : rider.gameMode.getGameModeForPlayer().getName(),
                    slot == null ? "none" : slot.result == null ? "pending" : slot.result.status().name(),
                    slot == null || slot.result == null ? -1 : slot.result.ordinal(), offer,
                    slot != null && slot.descent != null && slot.descent.corrections.accepted,
                    history == null ? "none" : history.issued() ? "issued" : "closed",
                    history == null ? -1 : history.firstTick(), history == null ? -1 : history.deadline(),
                    record == null ? "NONE" : record.phase().name(), record == null ? -1 : seat(record.sourceFlightData().passengers()),
                    rocket == null ? "NONE" : rocket.getUUID(), rocket == null ? "NONE" : rocket.getY(),
                    rocket == null ? -1 : seat(rocket.flightData().map(data -> data.passengers()).orElse(null)),
                    claim == null ? "NONE" : claim.stage().name(), claim == null ? "NONE" : claim.hold().name(),
                    claim == null ? "NONE" : claim.reason(), counter(), landing());
        }
    }
}
