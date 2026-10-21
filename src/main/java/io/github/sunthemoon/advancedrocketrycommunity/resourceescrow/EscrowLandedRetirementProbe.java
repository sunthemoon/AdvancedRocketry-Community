package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** R52 process-stop fixtures. Follow-up actions run on disposable copies of a saved restart checkpoint. */
final class EscrowLandedRetirementProbe {
    static final String PREFIX = "LF_M_R52_";
    private static final Map<String, String> CUTS = Map.ofEntries(
            Map.entry("CAPTURE", "R52_BARRIER_STORE_BEFORE"), Map.entry("STORE", "R52_BARRIER_FORCE_BEFORE"),
            Map.entry("REMOVE", "R52_ORDINARY_REMOVAL_BEFORE"), Map.entry("TICKET", "R52_TICKETED"),
            Map.entry("ACK", "ACK"), Map.entry("K2", "K2"), Map.entry("K9", "K9"), Map.entry("MOVED", "MOVED"),
            Map.entry("UNKNOWN", "UNKNOWN"), Map.entry("STALE", "R52_DISCARDED"),
            Map.entry("HELD_COLUMN", "HELD"), Map.entry("HELD_STORAGE", "HELD"));
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), DEVICE = ORIGIN.offset(2, 0, 0);
    private static final RocketPosition OLD = new RocketPosition(65_536, 100, 65_536);
    private final MinecraftServer server;
    private final String cut, boundary, action;
    private final boolean recovery;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final EscrowRetirementObstruction obstruction = new EscrowRetirementObstruction();
    private RocketEntity rocket;
    private FluidFuelLoaderBlockEntity loader;
    private RocketTransferRecord record;
    private UUID launch;
    private boolean first, launchPending, noTransferAtStartup;
    private int step;
    private long began = -1;
    private long demand = -1, lastTicket = Long.MIN_VALUE;
    private int tickets;

    static boolean selected(String cut) { return cut.startsWith(PREFIX) && CUTS.containsKey(cut.substring(PREFIX.length())); }

    EscrowLandedRetirementProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH),
                "R52 fixtures require explicit hooks and per-device admission only");
        this.server = server; this.cut = cut; this.recovery = recovery;
        boundary = CUTS.get(cut.substring(PREFIX.length()));
        action = System.getProperty("advancedrocketrycommunity.retirementFollowUp", "CHECKPOINT");
        EscrowTags.require(List.of("CHECKPOINT", "REFILL", "LAUNCH").contains(action), "Unknown R52 follow-up");
    }

    void startTick() {
        if (first) { return; } first = true;
        var records = RocketTransferSavedData.get(server).entries();
        noTransferAtStartup = records.isEmpty();
        int tickets = records.stream().mapToInt(t -> RocketMarginGameTestSupport.flightTicketColumns(server, t.transferId()).size()).sum();
        int pending = RocketMarginGameTestSupport.ordinaryRetirementEntries(server);
        EscrowTags.require(tickets == 0 && pending == 0, "Flight tickets or pending retirement survived process restart");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_FIRST cut={} recovery={} transfers={} tickets={} pending={}", cut, recovery, records.size(), tickets, pending);
    }

    void checkpoint(String name) {
        if (name.equals("R52_TICKETED")) {
            long now = server.getTickCount();
            EscrowTags.require(lastTicket == Long.MIN_VALUE || now - lastTicket >= 200, "Loader retry reset the retirement backoff");
            lastTicket = now; tickets++;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_TICKET cut={} tick={} count={}", cut, now, tickets);
        }
        if (!recovery && step >= 3 && name.equals(boundary)) { pause(name); }
    }

    void tick() {
        if (paused.get()) { return; }
        if (recovery && noTransferAtStartup) {
            var oldLevel = server.getLevel(CelestialIds.MOON_LEVEL);
            EscrowTags.require(oldLevel.getChunkSource().getChunkNow(OLD.x() >> 4, OLD.z() >> 4) == null,
                    "A NONE-guard restart loaded the retired old launch site");
        }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(ORIGIN)) { return; }
        var journal = RocketTransferSavedData.get(server);
        if (!recovery && boundary.equals("UNKNOWN") && journal.checkedWriteResult().outcome() == RocketTransferSavedData.WriteOutcome.UNKNOWN) {
            EscrowTags.require(bank(rocket, false) == 0 && amount(loader) == 5_000, "UNKNOWN removal admitted fuel");
            pause("UNKNOWN"); return;
        }
        if (!journal.retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "R52 fixture exceeded its bounded wait");
        if (rocket == null && recovery) {
            var found = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(4));
            if (found.isEmpty() || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity source)) { return; }
            EscrowTags.require(found.size() == 1, "R52 destination missing or duplicated");
            rocket = found.get(0); loader = source;
            record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
            step = boundary.equals("HELD") ? 1 : 3;
        }
        if (step == 0) {
            rocket = rocket(level, ORIGIN, UUID.randomUUID(), mono(4_000));
            EscrowTags.require(level.addFreshEntity(rocket), "R52 destination publication failed");
            loader = loader(level, DEVICE, rocket.ownerId().orElseThrow(), tank(ROCKET_FUEL, 5_000), binding(rocket));
            record = EscrowLandedFillFixtures.landed(level, journal, rocket, CelestialIds.MOON_LEVEL.location(), OLD);
            EscrowTags.require(server.saveEverything(false, true, true), "R52 baseline save failed");
            step = 1; return;
        }
        if (step == 1) {
            if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
            RocketMarginGameTestSupport.removeFlightTickets(server, record); step = 2; return;
        }
        if (step == 2) {
            if (server.getLevel(CelestialIds.MOON_LEVEL).areEntitiesLoaded(ChunkPos.asLong(OLD.x() >> 4, OLD.z() >> 4))) { return; }
            EscrowTags.require(journal.find(record.transferId()).isPresent(), "Startup retired T without demand");
            step = 3;
        }
        var logical = rocket.assemblyTransactionId().orElseThrow();
        if (step == 3) {
            if (journal.findByLogicalRocket(logical).isPresent()) {
                if (demand < 0) { demand = server.getTickCount(); }
                if (cut.endsWith("HELD_STORAGE")) { obstruction.install(server, cut, record); }
                if (tickets > 0 && (boundary.equals("HELD") || !recovery && boundary.equals("R52_DISCARDED"))) {
                    if (!obstruction.install(server, cut, record)) { return; }
                    if (boundary.equals("HELD") && server.getTickCount() - demand > 450) {
                        EscrowTags.require(tickets == 3 && RocketRuntime.resourceMutationAllowed(rocket)
                                && amount(loader) == 5_000 && bank(rocket, false) == 0, "Held retirement changed ordinary authority or ticket rate");
                        AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_HELD cut={} elapsed={} tickets={} ordinary_mutation=true",
                                cut, server.getTickCount() - demand, tickets);
                        if (action.equals("REFILL")) {
                            var result = attempt();
                            AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_REFILL cut={} code={} conserved=true", cut, result);
                            EscrowTags.require(result == FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED, "Held retirement admitted escrow fuel");
                        } else if (action.equals("LAUNCH")) {
                            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID());
                            AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_LAUNCH_REQUEST cut={} code={}", cut, result.code());
                            EscrowTags.require(result.code() == RocketFlightRequestCode.INVALID_STATE, "Empty ordinary rocket had an unexpected launch outcome");
                        }
                        EscrowTags.require(server.saveEverything(false, true, true), "Held checkpoint save failed");
                        pause(action.equals("CHECKPOINT") ? "HELD" : action); return;
                    }
                }
                var result = attempt();
                EscrowTags.require(result != FluidFuelLoaderStatus.PREPARING && bank(rocket, false) == 0 && amount(loader) == 5_000,
                        "Named transfer admitted fuel before retirement");
                return;
            }
            if (journal.acknowledgedGuard(logical).isEmpty()) { return; }
            if (!recovery && boundary.equals("ACK")) { pause("ACK"); return; }
            step = 4;
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (step == 4) {
            if (data != null && (!data.bind(server).unresolvedNaming(rocket.getUUID()).isEmpty() || !data.ledger().records().isEmpty() || !data.bind(server).writerIdle()
                    || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty())) { return; }
            if (bank(rocket, false) == 0) { attempt(); return; }
            EscrowTags.require(bank(rocket, false) == 1_000 && amount(loader) == 4_000 && data.ledger().floors().isEmpty()
                    && journal.acknowledgedGuard(logical).orElseThrow().transfer() == null, "R52 NONE fill did not conserve/retire");
            if (action.equals("REFILL")) {
                var status = attempt();
                AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_REFILL cut={} code={} conserved={}", cut, status,
                        bank(rocket, false) == 1_000 && amount(loader) == 4_000);
                EscrowTags.require(status == FluidFuelLoaderStatus.PREPARING, "R52 second fill was refused");
                pause("REFILL"); return;
            }
            if (action.equals("LAUNCH")) { step = 5; }
            else {
                EscrowTags.require(server.saveEverything(false, true, true), "R52 checkpoint save failed");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_CONSERVATION cut={} tank={} bank={} floors={} transfer=NONE",
                        cut, amount(loader), bank(rocket, false), data.ledger().floors().size());
                AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_OLD_SITE cut={} startup_none={} loaded={}", cut, noTransferAtStartup,
                        server.getLevel(CelestialIds.MOON_LEVEL).getChunkSource().getChunkNow(OLD.x() >> 4, OLD.z() >> 4) != null);
                pause("CHECKPOINT"); return;
            }
        }
        if (step == 5) {
            if (rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN) {
                EscrowTags.require(journal.findByLogicalRocket(logical).orElseThrow().nativeTransfer().isPresent(), "Launch was not governed");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_LAUNCH cut={} state=COUNTDOWN governed=true", cut);
                pause("LAUNCH"); return;
            }
            if (launchPending) { return; }
            if (launch == null) { launch = UUID.randomUUID(); }
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_LAUNCH_REQUEST cut={} code={}", cut, result.code());
            EscrowTags.require(result.code() == RocketFlightRequestCode.JOURNAL_PENDING || result.code() == RocketFlightRequestCode.SUCCESS
                    || result.code() == RocketFlightRequestCode.DESTINATION_LOADING, "R52 governed launch refused: " + result.code());
            launchPending = result.code() == RocketFlightRequestCode.JOURNAL_PENDING || result.code() == RocketFlightRequestCode.SUCCESS;
        }
    }

    private FluidFuelLoaderStatus attempt() {
        var runtime = EscrowRuntime.of(server); runtime.loaderAdmissionForTests(server, device(loader), true);
        try { loader.admitFromQueue(); return loader.lastAttempt(); }
        finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
    }

    private void pause(String at) {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_PAUSED cut={} boundary={}", cut, at);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        throw new IllegalStateException("R52 forced stop did not arrive within 30 seconds");
    }
}
