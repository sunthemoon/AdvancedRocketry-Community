package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** W125's gated named E5 and cross-Level cuts. Setup/observations only; production owns every recovery action. */
final class EscrowLoaderNamedProbe {
    static final String PREFIX = "LF_M_NAMED_", E5 = PREFIX + "E5";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), DEVICE = ORIGIN.offset(2, 0, 0);
    private static final BlockPos HEALTHY = new BlockPos(88, 100, 88), HEALTHY_DEVICE = HEALTHY.offset(2, 0, 0);
    private static final RocketPosition OLD_SOURCE = new RocketPosition(65_536, 100, 65_536);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery, e5;
    private final AtomicBoolean paused = new AtomicBoolean();
    private RocketEntity rocket, healthy;
    private FluidFuelLoaderBlockEntity loader, healthyLoader;
    private long began = -1;
    private volatile int step;
    private boolean first, done, sourceEverLoaded;

    static boolean selected(String cut) { return E5.equals(cut) || (PREFIX + "CROSS_K2").equals(cut) || (PREFIX + "CROSS_K9").equals(cut); }

    EscrowLoaderNamedProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH),
                "Named cuts need per-device gates only");
        this.server = server; this.cut = cut; this.recovery = recovery; e5 = E5.equals(cut);
    }

    void startTick() {
        if (first) { return; } first = true;
        var delegate = EscrowRuntime.of(server).propulsion(server); var data = ResourceEscrowSavedData.existing(server);
        int records = data == null ? 0 : data.ledger().records().size();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIRST cut={} recovery={} delegate={} issued={} records={} pending={}",
                cut, recovery, delegate.state(), data == null ? 0 : data.ledger().issuedThrough(), records, delegate.authority().pendingCount());
        EscrowTags.require(records == delegate.authority().pendingCount(), "Restart lost a reservation");
        if (!recovery) { EscrowTags.require(data == null, "Named cut requires a fresh world"); }
    }

    void checkpoint(String name) {
        if (recovery || done || step < 2 || paused.get()) { return; }
        if (!e5 && cut.endsWith("_" + name) && (name.equals("K2") || name.equals("K9"))) { pause(name); return; }
        if (server.isSameThread() && name.equals("LOADER_PREPARATIONS_QUEUED")) {
            var coordinator = ResourceEscrowSavedData.existing(server).bind(server);
            for (var source : e5 ? List.of(loader, healthyLoader) : List.of(loader)) {
                if (!coordinator.unresolvedNaming(device(source)).isEmpty()) {
                    EscrowRuntime.of(server).loaderAdmissionForTests(server, device(source), false);
                }
            }
        }
    }

    void tick() {
        if (done || paused.get()) { return; }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(ORIGIN) || e5 && !level.isPositionEntityTicking(HEALTHY)) { return; }
        if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Named fixture exceeded its bounded wait");
        if (recovery) { recover(level); return; }
        if (step == 0) { place(level); step = 1; return; }
        var data = ResourceEscrowSavedData.existing(server);
        if (step == 1 && data.ledger().writerGeneration() >= 1 && data.bind(server).writerIdle()
                && RocketRuntime.resourceMutationAllowed(rocket)) {
            step = 2; queue(loader); if (e5) { queue(healthyLoader); } return;
        }
        if (e5 && step == 2 && data.bind(server).writerIdle() && data.ledger().records().size() == 1) {
            var op = data.ledger().records().get(0);
            if (op.phase() == EscrowOperation.Phase.STALLED && op.reason() == EscrowReason.BARRIER_IO
                    && op.guard().transfer() != null && op.guard().rocket().equals(rocket.assemblyTransactionId().orElseThrow())
                    && retired(healthy, healthyLoader) && bank(healthy, false) == 1_000) {
                EscrowTags.require(EscrowRuntime.of(server).barrier().ioFailures() >= 3, "E5 did not observe three proof failures");
                EscrowTags.require(server.saveEverything(false, true, true), "STALLED fixture save failed");
                state("STALLED"); pause("STALLED_RESTART");
            }
        }
    }

    private void place(ServerLevel level) {
        var owner = UUID.randomUUID(); rocket = rocket(level, ORIGIN, owner, mono(2_000));
        EscrowTags.require(level.addFreshEntity(rocket), "Named rocket publication failed");
        loader = loader(level, DEVICE, owner, tank(ROCKET_FUEL, 5_000), binding(rocket));
        if (e5) {
            healthy = rocket(level, HEALTHY, owner, mono(2_000));
            EscrowTags.require(level.addFreshEntity(healthy), "Healthy rocket publication failed");
            healthyLoader = loader(level, HEALTHY_DEVICE, owner, tank(ROCKET_FUEL, 5_000), binding(healthy));
        }
        var journal = RocketTransferSavedData.get(server);
        if (e5) { EscrowLoaderFloorProbe.landed(level, journal, rocket); }
        else { EscrowLoaderFloorProbe.landed(level, journal, rocket, CelestialIds.MOON_LEVEL.location(), OLD_SOURCE); }
        journal.flushChecked(server);
        ResourceEscrowSavedData.forTests(server).bind(server).autosave();
        EscrowTags.require(server.saveEverything(false, true, true), "Named fixture baseline save failed");
    }

    private void queue(FluidFuelLoaderBlockEntity source) {
        EscrowRuntime.of(server).loaderAdmissionForTests(server, device(source), true);
        EscrowTags.require(source.attempt() == FluidFuelLoaderStatus.READY, "Named fixture admission did not enter the fair queue");
    }

    private void recover(ServerLevel level) {
        if (rocket == null) {
            var candidates = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(4));
            if (candidates.isEmpty() || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity source)) { return; }
            EscrowTags.require(candidates.size() == 1, "Named rocket duplicated"); rocket = candidates.get(0); loader = source;
            if (e5) {
                var other = level.getEntitiesOfClass(RocketEntity.class, new AABB(HEALTHY).inflate(4));
                EscrowTags.require(other.size() == 1 && level.getBlockEntity(HEALTHY_DEVICE) instanceof FluidFuelLoaderBlockEntity,
                        "Healthy control endpoint missing or duplicated");
                healthy = other.get(0); healthyLoader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(HEALTHY_DEVICE);
            }
        }
        if (!e5) {
            var sourceLevel = server.getLevel(CelestialIds.MOON_LEVEL);
            EscrowTags.require(sourceLevel != null, "Missing cross-Level fixture Level");
            sourceEverLoaded |= sourceLevel.getChunkSource().getChunkNow(OLD_SOURCE.x() >> 4, OLD_SOURCE.z() >> 4) != null;
            EscrowTags.require(!sourceEverLoaded, "Recovery loaded the old cross-Level source");
        }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(amount(loader) + bank(rocket, false) + held(server, rocket, List.of(loader)) == 5_000,
                "Named fill failed conservation");
        boolean complete = data.ledger().records().isEmpty() && data.bind(server).writerIdle() && retired(rocket, loader);
        if (complete && (!e5 || retired(healthy, healthyLoader))
                && (e5 ? RocketRuntime.resourceMutationAllowed(rocket) : server.getTickCount() - began >= 1_300)) {
            EscrowTags.require(bank(rocket, false) == 1_000 && amount(loader) == 4_000
                    && data.bind(server).admissionAuthority().pendingCount() == 0, "Retired fill changed resources or reservations");
            if (!e5) { EscrowTags.require(!RocketRuntime.resourceMutationAllowed(rocket), "F1b gained unreviewed destination authority"); }
            state(e5 ? "RETIRED" : "F1B_HELD"); done = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_RESULT cut={}", cut);
        }
    }

    private boolean retired(RocketEntity target, FluidFuelLoaderBlockEntity source) {
        return target.escrowSection().markers().isEmpty() && section(source).markers().isEmpty()
                && amount(source) == 4_000 && bank(target, false) == 1_000;
    }

    private void state(String state) {
        var data = ResourceEscrowSavedData.existing(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_NAMED_STATE cut={} state={} records={} tank={} bank={} healthy_tank={} healthy_bank={} mutation_allowed={} source_ever_loaded={}",
                cut, state, data.ledger().records().size(), amount(loader), bank(rocket, false),
                e5 ? amount(healthyLoader) : -1, e5 ? bank(healthy, false) : -1, RocketRuntime.resourceMutationAllowed(rocket), sourceEverLoaded);
    }

    private void pause(String hook) {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PAUSED cut={} hook={} first_store=NONE thread={}", cut, hook, Thread.currentThread().getName());
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        throw new IllegalStateException("Named cut forced stop did not arrive within 30 seconds");
    }
}
