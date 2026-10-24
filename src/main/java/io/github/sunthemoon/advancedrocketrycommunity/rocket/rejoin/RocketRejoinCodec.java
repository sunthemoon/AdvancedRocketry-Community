package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/** Strict schema-1 codec. All structural checks precede recursive copying, decoding or hashing. */
public final class RocketRejoinCodec {
    public static final int SCHEMA = 1, ROOT_BYTES = 1_048_576, DEPTH = 32, NODES = 16_384;
    public static final int ENTRY_BYTES = 512, BATCH_BYTES = 2_048;
    public static final String EPOCH = "v1.8.0-rocket-rejoins";
    private RocketRejoinCodec() { }

    public static CompoundTag encode(RocketRejoinRoot root) {
        var tag = new CompoundTag(); tag.putInt("schema_version", SCHEMA); tag.putString("format_epoch", EPOCH);
        tag.putLong("next_sequence", root.nextSequence());
        var batches = new ListTag(); root.batches().forEach(b -> batches.add(batch(b))); tag.put("batches", batches);
        var entries = new ListTag(); root.entries().forEach(e -> entries.add(entry(e))); tag.put("entries", entries);
        bounds(tag); return tag;
    }

    public static RocketRejoinRoot decode(CompoundTag tag) {
        bounds(tag); fields(tag, "schema_version", "format_epoch", "next_sequence", "batches", "entries");
        require(integer(tag, "schema_version") == SCHEMA && EPOCH.equals(string(tag, "format_epoch")), "Unsupported rejoin schema or epoch");
        var batches = new ArrayList<Batch>();
        for (Tag raw : list(tag, "batches", Tag.TAG_COMPOUND, MAX_BATCHES)) { batches.add(batch((CompoundTag) raw)); }
        var entries = new ArrayList<Entry>();
        for (Tag raw : list(tag, "entries", Tag.TAG_COMPOUND, MAX_ENTRIES)) { entries.add(entry((CompoundTag) raw)); }
        return new RocketRejoinRoot(number(tag, "next_sequence"), batches, entries);
    }

    public static void bounds(CompoundTag tag) {
        require(BoundedNbt.fits(tag, ROOT_BYTES, DEPTH, NODES), "Rejoin root envelope exceeded");
        if (tag.get("batches") instanceof ListTag list) {
            for (Tag item : list) { require(BoundedNbt.fits(item, BATCH_BYTES, DEPTH, NODES), "Rejoin batch envelope exceeded"); }
        }
        if (tag.get("entries") instanceof ListTag list) {
            for (Tag item : list) { require(BoundedNbt.fits(item, ENTRY_BYTES, DEPTH, NODES), "Rejoin entry envelope exceeded"); }
        }
    }

    private static CompoundTag batch(Batch batch) {
        var tag = new CompoundTag(); tag.putUUID("release", batch.release()); tag.putLong("sequence", batch.sequence());
        tag.putUUID("transfer", batch.transfer()); tag.putUUID("rocket", batch.rocket()); tag.putUUID("authority", batch.authority());
        tag.putString("hold", batch.hold().name()); tag.putString("dimension", batch.dimension());
        position(tag, "pad", batch.pad()); position(tag, "minimum", batch.minimum()); position(tag, "maximum", batch.maximum());
        var resources = new CompoundTag(); resources.putString("snapshot", batch.resources().snapshot());
        resources.putString("fuel", batch.resources().fuel()); tag.put("resources", resources);
        tag.put("before", version(batch.before())); tag.put("scrubbed", version(batch.scrubbed()));
        var players = new ListTag(); batch.released().forEach(p -> players.add(NbtUtils.createUUID(p))); tag.put("released", players);
        var slots = new ListTag();
        for (var slot : batch.slots()) {
            var s = new CompoundTag(); s.putString("level", slot.level()); s.putInt("x", slot.x()); s.putInt("z", slot.z()); slots.add(s);
        }
        tag.put("slots", slots);
        return tag;
    }
    private static Batch batch(CompoundTag tag) {
        fields(tag, "release", "sequence", "transfer", "rocket", "authority", "hold", "dimension", "pad", "minimum", "maximum",
                "resources", "before", "scrubbed", "released", "slots");
        var resources = compound(tag, "resources"); fields(resources, "snapshot", "fuel");
        var players = new ArrayList<UUID>();
        for (Tag player : list(tag, "released", Tag.TAG_INT_ARRAY, MAX_PER_BATCH)) { players.add(NbtUtils.loadUUID(player)); }
        var slots = new ArrayList<Slot>();
        for (Tag raw : list(tag, "slots", Tag.TAG_COMPOUND, 3)) {
            var s = (CompoundTag) raw; fields(s, "level", "x", "z");
            slots.add(new Slot(string(s, "level"), integer(s, "x"), integer(s, "z")));
        }
        return new Batch(uuid(tag, "release"), number(tag, "sequence"), uuid(tag, "transfer"), uuid(tag, "rocket"),
                uuid(tag, "authority"), Hold.valueOf(string(tag, "hold")), string(tag, "dimension"), position(tag, "pad"),
                position(tag, "minimum"), position(tag, "maximum"), new Resources(string(resources, "snapshot"), string(resources, "fuel")),
                version(compound(tag, "before")), version(compound(tag, "scrubbed")), players, slots);
    }
    private static CompoundTag entry(Entry entry) {
        var tag = new CompoundTag(); tag.putUUID("release", entry.release()); tag.putUUID("player", entry.player());
        tag.putInt("seat_index", entry.seatIndex()); tag.putString("placement", entry.placement().name()); tag.putString("seat", entry.seat().name());
        if (entry.attempt() != null) {
            var a = entry.attempt(); var attempt = new CompoundTag(); attempt.putUUID("generation", a.generation());
            attempt.putString("target", a.target().name()); attempt.putDouble("x", a.x()); attempt.putDouble("y", a.y()); attempt.putDouble("z", a.z());
            tag.put("attempt", attempt);
        }
        return tag;
    }
    private static Entry entry(CompoundTag tag) {
        fields(tag, "release", "player", "seat_index", "placement", "seat", "attempt");
        Attempt attempt = null;
        if (tag.contains("attempt")) {
            var a = compound(tag, "attempt"); fields(a, "generation", "target", "x", "y", "z");
            attempt = new Attempt(uuid(a, "generation"), Target.valueOf(string(a, "target")), decimal(a, "x"), decimal(a, "y"), decimal(a, "z"));
        }
        return new Entry(uuid(tag, "release"), uuid(tag, "player"), integer(tag, "seat_index"),
                Placement.valueOf(string(tag, "placement")), Seat.valueOf(string(tag, "seat")), attempt);
    }
    private static CompoundTag version(Version version) {
        var tag = new CompoundTag(); tag.putString("checksum", version.checksum()); tag.putString("phase", version.phase());
        if (version.destination() != null) { tag.putUUID("destination", version.destination()); } return tag;
    }
    private static Version version(CompoundTag tag) {
        fields(tag, "checksum", "phase", "destination");
        return new Version(string(tag, "checksum"), string(tag, "phase"), tag.contains("destination") ? uuid(tag, "destination") : null);
    }
    private static void position(CompoundTag tag, String name, Position p) { tag.putIntArray(name, new int[] {p.x(), p.y(), p.z()}); }
    private static Position position(CompoundTag tag, String key) {
        type(tag, key, Tag.TAG_INT_ARRAY); int[] p = tag.getIntArray(key); require(p.length == 3, "Invalid rejoin position");
        return new Position(p[0], p[1], p[2]);
    }
    private static void fields(CompoundTag tag, String... fields) { require(Set.of(fields).containsAll(tag.getAllKeys()), "Unknown rejoin field"); }
    private static void type(CompoundTag tag, String key, int type) { require(tag.contains(key, type), "Missing or mistyped rejoin field: " + key); }
    private static int integer(CompoundTag tag, String key) { type(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { type(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static double decimal(CompoundTag tag, String key) { type(tag, key, Tag.TAG_DOUBLE); return tag.getDouble(key); }
    private static String string(CompoundTag tag, String key) { type(tag, key, Tag.TAG_STRING); return tag.getString(key); }
    private static CompoundTag compound(CompoundTag tag, String key) { type(tag, key, Tag.TAG_COMPOUND); return tag.getCompound(key); }
    private static UUID uuid(CompoundTag tag, String key) { require(tag.hasUUID(key), "Missing or invalid rejoin UUID"); return tag.getUUID(key); }
    private static ListTag list(CompoundTag tag, String key, int elementType, int max) {
        type(tag, key, Tag.TAG_LIST); var list = (ListTag) tag.get(key);
        require(list.size() <= max && (list.isEmpty() || list.getElementType() == elementType), "Invalid rejoin list"); return list;
    }
}
