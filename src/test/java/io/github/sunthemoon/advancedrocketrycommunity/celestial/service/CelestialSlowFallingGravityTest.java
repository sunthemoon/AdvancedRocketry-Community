package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CelestialSlowFallingGravityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void moonFloorSurvivesDragWithoutChangingTheBodyFactor() {
        var gravity = gravity(0.165D);
        var body = gravity.getModifier(CelestialGravityController.MODIFIER_ID);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertEquals(0.0031D, gravity.getValue(), 1.0E-12D);
        assertTrue(gravity.getValue() * 0.98D > 0.003D);
        assertSame(body, gravity.getModifier(CelestialGravityController.MODIFIER_ID));
        assertEquals(0.165D - 1.0D, body.getAmount());
        assertEquals(0.08D, gravity.getBaseValue());
        var floor = gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID);
        assertFalse(gravity.removePermanentModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        for (int tick = 0; tick < 100; tick++) {
            CelestialGravityController.applySlowFallFloor(gravity, true);
            assertSame(floor, gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        }
    }

    @Test void zeroAndSufficientGravityAreNeverRaised() {
        for (double factor : new double[]{0.0D, 0.38D, 1.0D, 4.0D}) {
            var gravity = gravity(factor);
            double before = gravity.getValue();
            CelestialGravityController.applySlowFallFloor(gravity, true);
            assertEquals(before, gravity.getValue());
            assertNull(gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        }
    }

    @Test void leavingSlowFallOrLosingItsNativeModifierRemovesOnlyTheFloor() {
        var gravity = gravity(0.165D);
        var foreign = new AttributeModifier(UUID.randomUUID(), "foreign", 0.25D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        gravity.addPermanentModifier(foreign);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertEquals(0.0031D, gravity.getValue(), 1.0E-12D);
        CelestialGravityController.applySlowFallFloor(gravity, false);
        assertEquals(0.01D * 0.165D * 1.25D, gravity.getValue(), 1.0E-12D);
        assertSame(foreign, gravity.getModifier(foreign.getId()));
        CelestialGravityController.applySlowFallFloor(gravity, true);
        gravity.removeModifier(CelestialGravityController.SLOW_FALLING_ID);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertNull(gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        assertEquals(0.08D * 0.165D * 1.25D, gravity.getValue(), 1.0E-12D);
    }

    @Test void changedForeignGravityRecomputesAndThenRemovesTheFloor() {
        var gravity = gravity(0.165D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        gravity.setBaseValue(0.075D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertEquals(0.0031D, gravity.getValue(), 1.0E-12D);
        gravity.setBaseValue(0.1D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertNull(gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        assertEquals(0.03D * 0.165D, gravity.getValue(), 1.0E-12D);
        gravity.setBaseValue(0.06D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertEquals(0.0D, gravity.getValue());
        assertNull(gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
    }

    @Test void clampedOldFloorDoesNotMaskProfileOrBaseChanges() {
        var gravity = gravity(1.0E-12D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        CelestialGravityController.applyMultiplier(gravity, 0.165D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertEquals(0.0031D, gravity.getValue(), 1.0E-12D);
        gravity.setBaseValue(0.07000001D);
        CelestialGravityController.applySlowFallFloor(gravity, true);
        gravity.setBaseValue(1.0D);
        gravity.addTransientModifier(foreign(AttributeModifier.Operation.ADDITION, 0.01D));
        gravity.addTransientModifier(foreign(AttributeModifier.Operation.MULTIPLY_BASE, 0.2D));
        gravity.addTransientModifier(foreign(AttributeModifier.Operation.MULTIPLY_TOTAL, 0.5D));
        CelestialGravityController.applySlowFallFloor(gravity, true);
        assertNull(gravity.getModifier(CelestialGravityController.SLOW_FALL_FLOOR_ID));
        assertEquals((1.0D - 0.07D + 0.01D) * 1.2D * 1.5D * 0.165D, gravity.getValue(), 1.0E-12D);
    }

    private static AttributeModifier foreign(AttributeModifier.Operation operation, double amount) {
        return new AttributeModifier(UUID.randomUUID(), "foreign change", amount, operation);
    }

    private static AttributeInstance gravity(double factor) {
        var gravity = new AttributeInstance(new RangedAttribute("test.gravity", 0.08D, 0.0D, 1.0D), ignored -> { });
        gravity.addTransientModifier(new AttributeModifier(CelestialGravityController.SLOW_FALLING_ID,
                "native slow falling fixture", -0.07D, AttributeModifier.Operation.ADDITION));
        CelestialGravityController.applyMultiplier(gravity, factor);
        return gravity;
    }
}
