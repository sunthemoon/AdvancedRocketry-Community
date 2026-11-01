package io.github.sunthemoon.advancedrocketrycommunity.client.elevator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Forge must select either endpoint renderer while any permitted capsule geometry is still in view. */
class ElevatorEndpointRenderBoundsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anchorBoxContainsTheCapsuleAtEveryYawAndKeepsTheTether() { check(true); }

    @Test void terminalBoxContainsTheCapsuleAtEveryYawAndKeepsTheTether() { check(false); }

    private static void check(boolean anchor) {
        for (BlockPos position : List.of(BlockPos.ZERO, new BlockPos(-2_410_007, 71, 1_234), new BlockPos(17, -40, -9))) {
            var endpoint = new Endpoint(position, anchor);
            var box = endpoint.getRenderBoundingBox();
            var tether = new AABB(position).expandTowards(0, anchor ? ElevatorRules.MAX_TETHER_BLOCKS
                    : -ElevatorRules.MAX_TETHER_BLOCKS, 0);
            assertEquals(box, box.minmax(tether), "the entire existing tether column remains visible");
            var model = CapsuleMesh.MODEL_BOUNDS;
            var origin = ElevatorEndpointBlockEntity.arrival(position).add(0, 1, 0);
            for (int yaw = 0; yaw < 360; yaw++) {
                double angle = Math.toRadians(yaw), cos = Math.cos(angle), sin = Math.sin(angle);
                for (double x : new double[] {model.minX(), model.maxX()}) {
                    for (double y : new double[] {model.minY(), model.maxY()}) {
                        for (double z : new double[] {model.minZ(), model.maxZ()}) {
                            assertTrue(box.contains(origin.x + cos * x + sin * z, origin.y + y,
                                    origin.z - sin * x + cos * z), "capsule corner at yaw " + yaw + " in " + box);
                        }
                    }
                }
            }
            assertEquals(new AABB(position.getX() - 3.5, tether.minY, position.getZ() - 3.5,
                    position.getX() + 4.5, Math.max(tether.maxY, position.getY() + 6), position.getZ() + 4.5), box,
                    "a fixed finite box, independent of the transient ride projection");
        }
    }

    /** Exercises the common implementation without needing Forge's deferred endpoint registrations. */
    private static final class Endpoint extends ElevatorEndpointBlockEntity {
        private final boolean anchor;

        Endpoint(BlockPos position, boolean anchor) {
            super(null, position, Blocks.STONE.defaultBlockState());
            this.anchor = anchor;
        }

        @Override public boolean anchor() { return anchor; }
        @Override public ResourceLocation kind() { return ModIdentity.id(anchor ? "elevator_anchor" : "elevator_terminal"); }
        @Override public EndgameCode placement() { return EndgameCode.OK; }
        @Override public Component getDisplayName() { return Component.literal("endpoint"); }
        @Override protected boolean placementReady(ServerLevel level, BlockState state, UUID id,
                EndgameDevices devices, long now) { return true; }
    }
}
