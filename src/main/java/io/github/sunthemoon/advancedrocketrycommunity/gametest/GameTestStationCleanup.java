package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketWarmupGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * W318 GameTest support: removes one station that a test created once the test ends, whether it passed, failed or timed
 * out. Like {@code StationWarpGameTests.Fixture.close}, it deletes the station from the registry, drops its notice
 * summary and removes its unchanged platform template. It also runs the test's own restore actions first (for example
 * a landing pad the test placed), drops any landing-pad index entry left under the station, and discards rockets left
 * in its region.
 *
 * <p>Removal waits until nothing the test started still names the station: no transfer record uses its region or targets
 * it, no pad of it holds a claim whose transfer is not yet acknowledged absent, no warm-up lies in its region, and every
 * condition the test added (its own rockets' records, ARMED tuples and warm-ups) is clear. A passing test has settled all
 * of these, so its station goes at once. After a failure the check repeats every {@value #RETRY_INTERVAL_TICKS} ticks
 * for at most {@value #MAX_WAIT_TICKS} ticks. A station still held then, or still present when the server stops, is
 * reported with a WARN, so the run's log check fails instead of a later test meeting the station.
 */
public final class GameTestStationCleanup implements GameTestListener {
    static final int RETRY_INTERVAL_TICKS = 20;
    /** Ten 600-tick warm-up lifetimes: room for a failed test's flight or warm-up to end on its own. */
    static final int MAX_WAIT_TICKS = 6_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIdentity.MOD_ID);

    private final MinecraftServer server;
    private final UUID stationId;
    private final String name;
    private final List<Supplier<? extends Collection<String>>> conditions = new ArrayList<>();
    private final List<Runnable> restores = new ArrayList<>();
    private String test = "unknown";
    private long endedAt = -1;
    private boolean done;

    private GameTestStationCleanup(MinecraftServer server, StationState station) {
        this.server = server;
        this.stationId = station.stationId();
        this.name = station.name();
    }

    /** Installs the cleanup on the running test; call it right after the station is created. */
    public static GameTestStationCleanup track(GameTestHelper helper, StationState station) {
        GameTestStationCleanup cleanup = new GameTestStationCleanup(helper.getLevel().getServer(), station);
        try {
            // Same development-only terminal-listener access as the adjacent native fixtures; no AT needed.
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(cleanup);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install the station cleanup", failure);
        }
        MinecraftForge.EVENT_BUS.register(cleanup);
        return cleanup;
    }

    /** A test-owned reason to wait: each returned line names something that still holds the station. */
    public GameTestStationCleanup heldWhile(Supplier<? extends Collection<String>> holds) {
        conditions.add(holds);
        return this;
    }

    /** Runs once the station is settled and before it is deleted, for blocks the test placed in its region. */
    public GameTestStationCleanup restoring(Runnable restore) {
        restores.add(restore);
        return this;
    }

    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { ended(info); }
    @Override public void testFailed(GameTestInfo info) { ended(info); }

    private void ended(GameTestInfo info) {
        if (done || endedAt >= 0) {
            return;
        }
        test = info.getTestName();
        endedAt = server.getTickCount();
        attempt();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() != server || done || endedAt < 0) {
            return;
        }
        long waited = server.getTickCount() - endedAt;
        if (waited > 0 && waited % RETRY_INTERVAL_TICKS == 0) {
            attempt();
        }
    }

    /** The end-of-run check: a station this cleanup never removed is reported before the server shuts down. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer() != server || done) {
            return;
        }
        finish();
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        if (!stations.operational() || stations.find(stationId).isPresent()) {
            LOGGER.warn("ARCE_GAMETEST_STATION_LEFT_AT_STOP test={} station={} name={} ended={}",
                    test, stationId, name, endedAt >= 0);
        }
    }

    private void attempt() {
        long waited = server.getTickCount() - endedAt;
        try {
            StationRegistrySavedData stations = StationRegistrySavedData.get(server);
            if (stations.operational() && stations.find(stationId).isEmpty()) {
                finish();
                LOGGER.info("ARCE_GAMETEST_STATION_REMOVED test={} station={} name={} waited={} already=true",
                        test, stationId, name, waited);
                return;
            }
            List<String> holds = holds(stations);
            if (holds.isEmpty()) {
                remove(stations, waited);
            } else if (waited >= MAX_WAIT_TICKS) {
                finish();
                LOGGER.warn("ARCE_GAMETEST_STATION_LEFT test={} station={} name={} waited={} holds={}",
                        test, stationId, name, waited, holds);
            }
        } catch (RuntimeException failure) {
            // Never thrown into the GameTest listener chain: the WARN fails the run's log check instead.
            finish();
            LOGGER.warn("ARCE_GAMETEST_STATION_CLEANUP_FAILED test={} station={} name={} waited={}",
                    test, stationId, name, waited, failure);
        }
    }

    private List<String> holds(StationRegistrySavedData stations) {
        List<String> holds = new ArrayList<>();
        if (!stations.operational() || stations.updatesQuarantined()) {
            holds.add("station registry unavailable");
            return holds;
        }
        StationState station = stations.find(stationId).orElseThrow();
        RocketTransferSavedData journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) {
            holds.add("transfer journal unavailable");
        } else {
            for (RocketTransferRecord record : journal.entries()) {
                if (names(station, record)) {
                    holds.add("transfer " + record.transferId());
                }
            }
        }
        LandingPadIndex pads = LandingPadIndex.get(server);
        if (!pads.operational()) {
            holds.add("landing-pad index unavailable");
        } else {
            for (var entry : pads.entries(stationId)) {
                if (entry.claim() != null && !journal.transferAbsenceAcknowledged(entry.claim())) {
                    holds.add("pad claim " + entry.claim());
                }
            }
        }
        for (var warmup : RocketWarmupGameTestSupport.live(server)) {
            if (inRegion(station, warmup.region())) {
                holds.add("warm-up " + warmup.logical());
            }
        }
        for (var condition : conditions) {
            holds.addAll(condition.get());
        }
        return holds;
    }

    private void remove(StationRegistrySavedData stations, long waited) {
        StationState station = stations.find(stationId).orElseThrow();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (space == null) {
            throw new IllegalStateException("Space Level is unavailable");
        }
        restores.forEach(Runnable::run);
        // Only a failed test can leave one: a passing test discards its arrivals before it succeeds.
        List<RocketEntity> rockets = space.getEntitiesOfClass(RocketEntity.class, new AABB(
                station.region().minimumX(), space.getMinBuildHeight(), station.region().minimumZ(),
                station.region().maximumX() + 1.0D, space.getMaxBuildHeight(), station.region().maximumZ() + 1.0D));
        rockets.forEach(Entity::discard);
        LandingPadIndex pads = LandingPadIndex.get(server);
        int padEntries = 0;
        for (var entry : pads.entries(stationId)) {
            if (pads.remove(stationId, entry.reference(), entry.physicalId())) {
                padEntries++;
            }
        }
        stations.delete(stationId);
        // Deleting through the registry also drops the station's notice summary (ADR-065 section 5.3), as the warp
        // fixture does.
        StationNoticeSavedData.get(server).remove(stationId);
        var template = new StationPlatformGenerator().removeTemplate(space, station.cell());
        stations.flush(server);
        finish();
        LOGGER.info("ARCE_GAMETEST_STATION_REMOVED test={} station={} name={} cell={},{} waited={} rockets={}"
                        + " padEntries={} template={}/{}", test, stationId, name, station.cell().x(), station.cell().z(),
                waited, rockets.size(), padEntries, template.changed(), template.inspected());
    }

    private void finish() {
        if (!done) {
            done = true;
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    /** A record whose source or destination lies in the station's region, or whose plan targets the station. */
    private static boolean names(StationState station, RocketTransferRecord record) {
        return inRegion(station, RocketRegion.fromSnapshot(record.sourceSnapshot()))
                || inRegion(station, RocketRegion.fromSnapshot(record.destinationSnapshot()))
                || record.sourceFlightData().plan()
                        .map(plan -> station.stationId().equals(plan.destinationStationId())).orElse(false);
    }

    private static boolean inRegion(StationState station, RocketRegion region) {
        return region.dimension().equals(CelestialIds.SPACE_LEVEL.location())
                && station.region().minimumX() <= region.maximum().x()
                && station.region().maximumX() >= region.minimum().x()
                && station.region().minimumZ() <= region.maximum().z()
                && station.region().maximumZ() >= region.minimum().z();
    }
}
