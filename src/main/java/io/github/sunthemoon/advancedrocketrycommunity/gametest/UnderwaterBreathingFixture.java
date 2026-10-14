package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Owns one bounded pool and one connected, non-FakePlayer with complete synchronous cleanup. */
final class UnderwaterBreathingFixture implements AutoCloseable {
    final ServerLevel level;
    final BlockPos pool;
    final GameTestHelper helper;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    ServerPlayer player;
    private EmbeddedChannel channel;

    UnderwaterBreathingFixture(GameTestHelper helper, ServerLevel level) {
        this.helper = helper;
        this.level = level;
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        pool = new BlockPos(origin.getX(), 196, origin.getZ());
        try {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    for (int y = -1; y <= 3; y++) {
                        BlockPos pos = pool.offset(x, y, z);
                        level.getChunkAt(pos); // Explicit fixture setup, not the life-support adapter.
                        helper.assertTrue(level.getBlockEntity(pos) == null, "Pool would replace a block entity");
                        before.put(pos, level.getBlockState(pos));
                        boolean wall = x == -2 || x == 2 || z == -2 || z == 2 || y == -1;
                        level.setBlock(pos, (wall ? Blocks.GLASS : Blocks.WATER).defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "underwaterTest"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.CREATIVE);
            // Expire native login damage protection without driving LivingTick or altering suit cadence.
            for (int tick = 0; tick < 61; tick++) { player.tick(); }
            player.teleportTo(level, pool.getX() + 0.5D, pool.getY(), pool.getZ() + 0.5D, 0, 0);
            player.setNoGravity(true);
            submerge();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(20.0F);
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void submerge() {
        player.setPos(pool.getX() + 0.5D, pool.getY(), pool.getZ() + 0.5D);
        // Refresh the native eye-fluid cache before the first observed LivingTick, just as Entity.baseTick does.
        player.baseTick();
        helper.assertTrue(player.isEyeInFluid(FluidTags.WATER), "Player's actual eye cell is not submerged");
    }

    ItemStack suit(int oxygen) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));
        ItemStack chest = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
        helper.assertTrue(SpaceSuitOxygen.set(chest, oxygen), "Could not seed native suit oxygen");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        return chest;
    }

    void tick() {
        // Native server lifecycle + the Player.tick path used by the network listener, not synthetic breathing.
        player.tick();
        player.doTick();
        helper.assertTrue(player.isEyeInFluid(FluidTags.WATER), "Native player tick left the pool");
    }

    @Override
    public void close() {
        try {
            if (player != null && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                level.getServer().getPlayerList().remove(player);
            }
        } finally {
            try {
                if (channel != null) { channel.finishAndReleaseAll(); }
            } finally {
                before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            }
        }
    }
}
