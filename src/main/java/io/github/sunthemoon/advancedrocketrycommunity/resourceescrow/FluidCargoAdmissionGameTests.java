package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real C-A devices stop at exact L1. The isolated batch restores the original ledger and native file. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidCargoAdmissionGameTests {
    private static final String BATCH = "fluid_cargo_admission";
    private static Scenario running;
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 1200)
    public static void realLoadAndUnloadReachPreparedWithoutPublishingResources(GameTestHelper helper) {
        running = new Scenario(helper); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { if (running != null) { running.cleanup(); running = null; } }

    private static final class Scenario {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final List<RocketFluidCargoBlockEntity> devices = new ArrayList<>();
        final List<RocketFluidCargoBlockEntity> competitors = new ArrayList<>();
        final List<RocketEntity> rockets = new ArrayList<>();
        final List<io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity> fuels = new ArrayList<>();
        ResourceEscrowSavedData original, isolated;
        Path file, backup;
        boolean detached, aside, done, baseline;
        int phase, direction;
        CompoundTag deviceBefore, rocketBefore;
        Scenario(GameTestHelper helper) { this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer()); }
        void tick() {
            if (done) { return; }
            pacer.pace(); var level = helper.getLevel(); var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            if (phase == 0) {
                var origin = helper.absolutePos(new BlockPos(4, 2, 4));
                if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())
                        || !RocketTransferSavedData.get(server).retryChecked(server) || runtime.propulsion(server).holdsWork()) { return; }
                original = ResourceEscrowSavedData.existing(server);
                helper.assertTrue(runtime.replaceLedgerForTests(server, null), "Cannot detach idle ledger"); detached = true;
                file = ResourceEscrowSavedData.file(server); backup = file.resolveSibling(file.getFileName() + ".w117-backup");
                try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } aside = true; }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                phase = 1;
            }
            if (phase == 1) {
                boolean load = direction == 0;
                var oversized = new FluidStack(Fluids.WATER, 5000);
                oversized.getOrCreateTag().putByteArray("blob", new byte[7900]);
                var rocket = EscrowCargoPreparationGameTests.create(helper, helper.absolutePos(new BlockPos(4, 2, 4 + direction * 6)), false,
                        load ? List.of(FluidStack.EMPTY) : List.of(oversized, new FluidStack(Fluids.WATER, 5000)));
                rockets.add(rocket);
                var role = load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD;
                var at = new BlockPos(2, 2, 4 + direction * 6);
                helper.setBlock(at, load ? ModBlocks.ROCKET_FLUID_LOADER.get() : ModBlocks.ROCKET_FLUID_UNLOADER.get());
                var device = (RocketFluidCargoBlockEntity) level.getBlockEntity(helper.absolutePos(at)); devices.add(device);
                var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), java.util.UUID.randomUUID(), role,
                        load ? LoaderTank.of(new FluidStack(Fluids.WATER, 5000)) : LoaderTank.EMPTY)
                        .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
                var outer = new CompoundTag(); outer.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(outer);
                deviceBefore = device.saveWithFullMetadata(); rocketBefore = EscrowNativeEnvelope.rocket(rocket);
                var saved = isolated == null ? null : EscrowCodec.encode(isolated.ledger());
                helper.assertTrue(device.attempt() == RocketCargoStatus.ADMISSION_DISABLED, "Closed cargo switch changed result");
                helper.assertTrue((saved == null ? ResourceEscrowSavedData.existing(server) == null && !Files.exists(file)
                        : saved.equals(EscrowCodec.encode(isolated.ledger()))) && !runtime.cargoPreparing(root.binding().logicalRocket())
                        && deviceBefore.equals(device.saveWithFullMetadata()), "Closed switch had side effects");
                EscrowCargoSwitches.forTests(server, load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                        root.device(), true);
                EscrowCargoSwitches.pauseReconciliationForTests(server, root.device(), true);
                phase = 2;
            }
            if (phase == 2) {
                var rocket = rockets.get(direction); var device = devices.get(direction);
                isolated = ResourceEscrowSavedData.existing(server);
                if (isolated == null) { return; }
                if (direction == 0 && isolated.ledger().writerGeneration() == 1 && isolated.ledger().records().isEmpty()) { baseline = true; }
                var op = isolated.ledger().records().stream().filter(member -> member.device().identity().equals(device.root().orElseThrow().device()))
                        .findFirst().orElse(null);
                if (op == null) { return; }
                helper.assertTrue(baseline && isolated.ledger().writerGeneration() >= 2, "Cargo L1 skipped its empty creation baseline");
                helper.assertTrue(op.phase() == EscrowOperation.Phase.PREPARED && op.reason() == EscrowReason.NONE,
                        "Cargo did not stop at acknowledged PREPARED");
                helper.assertTrue(EscrowCargoPayload.decode(op.payload()).cursor().x() == direction,
                        "Selection did not skip the inner-valid payload that exceeds complete O1 capacity");
                helper.assertTrue(device.escrowLocked() && !RocketEscrowGate.idle(rocket), "L1 did not lock both endpoints");
                helper.assertTrue(deviceBefore.equals(device.saveWithFullMetadata()) && rocketBefore.equals(EscrowNativeEnvelope.rocket(rocket)),
                        "C-E1 published native resources");
                isolated.bind(server).reconcile(server, op, result -> helper.assertTrue(
                        result.reason() == EscrowReason.RECONCILIATION_UNIMPLEMENTED, "Cargo reconciliation advanced"));
                if (!isolated.bind(server).writerIdle()) { return; }
                if (direction == 0) { competitors(rocket); }
                if (direction++ == 0) {
                    // The first rocket remains held; use another location and identity for the opposite direction.
                    rocket.discard(); phase = 1; return;
                }
                cleanup(); helper.succeed();
            }
        }
        void competitors(RocketEntity rocket) {
            var level = helper.getLevel(); var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            var coordinator = isolated.bind(server); var saved = EscrowCodec.encode(isolated.ledger());
            long sequence = coordinator.nextSequence(); int count = coordinator.admissionAuthority().pendingCount();
            int queued = runtime.fills(server).size();
            for (var role : RocketCargoRole.values()) {
                var at = new BlockPos(2, 2, role == RocketCargoRole.LOAD ? 2 : 6);
                helper.setBlock(at, role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_FLUID_LOADER.get() : ModBlocks.ROCKET_FLUID_UNLOADER.get());
                var device = (RocketFluidCargoBlockEntity) level.getBlockEntity(helper.absolutePos(at));
                competitors.add(device);
                var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), java.util.UUID.randomUUID(), role,
                        role == RocketCargoRole.LOAD ? LoaderTank.of(new FluidStack(Fluids.WATER, 1000)) : LoaderTank.EMPTY)
                        .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
                var outer = new CompoundTag(); outer.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(outer);
                var kind = role == RocketCargoRole.LOAD ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD;
                EscrowCargoSwitches.forTests(server, kind, root.device(), true);
                var before = device.saveWithFullMetadata();
                helper.assertTrue(device.attempt() == RocketCargoStatus.OTHER_TRANSFER_PENDING, "Second cargo direction was not refused");
                helper.assertTrue(before.equals(device.saveWithFullMetadata()), "Refusal changed the competing device");
                helper.assertTrue(coordinator.transferTurns().size() == 1, "Refusal did not retain one bounded next turn");
                EscrowCargoSwitches.forTests(server, kind, root.device(), false); device.setRemoved();
                helper.assertTrue(coordinator.transferTurns().size() == 0, "Unloaded cargo retained a turn");
            }
            var loader = FluidFuelLoaderEscrowFixtures.loader(level, helper.absolutePos(new BlockPos(6, 2, 4)),
                    rocket.ownerId().orElseThrow(), FluidFuelLoaderEscrowFixtures.tank(FluidFuelLoaderEscrowFixtures.ROCKET_FUEL, 1000),
                    FluidFuelLoaderEscrowFixtures.binding(rocket)); fuels.add(loader);
            var deviceId = loader.root().orElseThrow().device(); runtime.loaderAdmissionForTests(server, deviceId, true);
            var fuelBefore = loader.saveWithFullMetadata();
            helper.assertTrue(loader.attempt() == io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus.ROCKET_BUSY,
                    "Fuel loader bypassed cargo exclusivity");
            helper.assertTrue(coordinator.transferTurns().size() == 1 && fuelBefore.equals(loader.saveWithFullMetadata()), "Fuel refusal lost its turn or changed tank");
            runtime.loaderAdmissionForTests(server, deviceId, false); loader.setRemoved();
            helper.assertTrue(coordinator.transferTurns().size() == 0 && sequence == coordinator.nextSequence()
                    && count == coordinator.admissionAuthority().pendingCount() && queued == runtime.fills(server).size()
                    && !runtime.cargoPreparing(rocket.assemblyTransactionId().orElseThrow())
                    && saved.equals(EscrowCodec.encode(isolated.ledger())), "Refusal changed ledger, reservation, queue or ticket");
        }
        void cleanup() {
            if (done) { return; }
            done = true; var server = helper.getLevel().getServer(); var runtime = EscrowRuntime.of(server);
            for (var device : java.util.stream.Stream.concat(devices.stream(), competitors.stream()).toList()) {
                device.root().ifPresent(root -> EscrowCargoSwitches.pauseReconciliationForTests(server, root.device(), false));
                device.root().ifPresent(root -> EscrowCargoSwitches.forTests(server,
                        root.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                        root.device(), false));
                device.setRemoved();
            }
            rockets.forEach(RocketEntity::discard);
            for (var fuel : fuels) {
                runtime.loaderAdmissionForTests(server, fuel.root().orElseThrow().device(), false); fuel.setRemoved();
            }
            if (detached) { isolated = ResourceEscrowSavedData.existing(server); }
            if (isolated != null && isolated != original) { isolated.stop(); }
            if (aside) {
                try { Files.deleteIfExists(file); if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); } }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
            if (detached && !runtime.replaceLedgerForTests(server, original)) { throw new IllegalStateException("W117 ledger restore refused"); }
        }
    }
}
