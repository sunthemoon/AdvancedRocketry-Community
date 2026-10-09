package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Synchronous, loaded, BE-free fixture with an ordinary connected server player, never a FakePlayer. */
final class JackhammerFixture implements AutoCloseable {
    final GameTestHelper helper;
    final ServerLevel level;
    final BlockPos target;
    final JackhammerItem item;
    final AABB lootBounds;
    ServerPlayer player;
    private EmbeddedChannel channel;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private final Set<UUID> initialLoot = new HashSet<>();

    JackhammerFixture(GameTestHelper helper) { this(helper, helper.getLevel()); }

    JackhammerFixture(GameTestHelper helper, ServerLevel level) {
        this.helper = helper;
        this.level = level;
        BlockPos allocation = helper.absolutePos(new BlockPos(2, 2, 2));
        target = new BlockPos(allocation.getX(), 180, allocation.getZ());
        lootBounds = new AABB(target).inflate(1.0D);
        item = (JackhammerItem) ModItems.JACKHAMMER.get();
        helper.assertTrue(CommonConfig.classicEquipmentEnabled(), "Tool fixture requires the enabled control");
        drops().forEach(entity -> initialLoot.add(entity.getUUID()));
        try {
            // Loading is owned test setup, not item admission from an arbitrary client location.
            level.getChunkAt(target);
            for (BlockPos pos : List.of(target, target.east(), target.west(), target.below(), target.west().below())) {
                helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                        "Owned fixture requires loaded BE-free cells");
                before.put(pos, level.getBlockState(pos));
            }
            helper.assertTrue(drops().isEmpty(), "Owned loot bounds are not empty");
            level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.west().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "jackhammerTest"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.teleportTo(level, target.getX() - 0.5D, target.getY(), target.getZ() + 0.5D, 0, 0);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setOnGround(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    ItemStack tool() { return player.getMainHandItem(); }
    List<ItemEntity> drops() { return level.getEntitiesOfClass(ItemEntity.class, lootBounds); }
    void setTarget(Block block) { level.setBlock(target, block.defaultBlockState(), Block.UPDATE_ALL); }

    void action(ServerboundPlayerActionPacket.Action action) {
        player.gameMode.handleBlockBreakAction(target, action, Direction.WEST, level.getMaxBuildHeight(), 0);
    }

    void start() { action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK); }

    void tickProgress(int ticks) {
        helper.assertTrue(ticks >= 0 && ticks <= 40, "Owned native progress check exceeds its bound");
        // Invoke actual native game-mode progress ticks synchronously; this is not elapsed server/wall time.
        for (int tick = 0; tick < ticks; tick++) { player.gameMode.tick(); }
    }

    void unchanged(Block block, net.minecraft.nbt.CompoundTag held) {
        helper.assertTrue(level.getBlockState(target).is(block) && drops().isEmpty()
                && held.equals(tool().save(new net.minecraft.nbt.CompoundTag())), "Refusal mutated target, tool or loot");
    }

    void stoneLootAndWear(int damage) {
        helper.assertTrue(level.getBlockState(target).isAir()
                && level.getBlockState(target.east()).is(Blocks.STONE)
                && level.getBlockState(target.below()).is(Blocks.STONE), "Mining changed more than its one target");
        List<ItemEntity> loot = drops();
        helper.assertTrue(loot.size() == 1 && loot.get(0).getItem().is(Items.COBBLESTONE)
                && loot.get(0).getItem().getCount() == 1 && tool().getDamageValue() == damage,
                "Native qualified loot or tool wear differs");
    }

    @Override public void close() {
        Throwable first = null;
        if (player != null) {
            try {
                level.destroyBlockProgress(player.getId(), target, -1);
                if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                    level.getServer().getPlayerList().remove(player);
                }
            } catch (RuntimeException | Error failure) { first = failure; }
        }
        if (channel != null) {
            try { channel.finishAndReleaseAll(); }
            catch (RuntimeException | Error failure) { first = retain(first, failure); }
        }
        try { drops().stream().filter(entity -> !initialLoot.contains(entity.getUUID())).forEach(ItemEntity::discard); }
        catch (RuntimeException | Error failure) { first = retain(first, failure); }
        for (var entry : before.entrySet()) {
            try {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
                helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue(), "Owned cell was not restored");
            } catch (RuntimeException | Error failure) { first = retain(first, failure); }
        }
        if (first instanceof RuntimeException failure) { throw failure; }
        if (first instanceof Error failure) { throw failure; }
    }

    private static Throwable retain(Throwable first, Throwable failure) {
        if (first == null) { return failure; }
        if (first != failure) { first.addSuppressed(failure); }
        return first;
    }
}
