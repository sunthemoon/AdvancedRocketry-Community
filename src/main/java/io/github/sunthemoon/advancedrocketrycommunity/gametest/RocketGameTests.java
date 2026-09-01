package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketGameTests {
    private static final String TEMPLATE = "rocket_test";

    private RocketGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void blockStateAdapterRoundTripsAllProperties(GameTestHelper helper) {
        BlockState source = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.WATERLOGGED, true);
        RocketBlockState captured = RocketBlockStateAdapter.capture(source);

        helper.assertTrue("west".equals(captured.properties().get("facing")), "Facing was not captured");
        helper.assertTrue("top".equals(captured.properties().get("half")), "Half was not captured");
        helper.assertTrue("true".equals(captured.properties().get("waterlogged")), "Waterlogged was not captured");
        helper.assertTrue(
                RocketBlockStateAdapter.restore(captured).orElseThrow().equals(source),
                "Captured BlockState did not round-trip exactly"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void vanillaContainerAdapterConservesItemsAndRejectsMalformedSlots(GameTestHelper helper) {
        BlockPos sourcePosition = BlockPos.ZERO;
        BlockPos targetPosition = new BlockPos(2, 0, 0);
        helper.setBlock(sourcePosition, Blocks.CHEST);
        helper.setBlock(targetPosition, Blocks.CHEST);
        ChestBlockEntity source = (ChestBlockEntity) helper.getBlockEntity(sourcePosition);
        ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(targetPosition);
        source.setItem(0, new ItemStack(Items.DIAMOND, 17));
        source.setItem(26, new ItemStack(Items.IRON_INGOT, 64));
        VanillaContainerRocketAdapter adapter = new VanillaContainerRocketAdapter();

        RocketBlockEntityPayload payload = adapter.capture(source);
        helper.assertTrue(adapter.restore(target, payload), "Approved chest payload was rejected");
        helper.assertTrue(
                target.getItem(0).is(Items.DIAMOND) && target.getItem(0).getCount() == 17,
                "Diamond stack changed during adapter round-trip"
        );
        helper.assertTrue(
                target.getItem(26).is(Items.IRON_INGOT) && target.getItem(26).getCount() == 64,
                "Iron stack changed during adapter round-trip"
        );
        helper.assertTrue(!payload.data().contains("id"), "BlockEntity identity leaked into payload");

        CompoundTag malformed = new CompoundTag();
        ListTag items = new ListTag();
        items.add(stackTag(0, new ItemStack(Items.DIAMOND)));
        items.add(stackTag(0, new ItemStack(Items.IRON_INGOT)));
        malformed.put("Items", items);
        helper.assertTrue(
                !adapter.restore(target, new RocketBlockEntityPayload(adapter.id(), malformed)),
                "Duplicate container slots were accepted"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void legalRocketRoundTripConservesChestContents(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        placeLegalRocket(helper, origin, true);
        ChestBlockEntity sourceChest = (ChestBlockEntity) helper.getBlockEntity(origin.east());
        sourceChest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        sourceChest.setItem(26, new ItemStack(Items.IRON_INGOT, 64));
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        UUID owner = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        ServerLevelRocketTransactionWorld world = new ServerLevelRocketTransactionWorld(
                level,
                RocketBlockEntityAdapters.defaults(),
                owner
        );
        RocketRegionLockManager locks = new RocketRegionLockManager();
        RocketOperationLedger ledger = new RocketOperationLedger();

        RocketTransactionResult assembled = new RocketAssemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), snapshot);
        helper.assertTrue(assembled.success(), "Legal rocket assembly failed: " + assembled.code());
        UUID rocketId = assembled.rocketEntityId().orElseThrow();
        helper.assertTrue(level.getEntity(rocketId) instanceof RocketEntity, "Assembly did not create RocketEntity");
        helper.assertTrue(helper.getBlockState(origin).isAir(), "Assembly left source motor behind");
        helper.assertTrue(helper.getBlockState(origin.east()).isAir(), "Assembly left source chest behind");

        RocketTransactionResult disassembled = new RocketDisassemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), rocketId, snapshot);
        helper.assertTrue(disassembled.success(), "Legal rocket disassembly failed: " + disassembled.code());
        ChestBlockEntity restoredChest = (ChestBlockEntity) helper.getBlockEntity(origin.east());
        helper.assertTrue(
                restoredChest.getItem(0).is(Items.DIAMOND) && restoredChest.getItem(0).getCount() == 17,
                "Disassembly changed diamond contents"
        );
        helper.assertTrue(
                restoredChest.getItem(26).is(Items.IRON_INGOT)
                        && restoredChest.getItem(26).getCount() == 64,
                "Disassembly changed iron contents"
        );
        helper.assertTrue(level.getEntity(rocketId) == null, "Disassembly left duplicate RocketEntity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 30)
    public static void illegalStructuresReturnStableDiagnostics(GameTestHelper helper) {
        BlockPos noEngine = new BlockPos(2, 2, 2);
        helper.setBlock(noEngine, ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(noEngine.above(), ModBlocks.GUIDANCE_COMPUTER.get());
        RocketScanResult missingEngine = scan(helper, noEngine);
        helper.assertTrue(missingEngine.status() == RocketScanResult.Status.FAILED, "Engine-less rocket passed");
        helper.assertTrue(
                missingEngine.issues().get(0).code() == RocketValidationCode.MISSING_ENGINE,
                "Engine-less rocket returned " + missingEngine.issues().get(0).code()
        );

        BlockPos forbidden = new BlockPos(10, 2, 2);
        placeLegalRocket(helper, forbidden, false);
        helper.setBlock(forbidden.east(), Blocks.COMMAND_BLOCK);
        RocketScanResult forbiddenResult = scan(helper, forbidden);
        helper.assertTrue(forbiddenResult.status() == RocketScanResult.Status.FAILED, "Forbidden rocket passed");
        helper.assertTrue(
                forbiddenResult.issues().get(0).code() == RocketValidationCode.FORBIDDEN_BLOCK,
                "Forbidden rocket returned " + forbiddenResult.issues().get(0).code()
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void occupiedDisassemblyDoesNotOverwriteOrRemoveRocket(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        placeLegalRocket(helper, origin, false);
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        UUID owner = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        ServerLevelRocketTransactionWorld world = new ServerLevelRocketTransactionWorld(
                level,
                RocketBlockEntityAdapters.defaults(),
                owner
        );
        RocketRegionLockManager locks = new RocketRegionLockManager();
        RocketOperationLedger ledger = new RocketOperationLedger();
        RocketTransactionResult assembled = new RocketAssemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), snapshot);
        UUID rocketId = assembled.rocketEntityId().orElseThrow();

        helper.setBlock(origin, Blocks.STONE);
        RocketTransactionResult blocked = new RocketDisassemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), rocketId, snapshot);
        helper.assertTrue(blocked.code() == RocketValidationCode.TARGET_OCCUPIED,
                "Occupied target returned " + blocked.code());
        helper.assertTrue(helper.getBlockState(origin).is(Blocks.STONE), "Occupied block was overwritten");
        helper.assertTrue(level.getEntity(rocketId) instanceof RocketEntity, "Blocked disassembly removed rocket");

        helper.setBlock(origin, Blocks.AIR);
        RocketTransactionResult cleanup = new RocketDisassemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), rocketId, snapshot);
        helper.assertTrue(cleanup.success(), "Cleanup disassembly failed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 30)
    public static void duplicateAssemblyRequestCreatesOnlyOneRocket(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        placeLegalRocket(helper, origin, false);
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        UUID owner = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        ServerLevelRocketTransactionWorld world = new ServerLevelRocketTransactionWorld(
                level,
                RocketBlockEntityAdapters.defaults(),
                owner
        );
        RocketRegionLockManager locks = new RocketRegionLockManager();
        RocketOperationLedger ledger = new RocketOperationLedger();
        RocketAssemblyTransaction transaction = new RocketAssemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        );
        UUID requestId = UUID.randomUUID();
        RocketTransactionResult first = transaction.execute(requestId, snapshot);
        RocketTransactionResult replay = transaction.execute(requestId, snapshot);

        helper.assertTrue(first.success(), "First assembly request failed");
        helper.assertTrue(replay.code() == RocketValidationCode.REQUEST_REPLAYED, "Replay was not rejected");
        BlockPos absolute = helper.absolutePos(origin);
        helper.assertTrue(
                level.getEntitiesOfClass(
                        RocketEntity.class,
                        new AABB(absolute).inflate(2.0D)
                ).size() == 1,
                "Replay created duplicate RocketEntity"
        );
        UUID rocketId = first.rocketEntityId().orElseThrow();
        RocketTransactionResult cleanup = new RocketDisassemblyTransaction(
                world,
                locks,
                ledger,
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), rocketId, snapshot);
        helper.assertTrue(cleanup.success(), "Replay test cleanup failed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 30)
    public static void rocketEntitySnapshotPersistsAndFutureSchemaFailsClosed(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        placeLegalRocket(helper, origin, false);
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        UUID transactionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RocketEntity source = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(source != null, "Rocket entity type did not create an entity");
        source.initialize(snapshot, transactionId, ownerId);
        CompoundTag saved = source.saveWithoutId(new CompoundTag());

        RocketEntity restored = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Rocket entity type did not create restore target");
        restored.load(saved.copy());
        helper.assertTrue(restored.operational(), "Current entity schema did not reload operationally");
        helper.assertTrue(
                restored.snapshot().orElseThrow().contentHash().equals(snapshot.contentHash()),
                "Entity reload changed snapshot hash"
        );
        helper.assertTrue(restored.ownerId().orElseThrow().equals(ownerId), "Entity reload changed owner");
        helper.assertTrue(
                restored.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED,
                "Entity reload changed flight state"
        );
        helper.assertTrue(
                restored.flightData().orElseThrow().logicalRocketId().equals(transactionId),
                "Entity reload changed logical rocket identity"
        );

        CompoundTag legacySave = saved.copy();
        CompoundTag legacyData = legacySave.getCompound("RocketEntityData");
        legacyData.putInt("schema_version", 1);
        legacyData.remove("flight_data");
        RocketEntity migrated = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(migrated != null, "Rocket entity type did not create migration target");
        migrated.load(legacySave);
        helper.assertTrue(migrated.operational(), "Schema-1 entity did not migrate operationally");
        helper.assertTrue(
                migrated.flightData().orElseThrow().logicalRocketId().equals(transactionId),
                "Schema-1 migration changed logical rocket identity"
        );
        helper.assertTrue(
                migrated.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData")
                        .getInt("schema_version") == 2,
                "Schema-1 entity was not upgraded on save"
        );

        CompoundTag futureSave = saved.copy();
        CompoundTag futureData = futureSave.getCompound("RocketEntityData");
        futureData.putInt("schema_version", 3);
        futureData.putString("future_marker", "preserve-exactly");
        RocketEntity future = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(future != null, "Rocket entity type did not create future-schema target");
        future.load(futureSave);
        helper.assertTrue(!future.operational(), "Future entity schema remained operational");
        helper.assertTrue(
                future.preservedBlockedData().orElseThrow().equals(futureData),
                "Future entity payload was not preserved"
        );
        helper.assertTrue(
                future.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData").equals(futureData),
                "Future entity payload changed during re-save"
        );

        CompoundTag mismatchedSave = saved.copy();
        CompoundTag mismatchedData = mismatchedSave.getCompound("RocketEntityData");
        mismatchedData.getCompound("flight_data").putUUID("logical_rocket_id", UUID.randomUUID());
        RocketEntity mismatched = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(mismatched != null, "Rocket entity type did not create mismatch target");
        mismatched.load(mismatchedSave);
        helper.assertTrue(!mismatched.operational(), "Mismatched flight identity remained operational");
        helper.assertTrue(
                mismatched.preservedBlockedData().orElseThrow().equals(mismatchedData),
                "Mismatched flight payload was not preserved"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void fuelLoaderFuelsOnlyOwnedNearbyLoadedRocketAtFixedRate(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        BlockPos loaderPosition = origin.offset(5, 0, 0);
        placeLegalRocket(helper, origin, false);
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        ServerLevel level = helper.getLevel();
        UUID ownerId = UUID.randomUUID();

        RocketEntity unauthorized = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(unauthorized != null, "Rocket entity type did not create unauthorized target");
        unauthorized.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        helper.assertTrue(level.addFreshEntity(unauthorized), "Unauthorized rocket did not spawn");

        helper.setBlock(loaderPosition, ModBlocks.FUEL_LOADER.get());
        FuelLoaderBlockEntity loader = (FuelLoaderBlockEntity) helper.getBlockEntity(loaderPosition);
        loader.assignOwner(ownerId);
        ItemStack remainder = loader.itemHandler().insertItem(
                FuelLoaderBlockEntity.SLOT,
                new ItemStack(ModItems.ROCKET_FUEL_CELL.get()),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Fuel Loader rejected a valid fuel cell");
        FuelLoaderBlockEntity.serverTick(level, loader.getBlockPos(), loader.getBlockState(), loader);
        helper.assertTrue(
                loader.status() == FuelLoaderStatus.WAITING_FOR_ROCKET,
                "Fuel Loader selected an unauthorized rocket"
        );
        helper.assertTrue(
                unauthorized.flightData().orElseThrow().fuel().amount() == 0L,
                "Unauthorized rocket received fuel"
        );

        RocketEntity authorized = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(authorized != null, "Rocket entity type did not create authorized target");
        authorized.initialize(snapshot, UUID.randomUUID(), ownerId);
        helper.assertTrue(level.addFreshEntity(authorized), "Authorized rocket did not spawn");
        for (int tick = 0; tick < RocketFlightLimits.FUEL_CELL_UNITS
                / RocketFlightLimits.FUEL_TRANSFER_PER_TICK; tick++) {
            FuelLoaderBlockEntity.serverTick(level, loader.getBlockPos(), loader.getBlockState(), loader);
        }

        helper.assertTrue(
                authorized.flightData().orElseThrow().fuel().amount()
                        == RocketFlightLimits.FUEL_CELL_UNITS,
                "Fuel Loader did not transfer exactly one cell"
        );
        helper.assertTrue(
                authorized.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                "Fuel Loader did not move the rocket to FUELED"
        );
        helper.assertTrue(
                loader.itemHandler().getStackInSlot(FuelLoaderBlockEntity.SLOT)
                        .is(ModItems.EMPTY_CANISTER.get()),
                "Fuel Loader did not conserve the empty canister"
        );
        helper.assertTrue(loader.bufferedUnits() == 0L, "Fuel Loader retained fuel after completion");
        helper.assertTrue(
                unauthorized.flightData().orElseThrow().fuel().amount() == 0L,
                "Unauthorized rocket changed while another rocket was fueled"
        );
        unauthorized.discard();
        authorized.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void unloadedScanCreatesNoChunkTicket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(25_000_000, level.getMinBuildHeight() + 8, 25_000_000);
        helper.assertTrue(!level.hasChunkAt(far), "Far chunk was loaded before rocket scan");
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(),
                rocketPosition(far),
                UUID.randomUUID(),
                level.getGameTime()
        );
        RocketScanResult result = task.step(1);
        helper.assertTrue(result.status() == RocketScanResult.Status.FAILED, "Unloaded scan did not stop");
        helper.assertTrue(
                result.issues().get(0).code() == RocketValidationCode.UNLOADED_CHUNK,
                "Unloaded scan returned " + result.issues().get(0).code()
        );
        helper.assertTrue(!level.hasChunkAt(far), "Rocket scan force-loaded a chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void adminManagerAssemblyAndRecoveryUseServerAuthority(GameTestHelper helper) {
        BlockPos assemblerPosition = new BlockPos(3, 1, 3);
        BlockPos rocketOrigin = assemblerPosition.above();
        helper.setBlock(assemblerPosition, ModBlocks.ROCKET_ASSEMBLER.get());
        placeLegalRocket(helper, rocketOrigin, true);
        ChestBlockEntity sourceChest = (ChestBlockEntity) helper.getBlockEntity(rocketOrigin.east());
        sourceChest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        UUID ownerId = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        RocketManager manager = new RocketManager();

        RocketValidationCode queued = manager.requestAdminAssembler(
                level,
                helper.absolutePos(assemblerPosition),
                ownerId,
                true
        );
        helper.assertTrue(queued == RocketValidationCode.SCAN_IN_PROGRESS,
                "Admin assembly was not queued: " + queued);
        manager.tick(level.getServer());

        BlockPos absoluteOrigin = helper.absolutePos(rocketOrigin);
        var rockets = level.getEntitiesOfClass(
                RocketEntity.class,
                new AABB(absoluteOrigin).inflate(3.0D)
        );
        helper.assertTrue(rockets.size() == 1, "Manager did not create exactly one RocketEntity");
        RocketEntity rocket = rockets.get(0);
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        RocketTransactionRecord stale = new RocketTransactionRecord(
                rocket.assemblyTransactionId().orElseThrow(),
                RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.EXTRACTING,
                snapshot.snapshotId(),
                snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot),
                snapshot.blocks().size(),
                rocket.getUUID()
        );
        savedData.journalFor(snapshot, ownerId).write(stale);

        manager.tick(level.getServer());
        helper.assertTrue(!rocket.isAlive(), "Recovery left the stale RocketEntity alive");
        helper.assertTrue(helper.getBlockState(rocketOrigin).is(ModBlocks.ROCKET_MOTOR.get()),
                "Recovery did not restore the motor");
        ChestBlockEntity restoredChest = (ChestBlockEntity) helper.getBlockEntity(rocketOrigin.east());
        helper.assertTrue(
                restoredChest.getItem(0).is(Items.DIAMOND)
                        && restoredChest.getItem(0).getCount() == 17,
                "Recovery changed the approved chest payload"
        );
        helper.assertTrue(savedData.entries().isEmpty(), "Recovery did not clear the durable journal");
        manager.clear();
        helper.succeed();
    }

    private static CompoundTag stackTag(int slot, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        tag.putByte("Slot", (byte) slot);
        return tag;
    }

    private static void placeLegalRocket(GameTestHelper helper, BlockPos origin, boolean chest) {
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        if (chest) {
            helper.setBlock(origin.east(), Blocks.CHEST);
        }
    }

    private static RocketStructureSnapshot successfulSnapshot(GameTestHelper helper, BlockPos origin) {
        RocketScanResult result = scan(helper, origin);
        helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS,
                "Legal rocket scan failed: " + (result.issues().isEmpty() ? "unknown" : result.issues().get(0).code()));
        return result.snapshot().orElseThrow();
    }

    private static RocketScanResult scan(GameTestHelper helper, BlockPos relativeOrigin) {
        ServerLevel level = helper.getLevel();
        BlockPos absoluteOrigin = helper.absolutePos(relativeOrigin);
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(),
                rocketPosition(absoluteOrigin),
                UUID.randomUUID(),
                level.getGameTime()
        );
        RocketScanResult result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        int steps = 1;
        while (result.status() == RocketScanResult.Status.RUNNING && steps++ < 64) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        return result;
    }

    private static RocketPosition rocketPosition(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }
}
