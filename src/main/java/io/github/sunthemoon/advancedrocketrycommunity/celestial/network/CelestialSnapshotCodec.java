package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Hard-bounded binary codec for the display-only celestial snapshot payload. */
public final class CelestialSnapshotCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PACKET_BYTES = 96 * 1_024;

    private CelestialSnapshotCodec() {
    }

    public static DataResult<byte[]> encode(CelestialCatalog catalog) {
        ByteBuf backing = Unpooled.buffer();
        try {
            FriendlyByteBuf buffer = new FriendlyByteBuf(backing);
            List<CelestialBodyDefinition> definitions = catalog.definitions();
            buffer.writeVarInt(definitions.size());
            for (CelestialBodyDefinition definition : definitions) {
                writeId(buffer, definition.id());
                buffer.writeBoolean(definition.parentId().isPresent());
                definition.parentId().ifPresent(parent -> writeId(buffer, parent));
                writeId(buffer, definition.levelKey().location());
                buffer.writeDouble(definition.gravityMultiplier());
                buffer.writeBoolean(definition.atmosphere().pressure() == 0.0D);
                buffer.writeBoolean(definition.atmosphere().breathable());
                writeId(buffer, definition.atmosphere().profile());
                writeId(buffer, definition.visualProfile());
            }
            if (buffer.readableBytes() > MAX_PACKET_BYTES) {
                return DataResult.error(() -> "Celestial snapshot exceeds " + MAX_PACKET_BYTES + " bytes");
            }
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.getBytes(0, payload);
            return DataResult.success(payload);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Could not encode celestial snapshot: " + exception.getMessage());
        } finally {
            backing.release();
        }
    }

    public static DataResult<CelestialSnapshot> decode(byte[] payload) {
        if (payload.length > MAX_PACKET_BYTES) {
            return DataResult.error(() -> "Celestial snapshot exceeds " + MAX_PACKET_BYTES + " bytes");
        }
        ByteBuf backing = Unpooled.wrappedBuffer(payload);
        try {
            FriendlyByteBuf buffer = new FriendlyByteBuf(backing);
            int count = buffer.readVarInt();
            if (count <= 0 || count > CelestialCatalog.MAX_BODIES) {
                return DataResult.error(() -> "Celestial snapshot body count is outside 1.." + CelestialCatalog.MAX_BODIES);
            }

            List<CelestialSnapshot.Entry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ResourceLocation bodyId = readId(buffer, "body id");
                Optional<ResourceLocation> parentId = buffer.readBoolean()
                        ? Optional.of(readId(buffer, "parent id"))
                        : Optional.empty();
                ResourceLocation levelId = readId(buffer, "level id");
                double gravity = buffer.readDouble();
                boolean vacuum = buffer.readBoolean();
                boolean breathable = buffer.readBoolean();
                ResourceLocation atmosphereProfile = readId(buffer, "atmosphere profile");
                ResourceLocation visualProfile = readId(buffer, "visual profile");
                if (!Double.isFinite(gravity)
                        || gravity < 0.0D
                        || gravity > CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER) {
                    return DataResult.error(() -> "Celestial snapshot gravity is out of bounds: " + bodyId);
                }
                if (vacuum && breathable) {
                    return DataResult.error(() -> "Celestial snapshot marks vacuum as breathable: " + bodyId);
                }
                entries.add(new CelestialSnapshot.Entry(
                        bodyId,
                        parentId,
                        levelId,
                        gravity,
                        vacuum,
                        breathable,
                        atmosphereProfile,
                        visualProfile
                ));
            }
            if (buffer.isReadable()) {
                return DataResult.error(() -> "Celestial snapshot has trailing bytes");
            }
            String graphError = validateGraph(entries);
            if (graphError != null) {
                return DataResult.error(() -> graphError);
            }
            return DataResult.success(new CelestialSnapshot(SCHEMA_VERSION, entries));
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return DataResult.error(() -> "Malformed celestial snapshot: " + detail);
        } finally {
            backing.release();
        }
    }

    private static String validateGraph(List<CelestialSnapshot.Entry> entries) {
        Map<ResourceLocation, CelestialSnapshot.Entry> byId = new HashMap<>();
        for (CelestialSnapshot.Entry entry : entries) {
            if (byId.putIfAbsent(entry.bodyId(), entry) != null) {
                return "Duplicate celestial snapshot body: " + entry.bodyId();
            }
        }
        for (CelestialSnapshot.Entry entry : entries) {
            if (entry.parentId().isPresent() && !byId.containsKey(entry.parentId().orElseThrow())) {
                return "Missing celestial snapshot parent for " + entry.bodyId();
            }
            Set<ResourceLocation> path = new HashSet<>();
            ResourceLocation cursor = entry.bodyId();
            while (cursor != null) {
                if (!path.add(cursor)) {
                    return "Celestial snapshot parent cycle at " + cursor;
                }
                CelestialSnapshot.Entry current = byId.get(cursor);
                cursor = current == null ? null : current.parentId().orElse(null);
            }
        }
        return null;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer, String field) {
        String raw = buffer.readUtf(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw);
        }
        return id;
    }
}
