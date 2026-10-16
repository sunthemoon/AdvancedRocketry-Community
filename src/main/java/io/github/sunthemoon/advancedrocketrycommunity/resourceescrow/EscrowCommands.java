package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Permission-2 diagnostics and audited decisions. Production resource admission stays disabled. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class EscrowCommands {
    private EscrowCommands() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("escrow")
                .requires(source -> source.hasPermission(2)).then(Commands.literal("status").executes(context -> {
                    EscrowOperatorDiagnostics.status(context.getSource().getServer()).forEach(line ->
                            context.getSource().sendSuccess(() -> Component.literal(line), false));
                    return 1;
                })).then(diagnostic("inspect", false)).then(diagnostic("retry", true))
                .then(intent("accept-current", EscrowSettlementIntent.Choice.ACCEPT_CURRENT))
                .then(intent("abandon", EscrowSettlementIntent.Choice.ABANDON))
                .then(intent("settle-floor", EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> diagnostic(String name, boolean retry) {
        return Commands.literal(name).then(Commands.argument("operation", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(context -> {
                    var source = context.getSource();
                    var selected = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "operation");
                    try {
                        if (retry) {
                            var result = EscrowRuntime.of(source.getServer()).settlement().retry(source.getServer(), selected);
                            source.sendSuccess(() -> Component.literal(result.message()), false);
                            return result.outcome() == EscrowOperatorRetry.Outcome.ACCEPTED ? 1 : 0;
                        }
                        EscrowOperatorDiagnostics.inspect(source.getServer(), selected).forEach(line ->
                                source.sendSuccess(() -> Component.literal(line), false));
                        return 1;
                    } catch (IllegalArgumentException refused) {
                        source.sendFailure(Component.literal("Escrow " + name + " refused: " + refused.getMessage())); return 0;
                    }
                }));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> intent(String name, EscrowSettlementIntent.Choice choice) {
        return Commands.literal(name).then(Commands.argument("operation", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(context -> execute(context.getSource(), net.minecraft.commands.arguments.UuidArgument.getUuid(context, "operation"), choice, null))
                .then(Commands.literal("confirm").then(Commands.argument("digest", StringArgumentType.word())
                        .executes(context -> execute(context.getSource(), net.minecraft.commands.arguments.UuidArgument.getUuid(context, "operation"),
                                choice, StringArgumentType.getString(context, "digest"))))));
    }
    private static int execute(CommandSourceStack source, java.util.UUID selected, EscrowSettlementIntent.Choice choice, String digest) {
        try {
            var server = source.getServer(); var operator = EscrowRuntime.of(server).settlement();
            var actor = source.getEntity() == null
                    ? java.util.UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    : source.getEntity().getUUID();
            if (digest == null) {
                var preview = operator.preview(server, selected, choice, actor, source.getTextName());
                source.sendSuccess(() -> Component.literal(EscrowOperatorDiagnostics.preview(preview)), false);
                return 1;
            }
            boolean queued = operator.confirm(server, selected, choice, actor, digest, committed -> {
                if (committed) {
                    source.sendSuccess(() -> Component.literal("Decision acknowledged; affected resources remain locked until settlement checkpoints and release are acknowledged."), true);
                } else { source.sendFailure(Component.literal("Decision was not acknowledged; no settlement or release occurred.")); }
            });
            if (!queued) { source.sendFailure(Component.literal("Ledger is busy or unavailable; no intent was acknowledged.")); return 0; }
            source.sendSuccess(() -> Component.literal("Decision write queued, not acknowledged; affected resources remain locked."), false); return 1;
        } catch (IllegalArgumentException refused) {
            source.sendFailure(Component.literal("Settlement intent refused: " + refused.getMessage())); return 0;
        }
    }
}
