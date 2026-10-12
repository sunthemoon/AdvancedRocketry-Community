package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldPlacementGameTests {
    static final class Placement implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final FakePlayer player;
        final ItemStack stack = new ItemStack(ModItems.FORCE_FIELD_PROJECTOR.get(), 2);
        Placement(GameTestHelper helper) {
            level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            pos = new BlockPos(2_000_008 + base.getX() * 64, 220, 2_000_008 + base.getZ() * 64);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FieldPlacement"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        InteractionResult place() {
            return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false)));
        }
        @Override public void close() {
            if (level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector
                    && projector.state().owner().isPresent()) {
                FieldCleanupSavedData.get(level.getServer()).forget(projector.identity().key());
            }
            ForceFieldProtection.physical(level, pos, () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void cancelledNativePlacementRollsBackDespiteFakePlayerBreakRefusal(GameTestHelper helper) {
        try (var f = new Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var before = data.save(new net.minecraft.nbt.CompoundTag());
            int count = f.stack.getCount();
            AtomicInteger placements = new AtomicInteger();
            Consumer<BlockEvent.EntityPlaceEvent> vetoPlace = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)) {
                    placements.incrementAndGet(); event.setCanceled(true);
                }
            };
            Consumer<BlockEvent.BreakEvent> vetoBreak = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)
                        && event.getPlayer() instanceof FakePlayer) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, vetoPlace);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, vetoBreak);
            try {
                helper.assertTrue(f.place() == InteractionResult.FAIL && placements.get() == 1,
                        "Native placement did not reach the cancelled Forge transaction");
                helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.AIR) && f.level.getBlockEntity(f.pos) == null
                        && f.stack.getCount() == count && data.save(new net.minecraft.nbt.CompoundTag()).equals(before),
                        "Cancelled ItemStack.useOn left a refunded projector or changed cleanup data");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(vetoPlace); MinecraftForge.EVENT_BUS.unregister(vetoBreak);
            }
        }
        helper.succeed();
    }
    private ForceFieldPlacementGameTests() { }
}
