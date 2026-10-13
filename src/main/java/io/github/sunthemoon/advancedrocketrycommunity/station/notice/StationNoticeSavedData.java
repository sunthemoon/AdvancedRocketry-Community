package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The server's one offline member-notice store (ADR-065 section 5.3), stored with the Overworld data. It is advisory:
 * the warp audit stays in the server log, and nothing else reads it. A root that does not decode (unknown, future,
 * malformed or over a bound) is kept by reference and written back unchanged; while it is kept the store is
 * unavailable, so no outcome is recorded, shown or acknowledged, and live notices continue. Recovery is an offline
 * repair of {@code data/advancedrocketrycommunity_station_notices.dat} from a backup, or its removal, which loses only
 * the outcomes no member has seen yet.
 */
public final class StationNoticeSavedData extends SavedData {
    public static final String NAME = "advancedrocketrycommunity_station_notices";

    private final StationNoticeBook book;
    private final CompoundTag refused;

    private StationNoticeSavedData(StationNoticeBook book, CompoundTag refused) {
        this.book = book;
        this.refused = refused;
    }

    public StationNoticeSavedData() {
        this(new StationNoticeBook(), null);
    }

    public static StationNoticeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StationNoticeSavedData::load,
                StationNoticeSavedData::new, NAME);
    }

    public static StationNoticeSavedData load(CompoundTag raw) {
        try {
            return new StationNoticeSavedData(StationNoticeCodec.decode(raw), null);
        } catch (RuntimeException refusedRoot) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_STATION_NOTICES_REFUSED: the station notice store is kept "
                    + "unchanged and unavailable ({}); back it up and repair it offline", refusedRoot.getMessage());
            return new StationNoticeSavedData(new StationNoticeBook(), raw);
        }
    }

    public boolean operational() {
        return refused == null;
    }

    /** The station's summary; the empty one while the store is unavailable. */
    public StationNoticeSummary summary(UUID stationId) {
        return operational() ? book.summary(stationId) : StationNoticeSummary.EMPTY;
    }

    public int size() {
        return book.size();
    }

    /** A new station's empty summary; a full store first drops the summaries of stations that no longer exist. */
    public Optional<StationNoticeBook.Admission> add(UUID stationId, Predicate<UUID> stationExists) {
        if (!operational()) {
            return Optional.empty();
        }
        StationNoticeBook.Admission admission = book.add(stationId);
        if (admission == StationNoticeBook.Admission.FULL) {
            retain(stationExists);
            admission = book.add(stationId);
        }
        if (admission == StationNoticeBook.Admission.ADMITTED) {
            setDirty();
        }
        return Optional.of(admission);
    }

    /** See {@link StationNoticeBook#record}; a full store first drops the summaries of stations that no longer exist. */
    public Optional<StationNoticeBook.Admission> record(StationState station, StationNotice outcome,
                                                        Collection<UUID> told, Predicate<UUID> stationExists) {
        if (!operational()) {
            return Optional.empty();
        }
        StationNoticeBook.Admission admission = book.record(station, outcome, told);
        if (admission == StationNoticeBook.Admission.FULL) {
            retain(stationExists);
            admission = book.record(station, outcome, told);
        }
        if (admission == StationNoticeBook.Admission.ADMITTED) {
            setDirty();
        }
        return Optional.of(admission);
    }

    public Optional<StationNotice> pending(StationState station, UUID player) {
        return operational() ? book.pending(station, player) : Optional.empty();
    }

    public boolean acknowledge(StationState station, UUID player) {
        return operational() && changed(book.acknowledge(station, player));
    }

    public boolean reconcile(StationState station) {
        return operational() && changed(book.reconcile(station));
    }

    public boolean remove(UUID stationId) {
        return operational() && changed(book.remove(stationId));
    }

    /** Drops the summaries of stations that no longer exist; callers pass a predicate of an operational registry. */
    public int retain(Predicate<UUID> stationExists) {
        if (!operational()) {
            return 0;
        }
        int removed = book.retain(stationExists);
        changed(removed > 0);
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag ignored) {
        return refused != null ? refused : StationNoticeCodec.encode(book);
    }

    private boolean changed(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
