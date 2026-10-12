package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResources;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import java.util.Optional;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** All controller-owned resources and progress are published through one immutable reference. */
record ClassicCheckpoint(MultiblockControllerState structure, Set<BlockPos> claimedParts, ClassicResources resources,
                         Optional<Job> job, long batchOrdinal, Optional<UUID> lastTransaction) {
    ClassicCheckpoint {
        if (!structure.machineInstanceId().equals(resources.machineId()) || structure.partPositions().size() > 64
                || claimedParts.size() > 64 || !claimedParts.containsAll(structure.partPositions())
                || batchOrdinal < 0) { throw new IllegalArgumentException("Controller checkpoint ownership/limits"); }
        var positions = new LinkedHashSet<BlockPos>();
        claimedParts.stream().map(BlockPos::immutable).sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).forEach(positions::add);
        claimedParts = Collections.unmodifiableSet(positions);
    }
    record Job(ResourceLocation recipe, String signature, int duration, int energyPerTick, ProcessProgress progress) {
        Job {
            if (recipe.toString().length() > 128 || !signature.matches("[0-9a-f]{64}")
                    || duration < 1 || duration > 72_000 || energyPerTick < 1 || energyPerTick > 10_000
                    || !recipe.toString().equals(progress.definitionId()) || progress.progressTicks() > duration
                    || progress.consumedEnergy() != (long) progress.progressTicks() * energyPerTick) {
                throw new IllegalArgumentException("Active job bounds");
            }
        }
        Job advance(ProcessProgress next) { return new Job(recipe, signature, duration, energyPerTick, next); }
    }
    ClassicCheckpoint structure(MultiblockControllerState value) {
        return new ClassicCheckpoint(value, claimedParts, resources, job, batchOrdinal, lastTransaction);
    }
    ClassicCheckpoint resources(ClassicResources value) {
        return new ClassicCheckpoint(structure, claimedParts, value, job, batchOrdinal, lastTransaction);
    }
    ClassicCheckpoint job(Optional<Job> value) {
        return new ClassicCheckpoint(structure, claimedParts, resources, value, batchOrdinal, lastTransaction);
    }
}
