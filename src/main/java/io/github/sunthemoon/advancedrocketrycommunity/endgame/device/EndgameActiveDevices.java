package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-054 section 7 active-device counts, kept from loaded devices only. A device that asks to become active is
 * admitted when its owner's and the global count allow, first come first served; devices already active stay
 * active when the limits are lowered or more devices load. Unloading or stopping releases the place.
 */
public final class EndgameActiveDevices {
    private final Map<EndgameSystem, Map<UUID, UUID>> active = new EnumMap<>(EndgameSystem.class);

    public boolean admit(EndgameSystem system, UUID device, UUID owner, int perOwner, int global) {
        Objects.requireNonNull(owner, "owner");
        Map<UUID, UUID> devices = active.computeIfAbsent(system, ignored -> new HashMap<>());
        if (devices.containsKey(Objects.requireNonNull(device, "device"))) {
            return true;
        }
        if (devices.size() >= global || ownerCount(system, owner) >= perOwner) {
            return false;
        }
        devices.put(device, owner);
        return true;
    }

    public void release(EndgameSystem system, UUID device) {
        Map<UUID, UUID> devices = active.get(system);
        if (devices != null) {
            devices.remove(device);
        }
    }

    public boolean active(EndgameSystem system, UUID device) {
        Map<UUID, UUID> devices = active.get(system);
        return devices != null && devices.containsKey(device);
    }

    public int count(EndgameSystem system) {
        Map<UUID, UUID> devices = active.get(system);
        return devices == null ? 0 : devices.size();
    }

    public int ownerCount(EndgameSystem system, UUID owner) {
        Map<UUID, UUID> devices = active.get(system);
        if (devices == null) {
            return 0;
        }
        int count = 0;
        for (UUID value : devices.values()) {
            if (value.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        active.clear();
    }
}
