package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicChanceCheckpointTest {
    private static final ResourceLocation KIND = ResourceLocation.tryParse("advancedrocketrycommunity:centrifuge");
    private static final ResourceLocation LEVEL = ResourceLocation.tryParse("minecraft:overworld");
    private static final BlockPos POSITION = new BlockPos(10, 70, 8);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private ClassicCheckpoint checkpoint() {
        UUID machine = UUID.randomUUID(); var recipe = ResourceLocation.tryParse("test:chance");
        var job = new ClassicCheckpoint.Job(recipe, "a".repeat(64), 200, 10, new ProcessProgress(recipe.toString(), 199, 1_990))
                .start(8).choose(new ClassicCheckpoint.Outcome(UUID.randomUUID(), List.of(
                        new ClassicItemOutput(ResourceLocation.tryParse("minecraft:iron_nugget"), 1))));
        return new ClassicCheckpoint(MultiblockControllerState.initial(machine, new PatternTransform(PatternRotation.ZERO, false)),
                Set.of(), ClassicResources.empty(machine), Optional.of(job), 7, Optional.empty());
    }
    private CompoundTag outer(ClassicCheckpoint checkpoint) {
        var result = new CompoundTag(); result.put(ClassicCheckpointCodec.ROOT, ClassicCheckpointCodec.encode(checkpoint, KIND, LEVEL, POSITION));
        result.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(checkpoint.resources())); return result;
    }
    @Test void pendingOutcomeTransactionAndStartOrdinalSurviveRepeatedRoundTrips() {
        var checkpoint = checkpoint(); var encoded = outer(checkpoint);
        for (int i = 0; i < 5; i++) {
            var restored = ClassicCheckpointCodec.decode(encoded, KIND, POSITION);
            assertEquals(checkpoint.job(), restored.job()); assertEquals(7, restored.batchOrdinal());
            assertEquals(encoded, outer(restored)); encoded = outer(restored);
        }
        assertEquals(checkpoint.job().orElseThrow().outcome(), checkpoint.job().orElseThrow()
                .advance(new ProcessProgress("test:chance", 200, 2_000)).outcome());
    }
    @Test void chosenPayloadBoundsUnknownFieldsTransactionAndOrdinalAreStrict() {
        var saved = outer(checkpoint());
        for (int count : new int[]{0, -1, 65}) {
            var bad = saved.copy(); bad.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").getCompound("outcome")
                    .getList("outputs", 10).getCompound(0).putInt("count", count);
            assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(bad, KIND, POSITION));
        }
        var many = saved.copy(); var outputs = many.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").getCompound("outcome").getList("outputs", 10);
        for (int i = 0; i < 4; i++) { outputs.add(outputs.get(0).copy()); }
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(many, KIND, POSITION));
        var transaction = saved.copy(); transaction.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").getCompound("outcome").putString("transaction", "1-1-1-1-1");
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(transaction, KIND, POSITION));
        var extra = saved.copy(); extra.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").getCompound("outcome").putInt("unknown", 1);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(extra, KIND, POSITION));
        var ordinal = saved.copy(); ordinal.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").putLong("ordinal", 7);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(ordinal, KIND, POSITION));
    }
    @Test void priorSchemaOneJobWithoutOptionalFieldsStillReadsWithoutFabricatedChoices() {
        var saved = outer(checkpoint()); var job = saved.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job");
        job.remove("outcome"); job.remove("ordinal");
        var restored = ClassicCheckpointCodec.decode(saved, KIND, POSITION).job().orElseThrow();
        assertTrue(restored.outcome().isEmpty()); assertEquals(0, restored.ordinal()); assertEquals(199, restored.progress().progressTicks());
    }
}
