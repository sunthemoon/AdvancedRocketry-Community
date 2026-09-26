package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/** One owned rocket with an optional fuel tank and a local, controllable container adapter. */
final class RocketAdapterFailureFixture implements AutoCloseable {
    static final ResourceLocation ADAPTER_ID = ModIdentity.id("test_container_failure_v1");
    static final ResourceLocation OTHER_ID = ModIdentity.id("test_container_other_v1");
    static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    static final BlockPos SECOND_ORIGIN = new BlockPos(10, 2, 3);
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    final GameTestHelper helper;
    final ServerLevel level;
    final UUID ownerId = UUID.randomUUID();
    final RocketTransactionSavedData data;
    final FaultAdapter adapter;
    final RocketBlockEntityAdapters adapters;
    final RocketStructureSnapshot snapshot;
    final RocketRegionLockManager locks = new RocketRegionLockManager();
    final RocketOperationLedger ledger = new RocketOperationLedger();
    final BlockPos chestPosition;
    private final List<BlockPos> positions;
    private final List<UUID> transactionIds = new ArrayList<>();
    private final Set<UUID> initialDrops = new HashSet<>();
    private final AABB bounds;

    RocketAdapterFailureFixture(GameTestHelper helper) {
        this(helper, ORIGIN, ADAPTER_ID);
    }

    RocketAdapterFailureFixture(GameTestHelper helper, BlockPos relativeOrigin, ResourceLocation adapterId) {
        this(helper, relativeOrigin, adapterId, false);
    }

    RocketAdapterFailureFixture(GameTestHelper helper, boolean fuelTank) {
        this(helper, ORIGIN, ADAPTER_ID, fuelTank);
    }

    private RocketAdapterFailureFixture(GameTestHelper helper, BlockPos relativeOrigin,
                                       ResourceLocation adapterId, boolean fuelTank) {
        this.helper = helper;
        level = helper.getLevel();
        data = RocketTransactionSavedData.get(level.getServer());
        helper.assertTrue(data.operational(), "Fixture requires an operational transaction journal");
        BlockPos origin = helper.absolutePos(relativeOrigin);
        chestPosition = origin.east();
        positions = fuelTank ? List.of(origin.west(), origin, origin.above(), origin.above(2), chestPosition)
                : List.of(origin, origin.above(), origin.above(2), chestPosition);
        bounds = new AABB(origin).expandTowards(1.0D, 2.0D, 0.0D)
                .expandTowards(fuelTank ? -1.0D : 0.0D, 0.0D, 0.0D).inflate(0.5D);
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(item -> initialDrops.add(item.getUUID()));
        helper.setBlock(relativeOrigin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(relativeOrigin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(relativeOrigin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(relativeOrigin.east(), Blocks.CHEST);
        if (fuelTank) {
            helper.setBlock(relativeOrigin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        }
        chest().setItem(0, new ItemStack(Items.DIAMOND, 17));
        chest().setItem(26, new ItemStack(Items.IRON_INGOT, 3));
        adapter = new FaultAdapter(adapterId, chestPosition);
        adapters = new RocketBlockEntityAdapters(List.of(adapter));
        RocketStructureScanTask scan = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, adapters), level.dimension().location(),
                position(origin), UUID.randomUUID(), level.getGameTime());
        RocketScanResult result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS,
                "Rocket fixture did not scan successfully: " + result.issues());
        snapshot = result.snapshot().orElseThrow();
        helper.assertTrue(snapshot.blocks().size() == (fuelTank ? 5 : 4), "Fixture unexpectedly included neighboring blocks");
        helper.assertTrue(level.areEntitiesLoaded(ChunkPos.asLong(origin.getX() >> 4, origin.getZ() >> 4)),
                "Fixture entity chunk is not ready for recovery");
    }

    ServerLevelRocketTransactionWorld world() {
        return new ServerLevelRocketTransactionWorld(level, adapters, ownerId);
    }

    ChestBlockEntity chest() {
        return (ChestBlockEntity) level.getBlockEntity(chestPosition);
    }

    RocketBlock chestBlock() {
        return snapshot.blocks().stream().filter(block -> block.blockEntityPayload().isPresent())
                .findFirst().orElseThrow();
    }

    RocketTransactionJournal journal(UUID id) {
        transactionIds.add(id);
        return data.journalFor(snapshot, ownerId);
    }

    RocketEntity assemble() {
        UUID id = UUID.randomUUID();
        RocketTransactionResult result = new RocketAssemblyTransaction(world(), locks, ledger, journal(id))
                .execute(id, snapshot);
        helper.assertTrue(result.success(), "Healthy assembly failed: " + result.code());
        assertSourceEmpty();
        return (RocketEntity) level.getEntity(result.rocketEntityId().orElseThrow());
    }

    void writeRecovery(UUID id, RocketEntity rocket) {
        journal(id).write(new RocketTransactionRecord(id, RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.EXTRACTING, snapshot.snapshotId(), snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot), snapshot.blocks().size(), rocket.getUUID()));
    }

    RocketPersistedTransaction entry(UUID id) {
        return data.entries().stream().filter(value -> value.record().transactionId().equals(id))
                .findFirst().orElseThrow();
    }

    boolean hasEntry(UUID id) {
        return data.entries().stream().anyMatch(value -> value.record().transactionId().equals(id));
    }

    void assertSnapshotRetained(UUID id) {
        CompoundTag expected = RocketSnapshotNbtCodec.encode(snapshot);
        helper.assertTrue(expected.equals(RocketSnapshotNbtCodec.encode(entry(id).snapshot())),
                "Live journal changed the retained snapshot");
        RocketTransactionSavedData reloaded = RocketTransactionSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(reloaded.operational(), "Journal containing provider payload could not reload");
        RocketPersistedTransaction restored = reloaded.entries().stream()
                .filter(value -> value.record().transactionId().equals(id)).findFirst().orElseThrow();
        helper.assertTrue(expected.equals(RocketSnapshotNbtCodec.encode(restored.snapshot())),
                "NBT round-trip changed the provider payload or snapshot identity");
    }

    void assertRocketRetained(RocketEntity rocket) {
        helper.assertTrue(rocket.isAlive() && level.getEntity(rocket.getUUID()) == rocket,
                "Recovery removed the retained rocket authority");
        helper.assertTrue(RocketSnapshotNbtCodec.encode(snapshot).equals(
                        RocketSnapshotNbtCodec.encode(rocket.snapshot().orElseThrow())),
                "Retained rocket payload changed");
    }

    void assertSourceEmpty() {
        for (BlockPos position : positions) {
            helper.assertTrue(level.getBlockState(position).isAir() && level.getBlockEntity(position) == null,
                    "Transaction left a partial source block at " + position);
        }
        assertNoDrops();
    }

    void assertSourceRestored() {
        ServerLevelRocketTransactionWorld world = world();
        for (RocketBlock block : snapshot.blocks()) {
            helper.assertTrue(world.readBlock(snapshot.sourceOrigin().add(block.position()))
                            .filter(RocketWorldBlock.fromSnapshotBlock(block)::equals).isPresent(),
                    "Restored block or payload differs at " + block.position());
        }
        assertNoDrops();
    }

    void assertNoDrops() {
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                        .allMatch(item -> initialDrops.contains(item.getUUID())),
                "Transaction created an item drop outside the retained payload");
    }

    void clearChest() {
        level.removeBlockEntity(chestPosition);
        level.setBlock(chestPosition, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
    }

    @Override
    public void close() {
        for (UUID id : transactionIds) {
            data.journalFor(snapshot, ownerId).remove(id);
        }
        level.getEntitiesOfClass(RocketEntity.class, bounds).stream()
                .filter(rocket -> rocket.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId()))
                        .isPresent()).forEach(Entity::discard);
        for (BlockPos position : positions) {
            level.removeBlockEntity(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }
        level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(item -> !initialDrops.contains(item.getUUID())).forEach(Entity::discard);
    }

    static RocketPosition position(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }

    enum Fault {
        NONE, SUPPORTS_THROW, CAPTURE_THROW, FOREIGN_PAYLOAD_ID,
        RESTORE_THROW, RESTORE_FALSE, RESTORE_INCORRECT_TRUE
    }

    static final class FaultAdapter implements RocketBlockEntityAdapter {
        private final VanillaContainerRocketAdapter vanilla = new VanillaContainerRocketAdapter();
        private final ResourceLocation id;
        private final BlockPos ownedPosition;
        Fault fault = Fault.NONE;
        int restoreCalls;

        FaultAdapter(ResourceLocation id, BlockPos ownedPosition) {
            this.id = id;
            this.ownedPosition = ownedPosition;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public boolean supports(BlockEntity blockEntity) {
            if (!ownedPosition.equals(blockEntity.getBlockPos())) {
                return false;
            }
            if (fault == Fault.SUPPORTS_THROW) {
                throw new IllegalStateException("injected supports failure");
            }
            return vanilla.supports(blockEntity);
        }

        @Override
        public RocketBlockEntityPayload capture(BlockEntity blockEntity) {
            if (fault == Fault.CAPTURE_THROW) {
                throw new IllegalStateException("injected capture failure");
            }
            ResourceLocation capturedId = fault == Fault.FOREIGN_PAYLOAD_ID ? OTHER_ID : id;
            return new RocketBlockEntityPayload(capturedId, vanilla.capture(blockEntity).data());
        }

        @Override
        public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
            restoreCalls++;
            boolean restored = vanilla.restore(blockEntity,
                    new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, payload.data()));
            if (!restored) {
                return false;
            }
            if (fault == Fault.RESTORE_THROW) {
                throw new IllegalStateException("injected restore failure after inventory mutation");
            }
            if (fault == Fault.RESTORE_FALSE) {
                return false;
            }
            if (fault == Fault.RESTORE_INCORRECT_TRUE) {
                ((ChestBlockEntity) blockEntity).setItem(0, new ItemStack(Items.DIAMOND, 18));
            }
            return true;
        }
    }
}
