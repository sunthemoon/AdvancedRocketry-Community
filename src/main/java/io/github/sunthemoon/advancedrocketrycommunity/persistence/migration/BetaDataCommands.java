package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.diagnostics.BetaOperationalReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Permission-gated operator report for the managed Beta world roots. */
public final class BetaDataCommands {
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("arce")
                        .then(Commands.literal("beta")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("data-report")
                                        .executes(context -> report(context.getSource())))
                                .then(Commands.literal("report")
                                        .executes(context -> report(context.getSource()))))
        );
    }

    private static int report(CommandSourceStack source) {
        BetaOperationalReport report = BetaOperationalReport.collect(source.getServer());
        String line = report.format();
        AdvancedRocketryCommunity.LOGGER.info(line);
        source.sendSuccess(() -> Component.literal(line), false);
        return report.roots().operational() ? 1 : 0;
    }
}
