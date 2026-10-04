package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerLifecycle;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Compile-only until Root registration/DataGen. Embedded connected players are not real-client evidence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereAnalyzerGameTests {
    private static final ResourceLocation DISABLED_FIXTURE_ID = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "gametest_analyzer_disabled");
    private static final ResourceLocation UNAVAILABLE_FIXTURE_ID = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "gametest_analyzer_unavailable");
    private static final AtomicInteger FIXTURE_QUERIES = new AtomicInteger();

    private AtmosphereAnalyzerGameTests() { }

    /** Entries exist only in disposable dedicated GameTest registries, not ordinary profiles. */
    @Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class FixtureItems {
        private FixtureItems() { }

        @SubscribeEvent
        public static void onRegisterItems(RegisterEvent event) {
            if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || !ForgeGameTestHooks.isGametestServer()
                    || !event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) { return; }
            event.register(ForgeRegistries.Keys.ITEMS, DISABLED_FIXTURE_ID,
                    () -> new AtmosphereAnalyzerItem(new Item.Properties(), () -> false, player -> {
                        FIXTURE_QUERIES.incrementAndGet(); return AnalyzerReading.unavailable();
                    }));
            event.register(ForgeRegistries.Keys.ITEMS, UNAVAILABLE_FIXTURE_ID,
                    () -> new AtmosphereAnalyzerItem(new Item.Properties(), () -> true, player -> {
                        FIXTURE_QUERIES.incrementAndGet(); return AnalyzerReading.unavailable();
                    }));
        }
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void registeredUseSharesTwoTickCooldownAndPreservesHeldData(GameTestHelper helper) {
        List<String> replies = new ArrayList<>(), peerReplies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerUse", replies);
        ServerPlayer peer = join(helper, "analyzerPeer", peerReplies);
        try {
            Item item = registered();
            helper.assertTrue(item instanceof AtmosphereAnalyzerItem && item.getMaxStackSize() == 1, "Analyzer registration differs");
            ItemStack main = new ItemStack(item), off = new ItemStack(item);
            CompoundTag custom = new CompoundTag(); custom.putLong("untouched", Long.MAX_VALUE); custom.putString("note", "player data");
            main.setTag(custom.copy()); off.setTag(custom.copy());
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            replies.clear(); peerReplies.clear();
            var result = item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getObject() == main && replies.size() == 1, "Main hand did not return exactly one chat reply");
            helper.assertTrue(!replies.get(0).startsWith(ConnectedTestPlayers.ACTION_BAR), "Analyzer replaced hazard action bar");
            item.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
            helper.assertTrue(replies.size() == 1 && peerReplies.isEmpty(), "Cooldown/recipient boundary changed");
            player.getCooldowns().tick();
            item.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
            helper.assertTrue(replies.size() == 1, "One tick bypassed shared cooldown");
            player.getCooldowns().tick();
            item.use(helper.getLevel(), player, InteractionHand.OFF_HAND);
            helper.assertTrue(replies.size() == 2, "Two ticks did not admit the other hand");
            helper.assertTrue(main.getCount() == 1 && off.getCount() == 1 && custom.equals(main.getTag()) && custom.equals(off.getTag()),
                    "Native analyzer use mutated held resource or custom NBT");
        } finally { remove(helper, player); remove(helper, peer); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void disabledUnavailableAndInvalidUsesDoNotQueryOrMutate(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerFlags", replies);
        AtomicInteger queries = FIXTURE_QUERIES;
        queries.set(0);
        try {
            var disabled = fixtureItem(DISABLED_FIXTURE_ID);
            var unavailable = fixtureItem(UNAVAILABLE_FIXTURE_ID);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(disabled)); replies.clear();
            disabled.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(queries.get() == 0 && replies.size() == 1 && player.getCooldowns().isOnCooldown(disabled), "Disabled queried or skipped cooldown");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(unavailable)); replies.clear();
            unavailable.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            unavailable.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(queries.get() == 1 && replies.size() == 1 && player.getCooldowns().isOnCooldown(unavailable), "Unavailable queried twice or skipped cooldown");
            player.getCooldowns().removeCooldown(unavailable);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(unavailable, 2)); replies.clear();
            unavailable.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(queries.get() == 1 && replies.isEmpty() && !player.getCooldowns().isOnCooldown(unavailable), "Invalid count had side effects");
            var detached = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "detached"));
            detached.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(unavailable));
            unavailable.use(helper.getLevel(), detached, InteractionHand.MAIN_HAND);
            helper.assertTrue(queries.get() == 1 && !detached.getCooldowns().isOnCooldown(unavailable), "Unconnected identity was admitted");
        } finally { queries.set(0); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void earthMoonAndStoppedHandlesUseActualEyeAndNoEquipment(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerBodies", replies);
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var service = service(helper, catalogs, manager);
        try {
            AnalyzerReading earth = service.read(player);
            helper.assertTrue(earth.state() == AnalyzerReading.State.BREATHABLE && earth.ambient().orElseThrow().pressure() == 1.0D,
                    "Earth ambient/effective state differs");
            nativeUse(helper, player, replies, earth);
            ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon is unavailable");
            BlockPos moonPos = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX(), 80, helper.absolutePos(BlockPos.ZERO).getZ());
            moon.getChunkAt(moonPos); // Fixture setup, never analyzer query-side loading.
            player.teleportTo(moon, moonPos.getX() + 0.5, moonPos.getY(), moonPos.getZ() + 0.5, 0, 0);
            AnalyzerReading vacuum = service.read(player);
            helper.assertTrue(vacuum.state() == AnalyzerReading.State.NON_BREATHABLE && vacuum.ambient().orElseThrow().pressure() == 0.0D,
                    "Moon was exempted by the creative player");
            nativeUse(helper, player, replies, vacuum);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));
            helper.assertTrue(SpaceSuitOxygen.set(player.getItemBySlot(EquipmentSlot.CHEST), 777), "Suit fixture refill failed");
            player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().putString("untouched", "suit custom data");
            CompoundTag suit = player.getItemBySlot(EquipmentSlot.CHEST).save(new CompoundTag());
            helper.assertTrue(service.read(player).equals(vacuum) && suit.equals(player.getItemBySlot(EquipmentSlot.CHEST).save(new CompoundTag())),
                    "Equipment affected the atmosphere reading or was mutated");
            helper.assertTrue(CompletableFuture.supplyAsync(() -> service.read(player)).join().state() == AnalyzerReading.State.UNAVAILABLE,
                    "Off-thread handle read was admitted");
            helper.assertTrue(!catalogs.applyCandidate(DataResult.error(() -> "test rejected reload")) && service.read(player).equals(vacuum),
                    "Rejected reload lost the last-good observation");
            service.close();
            helper.assertTrue(service.read(player).state() == AnalyzerReading.State.UNAVAILABLE, "Stopped handle remained active");
        } finally { service.close(); manager.clear(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void suppliedEyeCellKeepsAmbientAndQueriesDoNotSpendVentResources(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerSupply", replies);
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var service = service(helper, catalogs, manager);
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon is unavailable");
        BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos ventPos = new BlockPos(center.getX(), 80, center.getZ());
        Set<ChunkPos> forced = new LinkedHashSet<>();
        try {
            prepareRoomChunks(moon, ventPos, 1, forced);
            for (int x = -1; x <= 1; x++) { for (int y = 0; y <= 2; y++) { for (int z = -1; z <= 1; z++) {
                moon.setBlock(ventPos.offset(x, y, z), (x == 0 && y == 1 && z == 0 ? Blocks.AIR : Blocks.IRON_BLOCK).defaultBlockState(), Block.UPDATE_ALL);
            } } }
            moon.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
            OxygenVentBlockEntity vent = (OxygenVentBlockEntity) moon.getBlockEntity(ventPos);
            var input = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
            helper.assertTrue(input.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false).isEmpty(), "Vent input refused fixture");
            OxygenVentBlockEntity.serverTick(moon, ventPos, vent.getBlockState(), vent);
            vent.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
            awaitRuntimeFixture(helper, player, moon, vent, AnalyzerReading.State.BREATHABLE, true, () -> {
                    manager.observeVent(moon, vent);
                    manager.tick(helper.getLevel().getServer()); // Record balances after this explicit supply tick.
                    player.teleportTo(moon, ventPos.getX() + 0.5, ventPos.getY(), ventPos.getZ() + 0.5, 0, 0);
                    int energy = vent.energyStored(), oxygen = vent.oxygenUnits();
                    var reading = service.read(player);
                    helper.assertTrue(reading.supplied() && reading.state() == AnalyzerReading.State.BREATHABLE
                            && reading.ambient().orElseThrow().pressure() == 0.0D && reading.ambient().orElseThrow().temperatureKelvin() == 220.0D,
                            "Eye-cell supply was confused with feet or ambient profile: reading=" + reading
                                    + " vent=" + vent.status() + " metrics=" + manager.metrics(moon.dimension())
                                    + " eye=" + BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
                    helper.assertTrue(!manager.controlledAt(moon, ventPos), "Feet fixture was unexpectedly in the indexed room");
                    helper.assertTrue(service.read(player).equals(reading) && vent.energyStored() == energy && vent.oxygenUnits() == oxygen,
                            "Analyzer queried by ticking or debiting a vent");
                    nativeUse(helper, player, replies, reading);
                    helper.assertTrue(vent.energyStored() == energy && vent.oxygenUnits() == oxygen, "Registered item debited the supplied vent");
                    moon.setBlock(ventPos.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    manager.markDirty(moon, ventPos.above(2));
                    helper.assertTrue(!service.read(player).supplied(), "Dirty wall retained positive supplied authority");
            }, () -> cleanRoomFixture(helper, player, service, manager, moon, ventPos, 1, 2, forced));
        } catch (RuntimeException failed) {
            cleanRoomFixture(helper, player, service, manager, moon, ventPos, 1, 2, forced);
            throw failed;
        }
    }

    @GameTest(template = "atmosphere_test", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void incompleteBudgetedRoomReportsPendingWithoutPositiveSupplyOrQueryTick(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerPending", replies);
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var service = service(helper, catalogs, manager);
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon is unavailable");
        BlockPos center = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos ventPos = new BlockPos(center.getX(), 80, center.getZ());
        Set<ChunkPos> forced = new LinkedHashSet<>();
        // 343 traversable cells exceed one task's immutable 256-observation budget, but stay below the 4096-cell limit.
        try {
            prepareRoomChunks(moon, ventPos, 4, forced);
            for (int x = -4; x <= 4; x++) { for (int y = 0; y <= 8; y++) { for (int z = -4; z <= 4; z++) {
                boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4 || y == 0 || y == 8;
                moon.setBlock(ventPos.offset(x, y, z), (wall ? Blocks.IRON_BLOCK : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
            } } }
            moon.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
            OxygenVentBlockEntity vent = (OxygenVentBlockEntity) moon.getBlockEntity(ventPos);
            OxygenVentBlockEntity.serverTick(moon, ventPos, vent.getBlockState(), vent);
            awaitRuntimeFixture(helper, player, moon, vent, AnalyzerReading.State.PENDING, false, () -> {
                    manager.observeVent(moon, vent);
                    manager.tick(helper.getLevel().getServer());
                    player.teleportTo(moon, ventPos.getX() + 0.5, ventPos.getY(), ventPos.getZ() + 0.5, 0, 0);
                    long inspections = manager.metrics(moon.dimension()).orElseThrow().totalInspections();
                    int energy = vent.energyStored(), oxygen = vent.oxygenUnits();
                    var reading = service.read(player);
                    helper.assertTrue(reading.state() == AnalyzerReading.State.PENDING && !reading.supplied()
                            && reading.ambient().orElseThrow().pressure() == 0.0D, "Incomplete scan made a positive or unknown ambient claim: reading="
                                    + reading + " vent=" + vent.status() + " metrics=" + manager.metrics(moon.dimension())
                                    + " eye=" + BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
                    helper.assertTrue(service.read(player).equals(reading)
                            && manager.metrics(moon.dimension()).orElseThrow().totalInspections() == inspections
                            && vent.energyStored() == energy && vent.oxygenUnits() == oxygen, "Pending query scanned or spent resources");
                    nativeUse(helper, player, replies, reading);
                    helper.assertTrue(vent.energyStored() == energy && vent.oxygenUnits() == oxygen, "Registered pending use spent resources");
            }, () -> cleanRoomFixture(helper, player, service, manager, moon, ventPos, 4, 8, forced));
        } catch (RuntimeException failed) {
            cleanRoomFixture(helper, player, service, manager, moon, ventPos, 4, 8, forced); throw failed;
        }
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void catalogIdentityChangeDuringReadReturnsUnavailableNotMixedGeneration(GameTestHelper helper) {
        ServerPlayer player = join(helper, "analyzerCatalog", new ArrayList<>());
        var first = catalogs().snapshot();
        var second = catalogs().snapshot();
        AtomicInteger captures = new AtomicInteger();
        CelestialCatalogManager swapping = CelestialCatalogManager.readOnly(() -> captures.getAndIncrement() == 0 ? first : second);
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(swapping));
        var service = service(helper, swapping, manager);
        try {
            helper.assertTrue(service.read(player).state() == AnalyzerReading.State.UNAVAILABLE && captures.get() > 1,
                    "A changed captured catalog leaked a mixed-generation result");
        } finally { service.close(); manager.clear(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void committedStationReadsSpaceNotOrbitedSurfaceAndReservationIsUnavailable(GameTestHelper helper) {
        List<String> replies = new ArrayList<>();
        ServerPlayer player = join(helper, "analyzerStation", replies);
        var server = helper.getLevel().getServer();
        var data = StationRegistrySavedData.get(server);
        UUID stationId = UUID.randomUUID();
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var service = service(helper, catalogs, manager);
        boolean committed = false;
        try {
            var reservation = data.reserve(stationId, player.getUUID(), "Analyzer", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
            ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space is unavailable");
            BlockPos pad = new BlockPos(reservation.landingPad().x(), 128, reservation.landingPad().z());
            space.getChunkAt(pad);
            player.teleportTo(space, pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0, 0);
            helper.assertTrue(service.read(player).state() == AnalyzerReading.State.UNAVAILABLE, "Reserved station was published");
            nativeUse(helper, player, replies, AnalyzerReading.unavailable());
            data.commit(stationId); committed = true;
            var reading = service.read(player);
            helper.assertTrue(reading.bodyId().orElseThrow().equals(CelestialIds.EARTH_ID.toString())
                    && reading.ambientBodyId().orElseThrow().equals(CelestialIds.SPACE_ID.toString())
                    && reading.locus().orElseThrow() == AnalyzerReading.Locus.ORBIT
                    && reading.ambient().orElseThrow().pressure() == 0.0D && reading.ambient().orElseThrow().temperatureKelvin() == 3.0D,
                    "Station orbit body leaked its surface atmosphere into actual Space");
            nativeUse(helper, player, replies, reading);
        } finally { service.close(); manager.clear(); remove(helper, player); if (committed) { data.delete(stationId); } else { data.release(stationId); } }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void unloadedEyeRefusesWithoutCreatingTicketsOrAtmosphereService(GameTestHelper helper) {
        ServerPlayer player = join(helper, "analyzerUnloaded", new ArrayList<>());
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var service = service(helper, catalogs, manager);
        try {
            ServerLevel level = helper.getLevel();
            BlockPos far = new BlockPos(25_000_000, 80, 25_000_000);
            helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Far fixture was loaded");
            int loaded = level.getChunkSource().getLoadedChunksCount();
            assertConnected(helper, player, level);
            helper.assertTrue(player.isAddedToWorld(), "Connected fixture was not attached");
            helper.assertTrue(level.getForcedChunks().size() <= 4096, "Forced snapshot fixture exceeds its 4096-mark bound");
            Set<Long> forcedBefore = Set.copyOf(level.getForcedChunks()); // Not the unmodifiable backing view.
            var original = player.position();
            // Controlled detached-coordinate query, not a claim about normal client movement.
            player.onRemovedFromWorld();
            try {
                player.setPos(far.getX(), far.getY(), far.getZ()); // Retains the native onMove callback.
                assertConnected(helper, player, level);
                helper.assertTrue(!player.isAddedToWorld()
                                && BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()).equals(far.above())
                                && level.getChunkSource().getLoadedChunksCount() == loaded
                                && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
                                && level.getForcedChunks().size() <= 4096 && forcedBefore.equals(level.getForcedChunks()),
                        "Movement setup loaded the target or changed the admitted fixture identity/tickets");
                var reading = service.read(player);
                helper.assertTrue(reading.state() == AnalyzerReading.State.UNAVAILABLE && manager.metrics(level.dimension()).isEmpty(),
                        "Unknown eye queried an atmosphere service: reading=" + reading + " metrics=" + manager.metrics(level.dimension())
                                + " actualPosition=" + player.position() + " eye=" + BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())
                                + " farLoaded=" + (level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null));
                helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loaded
                                && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
                                && !level.getForcedChunks().contains(ChunkPos.asLong(far.getX() >> 4, far.getZ() >> 4))
                                && level.getForcedChunks().size() <= 4096 && forcedBefore.equals(level.getForcedChunks()),
                        "Analyzer changed chunk loading");
            } finally {
                try { player.setPos(original); } finally { player.onAddedToWorld(); }
            }
            helper.assertTrue(player.isAddedToWorld(), "Controlled fixture did not restore attachment");
        } finally { service.close(); manager.clear(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void foreignLifecycleCannotReplaceOrRemoveTheInstalledHost(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "analyzerHost", new ArrayList<>());
        CelestialCatalogManager catalogs = catalogs();
        AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
        var foreignOwner = new AtmosphereAnalyzerLifecycle(catalogs, () -> manager);
        try {
            var before = AtmosphereAnalyzerRuntime.read(player);
            helper.assertTrue(before.state() != AnalyzerReading.State.UNAVAILABLE, "Root analyzer lifecycle was not installed");
            boolean refused = false;
            try { foreignOwner.onServerStarted(new ServerStartedEvent(server)); }
            catch (IllegalStateException expected) { refused = true; }
            helper.assertTrue(refused, "Another lifecycle replaced the installed service");
            // These are direct calls to the unregistered foreign instance, not server shutdown events on the global bus.
            foreignOwner.onServerStopping(new ServerStoppingEvent(server));
            foreignOwner.onServerStopped(new ServerStoppedEvent(server));
            helper.assertTrue(AtmosphereAnalyzerRuntime.read(player).equals(before), "Foreign lifecycle removed the current host");
        } finally { manager.clear(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_analyzer", timeoutTicks = 40)
    public static void registeredRecipeUsesOnlyTheThreeAdoptedInputs(GameTestHelper helper) {
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        grid.setItem(0, new ItemStack(ModItems.BASIC_CIRCUIT.get()));
        grid.setItem(4, new ItemStack(Items.GLASS_PANE));
        grid.setItem(8, new ItemStack(Items.IRON_INGOT));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
        var result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.getItem() == registered() && result.getCount() == 1 && !result.hasTag(), "Analyzer crafting output differs");
        grid.setItem(7, new ItemStack(Items.REDSTONE));
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Extra input matched the exact recipe");
        helper.succeed();
    }

    private static Item registered() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("advancedrocketrycommunity", "atmosphere_analyzer"));
        if (!(item instanceof AtmosphereAnalyzerItem)) { throw new IllegalStateException("Root analyzer registration is required"); }
        return item;
    }
    private static AtmosphereAnalyzerItem fixtureItem(ResourceLocation id) {
        var value = ForgeRegistries.ITEMS.getValue(id);
        if (!(value instanceof AtmosphereAnalyzerItem analyzer) || !id.equals(ForgeRegistries.ITEMS.getKey(value))) {
            throw new IllegalStateException("Dedicated GameTest analyzer item was not registered: " + id);
        }
        return analyzer;
    }
    private static void assertConnected(GameTestHelper helper, ServerPlayer player, ServerLevel level) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(!(player instanceof FakePlayer) && !player.isRemoved() && !player.hasDisconnected()
                        && player.isAlive() && !player.isSpectator() && player.connection != null
                        && player.getServer() == server && server.getPlayerList().getPlayer(player.getUUID()) == player
                        && player.serverLevel() == level && server.getLevel(level.dimension()) == level,
                "Controlled fixture lost an admitted connected player/Level identity");
    }
    private static ServerPlayer join(GameTestHelper helper, String name, List<String> replies) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), replies);
    }
    private static void remove(GameTestHelper helper, ServerPlayer player) { helper.getLevel().getServer().getPlayerList().remove(player); }
    private static void nativeUse(GameTestHelper helper, ServerPlayer player, List<String> replies, AnalyzerReading expected) {
        var item = registered();
        var actual = AtmosphereAnalyzerRuntime.read(player);
        helper.assertTrue(actual.equals(expected), "Registered runtime context differs from the fixture reading: actual="
                + actual + " expected=" + expected + " rootMetrics=" + AtmosphereRuntime.metrics(player.serverLevel()));
        var held = new ItemStack(item);
        held.getOrCreateTag().putLong("untouched", Long.MIN_VALUE);
        var before = held.save(new CompoundTag());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.getCooldowns().removeCooldown(item);
        replies.clear();
        var result = item.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getObject() == held && replies.size() == 1 && !replies.get(0).startsWith(ConnectedTestPlayers.ACTION_BAR)
                && player.getCooldowns().isOnCooldown(item) && before.equals(held.save(new CompoundTag())),
                "Registered native use changed context/recipient/data/cooldown semantics");
    }
    private static void clearRoom(ServerLevel level, BlockPos origin, int radius, int height) {
        for (int x = -radius; x <= radius; x++) { for (int y = 0; y <= height; y++) { for (int z = -radius; z <= radius; z++) {
            level.removeBlock(origin.offset(x, y, z), false);
        } } }
    }
    /** Fixture setup only: pin the bounded shell before writes and permit ordinary vent ticks. */
    private static void prepareRoomChunks(ServerLevel level, BlockPos center, int radius, Set<ChunkPos> added) {
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(x, z, true);
                    added.add(chunk);
                }
                level.getChunk(x, z); // Finish fixture generation before creating BlockEntities.
            }
        }
    }
    private static void releaseRoomChunks(ServerLevel level, Set<ChunkPos> added) {
        for (ChunkPos chunk : added) { level.setChunkForced(chunk.x, chunk.z, false); }
    }
    /** Only fixture producers run here; the installed manager scans on its ordinary ServerTick END. */
    private static void awaitRuntimeFixture(GameTestHelper helper, ServerPlayer player, ServerLevel level,
                                            OxygenVentBlockEntity vent, AnalyzerReading.State state, boolean supplied,
                                            Runnable verify, Runnable cleanup) {
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) { return; }
            try {
                BlockPos position = vent.getBlockPos();
                helper.assertTrue(level.getBlockEntity(position) == vent && !vent.isRemoved(),
                        "Prepared vent was replaced before observation");
                player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
                OxygenVentBlockEntity.serverTick(level, position, vent.getBlockState(), vent);
                int energy = vent.energyStored(), oxygen = vent.oxygenUnits();
                long inspections = AtmosphereRuntime.metrics(level).orElseThrow().totalInspections();
                var actual = AtmosphereAnalyzerRuntime.read(player);
                helper.assertTrue(vent.energyStored() == energy && vent.oxygenUnits() == oxygen
                                && AtmosphereRuntime.metrics(level).orElseThrow().totalInspections() == inspections,
                        "Readiness query scanned or spent vent resources");
                if (actual.state() != state || actual.supplied() != supplied) {
                    // Fail and release fixtures before the unchanged 40-tick GameTest deadline.
                    helper.assertTrue(helper.getTick() < 39, "Runtime fixture did not publish within 40 ticks: actual="
                            + actual + " required=" + state + "/" + supplied + " metrics=" + AtmosphereRuntime.metrics(level));
                    return;
                }
                AdvancedRocketryCommunity.LOGGER.info("ARCE_ANALYZER_FIX_READY state={} supplied={} tick={} metrics={}",
                        actual.state(), actual.supplied(), helper.getTick(), AtmosphereRuntime.metrics(level));
                verify.run();
                finished[0] = true;
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException failure) {
                if (!finished[0]) {
                    finished[0] = true;
                    try { cleanup.run(); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                }
                throw failure;
            }
        });
    }
    private static void cleanRoomFixture(GameTestHelper helper, ServerPlayer player, AtmosphereAnalyzerService service,
                                         AtmosphereManager manager, ServerLevel level, BlockPos position,
                                         int radius, int height, Set<ChunkPos> forced) {
        service.close(); manager.clear();
        try { remove(helper, player); }
        finally { try { clearRoom(level, position, radius, height); } finally { releaseRoomChunks(level, forced); } }
    }
    private static CelestialCatalogManager catalogs() {
        var catalogs = new CelestialCatalogManager(); catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())); return catalogs;
    }
    private static AtmosphereAnalyzerService service(GameTestHelper helper, CelestialCatalogManager catalogs, AtmosphereManager manager) {
        var server = helper.getLevel().getServer();
        var data = StationRegistrySavedData.get(server);
        return new AtmosphereAnalyzerService(server, catalogs, manager,
                new BodyContextResolver(catalogs, List.of(new StationRegionBodyContextResolver(data::findAt))));
    }
    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
