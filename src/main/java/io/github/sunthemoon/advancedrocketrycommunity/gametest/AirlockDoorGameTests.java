package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.AirlockDoorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CompletedVolumeScan;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanCoordinator;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Registered native fixtures; installed-service phase setup is controlled, not natural tick proof. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirlockDoorGameTests {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private AirlockDoorGameTests() { }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 40)
    public static void nativeCanceledSurvivalAndCreativePlacementRestoresBothHalvesAndSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
        try (Fixture fixture = new Fixture(helper, level, lower)) {
            fixture.set(lower.below(), Blocks.IRON_BLOCK.defaultBlockState());
            fixture.set(lower, Blocks.AIR.defaultBlockState()); fixture.set(lower.above(), Blocks.AIR.defaultBlockState());
            List<BlockPos> observed = List.of(lower, lower.above(), lower.below(), lower.east(), lower.west(), lower.north(), lower.south());
            observed.forEach(fixture::remember);
            FakePlayer player = player(level); player.setPos(lower.getX() + 0.5, lower.getY(), lower.getZ() + 0.5);
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, true);
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                player.setGameMode(mode); ItemStack source = new ItemStack(ModItems.AIRLOCK_DOOR.get(), 4);
                source.getOrCreateTag().putString("player_note", "native cancel " + mode.getName());
                player.setItemInHand(InteractionHand.MAIN_HAND, source); ItemStack before = source.copy();
                var surroundings = observed.stream().map(level::getBlockState).toList(); AtomicInteger calls = new AtomicInteger();
                Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
                    if (event.getEntity() == player && event.getLevel() == level
                            && (event.getPos().equals(lower) || event.getPos().equals(lower.above()))) {
                        calls.incrementAndGet(); event.setCanceled(true);
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, cancel);
                try {
                    helper.assertTrue(!player.gameMode.useItemOn(player, level, source, InteractionHand.MAIN_HAND,
                            hit(lower.below(), Direction.UP)).consumesAction(), "Canceled native placement consumed action");
                    helper.assertTrue(calls.get() > 0 && calls.get() <= 2, "Native scoped place event was not observed within bound");
                    helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "Canceled native placement changed source count/tag");
                    for (int index = 0; index < observed.size(); index++) {
                        helper.assertTrue(level.getBlockState(observed.get(index)).equals(surroundings.get(index)), "Canceled native placement changed selected cells");
                    }
                } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
            }
            // Without that listener, ordinary creative placement keeps the same tagged source count.
            ItemStack before = player.getMainHandItem().copy();
            helper.assertTrue(player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    hit(lower.below(), Direction.UP)).consumesAction() && ItemStack.matches(before, player.getMainHandItem()),
                    "Ordinary creative placement did not conserve the source");
            helper.assertTrue(level.getBlockState(lower).is(door()) && level.getBlockState(lower.above()).is(door()), "Creative placement missing a half");
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 40)
    public static void nativePlacementGatePreservesSourceAndExistingDisabledDoorActions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
        try (Fixture fixture = new Fixture(helper, level, lower)) {
            fixture.set(lower.below(), Blocks.IRON_BLOCK.defaultBlockState());
            fixture.set(lower, Blocks.AIR.defaultBlockState()); fixture.set(lower.above(), Blocks.AIR.defaultBlockState());
            FakePlayer player = player(level); player.setPos(lower.getX() + 0.5, lower.getY(), lower.getZ() + 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.AIRLOCK_DOOR.get(), 4));
            player.getItemInHand(InteractionHand.MAIN_HAND).getOrCreateTag().putString("player_note", "preserved");
            ItemStack before = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            helper.assertTrue(!player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    hit(lower.below(), Direction.UP)).consumesAction(), "Disabled native placement consumed action");
            helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()) && level.isEmptyBlock(lower)
                    && level.isEmptyBlock(lower.above()), "Disabled placement debited source or wrote a half");
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, true);
            helper.assertTrue(player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    hit(lower.below(), Direction.UP)).consumesAction(), "Enabled ordinary BlockItem placement failed");
            helper.assertTrue(player.getMainHandItem().getCount() == 3 && before.getTag().equals(player.getMainHandItem().getTag()),
                    "Ordinary survival placement count/tag changed");
            helper.assertTrue(level.getBlockState(lower).is(door()) && level.getBlockState(lower.above()).is(door()), "Two halves not placed");
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            door().setOpen(null, level, level.getBlockState(lower), lower, true);
            helper.assertTrue(level.getBlockState(lower).getValue(DoorBlock.OPEN)
                    && level.getBlockState(lower.above()).getValue(DoorBlock.OPEN), "Disabling content froze existing native actions");
            door().setOpen(null, level, level.getBlockState(lower), lower, false);
            helper.assertTrue(door().observeBoundary(level, lower, level.getBlockState(lower)) == CellObservation.SEALED,
                    "Disabling content disabled existing sealing");
            helper.assertTrue(player.gameMode.destroyBlock(lower), "Disabled existing door cannot be removed");
            helper.assertTrue(level.isEmptyBlock(lower) && level.isEmptyBlock(lower.above()), "Removal left a half");
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 40)
    public static void nativeRedstoneAndSupportChangesSynchronizeBothHalves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
        try (Fixture fixture = new Fixture(helper, level, lower)) {
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            pair(fixture, lower); BlockPos power = lower.east(); fixture.remember(power);
            level.setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            for (BlockPos pos : List.of(lower, lower.above())) {
                helper.assertTrue(level.getBlockState(pos).getValue(DoorBlock.POWERED)
                        && level.getBlockState(pos).getValue(DoorBlock.OPEN), "Redstone failed to open both halves");
            }
            level.removeBlock(power, false);
            helper.assertTrue(!level.getBlockState(lower).getValue(DoorBlock.OPEN)
                    && !level.getBlockState(lower.above()).getValue(DoorBlock.OPEN), "Removing redstone did not close both halves");
            level.removeBlock(lower.below(), false);
            helper.assertTrue(level.isEmptyBlock(lower) && level.isEmptyBlock(lower.above()), "Native support repair left an orphan half");
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 20)
    public static void registeredBoundaryRejectsEveryMismatchedHalfAndDoesNotLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
        try (Fixture fixture = new Fixture(helper, level, lower)) {
            pair(fixture, lower); BlockState bottom = level.getBlockState(lower), top = level.getBlockState(lower.above());
            helper.assertTrue(ModItems.AIRLOCK_DOOR.get().getClass() == BlockItem.class
                    && ((BlockItem) ModItems.AIRLOCK_DOOR.get()).getBlock() == door() && door().asItem() == ModItems.AIRLOCK_DOOR.get()
                    && !bottom.hasBlockEntity() && !bottom.isRandomlyTicking() && !bottom.canOcclude()
                    && bottom.requiresCorrectToolForDrops() && bottom.getDestroySpeed(level, lower) == 5.0F,
                    "Registered airlock content/properties differ from the ordinary iron-door leaf");
            var view = new ServerLevelVolumeWorldView(level, false);
            var beforeMetrics = AtmosphereRuntime.metrics(level);
            for (BlockPos pos : List.of(lower, lower.above())) { helper.assertTrue(view.observe(volume(pos)) == CellObservation.SEALED, "Closed pair not sealed"); }
            for (BlockState malformed : List.of(Blocks.AIR.defaultBlockState(), Blocks.IRON_DOOR.defaultBlockState(), bottom,
                    top.cycle(DoorBlock.FACING), top.cycle(DoorBlock.HINGE), top.cycle(DoorBlock.POWERED), top.cycle(DoorBlock.OPEN))) {
                level.setBlock(lower.above(), malformed, QUIET);
                var queryMetrics = AtmosphereRuntime.metrics(level);
                helper.assertTrue(view.observe(volume(lower)) == CellObservation.OPEN, "Mismatched pair sealed");
                helper.assertTrue(queryMetrics.equals(AtmosphereRuntime.metrics(level)), "Read-only pair query dirtied or scanned atmosphere");
                level.setBlock(lower.above(), top, QUIET);
            }
            door().setOpen(null, level, bottom, lower, true);
            for (BlockPos pos : List.of(lower, lower.above())) { helper.assertTrue(view.observe(volume(pos)) == CellObservation.TRAVERSABLE, "Valid open pair not traversable"); }
            int loaded = level.getChunkSource().getLoadedChunksCount();
            BlockPos missing = new BlockPos(28_000_000, lower.getY(), 28_000_000);
            helper.assertTrue(!level.hasChunkAt(missing), "Far query fixture unexpectedly loaded");
            helper.assertTrue(door().observeBoundary(level, missing, bottom) == CellObservation.UNLOADED
                    && loaded == level.getChunkSource().getLoadedChunksCount(), "Boundary query requested a chunk");
            helper.assertTrue(door().observeBoundary(level, new BlockPos(lower.getX(), level.getMaxBuildHeight() - 1, lower.getZ()), bottom)
                    == CellObservation.OPEN, "Out-of-height counterpart sealed");
            var afterMetrics = AtmosphereRuntime.metrics(level);
            helper.assertTrue(beforeMetrics.map(value -> value.totalInspections()).equals(afterMetrics.map(value -> value.totalInspections())),
                    "Boundary query performed a scan");
            for (BlockState state : door().getStateDefinition().getPossibleStates()) {
                helper.assertTrue(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state)).equals(state),
                        "Native BlockState roundtrip changed an airlock state");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 40)
    public static void survivalAndCreativeRemovalHaveExactLowerHalfDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos lower = helper.absolutePos(new BlockPos(1, 2, 1));
        try (Fixture fixture = new Fixture(helper, level, lower)) {
            FakePlayer player = player(level); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
            player.setPos(lower.getX() + 0.5, lower.getY(), lower.getZ() + 0.5);
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                for (boolean upper : new boolean[] {false, true}) {
                    pair(fixture, lower); player.setGameMode(mode);
                    helper.assertTrue(player.gameMode.destroyBlock(upper ? lower.above() : lower), "Native player destruction failed");
                    helper.assertTrue(level.isEmptyBlock(lower) && level.isEmptyBlock(lower.above()), "Player destruction left a half");
                    var drops = fixture.newDrops(); int count = drops.stream().filter(entity -> entity.getItem().is(ModItems.AIRLOCK_DOOR.get()))
                            .mapToInt(entity -> entity.getItem().getCount()).sum();
                    helper.assertTrue(count == (mode == GameType.SURVIVAL ? 1 : 0), "Ordinary player door drop count changed");
                    drops.forEach(ItemEntity::discard);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 20)
    public static void sixIronRecipeAndSeededNativeLowerHalfExplosionLoot(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager().byKey(ModIdentity.id("airlock_door")).orElseThrow();
        helper.assertTrue(found instanceof ShapedRecipe, "Airlock recipe not shaped"); var recipe = (ShapedRecipe) found;
        helper.assertTrue(recipe.getWidth() == 2 && recipe.getHeight() == 3, "Airlock recipe footprint changed");
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int y = 0; y < 3; y++) { for (int x = 0; x < 2; x++) { grid.setItem(y * 3 + x, new ItemStack(Items.IRON_INGOT)); } }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Six iron recipe does not match");
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.AIRLOCK_DOOR.get()) && result.getCount() == 3 && !result.hasTag(), "Recipe output differs");
        grid.setItem(8, new ItemStack(Items.IRON_INGOT)); helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Extra iron accepted");
        var table = helper.getLevel().getServer().getLootData().getLootTable(ModIdentity.id("blocks/airlock_door"));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1)); int kept = 0, decayed = 0;
        for (int seed = 1; seed <= 64; seed++) {
            BlockState lower = door().defaultBlockState(), upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            helper.assertTrue(dropCount(helper, table.getRandomItems(loot(helper.getLevel(), lower, pos, null), seed)) == 1, "Lower loot differs");
            helper.assertTrue(dropCount(helper, table.getRandomItems(loot(helper.getLevel(), upper, pos, null), seed)) == 0, "Upper loot duplicated");
            int count = dropCount(helper, table.getRandomItems(loot(helper.getLevel(), lower, pos, 4.0F), seed));
            helper.assertTrue(count == dropCount(helper, table.getRandomItems(loot(helper.getLevel(), lower, pos, 4.0F), seed)), "Seeded loot changed");
            if (count == 0) { decayed++; } else { kept++; }
        }
        helper.assertTrue(kept > 0 && decayed > 0, "Finite native loot samples missed a decay outcome"); helper.succeed();
    }

    @GameTest(template = "empty", batch = "airlock", timeoutTicks = 40)
    public static void bothHalfCallbacksRevokeInstalledCachedInflightAndCompletedAirBeforeTick(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon unavailable");
        BlockPos allocated = helper.absolutePos(new BlockPos(1, 1, 1));
        ChunkPos chunk = new ChunkPos(allocated); boolean forced = !moon.getForcedChunks().contains(chunk.toLong());
        try {
            if (forced) { moon.setChunkForced(chunk.x, chunk.z, true); }
            moon.getChunk(chunk.x, chunk.z); // Exactly one fixture chunk; observation/callbacks never request chunks.
            for (boolean upperOnly : new boolean[] {false, true}) {
                for (int phase = 0; phase < 3; phase++) { revokeCase(helper, moon, chunk, upperOnly, phase); }
            }
        } finally { if (forced) { moon.setChunkForced(chunk.x, chunk.z, false); } }
        helper.succeed();
    }

    private static void revokeCase(GameTestHelper helper, ServerLevel level, ChunkPos chunk, boolean upperOnly, int phase) {
        BlockPos cell = new BlockPos(chunk.getMinBlockX() + 8, 180, chunk.getMinBlockZ() + 8);
        BlockPos lower = cell.east().offset(0, upperOnly ? -1 : 0, 0), ventPos = cell.below();
        try (Fixture fixture = new Fixture(helper, level, cell)) {
            for (int x = -1; x <= 1; x++) { for (int y = -1; y <= 1; y++) { for (int z = -1; z <= 1; z++) {
                fixture.set(cell.offset(x, y, z), Blocks.IRON_BLOCK.defaultBlockState());
            } } }
            fixture.set(cell, Blocks.AIR.defaultBlockState()); pair(fixture, lower);
            fixture.set(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            var vent = (OxygenVentBlockEntity) level.getBlockEntity(ventPos);
            vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new)
                    .insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
            vent.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                    .receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
            OxygenVentBlockEntity.serverTick(level, ventPos, vent.getBlockState(), vent);
            AtmosphereManager manager = installedManager(); AtmosphereLevelService service = installedService(manager, level);
            helper.assertTrue(!manager.baseAtmosphereBreathable(level), "Ambient air cannot prove supplied-air revocation");
            for (int tick = 0; tick < 32 && !manager.controlledAt(level, cell); tick++) {
                OxygenVentBlockEntity.serverTick(level, ventPos, vent.getBlockState(), vent); service.tick();
            }
            helper.assertTrue(manager.controlledAt(level, cell), "Installed producer did not establish supplied chamber");
            VolumeScanCoordinator coordinator = (VolumeScanCoordinator) readField(service, "coordinator"); VolumePosition seed = volume(cell);
            if (phase > 0) {
                coordinator.schedule(seed); coordinator.tick(new ServerLevelVolumeWorldView(level, false), phase == 1 ? 1 : 64);
                helper.assertTrue(phase == 1 ? coordinator.taskForSeed(seed).isPresent() : completedContains(coordinator, seed), "Controlled scan phase not established");
            }
            // Twenty-five native door placements create >256 unrelated queued dirty positions, below the 8192 cap.
            for (int x = 0; x < 5; x++) { for (int z = 0; z < 5; z++) {
                pair(fixture, new BlockPos(chunk.getMinBlockX() + 1 + x * 3, 80, chunk.getMinBlockZ() + 1 + z * 3));
            } }
            helper.assertTrue(service.metrics().dirtyPositions() > AtmosphereLimits.MAX_DIRTY_POSITIONS_PER_TICK
                    && manager.controlledAt(level, cell), "Unrelated backlog not established without revoking chamber");
            BlockPos caller = upperOnly ? lower : lower.above(); long serviceTicks = service.metrics().completedServiceTicks();
            if (phase == 0) { door().setOpen(null, level, level.getBlockState(caller), caller, true); }
            else if (phase == 1) {
                BlockPos signal = caller.east(); fixture.remember(signal); level.setBlock(signal, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            } else { level.removeBlock(caller, false); }
            helper.assertTrue(service.metrics().completedServiceTicks() == serviceTicks && !manager.controlledAt(level, cell)
                    && coordinator.taskForSeed(seed).isEmpty() && !completedContains(coordinator, seed),
                    "Native opposite-half callback left cached/in-flight/completed authority before another tick");
            for (int tick = 0; tick < 8; tick++) {
                OxygenVentBlockEntity.serverTick(level, ventPos, vent.getBlockState(), vent); service.tick();
                helper.assertTrue(!manager.controlledAt(level, cell), "Dirty backlog republished an invalidated chamber");
            }
            if (phase == 1) { level.removeBlock(caller.east(), false); }
            if (level.getBlockState(lower).is(door()) && level.getBlockState(lower.above()).is(door())) {
                door().setOpen(null, level, level.getBlockState(lower), lower, false);
            } else { pair(fixture, lower); }
            helper.assertTrue(door().observeBoundary(level, lower, level.getBlockState(lower)) == CellObservation.SEALED,
                    "Ordinary repair/close did not restore a consistent pair");
            for (int tick = 0; tick < 32 && !manager.controlledAt(level, cell); tick++) {
                OxygenVentBlockEntity.serverTick(level, ventPos, vent.getBlockState(), vent); service.tick();
            }
            helper.assertTrue(manager.controlledAt(level, cell), "Installed atmosphere did not recover within 32 controlled service ticks");
        }
    }

    private static void pair(Fixture fixture, BlockPos lower) {
        fixture.set(lower.below(), Blocks.IRON_BLOCK.defaultBlockState());
        fixture.set(lower, door().defaultBlockState());
        fixture.set(lower.above(), door().defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static AirlockDoorBlock door() { return (AirlockDoorBlock) ModBlocks.AIRLOCK_DOOR.get(); }
    private static FakePlayer player(ServerLevel level) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AirlockFixture"));
        player.setGameMode(GameType.SURVIVAL); return player;
    }
    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), face, pos, false);
    }
    private static VolumePosition volume(BlockPos pos) { return new VolumePosition(pos.getX(), pos.getY(), pos.getZ()); }
    private static AtmosphereManager installedManager() { return (AtmosphereManager) readField(null, AtmosphereRuntime.class, "manager"); }
    private static AtmosphereLevelService installedService(AtmosphereManager manager, ServerLevel level) {
        Object service = ((Map<?, ?>) readField(manager, "levels")).get(level.dimension());
        if (!(service instanceof AtmosphereLevelService installed)) { throw new IllegalStateException("Installed Level service missing"); }
        return installed;
    }
    private static boolean completedContains(VolumeScanCoordinator coordinator, VolumePosition seed) {
        return ((List<?>) readField(coordinator, "completed")).stream().anyMatch(value -> ((CompletedVolumeScan) value).seeds().contains(seed));
    }
    private static Object readField(Object owner, String name) { return readField(owner, owner.getClass(), name); }
    private static Object readField(Object owner, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return java.util.Objects.requireNonNull(field.get(owner)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Installed witness unavailable: " + type.getName() + "." + name, failure); }
    }
    private static LootParams loot(ServerLevel level, BlockState state, BlockPos pos, Float radius) {
        var builder = new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.WOODEN_PICKAXE)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
        if (radius != null) { builder.withParameter(LootContextParams.EXPLOSION_RADIUS, radius); }
        return builder.create(LootContextParamSets.BLOCK);
    }
    private static int dropCount(GameTestHelper helper, List<ItemStack> stacks) {
        int count = 0; helper.assertTrue(stacks.size() <= 1, "Multiple loot entries");
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) { helper.assertTrue(stack.getCount() == 0 && stack.getTag() == null, "Malformed empty loot"); }
            else { helper.assertTrue(stack.is(ModItems.AIRLOCK_DOOR.get()) && !stack.hasTag() && stack.getCount() == 1, "Unexpected door loot"); count++; }
        }
        return count;
    }

    /** Owns only saved, BE-free cells and fresh item drops; restoration is attempted for every cell. */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private final List<UUID> oldDrops;
        private final AABB bounds;

        private Fixture(GameTestHelper helper, ServerLevel level, BlockPos centre) {
            this.helper = helper; this.level = level;
            bounds = new AABB(centre).inflate(3);
            oldDrops = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().map(ItemEntity::getUUID).toList();
        }
        private void remember(BlockPos pos) {
            helper.assertTrue(before.size() < 160 || before.containsKey(pos), "Fixture cell bound exceeded");
            helper.assertTrue(!level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                    "Fixture requires an already-loaded BE-free owned cell");
            before.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        private void set(BlockPos pos, BlockState state) { remember(pos); level.setBlock(pos, state, QUIET); }
        private List<ItemEntity> newDrops() { return level.getEntitiesOfClass(ItemEntity.class, bounds).stream().filter(entity -> !oldDrops.contains(entity.getUUID())).toList(); }
        @Override public void close() {
            Throwable first = null;
            for (var entry : before.entrySet()) {
                try {
                    if (level.getBlockEntity(entry.getKey()) instanceof OxygenVentBlockEntity vent) {
                        for (int slot = 0; slot < vent.itemHandler().getSlots(); slot++) { vent.itemHandler().extractItem(slot, 64, false); }
                    }
                    level.setBlock(entry.getKey(), entry.getValue(), QUIET);
                } catch (RuntimeException | Error failure) { if (first == null) { first = failure; } else { first.addSuppressed(failure); } }
            }
            try {
                var drops = newDrops(); helper.assertTrue(drops.size() <= 32, "Fixture fresh-drop bound exceeded");
                for (ItemEntity entity : drops) { entity.discard(); }
            } catch (RuntimeException | Error failure) { if (first == null) { first = failure; } else { first.addSuppressed(failure); } }
            if (first instanceof RuntimeException failure) { throw failure; }
            if (first instanceof Error failure) { throw failure; }
        }
    }
    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
