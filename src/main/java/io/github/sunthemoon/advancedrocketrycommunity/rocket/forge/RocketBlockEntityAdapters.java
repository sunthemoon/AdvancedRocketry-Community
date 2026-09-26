package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Immutable adapter allowlist. Missing types are rejected rather than serialized generically. */
public final class RocketBlockEntityAdapters {
    private final List<RocketBlockEntityAdapter> adapters;
    private final Map<ResourceLocation, RocketBlockEntityAdapter> byId;

    public RocketBlockEntityAdapters(Collection<? extends RocketBlockEntityAdapter> adapters) {
        this.adapters = List.copyOf(Objects.requireNonNull(adapters, "adapters"));
        HashMap<ResourceLocation, RocketBlockEntityAdapter> indexed = new HashMap<>();
        for (RocketBlockEntityAdapter adapter : this.adapters) {
            if (indexed.put(adapter.id(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate rocket BlockEntity adapter " + adapter.id());
            }
        }
        byId = Map.copyOf(indexed);
    }

    public static RocketBlockEntityAdapters defaults() {
        return new RocketBlockEntityAdapters(List.of(new VanillaContainerRocketAdapter()));
    }

    public CaptureResult capture(BlockEntity blockEntity) {
        for (RocketBlockEntityAdapter adapter : adapters) {
            try {
                if (adapter.supports(blockEntity)) {
                    RocketBlockEntityPayload payload = Objects.requireNonNull(adapter.capture(blockEntity));
                    if (!adapter.id().equals(payload.adapterId())) {
                        return CaptureResult.rejected(typeId(blockEntity) + ": adapter identity mismatch");
                    }
                    return CaptureResult.supported(payload);
                }
            } catch (RuntimeException exception) {
                return CaptureResult.rejected(typeId(blockEntity) + ": " + safeMessage(exception));
            }
        }
        return CaptureResult.rejected(typeId(blockEntity));
    }

    public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
        RocketBlockEntityAdapter adapter = byId.get(payload.adapterId());
        try {
            return adapter != null && adapter.canRestorePayload(payload) && adapter.supports(blockEntity)
                    && adapter.restore(blockEntity, payload)
                    && payload.equals(adapter.capture(blockEntity));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Checks availability/envelope version without calling external providers or touching a world. */
    public boolean supportsPayload(RocketBlockEntityPayload payload) {
        RocketBlockEntityAdapter adapter = byId.get(payload.adapterId());
        return adapter != null && adapter.canRestorePayload(payload);
    }

    private static String typeId(BlockEntity blockEntity) {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return id == null ? "unregistered_block_entity" : id.toString();
    }

    private static String safeMessage(RuntimeException exception) {
        // Callback messages may contain arbitrary data; keep rejection details bounded.
        return exception.getClass().getSimpleName();
    }

    public record CaptureResult(RocketBlockEntityPayload payload, String rejectionDetail) {
        public CaptureResult {
            if ((payload == null) == (rejectionDetail == null)) {
                throw new IllegalArgumentException("Capture result must be supported or rejected");
            }
        }

        static CaptureResult supported(RocketBlockEntityPayload payload) {
            return new CaptureResult(Objects.requireNonNull(payload, "payload"), null);
        }

        static CaptureResult rejected(String detail) {
            return new CaptureResult(null, Objects.requireNonNull(detail, "detail"));
        }

        public boolean supported() {
            return payload != null;
        }

        public Optional<RocketBlockEntityPayload> optionalPayload() {
            return Optional.ofNullable(payload);
        }

        public Optional<String> optionalRejectionDetail() {
            return Optional.ofNullable(rejectionDetail);
        }
    }
}
