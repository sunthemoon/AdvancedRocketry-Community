package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.CombustionTorches;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.TorchPair;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchRoot;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native downward water/lava flow, including admission and direct spreadTo, must preserve quarantined identities. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnlitTorchFluidGameTests {
    private static final TorchPair SOUL = new TorchPair(id("arce_test", "fluid_soul"), id("minecraft", "soul_torch"),
            id("minecraft", "soul_wall_torch"), id("minecraft", "soul_torch"));

    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void standingRetainedRootSurvivesWater(GameTestHelper h) { quarantined(h, false, true, Fluids.WATER); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void wallRetainedRootSurvivesWater(GameTestHelper h) { quarantined(h, true, true, Fluids.WATER); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void standingRemovedPairSurvivesWater(GameTestHelper h) { quarantined(h, false, false, Fluids.WATER); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void wallRemovedPairSurvivesWater(GameTestHelper h) { quarantined(h, true, false, Fluids.WATER); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void standingRetainedRootSurvivesLava(GameTestHelper h) { quarantined(h, false, true, Fluids.LAVA); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void wallRetainedRootSurvivesLava(GameTestHelper h) { quarantined(h, true, true, Fluids.LAVA); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void standingRemovedPairSurvivesLava(GameTestHelper h) { quarantined(h, false, false, Fluids.LAVA); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void wallRemovedPairSurvivesLava(GameTestHelper h) { quarantined(h, true, false, Fluids.LAVA); }

    private static void quarantined(GameTestHelper helper, boolean wall, boolean retained, FlowingFluid fluid) {
        UnlitTorchFixture f = UnlitTorchFixture.overworld(helper);
        f.run(() -> {
            CombustionTorches.installFixture(f.level.getServer(), Set.of(), List.of(SOUL));
            try {
                cage(f, f.origin);
                UnlitTorchRoot.Identity identity = new UnlitTorchRoot.Identity(SOUL.id(), SOUL.block(wall), SOUL.item());
                UnlitTorchBlockEntity torch = f.unlit(f.origin, wall ? Direction.EAST : null, identity);
                CompoundTag root = UnlitTorchRoot.encode(identity);
                if (retained) {
                    root.putInt("schema_version", 2);
                    CompoundTag outer = new CompoundTag();
                    outer.put(UnlitTorchRoot.KEY, root);
                    torch.load(outer);
                }
                CombustionTorches.installFixture(f.level.getServer(), Set.of(), List.of());
                BlockState before = torch.getBlockState();
                var savedIdentity = torch.identity();
                helper.assertTrue(torch.protectedData() && torch.retainedRoot() == retained, "Fixture is not quarantined");
                helper.assertTrue(!canHold(fluid, f, before), "Native canHoldFluid admitted a quarantined torch");
                BlockPos source = f.origin.above();
                f.set(source, fluid.defaultFluidState().createLegacyBlock());
                fluid.tick(f.level, source, f.level.getFluidState(source));
                assertKept(helper, f, torch, before, root, savedIdentity);
                // Native spreadTo must also remain safe if admission is bypassed by a caller.
                spreadTo(fluid, f, before);
                helper.assertTrue(!((LiquidBlockContainer) before.getBlock()).placeLiquid(f.level, f.origin, before,
                        fluid.defaultFluidState()), "Direct placement admitted a quarantined torch");
                assertKept(helper, f, torch, before, root, savedIdentity);
                helper.assertTrue(f.newDrops().isEmpty(), "Quarantine emitted an item");
                f.set(source, Blocks.IRON_BLOCK.defaultBlockState());
                CombustionTorches.installFixture(f.level.getServer(), Set.of(), List.of(SOUL));
                if (retained) {
                    helper.assertTrue(torch.protectedData(), "Pair restoration must not reinterpret a future schema");
                    CompoundTag repaired = new CompoundTag();
                    repaired.put(UnlitTorchRoot.KEY, UnlitTorchRoot.encode(identity));
                    torch.load(repaired); // Explicit scoped repair, not an automatic schema downgrade.
                }
                helper.assertTrue(!torch.protectedData() && torch.identity().orElseThrow().equals(identity),
                        "Restoring the pair/repair did not recover the exact identity");
                var player = f.player(GameType.SURVIVAL);
                ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
                player.setItemInHand(InteractionHand.MAIN_HAND, flint);
                var result = player.gameMode.useItemOn(player, f.level, flint, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(f.origin), Direction.UP, f.origin, false));
                helper.assertTrue(result.consumesAction() && flint.getDamageValue() == 1
                        && f.level.getBlockState(f.origin).is(wall ? Blocks.SOUL_WALL_TORCH : Blocks.SOUL_TORCH)
                        && (!wall || f.level.getBlockState(f.origin).getValue(
                                net.minecraft.world.level.block.WallTorchBlock.FACING) == Direction.EAST),
                        "Recovered torch did not relight to its exact source and orientation");
            } finally {
                CombustionTorches.clearFixture();
            }
        }, () -> true, () -> { });
    }

    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void resolvedTorchesKeepNativeWaterDrops(GameTestHelper h) { resolved(h, Fluids.WATER, 2); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_fluid", timeoutTicks = 100)
    public static void resolvedTorchesKeepNativeLavaDestruction(GameTestHelper h) { resolved(h, Fluids.LAVA, 0); }

    private static void resolved(GameTestHelper helper, FlowingFluid fluid, int expectedDrops) {
        UnlitTorchFixture f = UnlitTorchFixture.overworld(helper);
        f.run(() -> {
            for (boolean wall : List.of(false, true)) {
                BlockPos cell = wall ? f.origin.south(3) : f.origin;
                cage(f, cell);
                ResourceLocation torchId = id("minecraft", "torch");
                f.unlit(cell, wall ? Direction.EAST : null, new UnlitTorchRoot.Identity(torchId,
                        id("minecraft", wall ? "wall_torch" : "torch"), torchId));
                f.set(cell.above(), fluid.defaultFluidState().createLegacyBlock());
                fluid.tick(f.level, cell.above(), f.level.getFluidState(cell.above()));
                helper.assertTrue(f.level.getFluidState(cell).getType().isSame(fluid)
                        && f.level.getBlockEntity(cell) == null, "Resolved torch did not admit native flow");
            }
            helper.assertTrue(f.newDrops().stream().allMatch(drop -> drop.getItem().is(Items.TORCH))
                    && f.newDrops().stream().mapToInt(drop -> drop.getItem().getCount()).sum() == expectedDrops,
                    "Native fluid drop rule changed");
        }, () -> true, () -> { });
    }

    private static void cage(UnlitTorchFixture f, BlockPos cell) {
        f.set(cell.below(), Blocks.IRON_BLOCK.defaultBlockState());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            f.set(cell.relative(side), Blocks.IRON_BLOCK.defaultBlockState());
            f.set(cell.above().relative(side), Blocks.IRON_BLOCK.defaultBlockState());
        }
    }

    private static void assertKept(GameTestHelper helper, UnlitTorchFixture f, UnlitTorchBlockEntity torch,
                                   BlockState state, CompoundTag root,
                                   java.util.Optional<UnlitTorchRoot.Identity> identity) {
        helper.assertTrue(f.level.getBlockState(f.origin).equals(state) && f.level.getBlockEntity(f.origin) == torch
                && torch.saveWithoutMetadata().get(UnlitTorchRoot.KEY).equals(root) && torch.identity().equals(identity)
                && torch.protectedData(), "Native fluid flow removed or rewrote a quarantined torch");
    }

    private static boolean canHold(FlowingFluid fluid, UnlitTorchFixture f, BlockState state) {
        try {
            Method method = FlowingFluid.class.getDeclaredMethod("canHoldFluid", BlockGetter.class, BlockPos.class,
                    BlockState.class, Fluid.class);
            method.setAccessible(true);
            return (boolean) method.invoke(fluid, f.level, f.origin, state, fluid);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native fluid admission probe failed", failure); }
    }

    private static void spreadTo(FlowingFluid fluid, UnlitTorchFixture f, BlockState state) {
        try {
            Method method = FlowingFluid.class.getDeclaredMethod("spreadTo", LevelAccessor.class, BlockPos.class,
                    BlockState.class, Direction.class, FluidState.class);
            method.setAccessible(true);
            method.invoke(fluid, f.level, f.origin, state, Direction.DOWN, fluid.defaultFluidState());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native fluid spread probe failed", failure); }
    }

    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.tryBuild(namespace, path); }
    private UnlitTorchFluidGameTests() { }
}
