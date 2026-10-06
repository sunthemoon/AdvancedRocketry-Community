package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarExposure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarSave;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Registered adapters and bounded disposable fixtures; not dedicated restart, real-client or Gate evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolarGeneratorGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 2, 1);
    private SolarGeneratorGameTests() { }
    private static SolarGeneratorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POSITION, ModBlocks.SOLAR_GENERATOR.get());
        return (SolarGeneratorBlockEntity) helper.getBlockEntity(POSITION);
    }
    private static void state(SolarGeneratorBlockEntity generator, int energy) {
        CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ROOT, SolarSave.encode(energy)); generator.load(outer);
    }
    private static void tick(SolarGeneratorBlockEntity generator) {
        SolarGeneratorBlockEntity.serverTick((ServerLevel) generator.getLevel(), generator.getBlockPos(), generator.getBlockState(), generator);
    }
    private static IEnergyStorage port(SolarGeneratorBlockEntity generator) {
        return generator.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredPanelAndGeneratorHaveOnlyTheSelectedDeviceShape(GameTestHelper helper) {
        var generator = place(helper);
        helper.setBlock(POSITION.east(), ModBlocks.SOLAR_PANEL.get());
        helper.assertTrue(generator.getType() == ModBlockEntities.SOLAR_GENERATOR.get()
                && generator.getBlockState().getProperties().isEmpty(), "Generator acquired orientation or wrong BE type");
        helper.assertTrue(ModBlocks.SOLAR_PANEL.get().getClass() == Block.class
                && helper.getBlockEntity(POSITION.east()) == null, "Panel acquired a BE or custom adapter");
        helper.assertTrue(ModBlocks.SOLAR_PANEL.get().asItem() == ModItems.SOLAR_PANEL.get(), "Panel item/block mismatch");
        helper.assertTrue(generator.getBlockState().getDestroySpeed(helper.getLevel(), generator.getBlockPos()) == 5
                && ModBlocks.SOLAR_GENERATOR.get().getExplosionResistance() == 6
                && generator.getBlockState().getPistonPushReaction() == PushReaction.BLOCK
                && generator.getBlockState().getLightEmission() == 0, "Generator properties differ");
        helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(generator.getBlockState())
                && new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(generator.getBlockState()), "Stone-tier tool requirement differs");
        helper.assertTrue(!generator.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                && !generator.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Unexpected resource port");
        helper.assertTrue(!port(generator).canReceive() && port(generator).receiveEnergy(1_000, false) == 0,
                "Solar ENERGY is not output-only");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeCraftingUnlockAndLootAreOnePlainSelfItem(GameTestHelper helper) {
        for (String id : List.of("solar_panel", "solar_generator")) {
            var raw = helper.getLevel().getRecipeManager().byKey(ModIdentity.id(id)).orElse(null);
            helper.assertTrue(raw instanceof ShapedRecipe, "Solar recipe missing");
            ShapedRecipe recipe = (ShapedRecipe) raw;
            TransientCraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
            for (int slot = 0; slot < 9; slot++) {
                var item = id.equals("solar_panel") ? (slot < 3 ? Items.GLASS : slot < 6 ? ModItems.SILICON_WAFER.get() : Items.COPPER_INGOT)
                        : (slot < 3 ? ModItems.SOLAR_PANEL.get() : slot == 4 ? Items.GLASS : Items.COPPER_INGOT);
                grid.setItem(slot, new ItemStack(item));
            }
            List<ItemStack> before = new ArrayList<>();
            for (int slot = 0; slot < 9; slot++) { before.add(grid.getItem(slot).copy()); }
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Selected nine-slot grid refused");
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            var block = id.equals("solar_panel") ? ModBlocks.SOLAR_PANEL.get() : ModBlocks.SOLAR_GENERATOR.get();
            helper.assertTrue(result.is(block.asItem()) && result.getCount() == 1 && !result.hasTag(), "Crafted resource carries FE or wrong identity");
            for (int slot = 0; slot < 9; slot++) { helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)), "Recipe query mutated inventory"); }
            grid.setItem(0, ItemStack.EMPTY);
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Incomplete grid became obtainable");
            var unlock = helper.getLevel().getServer().getAdvancements().getAdvancement(ModIdentity.id("recipes/building_blocks/" + id));
            helper.assertTrue(unlock != null && unlock.getCriteria().size() == 2 && unlock.getRequirements().length == 1,
                    "Recipe unlock is missing or not OR");
            var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.STONE_PICKAXE))
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(POSITION)))
                    .create(LootContextParamSets.BLOCK);
            var loot = helper.getLevel().getServer().getLootData().getLootTable(ModIdentity.id("blocks/" + id)).getRandomItems(params, 17L);
            helper.assertTrue(loot.size() == 1 && loot.get(0).is(block.asItem()) && loot.get(0).getCount() == 1 && !loot.get(0).hasTag(),
                    "Supported ordinary loot became an energy carrier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sixSidesNullAndSimulationsShareOneOutputQuota(GameTestHelper helper) {
        var generator = place(helper); state(generator, 5_000);
        var nullSide = port(generator);
        helper.assertTrue(nullSide.extractEnergy(9_999, true) == 1_000 && generator.energyStored() == 5_000, "Simulation consumed FE/quota");
        int total = 0;
        for (Direction direction : Direction.values()) {
            total += generator.getCapability(ForgeCapabilities.ENERGY, direction).orElseThrow(IllegalStateException::new).extractEnergy(200, false);
        }
        helper.assertTrue(total == 1_000 && generator.energyStored() == 4_000 && nullSide.extractEnergy(1, false) == 0,
                "Null side or a face bypassed the shared quota");
        helper.runAfterDelay(1, () -> {
            int before = generator.energyStored();
            helper.assertTrue(nullSide.extractEnergy(9_999, true) == 1_000 && nullSide.extractEnergy(9_999, true) == 1_000
                    && generator.energyStored() == before, "Later simulation changed FE/quota");
            helper.assertTrue(nullSide.extractEnergy(9_999, false) == 1_000, "Later-tick allowance did not reset");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pushAndForeignReentryCannotSpendTheSameAllowanceTwice(GameTestHelper helper) {
        var generator = place(helper); state(generator, 5_000);
        IEnergyStorage output = port(generator); int[] observed = new int[2];
        helper.assertTrue(output.extractEnergy(700, false) == 700, "Initial pull failed");
        BlockPos neighbor = generator.getBlockPos().east();
        helper.getLevel().setBlock(neighbor, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        var receiver = new BlockEntity(BlockEntityType.CHEST, neighbor, Blocks.CHEST.defaultBlockState()) {
            final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> new IEnergyStorage() {
                @Override public int receiveEnergy(int amount, boolean simulate) {
                    observed[0] += output.extractEnergy(1_000, false); observed[1] += amount; return amount;
                }
                @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
                @Override public int getEnergyStored() { return 0; }
                @Override public int getMaxEnergyStored() { return 10_000; }
                @Override public boolean canExtract() { return false; }
                @Override public boolean canReceive() { return true; }
            });
            @Override public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> requested, Direction side) {
                return requested == ForgeCapabilities.ENERGY ? energy.cast() : super.getCapability(requested, side);
            }
        };
        helper.getLevel().setBlockEntity(receiver);
        try {
            tick(generator);
            helper.assertTrue(observed[0] == 0 && observed[1] == 300 && output.extractEnergy(1, false) == 0, "Reentry or push bypassed quota");
            helper.assertTrue(generator.energyStored() == 4_000 + generator.actualCredit(), "Credit/export accounting diverged");
        } finally { helper.getLevel().setBlock(neighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void disabledRepairOrphanAndClosedOwnerRefuseWithoutCatalogSampling(GameTestHelper helper) {
        var registered = place(helper); var level = helper.getLevel(); var position = registered.getBlockPos();
        SolarExposure handle = new SolarExposure(); CelestialCatalogManager catalog = catalogs();
        AtomicInteger captures = new AtomicInteger(), handleCalls = new AtomicInteger();
        CelestialCatalogManager observed = CelestialCatalogManager.readOnly(() -> { captures.incrementAndGet(); return catalog.snapshot(); });
        handle.start(level.getServer(), observed);
        boolean[] enabled = {false};
        var fixture = injected(position, registered.getBlockState(), () -> enabled[0], () -> { handleCalls.incrementAndGet(); return handle; });
        level.setBlockEntity(fixture); state(fixture, 777);
        try {
            var retained = port(fixture); tick(fixture);
            helper.assertTrue(fixture.reason() == SolarGeneration.Reason.DISABLED && fixture.energyStored() == 777
                    && retained.extractEnergy(1, false) == 0 && handleCalls.get() == 0 && captures.get() == 0, "Disabled path queried or exported");
            enabled[0] = true;
            CompoundTag bad = new CompoundTag(); bad.putInt(SolarSave.ROOT, 42); fixture.load(bad); tick(fixture);
            helper.assertTrue(fixture.repairRequired() && handleCalls.get() == 0 && captures.get() == 0, "Repair path sampled world");
            state(fixture, 777);
            var orphan = injected(position, registered.getBlockState(), () -> true, () -> handle); orphan.setLevel(level);
            helper.assertTrue(!handle.read(level, position, orphan).available() && captures.get() == 0, "Same-position orphan sampled catalog");
            tick(orphan); helper.assertTrue(orphan.energyStored() == 0 && orphan.actualCredit() == 0, "Orphan generated FE");
            handle.close(level.getServer()); handle.start(level.getServer(), new CelestialCatalogManager());
            tick(fixture);
            helper.assertTrue(fixture.reason() == SolarGeneration.Reason.CONTEXT_UNAVAILABLE && fixture.actualCredit() == 0
                    && fixture.energyStored() == 777 && port(fixture).extractEnergy(100, false) == 100,
                    "Live unavailable context disabled retained export or generated FE");
            var successor = catalogs(); AtomicInteger swappingCaptures = new AtomicInteger();
            handle.close(level.getServer()); handle.start(level.getServer(), CelestialCatalogManager.readOnly(
                    () -> swappingCaptures.getAndIncrement() == 0 ? catalog.snapshot() : successor.snapshot()));
            helper.assertTrue(!handle.read(level, position, fixture).available() && swappingCaptures.get() == 2,
                    "Catalog changed during a sample without refusal");
            var old = port(fixture); fixture.invalidateCaps(); fixture.reviveCaps();
            helper.assertTrue(old.extractEnergy(1, false) == 0, "Stale epoch revived");
            handle.close(level.getServer());
            helper.assertTrue(port(fixture).extractEnergy(1, false) == 0 && !fixture.available(), "Closed host exported retained FE");
        } finally { handle.close(level.getServer()); state(fixture, 0); level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "solar_environment", timeoutTicks = 40)
    public static void nativeSurfaceDayRoofNightAndWeatherPublishScalarCredit(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos anchor = helper.absolutePos(POSITION);
        BlockPos position = new BlockPos(anchor.getX(), level.getMaxBuildHeight() - 2, anchor.getZ());
        BlockPos roof = position.above();
        helper.assertTrue(level.isInWorldBounds(position) && level.isInWorldBounds(roof)
                && level.getWorldBorder().isWithinBounds(position) && level.getWorldBorder().isWithinBounds(roof),
                "Surface fixture cells are outside world bounds");
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        helper.assertTrue(chunk != null, "Surface fixture chunk is not already loaded");
        BlockState before = level.getBlockState(position), roofBefore = level.getBlockState(roof);
        helper.assertTrue(!before.hasBlockEntity() && !roofBefore.hasBlockEntity()
                && !chunk.getBlockEntities().containsKey(position) && !chunk.getBlockEntities().containsKey(roof),
                "Surface fixture cells contain BlockEntity data");
        long time = level.getDayTime(); float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        Runnable restore = surfaceRestoration(helper, level, position, before, roof, roofBefore, time, rain, thunder);
        try {
            level.setBlock(position, ModBlocks.SOLAR_GENERATOR.get().defaultBlockState(), Block.UPDATE_ALL);
            var installed = level.getBlockEntity(position);
            helper.assertTrue(installed instanceof SolarGeneratorBlockEntity, "Registered surface generator was not installed");
            var generator = (SolarGeneratorBlockEntity) installed;
            level.setDayTime(6_000); level.setRainLevel(0); level.setThunderLevel(0); level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            await(helper, "DAY_SKY", roof, () -> level.isDay() && level.canSeeSky(roof), () -> {
                state(generator, 0); tick(generator);
                helper.assertTrue(generator.reason() == SolarGeneration.Reason.GENERATING && generator.actualCredit() > 0, "Native daytime did not generate");
                int clear = generator.actualCredit();
                level.setRainLevel(1); level.setThunderLevel(1); state(generator, 0); tick(generator);
                helper.assertTrue(generator.environment().weatherPermille() == 250 && generator.actualCredit() <= clear, "Weather attenuation/display differs");
                level.setRainLevel(0); level.setThunderLevel(0); level.setBlock(roof, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                await(helper, "ROOF", roof, () -> !level.canSeeSky(roof), () -> {
                    state(generator, 777); tick(generator);
                    helper.assertTrue(generator.reason() == SolarGeneration.Reason.SKY_BLOCKED && generator.actualCredit() == 0
                            && port(generator).extractEnergy(100, false) == 100, "Roof blocked stored export or allowed credit");
                    level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); level.setDayTime(18_000);
                    await(helper, "NIGHT_SKY", roof, () -> !level.isDay() && level.canSeeSky(roof), () -> {
                        state(generator, 777); tick(generator);
                        helper.assertTrue(generator.reason() == SolarGeneration.Reason.NOT_DAYLIGHT && generator.actualCredit() == 0,
                                "Native night was replaced by a custom calendar or stale credit");
                        var view = SolarGeneratorMenu.project(SolarGeneratorMenu.liveData(generator));
                        helper.assertTrue(view.day() == 0 && view.context() == 1 && view.credit() == 0, "Live scalar menu differs");
                        restore.run();
                        helper.succeed();
                        return true;
                    }, restore);
                    return false;
                }, restore);
                return false;
            }, restore);
        } catch (RuntimeException | Error failed) { restorePreservingFailure(restore, failed); throw failed; }
    }

    private static Runnable surfaceRestoration(GameTestHelper helper, ServerLevel level, BlockPos position,
                                               BlockState before, BlockPos roof, BlockState roofBefore,
                                               long time, float rain, float thunder) {
        boolean[] attempted = {false};
        return () -> {
            if (attempted[0]) { return; }
            attempted[0] = true;
            Runnable[] actions = {
                    () -> level.setBlock(position, before, Block.UPDATE_ALL),
                    () -> level.setBlock(roof, roofBefore, Block.UPDATE_ALL),
                    () -> level.setDayTime(time), () -> level.setRainLevel(rain), () -> level.setThunderLevel(thunder),
                    () -> helper.assertTrue(level.getBlockState(position).equals(before) && level.getBlockState(roof).equals(roofBefore)
                            && level.getDayTime() == time && Float.floatToRawIntBits(level.getRainLevel(1)) == Float.floatToRawIntBits(rain)
                            && Float.floatToRawIntBits(level.getThunderLevel(1)) == Float.floatToRawIntBits(thunder),
                            "Surface fixture state was not completely restored")
            };
            Throwable failed = null;
            for (Runnable action : actions) {
                try { action.run(); }
                catch (RuntimeException | Error error) {
                    if (failed == null) { failed = error; }
                    else if (failed != error) { failed.addSuppressed(error); }
                }
            }
            if (failed instanceof RuntimeException error) { throw error; }
            if (failed instanceof Error error) { throw error; }
        };
    }

    private static void restorePreservingFailure(Runnable restore, Throwable original) {
        try { restore.run(); }
        catch (RuntimeException | Error cleanup) {
            if (original == null) { throw cleanup; }
            if (original != cleanup) { original.addSuppressed(cleanup); }
        }
    }

    @GameTest(template = "empty", batch = "solar_space", timeoutTicks = 40)
    public static void stockNoSkylightSpaceUsesOrdinaryColumnUpdatesAndCommittedOrbit(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null && !space.dimensionType().hasSkyLight(), "Stock Space fixture unavailable");
        var data = StationRegistrySavedData.get(server); UUID id = UUID.randomUUID();
        var reservation = data.reserve(id, UUID.randomUUID(), "Solar", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
        BlockPos position = new BlockPos(reservation.landingPad().x(), space.getMaxBuildHeight() - 2, reservation.landingPad().z());
        ChunkPos chunk = new ChunkPos(position); boolean ownedForce = !space.getForcedChunks().contains(chunk.toLong());
        SolarExposure handle = new SolarExposure(); CelestialCatalogManager catalog = catalogs();
        boolean[] committed = {false}; BlockState[] before = new BlockState[2];
        Runnable restore = () -> {
            try { if (before[0] != null) { space.setBlock(position, before[0], Block.UPDATE_ALL); space.setBlock(position.above(), before[1], Block.UPDATE_ALL); } }
            finally { try { handle.close(server); if (committed[0]) { data.delete(id); } else { data.release(id); } }
                finally { if (ownedForce) { space.setChunkForced(chunk.x, chunk.z, false); } } }
        };
        try {
            if (ownedForce) { space.setChunkForced(chunk.x, chunk.z, true); }
            space.getChunkAt(position); // Explicit disposable fixture setup, never inside an exposure query.
            helper.assertTrue(space.getBlockEntity(position) == null && space.getBlockEntity(position.above()) == null, "Space cells are not fixture-owned");
            before[0] = space.getBlockState(position); before[1] = space.getBlockState(position.above());
            space.setBlock(position, ModBlocks.SOLAR_GENERATOR.get().defaultBlockState(), Block.UPDATE_ALL);
            var fixture = injected(position, ModBlocks.SOLAR_GENERATOR.get().defaultBlockState(), () -> true, () -> handle);
            space.setBlockEntity(fixture); handle.start(server, catalog);
            helper.assertTrue(!handle.read(space, position, fixture).available(), "Reservation became operational authority");
            data.commit(id); committed[0] = true;
            Block[] roofs = {Blocks.AIR, Blocks.STONE, Blocks.GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.OAK_LEAVES, Blocks.TINTED_GLASS, Blocks.WATER, Blocks.GLASS_PANE};
            boolean[] open = {true, false, true, true, false, false, false, true};
            inspectRoofs(helper, space, fixture, roofs, open, 0, () -> {
                data.foldWarpCredits(Map.of(id, 1));
                var observed = data.find(id).orElseThrow();
                helper.assertTrue(data.checkedRelocation(server, observed, CelestialIds.MOON_ID, 1) == StationRegistrySavedData.CheckedUpdate.COMMITTED,
                        "Existing fixture relocation did not commit");
                state(fixture, 0); tick(fixture);
                helper.assertTrue(fixture.environment().context() == 2 && fixture.environment().day() == 2,
                        "Next sample did not observe committed orbit or used Space day");
                var withoutMoon = CelestialDefaults.definitions().stream().filter(body -> !body.id().equals(CelestialIds.MOON_ID)).toList();
                helper.assertTrue(catalog.applyCandidate(CelestialCatalog.create(withoutMoon)), "Controlled catalog reload failed");
                state(fixture, 0); tick(fixture);
                helper.assertTrue(fixture.environment().context() == 3 && fixture.actualCredit() == 2,
                        "Missing orbit did not use existing Space fallback");
                helper.succeed();
            }, restore);
        } catch (RuntimeException | Error failed) { restore.run(); throw failed; }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void supportedRepeatLoadAndFutureRawRootsKeepTheirExactMeaning(GameTestHelper helper) {
        var generator = place(helper); state(generator, 777);
        var first = generator.saveWithoutMetadata(); generator.load(first); var second = generator.saveWithoutMetadata(); generator.load(second);
        helper.assertTrue(generator.energyStored() == 777 && generator.actualCredit() == 0
                && first.get(SolarSave.ROOT).equals(second.get(SolarSave.ROOT)), "Repeated load changed FE or kept stale credit");
        CompoundTag future = SolarSave.encode(777); future.putInt("schema", 2); future.putString("unknown", "preserve");
        try {
            for (var raw : List.of(future, IntTag.valueOf(42))) {
                CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ROOT, raw); generator.load(outer); tick(generator);
                helper.assertTrue(generator.repairRequired() && generator.saveWithoutMetadata().get(SolarSave.ROOT).equals(raw)
                        && !generator.getCapability(ForgeCapabilities.ENERGY).isPresent(), "Refused root lost fidelity or exposed energy");
                helper.assertTrue(!ModBlocks.SOLAR_GENERATOR.get().onDestroyedByPlayer(generator.getBlockState(), helper.getLevel(),
                        generator.getBlockPos(), helper.makeMockPlayer(), true, generator.getBlockState().getFluidState()), "Ordinary break removed preserved root");
            }
        } finally { state(generator, 0); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nonAdmissibleReferenceRefusesTheWholeOutgoingChunk(GameTestHelper helper) {
        var generator = place(helper); CompoundTag huge = new CompoundTag(); huge.putByteArray("payload", new byte[4_096]);
        CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ROOT, huge); generator.load(outer);
        var chunk = helper.getLevel().getChunkAt(generator.getBlockPos());
        try {
            helper.assertTrue(generator.repairRequired() && generator.saveWithoutMetadata().get(SolarSave.ROOT) == huge,
                    "Non-admissible root copied, normalized or omitted");
            var outgoing = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(helper.getLevel(), chunk);
            chunk.setUnsaved(false); boolean refused = false;
            try { MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing)); }
            catch (IllegalStateException expected) { refused = expected.getMessage().contains("non-admissible solar input"); }
            helper.assertTrue(refused && chunk.isUnsaved(), "Outgoing chunk was not refused with retry retained");
        } finally { state(generator, 0); GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos()); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void connectedMenuRequiresCurrentLoadedOwnerAndHasNoInventoryActions(GameTestHelper helper) {
        var generator = place(helper); var level = helper.getLevel(); var pos = generator.getBlockPos();
        var player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "solarMenu", level, pos, new ArrayList<>());
        try {
            var menu = new SolarGeneratorMenu(ModMenuTypes.SOLAR_GENERATOR.get(), 17, generator);
            helper.assertTrue(menu.stillValid(player) && menu.slots.isEmpty(), "Connected scalar menu rejected owner or acquired slots");
            ItemStack held = new ItemStack(Items.COPPER_INGOT, 7); player.setItemInHand(InteractionHand.MAIN_HAND, held);
            menu.clicked(-999, 0, net.minecraft.world.inventory.ClickType.THROW, player);
            helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && player.getMainHandItem().getCount() == 7,
                    "Scalar menu action mutated inventory");
            var result = ModBlocks.SOLAR_GENERATOR.get().use(generator.getBlockState(), level, pos, player,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            helper.assertTrue(result == InteractionResult.CONSUME && player.containerMenu instanceof SolarGeneratorMenu,
                    "Actual connected block interaction did not open the registered menu");
            player.teleportTo(level, pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            helper.assertTrue(!menu.stillValid(player), "Distance limit was not repeated");
            helper.assertTrue(!menu.stillValid(helper.makeMockPlayer()), "Unconnected mock player was admitted");
            generator.setRemoved(); helper.assertTrue(!menu.stillValid(player), "Removed owner remained valid");
            generator.clearRemoved();
        } finally { player.closeContainer(); level.getServer().getPlayerList().remove(player); }
        helper.succeed();
    }

    private static SolarGeneratorBlockEntity injected(BlockPos position, BlockState state, java.util.function.BooleanSupplier enabled,
                                                       java.util.function.Supplier<SolarExposure> exposure) {
        return new SolarGeneratorBlockEntity(ModBlockEntities.SOLAR_GENERATOR.get(), position, state,
                ModMenuTypes.SOLAR_GENERATOR::get, enabled, () -> 1, exposure);
    }
    private static CelestialCatalogManager catalogs() {
        var catalogs = new CelestialCatalogManager(); catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())); return catalogs;
    }
    private static void await(GameTestHelper helper, String stage, BlockPos roof, java.util.function.BooleanSupplier ready,
                              java.util.function.BooleanSupplier verify, Runnable restore) {
        helper.runAfterDelay(1, () -> {
            boolean pending = false;
            Throwable failed = null;
            try {
                boolean published = ready.getAsBoolean();
                if (!published && helper.getTick() < 39) { await(helper, stage, roof, ready, verify, restore); pending = true; return; }
                try {
                    helper.assertTrue(published, "Ordinary producer did not publish within 40 ticks");
                } catch (RuntimeException | Error assertion) {
                    observeSurfaceFailure(helper, stage, roof, published);
                    throw assertion;
                }
                pending = !verify.getAsBoolean();
            } catch (RuntimeException | Error assertion) { failed = assertion; throw assertion; }
            finally { if (!pending) { restorePreservingFailure(restore, failed); } }
        });
    }
    private static void observeSurfaceFailure(GameTestHelper helper, String stage, BlockPos roof, boolean published) {
        try {
            var level = helper.getLevel();
            int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, roof);
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_SOLAR_AWAIT stage={} tick={} published={} game_time={} day_time={} sky_darken={} native_day={}"
                            + " roof_sky={} max_light={} rain_bits={} thunder_bits={} x={} y={} z={} has_sky={} fixed_time={}",
                    stage, helper.getTick(), published, level.getGameTime(), level.getDayTime(), level.getSkyDarken(), level.isDay(),
                    sky, level.getMaxLightLevel(), Float.floatToRawIntBits(level.getRainLevel(1)),
                    Float.floatToRawIntBits(level.getThunderLevel(1)), roof.getX(), roof.getY(), roof.getZ(),
                    level.dimensionType().hasSkyLight(), level.dimensionType().hasFixedTime());
        } catch (RuntimeException | Error ignored) {
            // Observation must not replace the original assertion or prevent its existing restore.
        }
    }
    private static void inspectRoofs(GameTestHelper helper, ServerLevel space, SolarGeneratorBlockEntity fixture,
                                     Block[] roofs, boolean[] open, int index, Runnable verify, Runnable restore) {
        if (index == roofs.length) { space.setBlock(fixture.getBlockPos().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); verify.run(); return; }
        space.setBlock(fixture.getBlockPos().above(), roofs[index].defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            boolean pending = false;
            try {
                helper.assertTrue(helper.getTick() < 39, "Roof controls exceeded 40 ticks");
                state(fixture, 0); tick(fixture);
                helper.assertTrue(fixture.environment().available() && fixture.environment().sky() == open[index]
                        && fixture.environment().day() == 2 && fixture.environment().weatherPermille() == 1_000
                        && fixture.actualCredit() == (open[index] ? 2 : 0), "Stock no-skylight column case differs: " + index);
                inspectRoofs(helper, space, fixture, roofs, open, index + 1, verify, restore); pending = index + 1 < roofs.length;
            } finally { if (!pending) { restore.run(); } }
        });
    }
    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
