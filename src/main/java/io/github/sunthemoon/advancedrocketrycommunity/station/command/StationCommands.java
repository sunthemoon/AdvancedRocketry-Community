package io.github.sunthemoon.advancedrocketrycommunity.station.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationGravityService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Player membership flow and bounded permission-level-2 station recovery tools. */
public final class StationCommands {
    private static final UUID CONSOLE_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String STATION = "station_id";
    private final StationManager stations;

    public StationCommands(StationManager stations) {
        this.stations = Objects.requireNonNull(stations, "stations");
    }

    public void register(RegisterCommandsEvent event) {
        var station = Commands.literal("station")
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("invite")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::invite))))
                .then(Commands.literal("accept")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .executes(context -> answerInvitation(context, true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .executes(context -> answerInvitation(context, false))))
                .then(Commands.literal("remove")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::remove))))
                .then(Commands.literal("expand")
                        .executes(this::requestExpansion)
                        .then(Commands.literal("confirm")
                                .then(Commands.argument(STATION, UuidArgument.uuid())
                                        .executes(this::confirmExpansion))))
                .then(Commands.literal("gravity")
                        .then(Commands.argument("percent",
                                        IntegerArgumentType.integer(0, StationGravityService.MAX_PERCENT))
                                .executes(this::setGravity)))
                .then(Commands.literal("environment").executes(this::environment));
        var admin = Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("owner", UuidArgument.uuid())
                                .then(Commands.argument("orbit", ResourceLocationArgument.id())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(this::create)))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .executes(this::inspect)))
                .then(Commands.literal("dump").executes(this::dump))
                .then(Commands.literal("recover-reservations").executes(this::recover))
                .then(Commands.literal("transfer")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .then(Commands.argument("owner", UuidArgument.uuid())
                                        .executes(this::transfer))))
                .then(Commands.literal("delete")
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .then(Commands.argument("confirmation", StringArgumentType.word())
                                        .executes(this::delete))));
        station.then(admin);
        event.getDispatcher().register(Commands.literal("arce").then(station));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<StationState> all = stations.stations(source.getServer());
        if (source.getEntity() instanceof ServerPlayer player && !source.hasPermission(2)) {
            all = all.stream().filter(station -> station.ownerId().equals(player.getUUID())
                    || station.members().contains(player.getUUID())
                    || station.invitations().contains(player.getUUID())).toList();
        }
        List<StationState> visible = all;
        source.sendSuccess(() -> Component.literal("Stations: " + visible.size()), false);
        for (StationState station : visible) {
            source.sendSuccess(() -> summary(station), false);
        }
        return visible.size();
    }

    private int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID stationId = UuidArgument.getUuid(context, STATION);
        return mutate(context, () -> stations.invite(
                context.getSource().getServer(),
                actor.getUUID(),
                context.getSource().hasPermission(2),
                stationId,
                target.getUUID()
        ), "Invitation recorded for " + target.getScoreboardName());
    }

    private int answerInvitation(CommandContext<CommandSourceStack> context, boolean accept)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID stationId = UuidArgument.getUuid(context, STATION);
        return mutate(
                context,
                () -> accept
                        ? stations.accept(context.getSource().getServer(), stationId, player.getUUID())
                        : stations.decline(context.getSource().getServer(), stationId, player.getUUID()),
                accept ? "Station invitation accepted" : "Station invitation declined"
        );
    }

    private int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        UUID stationId = UuidArgument.getUuid(context, STATION);
        return mutate(context, () -> stations.removeMember(
                context.getSource().getServer(),
                actor.getUUID(),
                context.getSource().hasPermission(2),
                stationId,
                target.getUUID()
        ), "Member removed immediately");
    }

    private int requestExpansion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationManagementResult result = stations.requestExpansion(player, issuedBy(context.getSource(), player));
        if (result.code() != StationManagementCode.ISSUED) {
            return rejected(context, "expansion", result);
        }
        StationState station = result.station().orElseThrow();
        long seconds = StationLimits.EXPANSION_CONFIRMATION_TICKS / 20L;
        context.getSource().sendSuccess(() -> Component.literal(
                "Expand " + station.name() + " id=" + station.stationId()
                        + " from region " + bounds(station.region())
                        + " to " + bounds(station.withExpandedRegion().region()) + "."
                        + " Any blocks already in the added area become part of this station:"
                        + " members can then build there and they are not checked, moved or removed."
                        + " This cannot be undone. To confirm within " + seconds + " seconds, run"
                        + " /arce station expand confirm " + station.stationId()
        ), false);
        return 1;
    }

    private int confirmExpansion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationManagementResult result = stations.confirmExpansion(player, issuedBy(context.getSource(), player),
                UuidArgument.getUuid(context, STATION));
        if (result.code() != StationManagementCode.EXPANDED) {
            return rejected(context, "expansion", result);
        }
        StationState station = result.station().orElseThrow();
        context.getSource().sendSuccess(() -> Component.literal(
                "Station expanded; station=" + station.stationId() + " region=" + bounds(station.region())
        ), true);
        return 1;
    }

    private int setGravity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int percent = IntegerArgumentType.getInteger(context, "percent");
        StationManagementResult result = stations.setGravity(player, issuedBy(context.getSource(), player), percent);
        if (!result.code().success()) {
            return rejected(context, "gravity change", result);
        }
        StationState station = result.station().orElseThrow();
        String state = result.code() == StationManagementCode.GRAVITY_SET ? "Station gravity set" : "Station gravity unchanged";
        context.getSource().sendSuccess(() -> Component.literal(
                state + "; station=" + station.stationId() + " gravity=" + percent + "%"
        ), result.code() == StationManagementCode.GRAVITY_SET);
        return 1;
    }

    private int environment(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var environment = stations.environmentAt(player.serverLevel(), player.blockPosition()).orElse(null);
        if (environment == null) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Not inside a station region; the Level's own environment applies"), false);
            return 0;
        }
        String gravity = percent(environment.effectiveGravity()) + "%"
                + (environment.gravityClamped() ? " (configured " + percent(environment.configuredGravity())
                + "%, limited to " + percent(environment.effectiveGravity()) + "%)" : "");
        // Identity is shown only to the owner, members and operators (/arce station list also shows invitees).
        String identity = stations.canIdentify(player, environment.stationId())
                ? environment.stationName() + " id=" + environment.stationId()
                : "A station you are not a member of;";
        context.getSource().sendSuccess(() -> Component.literal(
                identity
                        + " orbit=" + environment.orbitBody()
                        + (environment.orbitBodyAvailable() ? "" : " (unavailable)")
                        + " gravity=" + gravity
                        + " vacuum=" + environment.vacuum()
                        + " solar=" + String.format(java.util.Locale.ROOT, "%.2f", environment.solarIntensity())
                        + " sun_angle=" + String.format(java.util.Locale.ROOT, "%.1f", environment.sunAngleDegrees())
        ), false);
        return 1;
    }

    private static long percent(double multiplier) {
        return Math.round(multiplier * 100.0D);
    }

    /**
     * The player's own, non-silent command source. {@code /execute as}, command blocks and signs
     * keep a different output source; functions (including {@code /function} and advancement
     * rewards) and silent mod calls run with suppressed output. For a player source,
     * {@code withSuppressedOutput()} returns the same object exactly when it is already silent.
     */
    static boolean issuedBy(CommandSourceStack source, ServerPlayer player) {
        return source.source == player && source.withSuppressedOutput() != source;
    }

    private static int rejected(CommandContext<CommandSourceStack> context, String action,
                                StationManagementResult result) {
        context.getSource().sendFailure(Component.literal(
                "Station " + action + " rejected: " + result.code().description()
        ));
        return 0;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        UUID owner = UuidArgument.getUuid(context, "owner");
        ResourceLocation orbit = ResourceLocationArgument.getId(context, "orbit");
        StationCreationResult result = stations.createForOperator(
                context.getSource().getServer(),
                owner,
                StringArgumentType.getString(context, "name"),
                orbit
        );
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal("Station creation failed: " + result.code()));
            return 0;
        }
        StationState station = result.station().orElseThrow();
        context.getSource().sendSuccess(() -> Component.literal(
                "Created " + station.name() + " id=" + station.stationId()
                        + " cell=" + station.cell().x() + "," + station.cell().z()
        ), true);
        return 1;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        UUID stationId = UuidArgument.getUuid(context, STATION);
        StationState station = stations.station(context.getSource().getServer(), stationId).orElse(null);
        if (station == null) {
            context.getSource().sendFailure(Component.literal("Station not found"));
            return 0;
        }
        context.getSource().sendSuccess(() -> summary(station), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "region=" + bounds(station.region())
                        + " pad=" + station.landingPad().x() + "," + station.landingPad().y()
                        + "," + station.landingPad().z()
                        + " orbit=" + station.orbitBody()
                        + " gravity_milli=" + station.environment().gravityMilli()
                        + " vacuum=" + station.environment().vacuum()
                        + " warp_energy=" + StationRegistrySavedData.get(context.getSource().getServer())
                        .warpEnergy(station.stationId())
        ), false);
        return 1;
    }

    private int dump(CommandContext<CommandSourceStack> context) {
        List<StationState> all = stations.stations(context.getSource().getServer());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_STATION_REGION_DUMP count={}", all.size());
        for (StationState station : all) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_STATION_REGION station={} owner={} cell={},{} region={},{},{},{} members={}",
                    station.stationId(), station.ownerId(), station.cell().x(), station.cell().z(),
                    station.region().minimumX(), station.region().minimumZ(),
                    station.region().maximumX(), station.region().maximumZ(),
                    station.members().size()
            );
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Wrote bounded region dump for " + all.size() + " stations"
        ), true);
        return all.size();
    }

    private int recover(CommandContext<CommandSourceStack> context) {
        int recovered = stations.recoverReservations(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
                "Recovered " + recovered + " station reservations"
        ), true);
        return recovered == 0 ? 1 : recovered;
    }

    private int transfer(CommandContext<CommandSourceStack> context) {
        UUID stationId = UuidArgument.getUuid(context, STATION);
        UUID owner = UuidArgument.getUuid(context, "owner");
        return mutate(context, () -> stations.transferOwnership(
                context.getSource().getServer(),
                actor(context.getSource()),
                true,
                stationId,
                owner
        ), "Station ownership transferred");
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        UUID stationId = UuidArgument.getUuid(context, STATION);
        String confirmation = StringArgumentType.getString(context, "confirmation");
        return mutate(context, () -> stations.delete(
                context.getSource().getServer(),
                actor(context.getSource()),
                true,
                stationId,
                confirmation
        ), "Station deleted; unmatched player construction was left as an orphan");
    }

    private int mutate(
            CommandContext<CommandSourceStack> context,
            java.util.function.Supplier<StationState> mutation,
            String message
    ) {
        try {
            StationState state = mutation.get();
            context.getSource().sendSuccess(() -> Component.literal(
                    message + "; station=" + state.stationId()
            ), true);
            return 1;
        } catch (RuntimeException exception) {
            context.getSource().sendFailure(Component.literal(
                    "Station action rejected: " + safeMessage(exception)
            ));
            return 0;
        }
    }

    private static UUID actor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE_ACTOR;
    }

    private static String bounds(StationRegion region) {
        return region.minimumX() + "," + region.minimumZ() + ".." + region.maximumX() + "," + region.maximumZ();
    }

    private static Component summary(StationState station) {
        return Component.literal(station.name() + " id=" + station.stationId()
                + " owner=" + station.ownerId()
                + " members=" + station.members().size()
                + " invitations=" + station.invitations().size()
                + " cell=" + station.cell().x() + "," + station.cell().z());
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.length() > 160
                ? exception.getClass().getSimpleName()
                : message;
    }
}
