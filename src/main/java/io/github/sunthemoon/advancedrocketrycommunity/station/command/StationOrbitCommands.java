package io.github.sunthemoon.advancedrocketrycommunity.station.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationOrbitalControlService;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-065 D2-A commands under {@code /arce station}: {@code altitude <4..190>}, {@code orientation rates
 * <east> <up> <north>} (-60..60 turns per 72,000 ticks), {@code orientation stop} and {@code orientation phases
 * <east> <up> <north>} (milli-degrees, only while stopped). The same local actor rule as gravity applies.
 */
public final class StationOrbitCommands {
    private final StationOrbitalControlService controls;

    public StationOrbitCommands(StationOrbitalControlService controls) {
        this.controls = Objects.requireNonNull(controls, "controls");
    }

    public void register(RegisterCommandsEvent event) {
        var altitude = Commands.literal("altitude")
                .then(Commands.argument("distance", IntegerArgumentType.integer(
                                StationOrbitalControls.MIN_DISTANCE, StationOrbitalControls.MAX_DISTANCE))
                        .executes(this::setAltitude));
        var orientation = Commands.literal("orientation")
                .then(Commands.literal("stop").executes(this::stop))
                .then(Commands.literal("rates")
                        .then(rate("east").then(rate("up").then(rate("north").executes(this::setRates)))))
                .then(Commands.literal("phases")
                        .then(phase("east").then(phase("up").then(phase("north").executes(this::setPhases)))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("station")
                .then(altitude).then(orientation)));
    }

    /** Distance, altitude, GEO flag, rates and the epoch phases; English diagnostics like the other lines. */
    static String describe(StationOrbitalControls controls) {
        return String.format(Locale.ROOT,
                "distance=%d altitude=%dkm geo=%s rates=%d,%d,%d phases=%.3f,%.3f,%.3f epoch=%d",
                controls.distance(), controls.altitudeKilometres(), controls.geostationary(), controls.eastRate(),
                controls.upRate(), controls.northRate(), controls.eastPhase() / 1_000.0D, controls.upPhase() / 1_000.0D,
                controls.northPhase() / 1_000.0D, controls.epoch());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> rate(String axis) {
        return Commands.argument(axis, IntegerArgumentType.integer(-StationOrbitalControls.MAX_RATE,
                StationOrbitalControls.MAX_RATE));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> phase(String axis) {
        return Commands.argument(axis, IntegerArgumentType.integer(0, StationOrbitalControls.MAX_PHASE));
    }

    private int setAltitude(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return reply(context, "altitude change", controls.setAltitude(player, actor(context, player), Optional.empty(),
                IntegerArgumentType.getInteger(context, "distance")));
    }

    private int setRates(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return reply(context, "orientation change", controls.setRates(player, actor(context, player), Optional.empty(),
                IntegerArgumentType.getInteger(context, "east"), IntegerArgumentType.getInteger(context, "up"),
                IntegerArgumentType.getInteger(context, "north")));
    }

    private int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return reply(context, "orientation change", controls.stop(player, actor(context, player), Optional.empty()));
    }

    private int setPhases(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return reply(context, "orientation change", controls.setPhases(player, actor(context, player), Optional.empty(),
                IntegerArgumentType.getInteger(context, "east"), IntegerArgumentType.getInteger(context, "up"),
                IntegerArgumentType.getInteger(context, "north")));
    }

    private static StationActorContext actor(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        return StationActorContext.command(StationCommands.issuedBy(context.getSource(), player));
    }

    private static int reply(CommandContext<CommandSourceStack> context, String action, StationManagementResult result) {
        if (!result.code().success()) {
            context.getSource().sendFailure(Component.literal(
                    "Station " + action + " rejected: " + result.code().description()));
            return 0;
        }
        StationState station = result.station().orElseThrow();
        boolean changed = result.code() == StationManagementCode.ALTITUDE_SET
                || result.code() == StationManagementCode.ORIENTATION_SET;
        context.getSource().sendSuccess(() -> Component.literal("Station " + result.code().description()
                + "; station=" + station.stationId() + " " + describe(station.orbitalControls())), changed);
        return 1;
    }
}
