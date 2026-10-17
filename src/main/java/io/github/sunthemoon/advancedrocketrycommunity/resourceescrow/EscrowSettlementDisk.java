package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** Immutable O2 predicates, shared by capture and stable readback. Never touches a live world. */
final class EscrowSettlementDisk {
    private EscrowSettlementDisk() { }
    static Predicate<CompoundTag> validator(EscrowSettlementIntent.Observation frozen, UUID ledger) {
        return root -> {
            try { return frozen.equals(observe(root, frozen.endpoint(), ledger)); }
            catch (RuntimeException invalid) { return false; }
        };
    }
    static EscrowSettlementIntent.Observation observe(CompoundTag root, EscrowEndpoint endpoint, UUID ledger) {
        boolean entity = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
        if (entity) {
            int[] position = root.getIntArray("Position");
            EscrowTags.require(position.length == 2 && position[0] == endpoint.x() && position[1] == endpoint.z(), "Wrong absence entity slot");
        } else {
            EscrowTags.require(EscrowTags.integer(root, "xPos") == endpoint.x() >> 4 && EscrowTags.integer(root, "zPos") == endpoint.z() >> 4,
                    "Wrong absence terrain slot");
        }
        var objects = new ArrayList<CompoundTag>();
        collect(EscrowTags.list(root, entity ? "Entities" : "block_entities", EscrowSettlementObserver.ENTITIES_PER_SLOT), objects, 0, entity);
        CompoundTag found = null;
        for (var object : objects) {
            boolean exact = entity ? object.hasUUID("UUID") && endpoint.identity().equals(object.getUUID("UUID"))
                    : EscrowTags.integer(object, "x") == endpoint.x() && EscrowTags.integer(object, "y") == endpoint.y()
                    && EscrowTags.integer(object, "z") == endpoint.z();
            if (entity && object.getCompound("RocketEntityData").hasUUID("assembly_transaction_id")
                    && endpoint.logicalRocket().equals(object.getCompound("RocketEntityData").getUUID("assembly_transaction_id"))) {
                EscrowTags.require(exact, "Unproved logical entity copy");
            }
            if (!entity && object.getCompound("ForgeData").getCompound("arce_escrow_fixture").hasUUID("identity")
                    && endpoint.identity().equals(object.getCompound("ForgeData").getCompound("arce_escrow_fixture").getUUID("identity"))) {
                EscrowTags.require(exact, "Relocated source identity");
            }
            if (!entity && endpoint.identity().equals(EscrowLoaderEndpoint.device(object))) {
                EscrowTags.require(exact, "Relocated source identity");
            }
            if (exact) { EscrowTags.require(found == null && endpoint.type().equals(object.getString("id")), "Duplicate/wrong disk endpoint"); found = object; }
        }
        if (found == null) { return new EscrowSettlementIntent.Observation(endpoint, null, false, 0); }
        if (!entity && EscrowLoaderEndpoint.is(endpoint)) { return EscrowLoaderEndpoint.observation(endpoint, EscrowLoaderEndpoint.root(found), ledger); }
        if (!entity) {
            var persistent = EscrowTags.compound(found, "ForgeData");
            EscrowTags.require(endpoint.identity().equals(persistent.getCompound("arce_escrow_fixture").getUUID("identity")), "Source disk identity changed");
            return new EscrowSettlementIntent.Observation(endpoint, EscrowTestEndpoints.state(persistent, ledger, false), true, EscrowTestEndpoints.amount(persistent));
        }
        var wrapper = EscrowTags.compound(found, "RocketEntityData");
        EscrowTags.fields(wrapper, "schema_version", "assembly_transaction_id", "owner_id", "snapshot", "flight_data", "resource_escrow");
        EscrowTags.require(EscrowTags.integer(wrapper, "schema_version") == 3 && endpoint.logicalRocket().equals(EscrowTags.uuid(wrapper, "assembly_transaction_id")),
                "Invalid disk rocket identity/schema");
        var snapshot = RocketSnapshotNbtCodec.decode(wrapper.getCompound("snapshot")).snapshot().orElseThrow();
        var flight = RocketFlightNbtCodec.decode(wrapper.getCompound("flight_data")).data().orElseThrow();
        var section = RocketEscrowSection.decode(wrapper.getCompound("resource_escrow"));
        var position = found.getList("Pos", net.minecraft.nbt.Tag.TAG_DOUBLE); var origin = flight.currentOrigin();
        EscrowTags.require(position.size() == 3 && position.getDouble(0) == origin.x() + .5D && position.getDouble(1) == origin.y()
                && position.getDouble(2) == origin.z() + .5D && flight.currentDimension().toString().equals(endpoint.level())
                && endpoint.logicalRocket().equals(flight.logicalRocketId())
                && flight.state().acceptsFuel() && flight.passengers().assignments().isEmpty()
                && (!found.contains("Passengers") || found.getList("Passengers", 10).isEmpty())
                && (section.ledger() == null || ledger.equals(section.ledger())), "Disk rocket placement/occupancy changed");
        String signature = flight.fuel().resourceSignature();
        return new EscrowSettlementIntent.Observation(endpoint, new EscrowPrefix.State(ledger, section.epoch(), signature, section.markers()),
                section.ledger() != null, flight.fuel().totalAmount(), EscrowSignatureKind.FUEL_VECTOR_V1,
                EscrowInvariant.rocket(endpoint, EscrowTags.uuid(wrapper, "owner_id"), snapshot));
    }
    private static void collect(List<net.minecraft.nbt.Tag> entries, List<CompoundTag> result, int depth, boolean entities) {
        EscrowTags.require(depth <= 16, "Passenger observation depth exceeded");
        for (var raw : entries) {
            EscrowTags.require(result.size() < EscrowSettlementObserver.ENTITIES_PER_SLOT, "Disk identity census exceeded");
            var object = (CompoundTag) raw; result.add(object);
            if (entities && object.contains("Passengers")) {
                collect(EscrowTags.list(object, "Passengers", EscrowSettlementObserver.ENTITIES_PER_SLOT), result, depth + 1, true);
            }
        }
    }
}
