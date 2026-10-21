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
import java.util.UUID;
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

/** Native admission, replay, typed Anvil barrier, L2/C8 and the waiting device's next turn in both roles. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidCargoReconciliationGameTests {
    private static final String BATCH = "fluid_cargo_reconciliation";
    private static Scenario running;
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 1200)
    public static void realLoadAndUnloadRetireAndServeTheQueuedCompetitor(GameTestHelper helper) {
        running = new Scenario(helper, false); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { if (running != null) { running.cleanup(); running = null; } }
    @GameTest(template = "rocket_test", batch = "fluid_cargo_named_hold", timeoutTicks = 400)
    public static void namedTransferRemainsPreparedBehindTheExistingF1Fence(GameTestHelper helper) {
        running = new Scenario(helper, true); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = "fluid_cargo_named_hold")
    public static void cleanupNamed(ServerLevel level) { cleanup(level); }
    @GameTest(template = "rocket_test", batch = "fluid_cargo_r39", timeoutTicks = 600)
    public static void unavailableSavedFluidHoldsOnlyItsNativeComponent(GameTestHelper helper) {
        running = new Scenario(helper, false, true); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = "fluid_cargo_r39")
    public static void cleanupR39(ServerLevel level) { cleanup(level); }

    private static final class Scenario {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final boolean named;
        final boolean unavailable;
        final List<RocketFluidCargoBlockEntity> devices = new ArrayList<>();
        final List<RocketEntity> rockets = new ArrayList<>();
        ResourceEscrowSavedData original, isolated;
        Path file, backup;
        boolean detached, aside, done;
        int phase, direction;
        UUID first, second, snapshot;
        String beforeHash, fuel;
        CompoundTag deviceBefore, rocketBefore;
        RocketEntity rocket;
        RocketFluidCargoBlockEntity a, b;
        io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot beforeSnapshot;
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord namedRecord;
        int heldSince;
        RocketEntity otherRocket;
        CompoundTag heldPayload;
        Scenario(GameTestHelper helper, boolean named) { this(helper, named, false); }
        Scenario(GameTestHelper helper, boolean named, boolean unavailable) {
            this.helper = helper; this.named = named; this.unavailable = unavailable; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void tick() {
            if (done) { return; }
            pacer.pace(); var level = helper.getLevel(); var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            if (phase == 0) {
                var origin = helper.absolutePos(new BlockPos(4, 2, 4));
                if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())
                        || !RocketTransferSavedData.get(server).retryChecked(server) || runtime.propulsion(server).holdsWork()) { return; }
                original = ResourceEscrowSavedData.existing(server);
                check(runtime.replaceLedgerForTests(server, null), "Cannot detach idle ledger"); detached = true;
                file = ResourceEscrowSavedData.file(server); backup = file.resolveSibling(file.getFileName() + ".w124-backup");
                try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } aside = true; }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                phase = 1;
            }
            if (phase == 1) {
                rocket = EscrowCargoPreparationGameTests.create(helper, helper.absolutePos(new BlockPos(4, 2, 4 + direction * 6)),
                        false, List.of(direction == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000)));
                rockets.add(rocket); snapshot = rocket.snapshot().orElseThrow().snapshotId(); beforeHash = rocket.snapshot().orElseThrow().contentHash();
                beforeSnapshot = rocket.snapshot().orElseThrow();
                fuel = rocket.flightData().orElseThrow().fuel().resourceSignature();
                a = device(new BlockPos(2, 2, 4 + direction * 6));
                if (unavailable) {
                    var heldRocket = rocket;
                    otherRocket = EscrowCargoPreparationGameTests.create(helper, helper.absolutePos(new BlockPos(4, 2, 10)), false);
                    rockets.add(otherRocket); rocket = otherRocket; b = device(new BlockPos(2, 2, 10)); rocket = heldRocket;
                } else { b = device(new BlockPos(6, 2, 4 + direction * 6)); }
                deviceBefore = a.saveWithFullMetadata(); rocketBefore = EscrowNativeEnvelope.rocket(rocket);
                if (named) {
                    namedRecord = EscrowCargoTransferFixtures.record(rocket, beforeSnapshot, false);
                    RocketTransferSavedData.get(server).put(namedRecord);
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
                    phase = 10;
                } else {
                    EscrowCargoSwitches.pauseReconciliationForTests(server, a.root().orElseThrow().device(), true);
                    enable(a, true); phase = 2;
                }
            }
            if (phase == 10) {
                if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                EscrowCargoSwitches.pauseReconciliationForTests(server, a.root().orElseThrow().device(), true);
                enable(a, true); phase = 2;
            }
            isolated = ResourceEscrowSavedData.existing(server);
            if (isolated == null) { return; }
            var coordinator = isolated.bind(server);
            if (phase == 2) {
                var op = member(a); if (op == null) { return; }
                first = op.id(); check(named ? namedRecord.transferId().equals(op.guard().transfer()) : op.guard().transfer() == null,
                        "Cargo used the wrong journal guard");
                check(op.phase() == EscrowOperation.Phase.PREPARED && op.nativeBinding().complete(), "C1 lost complete PREPARED");
                check(deviceBefore.equals(a.saveWithFullMetadata()) && rocketBefore.equals(EscrowNativeEnvelope.rocket(rocket)), "C1 published resources");
                enable(a, false);
                if (named) {
                    EscrowCargoSwitches.pauseReconciliationForTests(server, a.root().orElseThrow().device(), false);
                    heldSince = server.getTickCount(); phase = 11; return;
                }
                if (unavailable) {
                    // A structurally valid saved Fluid whose mod is absent on restart. No balance or native capture changes.
                    var encoded = EscrowCodec.operation(op);
                    encoded.getCompound("payload").getCompound("fluid").putString("FluidName", "w124:missing");
                    encoded.putString("payload_hash", io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources
                            .signature(encoded.getCompound("payload")));
                    var held = EscrowCodec.operation(encoded); heldPayload = held.payload();
                    isolated.acknowledged(isolated.ledger().replace(List.of(held), isolated.ledger().floors(), isolated.ledger().tombstones()), true);
                    enable(b, true); EscrowCargoSwitches.pauseReconciliationForTests(server, a.root().orElseThrow().device(), false);
                    phase = 12; return;
                }
                enable(b, true);
                check(b.attempt() == RocketCargoStatus.OTHER_TRANSFER_PENDING && coordinator.transferTurns().size() == 1,
                        "Competing device did not retain its bounded next turn");
                EscrowCargoSwitches.pauseReconciliationForTests(server, a.root().orElseThrow().device(), false);
                phase = 3;
            }
            if (phase == 12 || phase == 13) {
                var next = member(b);
                if (next != null) { second = next.id(); enable(b, false); phase = 13; }
                var held = member(a);
                check(held != null && held.id().equals(first) && held.payload().equals(heldPayload)
                        && deviceBefore.equals(a.saveWithFullMetadata()) && rocketBefore.equals(EscrowNativeEnvelope.rocket(rocket)),
                        "R39 changed or lost the held operation or native endpoints");
                if (phase == 12 || next != null || !coordinator.writerIdle() || held.phase() != EscrowOperation.Phase.REPAIR) { return; }
                check(held.reason() == EscrowReason.OTHER_STATE && isolated.ledger().records().size() == 1 && coordinator.operational(),
                        "R39 did not isolate its manual repair");
                check(b.root().orElseThrow().tank().amount() == 4_000 && b.root().orElseThrow().escrow().epoch() == 1
                        && b.root().orElseThrow().escrow().markers().isEmpty() && otherRocket.escrowSection().epoch() == 1
                        && otherRocket.escrowSection().markers().isEmpty() && !coordinator.logicalLocked(otherRocket.assemblyTransactionId().orElseThrow()),
                        "The other native component did not complete C8 while R39 was held");
                var delivered = otherRocket.snapshot().orElseThrow().blocks().stream().map(block -> block.blockEntityPayload().orElse(null))
                        .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                check(io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.fluid(delivered.data()).getAmount() == 1_000,
                        "R39 isolation did not conserve the other component's cargo");
                cleanup(); helper.succeed(); return;
            }
            if (phase == 11) {
                var op = member(a);
                check(op != null && op.id().equals(first) && op.phase() == EscrowOperation.Phase.PREPARED && !coordinator.currentReason(op).manual()
                        && deviceBefore.equals(a.saveWithFullMetadata()) && rocketBefore.equals(EscrowNativeEnvelope.rocket(rocket))
                        && coordinator.logicalLocked(op.guard().rocket()), "Named F1 hold lost PREPARED or changed cargo");
                if (server.getTickCount() - heldSince < 100) { return; }
                cleanup(); helper.succeed(); return;
            }
            if (phase == 3) {
                check(isolated.ledger().records().stream().noneMatch(op -> coordinator.currentReason(op).manual()), "Native cargo entered REPAIR");
                var next = member(b); if (next == null) { return; }
                second = next.id(); enable(b, false);
                check(!first.equals(second) && isolated.ledger().records().stream().noneMatch(op -> op.id().equals(first)), "Queued device overtook L2");
                check(a.root().orElseThrow().escrow().epoch() == 1 && a.root().orElseThrow().escrow().markers().isEmpty(), "First C8 did not prune");
                phase = 4;
            }
            if (phase == 4) {
                check(isolated.ledger().records().stream().noneMatch(op -> op.reason().manual()), "Queued cargo entered REPAIR");
                if (!isolated.ledger().records().isEmpty() || !coordinator.writerIdle()) { return; }
                var state = rocket.escrowSection();
                check(state.epoch() == 2 && state.markers().isEmpty() && state.ledger().equals(isolated.ledger().id()), "Rocket C8 lost epoch or binding");
                for (var device : List.of(a, b)) {
                    var root = device.root().orElseThrow();
                    check(root.escrow().epoch() == 1 && root.escrow().markers().isEmpty() && root.tank().amount() == (direction == 0 ? 4_000 : 1_000),
                            "Device resource delta or C8 is wrong");
                }
                var payload = rocket.snapshot().orElseThrow().blocks().stream().map(block -> block.blockEntityPayload().orElse(null))
                        .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                check(io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.fluid(payload.data()).getAmount()
                        == (direction == 0 ? 2_000 : 3_000), "Cargo conservation failed");
                check(snapshot.equals(rocket.snapshot().orElseThrow().snapshotId()) && !beforeHash.equals(rocket.snapshot().orElseThrow().contentHash())
                        && fuel.equals(rocket.flightData().orElseThrow().fuel().resourceSignature()), "Cargo identity or propulsion changed");
                check(!coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow()) && coordinator.transferTurns().size() == 0,
                        "Retirement retained a lock or stale turn");
                io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketCargoRecoveryGameTestSupport.verify(helper, rocket, beforeSnapshot);
                if (direction++ == 0) { rocket.discard(); a.setRemoved(); b.setRemoved(); phase = 1; return; }
                cleanup(); helper.succeed();
            }
        }
        void check(boolean value, String message) { helper.assertTrue(value, message); }
        EscrowOperation member(RocketFluidCargoBlockEntity device) {
            return isolated.ledger().records().stream().filter(op -> op.device().identity().equals(device.root().orElseThrow().device())).findFirst().orElse(null);
        }
        RocketFluidCargoBlockEntity device(BlockPos at) {
            helper.setBlock(at, direction == 0 ? ModBlocks.ROCKET_FLUID_LOADER.get() : ModBlocks.ROCKET_FLUID_UNLOADER.get());
            var device = (RocketFluidCargoBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
            var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), direction == 0 ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD,
                    direction == 0 ? LoaderTank.of(new FluidStack(Fluids.WATER, 5_000)) : LoaderTank.EMPTY)
                    .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
            var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag); devices.add(device); return device;
        }
        void enable(RocketFluidCargoBlockEntity device, boolean enabled) {
            var root = device.root().orElseThrow(); EscrowCargoSwitches.forTests(helper.getLevel().getServer(),
                    root.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD, root.device(), enabled);
        }
        void cleanup() {
            if (done) { return; } done = true;
            var server = helper.getLevel().getServer(); var runtime = EscrowRuntime.of(server);
            for (var device : devices) {
                enable(device, false); EscrowCargoSwitches.pauseReconciliationForTests(server, device.root().orElseThrow().device(), false); device.setRemoved();
            }
            rockets.forEach(RocketEntity::discard);
            if (namedRecord != null) {
                RocketTransferSavedData.get(server).remove(namedRecord.transferId());
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            }
            if (detached) { isolated = ResourceEscrowSavedData.existing(server); }
            if (isolated != null && isolated != original) { isolated.stop(); }
            if (aside) {
                try { Files.deleteIfExists(file); if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); } }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
            if (detached && !runtime.replaceLedgerForTests(server, original)) { throw new IllegalStateException("W124 ledger restore refused"); }
        }
    }
}
