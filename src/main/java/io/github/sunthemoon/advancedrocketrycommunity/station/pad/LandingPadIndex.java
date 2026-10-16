package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-lifecycle-owned index. No world scans, global mutable collection, tickets or chunk loads. */
public final class LandingPadIndex extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_landing_pads";
    private final Map<UUID, Map<UUID, LandingPadEntry>> stations = new LinkedHashMap<>();
    private long nextEpoch;
    private CompoundTag preserved;
    private boolean saveRefusalReported;
    private final Map<UUID, Long> interactions = new LinkedHashMap<>();
    private final Map<UUID, io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot> choices = new LinkedHashMap<>();

    /** Menu-only intent, bounded and transient; never grants authority or persists flight coordinates. */
    public boolean choose(UUID actor, UUID station, LandingPadReference reference) {
        if (!operational() || !choices.containsKey(actor) && choices.size() >= 128) { return false; }
        choices.put(actor, new io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot(
                new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.Station(station), reference));
        return true;
    }
    public io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot choice(UUID actor) {
        return operational() ? choices.getOrDefault(actor,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot.empty())
                : io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot.empty();
    }
    public void forgetChoice(UUID actor) { choices.remove(actor); }

    public boolean allowInteraction(UUID actor, long time) {
        Long previous = interactions.get(actor);
        if (previous != null && time >= previous && time - previous < 10) { return false; }
        interactions.entrySet().removeIf(entry -> time < entry.getValue() || time - entry.getValue() >= 10);
        if (interactions.size() >= 128) { return false; }
        interactions.put(actor, time); return true;
    }

    public static LandingPadIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LandingPadIndex::load, LandingPadIndex::new, DATA_NAME);
    }

    public static LandingPadIndex load(CompoundTag root) {
        LandingPadIndex data = new LandingPadIndex();
        try {
            var decoded = LandingPadIndexCodec.decode(root);
            data.nextEpoch = decoded.nextEpoch();
            for (var entry : decoded.entries()) { data.bucket(entry.stationId()).put(entry.padId(), entry); }
        } catch (IllegalArgumentException invalid) {
            // Native preflight failed: retain the original, do not recursively copy an unbounded tag.
            data.preserved = LandingPadIndexCodec.fits(root) ? root.copy() : root;
        }
        return data;
    }

    public boolean operational() { return preserved == null; }
    public List<LandingPadEntry> entries(UUID station) {
        return operational() ? List.copyOf(stations.getOrDefault(station, Map.of()).values()) : List.of();
    }
    public Optional<LandingPadEntry> find(UUID station, LandingPadReference reference) {
        if (!operational() || reference == null) { return Optional.empty(); }
        var entry = stations.getOrDefault(station, Map.of()).get(reference.padId());
        return entry != null && entry.epoch() == reference.epoch() ? Optional.of(entry) : Optional.empty();
    }

    public LandingPadEntry register(UUID station, UUID owner, UUID physical, String name, StationPosition local) {
        if (!operational()) { throw new IllegalStateException("Landing-pad index is unavailable"); }
        var current = entries(station);
        if (current.size() >= LandingPadIndexCodec.MAX_PADS
                || (!stations.containsKey(station) && stations.size() >= LandingPadIndexCodec.MAX_STATIONS)
                || current.stream().anyMatch(e -> e.localPosition().equals(local) || e.physicalId().equals(physical))
                || nextEpoch == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Landing-pad limit or duplicate position");
        }
        LandingPadEntry entry = new LandingPadEntry(station, UUID.randomUUID(), physical, owner, name, local, nextEpoch, null);
        List<LandingPadEntry> candidate = all(); candidate.add(entry);
        // Reserve worst-case claim bytes before publishing any index entry.
        LandingPadIndexCodec.decode(LandingPadIndexCodec.encode(nextEpoch + 1, candidate, true));
        bucket(station).put(entry.padId(), entry); nextEpoch++; setDirty();
        return entry;
    }

    public boolean remove(UUID station, LandingPadReference reference, UUID physical) {
        LandingPadEntry entry = find(station, reference).orElse(null);
        if (entry == null || !entry.matches(reference, physical)) { return false; }
        var bucket = stations.get(station); bucket.remove(entry.padId());
        if (bucket.isEmpty()) { stations.remove(station); }
        setDirty(); return true;
    }

    public boolean claim(UUID station, LandingPadReference reference, UUID physical, UUID transfer) {
        java.util.Objects.requireNonNull(transfer);
        LandingPadEntry entry = find(station, reference).orElse(null);
        if (entry == null || !entry.matches(reference, physical)
                || entry.claim() != null && !entry.claim().equals(transfer)) { return false; }
        bucket(station).put(entry.padId(), entry.withClaim(transfer)); setDirty(); return true;
    }

    public boolean release(UUID station, LandingPadReference reference, UUID transfer) {
        LandingPadEntry entry = find(station, reference).orElse(null);
        if (entry == null || !transfer.equals(entry.claim())) { return false; }
        bucket(station).put(entry.padId(), entry.withClaim(null)); setDirty(); return true;
    }

    /** Checked terminal cleanup may restart after its journal record, and hence its plan, is gone. */
    public boolean releaseTransfer(UUID transfer) {
        java.util.Objects.requireNonNull(transfer);
        if (!operational()) { return false; }
        // The codec/registration limits bound this saved index; never query any world blocks.
        for (var bucket : stations.values()) {
            for (var entry : List.copyOf(bucket.values())) {
                if (transfer.equals(entry.claim())) { bucket.put(entry.padId(), entry.withClaim(null)); setDirty(); }
            }
        }
        return true;
    }

    @Override public CompoundTag save(CompoundTag ignored) {
        return preserved == null ? LandingPadIndexCodec.encode(nextEpoch, all(), false) : preserved;
    }
    @Override public void save(java.io.File file) {
        if (preserved != null && !LandingPadIndexCodec.fits(preserved)) {
            setDirty();
            if (!saveRefusalReported) {
                saveRefusalReported = true;
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_LANDING_PAD_INDEX_SAVE_REFUSED: back up and repair the oversized pad index");
            }
            return;
        }
        super.save(file);
    }
    private Map<UUID, LandingPadEntry> bucket(UUID station) {
        return stations.computeIfAbsent(station, id -> new LinkedHashMap<>());
    }
    private List<LandingPadEntry> all() {
        var entries = new ArrayList<LandingPadEntry>();
        stations.values().forEach(bucket -> entries.addAll(bucket.values())); return entries;
    }
}
