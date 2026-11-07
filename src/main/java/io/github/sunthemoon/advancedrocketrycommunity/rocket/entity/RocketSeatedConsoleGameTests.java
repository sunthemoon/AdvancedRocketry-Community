package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W311 (finding 7-2): a rider on a high seat reopens the flight console from that seat. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSeatedConsoleGameTests {
    /** W278 round 7: the seat was the top block, 9 above an origin at y=151, which is 7 blocks into its section. */
    static final RocketPosition TOP_SEAT = new RocketPosition(0, 9, 0);
    static final int REPORTED_SECTION_OFFSET = 7;
    /** Yaw and pitch: the four horizontal directions, straight up and straight down. */
    static final float[][] VIEWS = {{0, 0}, {90, 0}, {180, 0}, {270, 0}, {0, -90}, {0, 90}};

    private RocketSeatedConsoleGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_seated_console")
    public static void topSeatRiderReopensTheClosedConsoleFromItsSeat(GameTestHelper helper) {
        var level = helper.getLevel();
        var at = sectionAligned(helper, REPORTED_SECTION_OFFSET);
        var rider = FirstEventGameTestPlayers.join(level.getServer(), UUID.randomUUID(), "SeatConsole", level, at);
        var bystander = FirstEventGameTestPlayers.join(level.getServer(), UUID.randomUUID(), "SeatBystander", level, at);
        var rocket = rocket(helper, at, rider.getUUID());
        try {
            seat(helper, rocket, rider);
            AABB box = rocket.getBoundingBox();
            helper.assertTrue(rider.getEyePosition().y - rocket.getY() > 6.0D, "Precondition: the seat is not high");
            helper.assertTrue(encloses(box, rider.getBoundingBox()), "The seated rider is outside the rocket box: " + box);
            helper.assertTrue(encloses(box, ModEntities.ROCKET.get().getDimensions().makeBoundingBox(rocket.position())),
                    "The box shrank below the type's fixed base box");
            for (var block : rocket.snapshot().orElseThrow().blocks()) {
                var p = block.position();
                helper.assertTrue(encloses(box, new AABB(rocket.getX() + p.x() - 0.5D, rocket.getY() + p.y(),
                        rocket.getZ() + p.z() - 0.5D, rocket.getX() + p.x() + 0.5D, rocket.getY() + p.y() + 1.0D,
                        rocket.getZ() + p.z() + 0.5D)), "A structure block is outside the rocket box: " + p);
            }
            for (float[] view : VIEWS) {
                look(rider, view);
                HitResult hit = pick(rider);
                helper.assertTrue(hit instanceof EntityHitResult entity && entity.getEntity() == rocket,
                        "Vanilla picking from the seat missed the rocket at yaw " + view[0] + ", pitch " + view[1] + ": " + hit);
            }
            // The exact reach rule of ServerGamePacketListenerImpl.handleInteract in Forge 47.
            helper.assertTrue(rider.canReachRaw(rocket, 3.0D), "The server's reach check refuses the seat");
            rider.closeContainer();
            helper.assertTrue(!(rider.containerMenu instanceof RocketFlightMenu), "Precondition: the console is still open");
            rider.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(rocket, false, InteractionHand.MAIN_HAND));
            helper.assertTrue(rider.containerMenu instanceof RocketFlightMenu menu && menu.rocketEntityId() == rocket.getId(),
                    "The interaction packet from the seat did not reopen the console");
            helper.assertTrue(rider.getVehicle() == rocket, "Reopening the console unseated the rider");

            // The larger box widens vanilla's reach only: an unmounted player beside the top keeps the console's own range.
            bystander.teleportTo(level, rocket.getX() + 1.5D, rocket.getY() + TOP_SEAT.y(), rocket.getZ(), 90.0F, 0.0F);
            helper.assertTrue(bystander.canReachRaw(rocket, 3.0D), "Precondition: the bystander cannot reach the box");
            bystander.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(rocket, false, InteractionHand.MAIN_HAND));
            helper.assertTrue(!(bystander.containerMenu instanceof RocketFlightMenu), "An unmounted player far above the base opened the console");
            helper.assertTrue(!rocket.isPushable() && !rocket.canBeCollidedWith() && rocket.ignoreExplosion(),
                    "The structure box made the rocket pushable, a collision surface or an explosion target");
            helper.succeed();
        } finally {
            rider.stopRiding(); rocket.discard();
            level.getServer().getPlayerList().remove(rider); level.getServer().getPlayerList().remove(bystander);
        }
    }

    /** The rocket origin {@code offset} blocks into its entity section, above the test's usual origin. */
    static BlockPos sectionAligned(GameTestHelper helper, int offset) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 2, 3));
        return new BlockPos(base.getX(), base.getY() + Math.floorMod(offset - base.getY(), 16), base.getZ());
    }

    /** A ten-block column whose top block is the only seat. */
    static RocketEntity rocket(GameTestHelper helper, BlockPos at, UUID owner) {
        var blocks = new ArrayList<RocketBlock>();
        for (int y = 0; y <= TOP_SEAT.y(); y++) {
            blocks.add(new RocketBlock(new RocketPosition(0, y, 0), RocketBlockStateAdapter.capture(y == TOP_SEAT.y()
                    ? ModBlocks.ROCKET_SEAT.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState())));
        }
        var anchors = List.of(TOP_SEAT);
        var origin = new RocketPosition(at.getX(), at.getY(), at.getZ());
        var dimension = helper.getLevel().dimension().location();
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), dimension, origin, blocks, anchors,
                new RocketStats(blocks.size(), 100, 1_000, 1_000, 1, anchors.size(), 1, 0), helper.getLevel().getGameTime());
        var logical = UUID.randomUUID();
        var flight = RocketFlightData.initial(logical, 1_000, anchors.size(), RocketDestination.EARTH.bodyId(), dimension,
                origin, helper.getLevel().getGameTime());
        var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(rocket != null, "Rocket entity type unavailable");
        rocket.initializeTransferred(snapshot, logical, owner, flight);
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Rocket was not published");
        return rocket;
    }

    /** Opens the console at the base and boards through the normal request, as a player does. */
    static void seat(GameTestHelper helper, RocketEntity rocket, ServerPlayer player) {
        RocketRuntime.openFlightMenu(player, rocket);
        helper.assertTrue(player.containerMenu instanceof RocketFlightMenu, "The console did not open at the base");
        RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD, RocketDestination.EARTH,
                UUID.randomUUID());
        helper.assertTrue(player.getVehicle() == rocket, "Normal boarding did not mount the rider");
        rocket.positionRider(player);
        Vec3 expected = rocket.position().add(TOP_SEAT.x(), TOP_SEAT.y() + 0.15D, TOP_SEAT.z());
        helper.assertTrue(player.position().distanceTo(expected) < 1.0E-9, "The rider is not at the top seat: " + player.position());
    }

    static void look(ServerPlayer player, float[] view) {
        player.setYRot(view[0]); player.setYHeadRot(view[0]); player.setXRot(view[1]);
    }

    /** Forge 47.4.10's client GameRenderer.pick, run against the same Level: block pick, then entity pick. */
    static HitResult pick(ServerPlayer player) {
        double pickRange = player.getBlockReach();
        double entityReach = player.getEntityReach();
        double reach = Math.max(pickRange, entityReach);
        HitResult hit = player.pick(reach, 1.0F, false);
        Vec3 eye = player.getEyePosition(1.0F);
        double limit = reach * reach;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            limit = hit.getLocation().distanceToSqr(eye);
            if (limit > pickRange * pickRange) {
                hit = BlockHitResult.miss(hit.getLocation(), Direction.getNearest(eye.x, eye.y, eye.z),
                        BlockPos.containing(hit.getLocation()));
            }
        }
        Vec3 view = player.getViewVector(1.0F);
        AABB search = player.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0D, 1.0D, 1.0D);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, eye.add(view.scale(reach)), search,
                candidate -> !candidate.isSpectator() && candidate.isPickable(), limit);
        if (entity != null) {
            double distance = eye.distanceToSqr(entity.getLocation());
            if (distance > limit || distance > entityReach * entityReach) {
                hit = BlockHitResult.miss(entity.getLocation(), Direction.getNearest(view.x, view.y, view.z),
                        BlockPos.containing(entity.getLocation()));
            } else if (distance < limit || hit == null) {
                hit = entity;
            }
        }
        return hit;
    }

    static boolean encloses(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }
}
