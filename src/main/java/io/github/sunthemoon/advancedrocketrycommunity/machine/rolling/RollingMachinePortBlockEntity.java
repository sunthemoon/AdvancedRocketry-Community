package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingVerifier;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtLoadResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Typed part adapter that persists only its generation-aware controller binding for now. */
public final class RollingMachinePortBlockEntity extends BlockEntity
        implements MultiblockPartBindingTarget {
    private Optional<MultiblockPartBinding> binding = Optional.empty();
    private MultiblockNbtStatus persistenceStatus = MultiblockNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedBindingRoot;

    public RollingMachinePortBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ROLLING_MACHINE_PORT.get(), position, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RollingMachineRuntime.markDirty(serverLevel, worldPosition);
        }
    }

    public RollingMachinePortType portType() {
        if (getBlockState().getBlock() instanceof RollingMachinePortBlock port) {
            return port.portType();
        }
        throw new IllegalStateException("rolling port BlockEntity has an incompatible block state");
    }

    boolean acceptsBindingMutations() {
        return persistenceStatus == MultiblockNbtStatus.SUPPORTED;
    }

    @Override
    public Optional<MultiblockPartBinding> multiblockBinding() {
        return binding;
    }

    @Override
    public void setMultiblockBinding(Optional<MultiblockPartBinding> replacement) {
        if (!acceptsBindingMutations()) {
            throw new IllegalStateException("rolling port binding data is blocked");
        }
        Optional<MultiblockPartBinding> checked = java.util.Objects.requireNonNull(
                replacement,
                "replacement"
        );
        if (!binding.equals(checked)) {
            binding = checked;
            setChanged();
        }
    }

    public Optional<PartBindingValidationStatus> bindingStatus() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return binding.map(value -> PartBindingVerifier.verify(
                value,
                serverLevel.dimension(),
                serverLevel::hasChunkAt,
                position -> loadedController(serverLevel, position)
        ));
    }

    String bindingStatusText() {
        if (!acceptsBindingMutations()) {
            return persistenceStatus.name().toLowerCase(Locale.ROOT);
        }
        return bindingStatus()
                .map(status -> status.name().toLowerCase(Locale.ROOT))
                .orElse("unbound");
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (preservedBindingRoot != null) {
            parent.put(MultiblockPartBindingNbtCodec.ROOT, preservedBindingRoot.copy());
        } else if (binding.isPresent()) {
            parent.put(
                    MultiblockPartBindingNbtCodec.ROOT,
                    MultiblockPartBindingNbtCodec.encode(binding.orElseThrow())
            );
        } else {
            parent.remove(MultiblockPartBindingNbtCodec.ROOT);
        }
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        binding = Optional.empty();
        preservedBindingRoot = null;
        MultiblockNbtLoadResult<MultiblockPartBinding> decoded =
                MultiblockPartBindingNbtCodec.decode(parent);
        persistenceStatus = decoded.status();
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            binding = decoded.value();
        } else if (decoded.status() == MultiblockNbtStatus.EMPTY) {
            persistenceStatus = MultiblockNbtStatus.SUPPORTED;
        } else {
            preservedBindingRoot = decoded.preservedRoot().orElseThrow();
        }
    }

    private static Optional<ControllerBindingView> loadedController(
            ServerLevel level,
            BlockPos position
    ) {
        if (!level.hasChunkAt(position)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof RollingMachineBlockEntity controller
                ? Optional.of(controller.bindingView())
                : Optional.empty();
    }
}
