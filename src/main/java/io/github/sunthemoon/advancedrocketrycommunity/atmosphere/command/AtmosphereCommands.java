package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.command;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Bounded operator diagnostics: summaries only, never volume-cell dumps. */
public final class AtmosphereCommands {
    private final AtmosphereManager atmosphere;

    public AtmosphereCommands(AtmosphereManager atmosphere) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce")
                .then(Commands.literal("atmosphere")
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("rescan")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> rescan(context.getSource())))));
    }

    private int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos position = player.blockPosition();
        String state = label(atmosphere.breathabilityAt(level, position), atmosphere.profileVacuum(level.dimension()));
        AtmosphereLevelMetrics metrics = atmosphere.metrics(level.dimension())
                .orElse(new AtmosphereLevelMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L));
        source.sendSuccess(() -> Component.literal(
                "Atmosphere=" + state
                        + " vents=" + metrics.trackedVents()
                        + " providers=" + metrics.activeProviders()
                        + " scans=" + metrics.activeScanTasks()
                        + " pending=" + metrics.pendingScanTasks()
                        + " volumes=" + metrics.indexedVolumes()
                        + " cells=" + metrics.indexedCells()
                        + " dirty=" + metrics.dirtyPositions()
                        + " inspections=" + metrics.lastTickInspections()
        ), false);
        return metrics.activeProviders();
    }

    /**
     * ADR-066 section 3.1's names for the cell: the internal state calls every non-breathable cell {@code VACUUM}, but
     * that name is only for zero ambient pressure; a non-breathable atmosphere with pressure (Venus, Tau Ceti g) is
     * {@code NON_BREATHABLE_ATMOSPHERE}, as the analyzer and the detector already distinguish (W294, finding 3-7).
     */
    static String label(BreathabilityState state, boolean ambientVacuum) {
        return switch (state) {
            case BREATHABLE -> "BREATHABLE";
            case PENDING -> "PENDING";
            case VACUUM -> ambientVacuum ? "VACUUM" : "NON_BREATHABLE_ATMOSPHERE";
        };
    }

    private int rescan(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        atmosphere.markDirty(player.serverLevel(), player.blockPosition());
        source.sendSuccess(() -> Component.literal("Queued bounded atmosphere rescan near player"), true);
        return 1;
    }
}
