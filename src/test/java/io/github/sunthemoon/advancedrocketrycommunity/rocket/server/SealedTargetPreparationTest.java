package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedTargetPreparationTest {
    private static final VanillaContainerRocketAdapter VANILLA = new VanillaContainerRocketAdapter();
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void preparationIsDetachedAndAbortEmptiesTheRetainedBuiltInReference() throws Exception {
        for (var state : List.of(Blocks.CHEST.defaultBlockState(), Blocks.BARREL.defaultBlockState())) {
            var prepared = prepare(state, inventory(), RocketBlockEntityAdapters.defaults()).orElseThrow();
            var target = target(prepared);
            assertNull(target.getLevel()); assertFalse(target.isRemoved());
            assertEquals(7, ((Container) target).getItem(0).getCount());
            close(prepared); close(prepared);
            assertTrue(target.isRemoved()); assertTrue(((Container) target).isEmpty());
        }
    }
    @Test void abortAlsoRemovesLazyLootAuthorityWithoutUnpackingIt() throws Exception {
        var source = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        source.setLootTable(ResourceLocation.tryParse("minecraft:chests/simple_dungeon"), 37);
        var prepared = prepare(Blocks.CHEST.defaultBlockState(), VANILLA.capture(source), RocketBlockEntityAdapters.defaults()).orElseThrow();
        var target = (RandomizableContainerBlockEntity) target(prepared);
        assertNotNull(target.lootTable); close(prepared);
        assertNull(target.lootTable); assertEquals(0, target.lootTableSeed); assertTrue(target.isRemoved());
    }
    @Test void falseThrowAndRecaptureMismatchLeaveNoResourcesInDiscardedTargets() throws Exception {
        for (String failure : List.of("false", "throw", "mismatch")) {
            var retained = new AtomicReference<BlockEntity>(); var payload = inventory();
            var adapter = new RocketBlockEntityAdapter() {
                public ResourceLocation id() { return VANILLA.id(); }
                public boolean supports(BlockEntity target) { return VANILLA.supports(target); }
                public RocketBlockEntityPayload capture(BlockEntity target) {
                    assertNull(target.getLevel());
                    if (failure.equals("mismatch")) { ((Container) target).setItem(1, new ItemStack(Items.STICK)); }
                    return VANILLA.capture(target);
                }
                public boolean restore(BlockEntity target, RocketBlockEntityPayload value) {
                    retained.set(target); assertNull(target.getLevel()); assertTrue(VANILLA.restore(target, value));
                    if (failure.equals("throw")) { throw new IllegalArgumentException("fixture restore failure"); }
                    return !failure.equals("false");
                }
            };
            assertTrue(prepare(Blocks.CHEST.defaultBlockState(), payload, new RocketBlockEntityAdapters(List.of(adapter))).isEmpty());
            assertNotNull(retained.get()); assertNull(retained.get().getLevel()); assertTrue(retained.get().isRemoved());
            assertTrue(((Container) retained.get()).isEmpty()); assertEquals(7, payload.data().getList("Items", 10).getCompound(0).getByte("Count"));
        }
    }
    @Test void publishedTargetsAreNeverEmptiedByLaterCleanup() throws Exception {
        var prepared = prepare(Blocks.CHEST.defaultBlockState(), inventory(), RocketBlockEntityAdapters.defaults()).orElseThrow();
        var target = target(prepared);
        prepared.getClass().getMethod("published").invoke(prepared); close(prepared);
        assertFalse(target.isRemoved()); assertEquals(7, ((Container) target).getItem(0).getCount());
    }
    @Test void plainMembersNeedNoTargetAndPayloadShapeMismatchesRefuse() throws Exception {
        var plain = prepare(Blocks.IRON_BLOCK.defaultBlockState(), null, RocketBlockEntityAdapters.defaults()).orElseThrow();
        assertNull(target(plain)); close(plain);
        assertTrue(prepare(Blocks.IRON_BLOCK.defaultBlockState(), inventory(), RocketBlockEntityAdapters.defaults()).isEmpty());
        assertTrue(prepare(Blocks.CHEST.defaultBlockState(), null, RocketBlockEntityAdapters.defaults()).isEmpty());
        assertTrue(prepare(Blocks.AIR.defaultBlockState(), null, RocketBlockEntityAdapters.defaults()).isEmpty());
    }
    private static RocketBlockEntityPayload inventory() {
        var chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        return VANILLA.capture(chest);
    }
    @SuppressWarnings("unchecked") private static Optional<Object> prepare(BlockState state, RocketBlockEntityPayload payload,
            RocketBlockEntityAdapters adapters) throws Exception {
        return (Optional<Object>) Class.forName(SealedTargetPreparationTest.class.getPackageName() + ".SealedTargetPreparation")
                .getMethod("prepare", Thread.class, BlockPos.class, BlockState.class, RocketBlockEntityPayload.class, RocketBlockEntityAdapters.class)
                .invoke(null, Thread.currentThread(), BlockPos.ZERO, state, payload, adapters);
    }
    private static BlockEntity target(Object prepared) throws Exception { return (BlockEntity) prepared.getClass().getMethod("target").invoke(prepared); }
    private static void close(Object prepared) throws Exception { ((AutoCloseable) prepared).close(); }
}
