package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.CombustionTorches;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.TorchPair;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchRoot;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bracket native mutation adapters at an owned loaded/unloaded edge; include every ticket type, not just forces. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnlitTorchBoundaryGameTests {
    @GameTest(template = "rocket_test", batch = "unlit_torch_boundary", timeoutTicks = 100)
    public static void conversionDoesNotAcquireTheAdjacentChunk(GameTestHelper h) { extinguish(h, false); }
    @GameTest(template = "rocket_test", batch = "unlit_torch_boundary", timeoutTicks = 100)
    public static void dropModeDoesNotAcquireTheAdjacentChunk(GameTestHelper h) { extinguish(h, true); }

    private static void extinguish(GameTestHelper helper, boolean drop) {
        try (OwnedLoadedFrontier f = new OwnedLoadedFrontier(helper, helper.getLevel(), 224)) {
            for (boolean wall : new boolean[]{false, true}) {
                BlockPos pos = wall ? f.edge.north(3) : f.edge;
                f.set(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
                f.set(pos.west(), Blocks.IRON_BLOCK.defaultBlockState());
                BlockState lit = wall ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST)
                        : Blocks.TORCH.defaultBlockState();
                f.set(pos, lit);
                int chunks = f.level.getChunkSource().getLoadedChunksCount(), tickets = f.ticketCount();
                Object result = change("extinguish", new Class<?>[]{ServerLevel.class, BlockPos.class, BlockState.class,
                        TorchPair.class, boolean.class}, f.level, pos, lit, CombustionTorches.pairFor(lit).orElseThrow(), drop);
                helper.assertTrue(result.toString().equals("DEFERRED") && f.level.getBlockState(pos).equals(lit)
                        && f.level.getBlockEntity(pos) == null, "Boundary extinguish/drop changed a torch instead of deferring");
                unchanged(helper, f, chunks, tickets);
                helper.assertTrue(f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(pos).inflate(2)).isEmpty(), "Deferred drop emitted an item");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "unlit_torch_boundary", timeoutTicks = 100)
    public static void relightingDoesNotAcquireTheAdjacentSupportChunk(GameTestHelper helper) {
        try (OwnedLoadedFrontier f = new OwnedLoadedFrontier(helper, helper.getLevel(), 224)) {
            var player = new FakePlayer(f.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "TorchEdge"));
            player.setGameMode(GameType.SURVIVAL);
            for (boolean wall : new boolean[]{false, true}) {
                BlockPos pos = wall ? f.edge.north(3) : f.edge;
                f.set(pos.below(), Blocks.IRON_BLOCK.defaultBlockState());
                // Wall support is EAST, in the absent chunk: canSurvive must not run before admission.
                BlockState state = wall ? ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)
                        : ModBlocks.UNLIT_TORCH.get().defaultBlockState();
                f.set(pos, state);
                UnlitTorchBlockEntity torch = (UnlitTorchBlockEntity) f.level.getBlockEntity(pos);
                CompoundTag outer = new CompoundTag();
                ResourceLocation id = ResourceLocation.tryParse("minecraft:torch");
                outer.put(UnlitTorchRoot.KEY, UnlitTorchRoot.encode(new UnlitTorchRoot.Identity(id,
                        ResourceLocation.tryParse(wall ? "minecraft:wall_torch" : "minecraft:torch"), id)));
                torch.load(outer);
                player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
                player.setItemInHand(InteractionHand.MAIN_HAND, flint);
                int chunks = f.level.getChunkSource().getLoadedChunksCount(), tickets = f.ticketCount();
                CompoundTag before = torch.saveWithoutMetadata();
                var result = player.gameMode.useItemOn(player, f.level, flint, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                helper.assertTrue(!result.consumesAction() && flint.getDamageValue() == 0
                        && f.level.getBlockEntity(pos) == torch && f.level.getBlockState(pos).equals(state)
                        && before.equals(torch.saveWithoutMetadata()), "Boundary relight changed the root or spent durability");
                unchanged(helper, f, chunks, tickets);
            }
        }
        helper.succeed();
    }

    private static void unchanged(GameTestHelper helper, OwnedLoadedFrontier f, int chunks, int tickets) {
        f.assertAbsent();
        helper.assertTrue(f.level.getChunkSource().getLoadedChunksCount() == chunks && f.ticketCount() == tickets,
                "Torch operation changed the loaded-chunk or native ticket count");
    }

    private static Object change(String name, Class<?>[] signature, Object... args) {
        try {
            Method method = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.TorchWorldChanges")
                    .getDeclaredMethod(name, signature);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native torch adapter probe failed", failure); }
    }

    private UnlitTorchBoundaryGameTests() { }
}
