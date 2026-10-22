package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * W129a native cuts: {@code arce rocket release-test transaction-writer ...}. Absent unless the release-test JVM property
 * is set; every action also requires an empty dedicated server (RocketTransactionWriterCuts#enabled).
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketTransactionWriterCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RocketTransactionWriterCommands() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(RocketTransactionWriterCuts.PROPERTY)) { return; }
        var writer = Commands.literal("transaction-writer")
                .then(Commands.literal("status").executes(context -> run(context, journal -> status(journal))))
                .then(Commands.literal("add").executes(context -> run(context, journal -> journal.cuts().add(journal))))
                .then(Commands.literal("request").executes(context -> run(context, journal -> {
                    var result = journal.requestCheckpoint(context.getSource().getServer());
                    LOGGER.info("ARCE_RELEASE_TXW_REQUESTED result={} sequence={}", result, journal.mutationSequence());
                })))
                .then(Commands.literal("release").executes(context -> run(context, journal -> journal.cuts().release())))
                .then(Commands.literal("at-stop").executes(context -> run(context, journal -> journal.cuts().mutateAtStopping())))
                .then(Commands.literal("crash").then(Commands.argument("release", BoolArgumentType.bool())
                        .executes(context -> run(context, journal -> journal.cuts().crashAtNextTick(
                                BoolArgumentType.getBool(context, "release"))))))
                .then(Commands.literal("arm").then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word()).executes(context -> run(context,
                                journal -> journal.cuts().arm(
                                        RocketTransactionWriterCuts.Point.valueOf(upper(context, "point")),
                                        RocketTransactionWriterCuts.Mode.valueOf(upper(context, "mode"))))))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("rocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("release-test").then(writer))));
    }

    private static void status(RocketTransactionSavedData journal) {
        LOGGER.info("ARCE_RELEASE_TXW_STATUS b14={} sequence={} acknowledged={} outcome={} busy={} dirty={} records={}",
                journal.absenceStatus(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), journal.mutationSequence(),
                journal.acknowledgement().map(ack -> String.valueOf(ack.sequence())).orElse("none"), journal.writeOutcome(),
                journal.writerBusy(), journal.isDirty(), journal.entries().size());
    }

    private static String upper(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name).toUpperCase(Locale.ROOT);
    }

    private static int run(CommandContext<CommandSourceStack> context, Consumer<RocketTransactionSavedData> action)
            throws CommandSyntaxException {
        var server = context.getSource().getServer();
        if (!RocketTransactionWriterCuts.enabled(server)) {
            throw new SimpleCommandExceptionType(Component.literal("Transaction writer cuts need an empty release-test dedicated server")).create();
        }
        try {
            action.accept(RocketTransactionSavedData.get(server));
        } catch (IllegalArgumentException | IllegalStateException refused) {
            throw new SimpleCommandExceptionType(Component.literal(refused.getMessage())).create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Transaction writer cut command done"), false);
        return 1;
    }
}
