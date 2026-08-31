package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bounded access-ordered index; eviction and invalidation always fail closed. */
public final class VolumeIndex {
    private final int maxVolumes;
    private final int maxCells;
    private final LinkedHashMap<VolumeId, AtmosphereVolume> volumes =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Map<VolumePosition, VolumeId> cells = new HashMap<>();

    public VolumeIndex() {
        this(AtmosphereLimits.MAX_INDEXED_VOLUMES, AtmosphereLimits.MAX_INDEXED_CELLS);
    }

    public VolumeIndex(int maxVolumes, int maxCells) {
        if (maxVolumes <= 0 || maxVolumes > AtmosphereLimits.MAX_INDEXED_VOLUMES) {
            throw new IllegalArgumentException("Invalid indexed-volume limit");
        }
        if (maxCells <= 0 || maxCells > AtmosphereLimits.MAX_INDEXED_CELLS) {
            throw new IllegalArgumentException("Invalid indexed-cell limit");
        }
        this.maxVolumes = maxVolumes;
        this.maxCells = maxCells;
    }

    public List<VolumeId> put(AtmosphereVolume volume) {
        Objects.requireNonNull(volume, "volume");
        if (volume.cells().size() > maxCells) {
            throw new IllegalArgumentException("Volume exceeds the complete index cell budget");
        }

        List<VolumeId> evicted = new ArrayList<>();
        remove(volume.id());
        Set<VolumeId> overlapping = new HashSet<>();
        for (VolumePosition position : volume.cells()) {
            VolumeId existing = cells.get(position);
            if (existing != null && !existing.equals(volume.id())) {
                overlapping.add(existing);
            }
        }
        for (VolumeId id : overlapping) {
            if (remove(id).isPresent()) {
                evicted.add(id);
            }
        }

        while (volumes.size() >= maxVolumes || cells.size() + volume.cells().size() > maxCells) {
            VolumeId eldest = volumes.keySet().iterator().next();
            remove(eldest);
            evicted.add(eldest);
        }
        volumes.put(volume.id(), volume);
        for (VolumePosition position : volume.cells()) {
            cells.put(position, volume.id());
        }
        return List.copyOf(evicted);
    }

    public Optional<AtmosphereVolume> find(VolumePosition position) {
        VolumeId id = cells.get(position);
        return id == null ? Optional.empty() : Optional.ofNullable(volumes.get(id));
    }

    public Optional<AtmosphereVolume> find(VolumeId id) {
        return Optional.ofNullable(volumes.get(id));
    }

    public Set<VolumeId> invalidateAround(Set<VolumePosition> positions) {
        Objects.requireNonNull(positions, "positions");
        Set<VolumeId> invalidated = new HashSet<>();
        for (VolumePosition position : positions) {
            VolumeId id = cells.get(position);
            if (id != null) {
                invalidated.add(id);
            }
        }
        for (VolumeId id : invalidated) {
            remove(id);
        }
        return Set.copyOf(invalidated);
    }

    public Optional<AtmosphereVolume> remove(VolumeId id) {
        AtmosphereVolume removed = volumes.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        for (VolumePosition position : removed.cells()) {
            cells.remove(position, id);
        }
        return Optional.of(removed);
    }

    public void clear() {
        volumes.clear();
        cells.clear();
    }

    public int volumeCount() {
        return volumes.size();
    }

    public int cellCount() {
        return cells.size();
    }

    public List<AtmosphereVolume> snapshot() {
        return List.copyOf(volumes.values());
    }
}
