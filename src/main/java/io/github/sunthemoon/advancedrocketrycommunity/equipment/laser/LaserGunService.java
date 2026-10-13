package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;

/** Server-lifetime transient work: one state per real connected player, no offline or persisted progress. */
final class LaserGunService {
    private final MinecraftServer server;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean executing;
    private record Target(BlockPos pos, BlockState state, UUID entity) { }
    private static final class Session {
        final ServerPlayer player;
        final LaserProgress<Target> work = new LaserProgress<>();
        ServerLevel level;
        ItemStack stack;
        Target committing;
        Session(ServerPlayer player, ItemStack stack) { this.player = player; this.stack = stack; level = player.serverLevel(); }
    }

    LaserGunService(MinecraftServer server) { this.server = server; }

    private boolean connected(ServerPlayer player) {
        return server.isSameThread() && player.getServer() == server && !(player instanceof FakePlayer)
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private boolean usable(ServerPlayer player, ItemStack stack) {
        return connected(player) && CommonConfig.classicEquipmentEnabled() && player.isAlive()
                && !player.isSpectator() && !stack.isEmpty() && stack.getCount() == 1
                && stack.getItem() instanceof BasicLaserGunItem && player.getMainHandItem() == stack
                && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem() == stack;
    }

    void tick(ServerPlayer player, ItemStack stack) {
        if (executing) { return; }
        if (!usable(player, stack)) { stop(player); return; }
        executing = true;
        try {
            Session session = sessions.computeIfAbsent(player.getUUID(), id -> new Session(player, stack));
            if (session.player != player) { sessions.remove(player.getUUID()); return; }
            if (session.level != player.serverLevel() || session.stack != stack) {
                session.work.reset(); session.level = player.serverLevel(); session.stack = stack;
            }
            long now = player.tickCount;
            if (session.work.cooling(now)) { session.work.tick(null, now, 1); return; }
            LoadedLaserTrace.Hit hit = LoadedLaserTrace.trace(player);
            if (hit == null) { session.work.tick(null, now, 1); return; }
            if (hit.entity() != null) {
                Target target = new Target(null, null, hit.entity().getUUID());
                if (session.work.tick(target, now, 1)) {
                    session.work.attempted(now);
                    if (attack(player, stack, hit.entity()) && usable(player, stack)) { sound(player); }
                }
                return; // Even a refused entity attack occludes the block behind it.
            }
            if (!eligible(player, hit.position(), hit.state())) { session.work.tick(null, now, 1); return; }
            int needed = hit.state().getDestroySpeed(player.level(), hit.position()) == 0 ? 1 : LaserProgress.BREAK_TICKS;
            Target target = new Target(hit.position(), hit.state(), null);
            if (!session.work.tick(target, now, needed)) { return; }
            session.work.attempted(now);
            if (!LaserGunProtection.breakingAllowed(player, hit.position()) || !usable(player, stack)) { return; }
            session.committing = target;
            try {
                // Native BreakEvent, adventure restriction, harvest check, removal, loot and XP; no manual drops.
                boolean accepted = player.gameMode.destroyBlock(hit.position());
                if (accepted && player.serverLevel() == session.level && loaded(session.level, hit.position())
                        && session.level.getBlockState(hit.position()) != hit.state() && player.getMainHandItem() == stack) {
                    stack.hurtAndBreak(1, player, actor -> actor.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                    sound(player);
                }
            } finally { session.committing = null; }
        } finally { executing = false; }
    }

    /** Called by the native item hook AFTER BreakEvent, revalidating callbacks before removal. */
    boolean admitBreak(ServerPlayer player, ItemStack stack, BlockPos pos) {
        Session session = sessions.get(player.getUUID());
        if (!executing || session == null || session.committing == null || !usable(player, stack)
                || session.level != player.serverLevel() || session.stack != stack
                || !pos.equals(session.committing.pos()) || !eligible(player, pos, session.committing.state())
                || !LaserGunProtection.areaAllowed(player, pos, pos)) { return false; }
        var hit = LoadedLaserTrace.trace(player);
        return hit != null && hit.entity() == null && pos.equals(hit.position()) && hit.state() == session.committing.state();
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static boolean eligible(ServerPlayer player, BlockPos pos, BlockState expected) {
        ServerLevel level = player.serverLevel();
        if (!loaded(level, pos) || level.getBlockState(pos) != expected || expected.isAir()
                || expected.hasBlockEntity() || level.getBlockEntity(pos) != null) { return false; }
        float hardness = expected.getDestroySpeed(level, pos);
        return Float.isFinite(hardness) && hardness >= 0 && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private boolean attack(ServerPlayer player, ItemStack stack, Entity entity) {
        ServerLevel level = player.serverLevel();
        if (!entity.isAlive() || !entity.isAttackable() || entity.level() != level
                || entity instanceof Player target && (!server.isPvpAllowed() || !player.canHarmPlayer(target))) { return false; }
        var box = entity.getBoundingBox();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() > 64 || box.getYsize() > 64 || box.getZsize() > 64) { return false; }
        if (!LaserGunProtection.areaAllowed(player, BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)) || !ForgeHooks.onPlayerAttackTarget(player, entity)) { return false; }
        // Revalidate after the interaction/protection callbacks. The loaded-only ray supplies exact line of sight.
        var hit = LoadedLaserTrace.trace(player);
        if (hit == null || hit.entity() != entity || !usable(player, stack) || player.serverLevel() != level
                || entity.level() != level || !entity.isAlive() || !entity.isAttackable()
                || entity instanceof Player target && (!server.isPvpAllowed() || !player.canHarmPlayer(target))
                || !LaserGunProtection.areaAllowed(player, BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ)) || entity.skipAttackInteraction(player)) { return false; }
        return entity.hurt(player.damageSources().playerAttack(player), 1.0F);
    }

    private static void sound(ServerPlayer player) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.classic(ClassicSound.BASIC_LASER_GUN), ClassicSound.BASIC_LASER_GUN.source(), 1, 1);
    }

    void stop(ServerPlayer player) {
        if (!server.isSameThread()) { return; }
        Session session = sessions.get(player.getUUID());
        if (session != null) { session.work.reset(); }
    }
    void logout(ServerPlayer player) { if (server.isSameThread()) { sessions.remove(player.getUUID()); } }
    void sweep() {
        if (!server.isSameThread()) { return; }
        sessions.values().removeIf(session -> {
            if (!connected(session.player)) { return true; }
            if (!usable(session.player, session.stack) || session.level != session.player.serverLevel()) {
                session.work.reset();
                return !session.work.cooling(session.player.tickCount);
            }
            return false;
        });
    }
    int tracked() { return sessions.size(); }
    void clear() { sessions.clear(); }
}
