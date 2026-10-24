package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedRecoveryGameTests {
    @GameTest(template = "rocket_test", batch = "w152_recovery", timeoutTicks = 400)
    public static void earlierRecordWithDProvesRollbackBeforeReleasingTheSurvivor(GameTestHelper helper) {
        recover(helper, false, true);
    }
    @GameTest(template = "rocket_test", batch = "w152_recovery", timeoutTicks = 400)
    public static void electedRecordCompletesPartialCBeforeDiscardingD(GameTestHelper helper) {
        recover(helper, true, true);
    }
    @GameTest(template = "rocket_test", batch = "w152_recovery", timeoutTicks = 400)
    public static void earlierRecordWithoutDCompletesTheExistingBlockFallback(GameTestHelper helper) {
        recover(helper, false, false);
    }
    @GameTest(template = "rocket_test", batch = "w152_copies", timeoutTicks = 400)
    public static void electedRecoveryRetiresBothExactNativeCopies(GameTestHelper helper) {
        recover(helper, true, true, true);
    }
    @GameTest(template = "rocket_test", batch = "w152_copies", timeoutTicks = 400)
    public static void earlierRecoveryKeepsTheBoundSurvivorAndRetiresItsExactCopy(GameTestHelper helper) {
        recover(helper, false, true, true);
    }
    private static void recover(GameTestHelper helper, boolean elected, boolean survivor) {
        recover(helper, elected, survivor, false);
    }
    private static void recover(GameTestHelper helper, boolean elected, boolean survivor, boolean duplicate) {
        var level = helper.getLevel(); var server = level.getServer();
        // Reflection lets the committed regression execute against the pre-implementation source.
        tracked(server, UUID.randomUUID());
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var source = new ChestBlockEntity(origin.east(), Blocks.CHEST.defaultBlockState());
        source.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var payload = new VanillaContainerRocketAdapter().capture(source);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), List.of(
                        new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState())),
                        new RocketBlock(new RocketPosition(1, 0, 0), RocketBlockStateAdapter.capture(Blocks.CHEST.defaultBlockState()), payload)),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(2, 200, 1000, 1000, 1, 1, 1, 1), level.getGameTime());
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2); level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 2);
        var id = UUID.randomUUID(); var owner = UUID.randomUUID(); var logical = UUID.randomUUID();
        var rocket = ModEntities.ROCKET.get().create(level); helper.assertTrue(rocket != null, "Missing rocket type");
        rocket.initialize(snapshot, logical, owner); RocketTransferEntities.positionAtOrigin(rocket, snapshot.sourceOrigin());
        if (survivor) { helper.assertTrue(level.addFreshEntity(rocket), "Fixture D was not published"); }
        var copies = new java.util.ArrayList<io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity>();
        if (duplicate) {
            var image = new CompoundTag(); helper.assertTrue(rocket.save(image), "Fixture D was not serializable");
            var copy = ModEntities.ROCKET.get().create(level); helper.assertTrue(copy != null, "Missing copy type");
            copy.load(image); copy.setUUID(UUID.randomUUID());
            helper.assertTrue(level.addFreshEntity(copy), "Fixture copy was not published"); copies.add(copy);
        }
        var data = RocketTransactionSavedData.get(server);
        var record = new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY,
                elected ? RocketTransactionPhase.RESTORED : RocketTransactionPhase.LOCKED,
                snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, rocket.getUUID());
        data.journalFor(snapshot, owner).write(record); data.checkpoint(server);
        var carrierState = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState();
        level.setBlock(origin, carrierState, 2);
        var carrier = new SealedRocketBlockEntity(origin, carrierState);
        carrier.initialize(SealedRocketRoot.stage(new SealedRocketMember(id, snapshot.snapshotId(), rocket.getUUID(),
                snapshot.contentHash(), snapshot.sourceDimension(), snapshot.sourceOrigin(), 0, snapshot.blocks().get(0).state(), null)));
        level.setBlockEntity(carrier); carrier.setChanged();
        var seen = new boolean[1]; var done = new boolean[1];
        helper.runAtTickTime(399, () -> {
            if (!done[0]) { helper.fail("Recovery incomplete: " + RocketRuntime.sealedDiagnosticForReleaseTest(server, id)
                    + ",absent=" + data.acknowledgedAbsent(id, snapshot.snapshotId(), rocket.getUUID())
                    + ",cells=" + level.getBlockState(origin) + "/" + level.getBlockState(origin.east())
                    + ",lazy=" + RocketRuntime.sealedLazyDiagnosticForReleaseTest(server)); }
        });
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            seen[0] |= tracked(server, id);
            boolean absent = data.acknowledgedAbsent(id, snapshot.snapshotId(), rocket.getUUID());
            if (!absent) {
                helper.assertTrue(!level.getBlockState(origin.east()).is(Blocks.CHEST), "Native resources appeared before checked U absence");
                if (!elected && survivor) { helper.assertTrue(rocket.isAlive(), "Pre-election rollback discarded D"); }
                if (rocket.isAddedToWorld() && rocket.isAlive()) { helper.assertTrue(RocketRuntime.sealedFrozen(rocket), "Recovery exposed D"); }
                return;
            }
            helper.assertTrue(seen[0], "Sealed recovery never owned the durable record");
            if (tracked(server, id)) {
                // The aggregate writer acknowledges after the selected step; the claim belongs to its next continuation.
                helper.assertTrue(!level.getBlockState(origin.east()).is(Blocks.CHEST), "A retained RAM owner admitted unseal");
                if (rocket.isAlive() && rocket.isAddedToWorld()) {
                    helper.assertTrue(RocketRuntime.sealedFrozen(rocket), "Pending acknowledgement continuation released D");
                }
                return;
            }
            boolean rollback = !elected && survivor;
            helper.assertTrue(copies.stream().noneMatch(copy -> level.getEntity(copy.getUUID()) != null),
                    "Recovery released U while an exact duplicate remained live");
            if (rollback) {
                helper.assertTrue(level.getEntity(rocket.getUUID()) == rocket && rocket.isAlive(), "Rollback lost the original D");
                helper.assertTrue(level.getBlockState(origin).isAir() && level.getBlockState(origin.east()).isAir(), "Rollback retained terrain");
                helper.assertTrue(!RocketRuntime.sealedFrozen(rocket), "Checked rollback retained its RAM claim");
            } else {
                helper.assertTrue(level.getEntity(rocket.getUUID()) == null, "Elected terrain retained a live D");
                if (!level.getBlockState(origin).is(Blocks.IRON_BLOCK) || !level.getBlockState(origin.east()).is(Blocks.CHEST)) { return; }
                var target = (ChestBlockEntity) level.getBlockEntity(origin.east());
                helper.assertTrue(target.getItem(0).is(Items.DIAMOND) && target.getItem(0).getCount() == 7, "Recovery lost or duplicated owned items");
            }
            try {
                var root = AnvilRecordReader.read(server.getWorldPath(LevelResource.ROOT).resolve("entities"), new ChunkPos(origin)).root();
                if (rollback) {
                    var matches = root.getList("Entities", 10).stream().map(CompoundTag.class::cast)
                            .filter(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(rocket.getUUID())).toList();
                    helper.assertTrue(matches.size() == 1 && matches.get(0).getCompound("RocketEntityData").getCompound("snapshot")
                            .equals(RocketSnapshotNbtCodec.encode(snapshot)), "Rollback retired U without the exact native D snapshot");
                } else { helper.assertTrue(SealedEntityImages.absent(root, logical, snapshot.snapshotId(), rocket.getUUID()), "Retired U before native D absence"); }
            } catch (java.io.IOException failure) { throw new IllegalStateException("Native recovery proof unreadable", failure); }
            done[0] = true; helper.succeed();
        });
    }
    private static boolean tracked(MinecraftServer server, UUID id) {
        try { return (boolean) RocketRuntime.class.getMethod("sealedRecoveryTrackedForReleaseTest", MinecraftServer.class, UUID.class).invoke(null, server, id); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Sealed recovery unavailable", failure); }
    }
    private SealedRecoveryGameTests() { }
}
