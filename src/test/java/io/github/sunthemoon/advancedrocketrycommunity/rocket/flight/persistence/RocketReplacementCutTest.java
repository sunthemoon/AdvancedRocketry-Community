package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketReplacementCutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    private static CompoundTag root(boolean successor) {
        var root = new CompoundTag(); var records = new ListTag(); var record = new CompoundTag();
        record.putString("phase", successor ? "PREPARED" : "COMMITTED");
        record.putUUID("transfer_id", new UUID(0, successor ? 2 : 1));
        record.putUUID("logical_rocket_id", new UUID(0, 3));
        record.putUUID(successor ? "source_entity_id" : "destination_entity_id", new UUID(0, 4));
        records.add(record); root.put("transfers", records); return root;
    }

    @Test void onlyAnOrdinarySameLogicalAndPhysicalHandoffSelectsTheNativeCut() {
        assertTrue(RocketLandedRemovalCut.replacement(root(true), root(false)));
        assertFalse(RocketLandedRemovalCut.replacement(root(false), root(false)));
        assertFalse(RocketLandedRemovalCut.replacement(new CompoundTag(), root(false)));
        assertFalse(RocketLandedRemovalCut.replacement(root(true), null));
        for (String key : new String[] {"logical_rocket_id", "source_entity_id", "transfer_id"}) {
            var candidate = root(true); candidate.getList("transfers", 10).getCompound(0).remove(key);
            assertFalse(RocketLandedRemovalCut.replacement(candidate, root(false)));
        }
        var governed = root(true); governed.getList("transfers", 10).getCompound(0).put("native_transfer", new CompoundTag());
        assertFalse(RocketLandedRemovalCut.replacement(governed, root(false)));
    }

    @Test void selectedUnknownKeepsTheMovedSuccessorUnacknowledgedUntilAnUninjectedRestart() {
        var type = ManagedSavedDataType.ROCKET_TRANSFERS; var file = directory.resolve(type.fileName());
        var checkpoint = new CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, 64);
        var policy = RocketLandedRemovalCut.policy(true, () -> fail("UNKNOWN ran a success pause"), RocketLandedRemovalCut::replacement);
        assertEquals(CheckedJournalCheckpoint.Outcome.COMMITTED, checkpoint.write(file, policy, 1, root(false), Map.of()));
        assertEquals(CheckedJournalCheckpoint.Outcome.UNKNOWN, checkpoint.write(file, policy, 2, root(true), Map.of()));
        assertEquals(root(true), CheckedSavedDataFile.readPayload(file, type).orElseThrow());
        assertEquals(CheckedJournalCheckpoint.Outcome.UNKNOWN, checkpoint.write(file, policy, 3, root(true), Map.of()));
        assertTrue(checkpoint.acknowledged().isEmpty());
        assertEquals(CheckedJournalCheckpoint.Outcome.COMMITTED,
                checkpoint.write(file, CheckedSavedDataPolicy.atomic(type), 4, root(true), Map.of()));
        assertTrue(checkpoint.current(4));
    }
}
