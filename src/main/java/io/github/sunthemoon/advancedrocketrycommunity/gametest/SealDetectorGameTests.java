package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.SealDetectorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.SealDetectorLifecycle;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Embedded connected fixtures are not native/client proof. No MOD provider or extra registry item is installed. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealDetectorGameTests {
    private static final String PREFIX = "message.advancedrocketrycommunity.seal_detector.";
    private SealDetectorGameTests() { }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void nativeClicksShareTwoTickCooldownAndPreserveHandsAndRecipient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> replies = new ArrayList<>(), others = new ArrayList<>();
        ServerPlayer player = join(helper, "sealHands", replies), peer = join(helper, "sealPeer", others);
        BlockState before = level.getBlockState(target), above = level.getBlockState(target.above());
        try {
            level.setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            place(player, level, target);
            SealDetectorItem item = registered();
            ItemStack main = new ItemStack(item), off = new ItemStack(item);
            main.getOrCreateTag().putString("player_note", "main custom data");
            off.getOrCreateTag().putLong("player_note", Long.MIN_VALUE);
            var mainData = main.save(new net.minecraft.nbt.CompoundTag());
            var offData = off.save(new net.minecraft.nbt.CompoundTag());
            player.setItemInHand(InteractionHand.MAIN_HAND, main); player.setItemInHand(InteractionHand.OFF_HAND, off);
            replies.clear(); others.clear();
            helper.assertTrue(nativeUse(player, InteractionHand.MAIN_HAND, target, Direction.UP).consumesAction(), "Main native use did not respond");
            helper.assertTrue(replies.equals(List.of(expected("sealed", "not_known_supplied"))), "Ambient air was called supplied");
            nativeUse(player, InteractionHand.OFF_HAND, target, Direction.UP);
            helper.assertTrue(replies.size() == 1, "Off-hand bypassed shared cooldown");
            player.getCooldowns().tick(); nativeUse(player, InteractionHand.OFF_HAND, target, Direction.UP);
            helper.assertTrue(replies.size() == 1, "One tick bypassed cooldown");
            player.getCooldowns().tick(); nativeUse(player, InteractionHand.OFF_HAND, target, Direction.UP);
            helper.assertTrue(replies.size() == 2 && others.isEmpty(), "Second hand or recipient isolation differs");
            helper.assertTrue(mainData.equals(main.save(new net.minecraft.nbt.CompoundTag()))
                    && offData.equals(off.save(new net.minecraft.nbt.CompoundTag())), "Measurement changed held count or custom NBT");
        } finally { level.setBlock(target, before, Block.UPDATE_ALL); level.setBlock(target.above(), above, Block.UPDATE_ALL); remove(player); remove(peer); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void disabledInvalidAndForeignDenialFlagsDoNotMeasureOrMutate(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "sealFlags", replies);
        SealDetectorItem item = registered();
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        try {
            place(player, helper.getLevel(), target);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            var metrics = AtmosphereRuntime.metrics(helper.getLevel());
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            replies.clear();
            item.useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP));
            helper.assertTrue(replies.equals(List.of(Component.translatable(PREFIX + "disabled").getString()))
                    && AtmosphereRuntime.metrics(helper.getLevel()).equals(metrics), "Disabled response measured atmosphere");
            player.getCooldowns().removeCooldown(item); replies.clear();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            helper.assertTrue(item.useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP)) == InteractionResult.FAIL
                    && replies.isEmpty() && !player.getCooldowns().isOnCooldown(item), "Invalid stack had side effects");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, target, hit(target, Direction.UP));
            event.setUseItem(Event.Result.DENY); event.setCanceled(true);
            item.onRightClickBlock(event);
            helper.assertTrue(event.isCanceled() && event.getUseItem() == Event.Result.DENY && event.getUseBlock() == Event.Result.DENY,
                    "Routing cleared foreign cancellation or item denial");
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); remove(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void nativeDoorMeasurementDeniesActivationAndKeepsOrdinaryInvalidation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "sealDoor", replies);
        BlockState below = level.getBlockState(target.below()), lower = level.getBlockState(target), upper = level.getBlockState(target.above());
        AtmosphereManager local = new AtmosphereManager(new CelestialEnvironmentService(new CelestialCatalogManager()));
        try {
            level.setBlock(target.below(), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target, Blocks.OAK_DOOR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            place(player, level, target); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            nativeUse(player, InteractionHand.MAIN_HAND, target, Direction.WEST);
            player.getCooldowns().removeCooldown(registered()); replies.clear();
            var metrics = AtmosphereRuntime.metrics(level);
            nativeUse(player, InteractionHand.MAIN_HAND, target, Direction.WEST);
            helper.assertTrue(!level.getBlockState(target).getValue(BlockStateProperties.OPEN)
                    && replies.equals(List.of(expected("sealed", "not_known_supplied"))) && metrics.equals(AtmosphereRuntime.metrics(level)),
                    "Detector activated door or changed synchronous dirty/scan counters");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var ordinary = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, target, hit(target, Direction.WEST));
            new AtmosphereServerEvents(local).onRightClickBlock(ordinary);
            helper.assertTrue(local.metrics(level.dimension()).orElseThrow().dirtyPositions() > 0, "Old adapter constructor lost ordinary door invalidation");
            nativeUse(player, InteractionHand.MAIN_HAND, target, Direction.WEST);
            helper.assertTrue(level.getBlockState(target).getValue(BlockStateProperties.OPEN), "Ordinary native door activation was suppressed");
        } finally {
            local.clear(); level.setBlock(target, lower, Block.UPDATE_ALL); level.setBlock(target.above(), upper, Block.UPDATE_ALL);
            level.setBlock(target.below(), below, Block.UPDATE_ALL); remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void nativeShapesFluidsDoorsAndAmbientSupplyKeepSeparateMeanings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> replies = new ArrayList<>(); ServerPlayer player = join(helper, "sealStates", replies);
        BlockState before = level.getBlockState(target), above = level.getBlockState(target.above());
        try {
            place(player, level, target); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockState[] states = {Blocks.STONE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                    Blocks.AIR.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState(),
                    Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true), Blocks.OAK_FENCE_GATE.defaultBlockState(),
                    Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.OPEN, true)};
            String[] boundaries = {"sealed", "open", "sealed", "open", "open", "sealed", "open", "sealed", "open"};
            for (int i = 0; i < states.length; i++) {
                level.setBlock(target, states[i], Block.UPDATE_ALL); replies.clear(); player.getCooldowns().removeCooldown(registered());
                // Direct native Item use preserves a selected AIR cell too; it is not a client packet/ray.
                registered().useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP));
                helper.assertTrue(replies.equals(List.of(expected(boundaries[i], "not_known_supplied"))), "State/supply projection differs at " + i);
            }
        } finally { level.setBlock(target, before, Block.UPDATE_ALL); level.setBlock(target.above(), above, Block.UPDATE_ALL); remove(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void localCompiledCatalogProofRetainsTagsAndDoorPrecedence(GameTestHelper helper) {
        // A local compiled catalog, not an installed detector-runtime provider claim.
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState before = level.getBlockState(target);
        var compiler = new AtmosphereBoundaryRegistry(ForgeRegistries.BLOCKS::getValue);
        try {
            var registrar = compiler.forOwner("seal_local");
            registrar.register(new ResourceLocation("seal_local", "open"),
                    Set.of(new ResourceLocation("minecraft", "stone"), new ResourceLocation("minecraft", "oak_trapdoor")),
                    state -> AtmosphereBoundary.PERMEABLE);
            registrar.register(new ResourceLocation("seal_local", "closed"), Set.of(new ResourceLocation("minecraft", "iron_bars")),
                    state -> AtmosphereBoundary.SEALED);
            var view = new ServerLevelVolumeWorldView(level, false, compiler.freeze());
            var position = new VolumePosition(target.getX(), target.getY(), target.getZ());
            level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(view.observe(position) == CellObservation.TRAVERSABLE, "Compiled rule did not override full shape");
            level.setBlock(target, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(view.observe(position) == CellObservation.TRAVERSABLE, "Permeable tag lost precedence over compiled rule");
            level.setBlock(target, Blocks.OAK_TRAPDOOR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(view.observe(position) == CellObservation.SEALED, "Closed door lost precedence over compiled rule");
        } finally { compiler.close(); level.setBlock(target, before, Block.UPDATE_ALL); }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "seal_detector", timeoutTicks = 40)
    public static void ordinaryProviderPublishesSupplyWithoutMeasurementDebit(GameTestHelper helper) { roomCase(helper, false); }

    @GameTest(template = "atmosphere_test", batch = "seal_detector", timeoutTicks = 40)
    public static void incompleteOrdinaryScanReportsPendingWithoutMeasurementScan(GameTestHelper helper) { roomCase(helper, true); }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void foreignLifecycleCannotReplaceOrCloseInstalledReader(GameTestHelper helper) {
        List<String> replies = new ArrayList<>(); ServerPlayer player = join(helper, "sealOwner", replies);
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState before = helper.getLevel().getBlockState(target), above = helper.getLevel().getBlockState(target.above());
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(new CelestialCatalogManager()));
        SealDetectorLifecycle foreign = new SealDetectorLifecycle(() -> manager, AtmosphereBoundaryCatalog::empty);
        try {
            helper.getLevel().setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(target.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            place(player, helper.getLevel(), target); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            registered().useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP));
            helper.assertTrue(replies.equals(List.of(expected("sealed", "not_known_supplied"))), "Installed reader was unavailable before foreign lifecycle");
            boolean refused = false;
            try { foreign.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer())); }
            catch (IllegalStateException expected) { refused = true; }
            helper.assertTrue(refused, "Foreign lifecycle replaced installed detector");
            foreign.onServerStopping(new ServerStoppingEvent(helper.getLevel().getServer()));
            foreign.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
            player.getCooldowns().removeCooldown(registered()); replies.clear();
            registered().useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP));
            helper.assertTrue(replies.equals(List.of(expected("sealed", "not_known_supplied"))), "Foreign closure removed installed reader");
        } finally {
            manager.clear(); helper.getLevel().setBlock(target, before, Block.UPDATE_ALL);
            helper.getLevel().setBlock(target.above(), above, Block.UPDATE_ALL); remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void validTopCellKeepsBoundaryButRejectsOutOfBuildNeighbor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos target = new BlockPos(origin.getX(), level.getMaxBuildHeight() - 1, origin.getZ());
        BlockState before = level.getBlockState(target); List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "sealHeight", replies);
        try {
            level.setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            player.teleportTo(level, target.getX() - 1.5, target.getY() - 1, target.getZ() + 0.5, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            registered().useOn(context(player, InteractionHand.MAIN_HAND, target, Direction.UP));
            helper.assertTrue(replies.equals(List.of(expected("sealed", "unavailable"))), "Invalid neighbor erased valid boundary or queried supply");
        } finally { level.setBlock(target, before, Block.UPDATE_ALL); remove(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void controlledDetachedUnknownCellReturnsUnavailableWithoutLoading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "sealUnknown", replies); BlockPos far = new BlockPos(25_000_000, 80, 25_000_000);
        Vec3 original = player.position(); boolean detached = false;
        try {
            helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Unknown cell fixture was already loaded");
            helper.assertTrue(level.getForcedChunks().size() <= 4096, "Forced fixture copy exceeds 4096 marks");
            Set<Long> forced = Set.copyOf(level.getForcedChunks());
            int loaded = level.getChunkSource().getLoadedChunksCount(); var metrics = AtmosphereRuntime.metrics(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            player.onRemovedFromWorld(); detached = true;
            player.setPos(far.getX(), far.getY(), far.getZ()); // Native onMove still runs; not a normal client move claim.
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded
                    && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Detached coordinate setup loaded the selected cell");
            registered().useOn(context(player, InteractionHand.MAIN_HAND, far, Direction.UP));
            helper.assertTrue(replies.equals(List.of(Component.translatable(PREFIX + "unavailable").getString()))
                    && level.getChunkSource().getLoadedChunksCount() == loaded && forced.equals(level.getForcedChunks())
                    && metrics.equals(AtmosphereRuntime.metrics(level)), "Unknown-cell query loaded, ticketed or measured the world");
        } finally {
            if (detached) { player.setPos(original.x, original.y, original.z); player.onAddedToWorld(); }
            remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector", timeoutTicks = 40)
    public static void ordinaryRecipeCraftsOnlyTheExactThreeVanillaInputs(GameTestHelper helper) {
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        grid.setItem(0, new ItemStack(Items.REDSTONE)); grid.setItem(4, new ItemStack(Items.GLASS_PANE)); grid.setItem(8, new ItemStack(Items.IRON_INGOT));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.getItem() == registered() && result.getCount() == 1 && !result.hasTag(), "Detector crafting output differs");
        grid.setItem(7, new ItemStack(Items.REDSTONE));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "An extra ingredient matched exact recipe"); helper.succeed();
    }

    private static void roomCase(GameTestHelper helper, boolean pending) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon fixture level is unavailable");
        BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos ventPos = new BlockPos(origin.getX(), pending ? 140 : 120, origin.getZ());
        int radius = pending ? 4 : 1, height = pending ? 8 : 2;
        Set<ChunkPos> added = new LinkedHashSet<>(); List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, pending ? "sealPending" : "sealSupply", replies);
        Runnable cleanup = () -> {
            try { remove(player); } finally {
                try { for (int x = -radius; x <= radius; x++) { for (int y = 0; y <= height; y++) { for (int z = -radius; z <= radius; z++) {
                    moon.removeBlock(ventPos.offset(x, y, z), false);
                } } } } finally { for (ChunkPos chunk : added) { moon.setChunkForced(chunk.x, chunk.z, false); } }
            }
        };
        try {
            for (int x = (ventPos.getX() - radius) >> 4; x <= (ventPos.getX() + radius) >> 4; x++) {
                for (int z = (ventPos.getZ() - radius) >> 4; z <= (ventPos.getZ() + radius) >> 4; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    if (!moon.getForcedChunks().contains(chunk.toLong())) { moon.setChunkForced(x, z, true); added.add(chunk); }
                    moon.getChunk(x, z); // At most four fixture chunks; never measurement-side loading.
                }
            }
            for (int x = -radius; x <= radius; x++) { for (int y = 0; y <= height; y++) { for (int z = -radius; z <= radius; z++) {
                boolean wall = Math.abs(x) == radius || Math.abs(z) == radius || y == 0 || y == height;
                moon.setBlock(ventPos.offset(x, y, z), (wall ? Blocks.IRON_BLOCK : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
            } } }
            moon.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
            OxygenVentBlockEntity vent = (OxygenVentBlockEntity) moon.getBlockEntity(ventPos);
            if (!pending) {
                vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new)
                        .insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
                vent.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                        .receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(registered()));
            boolean[] done = {false};
            helper.onEachTick(() -> {
                if (done[0]) { return; }
                try {
                    player.teleportTo(moon, ventPos.getX() + 0.5, ventPos.getY(), ventPos.getZ() + 0.5, 0, 0);
                    OxygenVentBlockEntity.serverTick(moon, ventPos, vent.getBlockState(), vent); // Fixture producer, not query.
                    int energy = vent.energyStored(), oxygen = vent.oxygenUnits();
                    var metrics = AtmosphereRuntime.metrics(moon).orElseThrow();
                    BlockPos target = pending ? ventPos : ventPos.offset(-radius, 1, 0);
                    replies.clear(); player.getCooldowns().removeCooldown(registered());
                    registered().useOn(context(player, InteractionHand.MAIN_HAND, target, pending ? Direction.UP : Direction.EAST));
                    helper.assertTrue(energy == vent.energyStored() && oxygen == vent.oxygenUnits()
                            && metrics.totalInspections() == AtmosphereRuntime.metrics(moon).orElseThrow().totalInspections(), "Measurement ticked/scanned/debited provider");
                    String expected = expected("sealed", pending ? "pending" : "supplied");
                    if (!replies.equals(List.of(expected))) {
                        helper.assertTrue(helper.getTick() < 39, "Ordinary producer did not publish detector fixture within 40 ticks: " + replies);
                        return;
                    }
                    done[0] = true; cleanup.run(); helper.succeed();
                } catch (RuntimeException failure) {
                    if (!done[0]) { done[0] = true; try { cleanup.run(); } catch (RuntimeException failedCleanup) { failure.addSuppressed(failedCleanup); } }
                    throw failure;
                }
            });
        } catch (RuntimeException failure) {
            try { cleanup.run(); } catch (RuntimeException failedCleanup) { failure.addSuppressed(failedCleanup); }
            throw failure;
        }
    }

    private static SealDetectorItem registered() {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("advancedrocketrycommunity", "seal_detector"));
        if (!(item instanceof SealDetectorItem detector)) { throw new IllegalStateException("Root detector registration is required"); }
        return detector;
    }
    private static ServerPlayer join(GameTestHelper helper, String name, List<String> replies) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), replies);
    }
    private static void remove(ServerPlayer player) { player.getServer().getPlayerList().remove(player); }
    private static void place(ServerPlayer player, ServerLevel level, BlockPos target) {
        player.teleportTo(level, target.getX() - 1.5, target.getY(), target.getZ() + 0.5, 0, 0);
    }
    private static BlockHitResult hit(BlockPos target, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(target).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5), face, target, false);
    }
    private static UseOnContext context(ServerPlayer player, InteractionHand hand, BlockPos target, Direction face) {
        return new UseOnContext(player, hand, hit(target, face));
    }
    private static InteractionResult nativeUse(ServerPlayer player, InteractionHand hand, BlockPos target, Direction face) {
        return player.gameMode.useItemOn(player, player.serverLevel(), player.getItemInHand(hand), hand, hit(target, face));
    }
    private static String expected(String boundary, String supply) {
        return Component.translatable(PREFIX + "reading", Component.translatable(PREFIX + "boundary." + boundary), Component.translatable(PREFIX + "supply." + supply)).getString();
    }
    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
