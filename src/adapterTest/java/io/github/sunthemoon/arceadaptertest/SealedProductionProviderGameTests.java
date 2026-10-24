package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.InvWrapper;

/** The actual host materializer, not the platform probe's publication implementation. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedProductionProviderGameTests {
    private static final String ACCESS = "io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedProductionTestAccess";
    private static final ThreadLocal<Observation> ACTIVE = new ThreadLocal<>();

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_provider", timeoutTicks = 100)
    public static void sealedProviderFixtureHasAnIndependentNativeType(GameTestHelper helper) {
        var id = AdapterTestMod.id("sealed_cargo_container");
        var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id);
        helper.assertTrue(block instanceof net.minecraft.world.level.block.EntityBlock, "Missing independent sealed provider fixture");
        var target = ((net.minecraft.world.level.block.EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        helper.assertTrue(target instanceof FixtureContainerBlockEntity && target.getType() != AdapterTestMod.CONTAINER_TYPE.get()
                && id.equals(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(target.getType())),
                "Sealed provider shares the legacy fixture's native type");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_provider", timeoutTicks = 100)
    public static void failedProductionPreparationRetainsCAndRejectsRemovedReferenceAccess(GameTestHelper helper) {
        var at = helper.absolutePos(new BlockPos(4, 2, 4));
        for (var mode : List.of(FixtureAdapterFaults.Mode.RESTORE_FALSE, FixtureAdapterFaults.Mode.RESTORE_THROW,
                FixtureAdapterFaults.Mode.RESTORE_SLOW, FixtureAdapterFaults.Mode.RESTORE_MISMATCH,
                FixtureAdapterFaults.Mode.RESTORE_NEEDS_LEVEL, FixtureAdapterFaults.Mode.RESTORE_NEEDS_INSTALLED)) {
            var carrier = stage(helper.getLevel(), at); var saved = carrier.saveWithoutMetadata();
            var observation = new Observation(helper, at, carrier, "never", false);
            ACTIVE.set(observation);
            try {
                FixtureAdapterFaults.duringRestore(new FixtureAdapterFaults.Probe(mode),
                        () -> helper.assertTrue(!unseal(carrier), "Failed provider published: " + mode));
                helper.assertTrue(helper.getLevel().getBlockEntity(at) == carrier && saved.equals(carrier.saveWithoutMetadata()),
                        "Failed preparation changed the original C");
                helper.assertTrue(observation.target != null && observation.target.isRemoved(), "Failed target survived");
                var raw = (Container) observation.target;
                helper.assertTrue(raw.getItem(0).isEmpty() && raw.removeItemNoUpdate(0).isEmpty(), "Removed target exposed owned items");
                helper.assertTrue(observation.retained.extractItem(0, 64, false).isEmpty(), "Cached handler exposed removed items");
                var refused = observation.retained.insertItem(1, observation.foreign.copy(), false);
                helper.assertTrue(refused.getCount() == 3 && raw.getItem(1).isEmpty(), "Removed target consumed foreign items");
                helper.assertTrue(observation.recipient.isEmpty() && observation.foreign.getCount() == 3,
                        "Failed preparation moved original or foreign resources");
            } finally { ACTIVE.remove(); helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2); }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_provider", timeoutTicks = 100)
    public static void productionPublicationPreservesOnLoadOnPlaceAndNeighborTransfers(GameTestHelper helper) {
        var at = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlock(at.east(), SealedStagingProbeFixture.OBSERVER.get().defaultBlockState(), 2);
        for (String phase : List.of("external.onPlace", "external.onLoad", "observer.onNeighborChange", "observer.neighborChanged")) {
            var carrier = stage(helper.getLevel(), at);
            var observation = new Observation(helper, at, carrier, phase, false); ACTIVE.set(observation);
            try {
                helper.assertTrue(unseal(carrier), "Production publication failed at " + phase);
                helper.getLevel().updateNeighborsAt(at, AdapterTestMod.CONTAINER.get());
                helper.getLevel().updateNeighbourForOutputSignal(at, AdapterTestMod.CONTAINER.get());
                observation.assertTransferred();
                helper.assertTrue(!unseal(carrier), "Stale C was published twice");
                observation.assertTransferred();
            } finally { ACTIVE.remove(); helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2); }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_provider", timeoutTicks = 100)
    public static void failureAfterPublicationDoesNotRestoreCOrWipeForeignContents(GameTestHelper helper) {
        var at = helper.absolutePos(new BlockPos(4, 2, 4)); var carrier = stage(helper.getLevel(), at);
        var observation = new Observation(helper, at, carrier, "external.onPlace", true); ACTIVE.set(observation);
        boolean threw = false;
        try {
            try { unseal(carrier); } catch (IllegalStateException failure) { threw = true; }
            helper.assertTrue(threw, "Fixture callback did not fail"); observation.assertTransferred();
            helper.assertTrue(!unseal(carrier), "Callback failure replayed the old carrier"); observation.assertTransferred();
            helper.succeed();
        } finally { ACTIVE.remove(); helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2); }
    }

    static void attached(BlockEntity target) {
        var observation = ACTIVE.get();
        if (observation != null && target.getBlockPos().equals(observation.at) && target instanceof FixtureContainerBlockEntity) {
            observation.target = target; observation.events.add("attachment");
        }
    }
    static void callback(String phase) {
        var observation = ACTIVE.get(); if (observation == null || observation.target == null) { return; }
        observation.events.add(phase);
        if (phase.equals("external.constructor.return")) {
            observation.helper.assertTrue(observation.target.getLevel() == null && ((Container) observation.target).isEmpty(),
                    "New provider target was not detached and empty");
            observation.retained = new InvWrapper((Container) observation.target); return;
        }
        if (!phase.equals(observation.phase) || observation.transferred) { return; }
        observation.helper.assertTrue(observation.helper.getLevel().getBlockEntity(observation.at) == observation.target,
                "Resource callback reached an unpublished target");
        observation.transferred = true;
        observation.recipient.setItem(0, observation.retained.extractItem(0, 64, false));
        observation.foreign = observation.retained.insertItem(1, observation.foreign, false);
        if (observation.failAfter) { throw new IllegalStateException("W152 fixture post-publication failure"); }
    }
    private static final class Observation {
        final GameTestHelper helper; final BlockPos at; final BlockEntity carrier; final String phase; final boolean failAfter;
        final List<String> events = new ArrayList<>(); final SimpleContainer recipient = new SimpleContainer(1);
        BlockEntity target; InvWrapper retained; boolean transferred; ItemStack foreign = new ItemStack(Items.GOLD_INGOT, 3);
        Observation(GameTestHelper helper, BlockPos at, BlockEntity carrier, String phase, boolean failAfter) {
            this.helper = helper; this.at = at; this.carrier = carrier; this.phase = phase; this.failAfter = failAfter;
        }
        void assertTransferred() {
            helper.assertTrue(transferred, "Callback not reached: " + phase + " " + events);
            helper.assertTrue(helper.getLevel().getBlockEntity(at) == target && !target.isRemoved() && carrier.isRemoved(), "Publication identity changed");
            helper.assertTrue(recipient.getItem(0).is(Items.DIAMOND) && recipient.getItem(0).getCount() == 7
                    && ((Container) target).getItem(0).isEmpty(), "Original resources lost or replayed");
            helper.assertTrue(foreign.isEmpty() && ((Container) target).getItem(1).is(Items.GOLD_INGOT)
                    && ((Container) target).getItem(1).getCount() == 3, "Foreign resources lost or duplicated");
        }
    }
    private static BlockEntity stage(ServerLevel level, BlockPos at) {
        var source = new FixtureContainerBlockEntity(at, AdapterTestMod.CONTAINER.get().defaultBlockState());
        source.setItem(0, new ItemStack(Items.DIAMOND, 7));
        try { return (BlockEntity) Class.forName(ACCESS).getMethod("stage", ServerLevel.class, BlockEntity.class,
                ResourceLocation.class, RocketBlockEntityAdapter.class).invoke(null, level, source, AdapterTestMod.ADAPTER_ID, new FixtureInventoryAdapter()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Production staging fixture unavailable", failure); }
    }
    private static boolean unseal(BlockEntity carrier) {
        try { return (boolean) Class.forName(ACCESS).getMethod("unseal", BlockEntity.class).invoke(null, carrier); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Production publication failed", failure); }
    }
    private SealedProductionProviderGameTests() { }
}
