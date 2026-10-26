package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Only live Forge persistent data is changed; vanilla owns all player-file writes. */
final class RocketRejoinMarker {
    static final String KEY = "arce_passenger_rejoin";
    static final int SCHEMA = 1, MAX_BYTES = 160;
    record Value(UUID release, UUID player, UUID generation) { }
    private RocketRejoinMarker() { }
    static CompoundTag encode(Value value) {
        var tag = new CompoundTag(); tag.putInt("schema", SCHEMA);
        tag.putUUID("release", value.release()); tag.putUUID("player", value.player()); tag.putUUID("generation", value.generation());
        return tag;
    }
    static Value decode(CompoundTag tag) {
        if (tag.size() != 4 || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA
                || !tag.hasUUID("release") || !tag.hasUUID("player") || !tag.hasUUID("generation")) { return null; }
        return new Value(tag.getUUID("release"), tag.getUUID("player"), tag.getUUID("generation"));
    }
    static boolean matches(CompoundTag persistent, RocketRejoinRoot.Entry entry) {
        var value = decode(persistent.getCompound(KEY));
        return value != null && entry.attempt() != null && value.release().equals(entry.release())
                && value.player().equals(entry.player()) && value.generation().equals(entry.attempt().generation());
    }
}
