package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResources;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicCheckpointCodecTest {
    private static final ResourceLocation KIND = ResourceLocation.tryParse("advancedrocketrycommunity:lathe");
    private static final ResourceLocation LEVEL = ResourceLocation.tryParse("minecraft:overworld");
    private static final BlockPos POSITION = new BlockPos(3, 70, -19);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private ClassicCheckpoint checkpoint(Set<BlockPos> claims, boolean active) {
        UUID id = UUID.randomUUID(); var recipe = ResourceLocation.tryParse("test:rod");
        return new ClassicCheckpoint(MultiblockControllerState.initial(id, new PatternTransform(PatternRotation.ZERO, false)),
                claims, ClassicResources.empty(id), active ? Optional.of(new ClassicCheckpoint.Job(recipe, "a".repeat(64), 300, 20,
                new ProcessProgress(recipe.toString(), 42, 840))) : Optional.empty(), 0, Optional.empty());
    }
    private CompoundTag outer(ClassicCheckpoint checkpoint) {
        var root = new CompoundTag();
        root.put(ClassicCheckpointCodec.ROOT, ClassicCheckpointCodec.encode(checkpoint, KIND, LEVEL, POSITION));
        root.put(ClassicResourcesCodec.ROOT, ClassicResourcesCodec.encode(checkpoint.resources())); return root;
    }
    @Test void unformedClaimsAndActiveProgressRoundTripDeterministically() {
        var before = checkpoint(Set.of(POSITION.below(), POSITION.offset(3, 0, 0)), true);
        var saved = outer(before); var after = ClassicCheckpointCodec.decode(saved, KIND, POSITION);
        assertEquals(before.structure(), after.structure()); assertEquals(before.claimedParts(), after.claimedParts());
        assertEquals(before.job(), after.job()); assertEquals(saved, outer(after));
        assertThrows(UnsupportedOperationException.class, () -> after.claimedParts().clear());
    }
    @Test void sixtyFourClaimsAllowedAndSixtyFiveRefused() {
        var claims = IntStream.range(0, 64).mapToObj(x -> new BlockPos(x, 70, 0)).collect(Collectors.toSet());
        assertEquals(64, ClassicCheckpointCodec.decode(outer(checkpoint(claims, false)), KIND, POSITION).claimedParts().size());
        claims.add(new BlockPos(64, 70, 0)); assertThrows(IllegalArgumentException.class, () -> checkpoint(claims, false));
        var saved = outer(checkpoint(Set.of(POSITION), false)); var list = (ListTag) saved.getCompound(ClassicCheckpointCodec.ROOT).get("claims");
        for (int i = 0; i < 64; i++) { list.add(list.get(0).copy()); }
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(saved, KIND, POSITION));
    }
    @Test void duplicateClaimsWrongTypesAndUnknownFieldsRefused() {
        var original = outer(checkpoint(Set.of(POSITION), false));
        var duplicate = original.copy(); var list = (ListTag) duplicate.getCompound(ClassicCheckpointCodec.ROOT).get("claims");
        list.add(list.get(0).copy()); assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(duplicate, KIND, POSITION));
        var badType = original.copy(); var strings = new ListTag(); strings.add(StringTag.valueOf("claim"));
        badType.getCompound(ClassicCheckpointCodec.ROOT).put("claims", strings);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(badType, KIND, POSITION));
        var unknown = original.copy(); unknown.getCompound(ClassicCheckpointCodec.ROOT).putInt("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(unknown, KIND, POSITION));
        var wrongCoordinate = original.copy();
        ((ListTag) wrongCoordinate.getCompound(ClassicCheckpointCodec.ROOT).get("claims")).getCompound(0).putLong("x", 3);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(wrongCoordinate, KIND, POSITION));
    }
    @Test void formedPartsMustBeRepresentedByDurableClaims() {
        var before = checkpoint(Set.of(), false); var formed = before.structure().formed(1, Set.of(POSITION.below()));
        assertThrows(IllegalArgumentException.class, () -> before.structure(formed));
    }
    @Test void futureSchemaWrongAnchorOversizedRootAndCounterExhaustionRefused() {
        var original = outer(checkpoint(Set.of(), false));
        var future = original.copy(); future.getCompound(ClassicCheckpointCodec.ROOT).putInt("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(future, KIND, POSITION));
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(original, KIND, POSITION.above()));
        var large = original.copy(); large.getCompound(ClassicCheckpointCodec.ROOT).putString("extra", "x".repeat(65_536));
        assertFalse(ClassicCheckpointCodec.bounded(large.get(ClassicCheckpointCodec.ROOT)));
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(large, KIND, POSITION));
        var exhausted = original.copy(); exhausted.getCompound(ClassicCheckpointCodec.ROOT).putLong("batch_ordinal", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(exhausted, KIND, POSITION));
    }
    @Test void noncanonicalIdsAndUnaccountedProgressRefused() {
        var original = outer(checkpoint(Set.of(), true));
        var badLevel = original.copy(); badLevel.getCompound(ClassicCheckpointCodec.ROOT).putString("level", "overworld");
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(badLevel, KIND, POSITION));
        var badRecipe = original.copy(); badRecipe.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").putString("recipe", "rod");
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(badRecipe, KIND, POSITION));
        var badEnergy = original.copy(); badEnergy.getCompound(ClassicCheckpointCodec.ROOT).getCompound("job").putLong("energy", 841);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(badEnergy, KIND, POSITION));
        var badTransaction = original.copy(); badTransaction.getCompound(ClassicCheckpointCodec.ROOT).putString("last_transaction", "1-1-1-1-1");
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointCodec.decode(badTransaction, KIND, POSITION));
    }
}
