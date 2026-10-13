package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Connected real player and owned BE-free cells; all native use ticks execute synchronously. */
final class LaserGunFixture implements AutoCloseable {
    final GameTestHelper helper;
    final ServerLevel level;
    final BlockPos target;
    final BasicLaserGunItem gun;
    ServerPlayer player;
    private EmbeddedChannel channel;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private final java.util.Set<UUID> initialLoot = new java.util.HashSet<>();

    LaserGunFixture(GameTestHelper helper) { this(helper, helper.getLevel()); }
    LaserGunFixture(GameTestHelper helper, ServerLevel level) {
        this.helper = helper; this.level = level;
        BlockPos allocation = helper.absolutePos(new BlockPos(2, 2, 2));
        target = new BlockPos(allocation.getX(), 180, allocation.getZ());
        gun = (BasicLaserGunItem) ModItems.BASIC_LASER_GUN.get();
        drops().forEach(entity -> initialLoot.add(entity.getUUID()));
        try {
            for (int x = -4; x <= 3; x++) {
                BlockPos pos = target.offset(x, 0, 0);
                // Explicit test-owned loading only; production tracing never performs this call.
                level.getChunkAt(pos);
                helper.assertTrue(level.getBlockEntity(pos) == null, "Owned cells must be BE-free");
                before.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            set(Blocks.STONE);
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "laserGunTest"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setNoGravity(true);
            aim(target);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(gun));
            helper.assertTrue(LaserGunRuntime.service() != null, "Installed laser service missing");
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    void aim(BlockPos pos) {
        player.teleportTo(level, pos.getX() - 2.5, pos.getY() - 1, pos.getZ() + 0.5, -90, 0);
        player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
    }
    ItemStack tool() { return player.getMainHandItem(); }
    void set(Block block) { level.setBlock(target, block.defaultBlockState(), Block.UPDATE_ALL); }
    void start() {
        var result = player.gameMode.useItem(player, level, tool(), InteractionHand.MAIN_HAND);
        helper.assertTrue(result.consumesAction() && player.isUsingItem(), "Native item use did not start");
    }
    void ticks(int count) {
        helper.assertTrue(count >= 0 && count <= 100, "Native fixture tick budget exceeded");
        for (int tick = 0; tick < count; tick++) {
            // Native Level tick advances Entity.tickCount and ServerPlayer.tick; the connection phase
            // executes Player.tick -> LivingEntity item-use -> Item.onUseTick. No fabricated progress/target.
            level.tickNonPassenger(player);
            player.doTick();
        }
    }
    void unchanged(Block block, int damage) {
        helper.assertTrue(level.getBlockState(target).is(block) && tool().getDamageValue() == damage
                && drops().isEmpty(), "Refusal changed the block, durability or loot");
    }
    java.util.List<ItemEntity> drops() { return level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2)); }
    @Override public void close() {
        if (player != null) {
            player.stopUsingItem();
            if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                level.getServer().getPlayerList().remove(player);
            }
        }
        if (channel != null) { channel.finishAndReleaseAll(); }
        drops().stream().filter(entity -> !initialLoot.contains(entity.getUUID())).forEach(ItemEntity::discard);
        before.forEach((pos, state) -> {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(pos) == state, "Owned cell was not restored");
        });
    }
}
