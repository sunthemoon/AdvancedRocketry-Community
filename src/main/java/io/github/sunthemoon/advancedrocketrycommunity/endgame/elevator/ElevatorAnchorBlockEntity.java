package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ADR-059 section 2: the elevator anchor, an {@code elevator_anchor} endpoint whose controller tops a 5 × 2 × 5
 * structure-only multiblock on a body's surface Level. Its column {@code (x, z)} is the controller's; its platform is
 * the controller and the iron ring around it. The pattern is symmetric, so it has no facing.
 */
public final class ElevatorAnchorBlockEntity extends ElevatorEndpointBlockEntity {
    public static final ResourceLocation KIND = ModIdentity.id("elevator_anchor");
    public static final String PATTERN_ID = "advancedrocketrycommunity:elevator_anchor";

    private final EndgameStructure structure = new EndgameStructure(PATTERN_ID, ModBlocks.ELEVATOR_ANCHOR.get());

    public ElevatorAnchorBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ELEVATOR_ANCHOR.get(), position, state);
    }

    @Override
    public ResourceLocation kind() {
        return KIND;
    }

    @Override
    public boolean anchor() {
        return true;
    }

    @Override
    protected boolean placementReady(ServerLevel level, BlockState state, UUID id, EndgameDevices devices, long now) {
        structure.tick(level, worldPosition, Direction.NORTH, id, devices, now);
        return structure.known();
    }

    @Override
    public EndgameCode placement() {
        return structure.code();
    }

    @Override
    protected void unloadDevice() {
        EndgameRuntime.devices().ifPresent(structure::untrack);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.elevator_anchor");
    }
}
