package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

class CelestialGravityControllerTest {
    @Test
    void moonMultiplierIsAppliedIdempotentlyAndVanillaRemovesIt() {
        AttributeInstance gravity = gravityAttribute();

        CelestialGravityController.applyMultiplier(gravity, 0.165D);
        AttributeModifier first = gravity.getModifier(CelestialGravityController.MODIFIER_ID);

        assertEquals(0.08D * 0.165D, gravity.getValue(), 1.0E-12D);
        CelestialGravityController.applyMultiplier(gravity, 0.165D);
        assertSame(first, gravity.getModifier(CelestialGravityController.MODIFIER_ID));

        CelestialGravityController.applyMultiplier(gravity, 1.0D);
        assertNull(gravity.getModifier(CelestialGravityController.MODIFIER_ID));
        assertEquals(0.08D, gravity.getValue(), 1.0E-12D);
    }

    @Test
    void spaceCanUseZeroGravityWithoutChangingBaseAttribute() {
        AttributeInstance gravity = gravityAttribute();

        CelestialGravityController.applyMultiplier(gravity, 0.0D);

        assertEquals(0.0D, gravity.getValue(), 1.0E-12D);
        assertEquals(0.08D, gravity.getBaseValue(), 1.0E-12D);
    }

    private static AttributeInstance gravityAttribute() {
        return new AttributeInstance(
                new RangedAttribute("test.gravity", 0.08D, 0.0D, 1.0D),
                ignored -> {
                }
        );
    }
}
