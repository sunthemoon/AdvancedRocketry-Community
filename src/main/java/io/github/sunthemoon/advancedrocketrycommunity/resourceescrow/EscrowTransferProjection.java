package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Canonical references to the enclosing record's bodies, not duplicate resource/snapshot payloads. */
final class EscrowTransferProjection {
    private static final List<String> POSE = List.of("pos", "motion", "no_gravity", "no_physics",
            "on_ground", "invulnerable", "yaw", "pitch", "passengers");
    private static final Set<String> FLIGHT_CHANGES = Set.of("state", "plan", "current_body",
            "state_started_game_time", "active_transfer_id", "current_target");
    private static final Set<String> OPTIONAL_FLIGHT = Set.of("plan", "active_transfer_id");

    private EscrowTransferProjection() { }

    static CompoundTag capture(CompoundTag envelope, EscrowFlightTables.Armed arm, boolean destination,
                               RocketStructureSnapshot snapshot, RocketFlightData flight) {
        EscrowTags.bound(envelope, EscrowNativeEnvelope.MAX_BYTES, 24, 131_072);
        var compact = new CompoundTag();
        for (String key : POSE) {
            EscrowTags.require(envelope.contains(key), "Missing native transfer pose");
            compact.put(key, envelope.get(key).copy());
        }
        var base = RocketFlightNbtCodec.encode(flight);
        var actual = EscrowTags.compound(envelope, "flight");
        var changed = new CompoundTag(); var absent = new ListTag();
        for (String key : actual.getAllKeys()) {
            if (!actual.get(key).equals(base.get(key))) {
                EscrowTags.require(!destination && FLIGHT_CHANGES.contains(key), "Transfer changed a shared flight body");
                changed.put(key, actual.get(key).copy());
            }
        }
        for (String key : base.getAllKeys().stream().sorted().toList()) {
            if (!actual.contains(key)) {
                EscrowTags.require(!destination && OPTIONAL_FLIGHT.contains(key), "Transfer omitted a shared flight field");
                absent.add(StringTag.valueOf(key));
            }
        }
        compact.put("flight_changes", changed); compact.put("flight_absent", absent);
        EscrowTags.require(expand(compact, arm, destination, snapshot, flight).equals(envelope),
                "Native envelope differs from its canonical record references");
        return compact;
    }

    static CompoundTag expand(CompoundTag compact, EscrowFlightTables.Armed arm, boolean destination,
                              RocketStructureSnapshot snapshot, RocketFlightData flight) {
        EscrowTags.fields(compact, "pos", "motion", "no_gravity", "no_physics", "on_ground",
                "invulnerable", "yaw", "pitch", "passengers", "flight_changes", "flight_absent");
        var body = RocketFlightNbtCodec.encode(flight);
        var changes = EscrowTags.compound(compact, "flight_changes");
        EscrowTags.require(FLIGHT_CHANGES.containsAll(changes.getAllKeys()), "Invalid native flight projection");
        for (String key : changes.getAllKeys()) {
            EscrowTags.require(!changes.get(key).equals(body.get(key)), "Redundant native flight projection");
            body.put(key, changes.get(key).copy());
        }
        EscrowTags.type(compact, "flight_absent", Tag.TAG_LIST);
        var absent = (ListTag) compact.get("flight_absent"); var removed = new HashSet<String>();
        EscrowTags.require(absent.size() <= OPTIONAL_FLIGHT.size()
                && (absent.isEmpty() || absent.getElementType() == Tag.TAG_STRING), "Invalid absent flight fields");
        String previous = "";
        for (Tag raw : absent) {
            String key = raw.getAsString();
            EscrowTags.require(OPTIONAL_FLIGHT.contains(key) && removed.add(key) && body.contains(key)
                    && !changes.contains(key) && key.compareTo(previous) > 0, "Invalid absent flight field");
            previous = key; body.remove(key);
        }
        EscrowTags.require(!destination || changes.isEmpty() && absent.isEmpty(), "Destination flight body changed");
        var decoded = RocketFlightNbtCodec.decode(body).data().orElseThrow(() -> new IllegalArgumentException("Invalid checkpoint flight"));
        EscrowTags.require(RocketFlightNbtCodec.encode(decoded).equals(body), "Noncanonical checkpoint flight");
        if (!destination) {
            EscrowTags.require(decoded.state().acceptsFuel() && decoded.activeTransferId().isEmpty(), "F1 source is not stationary");
        }
        var t = new CompoundTag(); t.putInt("schema_version", 1);
        t.putUUID("physical", destination ? arm.destination() : arm.source()); t.putString("type", EscrowAdapterIds.ROCKET);
        t.putUUID("logical", arm.logical()); t.putUUID("owner", arm.owner());
        t.putString("level", snapshot.sourceDimension().toString());
        var position = vector(compact, "pos"); vector(compact, "motion");
        int x = net.minecraft.util.Mth.floor(position.getDouble(0));
        int z = net.minecraft.util.Mth.floor(position.getDouble(2));
        t.putIntArray("chunk", new int[] {x >> 4, z >> 4});
        t.putUUID("assembly_snapshot", snapshot.snapshotId()); t.putString("content_hash", snapshot.contentHash());
        t.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); t.put("flight", body);
        t.put("wrapper", new RocketEscrowSection(arm.ledger(), destination ? 0 : arm.epoch(), List.of()).encode());
        for (String key : List.of("no_gravity", "no_physics", "on_ground", "invulnerable")) {
            EscrowTags.type(compact, key, Tag.TAG_BYTE);
            EscrowTags.require(compact.getByte(key) == 0 || compact.getByte(key) == 1, "Invalid native motion flag");
        }
        EscrowTags.require(compact.getBoolean("no_physics"), "Native rocket adapter requires noPhysics");
        for (String key : List.of("yaw", "pitch")) {
            EscrowTags.type(compact, key, Tag.TAG_FLOAT);
            EscrowTags.require(Float.isFinite(compact.getFloat(key)), "Invalid native rotation");
        }
        var passengers = EscrowTags.list(compact, "passengers", RocketFlightLimits.MAX_PASSENGERS);
        var ids = new HashSet<java.util.UUID>();
        for (Tag raw : passengers) {
            var passenger = (CompoundTag) raw; EscrowTags.fields(passenger, "physical");
            var id = EscrowTags.uuid(passenger, "physical");
            EscrowTags.require(ids.add(id) && decoded.passengers().assignment(id).isPresent(), "Invalid mounted passenger");
        }
        for (String key : POSE) { t.put(key, compact.get(key).copy()); }
        return t;
    }

    private static ListTag vector(CompoundTag tag, String key) {
        EscrowTags.type(tag, key, Tag.TAG_LIST); var list = (ListTag) tag.get(key);
        EscrowTags.require(list.size() == 3 && list.getElementType() == Tag.TAG_DOUBLE, "Invalid native vector");
        for (int i = 0; i < 3; i++) {
            EscrowTags.require(Double.isFinite(list.getDouble(i)) && Math.abs(list.getDouble(i)) <= 30_000_000,
                    "Native vector outside world bounds");
        }
        return list;
    }
}
