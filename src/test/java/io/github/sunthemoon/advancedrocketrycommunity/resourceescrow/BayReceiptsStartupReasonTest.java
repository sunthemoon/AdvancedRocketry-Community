package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.id;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.receipt;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W328 (W327 audit L1) on real files: an unknown receipt presence keeps the bay index NOT_LOADED (R390) and its WARN
 * says so, instead of claiming that a file exists. Nothing is created or replaced, and a later start whose status check
 * works loads the very same file unchanged.
 */
final class BayReceiptsStartupReasonTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = id(9);
    private static final String FILE = ManagedSavedDataType.BAY_RECEIPTS.fileName();

    private static BayReceipts receipts(EscrowAdmissionAuthority authority) {
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
        return new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
    }
    private Path receiptFile(String name) throws Exception { return Files.createDirectories(temp.resolve(name)).resolve(FILE); }

    @Test void anUnknownPresenceStaysNotLoadedWithItsOwnReasonAndACleanStartLoadsTheFileUnchanged() throws Exception {
        Path file = receiptFile("existing");
        var rows = List.of(receipt(1, OWNER, id(30), BayReceipt.Phase.INTENT), receipt(2, OWNER, id(31), BayReceipt.Phase.REGISTERED));
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.BAY_RECEIPTS, () -> BayReceiptCodec.encode(BayReceiptFixtures.root(rows)));
        var before = StartupLogLines.tree(file.getParent());

        var authority = new EscrowAdmissionAuthority(); var bay = receipts(authority);
        try (var lines = new StartupLogLines("ARCE_BAY_RECEIPTS_")) {
            bay.start(null, CheckedSavedDataFile.Presence.UNKNOWN, file, 0);
            assertEquals(2, lines.lines.size(), lines.lines::toString);
            assertTrue(lines.lines.get(0).startsWith("WARN ARCE_BAY_RECEIPTS_NOT_LOADED reason=PRESENCE_UNKNOWN file=" + FILE
                    + "; the file's status could not be checked, so its absence is not proven"), lines.lines::toString);
            assertFalse(lines.lines.get(0).contains("exists"), "an unknown presence never claims that the file exists");
            assertEquals("INFO ARCE_BAY_RECEIPTS_INDEX state=NOT_LOADED receipts=0", lines.lines.get(1));
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, bay.state(), "R390: an unknown presence is never NO_FILE");
        assertEquals(EscrowNotLoadedReason.PRESENCE_UNKNOWN, bay.notLoadedReason());
        assertNull(bay.store());
        assertThrows(IllegalArgumentException.class, () -> bay.createForAdmission(file, 0), "no root is created over an unproven absence");
        assertTrue(bay.blocksInsertion(id(500)), "every package stays refused");
        assertTrue(bay.holds(id(600), () -> fail("NOT_LOADED needs no journal")), "every rocket stays held");
        for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
            assertEquals(EscrowAdmissionAuthority.Result.UNAVAILABLE, authority.checkReserve(id(1), OWNER, id(2), consumer), consumer::name);
        }
        assertEquals(before, StartupLogLines.tree(file.getParent()), "no file was created, replaced or changed");

        // The next start's status check works: the same root loads and indexes both receipts.
        var restart = receipts(new EscrowAdmissionAuthority());
        var payload = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.BAY_RECEIPTS).orElseThrow();
        restart.start(BayReceiptSavedData.load(payload), CheckedSavedDataFile.presence(file), file, 0);
        try {
            assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, restart.state());
            assertNull(restart.notLoadedReason());
            assertEquals(new HashSet<>(rows), new HashSet<>(restart.store().receipts()), "the clean start loaded the existing receipts");
        } finally {
            restart.stop();
        }
        assertEquals(before, StartupLogLines.tree(file.getParent()), "the clean start left the file unchanged");
    }

    @Test void aPresentFileThatDidNotLoadNamesLoadFailedAndAProvenAbsenceLogsNoWarning() throws Exception {
        Path file = receiptFile("unloadable");
        Files.writeString(file, "not receipts");
        var bay = receipts(new EscrowAdmissionAuthority());
        try (var lines = new StartupLogLines("ARCE_BAY_RECEIPTS_NOT_LOADED")) {
            bay.start(null, CheckedSavedDataFile.presence(file), file, 0);
            assertEquals(List.of("WARN ARCE_BAY_RECEIPTS_NOT_LOADED reason=LOAD_FAILED file=" + FILE + "; the file exists but did not "
                    + "load, so bay admission and package insertion stay refused for this session"), lines.lines);
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, bay.state());
        assertEquals(EscrowNotLoadedReason.LOAD_FAILED, bay.notLoadedReason());
        assertEquals("not receipts", Files.readString(file));

        Path absent = receiptFile("absent");
        var noFile = receipts(new EscrowAdmissionAuthority());
        try (var lines = new StartupLogLines("ARCE_BAY_RECEIPTS_NOT_LOADED")) {
            noFile.start(null, CheckedSavedDataFile.presence(absent), absent, 0);
            assertEquals(List.of(), lines.lines);
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, noFile.state());
        assertNull(noFile.notLoadedReason());
        assertFalse(Files.exists(absent), "the lookup itself creates nothing");
    }
}
