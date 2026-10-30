package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;

/** S6 E5: only fixture placement/tickets/admission and read-only observations; production owns every recovery step. */
final class EscrowLoaderS6UnloadedProbe {
    static final String CASE = "LF_S6_E5_UNLOADED";
    private static final BlockPos FAR = new BlockPos(2048, 100, 2048), HEALTHY = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final boolean recovery;
    private final boolean diagnostic;
    private final EscrowLoaderS6UnloadObservation unloadObservation = new EscrowLoaderS6UnloadObservation();
    private final Area area;
    private RocketEntity farRocket, healthyRocket;
    private FluidFuelLoaderBlockEntity farLoader, healthyLoader;
    private boolean injected, released, queued, done;
    private long began = -1, unloadedAt = -1;
    private UUID heldId;

    EscrowLoaderS6UnloadedProbe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(!Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "S6 uses per-device admission only");
        this.server = server; this.recovery = recovery;
        diagnostic = !recovery && Boolean.getBoolean("advancedrocketrycommunity.escrowS6UnloadDiagnostic");
        area = new Area(server.overworld(), 128, 128);
        area.hold(); // Fixed disposable fixture area, not a client-supplied endpoint.
    }

    void checkpoint(String name) {
        if (recovery || done || farLoader == null) { return; }
        if (name.equals("LOADER_PREPARATIONS_QUEUED")) {
            var data = ResourceEscrowSavedData.existing(server);
            for (var loader : List.of(farLoader, healthyLoader)) {
                if (!data.bind(server).unresolvedNaming(device(loader)).isEmpty()) {
                    EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), false);
                }
            }
        }
        if (!injected && name.equals("C4_SOURCE_SELECTED")) {
            injected = true;
            int size = EscrowCargoContentionProbe.injectCacheClose(true, server.overworld().getChunkSource().chunkMap.worker);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FAIR_CACHE regions={}", size);
        }
    }

    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 fairness exceeded the existing fixture bound");
        var level = server.overworld(); var runtime = EscrowRuntime.of(server);
        if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server).retryChecked(server)) { return; }
        if (farRocket == null) {
            if (!area.ticking() || !level.isPositionEntityTicking(HEALTHY)) { return; }
            if (recovery) {
                var far = level.getEntitiesOfClass(RocketEntity.class, new AABB(FAR).inflate(8));
                var healthy = level.getEntitiesOfClass(RocketEntity.class, new AABB(HEALTHY).inflate(8));
                if (far.isEmpty() || healthy.isEmpty()) { return; }
                EscrowTags.require(far.size() == 1 && healthy.size() == 1, "S6 fairness duplicated a native rocket");
                farRocket = far.get(0); healthyRocket = healthy.get(0);
                farLoader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(FAR.offset(2, 0, 0));
                healthyLoader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(HEALTHY.offset(2, 0, 0));
            } else {
                var owner = UUID.randomUUID();
                farRocket = rocket(level, FAR, owner, mono(2_000));
                healthyRocket = rocket(level, HEALTHY, owner, mono(2_000));
                EscrowTags.require(level.addFreshEntity(farRocket) && level.addFreshEntity(healthyRocket), "S6 fairness publication refused");
                farLoader = loader(level, FAR.offset(2, 0, 0), owner, tank(ROCKET_FUEL, 5_000), binding(farRocket));
                healthyLoader = loader(level, HEALTHY.offset(2, 0, 0), owner, tank(ROCKET_FUEL, 5_000), binding(healthyRocket));
                EscrowTags.require(server.saveEverything(false, true, true), "S6 fairness pre-admission baseline refused");
                if (diagnostic) { observeUnload(); }
                queue(farLoader); return;
            }
        }
        if (diagnostic) { observeUnload(); }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return; }
        var coordinator = data.bind(server);
        if (recovery) {
            boolean farComplete = checkPair(farLoader, farRocket), healthyComplete = checkPair(healthyLoader, healthyRocket);
            if (server.getTickCount() - began < 100 || !coordinator.writerIdle() || !data.ledger().records().isEmpty()
                    || !farComplete || !healthyComplete) { return; }
            EscrowTags.require(data.ledger().issuedThrough() == 2 && coordinator.admissionAuthority().pendingCount() == 0,
                    "S6 fairness restart changed sequence or reservations");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FAIR_RESULT cut={} pairs=2 totals=5000,5000 records=0 markers=0 epochs=1,1,1,1",
                    CASE);
            done = true; area.release(); return;
        }
        if (!released && injected && runtime.barrier().ioFailures() > 0) {
            EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().issuedThrough() == 1,
                    "S6 fairness missing first durable record");
            heldId = data.ledger().records().get(0).id();
            area.release(); released = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FAIR_RELEASE record={} io_failures={}", heldId, runtime.barrier().ioFailures());
        }
        if (!released) { return; }
        EscrowTags.require(data.ledger().records().stream().anyMatch(op -> op.id().equals(heldId)),
                "S6 fairness selection lost its record before unloaded competition");
        if (diagnostic) {
            if (unloadObservation.windowComplete(server.getTickCount())) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_UNLOAD_DIAGNOSTIC_END cut={} tick={} window={} healthy_queued=false",
                        CASE, server.getTickCount(), EscrowLoaderS6UnloadObservation.WINDOW);
                try { TimeUnit.SECONDS.sleep(30); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                throw new IllegalStateException("S6 diagnostic forced stop did not arrive within 30 seconds");
            }
            // Diagnosis deliberately does not enter or pass the ordinary fairness assertions/admission below.
            return;
        }
        boolean unloaded = unloadObservation.awaitComplete(server.getTickCount(),
                level.getChunkSource().getChunkNow(128, 128) == null, !level.areEntitiesLoaded(area.chunk.toLong()),
                farLoader.isRemoved(), farRocket.isRemoved());
        if (!unloaded) {
            EscrowTags.require(unloadedAt < 0, "S6 fairness forced the unloaded work back in"); return;
        }
        EscrowTags.require(farLoader.isRemoved() && farRocket.isRemoved(), "S6 fairness slot unloaded without endpoint removal");
        if (unloadedAt < 0) {
            unloadedAt = server.getTickCount();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FAIR_UNLOADED tick={} first_chunk_absent={} first_entities_unloaded={}"
                            + " first_loader_removed={} first_rocket_removed={} removal_bound={}", unloadedAt,
                    unloadObservation.chunkAbsentAt(), unloadObservation.entitiesUnloadedAt(),
                    unloadObservation.loaderRemovedAt(), unloadObservation.rocketRemovedAt(), EscrowLoaderS6UnloadObservation.REMOVAL_WAIT_TICKS);
        }
        if (!queued) { queued = true; queue(healthyLoader); }
        if (server.getTickCount() - unloadedAt < 20 || !coordinator.writerIdle()
                || data.ledger().records().size() != 1 || data.ledger().issuedThrough() != 2) { return; }
        if (!checkPair(healthyLoader, healthyRocket)) { return; }
        EscrowTags.require(coordinator.admissionAuthority().pendingCount() == 1, "S6 fairness released the unloaded reservation");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FAIR cut={} unloaded_ticks={} records=1 issued=2 pending=1 healthy_tank=4000 healthy_bank=1000 healthy_markers=0",
                CASE, server.getTickCount() - unloadedAt);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAUSED cut={} hook=UNLOADED_COMPETITOR_RETIRED", CASE);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 fairness forced stop did not arrive within 30 seconds");
    }

    private void observeUnload() {
        var level = server.overworld();
        var chunk = level.getChunkSource().getChunkNow(128, 128);
        boolean chunkAbsent = chunk == null, entitiesUnloaded = !level.areEntitiesLoaded(area.chunk.toLong());
        // Inspect the existing map only: Level.getBlockEntity could load this absent chunk.
        var loaderHeld = chunkAbsent ? null : chunk.getBlockEntities().get(farLoader.getBlockPos());
        var rocketHeld = level.getEntity(farRocket.getUUID());
        var rocketIdHeld = level.getEntity(farRocket.getId());
        long tick = server.getTickCount();
        unloadObservation.observe(tick, chunkAbsent, entitiesUnloaded, farLoader.isRemoved(), farRocket.isRemoved());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_UNLOAD tick={} released={} chunk_absent={} entities_unloaded={}"
                        + " loader_ref={} loader_pos={},{},{} loader_device={} loader_removed={} loader_reason=NOT_APPLICABLE"
                        + " loader_holder={} loader_held_ref={} rocket_ref={} rocket_uuid={} rocket_id={} rocket_removed={}"
                        + " rocket_reason={} rocket_holder={} rocket_held_ref={} rocket_id_holder={} rocket_id_held_ref={}"
                        + " first_chunk_absent={} first_entities_unloaded={} first_loader_removed={} first_rocket_removed={}",
                tick, released, chunkAbsent, entitiesUnloaded, objectToken(farLoader),
                farLoader.getBlockPos().getX(), farLoader.getBlockPos().getY(), farLoader.getBlockPos().getZ(), device(farLoader),
                farLoader.isRemoved(), chunkAbsent ? "CHUNK_ABSENT" : holder(farLoader, loaderHeld), objectToken(loaderHeld),
                objectToken(farRocket), farRocket.getUUID(), farRocket.getId(), farRocket.isRemoved(), farRocket.getRemovalReason(),
                holder(farRocket, rocketHeld), objectToken(rocketHeld), holder(farRocket, rocketIdHeld), objectToken(rocketIdHeld),
                unloadObservation.chunkAbsentAt(), unloadObservation.entitiesUnloadedAt(),
                unloadObservation.loaderRemovedAt(), unloadObservation.rocketRemovedAt());
    }

    private static String holder(Object retained, Object held) {
        return held == null ? "ABSENT" : held == retained ? "SAME" : "OTHER";
    }

    private static String objectToken(Object object) {
        return object == null ? "NONE" : object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    private void queue(FluidFuelLoaderBlockEntity loader) {
        EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), true);
        EscrowTags.require(loader.attempt() == FluidFuelLoaderStatus.READY, "S6 fairness admission did not enter the queue");
    }

    private boolean checkPair(FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
        var data = ResourceEscrowSavedData.existing(server); var root = loader.root().orElseThrow();
        var source = section(loader); var destination = rocket.escrowSection();
        EscrowTags.require(root.owner().equals(rocket.ownerId().orElseThrow()) && root.binding().equals(binding(rocket))
                && root.tank().fluid().equals(ROCKET_FUEL) && root.tank().tag() == null && bank(rocket, true) == 0,
                "S6 fairness changed owner, binding or fluid");
        var records = data.ledger().records().stream().filter(op -> op.source().identity().equals(device(loader))).toList();
        EscrowTags.require(records.size() <= 1, "S6 fairness has multiple records for one loader");
        Long q = null;
        boolean sourceAfter = source.epoch() == 1, destinationAfter = destination.epoch() == 1;
        if (!records.isEmpty()) {
            var op = records.get(0);
            EscrowTags.require(op.destination().identity().equals(rocket.getUUID()) && op.owner().equals(root.owner())
                    && op.payload().equals(tank(ROCKET_FUEL, 1_000).encode()), "S6 fairness changed durable payload or identity");
            q = (long) op.payload().getInt("Amount");
            sourceAfter = EscrowLoaderS6Recovery.after(data.ledger().id(), op, op.source(), "SOURCE", source.ledger(),
                    source.epoch(), root.signature(), source.markers());
            destinationAfter = EscrowLoaderS6Recovery.after(data.ledger().id(), op, op.destination(), "DESTINATION", destination.ledger(),
                    destination.epoch(), rocket.flightData().orElseThrow().fuel().resourceSignature(), destination.markers());
        }
        if (q == null && (!source.markers().isEmpty() || !destination.markers().isEmpty())) {
            EscrowTags.require(amount(loader) == 4_000 && bank(rocket, false) == 1_000
                    && sourceAfter && destinationAfter, "S6 retired cleanup changed the native total");
            // No held amount is claimed for an absent record. C8 cleanup is still required for completion.
            return false;
        }
        EscrowLoaderS6Accounting.classify(true, amount(loader), bank(rocket, false), q,
                sourceAfter, destinationAfter, source.markers().size() + destination.markers().size());
        return q == null;
    }
}
