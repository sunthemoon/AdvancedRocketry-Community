package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-054 section 10: {@code advancedrocketrycommunity_endgame.dat}, owned by the Overworld's data storage and
 * written through the checked atomic path. A malformed or future root is preserved byte-for-byte and the endgame is
 * not operational; the pre-start validation already refuses to start such a world.
 */
public final class EndgameSavedData extends AtomicSavedData {
    public static final String DATA_NAME = ManagedSavedDataType.ENDGAME.dataName();

    private final EndgameRoot root;
    private CompoundTag preservedBlockedData;
    /** A mutation waits here for the next coalesced flush (at most one per 100 ticks, section 10 write policy). */
    private boolean flushPending;

    private EndgameSavedData(EndgameRoot root) {
        super(ManagedSavedDataType.ENDGAME);
        this.root = Objects.requireNonNull(root, "root");
    }

    public static EndgameSavedData create() {
        return new EndgameSavedData(EndgameRoot.create());
    }

    public static EndgameSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(EndgameSavedData::load, EndgameSavedData::create,
                DATA_NAME);
    }

    public static EndgameSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        CompoundTag preserved = source.copy();
        try {
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.ENDGAME, source);
            if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
                throw new IllegalArgumentException("The endgame root has no readable older or future schema");
            }
            return new EndgameSavedData(EndgameRootCodec.decode(migration.payload()));
        } catch (RuntimeException exception) {
            EndgameSavedData blocked = create();
            blocked.preservedBlockedData = preserved;
            return blocked;
        }
    }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null ? Optional.empty() : Optional.of(preservedBlockedData.copy());
    }

    /** Read access for queries; mutations go through {@link #update}. */
    public EndgameRoot view() {
        requireOperational();
        return root;
    }

    /** Applies a mutation; a changed root becomes dirty and waits for the coalesced flush or the caller's barrier. */
    public <T> T update(Function<EndgameRoot, T> operation) {
        requireOperational();
        T result = operation.apply(root);
        if (root.changedSinceEpoch()) {
            setDirty();
            flushPending = true;
        }
        return result;
    }

    public boolean flushPending() {
        return flushPending && operational();
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        return EndgameRootCodec.encode(root, target);
    }

    @Override
    protected void onPersisted() {
        if (preservedBlockedData == null) {
            root.markPersisted();
            flushPending = false;
        }
    }

    private void requireOperational() {
        if (!operational()) {
            throw new IllegalStateException("The endgame root is blocked by invalid or future data");
        }
    }
}
