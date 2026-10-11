package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One bounded cell per invocation. No operation requests a chunk or scans a line synchronously. */
public final class ForceFieldService {
    static BlockEntity loadedEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
    static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    private static boolean matches(ForceFieldProjectorBlockEntity source, FieldIdentity identity) {
        return !source.quarantined() && source.state().owner().isPresent() && source.identity().equals(identity)
                && source.getBlockState().getValue(ForceFieldProjectorBlock.FACING) == identity.direction();
    }
    static void work(MinecraftServer server, FieldCleanupSavedData data, FieldCleanupRoot.Entry entry) {
        FieldIdentity identity = entry.identity();
        ServerLevel level = server.getLevel(identity.level());
        if (level == null) { return; }
        if (entry.orphan()) { cleanup(level, data, entry); return; }
        if (data.view().full()) { return; }
        if (!loaded(level, identity.source())) { return; }
        BlockEntity source = loadedEntity(level, identity.source());
        if (source == null && level.getBlockState(identity.source()).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())) { return; }
        if (source instanceof ForceFieldNativeBlockEntity nativeSource && nativeSource.quarantined()) { return; }
        if (!(source instanceof ForceFieldProjectorBlockEntity projector) || !matches(projector, identity)) {
            data.retire(identity, entry.claims());
            return;
        }
        ProjectorState observedSource = projector.state();
        if (!ForceFieldConfig.enabled()) { return; }
        // hasNeighborSignal must never read a neighbor in an unavailable chunk.
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = identity.source().relative(direction);
            if (!loaded(level, neighbor)) { return; }
        }
        boolean powered = level.hasNeighborSignal(identity.source());
        int claims = entry.claims() | projector.state().claims();
        if (!powered && claims == 0) { return; }
        int offset = powered ? projector.next() : 32 - Integer.numberOfLeadingZeros(claims);
        BlockPos target = identity.target(offset);
        if (!loaded(level, target)) { return; }
        BlockEntity field = loadedEntity(level, target);
        if (field instanceof ForceFieldBlockEntity existing && identity.equals(existing.identity())) {
            if (powered) {
                if (data.reserve(identity, claims | FieldIdentity.bit(offset))) {
                    projector.claims(claims | FieldIdentity.bit(offset));
                    projector.advance();
                }
            } else if (remove(level, existing)) {
                release(data, entry, projector, offset);
            }
            return;
        }
        if (field instanceof ForceFieldNativeBlockEntity nativeField && nativeField.quarantined()) { return; }
        if (!powered) { release(data, entry, projector, offset); return; }
        // Empty AIR means the canonical air block, no fluid and no BlockEntity.
        if (field != null || !level.getBlockState(target).is(Blocks.AIR) || !level.getFluidState(target).isEmpty()) { return; }
        if (!ForceFieldProtection.placement(level, identity, target)
                || !loaded(level, identity.source()) || loadedEntity(level, identity.source()) != projector
                || !matches(projector, identity) || !loaded(level, target) || loadedEntity(level, target) != null
                || !projector.state().equals(observedSource) || !entry.equals(data.view().get(identity.key()))
                || !level.getBlockState(target).is(Blocks.AIR) || !level.getFluidState(target).isEmpty()
                || !ForceFieldConfig.enabled()) { return; }
        int candidate = claims | FieldIdentity.bit(offset);
        if (!data.reserve(identity, candidate)) { return; }
        projector.claims(candidate);
        ForceFieldProtection.physical(level, target, () -> {
            if (level.setBlock(target, ModBlocks.FORCE_FIELD.get().defaultBlockState(), Block.UPDATE_ALL)
                    && level.getBlockEntity(target) instanceof ForceFieldBlockEntity created) {
                created.initialize(identity);
                AtmosphereRuntime.invalidateDoorBoundary(level, target, target);
                projector.advance();
            }
        });
    }
    private static void release(FieldCleanupSavedData data, FieldCleanupRoot.Entry entry,
            ForceFieldProjectorBlockEntity source, int offset) {
        var current = data.view().get(entry.identity().key());
        if (current == null) { return; }
        int mask = current.claims() & ~FieldIdentity.bit(offset);
        if (data.put(current.withClaims(mask)) && source != null) { source.claims(source.state().claims() & ~FieldIdentity.bit(offset)); }
        if (current.orphan() && mask == 0) { data.forget(entry.identity().key()); }
    }
    private static void cleanup(ServerLevel level, FieldCleanupSavedData data, FieldCleanupRoot.Entry entry) {
        if (entry.claims() == 0) { data.forget(entry.identity().key()); return; }
        // Rotate through all claims, so a cancelled nearest claim cannot starve another loaded claim.
        int offset = data.cleanupOffset(entry);
        BlockPos target = entry.identity().target(offset);
        if (!loaded(level, target)) { return; }
        BlockEntity entity = loadedEntity(level, target);
        if (entity instanceof ForceFieldBlockEntity field) {
            if (field.quarantined() || field.identity() == null) { return; }
            if (entry.identity().equals(field.identity()) && !remove(level, field)) { return; }
        } else if (level.getBlockState(target).is(ModBlocks.FORCE_FIELD.get())) {
            // A missing field BE is not proof that its unreadable claim was removed.
            return;
        }
        release(data, entry, null, offset);
    }
    private static boolean remove(ServerLevel level, ForceFieldBlockEntity field) {
        FieldIdentity identity = field.identity();
        BlockPos pos = field.getBlockPos();
        if (identity == null || identity.offset(pos) == 0 || !ForceFieldProtection.sourceReadable(level, identity)
                || !ForceFieldProtection.removal(level, identity.owner(), pos, field.getBlockState(), Optional.empty())
                || !loaded(level, pos) || loadedEntity(level, pos) != field || field.quarantined()
                || !identity.equals(field.identity()) || !ForceFieldProtection.sourceReadable(level, identity)) { return false; }
        ForceFieldProtection.physical(level, pos, () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        return loaded(level, pos) && level.getBlockState(pos).is(Blocks.AIR);
    }
    static boolean validate(MinecraftServer server, FieldCleanupSavedData data, FieldCleanupSavedData.Observation observation) {
        FieldIdentity identity = observation.identity();
        ServerLevel level = server.getLevel(identity.level());
        if (level == null || !loaded(level, observation.target())) { return true; }
        BlockEntity observed = loadedEntity(level, observation.target());
        if (!(observed instanceof ForceFieldBlockEntity field) || !identity.equals(field.identity())) { return true; }
        int offset = identity.offset(observation.target());
        if (offset == 0 || !loaded(level, identity.source())) { return false; }
        BlockEntity source = loadedEntity(level, identity.source());
        if (source == null && level.getBlockState(identity.source()).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())) { return false; }
        if (source instanceof ForceFieldNativeBlockEntity nativeSource && nativeSource.quarantined()) { return false; }
        if (source instanceof ForceFieldProjectorBlockEntity projector && matches(projector, identity)) {
            if (!data.reserve(identity, projector.state().claims() | FieldIdentity.bit(offset))) { return false; }
            projector.claims(projector.state().claims() | FieldIdentity.bit(offset));
            return true;
        }
        var entry = data.view().get(identity.key());
        return data.retire(identity, (entry == null ? 0 : entry.claims()) | FieldIdentity.bit(offset));
    }
    private ForceFieldService() { }
}
