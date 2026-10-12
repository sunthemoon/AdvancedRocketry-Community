package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.SuitMotionService.Reply;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.SuitMotionService.Result;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-066 section 5.2 on the logical server, with a connected fixture player and its own motion service. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuitMotionGameTests {
    private static final long T = 1_000;

    private static void paid(MotionFixture f, long now, String why) {
        f.rest();
        Result result = f.service.step(f.player, now);
        f.helper.assertTrue(result == Result.PAID, why + ": " + result);
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void onlyPaidHoverResetsVanillasFloatingCounter(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean flightAllowed = server.isFlightAllowed();
        server.setFlightAllowed(false);
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 5));
            f.wear(EquipmentSlot.HEAD, MotionFixture.piece(EquipmentSlot.HEAD, "hover_upgrade"));
            f.player.teleportTo(f.player.getX(), f.player.getY() + 16, f.player.getZ());
            f.acknowledgeTeleport();
            f.send(JetpackIntentPacket.toggle(), T);
            f.send(JetpackIntentPacket.cycleMode(), T);
            f.send(JetpackIntentPacket.thrust(true), T);
            f.service.session(f.player.getUUID()).orElseThrow().reference(f.player.getY() + 2);
            for (int i = 0; i < 5; i++) {
                f.floatTick();
                helper.assertTrue(f.player.connection.aboveGroundTickCount == 1, "Vanilla did not count the floating tick");
                helper.assertTrue(f.service.step(f.player, T + i) == Result.PAID, "Paid hover correction refused");
                helper.assertTrue(f.player.connection.aboveGroundTickCount == 0 && f.hydrogen() == 4 - i,
                        "Paid hover did not reset the timer after exactly one hydrogen debit");
            }
            f.send(JetpackIntentPacket.thrust(true), T + 5);
            for (int i = 1; i <= 3; i++) {
                f.floatTick();
                helper.assertTrue(f.service.step(f.player, T + 4 + i) == Result.NO_HYDROGEN, "Empty tank granted hover");
                helper.assertTrue(f.player.connection.aboveGroundTickCount == i && f.sent.size() == 5,
                        "Unpaid floating suppressed vanilla's timer");
            }
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment.setHydrogen(
                    f.player.getItemBySlot(EquipmentSlot.CHEST), 5), "Could not refill the disposable fixture");
            f.floatTick();
            helper.assertTrue(f.service.step(f.player, T + 10) == Result.NO_INTENT && f.hydrogen() == 5
                    && f.player.connection.aboveGroundTickCount == 4, "Expired intent reset the unpaid floating timer");
            helper.assertTrue(!f.player.getAbilities().mayfly && !f.player.getAbilities().flying, "Creative flight flags changed");
        } finally { server.setFlightAllowed(flightAllowed); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void noThrustWithoutHydrogenAndOneUnitPerCorrection(GameTestHelper helper) {
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, -1));
            helper.assertTrue(f.send(JetpackIntentPacket.thrust(true), T) == Reply.IGNORED, "Thrust before enabling");
            helper.assertTrue(f.send(JetpackIntentPacket.toggle(), T) == Reply.ENABLED_NORMAL, "Jetpack did not enable");
            f.send(JetpackIntentPacket.thrust(true), T);
            helper.assertTrue(f.service.step(f.player, T) == Result.NO_HYDROGEN, "Thrust without a hydrogen tank");
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 0));
            f.send(JetpackIntentPacket.thrust(true), T + 1);
            helper.assertTrue(f.service.step(f.player, T + 1) == Result.NO_HYDROGEN, "Thrust from an empty tank");
            helper.assertTrue(f.sent.isEmpty() && f.player.getDeltaMovement().lengthSqr() == 0, "Unpaid thrust moved the player");
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 5));
            double increment = JetpackPhysics.increment(CommonConfig.jetPackForce());
            f.send(JetpackIntentPacket.thrust(true), T + 2);
            double velocity = 0;
            for (int i = 0; i < 5; i++) {
                f.player.setOldPosAndRot();
                f.player.setDeltaMovement(0, velocity, 0);
                helper.assertTrue(f.service.step(f.player, T + 2 + i) == Result.PAID, "Correction " + i + " refused");
                helper.assertTrue(f.hydrogen() == 4 - i, "Correction " + i + " did not spend exactly one unit: " + f.hydrogen());
                helper.assertTrue(f.sent.size() == i + 1 && f.sent.get(i).y() == increment && f.sent.get(i).x() == 0,
                        "Correction " + i + " sent another increment than 0.08 x force");
                velocity = Math.min(velocity + increment, JetpackPhysics.VERTICAL_CAP);
                helper.assertTrue(Math.abs(f.player.getDeltaMovement().y - velocity) < 1.0E-9, "Server motion differs from the paid increment");
                helper.assertTrue(f.service.step(f.player, T + 2 + i) == Result.ALREADY_CORRECTED && f.hydrogen() == 4 - i,
                        "A second correction in one tick");
            }
            f.send(JetpackIntentPacket.thrust(true), T + 7);
            helper.assertTrue(f.service.step(f.player, T + 7) == Result.NO_HYDROGEN && f.sent.size() == 5,
                    "Thrust continued after the last unit");
            helper.assertTrue(!f.player.getAbilities().mayfly && !f.player.getAbilities().flying, "Jetpack touched creative flight");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void thrustIntentExpiresFiveTicksAfterTheLastHeartbeat(GameTestHelper helper) {
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 20));
            f.send(JetpackIntentPacket.toggle(), T);
            f.send(JetpackIntentPacket.thrust(true), T);
            for (int i = 0; i < 5; i++) { paid(f, T + i, "Heartbeat tick +" + i); }
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 5) == Result.NO_INTENT, "Intent outlived five ticks");
            helper.assertTrue(f.hydrogen() == 15, "Expired intent spent hydrogen");
            f.send(JetpackIntentPacket.thrust(true), T + 6);
            paid(f, T + 6, "Renewed heartbeat");
            f.send(JetpackIntentPacket.thrust(false), T + 7);
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 7) == Result.NO_INTENT && f.hydrogen() == 14, "Release did not end thrust at once");
            f.send(JetpackIntentPacket.thrust(true), T + 8);
            f.send(JetpackIntentPacket.toggle(), T + 8);
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 8) == Result.NO_SESSION && f.hydrogen() == 14, "Switching off kept thrust");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void hoverIsRefusedWithoutTheHoverUpgrade(GameTestHelper helper) {
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 20));
            f.wear(EquipmentSlot.HEAD, MotionFixture.piece(EquipmentSlot.HEAD, "anti_fog_visor"));
            helper.assertTrue(f.send(JetpackIntentPacket.toggle(), T) == Reply.ENABLED_NORMAL, "Jetpack did not enable");
            helper.assertTrue(f.send(JetpackIntentPacket.cycleMode(), T) == Reply.NO_HOVER_UPGRADE, "HOVER without the upgrade");
            helper.assertTrue(f.service.session(f.player.getUUID()).orElseThrow().mode() == JetpackMode.NORMAL, "Refusal changed the mode");
            ItemStack hoverHelmet = MotionFixture.piece(EquipmentSlot.HEAD, "hover_upgrade");
            ItemStack heldHelmet = hoverHelmet.copy();
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, heldHelmet);
            helper.assertTrue(f.send(JetpackIntentPacket.cycleMode(), T + 1) == Reply.NO_HOVER_UPGRADE, "A held helmet counted as worn");
            f.wear(EquipmentSlot.HEAD, hoverHelmet);
            helper.assertTrue(f.send(JetpackIntentPacket.cycleMode(), T + 2) == Reply.MODE_HOVER, "HOVER refused with the upgrade worn");
            f.send(JetpackIntentPacket.thrust(true), T + 3);
            f.rest();
            Result atReference = f.service.step(f.player, T + 3);
            helper.assertTrue(atReference == Result.NOT_NEEDED && f.hydrogen() == 20, "Hover at rest paid without a correction: " + atReference);
            f.player.teleportTo(f.player.getX(), f.player.getY() - 2, f.player.getZ());
            paid(f, T + 4, "Hover below its reference");
            helper.assertTrue(f.hydrogen() == 19, "Hover correction did not spend one unit");
            f.wear(EquipmentSlot.HEAD, ItemStack.EMPTY);
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 5) == Result.NO_HOVER_UPGRADE
                    && !f.service.session(f.player.getUUID()).orElseThrow().enabled() && f.hydrogen() == 19,
                    "Removing the hover upgrade kept the jetpack in HOVER");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void sameKindWornReplacementsResetHoverWithoutLosingHydrogen(GameTestHelper helper) {
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 20));
            f.wear(EquipmentSlot.HEAD, MotionFixture.piece(EquipmentSlot.HEAD, "hover_upgrade"));
            f.send(JetpackIntentPacket.toggle(), T);
            f.send(JetpackIntentPacket.cycleMode(), T);
            f.send(JetpackIntentPacket.thrust(true), T);
            var session = f.service.session(f.player.getUUID()).orElseThrow();
            session.reference(f.player.getY() + 2);
            paid(f, T, "Initial hover correction");
            ItemStack chest = f.player.getItemBySlot(EquipmentSlot.CHEST);
            helper.assertTrue(session.hasReference() && f.hydrogen() == 19, "Debit reset HOVER or lost hydrogen");
            f.wear(EquipmentSlot.HEAD, f.player.getItemBySlot(EquipmentSlot.HEAD).copy());
            helper.assertTrue(f.service.step(f.player, T + 1) == Result.NO_INTENT && !session.hasReference()
                    && f.hydrogen() == 19, "Same-kind HEAD replacement kept the old session or spent gas");
            f.send(JetpackIntentPacket.thrust(true), T + 1);
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 1) == Result.NOT_NEEDED && f.hydrogen() == 19,
                    "Replacement helmet pulled toward the old reference");
            double reference = session.reference(f.player.getY());
            f.player.teleportTo(f.player.getX(), reference - 1, f.player.getZ());
            paid(f, T + 2, "Hover with replacement helmet");
            paid(f, T + 3, "Hover after an ordinary gas debit");
            helper.assertTrue(session.reference(f.player.getY()) == reference && f.hydrogen() == 17,
                    "Same-stack debit reset the reference or gas accounting was not exact");
            var oldChest = chest.serializeNBT();
            f.wear(EquipmentSlot.CHEST, chest.copy());
            helper.assertTrue(f.service.step(f.player, T + 4) == Result.NO_INTENT && !session.hasReference()
                    && f.hydrogen() == 17, "Same-kind CHEST replacement kept the session or spent gas");
            f.send(JetpackIntentPacket.thrust(true), T + 4);
            f.rest();
            helper.assertTrue(f.service.step(f.player, T + 4) == Result.NOT_NEEDED && f.hydrogen() == 17,
                    "Replacement chest pulled toward the old reference");
            f.player.teleportTo(f.player.getX(), f.player.getY() - 1, f.player.getZ());
            paid(f, T + 5, "Hover with replacement chest");
            helper.assertTrue(f.hydrogen() == 16 && oldChest.equals(chest.serializeNBT()),
                    "Replacement debit changed the removed chest or failed to spend exactly one unit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void elytraFlightAndRocketSeatsRefuseThrust(GameTestHelper helper) {
        try (var f = new MotionFixture(helper)) {
            f.wear(EquipmentSlot.CHEST, MotionFixture.chest(true, 20));
            f.send(JetpackIntentPacket.toggle(), T);
            f.send(JetpackIntentPacket.thrust(true), T);
            f.rest();
            f.player.startFallFlying();
            Result gliding = f.service.step(f.player, T);
            f.player.stopFallFlying();
            helper.assertTrue(gliding == Result.ELYTRA && f.hydrogen() == 20 && f.sent.isEmpty(), "Thrust during elytra flight: " + gliding);
            var rocket = ModEntities.ROCKET.get().create(f.level);
            helper.assertTrue(rocket != null, "Rocket type unavailable");
            // Dismounting goes beside the rocket, so keep it at the fixture rather than at the world origin.
            rocket.moveTo(f.player.getX(), f.player.getY(), f.player.getZ());
            helper.assertTrue(f.player.startRiding(rocket, true), "Fixture could not take a rocket seat");
            try {
                f.rest();
                Result seated = f.service.step(f.player, T + 1);
                helper.assertTrue(seated == Result.VEHICLE && f.hydrogen() == 20 && f.sent.isEmpty(), "Thrust from a rocket seat: " + seated);
            } finally {
                f.player.stopRiding();
                rocket.discard();
            }
            paid(f, T + 2, "Thrust after leaving the seat");
            helper.assertTrue(f.hydrogen() == 19, "Control correction did not spend one unit");
        }
        helper.succeed();
    }

    /** Closes the fixture before a failure escapes, so a failed step leaves no player behind. */
    private static Runnable guarded(MotionFixture f, Runnable step) {
        return () -> {
            try { step.run(); }
            catch (RuntimeException | Error failure) { f.close(); throw failure; }
        };
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 100)
    public static void bionicModifierIsRemovedOnUnequip(GameTestHelper helper) {
        var f = new MotionFixture(helper);
        UUID foreign = UUID.randomUUID();
        var speed = f.player.getAttribute(Attributes.MOVEMENT_SPEED);
        guarded(f, () -> {
            speed.addTransientModifier(new AttributeModifier(foreign, "other mod", 0.05, AttributeModifier.Operation.ADDITION));
            f.wear(EquipmentSlot.LEGS, MotionFixture.piece(EquipmentSlot.LEGS, "bionic_leg_upgrade"));
            f.player.setSprinting(true);
            helper.assertTrue(BionicLegs.update(f.player) && speed.getModifier(BionicLegs.MODIFIER_ID) != null, "Sprinting bionic legs gave no bonus");
            f.player.setSprinting(false);
            helper.assertTrue(!BionicLegs.update(f.player) && speed.getModifier(BionicLegs.MODIFIER_ID) == null, "Bonus outlived sprinting");
            f.player.setSprinting(true);
            BionicLegs.update(f.player);
            f.wear(EquipmentSlot.LEGS, ItemStack.EMPTY);
            helper.assertTrue(!BionicLegs.update(f.player) && speed.getModifier(BionicLegs.MODIFIER_ID) == null, "Bonus outlived the leggings");
            helper.assertTrue(speed.getModifier(foreign) != null, "Another mod's modifier was removed");
            f.wear(EquipmentSlot.LEGS, MotionFixture.piece(EquipmentSlot.LEGS, "bionic_leg_upgrade"));
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                helper.assertTrue(!BionicLegs.update(f.player) && speed.getModifier(BionicLegs.MODIFIER_ID) == null, "Bonus while equipment is disabled");
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }).run();
        // The installed server tick applies and removes it without a direct call.
        helper.startSequence()
                .thenExecuteAfter(2, guarded(f, () -> helper.assertTrue(speed.getModifier(BionicLegs.MODIFIER_ID) != null,
                        "Server tick gave no bonus")))
                .thenExecute(guarded(f, () -> f.wear(EquipmentSlot.LEGS, ItemStack.EMPTY)))
                .thenExecuteAfter(2, guarded(f, () -> helper.assertTrue(speed.getModifier(BionicLegs.MODIFIER_ID) == null
                        && speed.getModifier(foreign) != null, "Server tick kept the bonus after unequip")))
                .thenExecute(f::close)
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "equipment_motion", timeoutTicks = 200)
    public static void paddedBootsCancelOnlyFallDamageAndLowGravityNarrowsThem(GameTestHelper helper) {
        var f = new MotionFixture(helper);
        // A newly joined player is immune to damage for 60 ticks.
        helper.runAfterDelay(65, () -> {
            try {
                var player = f.player;
                var falls = player.damageSources().fall();
                player.setHealth(20); player.invulnerableTime = 0;
                f.wear(EquipmentSlot.FEET, MotionFixture.piece(EquipmentSlot.FEET));
                helper.assertTrue(player.causeFallDamage(8, 1, falls) && player.getHealth() < 20, "Control fall did no damage");
                player.setHealth(20); player.invulnerableTime = 0;
                f.wear(EquipmentSlot.FEET, MotionFixture.piece(EquipmentSlot.FEET, "padded_landing_boots"));
                helper.assertTrue(!player.causeFallDamage(8, 1, falls) && player.getHealth() == 20, "Padded boots did not cancel the fall");
                helper.assertTrue(player.hurt(player.damageSources().magic(), 2) && player.getHealth() == 18, "Padded boots cancelled other damage");
                player.setHealth(20); player.invulnerableTime = 0;
                SwitchOverrides.set(CommonConfig.LOW_GRAVITY_BOOTS, true);
                var gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
                try {
                    helper.assertTrue(PaddedBoots.gravityMultiplier(player) == 1.0, "Fixture is not in ordinary gravity");
                    helper.assertTrue(player.causeFallDamage(8, 1, falls) && player.getHealth() < 20, "lowGravityBoots cancelled a fall in full gravity");
                    player.setHealth(20); player.invulnerableTime = 0;
                    gravity.addTransientModifier(new AttributeModifier(CelestialGravityController.MODIFIER_ID, "fixture moon",
                            0.166 - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
                    helper.assertTrue(Math.abs(PaddedBoots.gravityMultiplier(player) - 0.166) < 1.0E-9, "Applied multiplier not read");
                    helper.assertTrue(!player.causeFallDamage(8, 1, falls) && player.getHealth() == 20, "lowGravityBoots did not cancel a low-gravity fall");
                    SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
                    helper.assertTrue(player.causeFallDamage(8, 1, falls) && player.getHealth() < 20, "Disabled equipment still cancelled the fall");
                } finally {
                    SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED);
                    SwitchOverrides.clear(CommonConfig.LOW_GRAVITY_BOOTS);
                    gravity.removeModifier(CelestialGravityController.MODIFIER_ID);
                }
                helper.succeed();
            } finally { f.close(); }
        });
    }
}
