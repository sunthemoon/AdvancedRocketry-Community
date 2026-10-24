package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.persistence.TravelTargetNbtCodec;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** New schema-1 metadata only; no inventory, resource ownership or change to loader/flight roots. */
public record RocketMonitorRoot(UUID owner, UUID device, LoaderBinding binding, int mode, boolean powered, boolean armed,
                                TravelTarget destination, String authority) {
    public static final String KEY = "arce_rocket_monitor";
    public static final int MAX_BYTES = 4096, MAX_DEPTH = 12, MAX_NODES = 128;

    public RocketMonitorRoot {
        Objects.requireNonNull(device);
        Objects.requireNonNull(authority);
        require(mode >= 0 && mode <= 1 && (binding == null || owner != null));
        require(destination == null || destination instanceof TravelTarget.BodySurface || destination instanceof TravelTarget.Station);
        require(authority.isEmpty() || authority.matches("[0-9a-f]{64}") && binding != null && destination != null);
        require(!armed || !authority.isEmpty());
    }

    public RocketMonitorRoot bind(LoaderBinding next) { return new RocketMonitorRoot(owner, device, next, 0, powered, false, null, ""); }
    public RocketMonitorRoot signal(boolean next) { return new RocketMonitorRoot(owner, device, binding, mode, next, armed, destination, authority); }
    public RocketMonitorRoot configure(int nextMode, boolean nextArmed, TravelTarget target, String stamp) {
        return new RocketMonitorRoot(owner, device, binding, nextMode, powered, nextArmed, target, stamp);
    }
    public RocketMonitorRoot disarm() { return new RocketMonitorRoot(owner, device, binding, 0, powered, false, null, ""); }

    public CompoundTag encode() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putUUID("device", device);
        if (owner != null) { tag.putUUID("owner", owner); }
        if (binding != null) { tag.put("binding", binding.encode()); }
        tag.putInt("mode", mode); tag.putBoolean("powered", powered);
        tag.putBoolean("armed", armed);
        if (destination != null) { tag.put("destination", TravelTargetNbtCodec.encode(destination).result().orElseThrow()); }
        tag.putString("authority", authority);
        require(bounded(tag));
        return tag;
    }

    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES); }
    public static RocketMonitorRoot decode(Tag raw) {
        require(raw instanceof CompoundTag && bounded(raw));
        var tag = (CompoundTag) raw;
        require(Set.of("schema", "owner", "device", "binding", "mode", "powered", "armed", "destination", "authority").containsAll(tag.getAllKeys()));
        require(tag.contains("schema", Tag.TAG_INT) && tag.getInt("schema") == 1 && tag.hasUUID("device")
                && (!tag.contains("owner") || tag.hasUUID("owner")) && tag.contains("mode", Tag.TAG_INT)
                && tag.contains("powered", Tag.TAG_BYTE) && (tag.getByte("powered") == 0 || tag.getByte("powered") == 1)
                && tag.contains("armed", Tag.TAG_BYTE) && (tag.getByte("armed") == 0 || tag.getByte("armed") == 1)
                && tag.contains("authority", Tag.TAG_STRING));
        return new RocketMonitorRoot(tag.contains("owner") ? tag.getUUID("owner") : null, tag.getUUID("device"),
                tag.contains("binding") ? LoaderBinding.decode(tag.get("binding")) : null,
                tag.getInt("mode"), tag.getBoolean("powered"), tag.getBoolean("armed"), tag.contains("destination")
                ? TravelTargetNbtCodec.decode(tag.get("destination")).result().orElseThrow(() -> new IllegalArgumentException("Invalid target")) : null,
                tag.getString("authority"));
    }

    private static void require(boolean valid) { if (!valid) { throw new IllegalArgumentException("Unsupported monitor metadata"); } }
}
