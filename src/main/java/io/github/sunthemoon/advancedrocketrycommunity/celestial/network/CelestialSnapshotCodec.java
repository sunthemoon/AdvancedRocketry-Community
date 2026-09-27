package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
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
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_PACKET_BYTES = 96 * 1_024;

    private CelestialSnapshotCodec() {
    }

    public static DataResult<byte[]> encode(CelestialCatalog catalog) {
        ByteBuf backing = Unpooled.buffer(256, MAX_PACKET_BYTES);
        try {
            FriendlyByteBuf buffer = new FriendlyByteBuf(backing);
            List<CelestialBodyDefinition> definitions = catalog.definitions();
            buffer.writeVarInt(definitions.size());
            for (CelestialBodyDefinition definition : definitions) {
                writeId(buffer, definition.id());
                buffer.writeBoolean(definition.parentId().isPresent());
                definition.parentId().ifPresent(parent -> writeId(buffer, parent));
                buffer.writeBoolean(definition.levelKey().isPresent());
                definition.levelKey().ifPresent(level -> writeId(buffer, level.location()));
                buffer.writeDouble(definition.gravityMultiplier());
                buffer.writeDouble(definition.atmosphere().pressure());
                buffer.writeBoolean(definition.atmosphere().breathable());
                buffer.writeDouble(definition.atmosphere().temperatureKelvin());
                writeId(buffer, definition.atmosphere().profile());
                writeId(buffer, definition.visualProfile());
                buffer.writeBoolean(definition.capabilities().landable());
                buffer.writeBoolean(definition.capabilities().orbitable());
                buffer.writeBoolean(definition.capabilities().gasGiant());
                buffer.writeDouble(definition.solarIntensity());
                buffer.writeDouble(definition.radiation());
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
                Optional<ResourceLocation> parentId = readFlag(buffer)
                        ? Optional.of(readId(buffer, "parent id"))
                        : Optional.empty();
                Optional<ResourceLocation> levelId = readFlag(buffer)
                        ? Optional.of(readId(buffer, "level id")) : Optional.empty();
                double gravity = buffer.readDouble();
                double pressure = buffer.readDouble();
                boolean breathable = readFlag(buffer);
                double temperature = buffer.readDouble();
                ResourceLocation atmosphereProfile = readId(buffer, "atmosphere profile");
                ResourceLocation visualProfile = readId(buffer, "visual profile");
                CelestialCapabilities capabilities = new CelestialCapabilities(
                        readFlag(buffer), readFlag(buffer), readFlag(buffer));
                double solar = buffer.readDouble();
                double radiation = buffer.readDouble();
                entries.add(new CelestialSnapshot.Entry(
                        bodyId,
                        parentId,
                        levelId,
                        gravity,
                        pressure,
                        breathable,
                        temperature,
                        atmosphereProfile,
                        visualProfile,
                        capabilities,
                        solar,
                        radiation
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

    private static boolean readFlag(FriendlyByteBuf buffer) {
        int value = buffer.readUnsignedByte();
        if (value > 1) {
            throw new IllegalArgumentException("Snapshot boolean must be zero or one");
        }
        return value == 1;
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer, String field) {
        String raw = buffer.readUtf(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw);
        }
        BoundedCelestialCodecs.requireId(id, field);
        return id;
    }
}
