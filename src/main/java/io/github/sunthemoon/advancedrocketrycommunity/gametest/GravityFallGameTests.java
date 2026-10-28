package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.HEIGHT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.MOON;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.assertOneScaling;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.clear;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.expect;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.heal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.horseDamage;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.landing;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.level;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.livingDamage;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.lost;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.scaled;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.start;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.sturdy;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.tickUntilLanded;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.travelUntilLanded;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.Fall;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.FallProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 leaf B (W38) for mobs and vehicles: real native landings where vanilla allows one, otherwise
 * {@code causeFallDamage} with the arguments vanilla passes. Each case asserts the damage of exactly one scaling.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityFallGameTests {
    private static final String BATCH = "gravity_fall";

    private GravityFallGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeMobLandingOnTheMoonIsScaledOnceAgainstTheOverworld(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos earthPad = null;
        BlockPos moonPad = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            earthPad = landing(helper, earth);
            moonPad = landing(helper, moon);
            Cow home = add(entities, mob(new Cow(EntityType.COW, earth), earthPad));
            Cow visitor = add(entities, mob(new Cow(EntityType.COW, moon), moonPad));
            expect(helper, CelestialGravityController.appliedMultiplier(home), 1.0D, "Overworld cow has an ARCE factor");
            expect(helper, CelestialGravityController.appliedMultiplier(visitor), MOON, "Moon cow lacks leaf A's factor");
            try (FallProbe probe = new FallProbe(home, visitor)) {
                helper.assertTrue(travelUntilLanded(home, start(earthPad)) && travelUntilLanded(visitor, start(moonPad)),
                        "A native fall did not land");
                Fall earthFall = probe.only(helper, home, "Overworld cow");
                Fall moonFall = probe.only(helper, visitor, "Moon cow");
                helper.assertTrue(earthFall.incoming() > HEIGHT - 3 && moonFall.incoming() > HEIGHT - 3,
                        "A native fall was shorter than its column: " + earthFall + " " + moonFall);
                helper.assertTrue(earthFall.outgoing() == earthFall.incoming(), "The Overworld fall was scaled");
                expect(helper, lost(home), livingDamage(earthFall.incoming()), "Overworld damage changed");
                helper.assertTrue(moonFall.outgoing() == scaled(moonFall.incoming(), MOON, 1),
                        "The Moon fall was not scaled once before damage: " + moonFall);
                assertOneScaling(helper, visitor, moonFall.incoming(), MOON, "Moon cow");

                // The switch also covers falls whose entity still carries an owned factor from an earlier tick.
                SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, false);
                heal(visitor);
                probe.reset();
                helper.assertTrue(visitor.causeFallDamage(HEIGHT, 1.0F, visitor.damageSources().fall()),
                        "Switched-off fall did no damage");
                Fall off = probe.only(helper, visitor, "Switched-off Moon cow");
                helper.assertTrue(off.outgoing() == HEIGHT, "The switch did not leave the fall unchanged");
                expect(helper, lost(visitor), livingDamage(HEIGHT), "Switched-off damage changed");
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
            clear(earth, earthPad);
            clear(moon, moonPad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void livingMountAndNestedRidersInheritOneScaledLanding(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos pad = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            pad = landing(helper, moon);
            Pig pig = add(entities, mob(new Pig(EntityType.PIG, moon), pad));
            Cow rider = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            Cow nested = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            helper.assertTrue(rider.startRiding(pig, true) && nested.startRiding(rider, true), "Mount fixture failed");
            try (FallProbe probe = new FallProbe(pig, rider, nested)) {
                helper.assertTrue(travelUntilLanded(pig, start(pad)), "The pig's native fall did not land");
                Fall mount = probe.only(helper, pig, "Pig");
                helper.assertTrue(mount.outgoing() == scaled(mount.incoming(), MOON, 1), "The mount was not scaled once");
                assertOneScaling(helper, pig, mount.incoming(), MOON, "Pig");
                for (Cow passenger : List.of(rider, nested)) {
                    Fall inherited = probe.only(helper, passenger, "Passenger");
                    helper.assertTrue(inherited.incoming() == mount.outgoing() && inherited.outgoing() == inherited.incoming(),
                            "A passenger of a living mount was scaled again: " + inherited + " after " + mount);
                    assertOneScaling(helper, passenger, mount.incoming(), MOON, "Passenger");
                }
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
            clear(moon, pad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void minecartAndBoatPassengersScaleOnceWithTheirOwnFactor(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos pad = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            pad = landing(helper, moon);
            Vec3 top = start(pad);
            Minecart cart = add(entities, new Minecart(moon, top.x, top.y, top.z));
            Cow carried = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            helper.assertTrue(carried.startRiding(cart, true), "Minecart fixture failed");
            Boat boat = add(entities, new Boat(moon, top.x, top.y, top.z));
            Cow first = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            Pig second = add(entities, mob(new Pig(EntityType.PIG, moon), pad));
            helper.assertTrue(first.startRiding(boat, true) && second.startRiding(boat, true), "Boat fixture failed");
            try (FallProbe probe = new FallProbe(carried, first, second)) {
                // A minecart's native fall: non-living gravity is not scaled, so its passenger receives the raw distance.
                helper.assertTrue(tickUntilLanded(cart, top, Vec3.ZERO), "The minecart's native fall did not land");
                Fall cartFall = probe.only(helper, carried, "Minecart passenger");
                helper.assertTrue(cartFall.incoming() > HEIGHT - 3, "The minecart fall was shorter than its column");
                helper.assertTrue(cartFall.outgoing() == scaled(cartFall.incoming(), MOON, 1), "Minecart passenger not scaled once");
                assertOneScaling(helper, carried, cartFall.incoming(), MOON, "Minecart passenger");
                // A landing boat breaks and drops items, so it is called as Boat.checkFallDamage calls it.
                boat.causeFallDamage(HEIGHT, 1.0F, boat.damageSources().fall());
                for (LivingEntity passenger : List.<LivingEntity>of(first, second)) {
                    Fall boatFall = probe.only(helper, passenger, "Boat passenger");
                    helper.assertTrue(boatFall.incoming() == HEIGHT && boatFall.outgoing() == scaled(HEIGHT, MOON, 1),
                            "A boat passenger was not scaled once: " + boatFall);
                    assertOneScaling(helper, passenger, HEIGHT, MOON, "Boat passenger");
                }
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
            clear(moon, pad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void horseFamilyLandingPostsNoForgeFallEventAndStaysNative(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos pad = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            pad = landing(helper, moon);
            Horse horse = add(entities, mob(new Horse(EntityType.HORSE, moon), pad));
            Cow rider = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            helper.assertTrue(rider.startRiding(horse, true), "Horse fixture failed");
            expect(helper, CelestialGravityController.appliedMultiplier(horse), MOON, "Moon horse lacks leaf A's factor");
            try (FallProbe probe = new FallProbe(horse, rider)) {
                Vec3 top = start(pad);
                horse.setPos(top);
                horse.setDeltaMovement(Vec3.ZERO);
                horse.setOnGround(false);
                horse.resetFallDistance();
                float distance = 0.0F;
                for (int tick = 0; tick < 600 && !horse.onGround(); tick++) {
                    distance = horse.fallDistance;
                    horse.travel(Vec3.ZERO);
                }
                helper.assertTrue(horse.onGround() && distance > HEIGHT - 3, "The horse's native fall did not land");
                // AbstractHorse.causeFallDamage never posts LivingFallEvent; see the W38 design note.
                helper.assertTrue(probe.of(horse).isEmpty() && probe.of(rider).isEmpty(),
                        "The horse family unexpectedly posted a Forge fall event");
                int nativeDamage = horseDamage(distance);
                helper.assertTrue(nativeDamage != horseDamage(scaled(distance, MOON, 1)), "Fixture cannot see a scaling");
                expect(helper, lost(horse), nativeDamage, "Horse damage is not the native, unscaled damage");
                expect(helper, lost(rider), nativeDamage, "The rider did not take the horse's native damage once");
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
            clear(moon, pad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void slowFallingAndFallImmunityKeepTheirNativeReach(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos pad = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            pad = landing(helper, moon);
            Cow drifting = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            Chicken immune = add(entities, mob(new Chicken(EntityType.CHICKEN, moon), pad));
            Cow carried = add(entities, mob(new Cow(EntityType.COW, moon), pad));
            helper.assertTrue(carried.startRiding(immune, true), "Chicken fixture failed");
            try (FallProbe probe = new FallProbe(drifting, immune, carried)) {
                drifting.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2_000));
                drifting.setNoAi(false);
                // Full native ticks: LivingEntity.aiStep resets the fall distance under slow falling before travel.
                // Keep this ledge-step fixture; LivingGravityGameTests separately covers starting from rest (W189).
                Vec3 top = GravityFallFixtures.above(pad, 6.0D);
                helper.assertTrue(tickUntilLanded(drifting, top, new Vec3(0.0D, -0.1D, 0.0D)), "The slow fall did not land");
                helper.assertTrue(probe.of(drifting).isEmpty() && drifting.fallDistance == 0.0F,
                        "A slow-falling landing reached the fall event");
                AttributeInstance gravity = drifting.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
                helper.assertTrue(Math.abs(gravity.getValue() / gravity.getBaseValue() - MOON) > 0.05D,
                        "Fixture lacks the native slow-falling gravity modifier");
                heal(drifting);
                helper.assertTrue(drifting.causeFallDamage(HEIGHT, 1.0F, drifting.damageSources().fall()),
                        "Slow-falling cow took no direct fall");
                Fall direct = probe.only(helper, drifting, "Slow-falling cow");
                helper.assertTrue(direct.outgoing() == scaled(HEIGHT, MOON, 1),
                        "The fall used the complete gravity attribute instead of the owned factor: " + direct);
                assertOneScaling(helper, drifting, HEIGHT, MOON, "Slow-falling cow");

                // A chicken is fall_damage_immune: its event fires, but Entity.causeFallDamage stops before passengers.
                immune.causeFallDamage(HEIGHT, 1.0F, immune.damageSources().fall());
                helper.assertTrue(probe.only(helper, immune, "Chicken").outgoing() == scaled(HEIGHT, MOON, 1),
                        "The immune mount was not scaled like any other living entity");
                helper.assertTrue(probe.of(carried).isEmpty() && lost(immune) == 0.0F && lost(carried) == 0.0F,
                        "Fall immunity changed its native reach");
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
            clear(moon, pad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void controllerFixtureLeavesEveryUnscaledEventUnchanged(GameTestHelper helper) {
        List<Entity> entities = new ArrayList<>();
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        AtomicBoolean enabled = new AtomicBoolean(true);
        CelestialGravityController controller = new CelestialGravityController(
                new CelestialEnvironmentService(new CelestialCatalogManager()),
                (world, pos) -> OptionalDouble.empty(), player -> OptionalDouble.empty(), enabled::get);
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            Cow cow = add(entities, mob(new Cow(EntityType.COW, moon), helper.absolutePos(new BlockPos(2, 2, 2)).atY(150)));
            float health = cow.getHealth();
            LivingFallEvent scaledEvent = fall(controller, cow, HEIGHT, 0.7F);
            helper.assertTrue(scaledEvent.getDistance() == scaled(HEIGHT, MOON, 1) && scaledEvent.getDamageMultiplier() == 0.7F,
                    "The fixture did not scale only the distance");
            helper.assertTrue(fall(controller, cow, Float.MAX_VALUE, 1.0F).getDistance() == scaled(Float.MAX_VALUE, MOON, 1),
                    "The largest finite fall was not scaled");
            LivingFallEvent canceled = new LivingFallEvent(cow, HEIGHT, 1.0F);
            canceled.setCanceled(true);
            controller.onLivingFall(canceled);
            helper.assertTrue(canceled.getDistance() == HEIGHT, "A canceled event was changed");
            for (float malformed : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.0F}) {
                unchanged(helper, fall(controller, cow, malformed, 1.0F), malformed, "Malformed distance " + malformed);
            }
            enabled.set(false);
            unchanged(helper, fall(controller, cow, HEIGHT, 1.0F), HEIGHT, "Switched-off fixture");
            enabled.set(true);
            LivingFallEvent elsewhere = new LivingFallEvent(cow, HEIGHT, 1.0F);
            CompletableFuture.runAsync(() -> controller.onLivingFall(elsewhere)).join();
            unchanged(helper, elsewhere, HEIGHT, "Off-thread event");
            Pig pig = add(entities, mob(new Pig(EntityType.PIG, moon), cow.blockPosition()));
            helper.assertTrue(cow.startRiding(pig, true), "Pig fixture failed");
            unchanged(helper, fall(controller, cow, HEIGHT, 1.0F), HEIGHT, "Passenger of a living mount");
            cow.stopRiding();
            Boat boat = add(entities, new Boat(moon, cow.getX(), cow.getY(), cow.getZ()));
            helper.assertTrue(cow.startRiding(boat, true), "Boat fixture failed");
            helper.assertTrue(fall(controller, cow, HEIGHT, 1.0F).getDistance() == scaled(HEIGHT, MOON, 1),
                    "Passenger of a non-living vehicle was not scaled");
            cow.stopRiding();

            AttributeInstance gravity = cow.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
            double[] amounts = {9.0D, -2.0D, 3.0D, -1.0D};
            float[] expected = {HEIGHT, HEIGHT, HEIGHT * 4.0F, 0.0F};
            for (int i = 0; i < amounts.length; i++) {
                owned(gravity, amounts[i], AttributeModifier.Operation.MULTIPLY_TOTAL);
                helper.assertTrue(fall(controller, cow, HEIGHT, 1.0F).getDistance() == expected[i],
                        "Owned amount " + amounts[i] + " was not bounded");
            }
            owned(gravity, -0.5D, AttributeModifier.Operation.ADDITION);
            unchanged(helper, fall(controller, cow, HEIGHT, 1.0F), HEIGHT, "Wrong-operation owned modifier");
            gravity.removeModifier(CelestialGravityController.MODIFIER_ID);
            unchanged(helper, fall(controller, cow, HEIGHT, 1.0F), HEIGHT, "Entity without the owned modifier");
            helper.assertTrue(cow.getHealth() == health, "The listener applied damage itself");
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** A sturdy mob above the landing block, after one native tick that applies the production gravity. */
    private static <T extends Mob> T mob(T mob, BlockPos landing) {
        mob.level().getChunkAt(landing); // Explicit fixture setup, not production fall loading.
        mob.setNoAi(true);
        mob.setPos(start(landing));
        mob.tick();
        mob.setNoAi(false);
        return sturdy(mob);
    }

    private static <T extends Entity> T add(List<Entity> entities, T entity) {
        entities.add(entity);
        return entity;
    }

    private static LivingFallEvent fall(CelestialGravityController controller, LivingEntity entity, float distance,
                                        float multiplier) {
        LivingFallEvent event = new LivingFallEvent(entity, distance, multiplier);
        controller.onLivingFall(event);
        return event;
    }

    private static void unchanged(GameTestHelper helper, LivingFallEvent event, float distance, String what) {
        helper.assertTrue(Float.floatToRawIntBits(event.getDistance()) == Float.floatToRawIntBits(distance)
                && event.getDamageMultiplier() == 1.0F && !event.isCanceled(), what + " changed the event");
    }

    private static void owned(AttributeInstance gravity, double amount, AttributeModifier.Operation operation) {
        gravity.removeModifier(CelestialGravityController.MODIFIER_ID);
        gravity.addTransientModifier(new AttributeModifier(CelestialGravityController.MODIFIER_ID,
                "Owned fixture " + UUID.randomUUID(), amount, operation));
    }
}
