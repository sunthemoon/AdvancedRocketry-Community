package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * W294 (finding 6-4): at exactly zero gravity nothing presses a player into the block under their feet, so vanilla
 * reports {@code onGround} false for a player standing on a station platform, and {@code Player.getDigSpeed} divides
 * their dig speed by 5 as if they were airborne. This lifts that one factor, and only while a block's collision shape
 * is within 1/16 block below the feet, the support probe of the station safety return. Jumping, movement, falls and the
 * safety return are unchanged.
 *
 * <p>Common code with no state: the client predicts the same speed from the synchronized gravity attribute and its own
 * loaded blocks, so its break progress and the server's check agree. The probe reads only loaded chunks.
 */
public final class ZeroGravityFooting {
    /** {@code Player.getDigSpeed}'s divisor for a player who is not on the ground (1.20.1). */
    static final float AIRBORNE_DIG_DIVISOR = 5.0F;
    private static final double SUPPORT_DEPTH = 0.0625D;

    @SubscribeEvent
    public void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (restingAtZeroGravity(event.getEntity())) {
            event.setNewSpeed(event.getNewSpeed() * AIRBORNE_DIG_DIVISOR);
        }
    }

    /** Not on the ground only because gravity is exactly zero, with a block's collision shape just below the feet. */
    public static boolean restingAtZeroGravity(Player player) {
        if (player == null || player.onGround() || player.isSpectator() || player.getAbilities().flying
                || player.isPassenger() || player.isFallFlying()
                || CelestialGravityController.appliedMultiplier(player) != 0.0D) {
            return false;
        }
        AABB body = player.getBoundingBox();
        // At most a normal-sized footprint and its collision iterator's one-cell margin; never scan down to a floor.
        if (body.getXsize() > 2.0D || body.getZsize() > 2.0D) {
            return false;
        }
        AABB support = new AABB(body.minX, body.minY - SUPPORT_DEPTH, body.minZ, body.maxX, body.minY, body.maxZ);
        Level level = player.level();
        if (!level.hasChunksAt(BlockPos.containing(support.minX - 1, support.minY - 1, support.minZ - 1),
                BlockPos.containing(support.maxX + 1, support.maxY + 1, support.maxZ + 1))) {
            return false;
        }
        return level.getBlockCollisions(player, support).iterator().hasNext();
    }
}
