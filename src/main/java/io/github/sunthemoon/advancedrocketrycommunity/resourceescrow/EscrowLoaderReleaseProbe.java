package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * W95's native restart run through the gated switch (cases {@code LF_*}): on a fresh flat world the real loader's first
 * fill creates the ledger; the server is force-stopped at a named cut; each restart inspects the START of its first tick,
 * before anything can bind lazily, and then lets production reconciliation finish. Release-test hooks only.
 */
final class EscrowLoaderReleaseProbe {
    static final String PREFIX = "LF_";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64);
    private static final BlockPos DEVICE = new BlockPos(66, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private final EscrowLoaderCutProbe matrix;
    private final EscrowLoaderFloorProbe floors;
    private final EscrowLoaderNamedProbe named;
    private final EscrowLandedRetirementProbe retirement;
    private final EscrowFillCountdownProbe countdown;
    private final EscrowLoaderS6Probe s6;
    private final AtomicBoolean paused = new AtomicBoolean();
    private boolean firstTick, done;
    private int step;
    private long began = -1;

    EscrowLoaderReleaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        s6 = EscrowLoaderS6Probe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6Probe(server, recovery) : null;
        countdown = EscrowFillCountdownProbe.selected(cut) ? new EscrowFillCountdownProbe(server, cut, recovery) : null;
        retirement = EscrowLandedRetirementProbe.selected(cut) ? new EscrowLandedRetirementProbe(server, cut, recovery) : null;
        // W107C: cut R1's floor fixture runs beside, never inside, the W97 matrix.
        named = EscrowLoaderNamedProbe.selected(cut) ? new EscrowLoaderNamedProbe(server, cut, recovery) : null;
        floors = EscrowLoaderFloorProbe.selected(cut) ? new EscrowLoaderFloorProbe(server, cut, recovery) : null;
        matrix = s6 == null && countdown == null && retirement == null && named == null && floors == null && EscrowLoaderCutProbe.selected(cut) ? new EscrowLoaderCutProbe(server, cut, recovery) : null;
        if (s6 != null || matrix != null || floors != null || named != null || retirement != null || countdown != null) { return; }
        EscrowTags.require(cut.startsWith(PREFIX) && (recovery || Boolean.getBoolean(EscrowLoaderAdmission.SWITCH)),
                "The loader restart fixture starts only through the gated switch");
    }

    /**
     * The first tick's START, before any END-phase lookup could bind a ledger lazily: a fresh world is NO_FILE, and a
     * restarted world's ledger was already bound at ServerStartingEvent, with its reservations indexed.
     */
    void startTick() {
        if (s6 != null) { s6.startTick(); return; }
        if (countdown != null) { countdown.startTick(); return; }
        if (retirement != null) { retirement.startTick(); return; }
        if (named != null) { named.startTick(); return; }
        if (floors != null) { floors.startTick(); return; }
        if (matrix != null) { matrix.startTick(); return; }
        if (firstTick) { return; }
        firstTick = true;
        var delegate = EscrowRuntime.of(server).propulsion(server);
        var state = delegate.state();
        var data = ResourceEscrowSavedData.existing(server);
        int records = data == null ? 0 : data.ledger().records().size();
        int pending = delegate.authority().pendingCount();
        long generation = data == null ? -1 : data.ledger().writerGeneration();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_LOADER_FIRST_TICK cut={} recovery={} tick={} delegate={} records={} pending={} generation={}",
                cut, recovery, server.getTickCount(), state, records, pending, generation);
        if (!recovery) {
            EscrowTags.require(data == null && state == EscrowAdmissionAuthority.Delegate.State.NO_FILE, "A fresh world did not start NO_FILE");
        } else {
            EscrowTags.require(data != null && state == EscrowAdmissionAuthority.Delegate.State.LOADED && pending == records
                    && generation >= 2, "The restarted ledger was not bound with its reservations before the first tick");
        }
    }

    void checkpoint(String name) {
        if (s6 != null) { s6.checkpoint(name); return; }
        if (countdown != null) { return; }
        if (retirement != null) { retirement.checkpoint(name); return; }
        if (named != null) { named.checkpoint(name); return; }
        if (floors != null) { floors.checkpoint(name); return; }
        if (matrix != null) { matrix.checkpoint(name); return; }
        if (recovery || done || step < 1 || !cut.equals(PREFIX + name)) { return; }
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_LOADER_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Loader forced stop did not arrive within 30 seconds");
    }

    void tick() {
        if (s6 != null) { s6.tick(); return; }
        if (countdown != null) { countdown.tick(); return; }
        if (retirement != null) { retirement.tick(); return; }
        if (named != null) { named.tick(); return; }
        if (floors != null) { floors.tick(); return; }
        if (matrix != null) { matrix.tick(); return; }
        var level = server.overworld();
        if (done || paused.get() || !level.areEntitiesLoaded(new ChunkPos(ORIGIN).toLong()) || !level.isPositionEntityTicking(ORIGIN)) { return; }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "The loader restart fixture exceeded its bounded wait");
        if (recovery) { recover(); return; }
        if (step == 0) {
            EscrowTags.require(ResourceEscrowSavedData.existing(server) == null, "A fresh world already had a ledger");
            var owner = UUID.randomUUID();
            var rocket = FluidFuelLoaderEscrowFixtures.rocket(level, ORIGIN, owner, mono(2_000));
            EscrowTags.require(level.addFreshEntity(rocket), "Native rocket publication failed");
            loader(level, DEVICE, owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
            // Persist only the pre-admission baseline; no fixture save follows admission or any cut.
            EscrowTags.require(server.saveEverything(false, true, true), "Initial loader and rocket baseline refused");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_LOADER_PLACED cut={} device={} rocket={}", cut,
                    device((FluidFuelLoaderBlockEntity) level.getBlockEntity(DEVICE)), rocket.getUUID());
            step = 1;
        }
    }

    private void recover() {
        var level = server.overworld();
        if (!(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity loader) || loader.root().isEmpty()) { return; }
        var rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(8));
        if (rockets.isEmpty()) { return; }
        EscrowTags.require(rockets.size() == 1, "Restart duplicated the native rocket");
        var rocket = rockets.get(0); var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.operational(), "The restarted ledger is unavailable");
        var coordinator = data.bind(server);
        long held = held(server, rocket, List.of(loader));
        EscrowTags.require(amount(loader) + bank(rocket, false) + held == 1_000, "Restart violated loader conservation");
        if (server.getTickCount() - began < 100 || !coordinator.writerIdle() || !data.ledger().records().isEmpty()
                || !rocket.escrowSection().markers().isEmpty() || !section(loader).markers().isEmpty()) { return; }
        EscrowTags.require(amount(loader) == 0 && bank(rocket, false) == 1_000 && rocket.escrowSection().epoch() == 1
                && section(loader).epoch() == 1 && data.ledger().id().equals(section(loader).ledger())
                && data.ledger().id().equals(rocket.escrowSection().ledger()) && !loader.escrowLocked() && RocketEscrowGate.idle(rocket)
                && coordinator.admissionAuthority().pendingCount() == 0, "Restart did not complete exactly one transfer");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_LOADER_RESULT cut={} tank={} fuel={} records={} rocket_epoch={} device_epoch={} ledger={}",
                cut, amount(loader), bank(rocket, false), data.ledger().records().size(), rocket.escrowSection().epoch(),
                section(loader).epoch(), data.ledger().id());
        done = true;
    }
}
