package io.github.sunthemoon.advancedrocketrycommunity.station.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpQuote;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-044 warp commands under {@code /arce station warp}. The client supplies only the target body
 * and, to confirm, the station ID shown by the server; costs, balances and checks are server-side.
 */
public final class StationWarpCommands {
    private static final String STATION = "station_id";
    private final StationWarpService warp;

    public StationWarpCommands(StationWarpService warp) {
        this.warp = Objects.requireNonNull(warp, "warp");
    }

    public void register(RegisterCommandsEvent event) {
        var tree = Commands.literal("warp")
                .then(Commands.literal("confirm")
                        .then(Commands.argument(STATION, UuidArgument.uuid()).executes(this::confirm)))
                .then(Commands.literal("cancel").executes(this::cancel))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.argument("body", ResourceLocationArgument.id()).executes(this::request));
        var admin = Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("warp")
                        .executes(context -> diagnostics(context, java.util.Optional.empty()))
                        .then(Commands.argument(STATION, UuidArgument.uuid())
                                .executes(context -> diagnostics(context,
                                        java.util.Optional.of(UuidArgument.getUuid(context, STATION))))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("station")
                .then(tree).then(admin)));
    }

    /** Operator-only warp diagnostics; read-only. */
    private int diagnostics(CommandContext<CommandSourceStack> context, java.util.Optional<java.util.UUID> station) {
        for (String line : warp.diagnostics(context.getSource().getServer(), station)) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private int request(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationWarpResult result = warp.request(player, StationCommands.issuedBy(context.getSource(), player),
                ResourceLocationArgument.getId(context, "body"));
        if (result.code() != StationManagementCode.WARP_ISSUED) {
            return rejected(context, result);
        }
        WarpQuote quote = result.quote().orElseThrow();
        StationState station = quote.observed();
        long seconds = StationLimits.WARP_CONFIRMATION_TICKS / 20L;
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Warp %s id=%s from %s to %s: %s warp, cost %d FE, warp energy %d FE."
                        + " Docked rockets move with the station.%s"
                        + " A warp back costs energy again. To confirm within %d seconds, run"
                        + " /arce station warp confirm %s",
                station.name(), station.stationId(), station.orbitBody(), quote.target(), quote.costClass().label(),
                quote.cost(), result.balance(),
                quote.targetSystemHasRoutes() ? "" : " The target's star system has no rocket routes.",
                seconds, station.stationId())), false);
        return 1;
    }

    private int confirm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationWarpResult result = warp.confirm(player, StationCommands.issuedBy(context.getSource(), player),
                UuidArgument.getUuid(context, STATION));
        if (result.code() != StationManagementCode.WARP_STARTED) {
            return rejected(context, result);
        }
        WarpQuote quote = result.quote().orElseThrow();
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Warp countdown started; station=%s target=%s commit in %d seconds;"
                        + " /arce station warp cancel stops it",
                quote.stationId(), quote.target(), StationLimits.WARP_COUNTDOWN_TICKS / 20L)), true);
        return 1;
    }

    private int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationWarpResult result = warp.cancel(player, StationCommands.issuedBy(context.getSource(), player));
        if (result.code() != StationManagementCode.WARP_CANCELLED) {
            return rejected(context, result);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Warp countdown cancelled; station=" + result.quote().orElseThrow().stationId()), true);
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StationWarpResult result = warp.status(player);
        if (result.code() != StationManagementCode.WARP_STATUS) {
            return rejected(context, result);
        }
        StationState station = result.station().orElseThrow();
        WarpSettings settings = warp.settings();
        String countdown = warp.countdown(station.stationId())
                .map(running -> String.format(Locale.ROOT, " countdown=%s in %d s",
                        running.quote().target(), (running.ticksLeft(player.getServer().getTickCount()) + 19L) / 20L))
                .orElse(" countdown=none");
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Warp status; station=%s orbit=%s energy=%d FE pending=%d FE enabled=%s cost_in_system=%d"
                        + " cost_interstellar=%d%s",
                station.stationId(), station.orbitBody(), result.balance(), warp.pendingCredit(station.stationId()),
                settings.enabled(),
                settings.inSystemCost(), settings.interstellarCost(), countdown)), false);
        return 1;
    }

    private static int rejected(CommandContext<CommandSourceStack> context, StationWarpResult result) {
        String numbers = result.quote()
                .map(quote -> String.format(Locale.ROOT, " (%s warp costs %d FE; warp energy %d FE)",
                        quote.costClass().label(), quote.cost(), result.balance()))
                .orElse("");
        context.getSource().sendFailure(Component.literal(
                "Station warp rejected: " + result.code().description() + numbers));
        return 0;
    }
}
