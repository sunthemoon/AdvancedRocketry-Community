package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** W179: the holder of one menu type in a {@link MenuAuthorityMatrix} GameTest. */
public abstract class MenuAuthorityFixture implements AutoCloseable {
    /** The Level that holds the holder. */
    public abstract ServerLevel level();

    /** A player the holder admits: its owner, or any player for a holder that has none. */
    public abstract UUID owner();

    /** Where a player stands within reach of the holder. */
    public abstract BlockPos stand();

    /** True once the holder can be opened; asked every tick until then. */
    public boolean ready() {
        return true;
    }

    /** Called once the owner has joined, before the checks, for example to claim the holder with a first use. */
    public void joined(ServerPlayer owner) {
    }

    /** Opens the holder for {@code player} through the server's own interaction path. */
    public abstract void open(ServerPlayer player);

    /** Leaves the holder as a chunk unload leaves it. The fixture is closed afterwards and not used again. */
    public abstract void unload();

    /** One rate-limited action of the open menu, as the server receives it from {@code player}. */
    public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
        throw new UnsupportedOperationException(describe() + " has no menu action");
    }

    public String describe() {
        return getClass().getSimpleName();
    }

    @Override
    public void close() {
    }

    /**
     * A holder that is a block, opened as a player's empty-handed use: {@code useItemOn} with an empty hand is the
     * server's step after vanilla's reach check, and for a spectator it opens the block's menu provider directly.
     * Unloading calls {@link BlockEntity#onChunkUnloaded} and {@link BlockEntity#setRemoved} on the holder's block
     * entity, as Forge's chunk unload does; the test area itself stays force-loaded.
     */
    public abstract static class OfBlock extends MenuAuthorityFixture {
        protected final ServerLevel level;
        protected final BlockPos holder;
        private final UUID owner;

        protected OfBlock(ServerLevel level, BlockPos holder, UUID owner) {
            this.level = level;
            this.holder = holder.immutable();
            this.owner = owner;
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public BlockPos stand() {
            return holder.north(2);
        }

        @Override
        public void open(ServerPlayer player) {
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(holder), Direction.UP, holder, false));
        }

        @Override
        public void unload() {
            BlockEntity entity = level.getChunkAt(holder).getBlockEntity(holder, LevelChunk.EntityCreationType.CHECK);
            if (entity == null) {
                throw new IllegalStateException("No block entity holds " + holder);
            }
            entity.onChunkUnloaded();
            entity.setRemoved();
        }

        /** Removes the holder block; subclasses remove the rest of their structure. */
        @Override
        public void close() {
            level.setBlock(holder, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        @Override
        public String describe() {
            return getClass().getSimpleName() + " at " + holder.toShortString() + " in " + level.dimension().location();
        }
    }
}
