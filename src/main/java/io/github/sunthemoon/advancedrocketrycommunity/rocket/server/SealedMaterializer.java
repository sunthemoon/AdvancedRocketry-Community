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
    enum Result { PUBLISHED, HELD, PREPARATION_FAILED, BUDGET }
    private final Thread owner = Thread.currentThread();
    private ServerLevel publishingLevel;
    private SealedRocketBlockEntity publishingCarrier;
    private BlockState publishingState;
    private SealedTargetPreparation publishingTarget;
    private boolean published;
    private final java.util.function.Consumer<String> nativeObserver;

    SealedMaterializer(java.util.function.Consumer<String> nativeObserver) { this.nativeObserver = nativeObserver; }

    Result unseal(ServerLevel level, SealedRocketBlockEntity carrier, RocketBlockEntityAdapters adapters,
            BooleanSupplier authorized, SealedWorkBudget budget) {
        owner();
        if (publishingLevel != null || !authorized.getAsBoolean()) { return Result.HELD; }
        if (!budget.take()) { return Result.BUDGET; }
        var member = carrier.member().orElse(null);
        if (member == null || !current(level, carrier, member)) { return Result.HELD; }
        var state = RocketBlockStateAdapter.restore(member.state()).orElse(null);
        if (state == null) { return Result.HELD; }
        // The position/publication step plus construction and all three external callbacks for a payload host.
        int additional = member.payload() == null ? 1 : 4;
        for (int i = 0; i < additional; i++) { if (!budget.take()) { return Result.BUDGET; } }
        nativeObserver.accept("UNSEAL_PREPARE_BEFORE");
        var candidate = SealedTargetPreparation.prepare(owner, carrier.getBlockPos(), state, member.payload(), adapters);
        if (candidate.isEmpty()) { nativeObserver.accept("UNSEAL_PREPARE_FAILED"); return Result.PREPARATION_FAILED; }
        try (var prepared = candidate.orElseThrow()) {
            nativeObserver.accept("UNSEAL_PREPARE_AFTER");
            // Restore/recapture may run provider code: recheck both exact C identity and checked authority afterwards.
            if (!current(level, carrier, member) || !authorized.getAsBoolean()) { return Result.HELD; }
            publishingLevel = level; publishingCarrier = carrier; publishingState = state;
            publishingTarget = prepared; published = false;
            try {
                nativeObserver.accept("UNSEAL_PUBLISH_BEFORE");
                level.setBlock(carrier.getBlockPos(), state, Block.UPDATE_ALL);
                return published ? Result.PUBLISHED : Result.PREPARATION_FAILED;
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
        nativeObserver.accept("UNSEAL_PUBLISH_AFTER");
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
