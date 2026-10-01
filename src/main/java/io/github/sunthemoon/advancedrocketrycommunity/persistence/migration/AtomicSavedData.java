package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Checked replacement for the two discovery authorities and the endgame root, including ordinary autosave. */
public abstract class AtomicSavedData extends SavedData {
    private final ManagedSavedDataType type;
    private boolean saveFailureReported;

    protected AtomicSavedData(ManagedSavedDataType type) {
        if (type != ManagedSavedDataType.CELESTIAL && type != ManagedSavedDataType.SATELLITE_MISSIONS
                && type != ManagedSavedDataType.ENDGAME) {
            throw new IllegalArgumentException("Atomic storage only supports the discovery authorities and the endgame root");
        }
        this.type = type;
    }

    @Override
    public final void save(File file) {
        if (!isDirty()) { return; }
        try {
            flush(file.toPath());
        } catch (RuntimeException exception) {
            if (!saveFailureReported) {
                LogUtils.getLogger().error("Atomic SavedData save failed for {}; retained dirty state", type.dataName(), exception);
                saveFailureReported = true;
            }
        }
    }

    /** Always acknowledge the current snapshot, even after an unchanged replay result. */
    public final void flush(MinecraftServer server) {
        flush(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(type.fileName()));
    }

    public final void flush(Path file) {
        flush(file, CheckedSavedDataFile::atomicMove);
    }

    final void flush(Path file, CheckedSavedDataFile.Committer committer) {
        // A failed explicit barrier must also remain eligible for a later ordinary save.
        setDirty();
        CheckedSavedDataFile.replace(file, type, () -> save(new CompoundTag()), committer);
        // No fallible operation follows replacement before acknowledgment.
        setDirty(false);
        saveFailureReported = false;
        onPersisted();
    }

    /** Called after a write of the current snapshot returned without error (ADR-050 §2 save epoch). */
    protected void onPersisted() {
    }
}
