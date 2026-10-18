package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** W97's observation-only cuts. Fixture setup uses the existing per-device admission gate; recovery does not. */
final class EscrowLoaderCutProbe {
    static final String PREFIX = "LF_M_";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64);
    private static final BlockPos A = new BlockPos(66, 100, 64);
    private static final BlockPos B = new BlockPos(67, 100, 64);
    private final MinecraftServer server;
    private final String cut, boundary;
    private final boolean recovery, bi, creation;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
    private RocketEntity rocket;
    private boolean firstTick, done, retrying;
    private volatile boolean armed;
    private volatile String firstStore = "NONE";
    private int step, stores;
    private long began = -1;

    static boolean selected(String cut) { return cut != null && cut.startsWith(PREFIX); }

    EscrowLoaderCutProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Matrix uses only per-device gated admission");
        this.server = server; this.cut = cut; this.recovery = recovery;
        bi = cut.endsWith("_BI"); boundary = cut.substring(PREFIX.length()).replace("_BI", "");
        creation = boundary.startsWith("N");
        EscrowTags.require(boundary.matches("N[01]|N3_(FULL|PREFLIGHT|NOT_LOADED)|K([0-9]|10)|K2_ACK|K3_DEBIT|K6_FORCED|K10_ACK")
                && (!bi || boundary.matches("K[269]")), "Unknown loader cut");
    }

    void startTick() {
        if (firstTick) { return; }
        firstTick = true;
        var delegate = EscrowRuntime.of(server).propulsion(server);
        var data = ResourceEscrowSavedData.existing(server);
        long issued = data == null ? 0 : data.ledger().issuedThrough();
        int records = data == null ? 0 : data.ledger().records().size();
        int pending = delegate.authority().pendingCount();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIRST cut={} recovery={} delegate={} issued={} records={} pending={}",
                cut, recovery, delegate.state(), issued, records, pending);
        EscrowTags.require(pending == records, "Restart reservations do not match durable records");
        if (!recovery) { EscrowTags.require(data == null, "Matrix needs a fresh world"); }
    }

    void checkpoint(String name) {
        if (done || paused.get() || !recovery && step == 0) { return; }
        if (name.equals("LOADER_PREPARATIONS_QUEUED")) {
            // Close only the fixture's admission gate, not any transaction decision or lease.
            var data = ResourceEscrowSavedData.existing(server);
            for (var loader : loaders) {
                if (!data.bind(server).unresolvedNaming(device(loader)).isEmpty()) {
                    EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), false);
                }
            }
            if (recovery) { return; }
            if (armed && (boundary.equals("N0") || boundary.equals("K0") && data.ledger().writerGeneration() > 0)) {
                EscrowTags.require(loader().escrowProgress() == FluidFuelLoaderStatus.PREPARING, "Pending baseline/L1 is not PREPARING");
                observe("pending"); pause(name); return;
            }
        }
        if (recovery || !armed) { return; }
        if (server.isSameThread() && (name.startsWith("C2_") || name.equals("C6_JOINT_READBACK")
                || name.equals("C7_ACKNOWLEDGED") || name.equals("C8_RELEASED"))) { observe(name); }
        if (name.equals("C4_SOURCE_SELECTED") || name.equals("C4_DESTINATION_SELECTED")) {
            stores++;
            if (stores == 1) { firstStore = name.equals("C4_SOURCE_SELECTED") ? "SOURCE" : "DESTINATION"; }
        }
        boolean match = switch (boundary) {
            case "N1" -> name.equals("BASELINE_REPLACED");
            case "K1", "K2", "K8", "K9" -> name.equals(boundary);
            case "K2_ACK" -> name.equals("C2_SOURCE_BEFORE");
            case "K3_DEBIT" -> name.equals("C2_SOURCE_AFTER");
            case "K3" -> name.equals("C2_DESTINATION_AFTER");
            // Observe whichever side the unmodified driver actually stores first. Never reorder its map or proofs.
            case "K4", "K5" -> stores == 1 && name.equals("BARRIER_FORCE_AFTER");
            case "K6" -> stores == 2 && name.equals("BARRIER_FORCE_BEFORE");
            case "K6_FORCED" -> stores == 2 && name.equals("BARRIER_FORCE_AFTER");
            case "K7" -> name.equals("C6_JOINT_READBACK");
            case "K10_ACK" -> name.equals("C7_ACKNOWLEDGED");
            case "K10" -> name.equals("C8_RELEASED");
            default -> false;
        };
        if (match) { pause(name); }
    }

    private void pause(String hook) {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PAUSED cut={} hook={} first_store={} thread={}",
                cut, hook, firstStore, Thread.currentThread().getName());
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Loader matrix forced stop did not arrive within 30 seconds");
    }

    void tick() {
        var level = server.overworld();
        if (done || paused.get() || !level.areEntitiesLoaded(new ChunkPos(ORIGIN).toLong()) || !level.isPositionEntityTicking(ORIGIN)) { return; }
        if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Loader matrix exceeded its bounded wait");
        if (recovery) { recover(); return; }
        if (step == 0) {
            var owner = UUID.randomUUID();
            rocket = rocket(level, ORIGIN, owner, bi ? bipropellant(2_000, 2_000) : mono(2_000));
            EscrowTags.require(level.addFreshEntity(rocket), "Native rocket publication failed");
            loaders.add(FluidFuelLoaderEscrowFixtures.loader(level, A, owner, tank(bi ? HYDROGEN : ROCKET_FUEL, 5_000), binding(rocket)));
            if (bi) { loaders.add(FluidFuelLoaderEscrowFixtures.loader(level, B, owner, tank(OXYGEN, 5_000), binding(rocket))); }
            EscrowTags.require(server.saveEverything(false, true, true), "Fixture baseline refused");
            step = 1; armed = !bi;
            if (boundary.startsWith("N3_")) { refuse(); return; }
            open(loaders.get(0));
        } else if (bi && step == 1 && settled(1)) {
            // Establish the primary fill normally, before cutting the oxidizer fill. This is not a chain test.
            EscrowTags.require(server.saveEverything(false, true, true), "Bipropellant primary baseline refused");
            step = 2; armed = true; open(loaders.get(1));
        }
    }

    private FluidFuelLoaderBlockEntity loader() { return loaders.get(bi ? 1 : 0); }
    private void open(FluidFuelLoaderBlockEntity loader) {
        EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), true);
        EscrowTags.require(loader.attempt() == FluidFuelLoaderStatus.READY, "Fixture admission did not enter the fair queue");
    }

    private void refuse() {
        var runtime = EscrowRuntime.of(server); var authority = runtime.propulsion(server).authority();
        var source = loader().saveWithFullMetadata(); var destination = EscrowNativeEnvelope.rocket(rocket);
        var pending = new ArrayList<EscrowAdmissionAuthority.Pending>();
        EscrowAdmissionAuthority.Delegate bay = null;
        if (boundary.equals("N3_FULL")) {
            for (int i = 0; i < EscrowLimits.OPERATIONS; i++) {
                pending.add(new EscrowAdmissionAuthority.Pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        EscrowAdmissionAuthority.Consumer.BAY));
            }
            authority.registerIndex(EscrowAdmissionAuthority.Consumer.BAY, () -> new EscrowAdmissionAuthority.Index(true, pending));
        } else if (boundary.equals("N3_NOT_LOADED")) {
            bay = authority.register(EscrowAdmissionAuthority.Consumer.BAY);
        }
        runtime.loaderAdmissionForTests(server, device(loader()), true);
        FluidFuelLoaderStatus status;
        if (boundary.equals("N3_PREFLIGHT")) {
            // A transient rider is a real eligibility refusal; it never changes either saved resource envelope.
            var rider = new ArmorStand(server.overworld(), 64, 100, 64);
            EscrowTags.require(rider.startRiding(rocket, true), "Preflight rider setup refused");
            status = loader().attempt(); rider.stopRiding(); rider.discard();
            EscrowTags.require(status == FluidFuelLoaderStatus.ROCKET_BUSY, "Occupied rocket passed preflight");
        } else {
            EscrowTags.require(loader().attempt() == FluidFuelLoaderStatus.READY, "Refusal did not visit the queue");
            runtime.fills(server).visit(); status = loader().lastAttempt();
            EscrowTags.require(status == (bay == null ? FluidFuelLoaderStatus.ESCROW_FULL : FluidFuelLoaderStatus.LEDGER_UNAVAILABLE),
                    "Wrong shared-authority refusal");
        }
        runtime.loaderAdmissionForTests(server, device(loader()), false);
        EscrowTags.require(source.equals(loader().saveWithFullMetadata()) && destination.equals(EscrowNativeEnvelope.rocket(rocket))
                && ResourceEscrowSavedData.existing(server) == null && runtime.fills(server).size() == 0
                && !runtime.fills(server).preparing() && authority.pendingCount() == pending.size(), "Refusal changed resources or reservations");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_REFUSED cut={} status={} bay={} pending={}",
                cut, status, bay == null ? "TEST_INDEX" : bay.state(), authority.pendingCount());
        pending.clear(); if (bay != null) { bay.noFile(); }
        EscrowTags.require(authority.pendingCount() == 0, "Fixture bay left a reservation");
        observe("refused"); pause(boundary);
    }

    private void recover() {
        if (rocket == null) {
            var found = server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(8));
            if (found.isEmpty() || !(server.overworld().getBlockEntity(A) instanceof FluidFuelLoaderBlockEntity a)) { return; }
            EscrowTags.require(found.size() == 1, "Native restart duplicated rocket");
            rocket = found.get(0); loaders.add(a);
            if (bi) { loaders.add((FluidFuelLoaderBlockEntity) server.overworld().getBlockEntity(B)); }
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (creation && !retrying) {
            EscrowTags.require(data == null || data.ledger().records().isEmpty() && data.ledger().issuedThrough() == 0,
                    "Creation restart issued a sequence");
            EscrowTags.require(amount(loader()) == 5_000 && bank(rocket, false) == 0 && section(loader()).epoch() == 0
                    && section(loader()).markers().isEmpty() && rocket.escrowSection().epoch() == 0
                    && rocket.escrowSection().markers().isEmpty()
                    && EscrowRuntime.of(server).propulsion(server).authority().pendingCount() == 0, "Creation restart changed resources");
            observe("creation_restart");
            if (Boolean.getBoolean("advancedrocketrycommunity.escrowLoaderRetry")) {
                retrying = true; open(loader()); return;
            }
            done = true; result(); return;
        }
        long total = loaders.stream().mapToLong(FluidFuelLoaderEscrowFixtures::amount).sum()
                + bank(rocket, false) + bank(rocket, true) + held(server, rocket, loaders);
        EscrowTags.require(total == (bi ? 10_000 : 5_000), "Native restart violated raw control total");
        long expected = boundary.equals("K0") || boundary.equals("K1") ? 0 : bi ? 2 : 1;
        if (server.getTickCount() - began >= 100 && settled(expected)) { observe("recovered"); done = true; result(); }
    }

    private boolean settled(long fills) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.operational() || !data.bind(server).writerIdle() || !data.ledger().records().isEmpty()
                || !rocket.escrowSection().markers().isEmpty() || loaders.stream().anyMatch(l -> !section(l).markers().isEmpty())) { return false; }
        if (data.ledger().issuedThrough() != fills) { return false; }
        EscrowTags.require(bank(rocket, false) == (fills > 0 ? 1_000 : 0) && bank(rocket, true) == (fills > 1 ? 1_000 : 0)
                && rocket.escrowSection().epoch() == fills && data.bind(server).admissionAuthority().pendingCount() == 0,
                "Retirement changed bank totals, epochs or reservations");
        for (int i = 0; i < loaders.size(); i++) {
            int moved = fills > i ? 1 : 0;
            EscrowTags.require(amount(loaders.get(i)) == 5_000 - moved * 1_000 && section(loaders.get(i)).epoch() == moved,
                    "Retirement changed loader amount or epoch");
        }
        return true;
    }

    private void observe(String phase) {
        var data = ResourceEscrowSavedData.existing(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_STATE cut={} phase={} tanks={} banks={},{} epochs={} rocket_epoch={} source_markers={} rocket_markers={} issued={} records={} pending={}",
                cut, phase, loaders.stream().map(FluidFuelLoaderEscrowFixtures::amount).toList(), bank(rocket, false), bank(rocket, true),
                loaders.stream().map(l -> section(l).epoch()).toList(), rocket.escrowSection().epoch(),
                loaders.stream().map(l -> section(l).markers().size()).toList(), rocket.escrowSection().markers().size(),
                data == null ? 0 : data.ledger().issuedThrough(), data == null ? 0 : data.ledger().records().size(),
                EscrowRuntime.of(server).propulsion(server).authority().pendingCount());
    }

    private void result() { AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_RESULT cut={}", cut); }
}
