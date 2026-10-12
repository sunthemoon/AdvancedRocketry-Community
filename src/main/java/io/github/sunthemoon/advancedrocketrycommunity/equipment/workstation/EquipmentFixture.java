package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Connected ordinary player and one owned loaded cell, with complete fixture cleanup. */
final class EquipmentFixture implements AutoCloseable {
    final GameTestHelper helper;
    final ServerLevel level;
    final BlockPos position;
    final BlockState before;
    final AABB bounds;
    final Set<UUID> initialDrops = new HashSet<>();
    ServerPlayer player;
    EmbeddedChannel channel;
    SuitWorkstationBlockEntity station;
    SuitWorkstationMenu menu;
    EquipmentFixture(GameTestHelper helper) {
        this.helper = helper; level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)); position = new BlockPos(origin.getX(), 196, origin.getZ());
        level.getChunkAt(position); before = level.getBlockState(position); bounds = new AABB(position).inflate(2);
        helper.assertTrue(level.getBlockEntity(position) == null, "Fixture must not replace a block entity");
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(entity -> initialDrops.add(entity.getUUID()));
        try {
            level.setBlock(position, SuitEquipmentContent.BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            station = (SuitWorkstationBlockEntity) level.getBlockEntity(position);
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "equipmentTest"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.teleportTo(level, position.getX() + .5, position.getY() + 1, position.getZ() + .5, 0, 0);
            menu = new SuitWorkstationMenu(42, player.getInventory(), station); player.containerMenu = menu;
            helper.assertTrue(station.admitted(player), "Fixture must have server authority");
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    void put(int slot, ItemStack stack) { helper.assertTrue(station.replace(player, slot, stack), "Fixture insertion refused"); }
    void clear() { put(0, ItemStack.EMPTY); put(1, ItemStack.EMPTY); }
    @Override public void close() {
        try {
            if (player != null && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                player.containerMenu = player.inventoryMenu; level.getServer().getPlayerList().remove(player);
            }
        } finally {
            try {
                if (channel != null) { channel.finishAndReleaseAll(); }
                level.setBlock(position, before, Block.UPDATE_ALL);
            } finally {
                level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(entity -> !initialDrops.contains(entity.getUUID())).forEach(ItemEntity::discard);
            }
        }
    }
}
