package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import net.minecraft.nbt.CompoundTag;

/** W44 native fixture FM11b: a historical journal root 2 holding one COMMITTED Moon reservation. */
public final class MoonLandingSourceTestSupport {
    private MoonLandingSourceTestSupport() {
    }

    public static CompoundTag committedMoonJournalRootTwo() {
        var journal = new RocketTransferSavedData();
        journal.put(RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record()));
        CompoundTag two = journal.save(new CompoundTag());
        two.remove(RocketTransferSavedData.FIRST_MOON_LANDING);
        two.putInt("schema_version", 2);
        two.putString("format_epoch", SavedDataSchemaMigrator.FORMAT_EPOCH);
        return two;
    }
}
