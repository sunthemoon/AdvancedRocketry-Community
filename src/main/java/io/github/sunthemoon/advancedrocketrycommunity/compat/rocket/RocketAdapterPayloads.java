package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Internal external-provider envelope; legacy vanilla data never passes through it. */
public final class RocketAdapterPayloads {
    private static final Set<String> KEYS = Set.of("payload_version", "data");

    private RocketAdapterPayloads() {
    }

    public static RocketBlockEntityPayload encode(ResourceLocation id, int version, CompoundTag body) {
        requireVersion(version);
        Objects.requireNonNull(body, "body");
        if (!validBody(body)) {
            throw new IllegalArgumentException("Provider data contains world identity fields");
        }
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("payload_version", version);
        envelope.put("data", body.copy());
        return new RocketBlockEntityPayload(id, envelope);
    }

    public static Optional<CompoundTag> decode(RocketBlockEntityPayload payload, int expectedVersion) {
        requireVersion(expectedVersion);
        CompoundTag envelope = Objects.requireNonNull(payload, "payload").data();
        if (!envelope.getAllKeys().equals(KEYS)
                || !envelope.contains("payload_version", Tag.TAG_INT)
                || envelope.getInt("payload_version") != expectedVersion
                || !envelope.contains("data", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag body = envelope.getCompound("data");
        return validBody(body) ? Optional.of(body.copy()) : Optional.empty();
    }

    private static boolean validBody(CompoundTag body) {
        return !body.contains("x") && !body.contains("y") && !body.contains("z") && !body.contains("id");
    }

    private static void requireVersion(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("Payload version must be positive");
        }
    }
}
