package io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-owned, schema-versioned discovery and first-visit state. */
public final class CelestialSavedData extends SavedData {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String DATA_NAME = "advancedrocketrycommunity_celestial";

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final String BODIES_TAG = "bodies";
    private static final String ID_TAG = "id";
    private static final String DISCOVERED_AT_TAG = "discovered_at";
    private static final String FIRST_VISIT_AT_TAG = "first_visit_at";

    private final int schemaVersion;
    private final boolean writable;
    private final CompoundTag preservedFuturePayload;
    private final Map<ResourceLocation, BodyProgress> progress;

    private CelestialSavedData(
            int schemaVersion,
            boolean writable,
            CompoundTag preservedFuturePayload,
            Map<ResourceLocation, BodyProgress> progress
    ) {
        this.schemaVersion = schemaVersion;
        this.writable = writable;
        this.preservedFuturePayload = preservedFuturePayload;
        this.progress = progress;
    }

    public static CelestialSavedData create() {
        return new CelestialSavedData(
                CURRENT_SCHEMA_VERSION,
                true,
                null,
                new LinkedHashMap<>()
        );
    }

    public static CelestialSavedData load(CompoundTag source) {
        if (!source.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Celestial SavedData is missing schema_version");
        }
        int schemaVersion = source.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Celestial SavedData schema_version must be positive");
        }
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            return new CelestialSavedData(schemaVersion, false, source.copy(), new LinkedHashMap<>());
        }
        if (!source.contains(BODIES_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Celestial SavedData is missing bodies list");
        }

        ListTag bodies = (ListTag) source.get(BODIES_TAG);
        if (!bodies.isEmpty() && bodies.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Celestial SavedData bodies must contain compounds");
        }
        if (bodies.size() > CelestialCatalog.MAX_BODIES) {
            throw new IllegalArgumentException("Celestial SavedData exceeds " + CelestialCatalog.MAX_BODIES + " bodies");
        }
        Map<ResourceLocation, BodyProgress> loaded = new LinkedHashMap<>();
        for (int index = 0; index < bodies.size(); index++) {
            CompoundTag body = bodies.getCompound(index);
            BodyProgress entry = loadEntry(body, index);
            if (loaded.putIfAbsent(entry.bodyId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate SavedData body id: " + entry.bodyId());
            }
        }
        return new CelestialSavedData(schemaVersion, true, null, loaded);
    }

    public static CelestialSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CelestialSavedData::load,
                CelestialSavedData::create,
                DATA_NAME
        );
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean isWritableSchema() {
        return writable;
    }

    public List<BodyProgress> entries() {
        List<BodyProgress> sorted = new ArrayList<>(progress.values());
        sorted.sort((left, right) -> left.bodyId().compareTo(right.bodyId()));
        return Collections.unmodifiableList(sorted);
    }

    public Optional<BodyProgress> get(ResourceLocation bodyId) {
        return Optional.ofNullable(progress.get(bodyId));
    }

    public MutationResult discover(ResourceLocation bodyId, long gameTime) {
        validateMutationInput(bodyId, gameTime);
        if (!writable) {
            return MutationResult.UNSUPPORTED_SCHEMA;
        }
        if (progress.containsKey(bodyId)) {
            return MutationResult.UNCHANGED;
        }
        if (progress.size() >= CelestialCatalog.MAX_BODIES) {
            return MutationResult.CAPACITY_REACHED;
        }

        progress.put(bodyId, new BodyProgress(bodyId, gameTime, OptionalLong.empty()));
        setDirty();
        return MutationResult.CHANGED;
    }

    public MutationResult recordVisit(ResourceLocation bodyId, long gameTime) {
        validateMutationInput(bodyId, gameTime);
        if (!writable) {
            return MutationResult.UNSUPPORTED_SCHEMA;
        }

        BodyProgress existing = progress.get(bodyId);
        if (existing != null && existing.firstVisitAt().isPresent()) {
            return MutationResult.UNCHANGED;
        }
        if (existing != null && gameTime < existing.discoveredAt()) {
            throw new IllegalArgumentException("Visit time cannot precede discovery time");
        }
        if (existing == null && progress.size() >= CelestialCatalog.MAX_BODIES) {
            return MutationResult.CAPACITY_REACHED;
        }

        long discoveredAt = existing == null ? gameTime : existing.discoveredAt();
        progress.put(bodyId, new BodyProgress(bodyId, discoveredAt, OptionalLong.of(gameTime)));
        setDirty();
        return MutationResult.CHANGED;
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (!writable) {
            return preservedFuturePayload.copy();
        }

        target.putInt(SCHEMA_VERSION_TAG, CURRENT_SCHEMA_VERSION);
        ListTag bodies = new ListTag();
        for (BodyProgress entry : entries()) {
            CompoundTag body = new CompoundTag();
            body.putString(ID_TAG, entry.bodyId().toString());
            body.putLong(DISCOVERED_AT_TAG, entry.discoveredAt());
            entry.firstVisitAt().ifPresent(value -> body.putLong(FIRST_VISIT_AT_TAG, value));
            bodies.add(body);
        }
        target.put(BODIES_TAG, bodies);
        return target;
    }

    private static BodyProgress loadEntry(CompoundTag body, int index) {
        if (!body.contains(ID_TAG, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("SavedData body " + index + " is missing id");
        }
        String rawId = body.getString(ID_TAG);
        if (rawId.length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("SavedData body id exceeds the character limit");
        }
        ResourceLocation bodyId = ResourceLocation.tryParse(rawId);
        if (bodyId == null) {
            throw new IllegalArgumentException("Invalid SavedData body id: " + rawId);
        }
        if (!body.contains(DISCOVERED_AT_TAG, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("SavedData body " + bodyId + " is missing discovered_at");
        }
        long discoveredAt = body.getLong(DISCOVERED_AT_TAG);
        if (discoveredAt < 0L) {
            throw new IllegalArgumentException("SavedData discovered_at cannot be negative: " + bodyId);
        }

        OptionalLong firstVisitAt = OptionalLong.empty();
        if (body.contains(FIRST_VISIT_AT_TAG, Tag.TAG_LONG)) {
            long value = body.getLong(FIRST_VISIT_AT_TAG);
            if (value < discoveredAt) {
                throw new IllegalArgumentException("SavedData first_visit_at precedes discovery: " + bodyId);
            }
            firstVisitAt = OptionalLong.of(value);
        }
        return new BodyProgress(bodyId, discoveredAt, firstVisitAt);
    }

    private static void validateMutationInput(ResourceLocation bodyId, long gameTime) {
        if (bodyId == null) {
            throw new IllegalArgumentException("bodyId cannot be null");
        }
        if (bodyId.toString().length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("bodyId exceeds the character limit");
        }
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime cannot be negative");
        }
    }

    public record BodyProgress(
            ResourceLocation bodyId,
            long discoveredAt,
            OptionalLong firstVisitAt
    ) {
        public BodyProgress {
            Objects.requireNonNull(bodyId, "bodyId");
            Objects.requireNonNull(firstVisitAt, "firstVisitAt");
            if (discoveredAt < 0L) {
                throw new IllegalArgumentException("discoveredAt cannot be negative");
            }
            if (firstVisitAt.isPresent() && firstVisitAt.getAsLong() < discoveredAt) {
                throw new IllegalArgumentException("firstVisitAt cannot precede discoveredAt");
            }
        }
    }

    public enum MutationResult {
        CHANGED,
        UNCHANGED,
        UNSUPPORTED_SCHEMA,
        CAPACITY_REACHED
    }
}
