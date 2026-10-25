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
        assertEquals("S2_PENDING", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_REJOINS, root, false));
    }
    @Test void journalCutsOnlyAfterReplacement() {
        assertEquals("", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_TRANSFERS, new CompoundTag(), false));
        assertEquals("C5", RocketReleaseNativeCuts.replacement(ManagedSavedDataType.ROCKET_TRANSFERS, new CompoundTag(), true));
    }
    @Test void recoveryCutIsIndependentOfSeedAndCannotFireOnTheSecondRestart() {
        assertEquals("C5", RocketReleaseNativeCuts.selectedBoundary("H1:C5:seed", "B1_CAPTURE"));
        assertEquals("B1_CAPTURE", RocketReleaseNativeCuts.selectedBoundary("H1:C5:recover", "B1_CAPTURE"));
        assertEquals("S2_PENDING", RocketReleaseNativeCuts.selectedBoundary("H2:C2:recover", "S2_PENDING"));
        assertEquals("", RocketReleaseNativeCuts.selectedBoundary("H1:C5:check", "B1_CAPTURE"));
        assertEquals("", RocketReleaseNativeCuts.selectedBoundary("H1:C5:recover", ""));
        assertEquals("C6", RocketReleaseNativeCuts.selectedBoundary("H1:C17:seed", ""));
        assertThrows(IllegalArgumentException.class,
                () -> RocketReleaseNativeCuts.selectedBoundary("H1:C5:recover", "C0"));
    }
    @Test void restoredPendingBatchArmsWithoutB0ButVerifiedSeatsDoNot() {
        var root = RocketRejoinCodec.decode(RocketRejoinCodecTest.encoded());
        assertTrue(RocketReleaseNativeCuts.needsRecoveryArm(root));
        for (var entry : root.entries()) {
            root = root.update(new RocketRejoinRoot.Entry(entry.release(), entry.player(), entry.seatIndex(),
                    entry.placement(), RocketRejoinRoot.Seat.VERIFIED, entry.attempt()));
        }
        assertFalse(RocketReleaseNativeCuts.needsRecoveryArm(root));
        assertFalse(RocketReleaseNativeCuts.needsRecoveryArm(RocketRejoinRoot.empty()));
    }
    @Test void recoveredS2HasAPreReplacementCutWhileTheWriteIsPending() {
        assertEquals("S2_PENDING", RocketReleaseNativeCuts.replacement(
                ManagedSavedDataType.ROCKET_REJOINS, new CompoundTag(), false));
    }
}
