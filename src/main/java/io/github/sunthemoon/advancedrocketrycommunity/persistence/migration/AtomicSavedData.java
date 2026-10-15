package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Synchronous checked policy for existing roots. Escrow supplies its own asynchronous single writer. */
public abstract class AtomicSavedData extends SavedData {
    /** The ordinary committer of every root; only a configured release-test cut installs another (W72). */
    static final CommitterSelector NORMAL_COMMITTER = (type, normal) -> normal;
    static volatile CommitterSelector committerSelector = NORMAL_COMMITTER;

    private final ManagedSavedDataType type;
    private boolean saveFailureReported;

    protected AtomicSavedData(ManagedSavedDataType type) {
        this.type = java.util.Objects.requireNonNull(type);
    }

    @Override
    public final void save(File file) {
        if (!isDirty() || !writable()) { return; }
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
        flush(file, committerSelector.select(type, CheckedSavedDataFile::atomicMove));
    }

    /**
     * Installs the ordinary-save committer selector of a release-test run (W44 section 6.1); {@code null} restores
     * the normal committer. The owner of the cut calls it at server start, so production keeps the default.
     */
    public static void installCommitterSelector(CommitterSelector selector) {
        committerSelector = selector == null ? NORMAL_COMMITTER : selector;
    }

    /** Chooses the committer of an ordinary save or explicit flush, given the type and the normal atomic move. */
    @FunctionalInterface
    public interface CommitterSelector {
        CheckedSavedDataFile.Committer select(ManagedSavedDataType type, CheckedSavedDataFile.Committer normal);
    }

    /** The committer is a gated release-test seam for the progression root (W44 section 6.1). */
    protected final void flush(Path file, CheckedSavedDataFile.Committer committer) {
        // W44 section 3.6: a root that may not be written never becomes dirty and never stages a file.
        if (!writable()) { throw new IllegalStateException(type.dataName() + " may not be written"); }
        // A failed explicit barrier must also remain eligible for a later ordinary save.
        setDirty();
        new CheckedSavedDataPolicy(type, (staged, target) -> {
            beforeCommit(target);
            committer.commit(staged, target);
        }).replace(file, () -> save(new CompoundTag()));
        // No fallible operation follows replacement before acknowledgment.
        setDirty(false);
        saveFailureReported = false;
        onPersisted();
    }

    /**
     * W44 section 3.6: whether this root may be staged and written at all; ordinary saves and barriers consult it
     * before staging. True by default; an unreadable root returns false.
     */
    protected boolean writable() {
        return true;
    }

    /** Runs immediately before the atomic move; a throw leaves the target untouched (W44 section 3.6, first write). */
    protected void beforeCommit(Path target) throws IOException {
    }

    /** Called after a write of the current snapshot returned without error (ADR-050 §2 save epoch). */
    protected void onPersisted() {
    }
}
