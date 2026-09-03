package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Pre-start transaction for managed SavedData root migrations. */
public final class WorldDataMigrationService {
    public static final int MAX_BACKUPS = 5;

    private static final String DATA_DIRECTORY = "data";
    private static final String BACKUP_DIRECTORY = "advancedrocketrycommunity-backups";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Clock clock;
    private final FileCommitter committer;

    public WorldDataMigrationService() {
        this(Clock.systemUTC(), WorldDataMigrationService::replaceFile);
    }

    WorldDataMigrationService(Clock clock, FileCommitter committer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.committer = Objects.requireNonNull(committer, "committer");
    }

    public MigrationReport migrate(Path suppliedWorldRoot) {
        WorldPaths paths = validateWorldPaths(suppliedWorldRoot);
        if (!Files.exists(paths.dataDirectory(), LinkOption.NOFOLLOW_LINKS)) {
            return MigrationReport.noData();
        }

        List<SourceFile> sources = discoverSources(paths);
        if (sources.isEmpty()) {
            return MigrationReport.noData();
        }
        List<SourceFile> upgrades = sources.stream()
                .filter(SourceFile::requiresMigration)
                .toList();
        if (upgrades.isEmpty()) {
            return MigrationReport.current(sources.size());
        }

        Backup backup = createBackup(paths, sources);
        Map<SourceFile, Path> staged = stageMigrations(paths, upgrades);
        try {
            commit(staged);
        } catch (IOException commitFailure) {
            restoreOrThrow(sources, staged.values(), backup, commitFailure);
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.COMMIT_ROLLED_BACK,
                    "SavedData migration commit failed; originals were restored from "
                            + backup.directory().getFileName(),
                    commitFailure
            );
        } finally {
            deleteStaged(staged.values());
        }
        return MigrationReport.migrated(
                sources.size(),
                upgrades.size(),
                backup.directory().getFileName().toString()
        );
    }

    private List<SourceFile> discoverSources(WorldPaths paths) {
        List<SourceFile> sources = new ArrayList<>();
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            Path source = safeChild(paths.dataDirectory(), type.fileName(), paths.worldRoot());
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireRegularFile(source);
            CompoundTag outer = BoundedSavedDataIo.read(source, type);
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    type,
                    BoundedSavedDataIo.payload(outer, type)
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.FUTURE_SCHEMA,
                        type.dataName() + " uses future schema " + migration.sourceSchema()
                );
            }
            ManagedSavedDataPayloadValidator.validate(type, migration.payload());
            sources.add(new SourceFile(
                    type,
                    source,
                    outer,
                    migration,
                    sha256(source)
            ));
        }
        return List.copyOf(sources);
    }

    private Backup createBackup(WorldPaths paths, List<SourceFile> sources) {
        Path root = safeChild(paths.worldRoot(), BACKUP_DIRECTORY, paths.worldRoot());
        Instant createdAt = clock.instant();
        try {
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(root);
            } else {
                Files.createDirectory(root);
            }
            if (countBackupDirectories(root) >= MAX_BACKUPS) {
                throw new SavedDataMigrationException(
                        MigrationDiagnosticId.BACKUP_LIMIT,
                        "The fixed limit of " + MAX_BACKUPS + " migration backups is reached"
                );
            }
            String name = BACKUP_TIME.format(createdAt) + "-schema1-to2";
            Path directory = safeChild(root, name, paths.worldRoot());
            Files.createDirectory(directory);

            List<ManifestFile> manifestFiles = new ArrayList<>();
            for (SourceFile source : sources) {
                copyBackupFile(source.path(), directory, source.type().fileName(), manifestFiles);
                Path old = safeChild(
                        paths.dataDirectory(),
                        source.type().fileName() + "_old",
                        paths.worldRoot()
                );
                if (Files.exists(old, LinkOption.NOFOLLOW_LINKS)) {
                    requireRegularFile(old);
                    copyBackupFile(old, directory, old.getFileName().toString(), manifestFiles);
                }
            }
            manifestFiles.sort(Comparator.comparing(ManifestFile::file));
            BackupManifest manifest = new BackupManifest(
                    1,
                    createdAt.toString(),
                    SavedDataSchemaMigrator.LEGACY_SCHEMA_VERSION,
                    SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                    List.copyOf(manifestFiles)
            );
            writeDurably(directory.resolve(MANIFEST_FILE), GSON.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Backup(directory);
        } catch (SavedDataMigrationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.BACKUP_FAILED,
                    "Cannot create the pre-migration backup",
                    exception
            );
        }
    }

    private Map<SourceFile, Path> stageMigrations(
            WorldPaths paths,
            List<SourceFile> upgrades
    ) {
        Map<SourceFile, Path> staged = new LinkedHashMap<>();
        try {
            for (SourceFile source : upgrades) {
                CompoundTag migratedOuter = BoundedSavedDataIo.withPayload(
                        source.outer(),
                        source.migration().payload()
                );
                byte[] encoded = BoundedSavedDataIo.write(migratedOuter, source.type());
                Path staging = safeChild(
                        paths.dataDirectory(),
                        "." + source.type().dataName() + ".migrating-" + UUID.randomUUID(),
                        paths.worldRoot()
                );
                staged.put(source, staging);
                writeDurably(staging, encoded);
                validateStaged(staging, source.type());
            }
            return staged;
        } catch (SavedDataMigrationException exception) {
            deleteStaged(staged.values());
            if (exception.diagnosticId() == MigrationDiagnosticId.OVERSIZED_DATA) {
                throw exception;
            }
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.STAGING_FAILED,
                    "Cannot stage and validate every migrated SavedData file",
                    exception
            );
        } catch (IOException exception) {
            deleteStaged(staged.values());
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.STAGING_FAILED,
                    "Cannot stage and validate every migrated SavedData file",
                    exception
            );
        }
    }

    private static void validateStaged(Path staging, ManagedSavedDataType type) {
        CompoundTag outer = BoundedSavedDataIo.read(staging, type);
        SavedDataSchemaMigrator.MigrationResult result = SavedDataSchemaMigrator.migrate(
                type,
                BoundedSavedDataIo.payload(outer, type)
        );
        if (result.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.STAGING_FAILED,
                    type.dataName() + " staged output is not current schema"
            );
        }
        ManagedSavedDataPayloadValidator.validate(type, result.payload());
    }

    private void commit(Map<SourceFile, Path> staged) throws IOException {
        for (Map.Entry<SourceFile, Path> entry : staged.entrySet()) {
            committer.replace(entry.getValue(), entry.getKey().path());
        }
    }

    private static void restoreOrThrow(
            List<SourceFile> sources,
            Iterable<Path> staged,
            Backup backup,
            IOException commitFailure
    ) {
        deleteStaged(staged);
        try {
            for (SourceFile source : sources) {
                Path backupFile = backup.directory().resolve(source.type().fileName());
                Path restore = Files.createTempFile(
                        source.path().getParent(),
                        "." + source.type().dataName() + ".restore-",
                        ".dat"
                );
                try {
                    Files.copy(backupFile, restore, StandardCopyOption.REPLACE_EXISTING);
                    force(restore);
                    replaceFile(restore, source.path());
                } finally {
                    Files.deleteIfExists(restore);
                }
                if (!source.sha256().equals(sha256(source.path()))) {
                    throw new IOException("Restored checksum differs for " + source.type().dataName());
                }
            }
        } catch (IOException | RuntimeException rollbackFailure) {
            rollbackFailure.addSuppressed(commitFailure);
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.ROLLBACK_FAILED,
                    "SavedData migration failed and automatic rollback was not complete; restore the backup manually",
                    rollbackFailure
            );
        }
    }

    private static void copyBackupFile(
            Path source,
            Path directory,
            String fileName,
            List<ManifestFile> manifestFiles
    ) throws IOException {
        Path destination = directory.resolve(fileName);
        CopyOption[] options = {
                StandardCopyOption.COPY_ATTRIBUTES,
                LinkOption.NOFOLLOW_LINKS
        };
        Files.copy(source, destination, options);
        force(destination);
        manifestFiles.add(new ManifestFile(
                fileName,
                Files.size(destination),
                sha256(destination)
        ));
    }

    private static int countBackupDirectories(Path root) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isSymbolicLink(child)) {
                    throw unsafe("Migration backup inventory contains a symbolic path");
                }
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static WorldPaths validateWorldPaths(Path suppliedWorldRoot) {
        Objects.requireNonNull(suppliedWorldRoot, "suppliedWorldRoot");
        Path worldRoot = suppliedWorldRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(worldRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(worldRoot)) {
            throw unsafe("World root is missing, not a directory, or symbolic");
        }
        Path dataDirectory = safeChild(worldRoot, DATA_DIRECTORY, worldRoot);
        if (Files.exists(dataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(dataDirectory);
        }
        return new WorldPaths(worldRoot, dataDirectory);
    }

    private static Path safeChild(Path parent, String childName, Path worldRoot) {
        Path child = parent.resolve(childName).toAbsolutePath().normalize();
        if (!child.startsWith(worldRoot) || Files.isSymbolicLink(child)) {
            throw unsafe("Managed migration path is outside the fixed world boundary or symbolic");
        }
        return child;
    }

    private static void requireRegularFile(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw unsafe("Managed SavedData source is not a regular non-symbolic file");
        }
    }

    private static void requireDirectory(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw unsafe("Managed migration directory is not a non-symbolic directory");
        }
    }

    private static SavedDataMigrationException unsafe(String message) {
        return new SavedDataMigrationException(MigrationDiagnosticId.UNSAFE_PATH, message);
    }

    private static void writeDurably(Path path, byte[] content) throws IOException {
        Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        force(path);
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void replaceFile(Path staged, Path target) throws IOException {
        try {
            Files.move(
                    staged,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteStaged(Iterable<Path> staged) {
        for (Path path : staged) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // The authoritative source or restored target remains intact.
            }
        }
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.BACKUP_FAILED,
                    "Cannot hash managed SavedData",
                    exception
            );
        }
    }

    @FunctionalInterface
    interface FileCommitter {
        void replace(Path staged, Path target) throws IOException;
    }

    public record MigrationReport(
            MigrationDiagnosticId diagnosticId,
            int managedFileCount,
            int migratedFileCount,
            Optional<String> backupDirectory
    ) {
        public MigrationReport {
            Objects.requireNonNull(diagnosticId, "diagnosticId");
            Objects.requireNonNull(backupDirectory, "backupDirectory");
        }

        static MigrationReport noData() {
            return new MigrationReport(
                    MigrationDiagnosticId.NO_MANAGED_DATA,
                    0,
                    0,
                    Optional.empty()
            );
        }

        static MigrationReport current(int count) {
            return new MigrationReport(
                    MigrationDiagnosticId.DATA_CURRENT,
                    count,
                    0,
                    Optional.empty()
            );
        }

        static MigrationReport migrated(int count, int migrated, String backup) {
            return new MigrationReport(
                    MigrationDiagnosticId.MIGRATION_COMPLETE,
                    count,
                    migrated,
                    Optional.of(backup)
            );
        }
    }

    private record WorldPaths(Path worldRoot, Path dataDirectory) {
    }

    private record SourceFile(
            ManagedSavedDataType type,
            Path path,
            CompoundTag outer,
            SavedDataSchemaMigrator.MigrationResult migration,
            String sha256
    ) {
        private boolean requiresMigration() {
            return migration.status() == SavedDataSchemaMigrator.MigrationStatus.MIGRATED;
        }
    }

    private record Backup(Path directory) {
    }

    private record BackupManifest(
            int manifestSchema,
            String createdAt,
            int sourceSchema,
            int targetSchema,
            List<ManifestFile> files
    ) {
    }

    private record ManifestFile(String file, long bytes, String sha256) {
    }
}
