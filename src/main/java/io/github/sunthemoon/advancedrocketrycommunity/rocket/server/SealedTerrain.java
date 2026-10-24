package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedTerrainImage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** One immutable U footprint, bounded detached carrier preparation and exact loaded-position mutations. */
final class SealedTerrain implements AutoCloseable {
    private final ServerLevel level;
    private final RocketBlockEntityAdapters adapters;
    private final SealedCarrierPlacement placement;
    private final List<SealedRocketMember> members;
    private final Map<ChunkPos, List<SealedRocketMember>> chunks = new LinkedHashMap<>();
    private final List<SealedRocketBlockEntity> prepared = new ArrayList<>();
    private final List<CompoundTag> frames = new ArrayList<>();
    private boolean closed;

    SealedTerrain(ServerLevel level, RocketPersistedTransaction entry, RocketBlockEntityAdapters adapters, SealedCarrierPlacement placement) {
        this.level = level; this.adapters = adapters; this.placement = placement;
        var snapshot = entry.snapshot(); var all = new ArrayList<SealedRocketMember>();
        for (int index = 0; index < snapshot.blocks().size(); index++) {
            var block = snapshot.blocks().get(index); var position = snapshot.sourceOrigin().add(block.position());
            var member = new SealedRocketMember(entry.record().transactionId(), snapshot.snapshotId(),
                    entry.record().rocketEntityIdOptional().orElseThrow(), snapshot.contentHash(), snapshot.sourceDimension(),
                    position, index, block.state(), block.blockEntityPayload().orElse(null));
            all.add(member); chunks.computeIfAbsent(new ChunkPos(position.x() >> 4, position.z() >> 4), key -> new ArrayList<>()).add(member);
        }
        members = List.copyOf(all); chunks.replaceAll((chunk, values) -> List.copyOf(values));
    }
    List<SealedRocketMember> members() { return members; }
    List<ChunkPos> chunks() { return List.copyOf(chunks.keySet()); }
    boolean prepared() { return prepared.size() == members.size(); }

    /** Construction and capability serialization each spend shared work, including during restart recovery. */
    boolean prepareOne(SealedWorkBudget budget) {
        if (closed || prepared() || !budget.take() || !budget.take()) { return false; }
        var member = members.get(prepared.size()); var state = RocketBlockStateAdapter.restore(member.state()).orElse(null);
        if (state == null || state.isAir() || state.is(ModBlocks.SEALED_ROCKET_BLOCK.get())
                || !state.is(ModBlockTags.ROCKET_MOVABLE) && !state.is(ModBlockTags.ROCKET_PROPULSION) && !state.is(ModBlockTags.ROCKET_SATELLITE_BAYS)
                || state.is(ModBlockTags.ROCKET_FORBIDDEN) || state.hasBlockEntity() != (member.payload() != null)
                || member.payload() != null && !adapters.supportsPayload(member.payload())) {
            throw new IllegalArgumentException("Unavailable sealed member state or adapter");
        }
        var carrier = new SealedRocketBlockEntity(position(member.index()), ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState());
        try {
            carrier.initialize(SealedRocketRoot.stage(member));
            var frame = carrier.saveWithFullMetadata();
            if (!BoundedNbt.fits(frame, SealedRocketMember.MAX_BYTES, SealedRocketMember.MAX_DEPTH, SealedRocketMember.MAX_NODES)) {
                throw new IllegalArgumentException("Prepared carrier exceeds its complete native frame bound");
            }
            prepared.add(carrier); frames.add(frame);
        } catch (RuntimeException | Error failure) { carrier.setRemoved(); throw failure; }
        return true;
    }
    SealedDisassembly.Cell inspect(int index) {
        var position = position(index); var chunk = chunk(position);
        if (chunk == null) { return SealedDisassembly.Cell.UNLOADED; }
        var state = chunk.getBlockState(position); var be = chunk.getBlockEntity(position);
        if (state.isAir() && be == null) { return SealedDisassembly.Cell.EMPTY; }
        return state.is(ModBlocks.SEALED_ROCKET_BLOCK.get()) && be instanceof SealedRocketBlockEntity carrier
                && !carrier.isRemoved() && carrier.member().filter(members.get(index)::equals).isPresent()
                ? SealedDisassembly.Cell.OWNED : SealedDisassembly.Cell.OTHER;
    }
    boolean stage(int index) {
        if (closed || !prepared() || inspect(index) != SealedDisassembly.Cell.EMPTY) { return false; }
        var carrier = prepared.get(index);
        // A failed placement may already have installed this object; never republish a removed/previously bound instance.
        if (carrier.hasLevel() || carrier.isRemoved()) { return false; }
        return placement.place(level, carrier);
    }
    boolean erase(int index) {
        if (closed || inspect(index) != SealedDisassembly.Cell.OWNED) { return false; }
        return level.setBlock(position(index), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                && inspect(index) == SealedDisassembly.Cell.EMPTY;
    }
    java.util.function.Predicate<CompoundTag> validator(ChunkPos chunk, SealedDisassembly.Goal goal, boolean recovery,
            java.util.function.Consumer<String> diagnostic) {
        var group = chunks.get(chunk);
        if (group == null || !prepared()) { throw new IllegalStateException("Unprepared native terrain proof"); }
        var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState();
        var capturedFrames = group.stream().map(member -> frames.get(member.index())).toList();
        return root -> {
            boolean valid = goal == SealedDisassembly.Goal.PREPARE
                    ? SealedTerrainImage.project(root, group, state, capturedFrames, recovery).isPresent()
                    : SealedTerrainImage.matches(root, group, state, goal != SealedDisassembly.Goal.ROLLBACK);
            diagnostic.accept("terrain,valid=" + valid);
            return valid;
        };
    }
    private BlockPos position(int index) {
        var p = members.get(index).position(); return new BlockPos(p.x(), p.y(), p.z());
    }
    private LevelChunk chunk(BlockPos position) {
        return !level.isOutsideBuildHeight(position) && level.getWorldBorder().isWithinBounds(position)
                ? level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) : null;
    }
    @Override public void close() {
        closed = true;
        for (var carrier : prepared) { if (!carrier.hasLevel()) { carrier.setRemoved(); } }
        prepared.clear(); frames.clear();
    }
}
