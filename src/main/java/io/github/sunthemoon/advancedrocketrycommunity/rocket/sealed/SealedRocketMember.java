package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Schema-1 inert member data. Registry/provider availability is a later materialization decision. */
public record SealedRocketMember(UUID transaction, UUID snapshot, UUID entity, String contentHash,
        ResourceLocation dimension, RocketPosition position, int index, RocketBlockState state,
        RocketBlockEntityPayload payload) {
    public static final String ROOT = "arce_sealed_rocket_member";
    public static final ResourceLocation TYPE = ModIdentity.id("sealed_rocket_block");
    public static final int SCHEMA = 1;
    public static final int MAX_BYTES = 278_528;
    public static final int MAX_DEPTH = 35;
    public static final int MAX_NODES = 262_272;
    private static final Set<String> KEYS = Set.of("schema", "transaction_id", "snapshot_id", "rocket_entity_id",
            "content_hash", "dimension", "position", "member_index", "state");

    public SealedRocketMember {
        Objects.requireNonNull(transaction); Objects.requireNonNull(snapshot); Objects.requireNonNull(entity);
        Objects.requireNonNull(dimension); Objects.requireNonNull(position); Objects.requireNonNull(state);
        require(contentHash != null && contentHash.length() == 64
                && contentHash.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f'));
        require(dimension.toString().length() <= RocketLimits.MAX_IDENTIFIER_LENGTH);
        require(index >= 0 && index < RocketLimits.MAX_BLOCKS);
    }

    public CompoundTag encode() {
        var root = new CompoundTag();
        root.putInt("schema", SCHEMA);
        root.putUUID("transaction_id", transaction); root.putUUID("snapshot_id", snapshot);
        root.putUUID("rocket_entity_id", entity); root.putString("content_hash", contentHash);
        root.putString("dimension", dimension.toString());
        root.putIntArray("position", new int[] {position.x(), position.y(), position.z()});
        root.putInt("member_index", index);
        var encodedState = new CompoundTag();
        encodedState.putString("id", state.blockId().toString());
        var properties = new CompoundTag();
        state.properties().forEach(properties::putString);
        encodedState.put("properties", properties); root.put("state", encodedState);
        if (payload != null) {
            var envelope = new CompoundTag();
            envelope.putString("adapter", payload.adapterId().toString());
            envelope.put("data", payload.data()); root.put("block_entity", envelope);
        }
        require(fitsFullTag(root, position));
        return root;
    }

    /** No coercion, registry lookup, normalization or mutation; the caller retains undecodable input. */
    public static Optional<SealedRocketMember> decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !BoundedNbt.fits(root, MAX_BYTES, MAX_DEPTH, MAX_NODES)) {
            return Optional.empty();
        }
        try {
            require(root.getAllKeys().equals(KEYS) || root.getAllKeys().equals(withPayloadKeys()));
            exact(root, "schema", Tag.TAG_INT); require(root.getInt("schema") == SCHEMA);
            UUID transaction = uuid(root, "transaction_id"); UUID snapshot = uuid(root, "snapshot_id");
            UUID entity = uuid(root, "rocket_entity_id");
            exact(root, "content_hash", Tag.TAG_STRING); exact(root, "member_index", Tag.TAG_INT);
            exact(root, "position", Tag.TAG_INT_ARRAY);
            int[] coordinates = root.getIntArray("position"); require(coordinates.length == 3);
            var position = new RocketPosition(coordinates[0], coordinates[1], coordinates[2]);
            require(fitsFullTag(root, position));
            exact(root, "state", Tag.TAG_COMPOUND);
            var state = root.getCompound("state");
            require(state.getAllKeys().equals(Set.of("id", "properties")));
            var block = id(state, "id"); exact(state, "properties", Tag.TAG_COMPOUND);
            var rawProperties = state.getCompound("properties");
            require(rawProperties.size() <= RocketLimits.MAX_BLOCK_PROPERTIES);
            Map<String, String> properties = new TreeMap<>();
            for (String key : rawProperties.getAllKeys()) {
                exact(rawProperties, key, Tag.TAG_STRING); properties.put(key, rawProperties.getString(key));
            }
            RocketBlockEntityPayload payload = null;
            if (root.contains("block_entity")) {
                exact(root, "block_entity", Tag.TAG_COMPOUND);
                var envelope = root.getCompound("block_entity");
                require(envelope.getAllKeys().equals(Set.of("adapter", "data")));
                var adapter = id(envelope, "adapter"); exact(envelope, "data", Tag.TAG_COMPOUND);
                var data = envelope.getCompound("data");
                require(BoundedNbt.fits(data, RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, 32, 262_144));
                payload = new RocketBlockEntityPayload(adapter, data);
            }
            return Optional.of(new SealedRocketMember(transaction, snapshot, entity, root.getString("content_hash"),
                    id(root, "dimension"), position, root.getInt("member_index"), new RocketBlockState(block, properties), payload));
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Includes native id/x/y/z and the named root; payload limits never pay for this framing. */
    public static boolean fitsFullTag(Tag root, RocketPosition at) {
        var outer = new CompoundTag();
        outer.putString("id", TYPE.toString());
        outer.putInt("x", at.x()); outer.putInt("y", at.y()); outer.putInt("z", at.z());
        outer.put(ROOT, root);
        return BoundedNbt.fits(outer, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    private static Set<String> withPayloadKeys() {
        var keys = new java.util.HashSet<>(KEYS); keys.add("block_entity"); return keys;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        exact(tag, key, Tag.TAG_INT_ARRAY); require(tag.getIntArray(key).length == 4); return tag.getUUID(key);
    }

    private static ResourceLocation id(CompoundTag tag, String key) {
        exact(tag, key, Tag.TAG_STRING);
        String text = tag.getString(key); require(text.length() <= RocketLimits.MAX_IDENTIFIER_LENGTH);
        var id = ResourceLocation.tryParse(text); require(id != null && id.toString().equals(text)); return id;
    }

    private static void exact(CompoundTag tag, String key, int type) { require(tag.contains(key, type)); }
    private static void require(boolean condition) {
        if (!condition) { throw new IllegalArgumentException("Invalid sealed rocket member"); }
    }
}
