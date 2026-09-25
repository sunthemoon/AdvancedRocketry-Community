package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStateData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Rolling adapter for the shared independent process-state schema. */
final class RollingMachineProcessPersistence {
    static final String ROOT = ProcessStatePersistence.ROOT;
    static final int SCHEMA_VERSION = ProcessStatePersistence.SCHEMA_VERSION;
    static final int MAX_ROOT_BYTES = ProcessStatePersistence.MAX_ROOT_BYTES;

    private RollingMachineProcessPersistence() {
    }

    static CompoundTag encode(ProcessData data) {
        return ProcessStatePersistence.encode(data.shared());
    }

    static RollingMachineNbtResult<ProcessData> decode(CompoundTag parent) {
        ProcessNbtResult<ProcessStateData> decoded = ProcessStatePersistence.decode(parent);
        return switch (decoded.status()) {
            case EMPTY -> RollingMachineNbtResult.empty();
            case SUPPORTED -> RollingMachineNbtResult.supported(
                    ProcessData.fromShared(decoded.value().orElseThrow())
            );
            case UNSUPPORTED_SCHEMA -> RollingMachineNbtResult.rejected(
                    MultiblockNbtStatus.UNSUPPORTED_SCHEMA,
                    decoded.preservedRoot().orElseThrow()
            );
            case INVALID_DATA -> RollingMachineNbtResult.rejected(
                    MultiblockNbtStatus.INVALID_DATA,
                    decoded.preservedRoot().orElseThrow()
            );
        };
    }

    record ProcessData(
            ProcessMachineState state,
            long resourceRevision,
            Optional<ProcessProgress> progress,
            Optional<String> recipeSignature,
            Optional<UUID> lastAppliedTransactionId,
            ProcessFailure failure
    ) {
        ProcessData {
            new ProcessStateData(
                    state,
                    resourceRevision,
                    progress,
                    recipeSignature,
                    lastAppliedTransactionId,
                    failure
            );
        }

        static ProcessData empty() {
            return fromShared(ProcessStateData.empty());
        }

        private ProcessStateData shared() {
            return new ProcessStateData(
                    state,
                    resourceRevision,
                    progress,
                    recipeSignature,
                    lastAppliedTransactionId,
                    failure
            );
        }

        private static ProcessData fromShared(ProcessStateData data) {
            return new ProcessData(
                    data.state(),
                    data.resourceRevision(),
                    data.progress(),
                    data.recipeSignature(),
                    data.lastAppliedTransactionId(),
                    data.failure()
            );
        }
    }
}
