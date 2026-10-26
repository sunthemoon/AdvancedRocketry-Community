package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedTargetPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExternalRocketBlockEntityAdapterDetachedTest {
    private static final ResourceLocation ID = ResourceLocation.tryParse("fixture:detached");
    private static final VanillaContainerRocketAdapter VANILLA = new VanillaContainerRocketAdapter();
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void detachedProviderRestoresAndRecapturesWithoutWeakeningTheOrdinaryEntryPoints() {
        var fixture = new Fixture("");
        var prepared = fixture.prepare().orElseThrow();
        var target = prepared.target();
        assertNull(target.getLevel()); assertEquals(7, ((Container) target).getItem(0).getCount());
        assertEquals(3, fixture.calls.get());
        assertThrows(IllegalStateException.class, () -> fixture.adapter.capture(target));
        assertThrows(IllegalStateException.class, () -> fixture.adapter.restore(target, payload()));
        assertEquals(3, fixture.calls.get()); prepared.close();
        assertTrue(target.isRemoved()); assertTrue(((Container) target).isEmpty());
    }

    @Test void wrongThreadAndRemovedTargetsNeverInvokeTheProvider() throws Exception {
        var fixture = new Fixture(""); var target = chest(); var owner = Thread.currentThread();
        var rejected = new AtomicReference<Throwable>();
        var other = new Thread(() -> {
            try { fixture.adapter.restoreDetached(owner, target, payload()); }
            catch (Throwable failure) { rejected.set(failure); }
        });
        other.start(); other.join();
        assertInstanceOf(IllegalStateException.class, rejected.get()); assertEquals(0, fixture.calls.get());
        target.setRemoved();
        assertThrows(IllegalStateException.class, () -> fixture.adapter.captureDetached(owner, target));
        assertThrows(IllegalStateException.class, () -> fixture.adapter.restoreDetached(owner, target, payload()));
        assertEquals(0, fixture.calls.get());
    }

    @Test void providerRequiringLevelIsHeldAndItsUnpublishedReferenceIsRemoved() {
        var fixture = new Fixture("needs-level");
        assertTrue(fixture.prepare().isEmpty());
        assertTrue(fixture.retained.get().isRemoved()); assertTrue(((Container) fixture.retained.get()).isEmpty());
        assertEquals(1, fixture.calls.get());
    }

    @Test void allDetachedCallbacksRetainTheExistingReturnedTimeBudget() {
        for (String phase : List.of("restore", "canMove", "capture")) {
            var fixture = new Fixture(phase);
            assertTrue(fixture.prepare().isEmpty(), phase);
            assertTrue(fixture.retained.get().isRemoved()); assertTrue(((Container) fixture.retained.get()).isEmpty());
            assertEquals(7, payload().data().getCompound("data").getList("Items", 10).getCompound(0).getByte("Count"));
        }
    }

    private static ChestBlockEntity chest() { return new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
    private static RocketBlockEntityPayload payload() {
        var source = chest(); source.setItem(0, new ItemStack(Items.DIAMOND, 7));
        return RocketAdapterPayloads.encode(ID, 1, VANILLA.capture(source).data());
    }
    private static final class Fixture {
        final AtomicLong clock = new AtomicLong();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<BlockEntity> retained = new AtomicReference<>();
        final ExternalRocketBlockEntityAdapter adapter;
        Fixture(String failure) {
            adapter = new ExternalRocketBlockEntityAdapter(ID, Set.of(ResourceLocation.tryParse("minecraft:chest")), 1,
                    new RocketBlockEntityAdapter() {
                        private void enter(String phase, BlockEntity target) {
                            assertNull(target.getLevel()); calls.incrementAndGet();
                            if (phase.equals(failure)) { clock.addAndGet(5_000_001); }
                        }
                        public boolean canMove(BlockEntity target) { enter("canMove", target); return true; }
                        public CompoundTag capture(BlockEntity target) { enter("capture", target); return VANILLA.capture(target).data(); }
                        public boolean restore(BlockEntity target, CompoundTag body) {
                            retained.set(target); enter("restore", target);
                            if (failure.equals("needs-level")) { return target.getLevel() != null; }
                            return VANILLA.restore(target, new RocketBlockEntityPayload(VANILLA.id(), body));
                        }
                    }, clock::get);
        }
        java.util.Optional<SealedTargetPreparation> prepare() {
            return SealedTargetPreparation.prepare(Thread.currentThread(), BlockPos.ZERO, Blocks.CHEST.defaultBlockState(),
                    payload(), new RocketBlockEntityAdapters(List.of(adapter)));
        }
    }
}
