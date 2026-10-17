package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;

/** Fixed native adapter envelope, never arbitrary vanilla/third-party restoration data. */
public final class EscrowNativeEnvelope {
    public static final int MAX_BYTES = RocketLimits.MAX_TOTAL_NBT_BYTES + 65_536 + 4_096;
    private EscrowNativeEnvelope() { }
    public static RocketFlightData fuelOnly(RocketFlightData current, RocketFuelState fuel) {
        return RocketFlightData.restore(current.schemaVersion(), current.logicalRocketId(), current.state(), fuel,
                current.plan().orElse(null), current.passengers(), current.currentBody(), current.currentDimension(),
                current.currentOrigin(), current.stateStartedGameTime(), current.activeTransferId().orElse(null), current.currentTarget().orElse(null));
    }
    public static CompoundTag rocket(RocketEntity rocket) {
        return rocket(rocket, rocket.flightData().orElseThrow(), rocket.escrowSection());
    }
    public static CompoundTag rocket(RocketEntity rocket, RocketFlightData flight, RocketEscrowSection wrapper) {
        EscrowTags.require(rocket.operational() && !rocket.level().isClientSide && rocket.getServer().isSameThread(), "Native capture requires server owner thread");
        rocket.governedCaptured(); // Review N4: a per-instance counter lets a GameTest pin capture-free warm visits.
        var t = frame(rocket);
        var snapshot = rocket.snapshot().orElseThrow();
        t.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); t.put("flight", RocketFlightNbtCodec.encode(flight)); t.put("wrapper", wrapper.encode());
        EscrowTags.bound(t, MAX_BYTES, 24, 131_072); return t;
    }
    /** Small mutable projection for a previously validated immutable snapshot/flight/wrapper binding. */
    static CompoundTag frame(RocketEntity rocket) {
        var t = new CompoundTag(); t.putInt("schema_version", 1); t.putUUID("physical", rocket.getUUID());
        t.putString("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(rocket.getType()).toString());
        t.putUUID("logical", rocket.assemblyTransactionId().orElseThrow()); t.putUUID("owner", rocket.ownerId().orElseThrow());
        t.putString("level", rocket.level().dimension().location().toString()); t.putIntArray("chunk", new int[] {rocket.chunkPosition().x, rocket.chunkPosition().z});
        t.put("pos", vector(rocket.getX(), rocket.getY(), rocket.getZ()));
        var snapshot = rocket.snapshot().orElseThrow(); t.putUUID("assembly_snapshot", snapshot.snapshotId()); t.putString("content_hash", snapshot.contentHash());
        t.put("motion", vector(rocket.getDeltaMovement().x, rocket.getDeltaMovement().y, rocket.getDeltaMovement().z));
        t.putBoolean("no_gravity", rocket.isNoGravity()); t.putBoolean("no_physics", rocket.noPhysics); t.putBoolean("on_ground", rocket.onGround());
        t.putBoolean("invulnerable", rocket.isInvulnerable()); t.putFloat("yaw", rocket.getYRot()); t.putFloat("pitch", rocket.getXRot());
        var passengers = new ListTag();
        EscrowTags.require(rocket.getPassengers().size() <= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_PASSENGERS,
                "Native passenger projection exceeds its bound");
        for (var passenger : rocket.getPassengers()) { var p = new CompoundTag(); p.putUUID("physical", passenger.getUUID()); passengers.add(p); }
        t.put("passengers", passengers); return t;
    }
    public static String digest(CompoundTag envelope) { return CanonicalNbtDigest.of(envelope, MAX_BYTES, 24); }
    private static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); list.add(DoubleTag.valueOf(x)); list.add(DoubleTag.valueOf(y)); list.add(DoubleTag.valueOf(z)); return list;
    }
}
