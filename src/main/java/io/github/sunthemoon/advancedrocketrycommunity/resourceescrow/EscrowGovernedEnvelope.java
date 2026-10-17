package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** R18: physical riders are observations, not a governed F1-F8 resource commitment. */
public final class EscrowGovernedEnvelope {
    private EscrowGovernedEnvelope() { }

    public static CompoundTag capture(RocketEntity rocket) {
        return comparison(EscrowNativeEnvelope.rocket(rocket));
    }

    public static CompoundTag comparison(CompoundTag envelope) {
        var flight = RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data().orElseThrow(() -> new IllegalArgumentException("Invalid governed flight"));
        riders(flight.passengers(), envelope);
        var result = envelope.copy(); result.put("passengers", new ListTag()); return result;
    }

    /** Only governed envelope digests change. Uncrewed inputs retain their existing digest, and
     * settlement/resource digests keep their separate definitions. The manifest stays exact.
     * Earlier development crewed arms do not match this definition; no migration is inferred. */
    public static String digest(CompoundTag envelope) { return EscrowNativeEnvelope.digest(comparison(envelope)); }

    static boolean same(CompoundTag expected, CompoundTag observed) {
        return expected == null || observed == null ? expected == observed
                : comparison(expected).equals(comparison(observed));
    }

    /** No flight/snapshot encoding or digest on interpolation observations. */
    static CompoundTag frame(RocketEntity rocket) {
        var result = EscrowNativeEnvelope.frame(rocket);
        riders(rocket.flightData().orElseThrow().passengers(), result);
        result.put("passengers", new ListTag()); return result;
    }

    static Set<UUID> riders(RocketPassengerManifest manifest, CompoundTag envelope) {
        var ids = new HashSet<UUID>();
        for (var raw : EscrowTags.list(envelope, "passengers", RocketFlightLimits.MAX_PASSENGERS)) {
            var rider = (CompoundTag) raw;
            EscrowTags.require(rider.getAllKeys().equals(Set.of("physical")) && rider.hasUUID("physical"), "Invalid physical rider");
            var id = rider.getUUID("physical");
            EscrowTags.require(manifest.assignment(id).isPresent() && ids.add(id), "Unexpected or duplicate physical rider");
        }
        return Set.copyOf(ids);
    }

    static boolean liveRiders(RocketEntity rocket) {
        var ids = new HashSet<UUID>(); var manifest = rocket.flightData().orElseThrow().passengers();
        return rocket.getPassengers().size() <= RocketFlightLimits.MAX_PASSENGERS
                && rocket.getPassengers().stream().allMatch(rider -> manifest.assignment(rider.getUUID()).isPresent() && ids.add(rider.getUUID()));
    }
}
