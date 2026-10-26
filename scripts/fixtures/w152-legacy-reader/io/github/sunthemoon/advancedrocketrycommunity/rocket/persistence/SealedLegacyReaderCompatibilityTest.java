package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Run against the committed pre-discriminator implementation, not the current reader or a mock enum. */
class SealedLegacyReaderCompatibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void actualOldReaderBlocksAndPreservesAMixedSchemaTwoRoot() {
        assertEquals(List.of("ASSEMBLY", "DISASSEMBLY"),
                java.util.Arrays.stream(RocketTransactionType.values()).map(Enum::name).toList(),
                "This compatibility fixture must execute the actual old reader");
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 64, -2), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 42L);
        var data = new RocketTransactionSavedData(); var journal = data.journalFor(snapshot, UUID.randomUUID());
        for (int index = 0; index < 2; index++) {
            journal.write(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.DISASSEMBLY,
                    RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(),
                    RocketRegion.fromSnapshot(snapshot), 0, UUID.randomUUID()));
        }
        var root = data.save(new CompoundTag());
        assertEquals(2, root.getInt("schema_version"));
        assertTrue(RocketTransactionSavedData.load(root).operational(), "Legacy control root must remain readable");
        root.getList("transactions", Tag.TAG_COMPOUND).getCompound(1).putString("type", "SEALED_DISASSEMBLY");
        var frozen = root.copy(); var loaded = RocketTransactionSavedData.load(root);
        assertFalse(loaded.operational(), "Old reader must not adopt or drop only the unknown transaction");
        assertEquals(frozen, loaded.save(new CompoundTag()));
        assertEquals(frozen, loaded.save(new CompoundTag()), "Repeated saves must preserve both original records");
        assertEquals(frozen, root, "Decoding must not rewrite its input");
    }
}
