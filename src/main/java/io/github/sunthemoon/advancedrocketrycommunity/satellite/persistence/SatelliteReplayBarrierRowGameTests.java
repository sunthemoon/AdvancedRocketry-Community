package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 F4.5/F4.6 and F2.7: exact identity proof, not retained initial mission history, controls extraction. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteReplayBarrierRowGameTests {
    private SatelliteReplayBarrierRowGameTests() { }

    @GameTest(template = "empty", batch = "w333_replay_barriers", timeoutTicks = 200)
    public static void dirtyReplayKeepsPackageUntilExactAcknowledgement(GameTestHelper h) throws Exception {
        replay(h, false); h.succeed();
    }
    @GameTest(template = "empty", batch = "w333_replay_barriers", timeoutTicks = 200)
    public static void replaceThenThrowCompletesReplayOnceFromFrozenReadback(GameTestHelper h) throws Exception {
        replay(h, true); h.succeed();
    }

    private static void replay(GameTestHelper h, boolean moved) throws Exception {
        try (var f = new Replay(h)) {
            var before = f.data.missions();
            long epoch = f.data.saveEpoch();
            var reads = new AtomicInteger();
            f.data.writePolicy(new io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy(
                    SatelliteRowGameTestFiles.TYPE, (staged, target) -> {
                        if (moved) { io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target); }
                        throw new SatelliteRegistryTestAwait.InjectedFailure();
                    }, (path, type) -> {
                        reads.incrementAndGet();
                        return io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.readPayload(path, type);
                    }));
            f.terminal.handleButton(f.player, SatelliteTerminalMenu.BUTTON_LAUNCH);
            var table = SatelliteRuntime.terminalActions().orElseThrow();
            var entry = table.at(f.key).orElseThrow();
            h.assertTrue(!entry.covered(f.data) && !f.pkg().isEmpty(), "Dirty replay consumed before acknowledgement");
            f.data.awaitForTests(f.server);
            if (!moved) {
                h.assertTrue(f.data.writerForTests().lastOutcome().orElseThrow() == SatelliteRegistryProof.Outcome.FAILED,
                        "Fixture did not fail before replacement");
                TerminalPendingEffects.complete(f.server);
                h.assertTrue(!entry.covered(f.data) && table.at(f.key).orElseThrow() == entry && ItemStack.matches(f.packageBefore, f.pkg()),
                        "Failed persistence released dirty matching replay");
                f.data.writePolicy(SatelliteRowGameTestFiles.ATOMIC);
                f.data.request(f.server); f.data.awaitForTests(f.server);
            }
            TerminalPendingEffects.complete(f.server);
            h.assertTrue(entry.covered(f.data) && f.pkg().isEmpty() && table.at(f.key).isEmpty(), "Exact acknowledged replay was not extracted");
            h.assertTrue(f.data.saveEpoch() == epoch + 1 && f.data.missions().equals(before)
                    && f.data.account(f.identity.ownerId()).balance() == 0, "Replay recreated/rewarded initial mission or advanced epoch twice");
            if (moved) { h.assertTrue(reads.get() == 1, "Replace-then-throw did not use one frozen readback"); }
            TerminalPendingEffects.complete(f.server);
            h.assertTrue(f.pkg().isEmpty() && f.data.missions().equals(before), "Repeated completion replayed the effect");
            var disk = SatelliteMissionSavedData.load(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile
                    .readPayload(SatelliteMissionSavedData.file(f.server), SatelliteRowGameTestFiles.TYPE).orElseThrow());
            h.assertTrue(disk.missions().equals(before) && disk.satellite(f.identity.satelliteId()).isPresent(), "Reload lost exact replay identity/history");
            disk.close();
        }
    }

    @GameTest(template = "empty", batch = "w333_replay_barriers", timeoutTicks = 200)
    public static void failedCheckpointThenLaterWriteStillProvesReplay(GameTestHelper h) throws Exception {
        for (boolean autosave : List.of(false, true)) {
            try (var f = new Replay(h)) {
                var watch = f.data.watch(f.identity.satelliteId());
                var before = f.data.missions();
                h.assertTrue(f.data.failOneWriteForTests(f.server) == SatelliteRegistryProof.Outcome.FAILED, "Checkpoint cut did not fail");
                h.assertTrue(!f.data.proves(watch), "Failed checkpoint proved identity");
                if (autosave) { f.server.overworld().getDataStorage().save(); }
                else { f.data.request(f.server); }
                f.data.awaitForTests(f.server);
                h.assertTrue(f.data.proves(watch), "Later coalesced/autosave commit did not prove exact identity");
                var service = new SatelliteLaunchService(new SatelliteCatalogManager(), new CelestialCatalogManager());
                var validation = service.validate(f.server, f.identity.ownerId(), f.identity, ModIdentity.id("removed_target"));
                h.assertTrue(validation.result().code() == SatelliteOperationCode.IDEMPOTENT, "Later commit made replay depend on definition/history");
                f.terminal.handleButton(f.player, SatelliteTerminalMenu.BUTTON_LAUNCH);
                h.assertTrue(f.pkg().isEmpty() && f.data.missions().equals(before), "Durable replay retained package or recreated initial mission");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "w333_replay_barriers", timeoutTicks = 200)
    public static void anotherOperationsIntentFencesTerminalAndSharedReplay(GameTestHelper h) throws Exception {
        try (var f = new Replay(h)) {
            String hash = RocketNativeResources.hash("w333-other-operation");
            long tick = f.server.getTickCount();
            var receipt = new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(f.server), UUID.randomUUID(), new RocketPosition(0, 1, 0),
                    f.identity.satelliteId(), f.identity.ownerId(), f.identity.definitionId().toString(), f.identity.kind(), ModIdentity.id("earth").toString(),
                    hash, UUID.randomUUID(), hash, null, hash, null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, tick, tick, 0);
            h.assertTrue(BayReceiptTestHooks.put(f.server, receipt, outcome -> { }).queued(), "Could not install other-operation INTENT");
            try {
                var before = f.data.save(new CompoundTag());
                var service = new SatelliteLaunchService(new SatelliteCatalogManager(), new CelestialCatalogManager());
                h.assertTrue(service.validate(f.server, f.identity.ownerId(), f.identity, null).result().code() == SatelliteOperationCode.DEPLOYMENT_PENDING,
                        "Shared replay bypassed another operation's fence");
                f.terminal.handleButton(f.player, SatelliteTerminalMenu.BUTTON_LAUNCH);
                h.assertTrue(ItemStack.matches(f.packageBefore, f.pkg()) && before.equals(f.data.save(new CompoundTag()))
                        && f.terminal.saveWithoutMetadata().getCompound("SatelliteTerminal").getInt("last_result")
                        == SatelliteOperationCode.DEPLOYMENT_PENDING.ordinal(), "Terminal bypassed deployment fence");
            } finally {
                settleReceipts(h);
                h.assertTrue(BayReceiptTestHooks.remove(f.server, receipt.op(), outcome -> { }).queued(), "Could not retire fixture receipt");
                settleReceipts(h);
            }
        }
        h.succeed();
    }

    private static void settleReceipts(GameTestHelper h) throws InterruptedException {
        var store = BayReceiptTestHooks.store(h.getLevel().getServer()).orElseThrow();
        for (int tick = 1; tick <= 1000 && store.holdsWork(); tick++) { Thread.sleep(1); store.tick(h.getLevel().getServer().getTickCount() + tick); }
        h.assertTrue(!store.holdsWork(), "Receipt fixture did not settle within bounded owner ticks");
    }

    private static final class Replay implements AutoCloseable {
        final GameTestHelper h;
        final net.minecraft.server.MinecraftServer server;
        final SatelliteMissionSavedData original, data;
        final SatelliteIdentity identity;
        final SatelliteTerminalBlockEntity terminal;
        final TerminalActionTable.Key key;
        final FakePlayer player;
        final ItemStack packageBefore;
        Replay(GameTestHelper h) {
            this.h = h; server = h.getLevel().getServer();
            original = SatelliteMissionSavedData.get(server); original.awaitForTests(server);
            data = SatelliteMissionSavedData.create(0);
            server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, data);
            SatelliteMissionSavedData.get(server);
            identity = new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), ModIdentity.id("removed_w333_data"));
            var initial = SatelliteManager.initialMissionId(identity.satelliteId());
            h.assertTrue(data.launch(identity.satelliteId(), initial, identity.ownerId(), new SatelliteDefinition(1, identity.definitionId(), 200, 120, 100,
                    List.of(ModIdentity.id("earth"))), ModIdentity.id("earth"), 0, false).success(), "Replay seed refused");
            h.assertTrue(data.cancel(initial, identity.ownerId(), false, 1).success(), "Initial cancellation refused");
            var pos = new BlockPos(1, 2, 1); h.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get());
            terminal = (SatelliteTerminalBlockEntity) h.getBlockEntity(pos);
            key = new TerminalActionTable.Key(h.getLevel().dimension(), terminal.getBlockPos());
            player = new FakePlayer(h.getLevel(), new GameProfile(identity.ownerId(), "W333Barrier"));
            player.setPos(terminal.getBlockPos().getX() + .5, terminal.getBlockPos().getY() + .5, terminal.getBlockPos().getZ() + .5);
            terminal.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
            var chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()); SatelliteItemData.write(chip, identity);
            var pkg = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get()); SatelliteItemData.write(pkg, identity);
            h.assertTrue(terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, chip, false).isEmpty()
                    && terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, pkg, false).isEmpty(), "Terminal insertion refused");
            packageBefore = pkg.copy();
        }
        ItemStack pkg() { return terminal.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE); }
        @Override public void close() {
            data.writePolicy(SatelliteRowGameTestFiles.ATOMIC); data.close();
            SatelliteRuntime.terminalActions().orElseThrow().at(key).ifPresent(entry -> SatelliteRuntime.terminalActions().orElseThrow().completed(entry));
            h.getLevel().removeBlock(terminal.getBlockPos(), false);
            server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, original);
            original.setDirty(); original.flush(server);
        }
    }
}
