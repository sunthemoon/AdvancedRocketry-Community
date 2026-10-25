package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
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

class SealedTransactionDiscriminatorTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void mixedSchemaTwoRootKeepsLegacyAndSealedTypesWithoutMigration() {
        var encoded = mixedRoot();
        var loaded = RocketTransactionSavedData.load(encoded);
        assertTrue(loaded.operational(), "Schema-2 sealed records must decode without rewriting legacy records");
        assertEquals(2, loaded.entries().size());
        assertEquals(java.util.Set.of("DISASSEMBLY", "SEALED_DISASSEMBLY"), loaded.entries().stream()
                .map(entry -> entry.record().type().name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, loaded.save(new CompoundTag()).getInt("schema_version"));
        assertEquals(encoded, loaded.save(new CompoundTag()));
    }

    @Test void unknownFutureDiscriminatorStillPreservesAndBlocksTheWholeRoot() {
        var encoded = mixedRoot();
        encoded.getList("transactions", Tag.TAG_COMPOUND).getCompound(1).putString("type", "FUTURE_DISASSEMBLY");
        var loaded = RocketTransactionSavedData.load(encoded);
        assertFalse(loaded.operational());
        assertEquals(encoded, loaded.save(new CompoundTag()));
    }

    @Test void sealedElectionUsesTheExistingPhasesAndNeverChangesLegacyElection() {
        var sealed = RocketTransactionType.valueOf("SEALED_DISASSEMBLY");
        for (var phase : RocketTransactionPhase.values()) {
            assertEquals(RocketRecoveryDecision.Authority.BLOCKS, RocketRecoveryDecision.authority(sealed, phase, false));
            var expected = phase == RocketTransactionPhase.RESTORED || phase == RocketTransactionPhase.COMMITTED
                    ? RocketRecoveryDecision.Authority.BLOCKS : RocketRecoveryDecision.Authority.ENTITY;
            assertEquals(expected, RocketRecoveryDecision.authority(sealed, phase, true));
            assertEquals(expected, RocketRecoveryDecision.authority(RocketTransactionType.DISASSEMBLY, phase, true));
        }
    }

    @Test void legacyRecoveryCannotRestoreASealedRecordAsNativeTerrain() {
        assertTrue(RocketTransactionRecoveryService.handles(RocketTransactionType.ASSEMBLY));
        assertTrue(RocketTransactionRecoveryService.handles(RocketTransactionType.DISASSEMBLY));
        assertFalse(RocketTransactionRecoveryService.handles(RocketTransactionType.SEALED_DISASSEMBLY));
        assertFalse(RocketTransactionType.ASSEMBLY.isDisassembly());
        assertTrue(RocketTransactionType.DISASSEMBLY.isDisassembly());
        assertTrue(RocketTransactionType.SEALED_DISASSEMBLY.isDisassembly());
        assertThrows(IllegalArgumentException.class, () ->
                io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionCheckpoint.parse(
                        "SEALED_DISASSEMBLY:EXTRACTED:1"));
        assertEquals(RocketTransactionType.SEALED_DISASSEMBLY,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionCheckpoint.parse(
                        "SEALED_DISASSEMBLY:RESTORED:1").type());
    }

    private static CompoundTag mixedRoot() {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                new RocketPosition(8, 64, -2), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 42L);
        var data = new RocketTransactionSavedData(); var journal = data.journalFor(snapshot, UUID.randomUUID());
        for (int i = 0; i < 2; i++) {
            journal.write(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.DISASSEMBLY,
                    RocketTransactionPhase.LOCKED, snapshot.snapshotId(), snapshot.contentHash(),
                    RocketRegion.fromSnapshot(snapshot), 0, UUID.randomUUID()));
        }
        var root = data.save(new CompoundTag());
        root.getList("transactions", Tag.TAG_COMPOUND).getCompound(1).putString("type", "SEALED_DISASSEMBLY");
        return root;
    }
}
