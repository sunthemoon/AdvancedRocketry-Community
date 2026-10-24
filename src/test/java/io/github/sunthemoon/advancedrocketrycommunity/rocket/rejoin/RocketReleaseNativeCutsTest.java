package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class RocketReleaseNativeCutsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }
    @Test void journalCheckpointNamesOnlyTheReleaseProjectionNotAnInterleavedOutboxWrite() {
        var sidecar = RocketRejoinCodecTest.encoded();
        sidecar.getList("batches", 10).getCompound(0).getCompound("scrubbed").putString("checksum", "d".repeat(64));
        var root = RocketRejoinCodec.decode(sidecar); var batch = root.batches().get(0);
        var record = new CompoundTag(); record.putUUID("transfer_id", batch.transfer());
        record.putString("checksum", batch.before().checksum()); record.putString("phase", "PREPARED");
        var transfers = new ListTag(); transfers.add(record); var journal = new CompoundTag(); journal.put("transfers", transfers);
        assertFalse(RocketReleaseNativeCuts.journalRelease(journal, root));
        record.putString("checksum", batch.scrubbed().checksum());
        assertTrue(RocketReleaseNativeCuts.journalRelease(journal, root));
        record.putString("phase", "COMMITTED"); assertFalse(RocketReleaseNativeCuts.journalRelease(journal, root));
        transfers.clear(); assertTrue(RocketReleaseNativeCuts.journalRelease(journal, root));
        assertFalse(RocketReleaseNativeCuts.journalRelease(journal, RocketRejoinRoot.empty()));
    }
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
