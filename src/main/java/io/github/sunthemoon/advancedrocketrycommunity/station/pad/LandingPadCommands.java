package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local management and bounded named selection; ordinary flight-console authority is never bypassed. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class LandingPadCommands {
    private LandingPadCommands() { }

    @SubscribeEvent public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arcepad")
                .then(Commands.literal("register").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> local(context, false))))
                .then(Commands.literal("remove").executes(context -> local(context, true)))
                .then(Commands.literal("list").then(Commands.argument("station", UuidArgument.uuid())
                        .executes(LandingPadCommands::list)))
                .then(Commands.literal("select").then(Commands.argument("station", UuidArgument.uuid())
                        .then(Commands.argument("pad", UuidArgument.uuid()).then(Commands.argument("epoch", LongArgumentType.longArg(0))
                                .executes(LandingPadCommands::select))))));
    }

    private static int local(CommandContext<CommandSourceStack> context, boolean remove) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        HitResult hit = player.pick(8, 0, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            player.sendSystemMessage(text("unavailable")); return 0;
        }
        return interact(player, player.serverLevel(), block.getBlockPos(), remove,
                remove ? "Landing Pad" : StringArgumentType.getString(context, "name")) ? 1 : 0;
    }

    public static boolean interact(ServerPlayer player, ServerLevel level, BlockPos position, boolean remove, String name) {
        if (!valid(player) || player.serverLevel() != level || player.distanceToSqr(position.getX() + .5,
                position.getY() + .5, position.getZ() + .5) > 64
                || LandingPadService.loadedPad(level, position) == null
                || !LandingPadIndex.get(level.getServer()).allowInteraction(player.getUUID(), level.getGameTime())) {
            player.sendSystemMessage(text("unavailable")); return false;
        }
        try {
            if (remove) {
                if (!LandingPadService.remove(level, position, player.getUUID(), player.hasPermissions(2))) {
                    player.sendSystemMessage(text("unavailable")); return false;
                }
                player.sendSystemMessage(text("removed"));
            } else {
                LandingPadEntry entry = LandingPadService.register(level, position, player.getUUID(), player.hasPermissions(2), name);
                player.sendSystemMessage(text("registered", entry.name(), entry.padId().toString(), entry.epoch()));
            }
            return true;
        } catch (IllegalArgumentException | IllegalStateException refused) {
            player.sendSystemMessage(text("refused")); return false;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var stationId = UuidArgument.getUuid(context, "station");
        var station = StationRegistrySavedData.get(player.getServer()).find(stationId).orElse(null);
        if (!valid(player) || station == null || !new StationAccessService().allowed(station,
                player.getUUID(), player.hasPermissions(2), StationAccessAction.VISIT)) {
            player.sendSystemMessage(text("refused")); return 0;
        }
        var index = LandingPadIndex.get(player.getServer());
        if (!index.operational()) { player.sendSystemMessage(text("unavailable")); return 0; }
        var entries = index.entries(stationId);
        player.sendSystemMessage(text("list", station.name(), entries.size()));
        for (var entry : entries) {
            boolean loaded = LandingPadService.resolve(player.getServer(), stationId, entry.reference()) != null;
            String command = "/arcepad select " + stationId + " " + entry.padId() + " " + entry.epoch();
            player.sendSystemMessage(text("entry", entry.name(), entry.padId().toString(), entry.epoch(),
                    text(loaded ? "loaded" : "unloaded")).withStyle(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))));
        }
        return entries.size();
    }

    private static int select(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var stationId = UuidArgument.getUuid(context, "station");
        var reference = new LandingPadReference(UuidArgument.getUuid(context, "pad"), LongArgumentType.getLong(context, "epoch"));
        var station = StationRegistrySavedData.get(player.getServer()).find(stationId).orElse(null);
        var entry = LandingPadService.resolve(player.getServer(), stationId, reference);
        if (!valid(player) || station == null || entry == null || !new StationAccessService().allowed(station,
                player.getUUID(), player.hasPermissions(2), StationAccessAction.VISIT)
                || !LandingPadIndex.get(player.getServer()).choose(player.getUUID(), stationId, reference)) {
            player.sendSystemMessage(text("refused")); return 0;
        }
        player.sendSystemMessage(text("selected", entry.name()));
        return 1;
    }

    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { LandingPadIndex.get(player.getServer()).forgetChoice(player.getUUID()); }
    }

    @SubscribeEvent public static void guardBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof LandingPadBlockEntity pad)) { return; }
        if (pad.operational() && pad.reference() == null) { return; }
        try {
            if (!(event.getPlayer() instanceof ServerPlayer player) || !valid(player)
                    || !LandingPadService.remove(level, event.getPos(), player.getUUID(), player.hasPermissions(2))) {
                event.setCanceled(true);
            }
        } catch (IllegalArgumentException refused) { event.setCanceled(true); }
    }

    private static boolean valid(ServerPlayer player) {
        return player.getServer() != null && player.getServer().isSameThread() && player.connection != null
                && player.connection.connection.isConnected() && player.isAlive() && !player.isSpectator() && !(player instanceof FakePlayer);
    }
    @SubscribeEvent public static void saving(ChunkDataEvent.Save event) {
        io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.refuse(event,
                    "Chunk block-entity count exceeds physical bound");
        }
        for (var tag : entities) {
            var entity = (net.minecraft.nbt.CompoundTag) tag;
            if (entity.getString("id").equals("advancedrocketrycommunity:landing_pad") && entity.contains("landing_pad")
                    && !io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt.fits(entity.get("landing_pad"), 512, 3, 16)) {
                io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.refuse(event,
                        "Refusing oversized landing-pad chunk save; back up and repair first");
            }
        }
    }
    private static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.translatable("message.advancedrocketrycommunity.pad." + key, args);
    }
}
