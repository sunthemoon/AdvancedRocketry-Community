package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/**
 * W44 section 3.6: the one root-presence supplier of the station registry, the transfer journal and the classic
 * progression root. Vanilla calls the supplier both for an absent file and for a present one whose read threw, so the
 * supplier stats the exact file: only a proven absence builds the ordinary empty root. It runs at most once per type
 * per server session, because vanilla caches the instance it returns.
 */
public final class ManagedRootSupplier {
    private ManagedRootSupplier() {
    }

    /** The overworld data file of {@code type}, the same path vanilla's storage reads. */
    public static Path dataFile(MinecraftServer server, ManagedSavedDataType type) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(type.fileName());
    }

    public static <T extends SavedData> T obtain(MinecraftServer server, ManagedSavedDataType type,
                                                 Function<CompoundTag, T> load,
                                                 Function<ManagedRootPresence, T> created) {
        Objects.requireNonNull(server, "server");
        return obtain(server.overworld().getDataStorage(), dataFile(server, type), type, load, created,
                CheckedSavedDataFile::presence);
    }

    /** {@code created} receives PROVEN_ABSENT or UNREADABLE; {@code probe} is a test seam for a failing stat. */
    public static <T extends SavedData> T obtain(DimensionDataStorage storage, Path file, ManagedSavedDataType type,
                                                 Function<CompoundTag, T> load,
                                                 Function<ManagedRootPresence, T> created,
                                                 Function<Path, CheckedSavedDataFile.Presence> probe) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(probe, "probe");
        return storage.computeIfAbsent(load, () -> created.apply(classify(file, type, probe)), type.dataName());
    }

    static ManagedRootPresence classify(Path file, ManagedSavedDataType type,
                                        Function<Path, CheckedSavedDataFile.Presence> probe) {
        if (probe.apply(file) == CheckedSavedDataFile.Presence.ABSENT) {
            return ManagedRootPresence.PROVEN_ABSENT;
        }
        // Vanilla already logged the read failure with its stack trace once; this names the held root.
        AdvancedRocketryCommunity.LOGGER.error("ARCE_MANAGED_ROOT_UNREADABLE type={} file={}",
                type.dataName(), file.toAbsolutePath().normalize());
        return ManagedRootPresence.UNREADABLE;
    }

    /**
     * W44 section 3.6, first write: an entry that appeared after a root was proven absent is never replaced. The
     * caller switches the root to UNREADABLE when it handles the resulting failure.
     */
    public static boolean stillAbsent(Path target) {
        return CheckedSavedDataFile.presence(target) == CheckedSavedDataFile.Presence.ABSENT;
    }
}
