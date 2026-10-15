package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FirstEventCaseExportsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void everyOverlayIsValidButKeepsItsSpecifiedProofCondition() {
        var journal = new RocketTransferSavedData();
        var moon = FirstEventFixtures.moon(44, List.of(FirstEventCutSeams.P1, FirstEventCutSeams.P2));
        journal.electFirstMoonLanding(moon); journal.deliverFirstMoonLanding(moon);
        var stations = new StationRegistrySavedData();
        stations.reserve(FirstEventFixtures.id(9_001), FirstEventFixtures.id(9_002), "Budget source", ModIdentity.id("earth"), 0);
        stations.commit(FirstEventFixtures.id(9_001));
        CompoundTag preWarp = stations.save(new CompoundTag());
        var warp = FirstEventFixtures.warp(45, List.of(FirstEventCutSeams.P1, FirstEventCutSeams.P2));
        CompoundTag warped = preWarp.copy();
        warped.getCompound("orbital_clock").putLong("logical_tick", warp.orbitalTick());
        warped.put("classic_first_warp", FirstEventOutbox.elected(warp).delivered().encode(warp.kind()));
        var fixtures = FirstEventCaseExports.fixtures(journal.save(new CompoundTag()), warped, preWarp);
        assertEquals(9, fixtures.size());
        fixtures.forEach((id, roots) -> {
            assertTrue(RocketTransferSavedData.load(roots.get(FirstEventCaseExports.JOURNAL).copy()).operational(), id);
            assertTrue(StationRegistrySavedData.load(roots.get(FirstEventCaseExports.STATIONS).copy()).operational(), id);
            if (roots.containsKey(FirstEventCaseExports.PROGRESSION)) {
                assertTrue(ClassicProgressionSavedData.load(roots.get(FirstEventCaseExports.PROGRESSION).copy()).operational(), id);
            }
        });
        assertFalse(fixtures.get("FP2").containsKey(FirstEventCaseExports.PROGRESSION));
        assertTrue(StationRegistrySavedData.load(fixtures.get("FP4").get(FirstEventCaseExports.STATIONS)).firstWarpRam().isEmpty());
        assertEquals(FirstEventOutbox.State.ELECTED, RocketTransferSavedData.load(fixtures.get("FP9").get(FirstEventCaseExports.JOURNAL)).firstMoonLandingRam().state());
    }
}
