package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** At most one mutation per visit, with a bounded loaded-only power snapshot and no chunk requests. */
public final class ForceFieldService {
    static BlockEntity loadedEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
    static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
    private static boolean matches(ForceFieldProjectorBlockEntity source, FieldIdentity identity) {
        return !source.quarantined() && source.reservationIdentity().equals(identity)
                && source.getBlockState().getValue(ForceFieldProjectorBlock.FACING) == identity.direction();
    }
    static int work(MinecraftServer server, FieldCleanupSavedData data, FieldCleanupRoot.Entry entry) {
        ServerLevel level = server.getLevel(entry.identity().level());
        if (level == null) { return 0; }
        var cells = data.cells(level);
        work(level, data, entry, cells);
        return cells.inspectedAndMutatedCells();
    }
    private static void work(ServerLevel level, FieldCleanupSavedData data, FieldCleanupRoot.Entry entry, FieldCellView cells) {
        FieldIdentity identity = entry.identity();
        if (entry.orphan()) { cleanup(level, data, entry, cells); return; }
        if (data.view().full()) { return; }
        if (!cells.available(identity.source())) { return; }
        BlockEntity source = cells.getBlockEntity(identity.source());
        if (source == null && cells.getBlockState(identity.source()).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())) { return; }
        if (source instanceof ForceFieldNativeBlockEntity nativeSource && nativeSource.quarantined()) { return; }
        if (!(source instanceof ForceFieldProjectorBlockEntity projector) || !matches(projector, identity)) {
            data.retire(identity, entry.claims());
            return;
        }
        ProjectorState observedSource = projector.state();
        if (observedSource.owner().isEmpty()) { return; }
        if (!ForceFieldConfig.enabled()) { return; }
        var power = cells.powered(identity.source());
        if (power.isEmpty()) { projector.reportPowerFailure(cells.powerFailure()); return; }
        boolean powered = power.orElseThrow();
        int claims = entry.claims() | projector.state().claims();
        if (!powered && claims == 0) { return; }
        int offset = powered ? projector.next() : 32 - Integer.numberOfLeadingZeros(claims);
        BlockPos target = identity.target(offset);
        if (!cells.available(target)) { return; }
        BlockEntity field = cells.getBlockEntity(target);
        if (field instanceof ForceFieldBlockEntity existing && identity.equals(existing.identity())) {
            if (powered) {
                if (data.reserve(identity, claims | FieldIdentity.bit(offset))) {
                    projector.claims(claims | FieldIdentity.bit(offset));
                    projector.advance();
                }
            } else if (remove(level, existing)) {
                cells.mutated();
                release(data, entry, projector, offset);
            }
            return;
        }
        if (field instanceof ForceFieldNativeBlockEntity nativeField && nativeField.quarantined()) { return; }
        if (!powered) { release(data, entry, projector, offset); return; }
        // Empty AIR means the canonical air block, no fluid and no BlockEntity.
        if (field != null || !cells.getBlockState(target).is(Blocks.AIR) || !cells.getFluidState(target).isEmpty()) { return; }
        if (!ForceFieldProtection.placement(level, identity, target, cells.getBlockState(identity.source()))
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
                cells.mutated();
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
    private static void cleanup(ServerLevel level, FieldCleanupSavedData data, FieldCleanupRoot.Entry entry, FieldCellView cells) {
        if (entry.claims() == 0) { data.forget(entry.identity().key()); return; }
        // Rotate through all claims, so a cancelled nearest claim cannot starve another loaded claim.
        int offset = data.cleanupOffset(entry);
        BlockPos target = entry.identity().target(offset);
        if (!cells.available(target)) { return; }
        BlockEntity entity = cells.getBlockEntity(target);
        if (entity instanceof ForceFieldBlockEntity field) {
            if (field.quarantined() || field.identity() == null) { return; }
            if (entry.identity().equals(field.identity())) {
                cells.available(entry.identity().source());
                if (!remove(level, field)) { return; }
                cells.mutated();
            }
        } else if (cells.getBlockState(target).is(ModBlocks.FORCE_FIELD.get())) {
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
        return checkedValidation(server, data, observation).complete();
    }
    record Validation(boolean complete, int cells) { }
    static Validation checkedValidation(MinecraftServer server, FieldCleanupSavedData data, FieldCleanupSavedData.Observation observation) {
        ServerLevel level = server.getLevel(observation.identity().level());
        if (level == null) { return new Validation(true, 0); }
        var cells = data.cells(level);
        boolean complete = validate(level, data, observation, cells);
        return new Validation(complete, cells.inspectedAndMutatedCells());
    }
    private static boolean validate(ServerLevel level, FieldCleanupSavedData data,
            FieldCleanupSavedData.Observation observation, FieldCellView cells) {
        FieldIdentity identity = observation.identity();
        if (!cells.available(observation.target())) { return true; }
        BlockEntity observed = cells.getBlockEntity(observation.target());
        if (!(observed instanceof ForceFieldBlockEntity field) || !identity.equals(field.identity())) { return true; }
        int offset = identity.offset(observation.target());
        if (offset == 0 || !cells.available(identity.source())) { return false; }
        BlockEntity source = cells.getBlockEntity(identity.source());
        if (source == null && cells.getBlockState(identity.source()).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())) { return false; }
        if (source instanceof ForceFieldNativeBlockEntity nativeSource && nativeSource.quarantined()) { return false; }
        if (source instanceof ForceFieldProjectorBlockEntity projector && projector.state().owner().isPresent()
                && matches(projector, identity)) {
            if (!data.reserve(identity, projector.state().claims() | FieldIdentity.bit(offset))) { return false; }
            projector.claims(projector.state().claims() | FieldIdentity.bit(offset));
            return true;
        }
        var entry = data.view().get(identity.key());
        return data.retire(identity, (entry == null ? 0 : entry.claims()) | FieldIdentity.bit(offset));
    }
    private ForceFieldService() { }
}
