package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.MoonLandingSourceTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FirstEventUnknownGenerationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectsOnlyAnUnrelatedRecordChangeAfterTheSameDeliveredEvent() {
        var changed = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.ROCKET_TRANSFERS,
                MoonLandingSourceTestSupport.committedMoonJournalRootTwo()).payload();
        var event = FirstEventFixtures.moon(44, List.of(FirstEventCutSeams.P1, FirstEventCutSeams.P2));
        String key = ManagedSavedDataType.FIRST_MOON_LANDING_KEY;
        changed.put(key, FirstEventOutbox.elected(event).delivered().encode(event.kind()));
        var baseline = changed.copy(); baseline.put("transfers", new ListTag());
        assertTrue(RocketTransferSavedData.load(baseline.copy()).operational());
        assertTrue(RocketTransferSavedData.load(changed.copy()).operational());
        assertFalse(FirstEventCutSeams.unrelatedGeneration(baseline.copy(), baseline));
        assertTrue(FirstEventCutSeams.unrelatedGeneration(changed, baseline));
        var election = changed.copy(); election.put(key, FirstEventOutbox.elected(event).encode(event.kind()));
        assertFalse(FirstEventCutSeams.unrelatedGeneration(election, baseline));
        var other = changed.copy();
        var different = FirstEventFixtures.moon(45, List.of(FirstEventCutSeams.P1));
        other.put(key, FirstEventOutbox.elected(different).delivered().encode(different.kind()));
        assertFalse(FirstEventCutSeams.unrelatedGeneration(other, baseline));
    }
}
