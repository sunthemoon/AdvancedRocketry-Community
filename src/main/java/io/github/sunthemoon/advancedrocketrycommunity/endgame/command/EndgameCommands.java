package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-054 section 13 commands that C11 owns. Operator commands need permission 2 at the leaf (the ADR-045 lesson);
 * {@code endpoint forget} is a player's own command for their own MISSING endpoints. Every command that changes
 * state is audited, and outputs are bounded to one page.
 */
public final class EndgameCommands {
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private final EndgameService service;

    public EndgameCommands(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(op(Commands.literal("status")).executes(this::status))
                .then(Commands.literal("audit")
                        .then(op(Commands.literal("all")).executes(context -> audit(context, null, 0))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0, 31))
                                        .executes(context -> audit(context, null,
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(op(Commands.argument("system", StringArgumentType.word()))
                                .executes(context -> audit(context, StringArgumentType.getString(context, "system"), 0))
                                .then(Commands.argument("page", IntegerArgumentType.integer(0, 31))
                                        .executes(context -> audit(context,
                                                StringArgumentType.getString(context, "system"),
                                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal("zone")
                        .then(op(Commands.literal("add"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(context -> addZone(context, List.of()))
                                                        .then(Commands.argument("players",
                                                                        GameProfileArgument.gameProfile())
                                                                .executes(context -> addZone(context,
                                                                        GameProfileArgument.getGameProfiles(context,
                                                                                "players"))))))))
                        .then(op(Commands.literal("remove"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(this::removeZone)))
                        .then(op(Commands.literal("list")).executes(this::listZones)))
                .then(Commands.literal("endpoint")
                        .then(op(Commands.literal("list"))
                                .executes(context -> listEndpoints(context, Optional.empty()))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> listEndpoints(context, Optional.of(single(context))))))
                        .then(op(Commands.literal("retire"))
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(this::retire)))
                        .then(Commands.literal("forget")
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(this::forget))))
                .then(Commands.literal("tombstone")
                        .then(op(Commands.literal("evict"))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(this::evict)))
                        .then(op(Commands.literal("settle"))
                                .then(Commands.argument("id", UuidArgument.uuid()).executes(this::settle))))));
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T op(T node) {
        return node.requires(source -> source.hasPermission(2));
    }

    private int status(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(service.status()), false);
        return 1;
    }

    private int audit(CommandContext<CommandSourceStack> context, String system, int page) {
        List<String> lines = service.audit().page(system, page);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("endgame audit page " + page + ": " + lines.size() + " lines"),
                false);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }

    private int addZone(CommandContext<CommandSourceStack> context, Collection<GameProfile> players)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        ServerLevel level = context.getSource().getLevel();
        List<UUID> allow = players.stream().map(GameProfile::getId).distinct().toList();
        ProtectedZone zone;
        try {
            zone = ProtectedZone.of(name, level.dimension().location(), from.getX(), from.getZ(), to.getX(), to.getZ(),
                    allow);
        } catch (IllegalArgumentException invalid) {
            return reply(context, "zone_add", EndgameCode.ZONE_INVALID, null, "name=" + name);
        }
        return change(context, "zone_add", null, "name=" + name + " level=" + zone.level() + " box=" + zone.minX()
                + "," + zone.minZ() + ".." + zone.maxX() + "," + zone.maxZ() + " allow=" + allow.size(),
                root -> root.addZone(zone, service.settings().zones()));
    }

    private int removeZone(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        return change(context, "zone_remove", null, "name=" + name, root -> root.removeZone(name));
    }

    private int listZones(CommandContext<CommandSourceStack> context) {
        Optional<EndgameRoot> root = service.root();
        if (root.isEmpty()) {
            return reply(context, "zone_list", EndgameCode.ROOT_UNAVAILABLE, null, "");
        }
        List<ProtectedZone> zones = new ArrayList<>(root.get().zones());
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("endgame zones: " + zones.size()), false);
        zones.stream().limit(EndgameLimits.AUDIT_PAGE_LINES).forEach(zone -> source.sendSuccess(() -> Component.literal(
                zone.name() + " " + zone.level() + " " + zone.minX() + "," + zone.minZ() + ".." + zone.maxX() + ","
                        + zone.maxZ() + " allow=" + zone.allowList().size()), false));
        return zones.size();
    }

    /** One page: the first 16 records in ID order and the total, so operators narrow by owner. */
    private int listEndpoints(CommandContext<CommandSourceStack> context, Optional<UUID> owner) {
        Optional<EndgameRoot> root = service.root();
        if (root.isEmpty()) {
            return reply(context, "endpoint_list", EndgameCode.ROOT_UNAVAILABLE, null, "");
        }
        List<EndpointRecord> records = root.get().endpoints().stream()
                .filter(record -> owner.isEmpty() || record.owner().equals(owner.get())).toList();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("endgame endpoints: " + records.size() + " (showing at most "
                + EndgameLimits.AUDIT_PAGE_LINES + ")"), false);
        records.stream().limit(EndgameLimits.AUDIT_PAGE_LINES).forEach(record -> source.sendSuccess(
                () -> Component.literal(record.id() + " " + record.kind() + " " + record.state() + " owner="
                        + record.owner() + " " + record.level() + " " + BlockPos.of(record.pos()).toShortString()),
                false));
        return records.size();
    }

    private int retire(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        Optional<EndpointRecord> record = service.root().flatMap(root -> root.endpoint(id));
        if (record.isPresent() && chunkLoaded(context, record.get())) {
            return reply(context, "endpoint_retire", EndgameCode.ENDPOINT_CHUNK_LOADED, id, "");
        }
        return changeWithEvictions(context, "endpoint_retire", id,
                root -> root.retireLost(id, EndgameService::pinned));
    }

    private int forget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return reply(context, "endpoint_forget", EndgameCode.NOT_A_PLAYER, null, "");
        }
        UUID id = UuidArgument.getUuid(context, "id");
        return changeWithEvictions(context, "endpoint_forget", id,
                root -> root.forget(id, player.getUUID(), source.hasPermission(2), EndgameService::pinned));
    }

    private int evict(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID owner = single(context);
        if (service.root().isEmpty()) {
            return reply(context, "tombstone_evict", EndgameCode.ROOT_UNAVAILABLE, null, "");
        }
        List<UUID> evicted = service.barrier(root -> root.evictOwner(owner, EndgameService::pinned));
        long now = context.getSource().getServer().overworld().getGameTime();
        service.auditEvictions(now, evicted);
        return reply(context, "tombstone_evict", EndgameCode.OK, null, "owner=" + owner + " evicted=" + evicted.size());
    }

    private int settle(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        return changeWithEvictions(context, "tombstone_settle", id, root -> root.settle(id, EndgameService::pinned));
    }

    private static UUID single(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw net.minecraft.commands.arguments.EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        }
        return profiles.iterator().next().getId();
    }

    private static boolean chunkLoaded(CommandContext<CommandSourceStack> context, EndpointRecord record) {
        ServerLevel level = context.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                record.level()));
        BlockPos pos = BlockPos.of(record.pos());
        return level != null && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private int change(CommandContext<CommandSourceStack> context, String action, UUID subject, String fields,
                       Function<EndgameRoot, EndgameCode> operation) {
        if (service.root().isEmpty()) {
            return reply(context, action, EndgameCode.ROOT_UNAVAILABLE, subject, fields);
        }
        EndgameCode code = service.barrier(operation);
        return reply(context, action, code, subject, fields);
    }

    private int changeWithEvictions(CommandContext<CommandSourceStack> context, String action, UUID subject,
                                    Function<EndgameRoot, EndgameRoot.Change> operation) {
        if (service.root().isEmpty()) {
            return reply(context, action, EndgameCode.ROOT_UNAVAILABLE, subject, "");
        }
        EndgameRoot.Change change = service.barrier(operation);
        service.auditEvictions(context.getSource().getServer().overworld().getGameTime(), change.evicted());
        return reply(context, action, change.code(), subject, change.evicted().isEmpty() ? ""
                : "evicted=" + change.evicted().size());
    }

    private int reply(CommandContext<CommandSourceStack> context, String action, EndgameCode code, UUID subject,
                      String fields) {
        CommandSourceStack source = context.getSource();
        UUID actor = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE;
        service.audit().line(source.getServer().overworld().getGameTime(), "endgame", action, code.name(), subject,
                null, actor, fields + (service.writePending() ? " write_pending=true" : ""));
        if (code != EndgameCode.OK) {
            source.sendFailure(Component.translatable(code.translationKey()).append(" (" + code.name() + ")"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(action + " " + (subject == null ? "" : subject + " ") + fields
                + (service.writePending() ? " (write pending)" : "")), true);
        return 1;
    }
}
