package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Rolling adapter for the shared independent process-journal schema. */
final class RollingMachineJournalPersistence {
    static final String ROOT = ProcessJournalPersistence.ROOT;
    static final int MAX_ROOT_BYTES = ProcessJournalPersistence.MAX_ROOT_BYTES;

    private RollingMachineJournalPersistence() {
    }

    static CompoundTag encode(ProcessTransactionJournal journal) {
        return ProcessJournalPersistence.encode(journal);
    }

    static RollingMachineNbtResult<ProcessTransactionJournal> decode(
            CompoundTag parent,
            UUID expectedMachineId
    ) {
        Objects.requireNonNull(expectedMachineId, "expectedMachineId");
        ProcessNbtResult<ProcessTransactionJournal> decoded = ProcessJournalPersistence.decode(parent);
        return switch (decoded.status()) {
            case EMPTY -> RollingMachineNbtResult.empty();
            case SUPPORTED -> {
                ProcessTransactionJournal journal = decoded.value().orElseThrow();
                if (!journal.machineId().equals(expectedMachineId)) {
                    yield RollingMachineNbtResult.rejected(
                            MultiblockNbtStatus.INVALID_DATA,
                            parent.get(ProcessJournalPersistence.ROOT)
                    );
                }
                yield RollingMachineNbtResult.supported(journal);
            }
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
}
