package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptRoot;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W328 (W327 audit L2): the pre-start check never reads an unknown status as absent. An unknown presence of any managed
 * file, or of the data directory, refuses startup before anything is read, backed up, staged or committed, and a later
 * start with a working status check passes over the very same bytes. W328F: every status check runs before any read.
 */
final class UnknownPresenceStartupTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static UUID id(long value) { return new UUID(3_280, value); }

    /** A world holding a valid empty escrow ledger and a valid empty receipt root, written as the game writes them. */
    private Path world(String name) throws Exception {
        Path data = Files.createDirectories(temp.resolve(name).resolve("data"));
        CheckedSavedDataFile.replace(data.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), ManagedSavedDataType.RESOURCE_ESCROW,
                () -> EscrowCodec.encode(EscrowLedger.empty(id(1))));
        CheckedSavedDataFile.replace(data.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName()), ManagedSavedDataType.BAY_RECEIPTS,
                () -> BayReceiptCodec.encode(BayReceiptRoot.empty(id(2))));
        return data.getParent();
    }

    /** Every file under the world with its bytes, so a refusal provably creates, removes and changes nothing. */
    private static Map<String, String> tree(Path world) throws Exception {
        var files = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(world)) {
            for (Path path : walk.toList()) {
                String bytes = Files.isRegularFile(path) ? java.util.HexFormat.of().formatHex(Files.readAllBytes(path)) : "<dir>";
                files.put(world.relativize(path).toString(), bytes);
            }
        }
        return files;
    }

    /** The real no-follow check, except an injected status failure for exactly one path. */
    private static Function<Path, CheckedSavedDataFile.Presence> failing(Path unknown) {
        Path target = unknown.toAbsolutePath().normalize();
        return path -> path.toAbsolutePath().normalize().equals(target)
                ? CheckedSavedDataFile.Presence.UNKNOWN : CheckedSavedDataFile.presence(path);
    }

    private static WorldDataMigrationService service(Function<Path, CheckedSavedDataFile.Presence> presence) {
        return new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> fail("No commit"),
                (target, bytes) -> fail("No staging"), presence);
    }

    @Test void anUnknownPresenceOfAnyManagedFileRefusesStartupAndAWorkingCheckThenPassesTheSameBytes() throws Exception {
        for (var type : ManagedSavedDataType.values()) {
            Path world = world(type.name());
            var before = tree(world);
            var refusal = assertThrows(SavedDataMigrationException.class,
                    () -> service(failing(world.resolve("data").resolve(type.fileName()))).migrate(world), type::name);
            assertEquals(type, refusal.dataType().orElseThrow());
            assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, refusal.diagnosticId());
            assertTrue(refusal.getMessage().startsWith(type.dataName() + " presence is unknown"), refusal::getMessage);
            assertEquals(before, tree(world), "the refusal created, removed or changed nothing");

            var clean = new WorldDataMigrationService().migrate(world);
            assertEquals(MigrationDiagnosticId.DATA_CURRENT, clean.diagnosticId());
            assertEquals(2, clean.managedFileCount(), "the ledger and the receipt root load");
            assertEquals(before, tree(world), "the clean start found the very same bytes and wrote nothing");
        }
    }

    /**
     * W328F (review F2): every managed path's status is checked before any file is decoded. A malformed earlier root
     * (the celestial file) and an unknown presence of a later one (the bay receipts) refuse with the later file's
     * unknown presence, and the read seam, which is the only decoder of managed files, saw no read at all.
     */
    @Test void everyStatusCheckRunsBeforeAnyManagedFileIsRead() throws Exception {
        Path world = world("order");
        Path data = world.resolve("data");
        Files.writeString(data.resolve(ManagedSavedDataType.CELESTIAL.fileName()), "not compressed NBT");
        var before = tree(world);
        var reads = new java.util.ArrayList<ManagedSavedDataType>();
        java.util.function.BiFunction<Path, ManagedSavedDataType, net.minecraft.nbt.CompoundTag> recording = (path, type) -> {
            reads.add(type);
            return BoundedSavedDataIo.read(path, type);
        };

        var refusal = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService(Clock.systemUTC(),
                (staged, target) -> fail("No commit"), (target, bytes) -> fail("No staging"),
                failing(data.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName())), recording).migrate(world));
        assertEquals(ManagedSavedDataType.BAY_RECEIPTS, refusal.dataType().orElseThrow());
        assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, refusal.diagnosticId());
        assertTrue(refusal.getMessage().startsWith(ManagedSavedDataType.BAY_RECEIPTS.dataName() + " presence is unknown"),
                refusal::getMessage);
        assertEquals(java.util.List.of(), reads, "a managed file was read before the later status check refused");
        assertEquals(before, tree(world));

        // The same seam does decode: with every status known, the malformed celestial root is read first and refuses.
        var malformed = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService(Clock.systemUTC(),
                (staged, target) -> fail("No commit"), (target, bytes) -> fail("No staging"),
                CheckedSavedDataFile::presence, recording).migrate(world));
        assertEquals(ManagedSavedDataType.CELESTIAL, malformed.dataType().orElseThrow());
        assertEquals(java.util.List.of(ManagedSavedDataType.CELESTIAL), reads);
        assertEquals(before, tree(world));
    }

    @Test void anUnknownDataDirectoryRefusesStartupInsteadOfReportingNoData() throws Exception {
        Path world = world("present");
        var before = tree(world);
        var refusal = assertThrows(SavedDataMigrationException.class, () -> service(failing(world.resolve("data"))).migrate(world));
        assertTrue(refusal.dataType().isEmpty(), "the directory has no managed type");
        assertTrue(refusal.getMessage().startsWith("The world data directory presence is unknown"), refusal::getMessage);
        assertEquals(before, tree(world));
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, new WorldDataMigrationService().migrate(world).diagnosticId());

        Path empty = Files.createDirectory(temp.resolve("no-data"));
        assertThrows(SavedDataMigrationException.class, () -> service(failing(empty.resolve("data"))).migrate(empty));
        assertFalse(Files.exists(empty.resolve("data")), "nothing was created");
        assertEquals(MigrationDiagnosticId.NO_MANAGED_DATA, new WorldDataMigrationService().migrate(empty).diagnosticId());
    }

    @Test void anUnknownLedgerOrReceiptPresenceGetsTheWholeWorldHintNeverTheSingleFileRestore() throws Exception {
        Path world = Files.createDirectory(temp.resolve("hint"));
        Path data = world.resolve("data");
        assertEquals(BetaWorldMigrationEvents.MIGRATION_HINT, BetaWorldMigrationEvents.recoveryHint(world));
        for (var type : new ManagedSavedDataType[] {ManagedSavedDataType.RESOURCE_ESCROW, ManagedSavedDataType.BAY_RECEIPTS}) {
            assertEquals(BetaWorldMigrationEvents.ESCROW_HINT,
                    BetaWorldMigrationEvents.recoveryHint(world, failing(data.resolve(type.fileName()))), type::name);
        }
        assertEquals(BetaWorldMigrationEvents.REJOIN_HINT, BetaWorldMigrationEvents.recoveryHint(world,
                failing(data.resolve(ManagedSavedDataType.ROCKET_REJOINS.fileName()))));
        assertEquals(BetaWorldMigrationEvents.MIGRATION_HINT, BetaWorldMigrationEvents.recoveryHint(world,
                failing(data.resolve(ManagedSavedDataType.STATIONS.fileName()))), "other types keep the migration hint");
        assertFalse(Files.exists(data), "the hint only checks status");
    }
}
