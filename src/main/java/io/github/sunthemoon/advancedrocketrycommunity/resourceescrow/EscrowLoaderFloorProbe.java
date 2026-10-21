package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * W107C (Root decision R43 item 5): cut R1's gated floor fixture and its observations, beside the W97 matrix. A fresh
 * world gets a ledger with 255 or 256 schema-2 neutral floors and, for each named rocket, a COMMITTED landed-reservation
 * journal record naming it. Neutral floors (no transfer) are the codec's counted legacy form: a named floor whose
 * transfer is absent from the 64-record journal is collected, so they are the only way a native world holds 255 floors.
 * Real loader fills then reserve, refuse or coalesce floor keys through the unmodified admission. The fixture writes,
 * opens per-device gates, observes and pauses; it decides nothing, and recovery is production's alone.
 * <ul>
 * <li>{@code 255_DISTINCT}: rocket R1 (transfer T1) is admitted as the 256th key; R2's distinct key T2 is refused
 * ESCROW_FULL before mutation. Stop at K2, R1's L1 replaced before its callback; each restart refuses R2 again.</li>
 * <li>{@code 255_COALESCED}: a second loader fills R1 with the same key while the first is queued; both are admitted
 * with one reserved key and share one L1. Stop at its K2; restarts must install one floor for both records.</li>
 * <li>{@code 256}: saturated; R2's named key is refused while R1's NONE fill is admitted; stop after its L1 callback,
 * and the fill retires without a floor.</li>
 * </ul>
 */
final class EscrowLoaderFloorProbe {
    static final String PREFIX = "LF_M_R1_FLOORS_";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), A = new BlockPos(66, 100, 64), B = new BlockPos(67, 100, 64);
    /** The distinct-key rocket and its loader, in the same terrain and entity chunk (4,4) as R1. */
    private static final BlockPos EXTRA = new BlockPos(72, 100, 72), C = new BlockPos(74, 100, 72);
    private final MinecraftServer server;
    private final String cut, variant;
    private final boolean recovery, coalesced, saturated, afterL1;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
    private RocketEntity rocket, extra;
    private FluidFuelLoaderBlockEntity other;
    private volatile int step;
    private long began = -1;
    private boolean firstTick, done, pendingChecked;
    /** Read again by the K2 stop on the ledger writer's IO thread. */
    private volatile boolean admitted, refused, secondQueued;

    static boolean selected(String cut) { return cut != null && cut.startsWith(PREFIX); }

    EscrowLoaderFloorProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH),
                "Floor cuts use only per-device gated admission");
        this.server = server; this.cut = cut; this.recovery = recovery; afterL1 = cut.endsWith("_ACK");
        variant = cut.substring(PREFIX.length()).replace("_ACK", "");
        EscrowTags.require(variant.equals("255_DISTINCT") || variant.equals("255_COALESCED") || variant.equals("256"), "Unknown floor cut");
        coalesced = variant.equals("255_COALESCED"); saturated = variant.equals("256");
    }

    /**
     * The fixture's floors: schema-2, with a guard, an identity, an epoch, a signature, a highest sequence, a signature
     * kind and an invariant digest, but no transfer. Floor {@code i} is the retirement of earlier sequence {@code i + 1},
     * so the seeded ledger's watermark is {@code count} and the fixture's own fills follow it. Deterministic, so the
     * oracle can compare them exactly.
     */
    static List<CompoundTag> floors(int count) {
        var floors = new ArrayList<CompoundTag>();
        for (int i = 0; i < count; i++) {
            var floor = EscrowCodec.guard(new EscrowGuard(new UUID(0x107C0L, i), null, 1, RocketNativeResources.hash("w107c-floor-journal-" + i)));
            floor.putInt("schema_version", 2); floor.putUUID("entity_id", new UUID(0x107C1L, i)); floor.putLong("resource_epoch", 1);
            floor.putString("signature", RocketNativeResources.hash("w107c-floor-signature-" + i)); floor.putLong("highest_seq", i + 1L);
            floor.putString("signature_kind", EscrowSignatureKind.FUEL_VECTOR_V1.name());
            floor.putString("invariant_digest", RocketNativeResources.hash("w107c-floor-invariant-" + i));
            EscrowCodec.validateSection(floor, false); floors.add(floor);
        }
        return floors;
    }

    void startTick() {
        if (firstTick) { return; }
        firstTick = true;
        var delegate = EscrowRuntime.of(server).propulsion(server); var data = ResourceEscrowSavedData.existing(server);
        long issued = data == null ? 0 : data.ledger().issuedThrough(); int records = data == null ? 0 : data.ledger().records().size();
        int pending = delegate.authority().pendingCount();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIRST cut={} recovery={} delegate={} issued={} records={} pending={}",
                cut, recovery, delegate.state(), issued, records, pending);
        EscrowTags.require(pending == records, "Restart reservations do not match durable records");
        if (!recovery) { EscrowTags.require(data == null, "Floor cuts need a fresh world"); }
    }

    void checkpoint(String name) {
        if (recovery || done || paused.get() || step < 2) { return; }
        // Keep W107C's K2 on the writer thread. W125's separate _ACK variants stop at the original
        // after-L1-callback C2 boundary, before the first endpoint application.
        if (name.equals("K2") && !saturated && !afterL1) {
            EscrowTags.require(admitted && (coalesced ? secondQueued : refused), "Floor admissions were not observed before the L1 write");
            pause(name); return;
        }
        if (!server.isSameThread()) { return; }
        var data = ResourceEscrowSavedData.existing(server); var runtime = EscrowRuntime.of(server);
        if (name.equals("LOADER_PREPARATIONS_QUEUED")) {
            // Close only the fixture's per-device gates once queued, as the matrix does; no decision or lease changes.
            for (var loader : loaders) {
                if (!data.bind(server).unresolvedNaming(device(loader)).isEmpty()) { runtime.loaderAdmissionForTests(server, device(loader), false); }
            }
            if (!admitted && !data.bind(server).unresolvedNaming(device(loaders.get(0))).isEmpty()) {
                admitted = true; usage("first", data);
                // While R1's key is queued, before its L1 write starts: the 257th key must refuse before any mutation,
                // and the same key on a second loader must coalesce into the one reservation.
                if (other != null) { refuseExtra("start"); refused = true; }
                if (coalesced) { sameKey(data); }
            }
            return;
        }
        if ((saturated || afterL1) && name.equals("C2_SOURCE_BEFORE")) {
            EscrowTags.require(admitted && (coalesced ? secondQueued : refused) && data.ledger().records().size() == (coalesced ? 2 : 1),
                    "Distinct-key refusal was not observed before the L1 acknowledgement");
            pause(name);
        }
    }

    /** The second loader's real attempt for R1's queued key: admitted as the chain's next member, still one reservation. */
    private void sameKey(ResourceEscrowSavedData data) {
        var runtime = EscrowRuntime.of(server); var second = loaders.get(1);
        runtime.loaderAdmissionForTests(server, device(second), true);
        EscrowTags.require(second.attempt() == FluidFuelLoaderStatus.READY, "Same-key request did not enter the fair queue");
        runtime.fills(server).visit();
        EscrowTags.require(second.lastAttempt() == FluidFuelLoaderStatus.PREPARING, "Same-key request did not coalesce: " + second.lastAttempt());
        secondQueued = true; usage("same_key", data);
    }

    void tick() {
        var level = server.overworld();
        if (done || paused.get()) { return; }
        if (!level.areEntitiesLoaded(new ChunkPos(ORIGIN).toLong()) || !level.isPositionEntityTicking(ORIGIN)) { return; }
        if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        if (recovery) { recover(level); return; }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Floor fixture exceeded its bounded wait");
        if (step == 0) { place(level); step = 1; return; }
        var data = ResourceEscrowSavedData.existing(server);
        if (step == 1 && data.ledger().writerGeneration() >= 1 && data.bind(server).writerIdle()
                && (saturated || RocketRuntime.resourceMutationAllowed(rocket)) && (extra == null || RocketRuntime.resourceMutationAllowed(extra))) {
            // The landed records are settled by production's own transfer recovery, as a restart would settle them.
            step = 2; EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loaders.get(0)), true);
            EscrowTags.require(loaders.get(0).attempt() == FluidFuelLoaderStatus.READY, "Fixture admission did not enter the fair queue");
        }
    }

    private void place(ServerLevel level) {
        var owner = UUID.randomUUID();
        rocket = rocket(level, ORIGIN, owner, mono(2_000));
        EscrowTags.require(level.addFreshEntity(rocket), "Native rocket publication failed");
        loaders.add(loader(level, A, owner, tank(ROCKET_FUEL, 5_000), binding(rocket)));
        if (coalesced) { loaders.add(loader(level, B, owner, tank(ROCKET_FUEL, 5_000), binding(rocket))); }
        else {
            extra = rocket(level, EXTRA, owner, mono(2_000));
            EscrowTags.require(level.addFreshEntity(extra), "Distinct-key rocket publication failed");
            other = loader(level, C, owner, tank(ROCKET_FUEL, 5_000), binding(extra));
        }
        var journal = RocketTransferSavedData.get(server);
        if (!saturated) { landed(level, journal, rocket); }
        if (extra != null) { landed(level, journal, extra); }
        journal.flushChecked(server); // The checked writer acknowledges later; opening waits for a clean journal.
        var data = ResourceEscrowSavedData.forTests(server); var coordinator = data.bind(server);
        int seeded = saturated ? 256 : 255;
        data.acknowledged(new EscrowLedger(data.ledger().id(), seeded, data.ledger().writerGeneration(), List.of(), floors(seeded), List.of()), true);
        coordinator.autosave();
        EscrowTags.require(server.saveEverything(false, true, true), "Floor fixture baseline refused");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FLOORS cut={} seeded={} named={} extra_named={} ledger={}",
                cut, seeded, !saturated, extra != null, data.ledger().id());
    }

    /** A COMMITTED landed reservation for {@code target}: its arrival is the live rocket's own snapshot and identity. */
    static void landed(ServerLevel level, RocketTransferSavedData journal, RocketEntity target) {
        landed(level, journal, target, level.dimension().location(), null);
    }

    static void landed(ServerLevel level, RocketTransferSavedData journal, RocketEntity target,
                       net.minecraft.resources.ResourceLocation sourceLevel, RocketPosition sourceOrigin) {
        UUID transfer = UUID.randomUUID(), logical = target.assemblyTransactionId().orElseThrow();
        var to = target.snapshot().orElseThrow(); var origin = to.sourceOrigin();
        var from = to.relocated(UUID.randomUUID(), sourceLevel,
                sourceOrigin == null ? new RocketPosition(origin.x() + 16, origin.y(), origin.z()) : sourceOrigin, 0);
        var fuel = target.flightData().orElseThrow().fuel().withFluidBank(false, ROCKET_FUEL, null, 500);
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                sourceLevel, level.dimension().location(), 500, 0)
                .withPropulsionDebit(new RocketPropulsionDebit(500, 0, fuel.propulsion().orElseThrow().revision(),
                        fuel.resourceSignature(), RocketNativeResources.hash("W107C named floor journal fixture")));
        var flight = RocketFlightData.initial(logical, from.stats().fuelCapacity(), 1, plan.sourceBody(), plan.sourceDimension(), from.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), to.sourceOrigin(), 160);
        journal.put(RocketTransferRecord.create(transfer, logical, target.ownerId().orElseThrow(), UUID.randomUUID(), from, to, flight, arrival, 500, 0)
                .destinationSpawned(target.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED));
    }

    private void usage(String phase, ResourceEscrowSavedData data) {
        var usage = data.bind(server).floorUsage();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FLOOR_ADMIT cut={} phase={} named={} used={} reserved={} total={}",
                cut, phase, !saturated, usage.used(), usage.reserved(), usage.total());
    }

    /** One real attempt of R2's distinct key with its gate open: refused ESCROW_FULL, with nothing changed or reserved. */
    private void refuseExtra(String phase) {
        var runtime = EscrowRuntime.of(server); var coordinator = ResourceEscrowSavedData.existing(server).bind(server);
        var usage = coordinator.floorUsage(); long next = coordinator.nextSequence(); int pending = coordinator.admissionAuthority().pendingCount();
        var source = other.saveWithFullMetadata(); var destination = EscrowNativeEnvelope.rocket(extra);
        runtime.loaderAdmissionForTests(server, device(other), true);
        try {
            EscrowTags.require(other.attempt() == FluidFuelLoaderStatus.READY, "Distinct-key request did not enter the fair queue");
            runtime.fills(server).visit();
            EscrowTags.require(other.lastAttempt() == FluidFuelLoaderStatus.ESCROW_FULL, "Distinct-key request was not refused: " + other.lastAttempt());
        } finally { runtime.loaderAdmissionForTests(server, device(other), false); }
        EscrowTags.require(usage.equals(coordinator.floorUsage()) && next == coordinator.nextSequence()
                && pending == coordinator.admissionAuthority().pendingCount() && source.equals(other.saveWithFullMetadata())
                && destination.equals(EscrowNativeEnvelope.rocket(extra)) && coordinator.unresolvedNaming(device(other)).isEmpty()
                && runtime.fills(server).size() == 0, "Refused distinct key changed resources, issued a sequence or reserved a slot");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FLOOR_REFUSED cut={} phase={} status=ESCROW_FULL used={} reserved={} total={} next={}",
                cut, phase, usage.used(), usage.reserved(), usage.total(), next);
    }

    private void recover(ServerLevel level) {
        if (rocket == null) {
            var found = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(4));
            if (found.isEmpty() || !(level.getBlockEntity(A) instanceof FluidFuelLoaderBlockEntity first)) { return; }
            EscrowTags.require(found.size() == 1, "Native restart duplicated rocket");
            rocket = found.get(0); loaders.add(first);
            if (coalesced) { loaders.add((FluidFuelLoaderBlockEntity) level.getBlockEntity(B)); }
            else {
                var extras = level.getEntitiesOfClass(RocketEntity.class, new AABB(EXTRA).inflate(4));
                EscrowTags.require(extras.size() == 1 && level.getBlockEntity(C) instanceof FluidFuelLoaderBlockEntity, "Missing distinct-key fixture");
                extra = extras.get(0); other = (FluidFuelLoaderBlockEntity) level.getBlockEntity(C);
            }
        }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.operational(), "The restarted floor ledger is unavailable");
        long total = loaders.stream().mapToLong(FluidFuelLoaderEscrowFixtures::amount).sum() + bank(rocket, false) + held(server, rocket, loaders);
        EscrowTags.require(total == loaders.size() * 5_000L && (other == null || amount(other) == 5_000 && bank(extra, false) == 0
                && section(other).epoch() == 0 && section(other).markers().isEmpty() && extra.escrowSection().epoch() == 0),
                "Native restart violated raw control totals");
        if (other != null && !pendingChecked) {
            if (data.ledger().records().isEmpty()) { pendingChecked = true; }
            else if (RocketRuntime.resourceMutationAllowed(extra)) { refuseExtra("restart_pending"); pendingChecked = true; }
        }
        long elapsed = server.getTickCount() - began, fills = coalesced ? 2 : 1;
        if (elapsed >= 100 && settled(data, fills) && (other == null || RocketRuntime.resourceMutationAllowed(extra))) {
            var floor = floorsCheck(data);
            if (other != null) { refuseExtra("restart_settled"); }
            var usage = data.bind(server).floorUsage();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FLOOR_SETTLED cut={} fills={} floors={} named_floor={} used={} reserved={}",
                    cut, fills, data.ledger().floors().size(), floor, usage.used(), usage.reserved());
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_MUTATION_BOUNDARY cut={} retired=true conserved=true mutation_allowed={}",
                    cut, RocketRuntime.resourceMutationAllowed(rocket));
            EscrowLoaderFollowUpProbe.observe(server, cut, rocket, loaders.get(0));
            done = true; result(); return;
        }
        if (elapsed >= 2_300) {
            // Production recovery did not settle within the matrix's bound: report the held state, so both restarts are kept.
            var records = data.ledger().records();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FLOOR_UNSETTLED cut={} ticks={} records={} reasons={} boundary={} mutation_allowed={} floors={}",
                    cut, elapsed, records.size(), records.stream().map(op -> data.bind(server).currentReason(op).name()).toList(),
                    RocketRuntime.escrowReconciliationBoundary(rocket), RocketRuntime.resourceMutationAllowed(rocket), data.ledger().floors().size());
            done = true; result();
        }
    }

    private boolean settled(ResourceEscrowSavedData data, long fills) {
        if (!data.bind(server).writerIdle() || !data.ledger().records().isEmpty() || !rocket.escrowSection().markers().isEmpty()
                || loaders.stream().anyMatch(l -> !section(l).markers().isEmpty())
                || data.ledger().issuedThrough() != (saturated ? 256 : 255) + fills) { return false; }
        EscrowTags.require(bank(rocket, false) == fills * 1_000 && rocket.escrowSection().epoch() == fills
                && data.bind(server).admissionAuthority().pendingCount() == 0
                && loaders.stream().allMatch(l -> amount(l) == 4_000 && section(l).epoch() == 1), "Retirement changed totals, epochs or reservations");
        return true;
    }

    /** The seeded floors survive unchanged; a named fill adds exactly one floor for its key, a NONE fill none. */
    private String floorsCheck(ResourceEscrowSavedData data) {
        var all = data.ledger().floors(); int seeded = saturated ? 256 : 255;
        var neutral = all.stream().filter(floor -> !floor.hasUUID("transfer_id")).toList();
        EscrowTags.require(all.size() == EscrowLimits.FLOORS && neutral.size() == seeded
                && new java.util.HashSet<>(neutral).equals(new java.util.HashSet<>(floors(seeded))), "Seeded floors changed or were collected");
        if (saturated) { return "NONE"; }
        var named = all.stream().filter(floor -> floor.hasUUID("transfer_id")).toList();
        var logical = rocket.assemblyTransactionId().orElseThrow(); long epoch = coalesced ? 2 : 1;
        EscrowTags.require(named.size() == 1 && named.get(0).getUUID("rocket_id").equals(logical)
                && named.get(0).getUUID("entity_id").equals(rocket.getUUID()) && named.get(0).getLong("resource_epoch") == epoch
                && named.get(0).getLong("highest_seq") == seeded + epoch, "The named fill did not install exactly its one floor");
        return "epoch" + epoch + "_seq" + (seeded + epoch);
    }

    private void pause(String hook) {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PAUSED cut={} hook={} first_store=NONE thread={}", cut, hook, Thread.currentThread().getName());
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Floor cut forced stop did not arrive within 30 seconds");
    }

    private void result() { AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_RESULT cut={}", cut); }
}
