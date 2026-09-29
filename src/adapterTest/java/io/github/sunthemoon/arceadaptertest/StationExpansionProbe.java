package io.github.sunthemoon.arceadaptertest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Opt-in native stand-in for local players in Space. It connects a mock player the way vanilla
 * GameTest does, runs only the host's public station-expansion command as that player, and
 * records the replies. It uses no host internals and never writes host data itself.
 */
final class StationExpansionProbe {
    private static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.tryParse("advancedrocketrycommunity:space"));
    private static final Pattern ALLOWED = Pattern.compile(
            "arce station expand( confirm [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?");
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_MESSAGES = 16;
    private static final Map<UUID, Probe> PLAYERS = new LinkedHashMap<>();

    private StationExpansionProbe() { }

    static void install() {
        if (Boolean.getBoolean("arce_adapter_test.stationSmoke")) {
            MinecraftForge.EVENT_BUS.addListener(StationExpansionProbe::commands);
            MinecraftForge.EVENT_BUS.addListener(StationExpansionProbe::stopping);
        }
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce_station_probe").requires(source -> source.hasPermission(2))
                .then(Commands.literal("join")
                        .then(Commands.argument("label", StringArgumentType.word())
                                .then(Commands.argument("actor", UuidArgument.uuid())
                                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                                                .executes(StationExpansionProbe::join)))))
                .then(Commands.literal("run")
                        .then(Commands.argument("label", StringArgumentType.word())
                                .then(Commands.argument("actor", UuidArgument.uuid())
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(StationExpansionProbe::run)))))
                .then(Commands.literal("leave")
                        .then(Commands.argument("label", StringArgumentType.word())
                                .then(Commands.argument("actor", UuidArgument.uuid())
                                        .executes(StationExpansionProbe::leave)))));
    }

    private static int join(CommandContext<CommandSourceStack> context) {
        String label = label(context);
        UUID actor = UuidArgument.getUuid(context, "actor");
        BlockPos position = BlockPosArgument.getBlockPos(context, "position");
        MinecraftServer server = context.getSource().getServer();
        ServerLevel space = server.getLevel(SPACE);
        if (space == null || PLAYERS.containsKey(actor) || PLAYERS.size() >= MAX_PLAYERS
                || server.getPlayerList().getPlayer(actor) != null) {
            throw new IllegalStateException("Station probe cannot connect this player");
        }
        boolean loadedBefore = space.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
        List<String> messages = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(server, space, new GameProfile(actor, "probe" + PLAYERS.size())) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                // As vanilla's mock player: exempt from vacuum/life-support damage while probing.
                return true;
            }

            @Override
            public void sendSystemMessage(Component message) {
                if (messages.size() < MAX_MESSAGES) {
                    messages.add(message.getString());
                }
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        // A present player's own chunk is loaded; the probe stands in for that only.
        space.getChunk(position.getX() >> 4, position.getZ() >> 4);
        player.teleportTo(space, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        PLAYERS.put(actor, new Probe(player, messages));
        JsonObject report = report("join", label, actor);
        report.addProperty("x", position.getX());
        report.addProperty("y", position.getY());
        report.addProperty("z", position.getZ());
        report.addProperty("player_chunk_loaded_before", loadedBefore);
        report.addProperty("connected", server.getPlayerList().getPlayer(actor) == player);
        LogUtils.getLogger().info("ARCE_STATION_PROBE {}", report);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        String label = label(context);
        UUID actor = UuidArgument.getUuid(context, "actor");
        String command = StringArgumentType.getString(context, "command");
        Probe probe = PLAYERS.get(actor);
        if (!ALLOWED.matcher(command).matches() || probe == null) {
            throw new IllegalArgumentException("Station probe accepts only expansion commands for its players");
        }
        ServerLevel level = probe.player().serverLevel();
        int before = probe.messages().size();
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        int result;
        String error = null;
        try {
            result = context.getSource().getServer().getCommands().getDispatcher()
                    .execute(command, probe.player().createCommandSourceStack());
        } catch (CommandSyntaxException exception) {
            result = -1;
            error = exception.getMessage();
        }
        int chunksAfter = level.getChunkSource().getLoadedChunksCount();
        JsonObject report = report("run", label, actor);
        report.addProperty("command", command);
        report.addProperty("level", level.dimension().location().toString());
        report.addProperty("loaded_chunks_before_command", chunksBefore);
        report.addProperty("loaded_chunks_after_command", chunksAfter);
        report.addProperty("result", result);
        if (error != null) {
            report.addProperty("error", error);
        }
        JsonArray replies = new JsonArray();
        probe.messages().subList(before, probe.messages().size()).forEach(replies::add);
        report.add("messages", replies);
        LogUtils.getLogger().info("ARCE_STATION_PROBE {}", report);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) {
        String label = label(context);
        UUID actor = UuidArgument.getUuid(context, "actor");
        Probe probe = PLAYERS.remove(actor);
        if (probe == null) {
            throw new IllegalArgumentException("Station probe player is not connected");
        }
        context.getSource().getServer().getPlayerList().remove(probe.player());
        LogUtils.getLogger().info("ARCE_STATION_PROBE {}", report("leave", label, actor));
        return 1;
    }

    private static void stopping(ServerStoppingEvent event) {
        PLAYERS.values().forEach(probe -> event.getServer().getPlayerList().remove(probe.player()));
        PLAYERS.clear();
    }

    private static String label(CommandContext<CommandSourceStack> context) {
        String label = StringArgumentType.getString(context, "label");
        if (!label.matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Bad probe label");
        }
        return label;
    }

    private static JsonObject report(String action, String label, UUID actor) {
        JsonObject report = new JsonObject();
        report.addProperty("label", label);
        report.addProperty("action", action);
        report.addProperty("actor", actor.toString());
        return report;
    }

    private record Probe(ServerPlayer player, List<String> messages) { }
}
