package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** W196 native fixtures: bounded end readiness and a durable REPAIR row, behind the complete release-test gate. */
final class BayReleasedTestCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private BayReleasedTestCommands() { }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("released-ends").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> ends(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("released-status").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> status(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                .then(Commands.literal("seed-repair").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> repair(context.getSource(), UuidArgument.getUuid(context, "logical"))))))));
    }

    private static int ends(CommandSourceStack source, UUID logical) {
        var server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var journal = RocketTransferSavedData.get(server);
        var record = journal.findByLogicalRocket(logical).orElseThrow();
        if (!journal.actionable(logical) || journal.logicalGuard(logical).isEmpty()) {
            LOGGER.info("ARCE_RELEASE_BAY_ENDS logical={} loaded=false reason=JOURNAL_PENDING", logical);
            return 0;
        }
        BayCensusColumns.renewRestart(server, record);
        LOGGER.info("ARCE_RELEASE_BAY_ENDS logical={} loaded={}", logical, BaySourceProof.endsLoaded(server, record));
        return 1;
    }

    private static int status(CommandSourceStack source, UUID logical) {
        var server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var session = RocketRuntime.installedBays().orElseThrow().sessionForTests();
        String rows = session.receipts().stream().filter(r -> r.rocket().equals(logical))
                .map(r -> r.op() + ":" + r.phase() + ":" + r.released() + ":" + session.acknowledged(r.op()))
                .collect(Collectors.joining(","));
        LOGGER.info("ARCE_RELEASE_BAY_FLAGS logical={} rows=[{}]", logical, rows);
        return 1;
    }

    private static int repair(CommandSourceStack source, UUID logical) {
        var server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var session = RocketRuntime.installedBays().orElseThrow().sessionForTests();
        var rows = session.receipts().stream().filter(r -> r.rocket().equals(logical)).toList();
        if (rows.size() != 1 || rows.get(0).phase() != BayReceipt.Phase.INTENT || rows.get(0).released()
                || !session.acknowledged(rows.get(0).op())) { return 0; }
        var row = rows.get(0).withPhase(BayReceipt.Phase.REPAIR, "SIGNATURE_CONFLICT", server.getTickCount());
        var result = BayReceiptTestHooks.put(server, row,
                outcome -> LOGGER.info("ARCE_RELEASE_BAY_REPAIR logical={} outcome={}", logical, outcome));
        return result.queued() ? 1 : 0;
    }
}
