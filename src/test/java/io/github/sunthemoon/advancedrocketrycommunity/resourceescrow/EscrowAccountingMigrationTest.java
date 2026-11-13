package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowAccountingMigrationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.RESOURCE_ESCROW;
    private Path file() throws Exception { return Files.createDirectories(temp.resolve("data")).resolve(TYPE.fileName()); }
    private static void write(Path file, CompoundTag payload) throws Exception {
        var outer = new CompoundTag(); outer.put("data", payload); NbtIo.writeCompressed(outer, file.toFile());
    }
    @Test void supportedSchemasAndEpochsArePinnedIndependently() {
        for (int i = -1; i < 6; i++) { assertEquals(i == 2 || i == 3, TYPE.supportsSchema(i)); }
        assertFalse(TYPE.hasLegacySchema()); assertEquals(2, TYPE.firstEpochSchema());
        assertEquals("v1.8.0-resource-escrow", TYPE.formatEpoch(2));
        assertEquals("v1.8.0-resource-escrow-fluid-accounting", TYPE.formatEpoch(3));
        assertEquals(2, TYPE.currentSchemaVersion());
    }
    @Test void liveFrozenInstalledAndReleasedRoot2FormsMigrateLosslessly() throws Exception {
        var live = ledger(operation(1).withReason(EscrowReason.OTHER_STATE));
        var intent = EscrowAccountingFormatTest.intent(live, List.of());
        var o1 = intent.freeze(live); var o3 = EscrowSettlementTombstones.install(o1, intent);
        var o4 = EscrowSettlementTombstones.release(o3, intent);
        for (var root : List.of(live, o1, o3, o4, EscrowCombinedReservationTest.retainedFloors(2))) {
            var source = EscrowCodec.encode(root); var original = source.copy();
            var target = SavedDataSchemaMigrator.migrateEscrow(source);
            assertEquals(original, source); assertEquals(3, target.getInt("schema_version"));
            assertEquals(TYPE.formatEpoch(3), target.getString("format_epoch"));
            for (String key : source.getAllKeys()) {
                if (!key.equals("schema_version") && !key.equals("format_epoch")) { assertEquals(source.get(key), target.get(key), key); }
            }
            assertTrue(target.getList("fluid_accounts", 10).isEmpty());
            assertEquals(target, EscrowCodec.encode(EscrowCodec.decode(target)));
            for (var op : EscrowCodec.decode(target).records()) { assertTrue(op.legacyFluid()); }
            var file = file(); write(file, source); var bytes = Files.readAllBytes(file);
            EscrowLedgerBaseline.requireMigration(file, target); assertArrayEquals(bytes, Files.readAllBytes(file));
        }
    }
    @Test void activationOffDoesNotMigrateOrCreateBackups() throws Exception {
        var file = file(); var source = EscrowCodec.encode(ledger(operation(1))); write(file, source);
        var bytes = Files.readAllBytes(file);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, SavedDataSchemaMigrator.migrate(TYPE, source).status());
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, new WorldDataMigrationService().migrate(temp).diagnosticId());
        assertArrayEquals(bytes, Files.readAllBytes(file)); assertFalse(Files.exists(temp.resolve("advancedrocketrycommunity-backups")));
    }
    @Test void wrongEpochNoncanonicalAndRoot1AreRefusedWithoutChangingSource() throws Exception {
        var canonical = EscrowCodec.encode(ledger(operation(1)));
        var wrong = canonical.copy(); wrong.putString("format_epoch", "v0.9.0-beta");
        var noncanonical = canonical.copy(); noncanonical.putString("unexpected", "field");
        var root1 = canonical.copy(); root1.putInt("schema_version", 1);
        for (var source : List.of(wrong, noncanonical, root1)) {
            var original = source.copy(); assertThrows(RuntimeException.class, () -> SavedDataSchemaMigrator.migrateEscrow(source));
            assertEquals(original, source); var file = file(); write(file, source); var bytes = Files.readAllBytes(file);
            assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(temp));
            assertArrayEquals(bytes, Files.readAllBytes(file));
        }
    }
    @Test void fullTargetRefusesBeforeReplacement() throws Exception {
        var source = EscrowCodec.encode(EscrowFlightBenchmarkCeiling.create(false)); var file = file(); write(file, source);
        var bytes = Files.readAllBytes(file); assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrateEscrow(source));
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }
    @Test void failedAndUnknownCheckedMigrationWritesKeepOriginalAndRetryExactTarget() throws Exception {
        var source = EscrowCodec.encode(ledger(operation(1))); var target = SavedDataSchemaMigrator.migrateEscrow(source);
        var file = file(); write(file, source); var bytes = Files.readAllBytes(file);
        for (boolean attempted : List.of(false, true)) {
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, destination) -> {
                if (attempted) { assertTrue(Files.size(staged) > 0); }
                throw new java.io.IOException(attempted ? "unknown replacement outcome" : "failed before replace");
            });
            assertThrows(RuntimeException.class, () -> policy.replace(file, () -> target));
            assertArrayEquals(bytes, Files.readAllBytes(file)); EscrowLedgerBaseline.requireMigration(file, target);
        }
        CheckedSavedDataPolicy.atomic(TYPE).replace(file, () -> target);
        assertEquals(target, CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        EscrowLedgerBaseline.requireMigration(file, target);
    }
    @Test void invalidTargetFailsItsOwnValidationEvenWithCanonicalSource() throws Exception {
        var source = EscrowCodec.encode(ledger(operation(1))); var target = SavedDataSchemaMigrator.migrateEscrow(source);
        var file = file(); write(file, source); target.putLong("issued_through", 0);
        assertThrows(RuntimeException.class, () -> EscrowLedgerBaseline.requireMigration(file, target));
    }
    @Test void backupLimitPreservesTheEscrowAlongsideAnUpgrade() throws Exception {
        var file = file(); write(file, EscrowCodec.encode(ledger(operation(1)))); var bytes = Files.readAllBytes(file);
        // With activation off, a legacy celestial root triggers the shared backup transaction instead.
        var celestial = new CompoundTag(); celestial.putInt("schema_version", 1); celestial.put("bodies", new net.minecraft.nbt.ListTag());
        write(file.resolveSibling(ManagedSavedDataType.CELESTIAL.fileName()), celestial);
        var backups = Files.createDirectory(temp.resolve("advancedrocketrycommunity-backups"));
        for (int i = 0; i < 5; i++) { Files.createDirectory(backups.resolve("existing-" + i)); }
        var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(temp));
        assertEquals(MigrationDiagnosticId.BACKUP_LIMIT, failure.diagnosticId()); assertArrayEquals(bytes, Files.readAllBytes(file));
    }
}
