package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.CombustionTorches;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.TorchPair;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.TorchQueue;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchRoot;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.2 torch rules on the native server: Moon vacuum conversion with orientation, fire starters,
 * both drop modes, unsupported tagged torches, thermite, the queue cap, a supplied room that loses supply, root
 * preservation, the guarded save and operator-only NBT. Each global override has its own batch.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnlitTorchGameTests {
    private static final String TEMPLATE = "rocket_test";
    private static final ResourceLocation TORCH = id("minecraft", "torch");
    private static final ResourceLocation WALL_TORCH = id("minecraft", "wall_torch");
    private static final UnlitTorchRoot.Identity STANDING = new UnlitTorchRoot.Identity(TORCH, TORCH, TORCH);
    private static final UnlitTorchRoot.Identity WALL = new UnlitTorchRoot.Identity(TORCH, WALL_TORCH, TORCH);
    private static final TorchPair SOUL = new TorchPair(id("arce_test", "soul_torch"), id("minecraft", "soul_torch"),
            id("minecraft", "soul_wall_torch"), id("minecraft", "soul_torch"));

    private UnlitTorchGameTests() {
    }

    @BeforeBatch(batch = "unlit_torch_drop")
    public static void beforeDrop(ServerLevel level) { SwitchOverrides.set(CommonConfig.DROP_EXTINGUISHED_TORCHES, true); }
    @AfterBatch(batch = "unlit_torch_drop")
    public static void afterDrop(ServerLevel level) { SwitchOverrides.clear(CommonConfig.DROP_EXTINGUISHED_TORCHES); }
    @BeforeBatch(batch = "unlit_torch_fixture")
    public static void beforeFixture(ServerLevel level) {
        CombustionTorches.installFixture(level.getServer(), Set.of(Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
                Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH), List.of(SOUL));
    }
    @AfterBatch(batch = "unlit_torch_fixture")
    public static void afterFixture(ServerLevel level) { CombustionTorches.clearFixture(); }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 100)
    public static void vacuumPlacementGoesOutKeepingStandingAndWallOrientation(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        List<BlockPos> cells = new ArrayList<>();
        f.run(() -> {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            place(helper, player, Items.TORCH, f.origin.below(), Direction.UP, true);
            cells.add(f.origin);
            int index = 1;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos cell = f.origin.offset(3 * index++, 0, 0);
                f.set(cell.relative(facing.getOpposite()), Blocks.IRON_BLOCK.defaultBlockState());
                player.setYRot(facing.getOpposite().toYRot());
                player.setXRot(0);
                place(helper, player, Items.TORCH, cell.relative(facing.getOpposite()), facing, true);
                helper.assertTrue(f.level.getBlockState(cell).is(Blocks.WALL_TORCH)
                        && f.level.getBlockState(cell).getValue(WallTorchBlock.FACING) == facing, "Vanilla wall placement differs: " + facing);
                cells.add(cell);
            }
        }, () -> cells.stream().allMatch(cell -> !CombustionTorches.isCombustionTorch(f.level.getBlockState(cell))), () -> {
            assertUnlit(helper, f.level, f.origin, ModBlocks.UNLIT_TORCH.get(), null, STANDING);
            int wall = 1;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                assertUnlit(helper, f.level, cells.get(wall++), ModBlocks.UNLIT_WALL_TORCH.get(), facing, WALL);
            }
            helper.assertTrue(f.newDrops().isEmpty(), "Default mode dropped an item");
        });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 100)
    public static void thermiteTorchStaysLitInVacuum(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        int[] ticks = {0};
        f.run(() -> {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            place(helper, f.player(GameType.SURVIVAL), ModItems.THERMITE_TORCH.get(), f.origin.below(), Direction.UP, true);
            f.set(f.origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        }, () -> ++ticks[0] > 10, () -> helper.assertTrue(f.level.getBlockState(f.origin).is(ModBlocks.THERMITE_TORCH.get())
                && f.level.getBlockState(f.origin).getLightEmission(f.level, f.origin) == 14
                && f.level.getBlockEntity(f.origin) == null, "Thermite torch changed in vacuum"));
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 20)
    public static void fireStartersAreSuppressedInVacuumAndRelightOnlyInAir(GameTestHelper helper) {
        try (UnlitTorchFixture moon = UnlitTorchFixture.moon(helper); UnlitTorchFixture air = UnlitTorchFixture.overworld(helper)) {
            FakePlayer vacuum = moon.player(GameType.SURVIVAL);
            moon.set(moon.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            moon.unlit(moon.origin, null, STANDING);
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            helper.assertTrue(!use(vacuum, moon.origin, Direction.UP, flint).consumesAction() && flint.getDamageValue() == 0
                    && moon.level.getBlockState(moon.origin).is(ModBlocks.UNLIT_TORCH.get()), "Vacuum relit a torch or spent flint");
            BlockPos iron = moon.origin.east(3);
            moon.set(iron, Blocks.IRON_BLOCK.defaultBlockState());
            ItemStack charges = new ItemStack(Items.FIRE_CHARGE, 2);
            helper.assertTrue(!use(vacuum, iron, Direction.UP, charges).consumesAction() && charges.getCount() == 2
                    && moon.level.isEmptyBlock(iron.above()), "Vacuum fire charge was not suppressed");
            BlockPos lever = moon.origin.east(6);
            moon.set(lever.below(), Blocks.IRON_BLOCK.defaultBlockState());
            moon.set(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
            ItemStack rod = new ItemStack(Items.BLAZE_ROD);
            helper.assertTrue(!use(vacuum, lever, Direction.UP, rod).consumesAction()
                    && !moon.level.getBlockState(lever).getValue(LeverBlock.POWERED), "Vacuum blaze rod right-click was not suppressed");

            FakePlayer breathing = air.player(GameType.SURVIVAL);
            BlockPos wall = air.origin;
            air.set(wall.west(), Blocks.IRON_BLOCK.defaultBlockState());
            air.unlit(wall, Direction.EAST, WALL);
            ItemStack airFlint = new ItemStack(Items.FLINT_AND_STEEL);
            helper.assertTrue(use(breathing, wall, Direction.EAST, airFlint).consumesAction() && airFlint.getDamageValue() == 1
                    && air.level.getBlockState(wall).is(Blocks.WALL_TORCH)
                    && air.level.getBlockState(wall).getValue(WallTorchBlock.FACING) == Direction.EAST
                    && air.level.getBlockEntity(wall) == null, "Air flint did not restore the exact wall torch");
            BlockPos standing = air.origin.north(3);
            air.set(standing.below(), Blocks.IRON_BLOCK.defaultBlockState());
            air.unlit(standing, null, STANDING);
            ItemStack airCharges = new ItemStack(Items.FIRE_CHARGE, 3);
            helper.assertTrue(use(breathing, standing, Direction.UP, airCharges).consumesAction() && airCharges.getCount() == 2
                    && air.level.getBlockState(standing).is(Blocks.TORCH), "Air fire charge did not relight once");
            BlockPos powder = air.origin.south(3);
            air.set(powder.below(), Blocks.IRON_BLOCK.defaultBlockState());
            air.unlit(powder, null, STANDING);
            ItemStack blaze = new ItemStack(Items.BLAZE_POWDER, 5);
            helper.assertTrue(use(breathing, powder, Direction.UP, blaze).consumesAction() && blaze.getCount() == 5
                    && air.level.getBlockState(powder).is(Blocks.TORCH), "Blaze powder relight spent or failed");
            BlockPos unknown = air.origin.north(6);
            air.set(unknown.below(), Blocks.IRON_BLOCK.defaultBlockState());
            air.unlit(unknown, null, null);
            ItemStack noRoot = new ItemStack(Items.FLINT_AND_STEEL);
            helper.assertTrue(!use(breathing, unknown, Direction.UP, noRoot).consumesAction() && noRoot.getDamageValue() == 0
                    && air.level.getBlockState(unknown).is(ModBlocks.UNLIT_TORCH.get()), "A torch without a root was relit");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 100)
    public static void defaultModeKeepsOneUnlitTorchThatDropsItsExactSource(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.overworld(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        f.run(() -> {
            BlockPos wall = f.origin;
            f.set(wall.west(), Blocks.IRON_BLOCK.defaultBlockState());
            f.unlit(wall, Direction.EAST, WALL);
            helper.assertTrue(player.gameMode.destroyBlock(wall) && f.level.isEmptyBlock(wall), "Ordinary unlit break failed");
            assertDrops(helper, f, Map.of(Items.TORCH, 1));
            BlockPos creative = f.origin.north(3);
            f.set(creative.below(), Blocks.IRON_BLOCK.defaultBlockState());
            f.unlit(creative, null, STANDING);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(player.gameMode.destroyBlock(creative) && f.level.isEmptyBlock(creative), "Creative unlit break failed");
            assertDrops(helper, f, Map.of());
            player.setGameMode(GameType.SURVIVAL);
            BlockPos rootless = f.origin.south(3);
            f.set(rootless.below(), Blocks.IRON_BLOCK.defaultBlockState());
            f.unlit(rootless, null, null);
            helper.assertTrue(player.gameMode.destroyBlock(rootless) && f.level.isEmptyBlock(rootless), "A rootless unlit torch was kept");
            assertDrops(helper, f, Map.of());
            BlockPos support = f.origin.north(6);
            f.set(support.below(), Blocks.IRON_BLOCK.defaultBlockState());
            f.unlit(support, null, STANDING);
            f.level.setBlock(support.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(f.level.isEmptyBlock(support), "Support loss kept an ordinary unlit torch");
            assertDrops(helper, f, Map.of(Items.TORCH, 1));
        }, () -> true, () -> { });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 100)
    public static void undecodableAndUnresolvableRootsArePreservedAndQuarantined(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.overworld(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        f.run(() -> {
            BlockPos future = f.origin;
            f.set(future.below(), Blocks.IRON_BLOCK.defaultBlockState());
            UnlitTorchBlockEntity torch = f.unlit(future, null, null);
            CompoundTag raw = UnlitTorchRoot.encode(STANDING);
            raw.putInt("schema_version", 2);
            raw.putString("future", "kept");
            CompoundTag outer = new CompoundTag();
            outer.put(UnlitTorchRoot.KEY, raw.copy());
            torch.load(outer);
            helper.assertTrue(torch.onlyOpCanSetNbt(), "Unlit torch roots accept non-operator item NBT");
            helper.assertTrue(torch.retainedRoot() && torch.protectedData() && torch.identity().isEmpty()
                    && torch.saveWithoutMetadata().get(UnlitTorchRoot.KEY).equals(raw), "Future root was not retained unchanged");
            BlockEntity reloaded = BlockEntity.loadStatic(future, torch.getBlockState(), torch.saveWithFullMetadata());
            helper.assertTrue(reloaded instanceof UnlitTorchBlockEntity copy && copy.retainedRoot()
                    && copy.saveWithoutMetadata().get(UnlitTorchRoot.KEY).equals(raw), "Retained root did not survive a reload");
            assertQuarantined(helper, f, player, future, raw);

            BlockPos gone = f.origin.north(3);
            f.set(gone.below(), Blocks.IRON_BLOCK.defaultBlockState());
            UnlitTorchBlockEntity orphan = f.unlit(gone, null, new UnlitTorchRoot.Identity(id("example", "gone"), TORCH, TORCH));
            CompoundTag orphanRoot = (CompoundTag) orphan.saveWithoutMetadata().get(UnlitTorchRoot.KEY);
            helper.assertTrue(orphan.identity().isPresent() && orphan.resolve().isEmpty() && orphan.protectedData(),
                    "A removed pair was not quarantined");
            assertQuarantined(helper, f, player, gone, orphanRoot);

            BlockPos mismatch = f.origin.south(3);
            f.set(mismatch.below(), Blocks.IRON_BLOCK.defaultBlockState());
            helper.assertTrue(f.unlit(mismatch, null, WALL).protectedData(), "A wall identity in a standing unlit torch resolved");
        }, () -> true, () -> { });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch", timeoutTicks = 20)
    public static void oversizedRetainedRootRefusesTheChunkSave(GameTestHelper helper) {
        try (UnlitTorchFixture f = UnlitTorchFixture.overworld(helper)) {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            UnlitTorchBlockEntity torch = f.unlit(f.origin, null, null);
            CompoundTag huge = new CompoundTag();
            huge.putByteArray("payload", new byte[UnlitTorchRoot.MAX_BYTES]);
            CompoundTag outer = new CompoundTag();
            outer.put(UnlitTorchRoot.KEY, huge);
            torch.load(outer);
            helper.assertTrue(torch.retainedRoot() && torch.saveWithoutMetadata().get(UnlitTorchRoot.KEY) == huge,
                    "Oversized root was copied or omitted");
            var chunk = f.level.getChunkAt(f.origin);
            boolean refused = false;
            try {
                var outgoing = ChunkSerializer.write(f.level, chunk);
                chunk.setUnsaved(false); // ChunkMap does this before its serialization/event/write sequence.
                MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, f.level, outgoing));
            } catch (IllegalStateException expected) {
                refused = expected.getMessage().contains("unlit torch root");
            }
            helper.assertTrue(refused && chunk.isUnsaved(), "Chunk save did not refuse the oversized unlit torch root");
            // Do not leave a deliberately unsavable fixture in the GameTest world's later chunk save.
            torch.load(new CompoundTag());
            GuardedChunkSaves.restoreGameTestFixture(f.level, chunk.getPos());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch_drop", timeoutTicks = 100)
    public static void dropModeRemovesTheTorchOnceAndDropsOneOriginal(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        BlockPos wall = f.origin.east(3);
        int[] settled = {0};
        f.run(() -> {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            place(helper, player, Items.TORCH, f.origin.below(), Direction.UP, true);
            f.set(wall.west(), Blocks.IRON_BLOCK.defaultBlockState());
            player.setYRot(Direction.WEST.toYRot());
            place(helper, player, Items.TORCH, wall.west(), Direction.EAST, true);
        }, () -> f.level.isEmptyBlock(f.origin) && f.level.isEmptyBlock(wall) && ++settled[0] > 5, () -> {
            helper.assertTrue(f.level.getBlockEntity(f.origin) == null && f.level.getBlockEntity(wall) == null,
                    "Drop mode left an unlit torch");
            assertDrops(helper, f, Map.of(Items.TORCH, 2));
        });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch_fixture", timeoutTicks = 100)
    public static void unsupportedTaggedTorchRefusesVacuumPlacement(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        f.run(() -> {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            helper.assertTrue(CombustionTorches.isCombustionTorch(Blocks.REDSTONE_TORCH.defaultBlockState())
                    && CombustionTorches.pairFor(Blocks.REDSTONE_TORCH.defaultBlockState()).isEmpty(), "Fixture tag differs");
            place(helper, player, Items.REDSTONE_TORCH, f.origin.below(), Direction.UP, false);
            helper.assertTrue(f.level.isEmptyBlock(f.origin) && f.newDrops().isEmpty(), "Unsupported tagged torch was placed");
        }, () -> true, () -> { });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch_fixture", timeoutTicks = 100)
    public static void twoSourcePairsWithTheSameOrientationRoundTripIndependently(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        BlockPos soul = f.origin;
        BlockPos plain = f.origin.east(3);
        UnlitTorchRoot.Identity soulIdentity = new UnlitTorchRoot.Identity(SOUL.id(), SOUL.standingBlock(), SOUL.item());
        f.run(() -> {
            f.set(soul.below(), Blocks.IRON_BLOCK.defaultBlockState());
            f.set(plain.below(), Blocks.IRON_BLOCK.defaultBlockState());
            place(helper, player, Items.SOUL_TORCH, soul.below(), Direction.UP, true);
            place(helper, player, Items.TORCH, plain.below(), Direction.UP, true);
        }, () -> f.level.getBlockState(soul).is(ModBlocks.UNLIT_TORCH.get())
                && f.level.getBlockState(plain).is(ModBlocks.UNLIT_TORCH.get()), () -> {
            assertUnlit(helper, f.level, soul, ModBlocks.UNLIT_TORCH.get(), null, soulIdentity);
            assertUnlit(helper, f.level, plain, ModBlocks.UNLIT_TORCH.get(), null, STANDING);
            for (BlockPos cell : List.of(soul, plain)) {
                BlockEntity entity = f.level.getBlockEntity(cell);
                BlockEntity copy = BlockEntity.loadStatic(cell, entity.getBlockState(), entity.saveWithFullMetadata());
                helper.assertTrue(copy instanceof UnlitTorchBlockEntity reloaded
                        && reloaded.identity().equals(((UnlitTorchBlockEntity) entity).identity()), "Identity did not survive a reload");
            }
            helper.assertTrue(player.gameMode.destroyBlock(soul), "Soul unlit torch did not break");
            assertDrops(helper, f, Map.of(Items.SOUL_TORCH, 1));
            helper.assertTrue(player.gameMode.destroyBlock(plain), "Plain unlit torch did not break");
            assertDrops(helper, f, Map.of(Items.TORCH, 1));
            try (UnlitTorchFixture air = UnlitTorchFixture.overworld(helper)) {
                FakePlayer breathing = air.player(GameType.SURVIVAL);
                BlockPos airSoul = air.origin;
                BlockPos airPlain = air.origin.north(3);
                air.set(airSoul.below(), Blocks.IRON_BLOCK.defaultBlockState());
                air.set(airPlain.below(), Blocks.IRON_BLOCK.defaultBlockState());
                air.unlit(airSoul, null, soulIdentity);
                air.unlit(airPlain, null, STANDING);
                helper.assertTrue(use(breathing, airSoul, Direction.UP, new ItemStack(Items.FLINT_AND_STEEL)).consumesAction()
                        && use(breathing, airPlain, Direction.UP, new ItemStack(Items.FLINT_AND_STEEL)).consumesAction()
                        && air.level.getBlockState(airSoul).is(Blocks.SOUL_TORCH)
                        && air.level.getBlockState(airPlain).is(Blocks.TORCH), "Relighting swapped the two source pairs");
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch_queue", timeoutTicks = 150)
    public static void fullQueueFailsNewPlacementsClosedAndDrains(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        boolean[] placed = {false};
        f.run(() -> {
            f.set(f.origin.below(), Blocks.IRON_BLOCK.defaultBlockState());
            long far = new BlockPos(29_000_000, 180, 29_000_000).asLong();
            int added = CombustionTorches.fillQueueForTest(f.level.getServer(), f.level, far);
            TorchQueue.Metrics full = CombustionTorches.metrics(f.level).orElseThrow();
            helper.assertTrue(added > 0 && full.queued() == TorchQueue.MAX_POSITIONS, "Queue fixture did not fill: " + full);
            place(helper, player, Items.TORCH, f.origin.below(), Direction.UP, false);
            TorchQueue.Metrics refused = CombustionTorches.metrics(f.level).orElseThrow();
            helper.assertTrue(f.level.isEmptyBlock(f.origin) && refused.overflows() > full.overflows(),
                    "A full queue admitted a vacuum torch: " + refused);
        }, () -> {
            if (!placed[0] && CombustionTorches.metrics(f.level).orElseThrow().queued() < TorchQueue.MAX_POSITIONS / 2) {
                place(helper, player, Items.TORCH, f.origin.below(), Direction.UP, true);
                placed[0] = true;
            }
            return placed[0] && f.level.getBlockState(f.origin).is(ModBlocks.UNLIT_TORCH.get());
        }, () -> helper.assertTrue(CombustionTorches.metrics(f.level).orElseThrow().queued() <= TorchQueue.MAX_POSITIONS / 2,
                "The queue did not drain"));
    }

    @GameTest(template = TEMPLATE, batch = "unlit_torch_room", timeoutTicks = 200)
    public static void suppliedRoomKeepsItsTorchLitUntilTheRoomLosesSupply(GameTestHelper helper) {
        UnlitTorchFixture f = UnlitTorchFixture.moon(helper);
        FakePlayer player = f.player(GameType.SURVIVAL);
        BlockPos vent = f.origin;
        BlockPos torch = vent.offset(1, 1, 0);
        BlockPos breach = vent.offset(-2, 1, 0);
        OxygenVentBlockEntity[] entity = {null};
        int[] phase = {0};
        int[] litTicks = {0};
        f.run(() -> {
            for (int x = -2; x <= 2; x++) { for (int y = 0; y <= 2; y++) { for (int z = -1; z <= 1; z++) {
                boolean interior = Math.abs(x) <= 1 && y == 1 && z == 0;
                f.set(vent.offset(x, y, z), interior ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
            } } }
            f.set(vent, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            entity[0] = (OxygenVentBlockEntity) f.level.getBlockEntity(vent);
            var input = entity[0].getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
            helper.assertTrue(input.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false).isEmpty(), "Vent refused oxygen");
            OxygenVentBlockEntity.serverTick(f.level, vent, entity[0].getBlockState(), entity[0]);
            entity[0].getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                    .receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
        }, () -> {
            OxygenVentBlockEntity.serverTick(f.level, vent, entity[0].getBlockState(), entity[0]);
            AnalyzerReading.State air = AtmosphereAnalyzerRuntime.readAt(f.level, torch).state();
            if (phase[0] == 0 && air == AnalyzerReading.State.BREATHABLE) {
                place(helper, player, Items.TORCH, torch.below(), Direction.UP, true);
                phase[0] = 1;
            } else if (phase[0] == 1) {
                helper.assertTrue(f.level.getBlockState(torch).is(Blocks.TORCH), "Supplied room put the torch out: " + air);
                if (air == AnalyzerReading.State.BREATHABLE && ++litTicks[0] > 10
                        && CombustionTorches.metrics(f.level).orElseThrow().queued() == 0) {
                    helper.assertTrue(player.gameMode.destroyBlock(breach) && f.level.isEmptyBlock(breach), "Wall breach failed");
                    phase[0] = 2;
                }
            }
            return phase[0] == 2 && f.level.getBlockState(torch).is(ModBlocks.UNLIT_TORCH.get());
        }, () -> {
            assertUnlit(helper, f.level, torch, ModBlocks.UNLIT_TORCH.get(), null, STANDING);
            helper.assertTrue(CombustionTorches.metrics(f.level).orElseThrow().sweepCells() > 0, "No volume sweep reached the torch");
        });
    }

    private static void assertQuarantined(GameTestHelper helper, UnlitTorchFixture f, FakePlayer player, BlockPos cell,
                                          CompoundTag root) {
        BlockState state = f.level.getBlockState(cell);
        helper.assertTrue(!player.gameMode.destroyBlock(cell) && f.level.getBlockState(cell) == state, "Quarantined torch broke");
        Explosion explosion = new Explosion(f.level, null, cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 1.0F,
                false, Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(cell);
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(f.level, explosion, new ArrayList<>()));
        helper.assertTrue(explosion.getToBlow().isEmpty(), "Explosion kept a quarantined torch in its block list");
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        helper.assertTrue(!use(player, cell, Direction.UP, flint).consumesAction() && flint.getDamageValue() == 0
                && f.level.getBlockState(cell) == state, "Quarantined torch was relit");
        f.level.setBlock(cell.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(f.level.getBlockState(cell) == state && f.level.getBlockEntity(cell) instanceof UnlitTorchBlockEntity kept
                && kept.saveWithoutMetadata().get(UnlitTorchRoot.KEY).equals(root), "Support loss removed or rewrote a quarantined root");
        assertDrops(helper, f, Map.of());
    }

    private static void assertUnlit(GameTestHelper helper, ServerLevel level, BlockPos cell, Block block, Direction facing,
                                    UnlitTorchRoot.Identity identity) {
        BlockState state = level.getBlockState(cell);
        helper.assertTrue(state.is(block) && (facing == null || state.getValue(WallTorchBlock.FACING) == facing)
                && state.getLightEmission(level, cell) == 0, "Unlit block or orientation differs at " + cell + ": " + state);
        helper.assertTrue(level.getBlockEntity(cell) instanceof UnlitTorchBlockEntity entity
                && entity.identity().equals(Optional.of(identity)) && !entity.protectedData(),
                "Unlit identity differs at " + cell);
    }

    private static void assertDrops(GameTestHelper helper, UnlitTorchFixture f, Map<Item, Integer> expected) {
        List<ItemEntity> drops = f.newDrops();
        Map<Item, Integer> actual = drops.stream().collect(Collectors.groupingBy(drop -> drop.getItem().getItem(),
                Collectors.summingInt(drop -> drop.getItem().getCount())));
        helper.assertTrue(actual.equals(expected) && drops.stream().noneMatch(drop -> drop.getItem().hasTag()),
                "Drops differ: expected " + expected + " but found " + actual);
        drops.forEach(ItemEntity::discard);
    }

    private static void place(GameTestHelper helper, FakePlayer player, Item item, BlockPos support, Direction face,
                              boolean admitted) {
        ItemStack stack = new ItemStack(item, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(support, face)));
        helper.assertTrue(result.consumesAction() == admitted && player.getMainHandItem().getCount() == (admitted ? 3 : 4),
                "Placement admission differs for " + item + " at " + support.relative(face) + ": " + result);
    }

    private static InteractionResult use(FakePlayer player, BlockPos cell, Direction face, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit(cell, face));
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.tryBuild(namespace, path);
    }
}
