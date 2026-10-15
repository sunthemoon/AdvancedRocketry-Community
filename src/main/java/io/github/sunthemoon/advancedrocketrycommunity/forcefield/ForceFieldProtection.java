package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.DeferredRoomBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ordered one-cell protection; permission callbacks run outside the narrowly scoped physical permit. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ForceFieldProtection {
    private static final ThreadLocal<Permit> PHYSICAL = new ThreadLocal<>();
    private record Permit(ServerLevel level, BlockPos pos) { }

    static boolean permitted(ServerLevel level, BlockPos pos) {
        Permit permit = PHYSICAL.get();
        return permit != null && permit.level() == level && permit.pos().equals(pos);
    }
    static void physical(ServerLevel level, BlockPos pos, Runnable change) {
        Permit previous = PHYSICAL.get();
        PHYSICAL.set(new Permit(level, pos.immutable()));
        try { change.run(); } finally { if (previous == null) { PHYSICAL.remove(); } else { PHYSICAL.set(previous); } }
    }
    static void restore(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity) {
        physical(level, pos, () -> {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            entity.clearRemoved();
            level.setBlockEntity(entity);
        });
    }
    static boolean region(ServerLevel level, UUID owner, BlockPos target) {
        if (owner == null || !level.getServer().isSameThread()) { return false; }
        EndgameSavedData data = EndgameSavedData.get(level.getServer());
        if (!data.operational()) { return false; }
        ForgeProtectionView view = new ForgeProtectionView(level, data.view().zones());
        if (!view.chunksFull(target, target) || !view.insideWorld(target, target)) { return false; }
        for (var zone : view.zones()) {
            if (zone.intersects(level.dimension().location(), target.getX(), target.getZ(), target.getX(), target.getZ())
                    && !zone.allows(owner)) { return false; }
        }
        return (!view.spaceLevel() || view.stationsAllow(owner, target, target))
                && view.spawnSquare().filter(square -> square.intersects(target, target)).isEmpty();
    }
    public static boolean safeEvent(BooleanSupplier event) {
        try { return !event.getAsBoolean(); }
        catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
        catch (Throwable failure) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_FORCE_FIELD_LISTENER_REFUSED: {}", failure.getClass().getSimpleName());
            return false;
        }
    }
    private static boolean api(ServerLevel level, UUID owner, BlockPos pos, EndgameEffect effect, Optional<UUID> actor) {
        return safeEvent(() -> MinecraftForge.EVENT_BUS.post(new EndgameEffectEvent(ModIdentity.id("force_field_projector"),
                effect, owner, actor, level.dimension(), pos, pos)));
    }
    /**
     * {@code against} is the projector's state from the visit's snapshot: the field grows out of it, and a world read
     * of another cell here would escape the visit's charged budget (W04F review N4).
     */
    static boolean placement(ServerLevel level, FieldIdentity identity, BlockPos pos, BlockState against) {
        if (!region(level, identity.owner(), pos) || !api(level, identity.owner(), pos, EndgameEffect.FIELD_PROJECTION,
                Optional.empty())) { return false; }
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return safeEvent(() -> MinecraftForge.EVENT_BUS.post(new PlacePreflight(snapshot,
                against, new OwnerActor(level, identity.owner()))))
                && region(level, identity.owner(), pos);
    }
    static boolean removal(ServerLevel level, UUID owner, BlockPos pos, BlockState state, Optional<UUID> actor) {
        return region(level, owner, pos) && api(level, owner, pos, EndgameEffect.BLOCK_BREAK, actor)
                && safeEvent(() -> MinecraftForge.EVENT_BUS.post(new BreakPreflight(level, pos, state,
                        new OwnerActor(level, owner)))) && region(level, owner, pos);
    }
    static boolean sourceReadable(ServerLevel level, FieldIdentity identity) {
        if (!identity.level().equals(level.dimension())) { return false; }
        if (!ForceFieldService.loaded(level, identity.source())) { return true; }
        var source = ForceFieldService.loadedEntity(level, identity.source());
        if (source instanceof ForceFieldNativeBlockEntity nativeSource && nativeSource.quarantined()) { return false; }
        return source != null || !level.getBlockState(identity.source()).is(
                io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.FORCE_FIELD_PROJECTOR.get());
    }
    static boolean admitRemoval(ServerLevel level, ForceFieldNativeBlockEntity entity, Optional<UUID> actor) {
        if (entity.quarantined()) { return false; }
        if (entity instanceof ForceFieldProjectorBlockEntity projector) {
            FieldCleanupSavedData cleanup = FieldCleanupSavedData.get(level.getServer());
            if (!cleanup.operational() || cleanup.view().full()) { return false; }
            UUID owner = projector.state().owner().orElse(actor.orElse(null));
            if (owner == null) { return false; }
            if (projector.state().owner().isPresent() && !cleanup.canRemove(projector.identity(), projector.state().claims())) {
                return false;
            }
            return removal(level, owner, entity.getBlockPos(), entity.getBlockState(), actor);
        }
        FieldIdentity identity = ((ForceFieldBlockEntity) entity).identity();
        return identity != null && sourceReadable(level, identity)
                && removal(level, identity.owner(), entity.getBlockPos(), entity.getBlockState(), actor);
    }
    static boolean commitRetirement(ServerLevel level, ForceFieldNativeBlockEntity entity) {
        if (entity instanceof ForceFieldProjectorBlockEntity projector) {
            return FieldCleanupSavedData.get(level.getServer()).retire(projector.reservationIdentity(), projector.state().claims());
        }
        return true;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !event.getPlacedBlock().is(io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.FORCE_FIELD_PROJECTOR.get())) { return; }
        if (!(ForceFieldService.loadedEntity(level, event.getPos()) instanceof ForceFieldProjectorBlockEntity projector)
                || !projector.hasReservation()) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event) {
        if (event instanceof BreakPreflight || !(event.getLevel() instanceof ServerLevel level)) { return; }
        var chunk = level.getChunkSource().getChunkNow(event.getPos().getX() >> 4, event.getPos().getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(event.getPos()) instanceof ForceFieldNativeBlockEntity entity) {
            Optional<UUID> actor = event.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                    && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                    ? Optional.of(player.getUUID()) : Optional.empty();
            if (!admitRemoval(level, entity, actor)) { event.setCanceled(true); }
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) { return; }
        event.getAffectedBlocks().removeIf(pos -> {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk != null && chunk.getBlockEntity(pos) instanceof ForceFieldNativeBlockEntity entity
                    && !admitRemoval(level, entity, Optional.empty());
        });
    }
    @SubscribeEvent public static void saving(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds physical bound");
        }
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.getCompound(i);
            boolean projector = entity.getString("id").equals(ModIdentity.id("force_field_projector").toString());
            boolean field = entity.getString("id").equals(ModIdentity.id("force_field").toString());
            if ((projector && entity.contains(ForceFieldCodecs.PROJECTOR_ROOT)
                    && !ForceFieldCodecs.projectorBounded(entity.get(ForceFieldCodecs.PROJECTOR_ROOT)))
                    || (field && entity.contains(ForceFieldCodecs.FIELD_ROOT)
                    && !ForceFieldCodecs.fieldBounded(entity.get(ForceFieldCodecs.FIELD_ROOT)))) {
                GuardedChunkSaves.refuse(event, "Refusing oversized force-field chunk save; back up and repair first");
            }
        }
    }
    /** UUID is attribution, not elevated authority, even when that UUID is on the server op list. */
    private static final class OwnerActor extends FakePlayer {
        OwnerActor(ServerLevel level, UUID owner) { super(level, new GameProfile(owner, "[ARCE Field]")); }
        @Override protected int getPermissionLevel() { return 0; }
        @Override public boolean hasPermissions(int level) { return level <= 0; }
    }
    private static final class PlacePreflight extends BlockEvent.EntityPlaceEvent implements DeferredRoomBoundary {
        PlacePreflight(BlockSnapshot snapshot, BlockState against, FakePlayer owner) { super(snapshot, against, owner); }
        @Override public BlockState getPlacedBlock() {
            return io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.FORCE_FIELD.get().defaultBlockState();
        }
        @Override public BlockState getState() { return getPlacedBlock(); }
    }
    private static final class BreakPreflight extends BlockEvent.BreakEvent implements DeferredRoomBoundary {
        BreakPreflight(ServerLevel level, BlockPos pos, BlockState state, FakePlayer owner) { super(level, pos, state, owner); }
    }
    private ForceFieldProtection() { }
}
