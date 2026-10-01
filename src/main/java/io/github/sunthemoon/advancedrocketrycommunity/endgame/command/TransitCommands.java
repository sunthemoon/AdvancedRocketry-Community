package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.TransitOperations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-054 section 13 ledger commands: {@code transfer list|inspect|purge|resettle} for operators, {@code transfer
 * redirect} and {@code endpoint resolve} for operators and, from their own connected command source, for owners of the
 * cargo or endpoint. Every change is an audited barrier flush; outputs are bounded to one page.
 */
public final class TransitCommands {
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private static final int MAX_PAGE = EndgameLimits.MAX_TRANSIT_RECORDS / EndgameLimits.AUDIT_PAGE_LINES - 1;
    private final EndgameService service;

    public TransitCommands(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("transfer")
                        .then(op(Commands.literal("list")).executes(context -> list(context, 0))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0, MAX_PAGE))
                                        .executes(context -> list(context,
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(op(Commands.literal("inspect")).then(transfer(seq -> seq.executes(this::inspect))))
                        .then(op(Commands.literal("purge")).then(transfer(seq -> seq.executes(this::purge))))
                        .then(op(Commands.literal("resettle")).then(transfer(seq -> seq
                                .then(Commands.literal("incoming").executes(context -> resettle(context, false)))
                                .then(Commands.literal("moved").executes(context -> resettle(context, true))))))
                        .then(Commands.literal("redirect").then(transfer(seq -> seq
                                .then(Commands.argument("endpoint", UuidArgument.uuid()).executes(this::redirect))))))
                .then(Commands.literal("endpoint")
                        .then(Commands.literal("resolve")
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(this::resolve))))));
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T op(T node) {
        return node.requires(source -> source.hasPermission(2));
    }

    /** {@code <source> <seq>}, with the leaf built on the seq node. */
    private static RequiredArgumentBuilder<CommandSourceStack, UUID> transfer(
            Consumer<RequiredArgumentBuilder<CommandSourceStack, Long>> leaf) {
        RequiredArgumentBuilder<CommandSourceStack, Long> seq = Commands.argument("seq", LongArgumentType.longArg(1L));
        leaf.accept(seq);
        return Commands.argument("source", UuidArgument.uuid()).then(seq);
    }

    private static TransitKey key(CommandContext<CommandSourceStack> context) {
        return new TransitKey(UuidArgument.getUuid(context, "source"), LongArgumentType.getLong(context, "seq"));
    }

    private int list(CommandContext<CommandSourceStack> context, int page) {
        if (service.root().isEmpty()) {
            return reply(context, "transfer_list", EndgameCode.ROOT_UNAVAILABLE, "");
        }
        List<String> lines = service.transitOperations().list(page);
        int total = service.root().get().transits().size();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("endgame transfers: " + total + " (page " + page + ", "
                + EndgameLimits.AUDIT_PAGE_LINES + " per page)"), false);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return total;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        Optional<String> line = service.transitOperations().inspect(key(context));
        if (line.isEmpty()) {
            return reply(context, "transfer_inspect", EndgameCode.TRANSFER_NOT_FOUND, "");
        }
        context.getSource().sendSuccess(() -> Component.literal(line.get()), false);
        return 1;
    }

    private int purge(CommandContext<CommandSourceStack> context) {
        TransitKey key = key(context);
        return reply(context, "transfer_purge", service.transitOperations().purge(key, actor(context), now(context)),
                "transfer=" + key);
    }

    private int resettle(CommandContext<CommandSourceStack> context, boolean moved) {
        TransitKey key = key(context);
        return reply(context, "transfer_resettle", service.transitOperations().resettle(key, moved, actor(context),
                now(context)), "transfer=" + key + " outcome=" + (moved ? "moved" : "incoming"));
    }

    /** Operators redirect any cargo; an owner only their own, from their own connected command source. */
    private int redirect(CommandContext<CommandSourceStack> context) {
        TransitKey key = key(context);
        UUID target = UuidArgument.getUuid(context, "endpoint");
        boolean operator = context.getSource().hasPermission(2);
        UUID actor;
        if (operator) {
            actor = actor(context);
        } else {
            Optional<ServerPlayer> player = EndgameCommandSources.ownPlayer(context.getSource());
            if (player.isEmpty()) {
                return refuse(context, EndgameCode.NOT_A_PLAYER);
            }
            actor = player.get().getUUID();
        }
        return reply(context, "transfer_redirect", service.transitOperations().redirect(key, target, actor, operator,
                now(context)), "transfer=" + key + " to=" + target);
    }

    /**
     * Resolves a loaded ledger endpoint's frozen contents. An owner resolves their own endpoints from their own
     * connected source, within the player barrier spacing; operators any.
     */
    private int resolve(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        boolean operator = context.getSource().hasPermission(2);
        UUID actor;
        if (operator) {
            actor = actor(context);
        } else {
            Optional<ServerPlayer> player = EndgameCommandSources.ownPlayer(context.getSource());
            if (player.isEmpty()) {
                return refuse(context, EndgameCode.NOT_A_PLAYER);
            }
            actor = player.get().getUUID();
        }
        Optional<TransitEndpoint> endpoint = service.transits().loaded(id);
        if (endpoint.isEmpty() || !operator && endpoint.get().endpointOwner().filter(actor::equals).isEmpty()) {
            // The endpoint's chunk must be loaded; another player's endpoint reads as not loaded.
            return operator ? reply(context, "endpoint_resolve", EndgameCode.CHUNK_UNLOADED, "id=" + id)
                    : refuse(context, EndgameCode.CHUNK_UNLOADED);
        }
        if (!operator && !service.transitOperations().playerBarrierAllowed(now(context))) {
            return refuse(context, EndgameCode.ROOT_BUSY);
        }
        TransitOperations.Resolved resolved = service.transitOperations().resolve(endpoint.get(), actor, now(context),
                null);
        return reply(context, "endpoint_resolve", EndgameCode.OK, "id=" + id + " returned=" + resolved.returned()
                + " moved=" + resolved.moved() + " destroyed=" + resolved.destroyed() + " receipts="
                + resolved.receipts() + " left=" + resolved.left());
    }

    private static UUID actor(CommandContext<CommandSourceStack> context) {
        return context.getSource().getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE;
    }

    private static long now(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().overworld().getGameTime();
    }

    private static int refuse(CommandContext<CommandSourceStack> context, EndgameCode code) {
        context.getSource().sendFailure(Component.translatable(code.translationKey()).append(" (" + code.name() + ")"));
        return 0;
    }

    private int reply(CommandContext<CommandSourceStack> context, String action, EndgameCode code, String fields) {
        CommandSourceStack source = context.getSource();
        if (code != EndgameCode.OK) {
            if (source.hasPermission(2)) {
                service.audit().line(now(context), "endgame", action, code.name(), null, null, actor(context), fields);
            }
            return refuse(context, code);
        }
        source.sendSuccess(() -> Component.literal(action + " " + fields), true);
        return 1;
    }
}
