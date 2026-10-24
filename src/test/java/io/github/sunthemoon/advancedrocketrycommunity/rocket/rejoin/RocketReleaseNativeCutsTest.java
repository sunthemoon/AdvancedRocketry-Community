package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class RocketReleaseNativeCutsTest {
    @Test void sidecarCutsDistinguishPendingFromVerifiedAndEmptyRetirement() {
        var root = new CompoundTag(); var entries = new ListTag(); var entry = new CompoundTag();
        entry.putString("seat", "PENDING"); entries.add(entry); root.put("entries", entries);
        assertEquals("C1", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, false));
        assertEquals("C2", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, true));
        entry.putString("seat", "VERIFIED");
        assertEquals("C6", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, true));
        entries.clear();
        assertEquals("C6", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, true));
        assertEquals("", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, false));
    }
    @Test void journalCutsOnlyAfterReplacement() {
        assertEquals("", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_TRANSFERS, new CompoundTag(), false));
        assertEquals("C5", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_TRANSFERS, new CompoundTag(), true));
    }
}
