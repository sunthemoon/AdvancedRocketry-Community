package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;

/**
 * Ordinary save, observing IO success instead of SavedData.save(File)'s swallowed IOException. Until the session's
 * first known commit of a proven-absent root it is staged, forced, read back and atomically moved behind the absence
 * guard, so a failed attempt never leaves a torn target of its own and keeps the absence proof (W44K). After that
 * commit it is vanilla's in-place write.
 */
final class StationOrdinaryWriter {
    private StationOrdinaryWriter() { }

    static void save(StationRegistrySavedData data, StationRootPresence root, File file) {
        save(data, root, file, CheckedSavedDataFile::atomicMove);
    }

    /** {@code firstMove} is the first write's atomic move; tests replace it to fail after the file is staged. */
    static void save(StationRegistrySavedData data, StationRootPresence root, File file,
                     CheckedSavedDataFile.Committer firstMove) {
        if (!data.isDirty() || !root.admitsOrdinarySave(file.toPath())) {
            if (root.unreadable()) { data.setDirty(false); }
            return;
        }
        // The adapter retains its ordinary-save live-clock / quarantined-floor policy; this is not a barrier.
        CompoundTag payload = data.save(new CompoundTag());
        if (root.mustRecheck()) {
            staged(data, root, file.toPath(), payload, firstMove);
        } else {
            inPlace(data, root, file, payload);
        }
        // Preserve vanilla ordinary-save dirty handling, including failed IO. A later mutation can retry.
        data.setDirty(false);
    }

    private static void staged(StationRegistrySavedData data, StationRootPresence root, Path file, CompoundTag payload,
                               CheckedSavedDataFile.Committer firstMove) {
        // A readback-confirmed candidate is a known commit; anything else leaves the proof for the next save.
        StationCheckedWriter.Outcome outcome = StationCheckedWriter.write(file, payload,
                new CheckedSavedDataPolicy(ManagedSavedDataType.STATIONS, firstMove),
                CheckedSavedDataPolicy.DirectoryForce.PLATFORM, root.guard(), diagnostics(data));
        if (outcome == StationCheckedWriter.Outcome.COMMITTED) {
            root.committed();
        } else {
            root.afterFailedWrite();
        }
    }

    private static void inPlace(StationRegistrySavedData data, StationRootPresence root, File file,
                                CompoundTag payload) {
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload);
        NbtUtils.addCurrentDataVersion(outer);
        try {
            NbtIo.writeCompressed(outer, file);
            root.committed();
        } catch (IOException failure) {
            AdvancedRocketryCommunity.LOGGER.error("Could not save data {}", data, failure);
        }
    }

    /** Vanilla's one ERROR per failed ordinary save; a replacement confirmed by its readback is a WARN. */
    private static StationCheckedWriter.Diagnostics diagnostics(StationRegistrySavedData data) {
        return new StationCheckedWriter.Diagnostics() {
            @Override public void beforeReplace(Throwable failure) { failed(failure); }

            @Override public void unknown(Path file, Throwable failure) { failed(failure); }

            @Override public void replacedDespiteError(Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.warn("Saved data {} despite an error", data, failure);
            }

            @Override public void replaceFailed(boolean filePresent, Throwable failure) { failed(failure); }

            private void failed(Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.error("Could not save data {}", data, failure);
            }
        };
    }
}
