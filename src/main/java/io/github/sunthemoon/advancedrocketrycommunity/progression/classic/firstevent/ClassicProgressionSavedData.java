package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * The classic progression receipt store (W44 section 3): one monotonic receipt per world-first kind, written by the
 * synchronous checked path. A receipt is durable only after the explicit barrier (flush, then directory force)
 * returned; {@code onPersisted()} and an ordinary save never confirm a new receipt. A root that is present but
 * unreadable, or blocked, is not operational, is never written and holds both kinds (section 3.6).
 */
public final class ClassicProgressionSavedData extends AtomicSavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_classic_progression";
    public static final int SCHEMA_VERSION = 1;
    public static final String FORMAT_EPOCH = "v1.8.0-classic-progression";
    public static final int MAX_ROOT_BYTES = 256 * 1_024;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 16_384;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.CLASSIC_PROGRESSION;
    private static final Set<String> ROOT_KEYS = Set.of("schema_version", "format_epoch",
            FirstEventKind.FIRST_MOON_LANDING.serializedName(), FirstEventKind.FIRST_STATION_WARP.serializedName());
    private static final Set<String> ABSENT_KEYS = Set.of("occurred");
    private static final Set<String> OCCURRED_KEYS = Set.of("occurred", "source", "event");

    /** RAM receipts: monotonic, never edited or removed online. */
    private final EnumMap<FirstEventKind, FirstEvent> receipts = new EnumMap<>(FirstEventKind.class);
    /** Session-local per-kind proof: the exact receipt that passed the barrier, or a strictly loaded one. */
    private final EnumMap<FirstEventKind, FirstEvent> confirmed = new EnumMap<>(FirstEventKind.class);
    private ManagedRootPresence presence;
    private CompoundTag preservedBlockedData;
    /** After the session's first known commit, the file is the session's own and absence is not re-checked. */
    private boolean sessionCommitted;

    private ClassicProgressionSavedData(ManagedRootPresence presence) {
        super(TYPE);
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    /** A new root whose file was proven absent, for detached fixtures and the root-presence supplier. */
    public static ClassicProgressionSavedData provenAbsent() {
        return new ClassicProgressionSavedData(ManagedRootPresence.PROVEN_ABSENT);
    }

    static ClassicProgressionSavedData created(ManagedRootPresence presence) {
        return new ClassicProgressionSavedData(presence);
    }

    public static ClassicProgressionSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return ManagedRootSupplier.obtain(server, TYPE, tag -> {
            FirstEventCutSeams.beforeLoad(server, TYPE);
            return load(tag);
        }, ClassicProgressionSavedData::created);
    }

    /** Strict decode; any deviation preserves the payload and blocks the root (the pre-start validator refuses it). */
    public static ClassicProgressionSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        ClassicProgressionSavedData data = new ClassicProgressionSavedData(ManagedRootPresence.LOADED);
        try {
            if (!BoundedNbt.fits(source, MAX_ROOT_BYTES, MAX_DEPTH, MAX_NODES)) {
                throw new IllegalArgumentException("Classic progression root exceeds its bound");
            }
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(TYPE, source);
            if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
                throw new IllegalArgumentException("Classic progression root is not current");
            }
            CompoundTag payload = migration.payload();
            if (!payload.getAllKeys().equals(ROOT_KEYS)) {
                throw new IllegalArgumentException("Classic progression root keys are not exact");
            }
            for (FirstEventKind kind : FirstEventKind.values()) {
                decodeReceipt(payload.get(kind.serializedName()), kind).ifPresent(event -> {
                    data.receipts.put(kind, event);
                    data.confirmed.put(kind, event);
                });
            }
        } catch (RuntimeException invalid) {
            data.receipts.clear();
            data.confirmed.clear();
            data.preservedBlockedData = source.copy();
            data.presence = ManagedRootPresence.BLOCKED;
        }
        return data;
    }

    public ManagedRootPresence presence() { return presence; }

    public boolean operational() { return presence.operational(); }

    public Optional<CompoundTag> preservedBlockedData() {
        return Optional.ofNullable(preservedBlockedData).map(CompoundTag::copy);
    }

    /** The RAM receipt of {@code kind}; empty while unavailable. A RAM receipt alone is not durable. */
    public Optional<FirstEvent> receipt(FirstEventKind kind) {
        return operational() ? Optional.ofNullable(receipts.get(kind)) : Optional.empty();
    }

    /** The exact receipt that passed the barrier this session, or was strictly loaded. */
    public Optional<FirstEvent> confirmedReceipt(FirstEventKind kind) {
        return operational() ? Optional.ofNullable(confirmed.get(kind)) : Optional.empty();
    }

    /** W44 section 3.6: the root is PROVEN_ABSENT, or it loaded valid with {@code occurred} 0 for this kind. */
    public boolean validatedAbsent(FirstEventKind kind) {
        return operational() && !receipts.containsKey(kind);
    }

    /** A RAM receipt that has not passed the barrier (RECEIPT_PENDING). */
    public boolean receiptPending(FirstEventKind kind) {
        return operational() && receipts.containsKey(kind) && !receipts.get(kind).equals(confirmed.get(kind));
    }

    /**
     * Monotonic insertion of R(E), only from a durable source event (invariant I1, the caller's check). An equal
     * receipt is a no-op; a different one is refused.
     */
    public boolean insert(FirstEvent event) {
        Objects.requireNonNull(event, "event");
        if (!operational()) { throw new IllegalStateException("Classic progression root is unavailable"); }
        FirstEvent existing = receipts.get(event.kind());
        if (existing != null) {
            if (!existing.equals(event)) { throw new IllegalStateException("A receipt is never replaced online"); }
            return false;
        }
        receipts.put(event.kind(), event);
        try {
            encode(new CompoundTag());
        } catch (RuntimeException overBudget) {
            receipts.remove(event.kind());
            throw overBudget;
        }
        setDirty();
        return true;
    }

    /**
     * The receipt barrier (W44 section 3.2): capture the generation, flush it through the checked path, force the data
     * directory, and only then publish the captured receipts as confirmed. A force failure marks the root dirty so a
     * later retry writes again; an already confirmed other kind stays confirmed.
     */
    public Barrier barrier(Path file, CheckedSavedDataFile.Committer committer,
                           CheckedSavedDataPolicy.DirectoryForce force) {
        if (!operational()) { return new Barrier(Barrier.Stage.UNAVAILABLE, null); }
        Map<FirstEventKind, FirstEvent> captured = new EnumMap<>(receipts);
        try {
            flush(file, committer);
        } catch (RuntimeException failure) {
            return new Barrier(Barrier.Stage.FLUSH, failure);
        }
        try {
            force.force(file.toAbsolutePath().normalize().getParent());
        } catch (IOException | RuntimeException failure) {
            setDirty();
            return new Barrier(Barrier.Stage.FORCE, failure);
        }
        confirmed.putAll(captured);
        return new Barrier(Barrier.Stage.CONFIRMED, null);
    }

    @Override
    protected boolean writable() {
        return operational();
    }

    /** An unwritable root is never dirty, so no ordinary save stages it. */
    @Override
    public boolean isDirty() {
        return writable() && super.isDirty();
    }

    /** W44 section 3.6: the first write of a proven-absent root never replaces an entry that has since appeared. */
    @Override
    protected void beforeCommit(Path target) throws IOException {
        if (presence == ManagedRootPresence.PROVEN_ABSENT && !sessionCommitted
                && !ManagedRootSupplier.stillAbsent(target)) {
            becomeUnreadable(target);
            throw new IOException("A classic progression file appeared before this session's first write");
        }
    }

    @Override
    protected void onPersisted() {
        sessionCommitted = true;
    }

    private void becomeUnreadable(Path target) {
        presence = ManagedRootPresence.UNREADABLE;
        receipts.clear();
        confirmed.clear();
        setDirty(false);
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.error(
                "ARCE_MANAGED_ROOT_UNREADABLE type={} file={} stage=first_write", DATA_NAME,
                target.toAbsolutePath().normalize());
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) { return preservedBlockedData.copy(); }
        if (presence == ManagedRootPresence.UNREADABLE) {
            throw new IllegalStateException("An unreadable classic progression root is never encoded");
        }
        return encode(target);
    }

    private CompoundTag encode(CompoundTag target) {
        SavedDataSchemaMigrator.stampCurrent(TYPE, target);
        for (FirstEventKind kind : FirstEventKind.values()) {
            target.put(kind.serializedName(), encodeReceipt(receipts.get(kind), kind));
        }
        if (!BoundedNbt.fits(target, MAX_ROOT_BYTES, MAX_DEPTH, MAX_NODES)) {
            throw new IllegalStateException("Encoded classic progression root exceeds its bound");
        }
        return target;
    }

    static CompoundTag encodeReceipt(FirstEvent event, FirstEventKind kind) {
        CompoundTag receipt = new CompoundTag();
        receipt.putByte("occurred", (byte) (event == null ? 0 : 1));
        if (event != null) {
            if (event.kind() != kind) { throw new IllegalArgumentException("A receipt holds another kind"); }
            receipt.putString("source", kind.source());
            receipt.put("event", FirstEventCodec.encode(event));
        }
        return receipt;
    }

    static Optional<FirstEvent> decodeReceipt(Tag raw, FirstEventKind kind) {
        if (!(raw instanceof CompoundTag receipt) || !receipt.contains("occurred", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Missing or invalid receipt " + kind.serializedName());
        }
        byte occurred = receipt.getByte("occurred");
        if (occurred == 0) {
            if (!receipt.getAllKeys().equals(ABSENT_KEYS)) {
                throw new IllegalArgumentException("An absent receipt carries other keys");
            }
            return Optional.empty();
        }
        if (occurred != 1 || !receipt.getAllKeys().equals(OCCURRED_KEYS)
                || !receipt.contains("source", Tag.TAG_STRING) || !kind.source().equals(receipt.getString("source"))
                || !receipt.contains("event", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Receipt " + kind.serializedName() + " is malformed");
        }
        return Optional.of(FirstEventCodec.decode(receipt.getCompound("event"), kind));
    }

    /** One receipt-barrier attempt: CONFIRMED, the failed stage with its cause, or UNAVAILABLE. */
    public record Barrier(Stage stage, Throwable failure) {
        public enum Stage { CONFIRMED, FLUSH, FORCE, UNAVAILABLE }

        public boolean confirmed() { return stage == Stage.CONFIRMED; }
    }
}
