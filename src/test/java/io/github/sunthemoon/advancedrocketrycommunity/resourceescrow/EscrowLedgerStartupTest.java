package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W328 (W327 audit L2) on real files: the escrow ledger's startup lookup never reads an unknown presence as absent. The
 * PROPULSION delegate stays NOT_LOADED, nothing can reserve, so no ledger is created and nothing is replaced, and a
 * later start whose status check works loads the very same file unchanged.
 */
final class EscrowLedgerStartupTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = EscrowTestFixtures.id(99);
    private static final String WARN = "WARN ARCE_ESCROW_LEDGER_NOT_LOADED reason=";

    /** One server's admission authority, registered as {@code EscrowRuntime} registers it: PROPULSION, then BAY. */
    private static final class Server {
        final EscrowAdmissionAuthority authority = new EscrowAdmissionAuthority();
        final EscrowAdmissionAuthority.Delegate propulsion = authority.register(EscrowAdmissionAuthority.Consumer.PROPULSION);
        final EscrowAdmissionAuthority.Delegate bay = authority.register(EscrowAdmissionAuthority.Consumer.BAY);
        Server() { bay.noFile(); } // A world without receipts, so only the ledger decides admission.
    }

    private Path ledgerFile(String name) throws Exception {
        return Files.createDirectories(temp.resolve(name)).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }

    private static void assertEveryAdmissionRefused(Server server) {
        for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
            assertEquals(EscrowAdmissionAuthority.Result.UNAVAILABLE,
                    server.authority.checkReserve(EscrowTestFixtures.id(1), OWNER, EscrowTestFixtures.id(2), consumer), consumer::name);
        }
        // EscrowLedgerCreation's own reservation: without it, no first fill or cargo admission creates a ledger.
        assertEquals(EscrowAdmissionAuthority.Result.UNAVAILABLE, server.authority.tryReserve(EscrowTestFixtures.id(1), OWNER,
                EscrowTestFixtures.id(2), EscrowAdmissionAuthority.Consumer.PROPULSION));
        assertEquals(EscrowAdmissionAuthority.Retention.UNAVAILABLE, server.authority.checkRetain(EscrowTestFixtures.id(1), OWNER));
        assertEquals(0, server.authority.pendingCount(), "nothing was charged");
    }

    @Test void anUnknownPresenceStaysNotLoadedCreatesAndReplacesNothingAndACleanStartLoadsTheFileUnchanged() throws Exception {
        Path file = ledgerFile("existing");
        var ledger = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1), EscrowTestFixtures.operation(2));
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(ledger));
        var before = StartupLogLines.tree(file.getParent());

        // Vanilla's lookup reads the failed status check as no file, so no root is in memory; the check itself fails.
        var server = new Server();
        try (var lines = new StartupLogLines("ARCE_ESCROW_LEDGER_NOT_LOADED")) {
            var reason = EscrowLedgerStartup.start(server.propulsion, null, root -> fail("nothing to bind"),
                    path -> CheckedSavedDataFile.Presence.UNKNOWN, file);
            assertEquals(Optional.of(EscrowNotLoadedReason.PRESENCE_UNKNOWN), reason);
            assertEquals(1, lines.lines.size(), lines.lines::toString);
            assertTrue(lines.lines.get(0).startsWith(WARN + "PRESENCE_UNKNOWN file=" + ManagedSavedDataType.RESOURCE_ESCROW.fileName()
                    + "; the file's status could not be checked"), lines.lines::toString);
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, server.propulsion.state(), "an unknown presence is never NO_FILE");
        assertEveryAdmissionRefused(server);
        assertEquals(before, StartupLogLines.tree(file.getParent()), "no file was created, replaced or changed");

        // The next start's status check works: the same root loads, binds and answers through the shared authority.
        var restart = new Server();
        var loaded = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
        var bound = new ArrayList<EscrowCoordinator>();
        try (var writer = new EscrowLedgerWriter(file, loaded.ledger().writerGeneration())) {
            assertEquals(Optional.empty(), EscrowLedgerStartup.start(restart.propulsion, loaded,
                    root -> bound.add(new EscrowCoordinator(root, writer, restart.propulsion)), CheckedSavedDataFile::presence, file));
            assertEquals(1, bound.size());
            assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, restart.propulsion.state());
            assertEquals(ledger, loaded.ledger(), "the clean start loaded the existing ledger");
            assertEquals(2, restart.authority.pendingCount(), "its two fills hold their slots again");
            assertEquals(Optional.empty(), EscrowLedgerStartup.start(restart.propulsion, null, root -> fail("already bound"),
                    path -> fail("no status check for a bound ledger"), file), "a bound ledger is never reclassified");
            bound.get(0).close();
        }
        assertEquals(before, StartupLogLines.tree(file.getParent()), "the clean start left the file unchanged");
    }

    @Test void anUnknownPresenceWithNoFileCreatesNoneAndOnlyAProvenAbsenceGivesNoFile() throws Exception {
        Path file = ledgerFile("absent");
        var server = new Server();
        EscrowLedgerStartup.start(server.propulsion, null, root -> fail("nothing to bind"), path -> CheckedSavedDataFile.Presence.UNKNOWN, file);
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, server.propulsion.state());
        assertEveryAdmissionRefused(server);
        assertEquals(List.of(""), new ArrayList<>(StartupLogLines.tree(file.getParent()).keySet()), "the directory stays empty");

        var restart = new Server();
        try (var lines = new StartupLogLines("ARCE_ESCROW_LEDGER_NOT_LOADED")) {
            assertEquals(Optional.empty(), EscrowLedgerStartup.start(restart.propulsion, null, root -> fail("nothing to bind"),
                    CheckedSavedDataFile::presence, file));
            assertEquals(List.of(), lines.lines, "a proven absence logs nothing");
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, restart.propulsion.state(), "the real check proves the absence");
        assertEquals(EscrowAdmissionAuthority.Result.RESERVED, restart.authority.checkReserve(EscrowTestFixtures.id(1), OWNER,
                EscrowTestFixtures.id(2), EscrowAdmissionAuthority.Consumer.PROPULSION), "a known-empty ledger admits again");
        assertFalse(Files.exists(file), "the lookup itself creates nothing");
    }

    @Test void aPresentFileThatDidNotLoadKeepsItsOwnReason() throws Exception {
        Path file = ledgerFile("unloadable");
        Files.writeString(file, "not a ledger");
        var server = new Server();
        try (var lines = new StartupLogLines("ARCE_ESCROW_LEDGER_NOT_LOADED")) {
            assertEquals(Optional.of(EscrowNotLoadedReason.LOAD_FAILED), EscrowLedgerStartup.start(server.propulsion, null,
                    root -> fail("nothing to bind"), CheckedSavedDataFile::presence, file));
            assertEquals(1, lines.lines.size(), lines.lines::toString);
            assertTrue(lines.lines.get(0).startsWith(WARN + "LOAD_FAILED file=" + ManagedSavedDataType.RESOURCE_ESCROW.fileName()
                    + "; the file exists but did not load"), lines.lines::toString);
        }
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, server.propulsion.state());
        assertEveryAdmissionRefused(server);
        assertEquals("not a ledger", Files.readString(file));
        assertThrows(IllegalArgumentException.class, () -> EscrowNotLoadedReason.of(CheckedSavedDataFile.Presence.ABSENT));
    }
}
