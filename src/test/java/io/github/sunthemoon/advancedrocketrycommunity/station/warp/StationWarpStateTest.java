package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pure ADR-044 warp state: pending credits, confirmations, countdowns, cost class and settings. */
final class StationWarpStateTest {
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation MOON = ModIdentity.id("moon");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void creditsRespectThePerTickAllowanceTheCapAndSimulation() {
        StationWarpCredits credits = new StationWarpCredits();
        UUID station = UUID.randomUUID();
        assertEquals(StationLimits.WARP_CREDIT_PER_TICK, credits.offer(station, Integer.MAX_VALUE, 0, 10, true));
        assertEquals(0, credits.pending(station), "Simulation changes nothing");
        assertEquals(150_000, credits.offer(station, 150_000, 0, 10, false));
        assertEquals(50_000, credits.offer(station, 150_000, 0, 10, false), "The allowance is per station and tick");
        assertEquals(0, credits.offer(station, 1, 0, 10, false));
        assertEquals(0, credits.offer(station, 1, 0, 10, true));
        assertEquals(StationLimits.WARP_CREDIT_PER_TICK, credits.pending(station));
        UUID other = UUID.randomUUID();
        assertEquals(StationLimits.WARP_CREDIT_PER_TICK, credits.offer(other, Integer.MAX_VALUE, 0, 10, false));
        // A new tick restores the allowance; balance plus pending never exceeds the cap.
        int balance = StationLimits.MAX_WARP_ENERGY - StationLimits.WARP_CREDIT_PER_TICK - 7;
        assertEquals(0, credits.offer(station, 100, balance + 8, 11, false), "Balance plus pending would pass the cap");
        assertEquals(7, credits.offer(station, 100, balance, 11, false), "Only the room under the cap is accepted");
        assertEquals(0, credits.offer(station, 100, balance, 11, false));
        assertEquals(7, credits.offer(UUID.randomUUID(), 100, StationLimits.MAX_WARP_ENERGY - 7, 11, false));
        assertEquals(0, credits.offer(station, 0, 0, 12, false));
        assertEquals(0, credits.offer(station, -5, 0, 12, false));
        assertEquals(0, credits.offer(station, 5, -1, 12, false));

        Map<UUID, Integer> drained = credits.drain();
        assertEquals(StationLimits.WARP_CREDIT_PER_TICK + 7, drained.get(station));
        assertEquals(StationLimits.WARP_CREDIT_PER_TICK, drained.get(other));
        assertTrue(credits.isEmpty());
        assertEquals(5, credits.offer(station, 5, 0, 12, true), "Rejected offers did not use tick 12's allowance");
    }

    @Test
    void pendingCreditsAreBoundedByTheStationLimit() {
        StationWarpCredits credits = new StationWarpCredits();
        for (int index = 0; index < StationLimits.MAX_PENDING_WARP_CREDITS; index++) {
            assertEquals(1, credits.offer(new UUID(7, index), 1, 0, 1, false));
        }
        assertEquals(0, credits.offer(new UUID(8, 0), 1, 0, 1, false), "A new station is refused when full");
        assertEquals(1, credits.offer(new UUID(7, 0), 1, 0, 1, false), "An existing entry still accepts");
        assertEquals(StationLimits.MAX_PENDING_WARP_CREDITS, credits.size());
        credits.clear();
        assertEquals(0, credits.size());
    }

    @Test
    void confirmationsAreOneShotBoundAndBounded() {
        StationWarpConfirmations confirmations = new StationWarpConfirmations();
        Object authority = new Object();
        WarpQuote quote = quote(station(), UUID.randomUUID());
        assertTrue(confirmations.issue(quote, authority, 100));
        assertEquals(StationWarpConfirmations.Status.MISMATCH,
                confirmations.take(quote.actorId(), UUID.randomUUID(), authority, 101).status());
        assertTrue(confirmations.issue(quote, authority, 100));
        assertEquals(StationWarpConfirmations.Status.MISMATCH,
                confirmations.take(quote.actorId(), quote.stationId(), new Object(), 101).status());
        assertTrue(confirmations.issue(quote, authority, 100));
        var ready = confirmations.take(quote.actorId(), quote.stationId(), authority, 100 + StationLimits.WARP_CONFIRMATION_TICKS);
        assertEquals(StationWarpConfirmations.Status.READY, ready.status());
        assertEquals(quote, ready.quote());
        assertEquals(StationWarpConfirmations.Status.MISSING,
                confirmations.take(quote.actorId(), quote.stationId(), authority, 101).status(), "One-shot");
        assertTrue(confirmations.issue(quote, authority, 100));
        assertEquals(StationWarpConfirmations.Status.EXPIRED, confirmations.take(quote.actorId(), quote.stationId(),
                authority, 101 + StationLimits.WARP_CONFIRMATION_TICKS).status());

        for (int index = 0; index < StationLimits.MAX_PENDING_WARPS; index++) {
            assertTrue(confirmations.issue(quote(station(), new UUID(9, index)), authority, 500));
        }
        assertFalse(confirmations.issue(quote(station(), UUID.randomUUID()), authority, 500), "Capacity");
        assertTrue(confirmations.issue(quote(station(), new UUID(9, 0)), authority, 500), "Replacing is allowed");
        assertTrue(confirmations.issue(quote(station(), UUID.randomUUID()), authority,
                501 + StationLimits.WARP_CONFIRMATION_TICKS), "Expired entries are purged");
        confirmations.clear(new UUID(9, 1));
        confirmations.clear();
        assertEquals(0, confirmations.size());
    }

    @Test
    void countdownsAreOnePerStationBoundedAnnouncedAndCommittedInOrder() {
        StationWarpCountdowns countdowns = new StationWarpCountdowns();
        WarpQuote first = quote(station(), UUID.randomUUID());
        WarpQuote second = quote(station(), UUID.randomUUID());
        assertEquals(StationWarpCountdowns.Start.STARTED, countdowns.start(first, 1_000));
        assertEquals(StationWarpCountdowns.Start.ALREADY_RUNNING, countdowns.start(first, 1_001));
        assertEquals(StationWarpCountdowns.Start.STARTED, countdowns.start(second, 1_000));
        long due = 1_000 + StationLimits.WARP_COUNTDOWN_TICKS;
        List<Integer> announced = new ArrayList<>();
        for (long tick = 1_000; tick < due; tick++) {
            for (var announcement : countdowns.announcements(tick)) {
                if (announcement.countdown().quote() == first) {
                    announced.add(announcement.secondsLeft());
                }
            }
        }
        assertEquals(List.of(5, 3, 2, 1), announced, "10 s is announced by the confirmation itself");
        assertEquals(10, StationWarpCountdowns.START_SECONDS);
        assertTrue(countdowns.nextDue(due - 1).isEmpty());
        assertEquals(first, countdowns.nextDue(due).orElseThrow().quote(), "Earliest started commits first");
        assertEquals(first, countdowns.cancel(first.stationId()).orElseThrow().quote());
        assertEquals(second, countdowns.nextDue(due).orElseThrow().quote(), "The other waits for the next tick");
        assertEquals(40L, countdowns.get(second.stationId()).orElseThrow().ticksLeft(due - 40));

        countdowns.clear();
        for (int index = 0; index < StationLimits.MAX_WARP_COUNTDOWNS; index++) {
            assertEquals(StationWarpCountdowns.Start.STARTED, countdowns.start(quote(station(), UUID.randomUUID()), 0));
        }
        assertEquals(StationWarpCountdowns.Start.CAPACITY_REACHED, countdowns.start(first, 0));
        assertEquals(StationLimits.MAX_WARP_COUNTDOWNS, countdowns.size());
    }

    @Test
    void costClassFollowsTheStarSystemsOfTheCatalog() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        CelestialCatalog catalog = CelestialCatalog.create(bodies).result().orElseThrow();
        assertEquals(WarpCostClass.IN_SYSTEM, WarpCostClass.of(catalog, EARTH, MOON));
        assertEquals(WarpCostClass.IN_SYSTEM, WarpCostClass.of(catalog, MOON, PlanetaryContent.MARS));
        assertEquals(WarpCostClass.INTERSTELLAR, WarpCostClass.of(catalog, EARTH, StarSystemContent.TAU_CETI_E));
        assertEquals(WarpCostClass.INTERSTELLAR, WarpCostClass.of(catalog, StarSystemContent.TAU_CETI_E, MOON));
        // Evacuation from an orbit body that is no longer in the catalog costs the interstellar price.
        assertEquals(WarpCostClass.INTERSTELLAR, WarpCostClass.of(catalog, ModIdentity.id("removed_body"), MOON));
    }

    @Test
    void settingsAreBoundedSoAWarpIsNeverFree() {
        assertEquals(2_000_000, WarpSettings.DEFAULTS.cost(WarpCostClass.IN_SYSTEM));
        assertEquals(8_000_000, WarpSettings.DEFAULTS.cost(WarpCostClass.INTERSTELLAR));
        assertTrue(WarpSettings.DEFAULTS.enabled());
        assertThrows(IllegalArgumentException.class, () -> new WarpSettings(true, 0, 8_000_000));
        assertThrows(IllegalArgumentException.class, () -> new WarpSettings(true, 2_000_000,
                StationLimits.MAX_WARP_ENERGY + 1));
        assertThrows(IllegalArgumentException.class, () -> new WarpSettings(true, StationLimits.MIN_WARP_COST - 1,
                StationLimits.MIN_WARP_COST));
        assertEquals(StationLimits.MIN_WARP_COST, new WarpSettings(false, StationLimits.MIN_WARP_COST,
                StationLimits.MAX_WARP_COST).cost(WarpCostClass.IN_SYSTEM));
        StationState station = station();
        assertThrows(IllegalArgumentException.class, () -> new WarpQuote(new GameProfile(UUID.randomUUID(), "a"),
                station, station.orbitBody(), WarpCostClass.IN_SYSTEM, 1, true), "A quote must move the orbit");
        assertThrows(IllegalArgumentException.class, () -> new WarpQuote(new GameProfile(UUID.randomUUID(), "a"),
                station, MOON, WarpCostClass.IN_SYSTEM, 0, true));
    }

    private static WarpQuote quote(StationState station, UUID actor) {
        return new WarpQuote(new GameProfile(actor, "actor"), station, MOON, WarpCostClass.IN_SYSTEM,
                2_000_000, true);
    }

    private static StationState station() {
        StationRegistryModel model = new StationRegistryModel();
        UUID stationId = UUID.randomUUID();
        model.reserve(stationId, UUID.randomUUID(), "Warp", EARTH, 0);
        return model.commit(stationId);
    }
}
