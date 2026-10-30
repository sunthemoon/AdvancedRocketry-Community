package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Dedicated native loader-funded flight fixture. */
final class EscrowLoaderS6FlightProbe {
    private static final List<String> CASES = List.of("LF_S6_F1_STORE_BEFORE", "LF_S6_F1_STORE_AFTER",
            "LF_S6_F1_FORCE_BEFORE", "LF_S6_F1_FORCE_AFTER", "LF_S6_F1_READBACK_BEFORE", "LF_S6_F1_READBACK_AFTER",
            "LF_S6_F1_ARM_BEFORE", "LF_S6_F1_ARM_REPLACED", "LF_S6_F1_ARM_ACK",
            "LF_S6_F2_BEFORE", "LF_S6_F2_REPLACED", "LF_S6_F2_ACK");
    private static final BlockPos SOURCE = new BlockPos(64, 100, 64), DEVICE = SOURCE.offset(2, 0, 0),
            DESTINATION = new BlockPos(80, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private RocketEntity rocket;
    private FluidFuelLoaderBlockEntity loader;
    private UUID fill;
    private boolean launching, discarded, done;
    private volatile boolean proof, arm;
    private long began = -1;

    static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }
    static boolean boundary(String cut, String point, boolean proof, boolean arm) {
        if (!selected(true, cut)) { return false; }
        if (cut.startsWith("LF_S6_F1_") && !cut.startsWith("LF_S6_F1_ARM_")) {
            return proof && point.equals("BARRIER_" + cut.substring("LF_S6_F1_".length()));
        }
        return arm && switch (cut) {
            case "LF_S6_F1_ARM_BEFORE" -> point.equals("K8");
            case "LF_S6_F1_ARM_REPLACED" -> point.equals("K9");
            case "LF_S6_F1_ARM_ACK" -> point.equals("F1_ARM_ACK");
            case "LF_S6_F2_ACK" -> point.equals("F2_PREPARED_ACK");
            default -> false;
        };
    }

    EscrowLoaderS6FlightProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "S6 flight requires exact per-device test gates");
        this.server = server; this.cut = cut; this.recovery = recovery;
        for (var pos : List.of(SOURCE, DESTINATION)) {
            var chunk = new ChunkPos(pos);
            server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String point) {
        if (recovery || done) { return; }
        if (!launching) {
            if (point.equals("C2_DESTINATION_AFTER")) {
                var rows = ResourceEscrowSavedData.existing(server).ledger().records();
                EscrowTags.require(rows.size() == 1, "S6 flight setup admitted more than one fill");
                fill = rows.get(0).id();
            }
            return;
        }
        if (point.equals("F1_STORE_BEFORE")) { proof = true; }
        if (point.equals("F1_ARM_CAPTURE")) { proof = false; arm = true; }
        if (boundary(cut, point, proof, arm)) { EscrowLoaderS6JournalProbe.pause(cut, point); }
    }

    void tick() {
        if (done || !loaded()) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 flight exceeded the existing fixture bound");
        var level = server.overworld(); var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (recovery) { recover(journal); return; }
        EscrowTags.require(!discarded, "S6 loader-funded launch discarded its callback");
        if (rocket == null) {
            rocket = rocket(level, SOURCE, UUID.randomUUID(), mono(2_000));
            EscrowTags.require(level.addFreshEntity(rocket), "S6 flight source publication failed");
            loader = loader(level, DEVICE, rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 5_000), binding(rocket));
            EscrowTags.require(server.saveEverything(false, true, true), "S6 pre-fill baseline save failed");
            return;
        }
        if (launching) { return; }
        var runtime = EscrowRuntime.of(server);
        if (bank(rocket, false) == 0) {
            runtime.loaderAdmissionForTests(server, device(loader), true);
            try { loader.admitFromQueue(); }
            finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
            return;
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (!data.bind(server).writerIdle() || !data.ledger().records().isEmpty()
                || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
        EscrowTags.require(fill != null && amount(loader) == 4_000 && bank(rocket, false) == 1_000
                && section(loader).epoch() == 1 && rocket.escrowSection().epoch() == 1 && RocketEscrowGate.idle(rocket)
                && data.ledger().issuedThrough() == 1 && data.ledger().issuedLaunchSerial() == 0,
                "S6 flight did not retire exactly one real fill");
        var base = rocket.flightData().orElseThrow(); var from = rocket.snapshot().orElseThrow();
        long now = level.getGameTime();
        var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), now);
        var fuel = base.fuel();
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W211 S6 real-loader flight")));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_FILLED cut={} operation={} source={} logical={} owner={} ledger={} source_digest={} tank=4000 bank=1000 epoch=1",
                cut, fill, rocket.getUUID(), base.logicalRocketId(), rocket.ownerId().orElseThrow(), data.ledger().id(),
                EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(rocket)));
        launching = true;
        EscrowTags.require(EscrowGovernedPreparation.begin(rocket, base.withPlan(plan).startCountdown(now), to,
                record -> loaded(), record -> { }, () -> discarded = true), "S6 loader-funded F1 refused");
    }

    private void recover(RocketTransferSavedData journal) {
        var level = server.overworld();
        if (!(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity value)) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.ledger().issuedThrough() == 1 && amount(value) == 4_000
                && section(value).epoch() == 1, "S6 flight restart changed its loader");
        if (server.getTickCount() - began < 100 || !data.bind(server).writerIdle() || !journal.actionAuthorityAvailable()
                || !journal.entries().isEmpty() || !data.ledger().armedFlights().isEmpty()
                || !data.ledger().records().isEmpty() || !section(value).markers().isEmpty()) { return; }
        var rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(63, 0, 63, 83, 300, 67));
        if (rockets.isEmpty()) { return; }
        EscrowTags.require(rockets.size() == 1, "S6 native image is not explained by its durable record: extra survivor");
        var survivor = rockets.get(0); var flight = survivor.flightData().orElseThrow();
        boolean forward = cut.equals("LF_S6_F2_REPLACED") || cut.equals("LF_S6_F2_ACK");
        EscrowTags.require(bank(survivor, false) == (forward ? 500 : 1_000) && bank(survivor, true) == 0
                && survivor.escrowSection().epoch() == (forward ? 0 : 1) && survivor.escrowSection().markers().isEmpty()
                && flight.fuel().committedDebits().size() == (forward ? 1 : 0) && flight.passengers().assignments().isEmpty()
                && flight.state() == (forward ? RocketFlightState.LANDED : RocketFlightState.ASSEMBLED)
                && survivor.getX() == (forward ? 80.5 : 64.5) && survivor.getY() == 100 && survivor.getZ() == 64.5
                && RocketEscrowGate.idle(survivor), "S6 flight restart changed its authorized survivor");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_RESULT cut={} source={} logical={} owner={} ledger={} bank={} epoch={} debits={} journal=0 armed=0",
                cut, survivor.getUUID(), flight.logicalRocketId(), survivor.ownerId().orElseThrow(), data.ledger().id(),
                bank(survivor, false), survivor.escrowSection().epoch(), flight.fuel().committedDebits().size());
        done = true;
    }

    private boolean loaded() {
        return List.of(SOURCE, DESTINATION).stream().allMatch(pos -> server.overworld().areEntitiesLoaded(new ChunkPos(pos).toLong())
                && server.overworld().isPositionEntityTicking(pos));
    }
}
