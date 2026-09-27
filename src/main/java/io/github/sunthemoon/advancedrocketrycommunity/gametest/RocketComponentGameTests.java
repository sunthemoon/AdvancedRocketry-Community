package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketForgeMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanObservation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketComponentGameTests {
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);

    private RocketComponentGameTests() { }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void combinedComponentFeedsStatsAnchorsCapacityAndFlightFuel(GameTestHelper helper) {
        helper.setBlock(ORIGIN, Blocks.IRON_BLOCK);
        var components = catalog(Map.of(Blocks.IRON_BLOCK,
                new RocketComponentDefinition(47, 2400, 1200, true, true, true)));
        var snapshot = successful(helper, ORIGIN, components).snapshot().orElseThrow();
        helper.assertTrue(snapshot.stats().equals(new RocketStats(1, 47, 2400, 1200, 1, 1, 1, 0)),
                "Combined block did not contribute each declared value once");
        helper.assertTrue(snapshot.passengerAnchors().equals(List.of(new RocketPosition(0, 0, 0))),
                "Combined seat did not add exactly one anchor");
        var fuel = RocketFuelState.empty(snapshot.stats().fuelCapacity());
        var empty = RocketFlightPlanner.plan(snapshot.stats(), fuel, RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, UUID.randomUUID(), 0);
        helper.assertTrue(empty.code() == RocketFlightPlanCode.INSUFFICIENT_FUEL && empty.requiredFuel() == 291,
                "Flight quote did not use the captured external mass and capacity");
        var filled = RocketFlightPlanner.plan(snapshot.stats(), fuel.fill(500).state(), RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, UUID.randomUUID(), 0);
        helper.assertTrue(filled.success() && filled.requiredFuel() == 291, "Valid custom component flight plan rejected");
        var encoded = RocketSnapshotNbtCodec.encode(snapshot);
        helper.assertTrue(encoded.equals(RocketSnapshotNbtCodec.encode(
                RocketSnapshotNbtCodec.decode(encoded).snapshot().orElseThrow())), "Component snapshot changed on codec round-trip");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void mixedLegacyComponentsKeepTheirOriginalMetrics(GameTestHelper helper) {
        helper.setBlock(ORIGIN, Blocks.IRON_BLOCK);
        helper.setBlock(ORIGIN.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(ORIGIN.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(ORIGIN.east(), ModBlocks.ROCKET_FUEL_TANK.get());
        var components = catalog(Map.of(Blocks.IRON_BLOCK,
                new RocketComponentDefinition(120, 2400, 0, true, false, false)));
        var snapshot = successful(helper, ORIGIN, components).snapshot().orElseThrow();
        helper.assertTrue(snapshot.stats().equals(new RocketStats(4, 220, 2400, 1000, 1, 1, 1, 0)),
                "Mixed legacy and explicit component values changed");
        helper.assertTrue(RocketForgeMetrics.resolve(ModBlocks.ROCKET_MOTOR.get().defaultBlockState(), components)
                        .equals(new RocketBlockMetrics(100, 1000, 0, true, false, false)), "Legacy motor fallback changed");
        helper.assertTrue(RocketForgeMetrics.resolve(Blocks.GLASS.defaultBlockState(), components)
                        .equals(RocketBlockMetrics.structural(10)), "Unclaimed structural mass changed");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void explicitDefinitionPrecedesTheLegacyRoleConflictCheck(GameTestHelper helper) {
        // The isolated compatibility fixture puts diamond blocks in both role tags.
        boolean conflict = false;
        try { RocketForgeMetrics.resolve(Blocks.DIAMOND_BLOCK.defaultBlockState()); }
        catch (IllegalArgumentException expected) { conflict = true; }
        helper.assertTrue(conflict, "Fixture did not preserve legacy conflicting-tag rejection");
        var components = catalog(Map.of(Blocks.DIAMOND_BLOCK,
                new RocketComponentDefinition(120, 2400, 0, true, false, false)));
        helper.assertTrue(RocketForgeMetrics.resolve(Blocks.DIAMOND_BLOCK.defaultBlockState(), components)
                        .equals(new RocketBlockMetrics(120, 2400, 0, true, false, false)),
                "Explicit definition did not take precedence over conflicting fallback tags");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void componentDoesNotGrantMovementOrBlockEntityPermission(GameTestHelper helper) {
        var definition = new RocketComponentDefinition(47, 2400, 1200, true, true, true);
        var components = catalog(Map.of(Blocks.DIRT, definition, Blocks.COMMAND_BLOCK, definition, Blocks.CHEST, definition));
        helper.setBlock(ORIGIN, Blocks.DIRT);
        helper.setBlock(ORIGIN.east(), Blocks.COMMAND_BLOCK);
        helper.setBlock(ORIGIN.west(), Blocks.CHEST);
        var view = new ServerLevelRocketScanWorld(helper.getLevel(), new RocketBlockEntityAdapters(List.of()), components);
        helper.assertTrue(view.observe(position(helper.absolutePos(ORIGIN))).kind() == RocketScanObservation.Kind.BOUNDARY,
                "Component registration granted movement to untagged dirt");
        helper.assertTrue(view.observe(position(helper.absolutePos(ORIGIN.east()))).kind() == RocketScanObservation.Kind.FORBIDDEN,
                "Component registration bypassed forbidden command blocks");
        helper.assertTrue(view.observe(position(helper.absolutePos(ORIGIN.west()))).kind() == RocketScanObservation.Kind.UNSUPPORTED_BLOCK_ENTITY,
                "Numeric component registration replaced the missing chest adapter");
        BlockPos unloaded = new BlockPos(30_000_000, 100, 30_000_000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(unloaded), "Fixture coordinate is unexpectedly loaded");
        helper.assertTrue(view.observe(position(unloaded)).kind() == RocketScanObservation.Kind.UNLOADED
                        && !helper.getLevel().hasChunkAt(unloaded), "Component scan loaded a remote chunk");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void oversizedAggregateCapacityRejectsBeforeProductionExtraction(GameTestHelper helper) {
        helper.setBlock(ORIGIN.below(), ModBlocks.ROCKET_ASSEMBLER.get());
        helper.setBlock(ORIGIN, Blocks.IRON_BLOCK);
        helper.setBlock(ORIGIN.east(), Blocks.COPPER_BLOCK);
        var components = catalog(Map.of(
                Blocks.IRON_BLOCK, new RocketComponentDefinition(47, 2400, 2_048_000, true, true, true),
                Blocks.COPPER_BLOCK, new RocketComponentDefinition(10, 0, 1, false, false, false)));
        var manager = new RocketManager(RocketBlockEntityAdapters.defaults(), null, null, components);
        try {
            helper.assertTrue(manager.requestAdminAssembler(helper.getLevel(), helper.absolutePos(ORIGIN.below()),
                    UUID.randomUUID(), true) == RocketValidationCode.SCAN_IN_PROGRESS, "Scan was not queued");
            manager.tick(helper.getLevel().getServer());
            var assembler = (RocketAssemblerBlockEntity) helper.getBlockEntity(ORIGIN.below());
            helper.assertTrue(assembler.report().code() == RocketValidationCode.FUEL_CAPACITY_EXCEEDED,
                    "Aggregate capacity was not rejected in the production assembler");
            helper.assertTrue(helper.getBlockState(ORIGIN).is(Blocks.IRON_BLOCK)
                            && helper.getBlockState(ORIGIN.east()).is(Blocks.COPPER_BLOCK), "Rejected structure lost blocks");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(RocketEntity.class,
                    new AABB(helper.absolutePos(ORIGIN)).inflate(3)).isEmpty(), "Rejected structure spawned an entity");
            helper.assertTrue(RocketTransactionSavedData.get(helper.getLevel().getServer()).entries().stream()
                            .noneMatch(entry -> entry.snapshot().sourceOrigin().equals(position(helper.absolutePos(ORIGIN)))),
                    "Rejected capacity created a transaction journal entry for this structure");
        } finally {
            manager.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void savedEntityKeepsCapturedValuesWhenDefinitionIsOmittedOrChanged(GameTestHelper helper) {
        helper.setBlock(ORIGIN, Blocks.IRON_BLOCK);
        var original = catalog(Map.of(Blocks.IRON_BLOCK,
                new RocketComponentDefinition(47, 2400, 1200, true, true, true)));
        var snapshot = successful(helper, ORIGIN, original).snapshot().orElseThrow();
        var entity = new RocketEntity(ModEntities.ROCKET.get(), helper.getLevel());
        entity.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        CompoundTag before = new CompoundTag();
        helper.assertTrue(entity.save(before), "Initialized entity could not be saved");
        var missing = scan(helper, ORIGIN, RocketComponentCatalog.empty());
        helper.assertTrue(missing.status() == RocketScanResult.Status.FAILED, "Missing definition granted new assembly roles");
        var changed = catalog(Map.of(Blocks.IRON_BLOCK,
                new RocketComponentDefinition(98, 2800, 1800, true, true, true)));
        helper.assertTrue(successful(helper, ORIGIN, changed).snapshot().orElseThrow().stats().mass() == 98,
                "New scan ignored current component values");
        var restored = new RocketEntity(ModEntities.ROCKET.get(), helper.getLevel());
        restored.load(before);
        helper.assertTrue(restored.operational() && restored.snapshot().orElseThrow().contentHash().equals(snapshot.contentHash())
                        && restored.flightData().orElseThrow().fuel().capacity() == 1200,
                "Saved entity was recomputed from current definitions");
        CompoundTag after = new CompoundTag();
        helper.assertTrue(restored.save(after) && before.getCompound("RocketEntityData").equals(after.getCompound("RocketEntityData")),
                "Saved component snapshot or flight data changed during entity load");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void seventeenComponentSeatsKeepAnchorsButOnlySixteenPassengerPlaces(GameTestHelper helper) {
        var components = catalog(Map.of(Blocks.IRON_BLOCK,
                new RocketComponentDefinition(1, 100, 0, true, true, true)));
        int placed = 0;
        for (int x = 0; x < 5 && placed < 17; x++) {
            for (int z = 0; z < 4 && placed < 17; z++) {
                helper.setBlock(new BlockPos(1 + x, 2, 1 + z), Blocks.IRON_BLOCK);
                placed++;
            }
        }
        var snapshot = successful(helper, new BlockPos(1, 2, 1), components).snapshot().orElseThrow();
        var rocket = new RocketEntity(ModEntities.ROCKET.get(), helper.getLevel());
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        helper.assertTrue(snapshot.stats().seatCount() == 17 && snapshot.passengerAnchors().size() == 17,
                "Snapshot truncated declared seat contributions");
        helper.assertTrue(rocket.flightData().orElseThrow().passengers().seatCapacity() == 16,
                "External seats increased the existing passenger limit");
        helper.succeed();
    }

    private static RocketComponentCatalog catalog(Map<Block, RocketComponentDefinition> entries) {
        try (var registry = new RocketComponentRegistry(id -> ForgeRegistries.BLOCKS.containsKey(id)
                ? ForgeRegistries.BLOCKS.getValue(id) : null)) {
            var registrar = registry.forOwner("component_test");
            int index = 0;
            for (var entry : entries.entrySet()) {
                registrar.register(ResourceLocation.tryParse("component_test:value" + index++),
                        Set.of(ForgeRegistries.BLOCKS.getKey(entry.getKey())), entry.getValue());
            }
            return registry.freeze();
        }
    }

    private static RocketScanResult successful(GameTestHelper helper, BlockPos origin, RocketComponentCatalog catalog) {
        var result = scan(helper, origin, catalog);
        helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS, "Component scan failed: " + result.issues());
        return result;
    }

    private static RocketScanResult scan(GameTestHelper helper, BlockPos origin, RocketComponentCatalog catalog) {
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(helper.getLevel(),
                RocketBlockEntityAdapters.defaults(), catalog), helper.getLevel().dimension().location(),
                position(helper.absolutePos(origin)), UUID.randomUUID(), helper.getLevel().getGameTime());
        var result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int step = 1; result.status() == RocketScanResult.Status.RUNNING && step < 64; step++) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        return result;
    }

    private static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }
}
