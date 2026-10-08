package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native placement/queue observations; no classic owner authority or new registration. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockEntityLoadOrderingGameTests {
    @GameTest(template = "empty", batch = "native_load_order", timeoutTicks = 20)
    public static void survivalPlacementIsInstalledAndSerializableBeforeTheNextTick(GameTestHelper helper) {
        place(helper, GameType.SURVIVAL, 1);
    }

    @GameTest(template = "empty", batch = "native_load_order", timeoutTicks = 20)
    public static void creativePlacementPreservesSourceAndDoesNotQueueTheNewOwner(GameTestHelper helper) {
        place(helper, GameType.CREATIVE, 2);
    }

    private static void place(GameTestHelper helper, GameType mode, int remaining) {
        Fixture fixture = new Fixture(helper);
        try {
            ServerPlayer player = fixture.player;
            ItemStack source = new ItemStack(Items.CHEST, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, source);
            player.gameMode.changeGameModeForPlayer(mode);
            BlockPos support = fixture.target.below();
            var result = player.gameMode.useItemOn(player, fixture.level, source, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5D, 0), Direction.UP, support, false));
            helper.assertTrue(result.consumesAction(), "Native game-mode placement did not complete");
            BlockEntity owner = fixture.level.getBlockEntity(fixture.target);
            helper.assertTrue(owner instanceof ChestBlockEntity && owner.getLevel() == fixture.level
                    && !owner.isRemoved(), "Placement did not install the registered native chest owner");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND) == source
                    && source.is(Items.CHEST) && source.getCount() == remaining && !source.hasTag(),
                    "Native source identity/count/tag differs from the selected game mode");
            assertNotQueued(helper, fixture.level, owner);
            var saved = owner.saveWithFullMetadata();
            helper.assertTrue(saved.getString("id").equals("minecraft:chest")
                    && saved.getInt("x") == fixture.target.getX() && saved.getInt("y") == fixture.target.getY()
                    && saved.getInt("z") == fixture.target.getZ() && saved.getList("Items", 10).isEmpty(),
                    "Pre-tick native serialization did not retain the empty installed owner");
            assertNotQueued(helper, fixture.level, owner);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(fixture.level.getBlockEntity(fixture.target) == owner && !owner.isRemoved(),
                        "Natural world tick replaced the installed owner");
                assertNotQueued(helper, fixture.level, owner);
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close(failure);
            throw failure;
        }
    }

    /** Read-only, bounded test observation; never drain, enqueue or synthesize onLoad. */
    private static void assertNotQueued(GameTestHelper helper, Level level, BlockEntity owner) {
        try {
            for (String name : new String[] { "freshBlockEntities", "pendingFreshBlockEntities" }) {
                Field field = Level.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(level);
                helper.assertTrue(value instanceof Collection<?>, "Native fresh-owner queue type changed");
                Collection<?> queue = (Collection<?>) value;
                helper.assertTrue(queue.size() <= 4_096, "Native queue observation exceeded the test bound");
                for (Object queued : queue) {
                    helper.assertTrue(queued != owner, "New placement was deferred to a fresh-owner tick queue");
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native queue observation unavailable", failure);
        }
    }

    /** Owns two loaded BE-free cells and one helper-created mock server player. */
    private static final class Fixture implements GameTestListener {
        private final ServerLevel level;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private ServerPlayer player;
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            BlockPos allocation = helper.absolutePos(new BlockPos(1, 1, 1));
            target = new BlockPos(allocation.getX(), 180, allocation.getZ());
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(this);
                for (BlockPos cell : new BlockPos[] { target, target.below() }) {
                    helper.assertTrue(!level.isOutsideBuildHeight(cell) && level.hasChunkAt(cell)
                            && level.getBlockEntity(cell) == null, "Fixture needs loaded BE-free cells");
                    before.put(cell, level.getBlockState(cell));
                }
                helper.assertTrue(level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                        || level.getBlockState(target).isAir(), "Fixture target was not cleared");
                helper.assertTrue(level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL)
                        || level.getBlockState(target.below()).is(Blocks.STONE), "Fixture support was not installed");
                player = helper.makeMockServerPlayerInLevel();
                player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
            } catch (ReflectiveOperationException failure) {
                close(failure);
                throw new IllegalStateException("Fixture terminal listener unavailable", failure);
            } catch (RuntimeException | Error failure) {
                close(failure);
                throw failure;
            }
        }

        private void close(Throwable primary) {
            if (closed) { return; }
            closed = true;
            Throwable first = primary;
            if (player != null) {
                try { level.getServer().getPlayerList().remove(player); }
                catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            for (var entry : before.entrySet()) {
                try { level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL); }
                catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (primary == null) {
                if (first instanceof RuntimeException failure) { throw failure; }
                if (first instanceof Error failure) { throw failure; }
            }
        }

        private static Throwable retain(Throwable first, Throwable failure) {
            if (first == null) { return failure; }
            if (first != failure) { first.addSuppressed(failure); }
            return first;
        }

        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(null); }
        @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
    }
}
