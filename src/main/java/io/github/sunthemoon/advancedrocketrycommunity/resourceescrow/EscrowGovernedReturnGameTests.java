package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native post-F4 return adapter and own-slot proofs; detached authority, not live cancellation or F7 evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedReturnGameTests {
    private EscrowGovernedReturnGameTests() { }

    @GameTest(template = "empty", batch = "escrow_governed_return_adapter", timeoutTicks = 600)
    public static void returnAfterF4RequiresJointNativeSlots(GameTestHelper helper) { new Scenario(helper, false).start(); }

    @GameTest(template = "empty", batch = "escrow_governed_return_adapter", timeoutTicks = 600)
    public static void returnAfterF4ProvesSharedSourceAndDestinationSlot(GameTestHelper helper) { new Scenario(helper, true).start(); }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final BlockPos position;
        private final BlockPos destination;
        private final RocketTransferSavedData journal = new RocketTransferSavedData();
        private final List<EscrowBarrier.Proof> proofs = new ArrayList<>();
        private List<EscrowEndpoint> slots;
        private EscrowFlightSource frozen;
        private RocketEntity rocket;
        private RocketTransferRecord record;
        private EscrowLedger root;
        private EscrowGovernedDispatch.Authority authority;
        private boolean pending;
        private boolean done;

        Scenario(GameTestHelper helper, boolean shared) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(shared ? 590_136 : 590_008, 100, 590_008);
            destination = position.offset(shared ? 4 : 32, 0, 0);
        }
        void start() {
            for (var chunk : chunks()) {
                level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
            }
            helper.onEachTick(this::tick);
        }
        private List<ChunkPos> chunks() { return List.of(new ChunkPos(position), new ChunkPos(destination)).stream().distinct().toList(); }
        private boolean loaded() {
            return List.of(position, destination).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                    && level.isPositionEntityTicking(pos));
        }
        private void tick() {
            if (done) { return; }
            pacer.pace(); if (!loaded()) { return; }
            if (rocket == null) { prepare(); return; }
            helper.assertTrue(frozen.observe(), "Native source changed while return proofs were pending");
            if (pending) { return; }
            if (proofs.stream().anyMatch(proof -> !proof.valid())) { proofs.forEach(EscrowBarrier.Proof::release); proofs.clear(); }
            if (proofs.size() < slots.size()) {
                boolean refused = false;
                try { permit(); } catch (IllegalArgumentException expected) { refused = true; }
                helper.assertTrue(refused, "Partial/absent storage proof issued a post-F4 return");
                var slot = slots.get(proofs.size()); var runtime = EscrowRuntime.of(level.getServer());
                pending = true;
                boolean started = runtime.barrier().begin(runtime.store(level), slot,
                        new EscrowPrefix.State(root.id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                        EscrowGovernedDestinationDisk.validator(record, slot, frozen.envelope), level.getServer().getTickCount(), result -> {
                            pending = false;
                            if (result.confirmed()) { proofs.add(result.proof()); }
                            else {
                                helper.assertTrue(result.reason() == EscrowReason.SUPERSEDING_SAVE
                                        || result.reason().retryableProofFailure(), "Native return proof refused: " + result.reason());
                                proofs.forEach(EscrowBarrier.Proof::release); proofs.clear();
                            }
                        });
                if (!started) { pending = false; }
                return;
            }
            var before = EscrowNativeEnvelope.rocket(rocket);
            rocket.returnGovernedSource(permit());
            var after = EscrowNativeEnvelope.rocket(rocket);
            helper.assertTrue(EscrowGovernedReturnAction.returned(record, after), "Returned source differs from its complete permitted envelope");
            var comparison = after.copy(); comparison.put("flight", before.get("flight").copy()); comparison.put("pos", before.get("pos").copy());
            helper.assertTrue(before.equals(comparison) && rocket.escrowSection().epoch() == 7
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == 1000
                    && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty(), "Return changed native credit or identity");
            helper.assertTrue(journal.acknowledgedCurrent() && journal.find(record.transferId()).orElseThrow().equals(record)
                    && root.armedFlights().get(0).outcome() == EscrowFlightTables.Outcome.ARMED && !RocketEscrowGate.idle(rocket),
                    "Return fabricated cancellation, journal cleanup or ordinary idle authority");
            proofs.forEach(EscrowBarrier.Proof::release); proofs.clear();
            rocket.discard(); journal.stop();
            for (var chunk : chunks()) { level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); }
            done = true; helper.succeed();
        }
        private EscrowGovernedReturnAction.Permit permit() {
            return EscrowGovernedReturnAction.issue(authority, frozen.envelope, level.getGameTime(), level.getGameTime(),
                    () -> EscrowNativeEnvelope.rocket(rocket), () -> loaded() && frozen.observe(),
                    () -> proofs.size() == slots.size() && proofs.stream().allMatch(EscrowBarrier.Proof::valid));
        }
        private void prepare() {
            root = EscrowLedger.empty(UUID.randomUUID());
            rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
            long now = level.getGameTime(); var base = rocket.flightData().orElseThrow();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(base.withFuel(fuel, now));
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(root.id(), 7, List.of()).encode());
            rocket.load(parent); helper.assertTrue(level.addFreshEntity(rocket), "Return fixture source was not published");
            var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("governed-return-adapter")
                    .resolve(rocket.getUUID().toString()).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            try { Files.createDirectories(file.getParent()); } catch (IOException failure) { throw new UncheckedIOException(failure); }
            RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("post-F4 return adapter")));
            var guard = journal.acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).orElseThrow();
            var launch = EscrowGovernedLaunch.prepare(root, EscrowNativeEnvelope.rocket(rocket), from,
                    rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now), to, UUID.randomUUID(),
                    new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
            root = launch.preflight(root, 1).generation(1);
            record = launch.record(root.armedFlights().get(0));
            record = record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination());
            journal.put(record); RocketJournalGameTestSupport.confirmDetached(helper, journal, file);
            authority = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record);
            helper.assertTrue(authority != null, "Detached F4 fixture has no checked authority");
            frozen = new EscrowFlightSource(rocket);
            var sourceSlot = EscrowGovernedDestinationDisk.endpoint(record, false); var destinationSlot = EscrowGovernedDestinationDisk.endpoint(record, true);
            slots = EscrowBarrier.sameSlot(sourceSlot, destinationSlot) ? List.of(sourceSlot) : List.of(sourceSlot, destinationSlot);
        }
    }
}
