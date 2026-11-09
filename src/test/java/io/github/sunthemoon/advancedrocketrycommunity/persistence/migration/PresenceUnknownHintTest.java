package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W328F (review check 1): a start refused because a status check failed logs the storage instruction first, then the
 * hint that applies today, so an escrow or rejoin world keeps its whole-world restrictions. The refusal is told apart by
 * the exception's typed marker. Refusals for any other reason keep exactly today's line.
 */
final class PresenceUnknownHintTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static final String CODE = "[ARCE-BETA-2000] Beta world data check blocked startup";
    private static final String UNKNOWN = " presence is unknown: its file status check failed, so its absence is not proven";
    // Today's hints as literals, so a refusal for another reason is pinned to its exact text.
    private static final String MIGRATION = "Keep the world stopped and restore the newest complete migration backup if recovery is required.";
    private static final String ESCROW = "Keep the world stopped. Escrow recovery requires a consistent whole-world backup including all terrain, entities, POI, journals and player data; never restore or delete only the escrow file.";
    private static final String REJOIN = "Keep the world stopped. Use compatible software or validated whole-world repair including all Levels, entities, POI, journals, rejoins, player data and metadata; never delete the sidecar or restore isolated files.";
    private static final String STORAGE = "Fix storage access to the world folder and restart; never delete, move or restore single files. If repair is still needed after that:";

    private Path world(String name, ManagedSavedDataType... files) throws Exception {
        Path data = Files.createDirectories(temp.resolve(name).resolve("data"));
        for (var type : files) { Files.writeString(data.resolve(type.fileName()), "not compressed NBT"); }
        return data.getParent();
    }

    /** The real no-follow check, except UNKNOWN for exactly one path. */
    private static Function<Path, CheckedSavedDataFile.Presence> failing(Path unknown) {
        Path target = unknown.toAbsolutePath().normalize();
        return path -> path.toAbsolutePath().normalize().equals(target)
                ? CheckedSavedDataFile.Presence.UNKNOWN : CheckedSavedDataFile.presence(path);
    }

    /** An unreadable data directory: its own status and every status under it are unknown. */
    private static Function<Path, CheckedSavedDataFile.Presence> failingUnder(Path directory) {
        Path target = directory.toAbsolutePath().normalize();
        return path -> path.toAbsolutePath().normalize().startsWith(target)
                ? CheckedSavedDataFile.Presence.UNKNOWN : CheckedSavedDataFile.presence(path);
    }

    private static SavedDataMigrationException refusal(Path world, Function<Path, CheckedSavedDataFile.Presence> presence) {
        return assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService(Clock.systemUTC(),
                (staged, target) -> fail("No commit"), (target, bytes) -> fail("No staging"), presence).migrate(world));
    }

    /** The single line that the refusal logs, through the same status check that refused it. */
    private static LogEvent logged(Path world, SavedDataMigrationException refusal, Function<Path, CheckedSavedDataFile.Presence> presence) {
        var events = new ArrayList<LogEvent>();
        var logger = (Logger) LogManager.getLogger("advancedrocketrycommunity");
        var context = logger.getContext(); var root = context.getConfiguration().getRootLogger();
        var appender = new AbstractAppender("w328f-hint-test", null, null, true, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start(); root.addAppender(appender, null, null); context.updateLoggers();
        try { BetaWorldMigrationEvents.logRefusal(world, refusal, presence); }
        finally { root.removeAppender(appender.getName()); context.updateLoggers(); appender.stop(); }
        assertEquals(1, events.size(), events::toString);
        var line = events.get(0).getMessage().getFormattedMessage();
        assertFalse(line.contains("\n") || line.contains("\r"), line);
        return events.get(0);
    }

    private static String line(LogEvent event) { return event.getMessage().getFormattedMessage(); }

    @Test void theConstantsMatchThisTestsLiterals() {
        assertEquals(MIGRATION, BetaWorldMigrationEvents.MIGRATION_HINT);
        assertEquals(ESCROW, BetaWorldMigrationEvents.ESCROW_HINT);
        assertEquals(REJOIN, BetaWorldMigrationEvents.REJOIN_HINT);
        assertEquals(STORAGE, BetaWorldMigrationEvents.STORAGE_HINT);
    }

    @Test void anUnknownRejoinPresenceLeadsWithStorageThenKeepsTheRejoinHint() throws Exception {
        Path world = world("rejoin");
        var presence = failing(world.resolve("data").resolve(ManagedSavedDataType.ROCKET_REJOINS.fileName()));
        var refusal = refusal(world, presence);
        assertTrue(refusal.isPresenceUnknown());
        assertEquals(ManagedSavedDataType.ROCKET_REJOINS, refusal.dataType().orElseThrow());
        var event = logged(world, refusal, presence);
        assertEquals(CODE + "; type=ROCKET_REJOINS; reason=advancedrocketrycommunity_rocket_rejoins" + UNKNOWN + ". "
                + STORAGE + " " + REJOIN, line(event));
        assertSame(refusal, event.getThrown());
    }

    @Test void anUnreadableDataDirectoryKeepsTheRejoinPrecedenceButLeadsWithStorage() throws Exception {
        // Today's precedence: the rejoin sidecar is checked first, and its status is unknown too. Even a ledger world gets
        // the rejoin restrictions, after the storage instruction.
        for (var files : new ManagedSavedDataType[][] {{}, {ManagedSavedDataType.RESOURCE_ESCROW}}) {
            Path world = world("unreadable-" + files.length, files);
            var presence = failingUnder(world.resolve("data"));
            var refusal = refusal(world, presence);
            assertTrue(refusal.isPresenceUnknown());
            assertEquals(ManagedSavedDataType.ROCKET_REJOINS, refusal.dataType().orElseThrow());
            assertEquals(CODE + "; type=ROCKET_REJOINS; reason=advancedrocketrycommunity_rocket_rejoins" + UNKNOWN + ". "
                    + STORAGE + " " + REJOIN, line(logged(world, refusal, presence)));
        }
    }

    @Test void anUnknownDataDirectoryAloneLeadsWithStorageThenTheHintItsFilesNeed() throws Exception {
        Path plain = world("directory-plain");
        var plainPresence = failing(plain.resolve("data"));
        var plainRefusal = refusal(plain, plainPresence);
        assertTrue(plainRefusal.isPresenceUnknown());
        assertTrue(plainRefusal.dataType().isEmpty());
        var plainEvent = logged(plain, plainRefusal, plainPresence);
        assertEquals(CODE + ": The world data directory" + UNKNOWN + ". " + STORAGE + " " + MIGRATION, line(plainEvent));
        assertSame(plainRefusal, plainEvent.getThrown());

        Path escrow = world("directory-escrow", ManagedSavedDataType.RESOURCE_ESCROW);
        var escrowPresence = failing(escrow.resolve("data"));
        var escrowRefusal = refusal(escrow, escrowPresence);
        assertEquals(CODE + "; type=WORLD; reason=The world data directory" + UNKNOWN + ". " + STORAGE + " " + ESCROW,
                line(logged(escrow, escrowRefusal, escrowPresence)));
    }

    @Test void anUnknownLedgerOrReceiptPresenceLeadsWithStorageThenKeepsTheEscrowHint() throws Exception {
        for (var type : new ManagedSavedDataType[] {ManagedSavedDataType.RESOURCE_ESCROW, ManagedSavedDataType.BAY_RECEIPTS}) {
            Path world = world("escrow-" + type.name());
            var presence = failing(world.resolve("data").resolve(type.fileName()));
            var refusal = refusal(world, presence);
            assertTrue(refusal.isPresenceUnknown());
            var event = logged(world, refusal, presence);
            assertEquals(CODE + "; type=" + type.name() + "; reason=" + type.dataName() + UNKNOWN + ". " + STORAGE + " " + ESCROW,
                    line(event), type::name);
            assertNull(event.getThrown(), "the escrow line stays one bounded line without a stack trace");
        }
        // Another type's unknown presence in a world that holds a ledger keeps the escrow restrictions too.
        Path world = world("escrow-stations", ManagedSavedDataType.RESOURCE_ESCROW);
        var presence = failing(world.resolve("data").resolve(ManagedSavedDataType.STATIONS.fileName()));
        assertEquals(CODE + "; type=STATIONS; reason=advancedrocketrycommunity_stations" + UNKNOWN + ". " + STORAGE + " " + ESCROW,
                line(logged(world, refusal(world, presence), presence)));
    }

    @Test void anUnknownPresenceInAPlainWorldLeadsWithStorageThenTheMigrationHint() throws Exception {
        Path world = world("plain");
        var presence = failing(world.resolve("data").resolve(ManagedSavedDataType.STATIONS.fileName()));
        var refusal = refusal(world, presence);
        assertTrue(refusal.isPresenceUnknown());
        var event = logged(world, refusal, presence);
        assertEquals(CODE + ": advancedrocketrycommunity_stations" + UNKNOWN + ". " + STORAGE + " " + MIGRATION, line(event));
        assertSame(refusal, event.getThrown());
    }

    @Test void refusalsForAnyOtherReasonKeepExactlyTodaysLine() throws Exception {
        Function<Path, CheckedSavedDataFile.Presence> real = CheckedSavedDataFile::presence;

        Path plain = world("other-plain", ManagedSavedDataType.STATIONS);
        var plainRefusal = refusal(plain, real);
        assertFalse(plainRefusal.isPresenceUnknown());
        var plainEvent = logged(plain, plainRefusal, real);
        assertEquals("[ARCE-BETA-2000] Beta world data check blocked startup: advancedrocketrycommunity_stations cannot decode "
                + "compressed SavedData. Keep the world stopped and restore the newest complete migration backup if recovery is "
                + "required.", line(plainEvent));
        assertSame(plainRefusal, plainEvent.getThrown());

        Path escrow = world("other-escrow", ManagedSavedDataType.STATIONS, ManagedSavedDataType.RESOURCE_ESCROW);
        var escrowRefusal = refusal(escrow, real);
        assertFalse(escrowRefusal.isPresenceUnknown());
        var escrowEvent = logged(escrow, escrowRefusal, real);
        assertEquals("[ARCE-BETA-2000] Beta world data check blocked startup; type=STATIONS; reason=advancedrocketrycommunity_stations "
                + "cannot decode compressed SavedData. Keep the world stopped. Escrow recovery requires a consistent whole-world "
                + "backup including all terrain, entities, POI, journals and player data; never restore or delete only the escrow "
                + "file.", line(escrowEvent));
        assertNull(escrowEvent.getThrown());

        Path rejoin = world("other-rejoin", ManagedSavedDataType.ROCKET_REJOINS);
        var rejoinRefusal = refusal(rejoin, real);
        assertFalse(rejoinRefusal.isPresenceUnknown());
        var rejoinEvent = logged(rejoin, rejoinRefusal, real);
        assertEquals("[ARCE-BETA-2000] Beta world data check blocked startup; type=ROCKET_REJOINS; "
                + "reason=advancedrocketrycommunity_rocket_rejoins cannot decode compressed SavedData. Keep the world stopped. "
                + "Use compatible software or validated whole-world repair including all Levels, entities, POI, journals, rejoins, "
                + "player data and metadata; never delete the sidecar or restore isolated files.", line(rejoinEvent));
        assertSame(rejoinRefusal, rejoinEvent.getThrown());

        // An unsafe path is not a failed status check either.
        assertFalse(new SavedDataMigrationException(MigrationDiagnosticId.UNSAFE_PATH, "symbolic").isPresenceUnknown());
    }
}
