package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W95, leaf B3-E's A1 acceptance with the real loader and real rockets, behind the gated switch: two competing
 * devices forming a mixed-bank prefix through the fair queue; five admitted fills and a refused sixth; every refusal
 * comparing both resource envelopes and the queue and count state; production reconciliation of a held and then
 * reloaded device and rocket; and a missing loader that stays REPAIR without reconstruction.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderEscrowGameTests {
    static final String BATCH = "fluid_fuel_loader_escrow";
    private FluidFuelLoaderEscrowGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void twoCompetingLoadersFillMixedBanksThroughOneRocketPrefix(GameTestHelper helper) {
        new Competing(helper).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void fiveLoadersFillOneRocketAndTheSixthIsRefusedWithNothingChanged(GameTestHelper helper) {
        new FiveAndSixth(helper).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void everyRefusalLeavesBothEnvelopesTheQueueAndTheCountsUnchanged(GameTestHelper helper) {
        new Refusals(helper).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void heldAndReloadedEndpointsRecoverThroughProductionReconciliation(GameTestHelper helper) {
        new Recovery(helper).start();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void aMissingLoaderStaysRepairWithoutReconstruction(GameTestHelper helper) throws Exception {
        new Missing(helper).start();
    }

    /** Shared per-test frame: one paced tick callback, registered once, stepping through a state variable. */
    private abstract static class Scenario {
        final GameTestHelper helper;
        final ServerLevel level;
        final GameTestTickPacer pacer;
        final UUID owner = UUID.randomUUID();
        final List<Area> areas = new ArrayList<>();
        final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
        final List<RocketEntity> rockets = new ArrayList<>();
        int step;
        boolean finished;
        Scenario(GameTestHelper helper, int... chunks) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            for (int i = 0; i < chunks.length; i += 2) { areas.add(new Area(level, chunks[i], chunks[i + 1])); }
        }
        final void start() { areas.forEach(Area::hold); helper.onEachTick(this::tick); }
        private void tick() {
            if (finished) { return; }
            pacer.pace();
            var server = level.getServer();
            if (step == 0 && (!areas.stream().allMatch(Area::ticking) || !RocketTransferSavedData.get(server).retryChecked(server))) {
                areas.forEach(Area::hold); return;
            }
            run();
        }
        abstract void run();
        RocketEntity rocket(BlockPos origin, UUID by, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion propulsion) {
            var rocket = FluidFuelLoaderEscrowFixtures.rocket(level, origin, by, propulsion);
            helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
            rockets.add(rocket); return rocket;
        }
        FluidFuelLoaderBlockEntity loader(BlockPos at, UUID by, LoaderTank tank, LoaderBinding binding) {
            var loader = FluidFuelLoaderEscrowFixtures.loader(level, at, by, tank, binding); loaders.add(loader); return loader;
        }
        void gate(FluidFuelLoaderBlockEntity loader, boolean open) {
            EscrowRuntime.of(level.getServer()).loaderAdmissionForTests(level.getServer(), device(loader), open);
        }
        /** Nothing is created or lost: tanks, banks and the debited-but-uncredited amount escrow holds add up. */
        void conserved(RocketEntity rocket, long total) {
            long sum = held(level.getServer(), rocket, loaders) + bank(rocket, false) + bank(rocket, true);
            for (var loader : loaders) { sum += amount(loader); }
            helper.assertTrue(sum == total, "Conservation failed: " + sum + " of " + total);
        }
        /** A PREPARING device has not been debited yet: "no fuel has moved yet" is true. */
        void honestProgress(FluidFuelLoaderBlockEntity loader, EnumSet<FluidFuelLoaderStatus> seen) {
            var status = loader.displayStatus(); seen.add(status);
            if (status == FluidFuelLoaderStatus.PREPARING) {
                helper.assertTrue(section(loader).markers().isEmpty(), "PREPARING after the device's own debit");
            }
            if (status == FluidFuelLoaderStatus.TRANSFERRING) {
                helper.assertTrue(!section(loader).markers().isEmpty(), "TRANSFERRING before the device's debit");
            }
        }
        boolean settled(RocketEntity rocket) {
            var data = ResourceEscrowSavedData.existing(level.getServer());
            return data != null && data.operational() && data.bind(level.getServer()).unresolvedNaming(rocket.getUUID()).isEmpty()
                    && rocket.escrowSection().markers().isEmpty() && loaders.stream().allMatch(loader -> loader.root().isEmpty()
                    || section(loader).markers().isEmpty());
        }
        void finish() {
            var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            for (var loader : loaders) {
                loader.root().ifPresent(root -> runtime.loaderAdmissionForTests(server, root.device(), false));
                clear(level, loader.getBlockPos());
            }
            rockets.forEach(RocketEntity::discard); areas.forEach(Area::release);
            finished = true; helper.succeed();
        }
    }

    /** Hydrogen into PRIMARY and oxygen into OXIDIZER of one bipropellant rocket, through the fair queue. */
    private static final class Competing extends Scenario {
        RocketEntity rocket;
        FluidFuelLoaderBlockEntity hydrogen, oxygen;
        int longestPrefix;
        final EnumSet<FluidFuelLoaderStatus> seen = EnumSet.noneOf(FluidFuelLoaderStatus.class);
        Competing(GameTestHelper helper) { super(helper, 47_000, 47_000); }
        @Override void run() {
            var server = level.getServer(); var area = areas.get(0);
            if (step == 0) {
                rocket = rocket(area.at(8, 100, 8), owner, bipropellant(4_000, 4_000));
                hydrogen = loader(area.at(6, 100, 8), owner, tank(HYDROGEN, 2_000), binding(rocket));
                oxygen = loader(area.at(10, 100, 8), owner, tank(OXYGEN, 1_000), binding(rocket));
                gate(hydrogen, true); gate(oxygen, true); step = 1; return;
            }
            conserved(rocket, 3_000); honestProgress(hydrogen, seen); honestProgress(oxygen, seen);
            var data = ResourceEscrowSavedData.existing(server);
            if (data != null && data.operational()) {
                longestPrefix = Math.max(longestPrefix, data.bind(server).unresolvedChain(EscrowLoaderAdmission.rocketKey(level, rocket)).size());
            }
            if (amount(hydrogen) != 0 || amount(oxygen) != 0 || bank(rocket, false) != 2_000 || bank(rocket, true) != 1_000 || !settled(rocket)) { return; }
            helper.assertTrue(longestPrefix >= 2, "The two devices never formed one rocket prefix: " + longestPrefix);
            helper.assertTrue(rocket.escrowSection().epoch() == 3 && data.ledger().id().equals(rocket.escrowSection().ledger()),
                    "The rocket's whole-vector epoch did not advance once per fill");
            helper.assertTrue(section(hydrogen).epoch() == 2 && section(oxygen).epoch() == 1
                    && data.ledger().id().equals(section(hydrogen).ledger()), "Each debit did not advance its own device once");
            var fuel = rocket.flightData().orElseThrow().fuel().propulsion().orElseThrow();
            helper.assertTrue(HYDROGEN.equals(fuel.primary().fluidId()) && OXYGEN.equals(fuel.oxidizer().fluidId()), "A bank holds the wrong Fluid");
            helper.assertTrue(seen.containsAll(EnumSet.of(FluidFuelLoaderStatus.PREPARING, FluidFuelLoaderStatus.TRANSFERRING)),
                    "Progress was not shown: " + seen);
            helper.assertTrue(!hydrogen.escrowLocked() && !oxygen.escrowLocked() && RocketEscrowGate.idle(rocket), "Escrow kept a hold");
            finish();
        }
    }

    /** Five devices admitted into one rocket's prefix; the sixth is the five-marker limit's refusal. */
    private static final class FiveAndSixth extends Scenario {
        RocketEntity rocket;
        FiveAndSixth(GameTestHelper helper) { super(helper, 47_008, 47_000); }
        @Override void run() {
            var server = level.getServer(); var area = areas.get(0);
            if (step == 0) {
                rocket = rocket(area.at(8, 100, 8), owner, mono(8_000));
                int[][] offsets = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -2}, {2, 2}};
                for (var offset : offsets) { loader(area.at(8 + offset[0], 100, 8 + offset[1]), owner, tank(ROCKET_FUEL, 1_000), binding(rocket)); }
                step = 1; return;
            }
            if (step == 1) {
                // The gate opens for this one owner-thread action only, so no paced attempt can join the queue meanwhile.
                loaders.forEach(loader -> gate(loader, true));
                var logical = rocket.assemblyTransactionId().orElseThrow();
                for (int i = 0; i < 5; i++) {
                    var loader = loaders.get(i);
                    helper.assertTrue(loader.admitFromQueue() && loader.lastAttempt() == FluidFuelLoaderStatus.PREPARING,
                            "Fill " + i + " was not admitted: " + loader.lastAttempt());
                    helper.assertTrue(counts(server, logical).forRocket() == i + 1 && loader.escrowLocked(), "Fill " + i + " holds no slot");
                }
                var sixth = loaders.get(5);
                var envelope = sixth.saveWithFullMetadata(); var native_ = rocket.saveWithoutId(new CompoundTag());
                var before = counts(server, logical);
                helper.assertTrue(sixth.admitFromQueue(), "The sixth refusal ran no capture");
                helper.assertTrue(sixth.lastAttempt() == FluidFuelLoaderStatus.ESCROW_FULL, "Sixth fill: " + sixth.lastAttempt());
                helper.assertTrue(envelope.equals(sixth.saveWithFullMetadata()) && native_.equals(rocket.saveWithoutId(new CompoundTag()))
                        && before.equals(counts(server, logical)) && !sixth.escrowLocked(), "The refused sixth changed an envelope, the queue or a count");
                loaders.forEach(loader -> gate(loader, false));
                step = 2; return;
            }
            conserved(rocket, 6_000);
            if (bank(rocket, false) != 5_000 || !settled(rocket)) { return; }
            helper.assertTrue(rocket.escrowSection().epoch() == 5, "Five fills did not advance the rocket five times");
            for (int i = 0; i < 5; i++) {
                helper.assertTrue(amount(loaders.get(i)) == 0 && section(loaders.get(i)).epoch() == 1, "Device " + i + " was not debited exactly once");
            }
            var sixth = loaders.get(5);
            helper.assertTrue(amount(sixth) == 1_000 && section(sixth).equals(LoaderEscrowSection.neutral()), "The refused device changed");
            finish();
        }
    }

    /** Each refusal is one synchronous fair-queue visit; both envelopes, the queues and the counts stay exactly equal. */
    private static final class Refusals extends Scenario {
        RocketEntity mono, full, foreign;
        int settling;
        Refusals(GameTestHelper helper) { super(helper, 47_016, 47_000); }
        @Override void run() {
            var server = level.getServer(); var area = areas.get(0);
            if (step == 0) {
                mono = rocket(area.at(8, 100, 8), owner, mono(2_000));
                full = rocket(area.at(2, 100, 2), owner, mono(2_000));
                var flight = full.flightData().orElseThrow();
                full.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ROCKET_FUEL, null, 2_000), level.getGameTime()));
                foreign = FluidFuelLoaderEscrowFixtures.rocket(level, area.at(13, 100, 13), owner, mono(2_000));
                var saved = foreign.saveWithoutId(new CompoundTag());
                saved.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(UUID.randomUUID(), 3, List.of()).encode());
                foreign.load(saved); helper.assertTrue(level.addFreshEntity(foreign), "Foreign-bound rocket publication failed"); rockets.add(foreign);
                step = 1; return;
            }
            if (++settling < 5) { return; }
            var bound = binding(mono);
            refuse("gate closed", loader(area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), bound), mono, FluidFuelLoaderStatus.ADMISSION_DISABLED, false);
            refuse("incompatible", loader(area.at(10, 100, 8), owner, tank(WATER, 1_000), bound), mono, FluidFuelLoaderStatus.INCOMPATIBLE_FLUID, true);
            refuse("empty", loader(area.at(8, 100, 6), owner, LoaderTank.EMPTY, bound), mono, FluidFuelLoaderStatus.EMPTY, true);
            refuse("out of range", loader(area.at(15, 100, 8), owner, tank(ROCKET_FUEL, 1_000), bound), mono, FluidFuelLoaderStatus.OUT_OF_RANGE, true);
            refuse("foreign owner", loader(area.at(8, 100, 10), UUID.randomUUID(), tank(ROCKET_FUEL, 1_000), bound), mono,
                    FluidFuelLoaderStatus.NOT_OWNER, true);
            var stale = new LoaderBinding(bound.logicalRocket(), bound.level(), UUID.randomUUID(), bound.locator(), null);
            refuse("stale binding", loader(area.at(7, 100, 6), owner, tank(ROCKET_FUEL, 1_000), stale), mono, FluidFuelLoaderStatus.STALE_BINDING, true);
            refuse("unbound", loader(area.at(9, 100, 6), owner, tank(ROCKET_FUEL, 1_000), null), mono, FluidFuelLoaderStatus.UNBOUND, true);
            var unloaded = new LoaderBinding(bound.logicalRocket(), bound.level(), bound.physicalRocket(), area.at(8 + 16 * 40, 100, 8), null);
            refuse("unloaded locator", loader(area.at(7, 100, 10), owner, tank(ROCKET_FUEL, 1_000), unloaded), mono,
                    FluidFuelLoaderStatus.WAITING_UNLOADED, true);
            var original = loader(area.at(9, 100, 10), owner, tank(ROCKET_FUEL, 1_000), bound);
            var copy = FluidFuelLoaderEscrowFixtures.loader(level, area.at(6, 100, 6), original.root().orElseThrow().encode()); loaders.add(copy);
            refuse("duplicate device", copy, mono, FluidFuelLoaderStatus.INVALID_DATA, true);
            var future = new LoaderRoot(owner, UUID.randomUUID(), bound, tank(ROCKET_FUEL, 1_000), LoaderEscrowSection.neutral()).encode();
            future.putInt("schema_version", 2);
            var newer = FluidFuelLoaderEscrowFixtures.loader(level, area.at(10, 100, 10), future); loaders.add(newer);
            refuse("future root", newer, mono, FluidFuelLoaderStatus.UNSUPPORTED_DATA, false);
            refuse("bank full", loader(area.at(2, 100, 4), owner, tank(ROCKET_FUEL, 1_000), binding(full)), full, FluidFuelLoaderStatus.BANK_FULL, true);
            refuse("foreign ledger", loader(area.at(13, 100, 11), owner, tank(ROCKET_FUEL, 1_000), binding(foreign)), foreign,
                    FluidFuelLoaderStatus.ESCROW_PENDING, true);
            var rider = new ArmorStand(level, mono.getX(), mono.getY(), mono.getZ());
            helper.assertTrue(level.addFreshEntity(rider) && rider.startRiding(mono, true), "The fixture rider did not mount");
            refuse("ridden rocket", loader(area.at(10, 100, 6), owner, tank(ROCKET_FUEL, 1_000), bound), mono, FluidFuelLoaderStatus.ROCKET_BUSY, true);
            rider.stopRiding(); rider.discard();
            finish();
        }
        void refuse(String label, FluidFuelLoaderBlockEntity loader, RocketEntity rocket, FluidFuelLoaderStatus expected, boolean open) {
            var server = level.getServer(); var logical = rocket.assemblyTransactionId().orElseThrow();
            if (open) { gate(loader, true); }
            var envelope = loader.saveWithFullMetadata(); var native_ = rocket.saveWithoutId(new CompoundTag());
            var before = counts(server, logical); boolean ledger = ResourceEscrowSavedData.existing(server) != null;
            boolean captured = loader.admitFromQueue();
            helper.assertTrue(!captured && loader.lastAttempt() == expected, label + " gave " + loader.lastAttempt() + ", captured " + captured);
            helper.assertTrue(envelope.equals(loader.saveWithFullMetadata()), label + " changed the loader's envelope");
            helper.assertTrue(native_.equals(rocket.saveWithoutId(new CompoundTag())), label + " changed the rocket's envelope");
            helper.assertTrue(before.equals(counts(server, logical)) && ledger == (ResourceEscrowSavedData.existing(server) != null),
                    label + " changed the queue, a count or the ledger's existence");
            helper.assertTrue(!loader.escrowLocked(), label + " locked the device");
            if (open) { gate(loader, false); }
        }
    }

    /**
     * Production reconciliation, not a fixture: the device's chunk stops entity-ticking before admission, so the durable
     * fill waits unapplied; the device and the rocket are then reloaded from their saved data, once while held and once
     * between the debit and the credit, and the scheduler still completes exactly one transfer.
     */
    private static final class Recovery extends Scenario {
        RocketEntity rocket;
        FluidFuelLoaderBlockEntity loader;
        BlockPos at;
        int waited, reloads;
        boolean midway;
        final EnumSet<FluidFuelLoaderStatus> seen = EnumSet.noneOf(FluidFuelLoaderStatus.class);
        Recovery(GameTestHelper helper) { super(helper, 47_024, 47_000, 47_025, 47_000); }
        @Override void run() {
            var server = level.getServer(); var device = areas.get(0); var vehicle = areas.get(1);
            if (step == 0) {
                rocket = rocket(vehicle.at(1, 100, 8), owner, mono(2_000));
                at = device.at(14, 100, 8);
                loader = loader(at, owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                device.release(); step = 1; return;
            }
            conserved(rocket, 1_000); honestProgress(loader, seen);
            var data = ResourceEscrowSavedData.existing(server);
            var named = data == null || !data.operational() ? List.<EscrowOperation>of() : data.bind(server).unresolvedNaming(device(loader));
            if (step == 1) {
                // Only the vehicle's ticket remains: the device's chunk stays loaded and block-ticking, not entity-ticking.
                if (level.isPositionEntityTicking(at) || level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) { return; }
                gate(loader, true); step = 2; return;
            }
            if (step == 2) {
                if (named.isEmpty() || !data.bind(server).durable(named.get(0).id())) { return; }
                // Durable L1, endpoint not entity-ticking: nothing applies, the device stays locked and its port refuses.
                helper.assertTrue(amount(loader) == 1_000 && bank(rocket, false) == 0 && loader.escrowLocked()
                        && port(loader).drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "A held fill moved or unlocked");
                if (++waited < 60) { return; }
                helper.assertTrue(loader.displayStatus() == FluidFuelLoaderStatus.ESCROW_PENDING, "Held fill shows " + loader.displayStatus());
                reload(); device.hold(); step = 3; return;
            }
            if (step == 3) {
                boolean debited = !section(loader).markers().isEmpty();
                boolean credited = !rocket.escrowSection().markers().isEmpty();
                if (debited && !credited && !midway) { midway = true; reload(); return; }
                if (amount(loader) != 0 || bank(rocket, false) != 1_000 || !settled(rocket)) { return; }
                helper.assertTrue(midway && reloads == 2, "The reload between debit and credit was not exercised");
                helper.assertTrue(rocket.escrowSection().epoch() == 1 && section(loader).epoch() == 1, "Recovery repeated or skipped an apply");
                helper.assertTrue(seen.contains(FluidFuelLoaderStatus.ESCROW_PENDING) && !loader.escrowLocked() && RocketEscrowGate.idle(rocket),
                        "Recovery did not release exactly once: " + seen);
                finish();
            }
        }
        /** A restart's view of both endpoints: each native object is rebuilt from its own saved data, identities kept. */
        void reload() {
            var saved = loader.saveWithFullMetadata(); var state = level.getBlockState(at);
            level.removeBlockEntity(at);
            var restored = BlockEntity.loadStatic(at, state, saved.copy());
            helper.assertTrue(restored instanceof FluidFuelLoaderBlockEntity, "The saved loader did not reload");
            level.setBlockEntity(restored);
            loaders.remove(loader); loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at); loaders.add(loader);
            helper.assertTrue(loader == restored && saved.equals(loader.saveWithFullMetadata()), "The reload changed the loader's root");
            var encoded = rocket.saveWithoutId(new CompoundTag()); var identity = rocket.getUUID();
            rockets.remove(rocket); rocket.discard();
            rocket = java.util.Objects.requireNonNull(ModEntities.ROCKET.get().create(level)); rocket.load(encoded);
            helper.assertTrue(rocket.getUUID().equals(identity) && level.addFreshEntity(rocket), "The saved rocket did not rejoin");
            rockets.add(rocket);
            helper.assertTrue(encoded.equals(rocket.saveWithoutId(new CompoundTag())), "The reload changed the rocket's data");
            reloads++;
        }
        static IFluidHandler port(FluidFuelLoaderBlockEntity loader) {
            return loader.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElseThrow();
        }
    }

    /** A detached checked ledger, so the deliberate sticky REPAIR record never enters the server's ledger. */
    private static final class Missing extends Scenario {
        final Path directory;
        final ResourceEscrowSavedData data = ResourceEscrowSavedData.create();
        final EscrowCoordinator coordinator;
        final ArrayList<EscrowReconciliationScheduler.Result> replies = new ArrayList<>();
        RocketEntity rocket;
        FluidFuelLoaderBlockEntity loader;
        EscrowOperation operation;
        boolean acknowledged;
        Missing(GameTestHelper helper) throws Exception {
            super(helper, 47_032, 47_000);
            directory = Files.createTempDirectory("arce-w95-missing-");
            coordinator = new EscrowCoordinator(data, new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0));
        }
        @Override void run() {
            var server = level.getServer(); var area = areas.get(0);
            coordinator.tick(server.getTickCount());
            if (step == 0) {
                rocket = rocket(area.at(8, 100, 8), owner, mono(2_000));
                loader = loader(area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                helper.assertTrue(EscrowLoaderAdmission.admitDetachedForTests(loader, rocket, coordinator, committed -> {
                    helper.assertTrue(committed, "The detached L1 failed"); acknowledged = true;
                }) == EscrowCoordinator.Fill.QUEUED, "The detached fill was refused");
                step = 1; return;
            }
            if (step == 1) { coordinator.commitPreparations(); if (!acknowledged) { return; } step = 2; }
            if (step == 2) {
                operation = data.ledger().records().get(0);
                var at = loader.getBlockPos(); clear(level, at); loaders.remove(loader);
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                helper.assertTrue(!replies.isEmpty() && replies.get(replies.size() - 1).reason() == EscrowReason.MISSING_ENDPOINT
                        && replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "A missing loader was not MISSING_ENDPOINT: " + replies);
                helper.assertTrue(level.getBlockEntity(at) == null && level.getBlockState(at).isAir() && bank(rocket, false) == 0
                        && rocket.escrowSection().markers().isEmpty(), "Recovery reconstructed the loader or credited the rocket");
                coordinator.transition(operation.id(), EscrowReason.MISSING_ENDPOINT); coordinator.persistTransitions();
                helper.assertTrue(coordinator.currentReason(operation) == EscrowReason.MISSING_ENDPOINT
                        && coordinator.locked(operation.destination().key()) && coordinator.locked(operation.source().key()),
                        "The missing loader lost its sticky REPAIR hold");
                coordinator.close();
                try (var files = Files.list(directory)) { for (var file : files.toList()) { Files.delete(file); } Files.delete(directory); }
                catch (java.io.IOException failure) { throw new IllegalStateException("Fixture cleanup failed", failure); }
                finish();
            }
        }
    }
}
