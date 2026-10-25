package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleaseResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Permission is checked again at execution because functions replay an already-parsed command. */
public final class RocketRejoinCommands {
    public static final String PREFIX = "command.advancedrocketrycommunity.rejoin.";
    private RocketRejoinCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, RocketRuntime::releaseAbsent, RocketRuntime::inspectRejoins);
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            BiFunction<MinecraftServer, UUID, RocketReleaseResult> release,
            BiFunction<MinecraftServer, UUID, RocketRejoinInspection> inspect) {
        dispatcher.register(Commands.literal("arce").then(Commands.literal("rocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("recover").then(Commands.argument("transfer", UuidArgument.uuid())
                        .then(Commands.literal("release-absent").requires(source -> source.hasPermission(3))
                                .executes(context -> {
                                    var source = context.getSource();
                                    // Before argument resolution, transfer lookup, sidecar access or owner creation.
                                    if (!permitted(source)) { return 0; }
                                    var result = release.apply(source.getServer(), UuidArgument.getUuid(context, "transfer"));
                                    var message = text(result.status().name(), result.release(), text("stage." + result.stage()));
                                    if (result.accepted()) { source.sendSuccess(() -> message, false); return 1; }
                                    source.sendFailure(message); return 0;
                                }))))
                .then(Commands.literal("rejoins").requires(source -> source.hasPermission(3))
                        .executes(context -> view(context.getSource(), null, inspect))
                        .then(Commands.argument("player", UuidArgument.uuid()).executes(context -> {
                            var source = context.getSource();
                            if (!permitted(source)) { return 0; }
                            return view(source, UuidArgument.getUuid(context, "player"), inspect);
                        })))));
    }

    private static boolean permitted(CommandSourceStack source) {
        if (source.hasPermission(3)) { return true; }
        source.sendFailure(text("permission")); return false;
    }
    private static int view(CommandSourceStack source, UUID player,
                            BiFunction<MinecraftServer, UUID, RocketRejoinInspection> inspect) {
        if (!permitted(source)) { return 0; }
        var view = inspect.apply(source.getServer(), player);
        if (view == null) { source.sendFailure(text("unavailable")); return 0; }
        // One response, at most eight entries; a UUID filter can select an offline player.
        var message = text("header", view.rows().size(), view.total());
        for (var row : view.rows()) {
            var e = row.entry();
            message.append("\n").append(text("entry", row.batch().release(), row.batch().transfer(), e.player(),
                    text("stage." + row.stage()), text("seat." + e.seat()), text("placement." + e.placement())));
        }
        source.sendSuccess(() -> message, false); return view.rows().size();
    }
    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable(PREFIX + key.toLowerCase(Locale.ROOT), args);
    }
}
