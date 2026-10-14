package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSourceView;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0 for the Moon landing source (sections 2.1-2.6 and 3.6). */
class MoonLandingSourceTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private static final String OUTBOX = RocketTransferSavedData.FIRST_MOON_LANDING;
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void rootThreeCarriesTheEmptyOutboxAndRejectsEveryWrongShape() {
        CompoundTag current = new RocketTransferSavedData().save(new CompoundTag());
        assertEquals(3, current.getInt("schema_version"));
        assertEquals("v1.8.0-first-moon-landing", current.getString("format_epoch"));
        assertEquals(Set.of("schema_version", "state"), current.getCompound(OUTBOX).getAllKeys());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, SavedDataSchemaMigrator.migrate(TYPE, current).status());
        var loaded = RocketTransferSavedData.load(current);
        assertTrue(loaded.operational());
        assertFalse(loaded.isDirty(), "a current root is a no-op");
        assertEquals(FirstEventOutbox.empty(), loaded.firstMoonLandingRam());

        CompoundTag missing = current.copy();
        missing.remove(OUTBOX);
        assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, missing));
        assertFalse(RocketTransferSavedData.load(missing).operational());
        CompoundTag invalid = current.copy();
        invalid.getCompound(OUTBOX).putString("state", "LANDED");
        assertFalse(RocketTransferSavedData.load(invalid).operational());
        assertEquals(invalid, RocketTransferSavedData.load(invalid).save(new CompoundTag()), "blocked bytes are kept");
        CompoundTag oldWithOutbox = rootTwo(current);
        oldWithOutbox.put(OUTBOX, current.getCompound(OUTBOX).copy());
        assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, oldWithOutbox));
    }

    /** FM11a/FM11b A0: roots 1 and 2 gain an EMPTY outbox, keep every record and never fabricate a landing. */
    @Test void rootsOneAndTwoMigrateOnceToAnEmptyOutboxAndKeepEveryRecord() {
        var journal = new RocketTransferSavedData();
        journal.put(RocketTransferSavedDataTest.committed(RocketTransferSavedDataTest.record()));
        CompoundTag current = journal.save(new CompoundTag());
        CompoundTag two = rootTwo(current);
        var migrated = SavedDataSchemaMigrator.migrate(TYPE, two);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migrated.status());
        assertEquals(current, migrated.payload(), "root 2 to 3 adds only the EMPTY outbox and the stamp");
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                SavedDataSchemaMigrator.migrate(TYPE, migrated.payload()).status());
        var reloaded = RocketTransferSavedData.load(two);
        assertTrue(reloaded.operational());
        assertTrue(reloaded.isDirty(), "an in-session migration schedules one canonical save");
        assertEquals(FirstEventOutbox.empty(), reloaded.firstMoonLandingRam(), "a COMMITTED Moon record is not a landing");
        assertEquals(current.get("transfers"), reloaded.save(new CompoundTag()).get("transfers"));

        CompoundTag one = two.copy();
        one.putInt("schema_version", 1);
        one.remove("format_epoch");
        CompoundTag fromOne = SavedDataSchemaMigrator.migrate(TYPE, one).payload();
        CompoundTag expected = current.copy();
        expected.putInt("migrated_from_schema", 1);
        assertEquals(expected, fromOne);
        CompoundTag broken = two.copy();
        broken.getList("transfers", 10).getCompound(0).putString("checksum", "0".repeat(64));
        assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, broken),
                "the old root is validated under its checksum-first rules before staging");
    }

    @Test void theDurableViewIsTheLatestExactConfirmationNeverRam() throws Exception {
        var journal = new RocketTransferSavedData();
        assertEquals(FirstEventSourceView.unconfirmed(), journal.durableFirstMoonLanding());
        journal.saveChecked(target(), atomic());
        assertEquals(FirstEventSourceView.known(FirstEventOutbox.empty()), journal.durableFirstMoonLanding());

        var event = event(1, 2);
        assertTrue(journal.electFirstMoonLanding(event));
        assertFalse(journal.electFirstMoonLanding(event(2, 1)), "the single slot never elects twice");
        assertEquals(FirstEventOutbox.State.ELECTED, journal.firstMoonLandingRam().state());
        assertEquals(FirstEventSourceView.known(FirstEventOutbox.empty()), journal.durableFirstMoonLanding(),
                "unconfirmed RAM is never authority");
        assertFalse(journal.acknowledgedCurrent());
        journal.saveChecked(target(), atomic());
        assertEquals(FirstEventSourceView.known(FirstEventOutbox.elected(event)), journal.durableFirstMoonLanding());

        assertFalse(journal.deliverFirstMoonLanding(event(3, 1)));
        long sequence = journal.mutationSequence();
        assertTrue(journal.deliverFirstMoonLanding(event));
        assertEquals(sequence + 1, journal.mutationSequence());
        assertTrue(journal.deliverFirstMoonLanding(event), "DELIVERED(E) is idempotent");
        assertEquals(sequence + 1, journal.mutationSequence());
        assertEquals(FirstEventOutbox.State.ELECTED, journal.durableFirstMoonLanding().outbox().state());
        journal.saveChecked(target(), atomic());
        var delivered = FirstEventOutbox.elected(event).delivered();
        assertEquals(FirstEventSourceView.known(delivered), journal.durableFirstMoonLanding());

        // An unrelated UNKNOWN after DELIVERED suspends proof; the next exact confirmation restores it online.
        journal.put(RocketTransferSavedDataTest.record());
        journal.saveChecked(target(), new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            Files.write(target, new byte[] {1, 2, 3});
            throw new IOException("injected unreadable outcome");
        }));
        assertEquals(RocketTransferSavedData.WriteOutcome.UNKNOWN, journal.checkedWriteResult().outcome());
        assertEquals(FirstEventSourceView.unconfirmed(), journal.durableFirstMoonLanding());
        assertEquals(delivered, journal.firstMoonLandingRam(), "the event and receipt authority are preserved");
        journal.saveChecked(target(), atomic());
        assertEquals(FirstEventSourceView.known(delivered), journal.durableFirstMoonLanding());
        assertEquals(delivered.event(), RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(target(), TYPE)
                .orElseThrow()).firstMoonLandingRam().event());
    }

    /** FM12a A0: a retained root event with no transfer records still confirms through the baseline. */
    @Test void aRetainedEventWithZeroRecordsConfirmsThroughTheBaseline() {
        var source = new RocketTransferSavedData();
        var event = event(1, 0);
        source.electFirstMoonLanding(event);
        CompoundTag payload = source.save(new CompoundTag());
        var reloaded = RocketTransferSavedData.load(payload);
        assertTrue(reloaded.entries().isEmpty());
        assertFalse(reloaded.isDirty());
        assertEquals(FirstEventOutbox.elected(event), reloaded.firstMoonLandingRam());
        assertEquals(FirstEventSourceView.unconfirmed(), reloaded.durableFirstMoonLanding(),
                "a loaded root is not proof before its baseline");
        reloaded.saveChecked(target(), atomic());
        assertEquals(FirstEventSourceView.known(FirstEventOutbox.elected(event)), reloaded.durableFirstMoonLanding());
        var capture = reloaded.capture();
        assertSame(reloaded.firstMoonLandingRam(), capture.firstMoonLanding(), "capture keeps the immutable outbox");
    }

    @Test void unreadableAndBlockedRootsNeverBaselineElectOrWrite() throws Exception {
        var unreadable = RocketTransferSavedData.created(ManagedRootPresence.UNREADABLE);
        assertFalse(unreadable.operational());
        assertEquals(ManagedRootPresence.UNREADABLE, unreadable.presence());
        assertEquals(FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_UNREADABLE),
                unreadable.durableFirstMoonLanding());
        assertFalse(unreadable.electFirstMoonLanding(event(1, 1)));
        assertFalse(unreadable.requestWrite(target(), atomic()));
        assertThrows(IllegalStateException.class, () -> unreadable.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> unreadable.put(RocketTransferSavedDataTest.record()));
        assertFalse(Files.exists(target()));
        CompoundTag future = new RocketTransferSavedData().save(new CompoundTag());
        future.putInt("schema_version", 4);
        var blocked = RocketTransferSavedData.load(future);
        assertEquals(ManagedRootPresence.BLOCKED, blocked.presence());
        assertEquals(FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_BLOCKED),
                blocked.durableFirstMoonLanding());
        assertFalse(blocked.electFirstMoonLanding(event(1, 1)));
    }

    /** A0 case a for the journal: a present file whose load throws, a failing stat and a missing file. */
    @Test void aRealStorageTellsAnUnreadableJournalFromAnAbsentOne() throws Exception {
        var seeded = new RocketTransferSavedData();
        seeded.electFirstMoonLanding(event(1, 1));
        seeded.saveChecked(target(), atomic());
        byte[] original = Files.readAllBytes(target());
        var storage = TestDataStorage.over(root);
        RocketTransferSavedData unreadable = ManagedRootSupplier.obtain(storage, target(), TYPE, tag -> {
            throw new IllegalStateException("injected runtime read failure");
        }, RocketTransferSavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.UNREADABLE, unreadable.presence());
        assertFalse(unreadable.isDirty());
        unreadable.setDirty();
        storage.save();
        assertArrayEquals(original, Files.readAllBytes(target()), "the file stays byte for byte");
        assertFalse(Files.exists(pending()));

        Path statDirectory = Files.createDirectory(root.resolve("stat"));
        RocketTransferSavedData statFailed = ManagedRootSupplier.obtain(TestDataStorage.over(statDirectory),
                statDirectory.resolve(TYPE.fileName()), TYPE, RocketTransferSavedData::load,
                RocketTransferSavedData::created, path -> CheckedSavedDataFile.Presence.UNKNOWN);
        assertEquals(ManagedRootPresence.UNREADABLE, statFailed.presence());
        Path absentDirectory = Files.createDirectory(root.resolve("absent"));
        RocketTransferSavedData absent = ManagedRootSupplier.obtain(TestDataStorage.over(absentDirectory),
                absentDirectory.resolve(TYPE.fileName()), TYPE, RocketTransferSavedData::load,
                RocketTransferSavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, absent.presence());
        assertTrue(absent.operational());
        assertEquals(FirstEventSourceView.unconfirmed(), absent.durableFirstMoonLanding());
    }

    /** A0 case b for the journal: a file that appears before the first write is never replaced. */
    @Test void aJournalFileThatAppearsBeforeTheFirstWriteIsKept() throws Exception {
        var journal = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        Files.writeString(target(), "appeared after the proof");
        journal.saveChecked(target(), atomic());
        assertEquals("appeared after the proof", Files.readString(target()));
        assertFalse(Files.exists(pending()));
        assertEquals(ManagedRootPresence.UNREADABLE, journal.presence());
        assertFalse(journal.operational());
        assertEquals(FirstEventSourceView.Status.SOURCE_UNAVAILABLE, journal.durableFirstMoonLanding().status());

        // After the first known commit the file is the session's own.
        Path ownDirectory = Files.createDirectory(root.resolve("own"));
        var own = RocketTransferSavedData.created(ManagedRootPresence.PROVEN_ABSENT);
        own.saveChecked(ownDirectory.resolve(TYPE.fileName()), atomic());
        own.electFirstMoonLanding(event(1, 1));
        own.saveChecked(ownDirectory.resolve(TYPE.fileName()), atomic());
        assertEquals(FirstEventOutbox.State.ELECTED, own.durableFirstMoonLanding().outbox().state());
    }

    /**
     * Section 2.1: the 16-participant outbox fits the fixed 65,536-byte root allowance, so 64 records at their maximum
     * plus the outbox stay within the unchanged journal limit. A list element costs its record's named-root size less
     * the 3-byte root header, which the second assertion confirms on a real record.
     */
    @Test void theMaximumOutboxFitsTheUnchangedJournalAllowance() {
        var full = FirstEventOutbox.elected(event(1, 16)).delivered();
        int rootBytes = RocketNbtSize.uncompressedBytes(RocketTransferSavedData.encode(List.of(), full, new CompoundTag()));
        assertTrue(rootBytes <= 65_536, "root and outbox bytes " + rootBytes);
        var record = RocketTransferSavedDataTest.record();
        int recordBytes = RocketNbtSize.uncompressedBytes(RocketTransferJournalCodec.encodeRecord(record));
        assertEquals(rootBytes + recordBytes - 3, RocketNbtSize.uncompressedBytes(
                RocketTransferSavedData.encode(List.of(record), full, new CompoundTag())));
        assertTrue((long) rootBytes + (long) RocketFlightLimits.MAX_ACTIVE_TRANSFERS
                * (RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES - 3) <= RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES);
    }

    private static FirstEvent.MoonLanding event(int id, int participants) {
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < participants; index++) { players.add(new UUID(9, 100 + index)); }
        return FirstEvent.MoonLanding.create(new UUID(8, id), new UUID(8, 100 + id), new UUID(8, 200 + id),
                new UUID(8, 300), new UUID(8, 400 + id), "a".repeat(64), 0, 80, 0, 160L, players);
    }

    private static CompoundTag rootTwo(CompoundTag current) {
        CompoundTag two = current.copy();
        two.remove(OUTBOX);
        two.putInt("schema_version", 2);
        two.putString("format_epoch", SavedDataSchemaMigrator.FORMAT_EPOCH);
        return two;
    }

    private static CheckedSavedDataPolicy atomic() { return CheckedSavedDataPolicy.atomic(TYPE); }

    private Path target() { return root.resolve(TYPE.fileName()); }

    private Path pending() { return root.resolve(TYPE.fileName() + ".arce-pending"); }
}
