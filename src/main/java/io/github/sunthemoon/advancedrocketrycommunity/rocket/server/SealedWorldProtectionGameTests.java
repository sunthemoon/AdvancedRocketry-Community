package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedWorldProtectionGameTests {
    @GameTest(template = "rocket_test", batch = "w152_world_protection", timeoutTicks = 400)
    public static void retainedCarrierSurvivesNativeBreakPistonExplosionFluidFireAndSupportUpdates(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(4, 4, 4)); var fence = at.offset(3, 0, 3);
        var source = new ChestBlockEntity(at, Blocks.CHEST.defaultBlockState()); source.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var payload = new VanillaContainerRocketAdapter().capture(source);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), SealedUnsealGameTests.position(at),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(Blocks.CHEST.defaultBlockState()), payload),
                        new RocketBlock(new RocketPosition(3, 0, 3), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(2, 200, 1000, 1000, 1, 1, 1, 1), level.getGameTime());
        var id = UUID.randomUUID(); var entity = UUID.randomUUID(); var data = RocketTransactionSavedData.get(level.getServer());
        data.journalFor(snapshot, UUID.randomUUID()).write(new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.RESTORED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, entity));
        // W129aM transition (ADR-074 item 8): a request; the held recovery waits for its acknowledgement by itself.
        data.requestCheckpoint(level.getServer()); level.setBlock(fence, Blocks.BEDROCK.defaultBlockState(), 3);
        var carrier = SealedUnsealGameTests.install(level, at, new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                snapshot.sourceDimension(), SealedUnsealGameTests.position(at), 0, snapshot.blocks().get(0).state(), payload));
        var held = carrier.saveWithoutMetadata();
        var player = FakePlayerFactory.getMinecraft(level); var oldMode = player.gameMode.getGameModeForPlayer();
        try {
            for (var mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                player.gameMode.changeGameModeForPlayer(mode);
                helper.assertTrue(!player.gameMode.destroyBlock(at), "Native player break removed C in " + mode);
            }
        } finally { player.gameMode.changeGameModeForPlayer(oldMode); }
        for (var type : List.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.ENDERMAN)) {
            helper.assertTrue(!carrier.getBlockState().canEntityDestroy(level, at, type.create(level)), "Entity destruction accepts C");
        }
        helper.assertTrue(Block.getDrops(carrier.getBlockState(), level, at, carrier).isEmpty(), "C produced a loot item");
        level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(at.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
        level.setBlock(at.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        var began = level.getGameTime(); var done = new boolean[1];
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            long age = level.getGameTime() - began;
            if (age == 10) {
                helper.assertTrue(!level.getBlockState(at.west()).getValue(PistonBaseBlock.EXTENDED), "Powered piston moved the carrier");
                level.explode(null, at.getX() + .5, at.getY() + .5, at.getZ() + .5, 3F, Level.ExplosionInteraction.TNT);
            }
            if (age == 20) { level.setBlock(at.above(), Blocks.FIRE.defaultBlockState(), 3); }
            if (age == 30) { level.setBlock(at.north(), Blocks.WATER.defaultBlockState(), 3); }
            if (age < 60) {
                helper.assertTrue(level.getBlockEntity(at) == carrier && held.equals(carrier.saveWithoutMetadata()), "Native world update changed C");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5)).stream()
                        .noneMatch(drop -> drop.getItem().is(Items.DIAMOND) || drop.getItem().is(ModBlocks.SEALED_ROCKET_BLOCK.get().asItem())),
                        "Retained C emitted a resource/carrier drop");
            }
            if (age == 60) { level.setBlock(fence, Blocks.AIR.defaultBlockState(), 3); }
            if (level.getBlockEntity(at) instanceof ChestBlockEntity restored) {
                helper.assertTrue(data.acknowledgedAbsent(id, snapshot.snapshotId(), entity), "Protection ended before checked U absence");
                helper.assertTrue(restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount() == 7,
                        "Native world updates lost the retained payload");
                done[0] = true; helper.succeed();
            }
        });
    }
    private SealedWorldProtectionGameTests() { }
}
