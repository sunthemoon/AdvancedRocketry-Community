package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.ROCKET_FUEL;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.binding;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.tank;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W329 (Root decision R393): the protective reads while the escrow ledger is NOT_LOADED, and their unchanged answers with
 * no ledger file. Each batch changes the server-wide ledger state, so no other test runs beside it. The first test to find
 * the shared delegate idle, and the transfer journal and assembly transactions empty, installs the state, and the batch's
 * {@link AfterBatch} restores the ledger it found. The rockets and devices are ordinary: they carry no escrow history in
 * RAM, which is the case whose history would live only in the unloaded ledger.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowLedgerNotLoadedGameTests {
    static final String NOT_LOADED = "w329_ledger_not_loaded";
    static final String NO_FILE = "w329_ledger_no_file";
    private static final int TIMEOUT = 200;
    /** Ticks to wait for an idle delegate and empty journals before failing with the reason. */
    private static final int INSTALL_WAIT = 150;
    private static final BlockPos ROCKET = new BlockPos(1, 2, 1);
    private static final BlockPos DEVICE = new BlockPos(3, 2, 1);
    /** The installed batch and the ledger it found; the batch's AfterBatch clears it. */
    private static Installed installed;
    private record Installed(String batch, @Nullable ResourceEscrowSavedData original) { }

    private EscrowLedgerNotLoadedGameTests() { }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedRocketIsNotIdleAndKillLeavesIt(GameTestHelper helper) {
        withRocket(helper, NOT_LOADED, rocket -> {
            helper.assertTrue(!RocketEscrowGate.escrowIdle(rocket) && !RocketEscrowGate.idle(rocket),
                    "An ordinary rocket read idle while the ledger is NOT_LOADED");
            rocket.kill();
            helper.assertTrue(rocket.isAlive() && !rocket.isRemoved(), "kill removed a rocket while the ledger is NOT_LOADED");
        });
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedRocketFlightIsFrozenAndRefusesAFlightDataUpdate(GameTestHelper helper) {
        withRocket(helper, NOT_LOADED, rocket -> {
            helper.assertTrue(RocketEscrowGate.flightFrozen(rocket), "An ordinary rocket's flight was not frozen");
            var before = rocket.flightData().orElseThrow();
            IllegalStateException refused = null;
            try { rocket.updateFlightData(before); } catch (IllegalStateException expected) { refused = expected; }
            helper.assertTrue(refused != null && String.valueOf(refused.getMessage()).contains("exact escrow action permit"),
                    "A flight data update was not refused: " + refused);
            helper.assertTrue(before.equals(rocket.flightData().orElseThrow()), "The refused update changed the flight data");
        });
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedRocketIsRetirementProtectedAndRefusesResourceMutation(GameTestHelper helper) {
        withRocket(helper, NOT_LOADED, rocket -> {
            helper.assertTrue(!RocketEscrowGate.retirementUnprotected(rocket), "An ordinary rocket may retire unprotected");
            // Disassembly's validation and every fuel or cargo path read this before any retirement or mutation.
            helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Resource mutation was allowed");
        });
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedRocketIsNotANeutralBaySource(GameTestHelper helper) {
        withRocket(helper, NOT_LOADED, rocket -> helper.assertTrue(!RocketEscrowGate.bayNeutral(rocket, UUID.randomUUID()),
                "An ordinary rocket passed the bay source check"));
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedLogicalRocketIsLocked(GameTestHelper helper) {
        withRocket(helper, NOT_LOADED, rocket -> {
            var server = helper.getLevel().getServer(); var logical = rocket.assemblyTransactionId().orElseThrow();
            helper.assertTrue(RocketEscrowGate.escrowLogicalLocked(server, logical) && RocketEscrowGate.logicalLocked(server, logical),
                    "A logical rocket was unlocked for transfer recovery and the flight rules");
        });
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedCargoDeviceIsHeld(GameTestHelper helper) {
        withCargoDevice(helper, NOT_LOADED, device -> helper.assertTrue(device.escrowLocked() && device.protectedFromRemoval()
                        && device.deviceRefusal() == RocketCargoStatus.ESCROW_PENDING,
                "An unmarked cargo device was not held: " + device.deviceRefusal()));
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedItemLoaderIsHeld(GameTestHelper helper) { withItemDevice(helper, RocketCargoRole.LOAD); }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedItemUnloaderIsHeld(GameTestHelper helper) { withItemDevice(helper, RocketCargoRole.UNLOAD); }

    private static void withItemDevice(GameTestHelper helper, RocketCargoRole role) {
        withRocket(helper, NOT_LOADED, rocket -> {
            var level = helper.getLevel(); var at = helper.absolutePos(DEVICE);
            var block = role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER : ModBlocks.ROCKET_ITEM_UNLOADER;
            level.setBlockAndUpdate(at, block.get().defaultBlockState());
            try {
                var device = (RocketItemCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
                var root = RocketItemCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), role).withBinding(binding(rocket));
                var tag = new CompoundTag(); tag.put(RocketItemCargoRoot.KEY, root.encode()); device.load(tag);
                var before = device.saveWithoutMetadata();
                helper.assertTrue(device.root().orElseThrow().escrow().equals(RocketEscrowSection.neutral()), "The item fixture carries escrow history");
                helper.assertTrue(device.escrowLocked() && device.protectedFromRemoval()
                                && device.deviceRefusal() == RocketCargoStatus.ESCROW_PENDING,
                        "An unmarked item " + role + " device was not held: " + device.deviceRefusal());
                helper.assertTrue(before.equals(device.saveWithoutMetadata()), "Protective reads mutated the item device");
            } finally {
                level.removeBlockEntity(at); level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedFuelLoaderIsHeld(GameTestHelper helper) {
        withLoader(helper, NOT_LOADED, loader -> helper.assertTrue(EscrowLoaderAdmission.progress(loader) == FluidFuelLoaderStatus.ESCROW_PENDING
                        && loader.escrowLocked() && loader.protectedFromRemoval()
                        && loader.deviceRefusal() == FluidFuelLoaderStatus.ESCROW_PENDING,
                "An unmarked fuel loader was not held: " + loader.deviceRefusal()));
    }

    @GameTest(template = "empty", batch = NOT_LOADED, timeoutTicks = TIMEOUT)
    public static void notLoadedStatusReportsTheLedgerNotLoaded(GameTestHelper helper) {
        run(helper, NOT_LOADED, () -> {
            var server = helper.getLevel().getServer();
            var line = EscrowOperatorDiagnostics.status(server).get(0);
            helper.assertTrue(line.contains("ledger=not-loaded") && !line.contains("ledger=absent"), "Status line: " + line);
            // As in production: the shared authority refuses every consumer, so no ledger can be created meanwhile.
            var authority = EscrowRuntime.of(server).propulsion(server).authority();
            for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
                helper.assertTrue(authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), consumer)
                        == EscrowAdmissionAuthority.Result.UNAVAILABLE, consumer + " admission was available");
            }
        });
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileRocketIsIdle(GameTestHelper helper) {
        withRocket(helper, NO_FILE, rocket -> helper.assertTrue(RocketEscrowGate.escrowIdle(rocket) && RocketEscrowGate.idle(rocket),
                "An ordinary rocket was not idle without a ledger file"));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileRocketFlightIsNotFrozen(GameTestHelper helper) {
        withRocket(helper, NO_FILE, rocket -> {
            helper.assertTrue(!RocketEscrowGate.flightFrozen(rocket), "An ordinary rocket's flight was frozen without a ledger file");
            rocket.updateFlightData(rocket.flightData().orElseThrow());
        });
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileRocketMayRetireUnprotected(GameTestHelper helper) {
        withRocket(helper, NO_FILE, rocket -> helper.assertTrue(RocketEscrowGate.retirementUnprotected(rocket),
                "An ordinary rocket was retirement-protected without a ledger file"));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileRocketIsANeutralBaySource(GameTestHelper helper) {
        withRocket(helper, NO_FILE, rocket -> helper.assertTrue(RocketEscrowGate.bayNeutral(rocket, UUID.randomUUID()),
                "An ordinary rocket failed the bay source check without a ledger file"));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileLogicalRocketIsUnlocked(GameTestHelper helper) {
        withRocket(helper, NO_FILE, rocket -> helper.assertTrue(!RocketEscrowGate.escrowLogicalLocked(helper.getLevel().getServer(),
                rocket.assemblyTransactionId().orElseThrow()), "A logical rocket was locked without a ledger file"));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileCargoDeviceIsFree(GameTestHelper helper) {
        withCargoDevice(helper, NO_FILE, device -> helper.assertTrue(!device.escrowLocked() && !device.protectedFromRemoval()
                        && device.deviceRefusal() == null, "An unmarked cargo device was held without a ledger file: " + device.deviceRefusal()));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileFuelLoaderIsFree(GameTestHelper helper) {
        withLoader(helper, NO_FILE, loader -> helper.assertTrue(EscrowLoaderAdmission.progress(loader) == null
                        && !loader.escrowLocked() && !loader.protectedFromRemoval(),
                "An unmarked fuel loader was held without a ledger file: " + loader.deviceRefusal()));
    }

    @GameTest(template = "empty", batch = NO_FILE, timeoutTicks = TIMEOUT)
    public static void noFileStatusReportsTheLedgerAbsent(GameTestHelper helper) {
        run(helper, NO_FILE, () -> {
            var line = EscrowOperatorDiagnostics.status(helper.getLevel().getServer()).get(0);
            helper.assertTrue(line.equals("escrow admission=disabled ledger=absent held=0 released=0 pruning_groups=0 receipts=0/64"),
                    "Status line: " + line);
        });
    }

    @AfterBatch(batch = NOT_LOADED)
    public static void restoreAfterNotLoaded(ServerLevel level) { restore(level, NOT_LOADED); }

    @AfterBatch(batch = NO_FILE)
    public static void restoreAfterNoFile(ServerLevel level) { restore(level, NO_FILE); }

    /** One check, once the batch's state is installed; it runs in a single tick and then succeeds. */
    private static void run(GameTestHelper helper, String batch, Runnable check) {
        var done = new boolean[1]; var waited = new int[1];
        helper.onEachTick(() -> {
            if (done[0] || !install(helper, batch, waited[0]++)) { return; }
            done[0] = true;
            check.run();
            helper.succeed();
        });
    }

    private static void withRocket(GameTestHelper helper, String batch, Consumer<RocketEntity> check) {
        run(helper, batch, () -> {
            var level = helper.getLevel();
            var rocket = RocketSettlementIdleGameTests.createRocket(level, helper.absolutePos(ROCKET), UUID.randomUUID());
            try {
                helper.assertTrue(level.addFreshEntity(rocket), "The ordinary rocket fixture did not join");
                helper.assertTrue(rocket.escrowSection().equals(RocketEscrowSection.neutral()), "The rocket fixture carries escrow history");
                check.accept(rocket);
            } finally { rocket.discard(); }
        });
    }

    private static void withCargoDevice(GameTestHelper helper, String batch, Consumer<RocketFluidCargoBlockEntity> check) {
        withRocket(helper, batch, rocket -> {
            var level = helper.getLevel(); var at = helper.absolutePos(DEVICE);
            level.setBlockAndUpdate(at, ModBlocks.ROCKET_FLUID_LOADER.get().defaultBlockState());
            try {
                var device = (RocketFluidCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
                var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), RocketCargoRole.LOAD, LoaderTank.EMPTY)
                        .withBinding(binding(rocket));
                var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag);
                helper.assertTrue(device.root().orElseThrow().escrow().equals(RocketEscrowSection.neutral()), "The device fixture carries escrow history");
                check.accept(device);
            } finally {
                // No removal callbacks, so removing a held fixture logs nothing.
                level.removeBlockEntity(at); level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    private static void withLoader(GameTestHelper helper, String batch, Consumer<FluidFuelLoaderBlockEntity> check) {
        withRocket(helper, batch, rocket -> {
            var level = helper.getLevel(); var at = helper.absolutePos(DEVICE);
            try {
                var loader = FluidFuelLoaderEscrowFixtures.loader(level, at, rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 1_000), binding(rocket));
                helper.assertTrue(!loader.root().orElseThrow().escrow().locked(), "The loader fixture carries escrow history");
                check.accept(loader);
            } finally { FluidFuelLoaderEscrowFixtures.clear(level, at); }
        });
    }

    /**
     * Installs the batch's ledger state once the shared delegate holds no work and no transfer record or assembly
     * transaction could be fenced meanwhile. NOT_LOADED is vanilla's cached miss for the ledger, which a ledger that did
     * not load leaves, with the PROPULSION delegate back at NOT_LOADED; NO_FILE detaches any ledger.
     */
    private static boolean install(GameTestHelper helper, String batch, int waited) {
        var server = helper.getLevel().getServer();
        EscrowTestEndpoints.requireEnabled(server);
        if (installed != null) {
            helper.assertTrue(installed.batch().equals(batch), "W329's two ledger batches overlapped");
            return true;
        }
        var runtime = EscrowRuntime.of(server); var delegate = runtime.propulsion(server);
        int journal = RocketTransferSavedData.get(server).entries().size();
        int transactions = RocketTransactionSavedData.get(server).entries().size();
        if (delegate.holdsWork() || journal > 0 || transactions > 0) {
            helper.assertTrue(waited < INSTALL_WAIT, "W329 could not install " + batch + ": delegate work=" + delegate.holdsWork()
                    + " journal=" + journal + " transactions=" + transactions);
            return false;
        }
        installed = new Installed(batch, ResourceEscrowSavedData.existing(server));
        if (NOT_LOADED.equals(batch)) {
            server.overworld().getDataStorage().set(ManagedSavedDataType.RESOURCE_ESCROW.dataName(), null);
            helper.assertTrue(delegate.unloadForTests(), "The idle PROPULSION delegate refused NOT_LOADED");
        } else {
            helper.assertTrue(runtime.replaceLedgerForTests(server, null), "The idle ledger was not detached to NO_FILE");
        }
        var expected = NOT_LOADED.equals(batch) ? EscrowLedgerLookup.State.NOT_LOADED : EscrowLedgerLookup.State.NO_FILE;
        helper.assertTrue(EscrowLedgerLookup.of(server).state() == expected, "The ledger lookup did not report " + expected);
        return true;
    }

    private static void restore(ServerLevel level, String batch) {
        var held = installed;
        if (held == null || !held.batch().equals(batch)) { return; }
        installed = null;
        var server = level.getServer();
        if (!EscrowRuntime.of(server).replaceLedgerForTests(server, held.original())) {
            throw new IllegalStateException("W329 could not restore the escrow ledger after " + batch);
        }
    }
}
