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
        var receipt = receipt(9); receipt.putBoolean("released", true); receipt.putLong("release_generation", 1);
        var logical = receipt.getList("guards", 10).getCompound(0).getUUID("rocket_id");
        var head = new EscrowFlightTables.Head(logical, id(99), receipt.getUUID("anchor_op"), 1, id(301), 2,
                EscrowSignatureKind.FUEL_VECTOR_V1, hash(2), hash(3), 0, 0, EscrowFlightTables.Outcome.READY);
        var ready = new EscrowLedger(id(500), 0, 1, List.of(), List.of(), List.of(receipt), 0, List.of(head), List.of());
        var launch = new EscrowFlightAuthority.Launch(logical, id(99), id(301), new RocketEscrowSection(ready.id(), 2, List.of()),
                hash(2), hash(3), id(401), id(501), hash(4), hash(5), hash(6), hash(7), new EscrowGuard(logical, null, 1, hash(8)), java.util.Set.of());
        var armed = EscrowFlightAuthority.arm(ready, launch, 2);
        for (var root : List.of(live, o1, o3, o4, ready, armed, EscrowCombinedReservationTest.retainedFloors(2))) {
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
    @Test void activationOffRefusesRoot3AtPreStartWithoutChangingBytesOrCreatingBackups() throws Exception {
        assertFalse(EscrowAccountingFormat.ENABLED);
        var source = EscrowCodec.encode(EscrowAccountingFormatTest.planned(operation(1)));
        var file = file(); write(file, source); var bytes = Files.readAllBytes(file);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.FUTURE, SavedDataSchemaMigrator.migrate(TYPE, source).status());
        var refused = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(temp));
        assertEquals(MigrationDiagnosticId.FUTURE_SCHEMA, refused.diagnosticId());
        assertArrayEquals(bytes, Files.readAllBytes(file)); assertFalse(Files.exists(temp.resolve("advancedrocketrycommunity-backups")));
    }
    @Test void decodableNoncanonicalSourceReachesTheReencodingCheckAndPreservesItsFile() throws Exception {
        var source = EscrowCodec.encode(ledger(operation(1)));
        source.getList("records", 10).getCompound(0).put("settlement", new CompoundTag());
        assertNotEquals(source, EscrowCodec.encode(EscrowCodec.decode(source)));
        var original = source.copy(); var file = file(); write(file, source); var bytes = Files.readAllBytes(file);
        var refused = assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrateEscrow(source));
        assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, refused.diagnosticId());
        assertEquals("Noncanonical root-2 escrow source", refused.getCause().getMessage());
        assertTrue(refused.getMessage().contains(refused.getCause().getMessage()));
        assertFalse(refused.getMessage().contains("needs capacity"));
        assertEquals(original, source); assertArrayEquals(bytes, Files.readAllBytes(file));
        assertThrows(NullPointerException.class, () -> SavedDataSchemaMigrator.migrateEscrow(null),
                "Programming errors are not converted into invalid-world diagnoses");
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
    @Test void validRoot2ReservationsCanBeFilledByOnlyTheNewHeader() throws Exception {
        var floors = EscrowCombinedReservationTest.retainedFloors(20).floors();
        int low = 0, high = 8000;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            try {
                if (EscrowSettlementCapacity.reservedBytes(reservationEdge(middle, floors)) <= EscrowLimits.ROOT_BYTES) { low = middle; }
                else { high = middle - 1; }
            } catch (IllegalArgumentException full) { high = middle - 1; }
        }
        var root = reservationEdge(low, floors);
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
        long bytes = EscrowSettlementCapacity.reservedBytes(root);
        assertTrue(bytes <= EscrowLimits.ROOT_BYTES && bytes + 39 > EscrowLimits.ROOT_BYTES);
        var source = EscrowCodec.encode(root); var file = file(); write(file, source); var original = Files.readAllBytes(file);
        var refused = assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrateEscrow(source));
        assertInstanceOf(EscrowSettlementCapacity.ReservationFull.class, refused.getCause());
        assertTrue(refused.getMessage().startsWith("Escrow root-2 migration needs capacity before upgrade: "));
        assertTrue(refused.getMessage().contains(refused.getCause().getMessage()));
        assertArrayEquals(original, Files.readAllBytes(file));
    }
    private static EscrowLedger reservationEdge(int blob, List<CompoundTag> floors) {
        var ops = new java.util.ArrayList<EscrowOperation>();
        for (int i = 1; i <= 62; i++) { ops.add(operation(i)); }
        var original = operation(63).withReason(EscrowReason.OTHER_STATE); var payload = original.payload();
        var tag = new CompoundTag(); tag.putByteArray("blob", new byte[blob]); payload.put("Tag", tag);
        var op = new EscrowOperation(original.id(), 63, original.owner(), 1, original.phase(), original.reason(), payload,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(payload),
                original.source(), original.destination(), original.guard());
        var intent = EscrowAccountingFormatTest.intent(ledger(op), List.of()); ops.add(op.settle(intent));
        return new EscrowLedger(id(500), 63, 1, ops, floors, List.of());
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
