package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable F1 source observation; disk predicates never read a live entity off-thread. */
final class EscrowFlightSource {
    final RocketEntity rocket;
    final CompoundTag envelope;
    final String digest;
    final String signature;
    final String invariant;
    final EscrowEndpoint endpoint;
    final EscrowPrefix.State frontier;
    final AtomicBoolean unchanged = new AtomicBoolean(true);
    private final Object flight;
    private final Object snapshot;
    private final RocketEscrowSection wrapper;

    private final java.util.function.BooleanSupplier boundary;

    EscrowFlightSource(RocketEntity rocket) {
        this(rocket, () -> true);
    }

    EscrowFlightSource(RocketEntity rocket, java.util.function.BooleanSupplier boundary) {
        this.rocket = rocket;
        this.boundary = java.util.Objects.requireNonNull(boundary);
        envelope = EscrowGovernedEnvelope.capture(rocket);
        digest = EscrowNativeEnvelope.digest(envelope);
        signature = signature(rocket.flightData().orElseThrow().fuel());
        invariant = EscrowInvariant.rocket(rocket);
        flight = rocket.flightData().orElseThrow(); snapshot = rocket.snapshot().orElseThrow(); wrapper = rocket.escrowSection();
        EscrowTags.require(wrapper.ledger() != null && wrapper.markers().isEmpty() && rocket.noPhysics,
                "Source checkpoint requires the bound, pruned native adapter");
        endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, envelope.getString("level"), rocket.getUUID(),
                rocket.assemblyTransactionId().orElseThrow(), envelope.getString("type"), rocket.chunkPosition().x, 0,
                rocket.chunkPosition().z, EscrowEndpoint.Unit.PRIMARY, wrapper.epoch(), signature, signature);
        frontier = new EscrowPrefix.State(wrapper.ledger(), wrapper.epoch(), signature, wrapper.markers());
    }

    /** Cheap owner-thread observation of immutable model identities and native mutable fields. */
    boolean observe() {
        if (!unchanged.get()) { return false; }
        var pos = envelope.getList("pos", Tag.TAG_DOUBLE); var motion = envelope.getList("motion", Tag.TAG_DOUBLE);
        boolean same = boundary.getAsBoolean() && !rocket.isRemoved() && rocket.isAddedToWorld() && rocket.operational()
                && rocket.flightData().orElse(null) == flight && rocket.snapshot().orElse(null) == snapshot
                && rocket.escrowSection().equals(wrapper) && rocket.getUUID().equals(endpoint.identity())
                && rocket.ownerId().filter(envelope.getUUID("owner")::equals).isPresent()
                && rocket.level().dimension().location().toString().equals(endpoint.level())
                && rocket.getX() == pos.getDouble(0) && rocket.getY() == pos.getDouble(1) && rocket.getZ() == pos.getDouble(2)
                && rocket.getDeltaMovement().x == motion.getDouble(0) && rocket.getDeltaMovement().y == motion.getDouble(1)
                && rocket.getDeltaMovement().z == motion.getDouble(2)
                && rocket.isNoGravity() == envelope.getBoolean("no_gravity") && rocket.noPhysics == envelope.getBoolean("no_physics")
                && rocket.onGround() == envelope.getBoolean("on_ground") && rocket.isInvulnerable() == envelope.getBoolean("invulnerable")
                && rocket.getYRot() == envelope.getFloat("yaw") && rocket.getXRot() == envelope.getFloat("pitch")
                && EscrowGovernedEnvelope.liveRiders(rocket);
        if (!same) { unchanged.set(false); }
        return same;
    }

    Predicate<CompoundTag> validator() { return validator(endpoint, envelope); }

    static String signature(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState fuel) {
        // Unsupported/legacy endpoints are holds, not an uncaught failure in the server tick.
        EscrowTags.require(fuel.propulsion().isPresent(), "Source checkpoint requires native fuel");
        return fuel.resourceSignature();
    }

    static Predicate<CompoundTag> validator(EscrowEndpoint endpoint, CompoundTag envelope) {
        CompoundTag frozen = envelope.copy();
        return root -> {
            try { return matches(root, endpoint, frozen); }
            catch (RuntimeException invalid) { return false; }
        };
    }

    private static boolean matches(CompoundTag root, EscrowEndpoint endpoint, CompoundTag frozen) {
        int[] position = root.getIntArray("Position");
        EscrowTags.require(position.length == 2 && position[0] == endpoint.x() && position[1] == endpoint.z(), "Wrong F1 slot");
        var objects = new ArrayList<CompoundTag>(); collect(root, "Entities", objects, 0);
        CompoundTag found = null;
        for (var object : objects) {
            boolean exact = object.hasUUID("UUID") && endpoint.identity().equals(object.getUUID("UUID"));
            var data = object.getCompound("RocketEntityData");
            if (data.hasUUID("assembly_transaction_id") && endpoint.logicalRocket().equals(data.getUUID("assembly_transaction_id"))) {
                EscrowTags.require(exact, "Duplicate logical source in F1 slot");
            }
            if (exact) { EscrowTags.require(found == null, "Duplicate physical source in F1 slot"); found = object; }
        }
        if (found == null || !endpoint.type().equals(found.getString("id"))) { return false; }
        var data = found.getCompound("RocketEntityData");
        EscrowTags.fields(data, "schema_version", "assembly_transaction_id", "owner_id", "snapshot", "flight_data", "resource_escrow");
        var rotation = found.getList("Rotation", Tag.TAG_FLOAT);
        return data.getInt("schema_version") == 3 && endpoint.logicalRocket().equals(EscrowTags.uuid(data, "assembly_transaction_id"))
                && frozen.getUUID("owner").equals(EscrowTags.uuid(data, "owner_id"))
                && frozen.getCompound("snapshot").equals(data.getCompound("snapshot"))
                && frozen.getCompound("flight").equals(data.getCompound("flight_data"))
                && frozen.getCompound("wrapper").equals(data.getCompound("resource_escrow"))
                && frozen.getList("pos", Tag.TAG_DOUBLE).equals(found.getList("Pos", Tag.TAG_DOUBLE))
                && frozen.getList("motion", Tag.TAG_DOUBLE).equals(found.getList("Motion", Tag.TAG_DOUBLE))
                && rotation.size() == 2 && rotation.getFloat(0) == frozen.getFloat("yaw") && rotation.getFloat(1) == frozen.getFloat("pitch")
                && found.getBoolean("NoGravity") == frozen.getBoolean("no_gravity")
                && found.getBoolean("OnGround") == frozen.getBoolean("on_ground")
                && found.getBoolean("Invulnerable") == frozen.getBoolean("invulnerable")
                // noPhysics is fixed by the native constructor; player entities are never serialized
                // as vehicle children. Their assignments are in the exact persisted flight manifest.
                && frozen.getBoolean("no_physics") && (!found.contains("Passengers") || found.getList("Passengers", Tag.TAG_COMPOUND).isEmpty());
    }

    private static void collect(CompoundTag parent, String key, List<CompoundTag> objects, int depth) {
        EscrowTags.require(depth <= 16, "F1 passenger depth exceeded");
        for (var raw : EscrowTags.list(parent, key, EscrowSettlementObserver.ENTITIES_PER_SLOT)) {
            EscrowTags.require(objects.size() < EscrowSettlementObserver.ENTITIES_PER_SLOT, "F1 slot census exceeded");
            var object = (CompoundTag) raw; objects.add(object);
            if (object.contains("Passengers")) { collect(object, "Passengers", objects, depth + 1); }
        }
    }
}
