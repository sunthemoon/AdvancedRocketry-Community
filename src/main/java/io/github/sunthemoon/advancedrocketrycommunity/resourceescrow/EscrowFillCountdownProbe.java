package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;

/** Native save/restart during an escrow-filled rocket's governed countdown; no production registration. */
final class EscrowFillCountdownProbe {
    static final String ASSEMBLED = "LF_M_W133F_ASSEMBLED", LANDED = "LF_M_W133F_LANDED";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), DEVICE = ORIGIN.offset(2, 0, 0);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private RocketEntity rocket;
    private FluidFuelLoaderBlockEntity loader;
    private long began = -1;
    private boolean first, pending, filled, paused;

    static boolean selected(String cut) { return ASSEMBLED.equals(cut) || LANDED.equals(cut); }

    EscrowFillCountdownProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && selected(cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Countdown probe needs explicit per-device test gates");
        this.server = server; this.cut = cut; this.recovery = recovery;
    }

    void startTick() {
        if (first) { return; } first = true;
        var records = RocketTransferSavedData.get(server).entries();
        EscrowTags.require(records.stream().noneMatch(r -> EscrowRuntime.of(server).sourceFlights().bound(r.logicalRocketId())),
                "A source-flight binding survived process restart");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_FIRST cut={} recovery={} transfers={} bound=false", cut, recovery, records.size());
    }

    void tick() {
        if (paused) { return; }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(ORIGIN)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Countdown probe exceeded its bounded wait");
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (rocket == null) {
            if (recovery) {
                var found = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(4));
                if (found.isEmpty() || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity source)) { return; }
                EscrowTags.require(found.size() == 1, "Reload duplicated the countdown source");
                rocket = found.get(0); loader = source;
            } else {
                rocket = rocket(level, ORIGIN, UUID.randomUUID(), mono(4_000));
                EscrowTags.require(level.addFreshEntity(rocket), "Countdown source publication failed");
                loader = loader(level, DEVICE, rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 5_000), binding(rocket));
                if (cut.equals(LANDED)) {
                    EscrowLandedFillFixtures.landed(level, journal, rocket, CelestialIds.MOON_LEVEL.location(), new RocketPosition(65_536, 100, 65_536));
                }
                EscrowTags.require(server.saveEverything(false, true, true), "Countdown baseline save failed");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_BASELINE cut={} state={} transfer={}", cut,
                        rocket.flightData().orElseThrow().state(), journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).isPresent());
                return;
            }
        }
        var logical = rocket.assemblyTransactionId().orElseThrow();
        var runtime = EscrowRuntime.of(server);
        if (!recovery && !filled) {
            if (bank(rocket, false) == 0) {
                EscrowTags.require(amount(loader) == 5_000 || journal.findByLogicalRocket(logical).isEmpty(),
                        "First fill consumed fuel before retiring the landed transfer");
                runtime.loaderAdmissionForTests(server, device(loader), true);
                try { loader.admitFromQueue(); }
                finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
                return;
            }
            var data = ResourceEscrowSavedData.existing(server);
            if (!data.bind(server).writerIdle() || !data.ledger().records().isEmpty()
                    || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
            EscrowTags.require(journal.acknowledgedGuard(logical).orElseThrow().transfer() == null
                    && data.ledger().floors().isEmpty() && RocketEscrowGate.idle(rocket), "First fill did not retire with NONE authority");
            EscrowTags.require(rocket.flightData().orElseThrow().state() == expectedSource(), "Fill changed the stationary source state");
            filled = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_FILLED cut={} state={} transfer=NONE tank={} bank={}",
                    cut, expectedSource(), amount(loader), bank(rocket, false));
        }
        EscrowTags.require(bank(rocket, false) == 1_000 && amount(loader) == 4_000, "Countdown changed conserved fuel");
        var flight = rocket.flightData().orElseThrow();
        if (flight.state() == RocketFlightState.COUNTDOWN && runtime.sourceFlights().bound(logical)) {
            var record = journal.findByLogicalRocket(logical).orElseThrow();
            var envelope = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
            var frozen = RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow();
            EscrowTags.require(frozen.state() == expectedSource(), "Reload changed the frozen stationary source");
            EscrowTags.require(EscrowGovernedSourceRecovery.sourceMatches(record, EscrowGovernedEnvelope.capture(rocket), level.getGameTime()),
                    "Reloaded countdown no longer matches the checkpoint");
            EscrowTags.require(server.saveEverything(false, true, true), "Countdown checkpoint save failed");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_SAVED cut={} recovery={} state=COUNTDOWN frozen={} bound=true transfer={} tank={} bank={}",
                    cut, recovery, frozen.state(), record.transferId(), amount(loader), bank(rocket, false));
            pause(); return;
        }
        if (recovery) {
            EscrowTags.require(flight.state() == RocketFlightState.COUNTDOWN, "Restart did not load the saved countdown");
            return;
        }
        if (pending) { return; }
        var request = UUID.randomUUID();
        EscrowLaunchTrace.note(rocket, request, "REQUEST", "MOON");
        var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
        EscrowLaunchTrace.note(rocket, request, "RESULT", result.code().name());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_REQUEST cut={} request={} code={}", cut, request, result.code());
        EscrowTags.require(result.code() == RocketFlightRequestCode.JOURNAL_PENDING || result.code() == RocketFlightRequestCode.SUCCESS
                || result.code() == RocketFlightRequestCode.DESTINATION_LOADING, "Governed countdown refused: " + result.code());
        pending = result.code() != RocketFlightRequestCode.DESTINATION_LOADING;
    }

    private RocketFlightState expectedSource() { return cut.equals(ASSEMBLED) ? RocketFlightState.ASSEMBLED : RocketFlightState.LANDED; }

    private void pause() {
        paused = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_FILL_COUNTDOWN_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        throw new IllegalStateException("Countdown forced stop did not arrive within 30 seconds");
    }
}
