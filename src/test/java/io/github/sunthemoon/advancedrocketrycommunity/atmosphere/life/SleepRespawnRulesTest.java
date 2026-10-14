package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Cell;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Options;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnProbe;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.StationAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SleepRespawnRulesTest {
    private static final Options ALLOW = new Options(true, false);
    private static final Options FORCE = new Options(true, true);

    @Test
    void sleepNeedsEveryCellLoadedPermittedAndBreathable() {
        Cell open = new Cell(true, BreathabilityState.BREATHABLE, StationAccess.NOT_APPLICABLE);
        assertEquals(SleepRefusal.NONE, SleepRespawnRules.admitSleep(List.of(open, open)));
        assertEquals(SleepRefusal.NONE, SleepRespawnRules.admitSleep(List.of(
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.OUTSIDE),
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.VISIT))));
        assertEquals(SleepRefusal.UNBREATHABLE, SleepRespawnRules.admitSleep(List.of(open,
                new Cell(true, BreathabilityState.VACUUM, StationAccess.NOT_APPLICABLE))));
        assertEquals(SleepRefusal.PENDING, SleepRespawnRules.admitSleep(List.of(
                new Cell(true, BreathabilityState.PENDING, StationAccess.NOT_APPLICABLE), open)));
        assertEquals(SleepRefusal.UNAVAILABLE, SleepRespawnRules.admitSleep(List.of(open, Cell.unloaded())));
        assertEquals(SleepRefusal.STATION_DENIED, SleepRespawnRules.admitSleep(List.of(
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.DENIED))));
        // An unavailable registry cannot show a cell is outside every station, even in a supplied room.
        assertEquals(SleepRefusal.STATION_UNAVAILABLE, SleepRespawnRules.admitSleep(List.of(
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.UNAVAILABLE))));
        assertThrows(IllegalArgumentException.class, () -> SleepRespawnRules.admitSleep(List.of()));
    }

    @Test
    void sleepReasonDoesNotDependOnCellOrder() {
        List<Cell> cells = List.of(
                new Cell(true, BreathabilityState.VACUUM, StationAccess.OUTSIDE),
                new Cell(true, BreathabilityState.PENDING, StationAccess.OUTSIDE),
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.DENIED),
                new Cell(true, BreathabilityState.BREATHABLE, StationAccess.UNAVAILABLE),
                Cell.unloaded());
        SleepRefusal[] expected = {SleepRefusal.UNBREATHABLE, SleepRefusal.PENDING, SleepRefusal.STATION_DENIED,
                SleepRefusal.STATION_UNAVAILABLE, SleepRefusal.UNAVAILABLE};
        for (int last = 0; last < cells.size(); last++) {
            List<Cell> prefix = cells.subList(0, last + 1);
            assertEquals(expected[last], SleepRespawnRules.admitSleep(prefix));
            List<Cell> reversed = new ArrayList<>(prefix);
            java.util.Collections.reverse(reversed);
            assertEquals(expected[last], SleepRespawnRules.admitSleep(reversed));
        }
    }

    @Test
    void disabledRespawnAsksNothingEvenWhenForced() {
        for (Options options : List.of(Options.DEFAULTS, new Options(false, true))) {
            FakeProbe probe = new FakeProbe();
            assertEquals(RespawnOutcome.DISABLED, SleepRespawnRules.decideRespawn(options, probe));
            assertTrue(probe.asked.isEmpty(), "Disabled respawn read " + probe.asked);
        }
        assertFalse(Options.DEFAULTS.allow());
        assertFalse(Options.DEFAULTS.force());
    }

    @Test
    void allowAcceptsOnlyBreathableArrival() {
        assertEquals(RespawnOutcome.ACCEPT, SleepRespawnRules.decideRespawn(ALLOW, new FakeProbe()));
        assertEquals(RespawnOutcome.UNBREATHABLE, SleepRespawnRules.decideRespawn(ALLOW,
                new FakeProbe().with(probe -> probe.air = BreathabilityState.VACUUM)));
        assertEquals(RespawnOutcome.PENDING, SleepRespawnRules.decideRespawn(ALLOW,
                new FakeProbe().with(probe -> probe.air = BreathabilityState.PENDING)));
        assertEquals(RespawnOutcome.ACCEPT, SleepRespawnRules.decideRespawn(ALLOW,
                new FakeProbe().with(probe -> probe.station = StationAccess.VISIT)));
        assertEquals(RespawnOutcome.ACCEPT, SleepRespawnRules.decideRespawn(ALLOW,
                new FakeProbe().with(probe -> probe.station = StationAccess.OUTSIDE)));
    }

    @Test
    void forceBypassesOnlyTheAirCheck() {
        for (BreathabilityState air : BreathabilityState.values()) {
            FakeProbe probe = new FakeProbe().with(fake -> fake.air = air);
            assertEquals(RespawnOutcome.ACCEPT, SleepRespawnRules.decideRespawn(FORCE, probe));
            assertFalse(probe.asked.contains("air"), "Force still asked for air");
        }
        assertEquals(RespawnOutcome.MISSING_BODY, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.bodyPresent = false)));
        assertEquals(RespawnOutcome.UNLOADED, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.loaded = false)));
        assertEquals(RespawnOutcome.NO_SPAWN_BLOCK, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.spawnBlock = false)));
        assertEquals(RespawnOutcome.OBSTRUCTED, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.arrivalFound = false)));
        assertEquals(RespawnOutcome.HAZARD, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.hazard = true)));
        assertEquals(RespawnOutcome.STATION_DENIED, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.station = StationAccess.DENIED)));
        assertEquals(RespawnOutcome.STATION_UNAVAILABLE, SleepRespawnRules.decideRespawn(FORCE,
                new FakeProbe().with(probe -> probe.station = StationAccess.UNAVAILABLE)));
    }

    @Test
    void factsAreAskedInOrderAndNothingIsReadAfterARefusal() {
        FakeProbe accepted = new FakeProbe();
        SleepRespawnRules.decideRespawn(ALLOW, accepted);
        assertEquals(List.of("body", "loaded", "spawnBlock", "arrival", "hazard", "station", "air"), accepted.asked);

        FakeProbe unloaded = new FakeProbe().with(probe -> probe.loaded = false);
        assertEquals(RespawnOutcome.UNLOADED, SleepRespawnRules.decideRespawn(FORCE, unloaded));
        assertEquals(List.of("body", "loaded"), unloaded.asked, "An unloaded arrival had its blocks read");

        FakeProbe missing = new FakeProbe().with(probe -> probe.bodyPresent = false);
        SleepRespawnRules.decideRespawn(ALLOW, missing);
        assertEquals(List.of("body"), missing.asked);
    }

    @Test
    void worseKeepsTheMostRestrictiveFact() {
        assertEquals(StationAccess.DENIED, SleepRespawnRules.worse(StationAccess.OUTSIDE, StationAccess.DENIED));
        assertEquals(StationAccess.UNAVAILABLE, SleepRespawnRules.worse(StationAccess.UNAVAILABLE, StationAccess.VISIT));
        assertEquals(StationAccess.VISIT, SleepRespawnRules.worse(StationAccess.OUTSIDE, StationAccess.VISIT));
        assertEquals(StationAccess.NOT_APPLICABLE,
                SleepRespawnRules.worse(StationAccess.NOT_APPLICABLE, StationAccess.NOT_APPLICABLE));
        assertEquals(BreathabilityState.VACUUM,
                SleepRespawnRules.worse(BreathabilityState.PENDING, BreathabilityState.VACUUM));
        assertEquals(BreathabilityState.PENDING,
                SleepRespawnRules.worse(BreathabilityState.BREATHABLE, BreathabilityState.PENDING));
        assertEquals(BreathabilityState.BREATHABLE,
                SleepRespawnRules.worse(BreathabilityState.BREATHABLE, BreathabilityState.BREATHABLE));
    }

    @Test
    void everyRefusalHasAStableKey() {
        assertEquals("unbreathable", SleepRefusal.UNBREATHABLE.key());
        assertEquals("station_denied", RespawnOutcome.STATION_DENIED.key());
        assertTrue(RespawnOutcome.ACCEPT.accepted());
        for (RespawnOutcome outcome : RespawnOutcome.values()) {
            assertEquals(outcome == RespawnOutcome.ACCEPT, outcome.accepted());
        }
    }

    private static final class FakeProbe implements RespawnProbe {
        private final List<String> asked = new ArrayList<>();
        private boolean bodyPresent = true;
        private boolean loaded = true;
        private boolean spawnBlock = true;
        private boolean arrivalFound = true;
        private boolean hazard;
        private StationAccess station = StationAccess.NOT_APPLICABLE;
        private BreathabilityState air = BreathabilityState.BREATHABLE;

        private FakeProbe with(Consumer<FakeProbe> change) {
            change.accept(this);
            return this;
        }

        @Override public boolean bodyPresent() { asked.add("body"); return bodyPresent; }
        @Override public boolean loaded() { asked.add("loaded"); return loaded; }
        @Override public boolean spawnBlock() { asked.add("spawnBlock"); return spawnBlock; }
        @Override public boolean arrivalFound() { asked.add("arrival"); return arrivalFound; }
        @Override public boolean hazard() { asked.add("hazard"); return hazard; }
        @Override public StationAccess station() { asked.add("station"); return station; }
        @Override public BreathabilityState air() { asked.add("air"); return air; }
    }
}
