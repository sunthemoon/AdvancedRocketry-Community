package io.github.sunthemoon.advancedrocketrycommunity.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * ADR-073: a chunk read that a pending write serves returns a copy of the pending tag. Vanilla 1.20.1 returns the
 * tag itself, and the chunk loader then changes it while the IO thread may still be writing it.
 */
@Mixin(IOWorker.class)
public abstract class IOWorkerPendingReadMixin {
    // The pending branch of loadAsync: Optional.ofNullable(pendingStore.data). The disk branch is the second call.
    // javac hides synthetic lambdas from the annotation processor, so the descriptor lets it find the SRG name
    // (m_223494_) and "target" silences its lookup warning; require = 1 still fails a missing target at load.
    @SuppressWarnings("target")
    @ModifyArg(method = "lambda$loadAsync$4(Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Either;",
            slice = @Slice(from = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker$PendingStore;"
                            + "data:Lnet/minecraft/nbt/CompoundTag;")),
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Ljava/util/Optional;ofNullable(Ljava/lang/Object;)Ljava/util/Optional;"),
            require = 1, allow = 1)
    private Object arce$copyPendingData(Object data) {
        return data == null ? null : ((CompoundTag) data).copy();
    }
}
