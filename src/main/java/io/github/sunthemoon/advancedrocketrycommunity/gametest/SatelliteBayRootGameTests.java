package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteBayFixtures.BATCH;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteBayFixtures.TEMPLATE;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayProtection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W27b A1: guarded roots, operator-only NBT, byte-equal rocket capture and the R-038 save refusal. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteBayRootGameTests {
    private SatelliteBayRootGameTests() {
    }

    private static CompoundTag item(String id, CompoundTag tag) {
        CompoundTag pkg = new CompoundTag();
        pkg.putString("id", id);
        pkg.putByte("Count", (byte) 1);
        pkg.put("tag", tag);
        return pkg;
    }

    private static CompoundTag rootWith(UUID owner, CompoundTag pkg) {
        CompoundTag root = SatelliteBayFixtures.root(owner, null);
        root.put(SatelliteBayRoot.PACKAGE, pkg);
        return root;
    }

    /** A package item whose identity has a newer schema: kept raw as UNSUPPORTED_DATA. */
    static CompoundTag futureIdentityPackage(UUID owner) {
        CompoundTag identity = new CompoundTag();
        identity.putInt("schema_version", 3);
        identity.putUUID("satellite_id", UUID.randomUUID());
        identity.putUUID("owner_id", owner);
        identity.putString("future_field", "kept");
        CompoundTag tag = new CompoundTag();
        tag.put("SatelliteIdentity", identity);
        return item(ModIdentity.id("satellite_package").toString(), tag);
    }

    /** Five guarded roots: unknown fields, a newer root schema, an unknown item, a newer identity, a foreign item. */
    private static Map<SatelliteBayStatus.Guard, Tag> guardedRoots(UUID owner) {
        Map<SatelliteBayStatus.Guard, Tag> roots = new LinkedHashMap<>();
        CompoundTag unknownField = SatelliteBayFixtures.root(owner, SatelliteBayFixtures.dataPackage(owner));
        unknownField.putString("unknown_field", "kept");
        roots.put(SatelliteBayStatus.Guard.QUARANTINED, unknownField);
        CompoundTag future = SatelliteBayFixtures.root(owner, null);
        future.putInt(SatelliteBayRoot.SCHEMA, 2);
        future.putString("future_field", "kept");
        roots.put(SatelliteBayStatus.Guard.UNSUPPORTED_ROOT, future);
        CompoundTag unknownItem = SatelliteBayFixtures.dataPackage(owner).serializeNBT();
        unknownItem.putString("id", "w27b_absent_mod:satellite_package");
        roots.put(SatelliteBayStatus.Guard.PACKAGE_UNAVAILABLE, rootWith(owner, unknownItem));
        roots.put(SatelliteBayStatus.Guard.UNSUPPORTED_DATA, rootWith(owner, futureIdentityPackage(owner)));
        roots.put(SatelliteBayStatus.Guard.INVALID_PACKAGE, rootWith(owner, item("minecraft:stone", new CompoundTag())));
        return roots;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void rawUnknownAndFutureRootsAreKeptGuardedAndDropNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID ownerId = UUID.randomUUID();
        List<BlockPos> placed = new ArrayList<>();
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w27bGuarded", level,
                    helper.absolutePos(new BlockPos(8, 2, 8)), new ArrayList<>(), false, new ArrayList<>(), false);
            joined.add(owner);
            owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            ItemStack mine = SatelliteBayFixtures.dataPackage(ownerId);
            int column = 2;
            for (Map.Entry<SatelliteBayStatus.Guard, Tag> entry : guardedRoots(ownerId).entrySet()) {
                BlockPos relative = new BlockPos(column, 2, 4);
                column += 3;
                BlockPos at = helper.absolutePos(relative);
                placed.add(at);
                Tag original = entry.getValue().copy();
                SatelliteBayBlockEntity bay = SatelliteBayFixtures.place(helper, relative, entry.getValue());
                String label = entry.getKey().name();
                helper.assertTrue(bay.dataGuard() == entry.getKey() && bay.guarded() && bay.protectedFromRemoval(),
                        label + " was not guarded: " + bay.dataGuard());
                helper.assertTrue(original.equals(SatelliteBayFixtures.saved(bay)), label + " root was not kept unchanged");
                helper.assertTrue(bay.itemHandler().getStackInSlot(0).isEmpty()
                        && bay.itemHandler().extractItem(0, 1, true).isEmpty()
                        && bay.itemHandler().extractItem(0, 1, false).isEmpty()
                        && !bay.itemHandler().isItemValid(0, mine)
                        && ItemStack.matches(bay.itemHandler().insertItem(0, mine, false), mine),
                        label + " exposed a live slot");
                helper.assertTrue(!bay.admitted(owner) && bay.createMenu(1, owner.getInventory(), owner) == null,
                        label + " opened its menu");
                // A live /data replacement keeps the held root.
                bay.load(SatelliteBayFixtures.outer(SatelliteBayFixtures.root(ownerId, mine)));
                helper.assertTrue(original.equals(SatelliteBayFixtures.saved(bay)) && bay.dataGuard() == entry.getKey(),
                        label + " was replaced by live NBT");
                BlockEvent.BreakEvent breaking = new BlockEvent.BreakEvent(level, at, bay.getBlockState(), owner);
                MinecraftForge.EVENT_BUS.post(breaking);
                helper.assertTrue(breaking.isCanceled(), label + " allowed player break");
                helper.assertTrue(!owner.gameMode.destroyBlock(at) && level.getBlockEntity(at) == bay,
                        label + " was broken by a player");
                Explosion explosion = new Explosion(level, null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                        1.0F, false, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(at);
                MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                helper.assertTrue(explosion.getToBlow().isEmpty(), label + " stayed in the explosion");
                bay.getBlockState().onBlockExploded(level, at, explosion);
                helper.assertTrue(level.getBlockEntity(at) == bay
                        && Block.getDrops(bay.getBlockState(), level, at, bay).isEmpty()
                        && SatelliteBayFixtures.drops(level, at).isEmpty()
                        && original.equals(SatelliteBayFixtures.saved(bay)), label + " dropped or lost its root");
            }
        } finally {
            placed.forEach(at -> SatelliteBayFixtures.clear(level, at));
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }

    /** As {@code OperatorItemNbtGameTests}: a non-operator's {@code BlockEntityTag} restores no owner or root. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void onlyAnOperatorCanLoadBayNbtAndOrdinaryPlacementRestoresNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos byBuilder = helper.absolutePos(new BlockPos(3, 2, 10));
        BlockPos byOperator = helper.absolutePos(new BlockPos(9, 2, 10));
        UUID forged = UUID.randomUUID();
        ItemStack pkg = SatelliteBayFixtures.dataPackage(forged);
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer operator = null;
        try {
            SatelliteBayBlockEntity probe = new SatelliteBayBlockEntity(byBuilder,
                    ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState());
            helper.assertTrue(probe.onlyOpCanSetNbt(), "The bay accepts non-operator item NBT");
            ServerPlayer builder = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w27bBuilder", level,
                    byBuilder.north(3), new ArrayList<>());
            joined.add(builder);
            builder.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(builder.getAbilities().instabuild && !builder.canUseGameMasterBlocks(),
                    "The builder is not a creative non-operator");
            SatelliteBayBlockEntity built = place(helper, builder, byBuilder, carrier(forged, pkg));
            helper.assertTrue(built.ownerId().filter(builder.getUUID()::equals).isPresent()
                    && built.contents().filter(contents -> contents.pkg() == null).isPresent(),
                    "A non-operator's item NBT reached the bay: " + built.saveWithoutMetadata());

            operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w27bOperatorNbt", level,
                    byOperator.north(3), new ArrayList<>(), true);
            joined.add(operator);
            server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
            operator.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            SatelliteBayBlockEntity loaded = place(helper, operator, byOperator, carrier(forged, pkg));
            helper.assertTrue(loaded.ownerId().filter(forged::equals).isPresent()
                    && loaded.packageState().filter(state -> state.available()
                    && ItemStack.matches(state.liveCopy(), pkg)).isPresent(), "An operator's item NBT was not loaded");
        } finally {
            SatelliteBayFixtures.clear(level, byBuilder);
            SatelliteBayFixtures.clear(level, byOperator);
            if (operator != null) {
                server.getPlayerList().getOps().remove(operator.getGameProfile());
            }
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }

    private static ItemStack carrier(UUID owner, ItemStack pkg) {
        CompoundTag blockEntity = SatelliteBayFixtures.outer(SatelliteBayFixtures.root(owner, pkg));
        ItemStack stack = new ItemStack(ModItems.ROCKET_SATELLITE_BAY.get());
        stack.getOrCreateTag().put(BlockItem.BLOCK_ENTITY_TAG, blockEntity);
        return stack;
    }

    private static SatelliteBayBlockEntity place(GameTestHelper helper, ServerPlayer placer, BlockPos at, ItemStack stack) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        placer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = placer.gameMode.useItemOn(placer, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
        placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(at).is(ModBlocks.ROCKET_SATELLITE_BAY.get()),
                "Placing the bay was refused: " + result);
        return (SatelliteBayBlockEntity) helper.getLevel().getBlockEntity(at);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void assemblyAndDisassemblyKeepEveryBayRootByteForByte(GameTestHelper helper) {
        BlockPos origin = new BlockPos(5, 2, 5);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            UUID ownerId = UUID.randomUUID();
            RocketTransactionSavedData journal = RocketTransactionSavedData.get(level.getServer());
            RocketBlockEntityAdapters adapters = RocketBlockEntityAdapters.defaults();
            ServerLevelRocketTransactionWorld world = new ServerLevelRocketTransactionWorld(level, adapters, ownerId);
            RocketRegionLockManager locks = new RocketRegionLockManager();
            RocketOperationLedger ledger = new RocketOperationLedger();
            List<BlockPos> structure = List.of(origin, origin.above(), origin.above(2), origin.east(), origin.west(),
                    origin.north());
            Map<BlockPos, String> expected = new LinkedHashMap<>();
            RocketEntity rocket = null;
            RocketStructureSnapshot snapshot = null;
            try {
                helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
                helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
                helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
                CompoundTag available = SatelliteBayFixtures.root(ownerId, SatelliteBayFixtures.solarPackage(ownerId));
                CompoundTag raw = rootWith(ownerId, futureIdentityPackage(ownerId));
                CompoundTag consumed = SatelliteBayFixtures.consumedRoot(ownerId);
                SatelliteBayFixtures.place(helper, origin.east(), available);
                SatelliteBayFixtures.place(helper, origin.west(), raw);
                SatelliteBayFixtures.place(helper, origin.north(), consumed);
                expected.put(origin.east(), SatelliteBayFixtures.signature(available));
                expected.put(origin.west(), SatelliteBayFixtures.signature(raw));
                expected.put(origin.north(), SatelliteBayFixtures.signature(consumed));
                snapshot = SatelliteBayGameTests.scan(helper, origin);
                assertPayloads(helper, snapshot, origin, expected);

                UUID assembly = UUID.randomUUID();
                RocketTransactionResult assembled = new RocketAssemblyTransaction(world, locks, ledger,
                        journal.journalFor(snapshot, ownerId)).execute(assembly, snapshot);
                helper.assertTrue(assembled.success(), "Assembly with bays failed: " + assembled.code());
                rocket = (RocketEntity) level.getEntity(assembled.rocketEntityId().orElseThrow());
                for (BlockPos relative : structure) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(relative)).isAir()
                            && level.getBlockEntity(helper.absolutePos(relative)) == null, "Assembly left " + relative);
                }
                // A captured bay has no live slot: only the rocket's snapshot holds its root.
                assertPayloads(helper, rocket.snapshot().orElseThrow(), origin, expected);
                helper.assertTrue(SatelliteBayFixtures.drops(level, helper.absolutePos(origin)).isEmpty(),
                        "Assembly dropped a package");

                UUID disassembly = UUID.randomUUID();
                RocketTransactionResult restored = new RocketDisassemblyTransaction(world, locks, ledger,
                        journal.journalFor(snapshot, ownerId)).execute(disassembly, rocket.getUUID(), snapshot);
                helper.assertTrue(restored.success(), "Disassembly with bays failed: " + restored.code());
                for (Map.Entry<BlockPos, String> bay : expected.entrySet()) {
                    BlockPos at = helper.absolutePos(bay.getKey());
                    helper.assertTrue(level.getBlockEntity(at) instanceof SatelliteBayBlockEntity restoredBay
                                    && bay.getValue().equals(SatelliteBayFixtures.signature(
                                    SatelliteBayFixtures.saved(restoredBay))),
                            "Disassembly changed the bay root at " + bay.getKey());
                }
                helper.assertTrue(SatelliteBayFixtures.drops(level, helper.absolutePos(origin)).isEmpty(),
                        "Disassembly dropped a package");

                // Capture validates the root: a quarantined bay keeps the rocket from scanning.
                CompoundTag quarantined = SatelliteBayFixtures.root(ownerId, null);
                quarantined.putString("unknown_field", "kept");
                SatelliteBayFixtures.clear(level, helper.absolutePos(origin.north()));
                SatelliteBayBlockEntity guarded = SatelliteBayFixtures.place(helper, origin.north(), quarantined);
                RocketScanResult refused = new RocketStructureScanTask(new ServerLevelRocketScanWorld(level, adapters),
                        level.dimension().location(), SatelliteBayGameTests.position(helper.absolutePos(origin)),
                        UUID.randomUUID(), level.getGameTime()).step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
                helper.assertTrue(refused.status() != RocketScanResult.Status.SUCCESS
                        && quarantined.equals(SatelliteBayFixtures.saved(guarded)), "A quarantined root was captured");
            } finally {
                if (snapshot != null) {
                    for (var entry : journal.entries()) {
                        if (entry.snapshot().snapshotId().equals(snapshot.snapshotId())) {
                            journal.journalFor(snapshot, ownerId).remove(entry.record().transactionId());
                        }
                    }
                }
                if (rocket != null && rocket.isAlive()) {
                    rocket.discard();
                }
                level.getEntitiesOfClass(RocketEntity.class, new AABB(helper.absolutePos(origin)).inflate(4.0D))
                        .forEach(Entity::discard);
                for (BlockPos relative : structure) {
                    SatelliteBayFixtures.clear(level, helper.absolutePos(relative));
                }
                SatelliteBayFixtures.discardDrops(level, helper.absolutePos(origin));
            }
        }, origin);
    }

    private static void assertPayloads(GameTestHelper helper, RocketStructureSnapshot snapshot, BlockPos origin,
                                       Map<BlockPos, String> expected) {
        int found = 0;
        for (RocketBlock block : snapshot.blocks()) {
            var payload = block.blockEntityPayload();
            if (payload.isEmpty() || !SatelliteBayRocketAdapter.ID.equals(payload.get().adapterId())) {
                continue;
            }
            BlockPos relative = helper.relativePos(new BlockPos(snapshot.sourceOrigin().x() + block.position().x(),
                    snapshot.sourceOrigin().y() + block.position().y(), snapshot.sourceOrigin().z() + block.position().z()));
            helper.assertTrue(expected.containsKey(relative)
                            && expected.get(relative).equals(SatelliteBayFixtures.signature(payload.get().data())),
                    "The captured bay root differs at " + relative);
            found++;
        }
        helper.assertTrue(found == expected.size(), "Expected " + expected.size() + " bay payloads, found " + found);
    }

    /**
     * R-038 at GameTest level: a serializable quarantined root is written unchanged and refuses nothing; a root that
     * native serialization cannot keep refuses its terrain chunk. The native scopes are S1's.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void onlyARootNativeSerializationCannotKeepRefusesItsChunkSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(8, 2, 8);
        BlockPos at = helper.absolutePos(relative);
        var chunk = level.getChunkAt(at);
        ChunkPos chunkPos = chunk.getPos();
        try {
            CompoundTag keepable = SatelliteBayFixtures.root(UUID.randomUUID(), null);
            keepable.putString("unknown_field", "kept");
            keepable.putByteArray("oversized", new byte[8_192]);
            SatelliteBayBlockEntity quarantined = SatelliteBayFixtures.place(helper, relative, keepable);
            helper.assertTrue(quarantined.dataGuard() == SatelliteBayStatus.Guard.QUARANTINED && !quarantined.unkeepable(),
                    "The serializable root was not a keepable quarantine");
            CompoundTag outgoing = chunkData(quarantined);
            GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(chunk, level, outgoing));
            SatelliteBayProtection.chunkSave(new ChunkDataEvent.Save(chunk, level, outgoing));
            helper.assertTrue(keepable.equals(outgoing.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0)
                    .get(SatelliteBayRoot.KEY)), "A serializable quarantined root was not written unchanged");
            SatelliteBayFixtures.clear(level, at);

            CompoundTag deep = SatelliteBayFixtures.root(UUID.randomUUID(), null);
            CompoundTag cursor = new CompoundTag();
            deep.put("nested", cursor);
            for (int depth = 0; depth < 520; depth++) {
                CompoundTag next = new CompoundTag();
                cursor.put("n", next);
                cursor = next;
            }
            SatelliteBayBlockEntity unkeepable = SatelliteBayFixtures.place(helper, relative, deep);
            helper.assertTrue(unkeepable.unkeepable() && unkeepable.dataGuard() == SatelliteBayStatus.Guard.QUARANTINED,
                    "A root deeper than the native reader allows was called keepable");
            expectRefusal(helper, () -> GuardedChunkSaves.beforeSave(
                    new ChunkDataEvent.Save(chunk, level, new CompoundTag())));
            GuardedChunkSaves.restoreGameTestFixture(level, chunkPos);
            expectRefusal(helper, () -> SatelliteBayProtection.chunkSave(
                    new ChunkDataEvent.Save(chunk, level, chunkData(unkeepable))));
        } finally {
            SatelliteBayFixtures.clear(level, at);
            GuardedChunkSaves.restoreGameTestFixture(level, chunkPos);
        }
        helper.succeed();
    }

    private static CompoundTag chunkData(SatelliteBayBlockEntity bay) {
        ListTag entities = new ListTag();
        entities.add(bay.saveWithFullMetadata());
        CompoundTag data = new CompoundTag();
        data.put("block_entities", entities);
        return data;
    }

    private static void expectRefusal(GameTestHelper helper, Runnable save) {
        try {
            save.run();
            helper.fail("An unkeepable bay root's chunk save was admitted");
        } catch (IllegalStateException refused) {
            helper.assertTrue(SatelliteBayBlockEntity.SAVE_REFUSAL.equals(refused.getMessage()),
                    "Another failure masked the bay's save refusal: " + refused.getMessage());
        }
    }
}
