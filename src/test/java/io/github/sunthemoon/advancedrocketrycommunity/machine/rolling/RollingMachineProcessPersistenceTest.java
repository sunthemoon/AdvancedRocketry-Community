package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

class RollingMachineProcessPersistenceTest {
    private static final UUID MACHINE_ID = UUID.fromString("43cf3a74-68c4-4a67-bfe6-737b8580f9dc");
    private static final UUID TRANSACTION_ID = UUID.fromString("de6dc9be-ea17-4453-929e-e69b629fbbef");
    private static final String DEFINITION_ID = "advancedrocketrycommunity:rolling_iron_bars";
    private static final String SIGNATURE = "a".repeat(64);

    @Test
    void activeProcessAndReplayMarkerRoundTripExactly() {
        RollingMachineProcessPersistence.ProcessData data =
                new RollingMachineProcessPersistence.ProcessData(
                        ProcessMachineState.WAITING_ENERGY,
                        17,
                        Optional.of(new ProcessProgress(DEFINITION_ID, 45, 900)),
                        Optional.of(SIGNATURE),
                        Optional.of(TRANSACTION_ID),
                        new ProcessFailure(ProcessFailureCode.INSUFFICIENT_ENERGY, DEFINITION_ID)
                );
        CompoundTag parent = new CompoundTag();
        parent.put(
                RollingMachineProcessPersistence.ROOT,
                RollingMachineProcessPersistence.encode(data)
        );

        RollingMachineNbtResult<RollingMachineProcessPersistence.ProcessData> decoded =
                RollingMachineProcessPersistence.decode(parent);

        assertEquals(MultiblockNbtStatus.SUPPORTED, decoded.status());
        assertEquals(data, decoded.value().orElseThrow());
    }

    @Test
    void journalRoundTripsSortedCompleteSnapshotsForItsMachine() {
        ProcessTransactionJournal journal = journal();
        CompoundTag parent = new CompoundTag();
        parent.put(RollingMachineJournalPersistence.ROOT, RollingMachineJournalPersistence.encode(journal));

        RollingMachineNbtResult<ProcessTransactionJournal> decoded =
                RollingMachineJournalPersistence.decode(parent, MACHINE_ID);

        assertEquals(MultiblockNbtStatus.SUPPORTED, decoded.status());
        assertEquals(journal, decoded.value().orElseThrow());
        assertEquals(
                MultiblockNbtStatus.INVALID_DATA,
                RollingMachineJournalPersistence.decode(parent, UUID.randomUUID()).status()
        );
    }

    @Test
    void futureAndPrimitiveRootsArePreservedAndBlockedIndependently() {
        CompoundTag futureProcess = new CompoundTag();
        futureProcess.putInt("schema_version", RollingMachineProcessPersistence.SCHEMA_VERSION + 1);
        futureProcess.putString("marker", "future-process");
        CompoundTag parent = new CompoundTag();
        parent.put(RollingMachineProcessPersistence.ROOT, futureProcess);
        parent.put(RollingMachineJournalPersistence.ROOT, IntTag.valueOf(7));

        RollingMachineNbtResult<RollingMachineProcessPersistence.ProcessData> process =
                RollingMachineProcessPersistence.decode(parent);
        RollingMachineNbtResult<ProcessTransactionJournal> journal =
                RollingMachineJournalPersistence.decode(parent, MACHINE_ID);

        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, process.status());
        assertEquals(futureProcess, process.preservedRoot().orElseThrow());
        assertNotSame(futureProcess, process.preservedRoot().orElseThrow());
        assertEquals(MultiblockNbtStatus.INVALID_DATA, journal.status());
        assertEquals(IntTag.valueOf(7), journal.preservedRoot().orElseThrow());
    }

    @Test
    void malformedCurrentProcessAndJournalRootsFailClosed() {
        RollingMachineProcessPersistence.ProcessData empty =
                RollingMachineProcessPersistence.ProcessData.empty();
        CompoundTag invalidProcess = RollingMachineProcessPersistence.encode(empty);
        invalidProcess.putString("definition_id", DEFINITION_ID);
        CompoundTag parent = new CompoundTag();
        parent.put(RollingMachineProcessPersistence.ROOT, invalidProcess);
        assertEquals(
                MultiblockNbtStatus.INVALID_DATA,
                RollingMachineProcessPersistence.decode(parent).status()
        );

        CompoundTag invalidJournal = RollingMachineJournalPersistence.encode(journal());
        invalidJournal.getCompound("after").putLong("revision", 99);
        parent.put(RollingMachineJournalPersistence.ROOT, invalidJournal);
        assertEquals(
                MultiblockNbtStatus.INVALID_DATA,
                RollingMachineJournalPersistence.decode(parent, MACHINE_ID).status()
        );
    }

    @Test
    void absentIndependentRootsDecodeAsEmpty() {
        assertEquals(
                MultiblockNbtStatus.EMPTY,
                RollingMachineProcessPersistence.decode(new CompoundTag()).status()
        );
        assertEquals(
                MultiblockNbtStatus.EMPTY,
                RollingMachineJournalPersistence.decode(new CompoundTag(), MACHINE_ID).status()
        );
    }

    private static ProcessTransactionJournal journal() {
        ProcessResourceKey input = new ProcessResourceKey(
                ProcessResourceKind.ITEM,
                "item_input",
                "minecraft:iron_ingot"
        );
        ProcessResourceKey fluid = new ProcessResourceKey(
                ProcessResourceKind.FLUID,
                "fluid_input",
                "minecraft:water"
        );
        ProcessResourceKey output = new ProcessResourceKey(
                ProcessResourceKind.ITEM,
                "item_output",
                "minecraft:iron_bars"
        );
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(17, Map.of(
                input, new ProcessResourceBalance(2, 64),
                fluid, new ProcessResourceBalance(100, 4_000),
                output, new ProcessResourceBalance(0, 64)
        ));
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(18, Map.of(
                input, new ProcessResourceBalance(0, 64),
                fluid, new ProcessResourceBalance(0, 4_000),
                output, new ProcessResourceBalance(8, 64)
        ));
        return new ProcessTransactionJournal(
                ProcessTransactionJournal.SCHEMA_VERSION,
                TRANSACTION_ID,
                MACHINE_ID,
                DEFINITION_ID,
                before.revision(),
                before.fingerprint(),
                before,
                after,
                ProcessJournalPhase.APPLYING
        );
    }
}
