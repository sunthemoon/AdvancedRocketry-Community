package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayPassengerExitGameTestFixture.Case.*;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Check that a negative landing/expiry test reaches its own stimulus with a live offer. */
final class BayPassengerExitGameTestDescentAudit {
    private BayPassengerExitGameTestDescentAudit() { }

    static Runnable evictOnDismount(BayPassengerExitGameTestFixture fixture) {
        if (fixture.scenario != CACHE_EVICTION) { return () -> { }; }
        var source = fixture.flight.source().blockPosition();
        var level = fixture.helper.getLevel();
        var registry = level.getChunkSource().getChunkNow(source.getX() >> 4, source.getZ() >> 4)
                .getListenerRegistry(net.minecraft.core.SectionPos.blockToSectionCoord(source.getY()));
        var listener = new GameEventListener() {
            @Override public PositionSource getListenerSource() { return new BlockPositionSource(source); }
            @Override public int getListenerRadius() { return 16; }
            @Override public boolean handleGameEvent(net.minecraft.server.level.ServerLevel world,
                    GameEvent event, GameEvent.Context context, Vec3 position) {
                if (fixture.closed || event != GameEvent.ENTITY_DISMOUNT || context.sourceEntity() != fixture.player) { return false; }
                fixture.injected = true;
                // The four-entry vanilla cache must not be relied on across game-event dispatch.
                for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) { continue; }
                    fixture.helper.assertTrue(world.getChunkSource().getChunkNow((source.getX() >> 4) + x,
                            (source.getZ() >> 4) + z) != null, "Fixture cache eviction encountered a missing column");
                } }
                return true;
            }
        };
        registry.register(listener);
        return () -> registry.unregister(listener);
    }

    static void enclose(BayPassengerExitGameTestFixture fixture) {
        if (!Set.of(ORDINARY, FIRST_TICK, DRIPSTONE, ZERO_LANDING, NON_FALL_LANDING, CANCELLED_FALL,
                REENTRANT_DAMAGE, EXPIRY, TELEPORT, EARLY_CONTINUOUS, SECOND_CORRECTION,
                EARLY_DISCONTINUITY, AMBIENT_FLUID, DESCENT, PACKET_LANDING).contains(fixture.scenario)) { return; }
        var level = fixture.helper.getLevel();
        if (fixture.scenario == AMBIENT_FLUID) {
            var source = fixture.origin.offset(4, 48, 0);
            for (var side : Direction.values()) {
                if (side != Direction.WEST) { level.setBlockAndUpdate(source.relative(side), Blocks.GLASS.defaultBlockState()); }
            }
            level.setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        }
        // Stay inside this fixture's horizontal footprint. Seal the collar before the warmup,
        // not during the descent: outside fluid must not replace the named landing stimulus.
        for (int x = -3; x <= 3; x++) { for (int z = -3; z <= 3; z++) {
            for (int y = -2; y <= 55; y++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3 || y == 55;
                level.setBlockAndUpdate(fixture.origin.offset(x, y, z),
                        (wall ? Blocks.GLASS : Blocks.AIR).defaultBlockState());
            }
        } }
    }

    static void beforeStimulus(BayPassengerExitGameTestFixture fixture) {
        if (fixture.offer == null || fixture.offer.descent == null) { return; }
        var descent = fixture.offer.descent;
        int tick = fixture.player.getServer().getTickCount();
        if (fixture.stage != 2 && fixture.stage != 6 && fixture.stage != 7
                && (fixture.stage != 4 || tick >= fixture.firstDeadline)) { return; }
        // Observe vanilla's tracker correction before the embedded client supplies another movement.
        fixture.exits.sample(fixture.player);
        acknowledge(fixture);
        var observed = fixture.exits.motion.generation(fixture.player);
        fixture.helper.assertTrue(fixture.exits.state.live.get(fixture.player) == fixture.offer && !descent.closed,
                "Offer ended before named stimulus: case=" + fixture.scenario + "; tick=" + tick
                        + "; deadline=" + fixture.firstDeadline + "; authority=" + fixture.offer.permit.authority()
                        + "; expected=" + descent.corrections.current + "; observed=" + observed
                        + "; last=" + descent.continuity.last + "; current="
                        + BayPassengerExitMotion.sample(fixture.player, tick)
                        + "; contact=" + fixture.offer.world.firstContact + "; usage=" + fixture.exits.budget.usage());
        fixture.helper.assertTrue(descent.deadline == fixture.firstDeadline
                        && fixture.exits.state.history(fixture.player.getUUID(), fixture.flight.record().transferId()).deadline() == fixture.firstDeadline,
                "Observation renewed the original deadline");
        if (fixture.stage == 4) {
            fixture.helper.assertTrue(fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled),
                    "Expiry case consumed protection before its deadline");
        }
        if (fixture.scenario == AMBIENT_FLUID) {
            fixture.helper.assertTrue(fixture.helper.getLevel().getBlockState(fixture.origin.offset(4, 48, 0)).is(Blocks.WATER),
                    "Ambient-fluid fixture lost its external water source");
        }
    }

    static void acknowledge(BayPassengerExitGameTestFixture fixture) {
        var current = fixture.exits.motion.generation(fixture.player);
        if (current != null && !fixture.offer.descent.closed && current.equals(fixture.offer.descent.corrections.current)
                && current.counter() != fixture.acknowledgedCorrection) {
            fixture.player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(current.counter()));
            fixture.acknowledgedCorrection = current.counter();
        }
    }

    static void correction(BayPassengerExitGameTestFixture fixture) {
        var player = fixture.player; var descent = fixture.offer.descent; var initial = descent.continuity.origin;
        double horizontal = fixture.scenario == EARLY_DISCONTINUITY ? 1.01 : .5;
        player.connection.teleport(player.getX() + horizontal, player.getY() - .5, player.getZ(), 25, 10,
                Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
        fixture.exits.sample(player);
        if (fixture.scenario == EARLY_DISCONTINUITY) { fixture.expectRevoked(); return; }
        fixture.helper.assertTrue(!descent.closed && descent.corrections.accepted
                        && fixture.exits.state.live.get(player) == fixture.offer,
                "One early continuous relative correction did not retain the live offer");
        fixture.helper.assertTrue(descent.continuity.origin.equals(initial) && descent.deadline == fixture.firstDeadline
                        && descent.continuity.horizontal == .5 && descent.continuity.downward == .5,
                "Correction changed the origin, deadline or movement allowances");
        if (fixture.scenario == SECOND_CORRECTION) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), 25, 10);
            fixture.exits.sample(player); fixture.expectRevoked();
        }
    }

    static void land(BayPassengerExitGameTestFixture fixture) {
        var player = fixture.player; var helper = fixture.helper; var offer = fixture.offer;
        if (!fixture.injected && fixture.falls == 0 && player.getY() == offer.permit.before.y) {
            helper.assertTrue(fixture.exits.state.live.get(player) == offer && !offer.descent.closed, "Offer ended before simulated descent");
        }
        player.setOnGround(false); // The simulated incoming movement is airborne until the final vanilla check.
        double next = Math.max(fixture.origin.getY() + .25, player.getY() - 3);
        player.setPos(player.getX(), next, player.getZ()); player.setDeltaMovement(new Vec3(0, -.2, 0));
        fixture.exits.sample(player);
        if (next > fixture.origin.getY() + .25) { return; }
        player.setPos(player.getX(), fixture.origin.getY(), player.getZ()); player.fallDistance = fixture.scenario == ZERO_LANDING ? 1 : 10;
        int before = fixture.falls; boolean closedBefore = offer.descent.closed;
        player.doCheckFallDamage(0, -.25, 0, true);
        helper.assertTrue(fixture.falls > before, "Fixture did not traverse vanilla fall event");
        if (fixture.scenario == ORDINARY || fixture.scenario == AMBIENT_FLUID) {
            helper.assertTrue(player.getHealth() == 20 && fixture.attacks.stream().filter(LivingAttackEvent::isCanceled).count() == 1,
                    "Ordinary landing did not consume exactly one FALL cancellation: health=" + player.getHealth()
                            + "; closedBefore=" + closedBefore + "; closedAfter=" + offer.descent.closed + "; attacks="
                            + fixture.attacks.stream().map(event -> event.getSource().type().msgId() + ":" + event.isCanceled()).toList());
            helper.assertTrue(fixture.fallDistance == 10 && fixture.multiplier == 1, "Exit altered source-less fall distance/multiplier");
            player.invulnerableTime = 0; player.causeFallDamage(10, 1, player.damageSources().fall());
            helper.assertTrue(player.getHealth() < 20, "Second fall was protected");
        } else if (fixture.scenario == ZERO_LANDING || fixture.scenario == CANCELLED_FALL) {
            player.hurt(player.damageSources().fall(), 4);
            helper.assertTrue(player.getHealth() < 20 && fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled), "Zero landing leaked a token");
        } else if (fixture.scenario == REENTRANT_DAMAGE) {
            helper.assertTrue(player.getHealth() < 20 && fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled), "Reentrant attack consumed landing authority");
        } else if (fixture.scenario == NON_FALL_LANDING) {
            helper.assertTrue(player.getHealth() < 20 && fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled)
                    && fixture.attacks.stream().anyMatch(event -> event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)), "Non-FALL landing call was protected");
        } else {
            helper.assertTrue(player.getHealth() < 20 && fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled)
                    && fixture.attacks.stream().anyMatch(event -> event.getSource().is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE)), "Dripstone damage changed");
        }
        fixture.done();
    }
}
