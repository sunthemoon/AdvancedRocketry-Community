package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One owner-thread C-to-N publication, using the native onRemove boundary proved by R95/R97. */
final class SealedMaterializer {
    private final Thread owner = Thread.currentThread();
    private ServerLevel publishingLevel;
    private SealedRocketBlockEntity publishingCarrier;
    private BlockState publishingState;
    private SealedTargetPreparation publishingTarget;
    private boolean published;

    boolean unseal(ServerLevel level, SealedRocketBlockEntity carrier, RocketBlockEntityAdapters adapters,
            BooleanSupplier authorized, SealedWorkBudget budget) {
        owner();
        if (publishingLevel != null || !budget.take() || !authorized.getAsBoolean()) { return false; }
        var member = carrier.member().orElse(null);
        if (member == null || !current(level, carrier, member)) { return false; }
        var state = RocketBlockStateAdapter.restore(member.state()).orElse(null);
        if (state == null) { return false; }
        // Position plus publication; payload hosts additionally pay construction and all three external callbacks.
        int additional = member.payload() == null ? 1 : 4;
        for (int i = 0; i < additional; i++) { if (!budget.take()) { return false; } }
        var candidate = SealedTargetPreparation.prepare(owner, carrier.getBlockPos(), state, member.payload(), adapters);
        if (candidate.isEmpty()) { return false; }
        try (var prepared = candidate.orElseThrow()) {
            // Restore/recapture may run provider code: recheck both exact C identity and checked authority afterwards.
            if (!current(level, carrier, member) || !authorized.getAsBoolean()) { return false; }
            publishingLevel = level; publishingCarrier = carrier; publishingState = state;
            publishingTarget = prepared; published = false;
            try {
                level.setBlock(carrier.getBlockPos(), state, Block.UPDATE_ALL);
                return published;
            } finally {
                publishingLevel = null; publishingCarrier = null; publishingState = null; publishingTarget = null;
            }
        }
    }

    /** Native state is already N; replace the map entry before N.onPlace, registration and onLoad. */
    boolean onRemove(ServerLevel level, BlockPos pos, BlockState next) {
        owner();
        var target = publishingTarget;
        if (publishingLevel != level || target == null || !publishingCarrier.getBlockPos().equals(pos)
                || !next.equals(publishingState)) { return false; }
        publishingTarget = null; // No reentrant second publication.
        target.published(); published = true;
        if (target.target() == null) { level.removeBlockEntity(pos); }
        else { level.setBlockEntity(target.target()); target.target().setChanged(); }
        return true;
    }

    private static boolean current(ServerLevel level, SealedRocketBlockEntity carrier, SealedRocketMember member) {
        if (!level.getServer().isSameThread() || level.noSave() || carrier.getLevel() != level || carrier.isRemoved()
                || carrier.member().orElse(null) != member || !carrier.available(level)) { return false; }
        var pos = carrier.getBlockPos(); var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(pos) == carrier && chunk.getBlockState(pos).equals(carrier.getBlockState());
    }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed publication requires its owner thread"); }
    }
}
