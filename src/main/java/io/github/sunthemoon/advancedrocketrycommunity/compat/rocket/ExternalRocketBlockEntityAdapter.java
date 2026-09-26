package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Converts the public provider into the existing transaction boundary. */
final class ExternalRocketBlockEntityAdapter implements
        io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter {
    static final long CALLBACK_NANOS = 5_000_000L;
    private final ResourceLocation id;
    private final Set<ResourceLocation> types;
    private final int version;
    private final RocketBlockEntityAdapter provider;
    private final LongSupplier clock;
    private boolean warnedSlow;

    ExternalRocketBlockEntityAdapter(ResourceLocation id, Set<ResourceLocation> types,
                                    int version, RocketBlockEntityAdapter provider) {
        this(id, types, version, provider, System::nanoTime);
    }

    ExternalRocketBlockEntityAdapter(ResourceLocation id, Set<ResourceLocation> types,
                                    int version, RocketBlockEntityAdapter provider, LongSupplier clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.types = Set.copyOf(types);
        this.version = version;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public boolean supports(BlockEntity blockEntity) {
        ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return type != null && types.contains(type);
    }

    @Override
    public RocketBlockEntityPayload capture(BlockEntity blockEntity) {
        requireServer(blockEntity);
        if (!supports(blockEntity) || !call("canMove", () -> provider.canMove(blockEntity))) {
            throw new IllegalArgumentException("Provider rejected source container");
        }
        return RocketAdapterPayloads.encode(id, version,
                call("capture", () -> provider.capture(blockEntity)));
    }

    @Override
    public boolean canRestorePayload(RocketBlockEntityPayload payload) {
        return id.equals(payload.adapterId()) && RocketAdapterPayloads.decode(payload, version).isPresent();
    }

    @Override
    public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
        requireServer(blockEntity);
        if (!supports(blockEntity) || !id.equals(payload.adapterId())) {
            return false;
        }
        return RocketAdapterPayloads.decode(payload, version)
                .map(body -> call("restore", () -> provider.restore(blockEntity, body))).orElse(false);
    }

    <T> T call(String phase, Supplier<T> callback) {
        long started = clock.getAsLong();
        T result = callback.get();
        if (clock.getAsLong() - started > CALLBACK_NANOS) {
            if (!warnedSlow) {
                warnedSlow = true;
                LogUtils.getLogger().warn("Rocket adapter {} exceeded returned-time budget in {}", id, phase);
            }
            throw new IllegalStateException("Provider exceeded callback time budget");
        }
        return result;
    }

    private static void requireServer(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !level.hasChunkAt(blockEntity.getBlockPos())) {
            throw new IllegalStateException("Provider callbacks require a loaded logical-server target");
        }
    }
}
