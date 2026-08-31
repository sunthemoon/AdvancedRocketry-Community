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
            if (adapter.supports(blockEntity)) {
                try {
                    return CaptureResult.supported(adapter.capture(blockEntity));
                } catch (RuntimeException exception) {
                    return CaptureResult.rejected(typeId(blockEntity) + ": " + safeMessage(exception));
                }
            }
        }
        return CaptureResult.rejected(typeId(blockEntity));
    }

    public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
        RocketBlockEntityAdapter adapter = byId.get(payload.adapterId());
        return adapter != null && adapter.restore(blockEntity, payload);
    }

    private static String typeId(BlockEntity blockEntity) {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return id == null ? "unregistered_block_entity" : id.toString();
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
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
