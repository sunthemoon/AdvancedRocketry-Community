package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void missingAttributeIsSkippedEvenWhenTheFactorIsInvalid() {
        assertDoesNotThrow(() -> CelestialGravityController.applyMultiplier(null, Double.NaN));
    }

    @Test
    void outOfRangeFactorsLeaveAllAttributeStateUnchanged() {
        AttributeInstance gravity = gravityAttribute();
        AttributeModifier foreign = foreignModifier(AttributeModifier.Operation.ADDITION, 0.01D);
        gravity.addPermanentModifier(foreign);
        CelestialGravityController.applyMultiplier(gravity, 0.5D);
        AttributeModifier owned = gravity.getModifier(CelestialGravityController.MODIFIER_ID);
        double before = gravity.getValue();

        for (double invalid : new double[]{-0.001D, 4.001D, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CelestialGravityController.applyMultiplier(gravity, invalid));
            assertSame(owned, gravity.getModifier(CelestialGravityController.MODIFIER_ID));
            assertSame(foreign, gravity.getModifier(foreign.getId()));
            assertEquals(before, gravity.getValue(), 1.0E-12D);
            assertEquals(0.08D, gravity.getBaseValue(), 1.0E-12D);
        }
        CelestialGravityController.applyMultiplier(gravity, 4.0D);
        assertEquals((0.08D + 0.01D) * 4.0D, gravity.getValue(), 1.0E-12D);
    }

    @Test
    void factorOneRemovesZeroAmountResidueRegardlessOfOperationOrPersistence() {
        for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
            for (boolean permanent : new boolean[]{false, true}) {
                AttributeInstance gravity = gravityAttribute();
                AttributeModifier foreign = foreignModifier(AttributeModifier.Operation.ADDITION, 0.01D);
                gravity.addPermanentModifier(foreign);
                AttributeModifier residue = ownedModifier(0.0D, operation);
                if (permanent) {
                    gravity.addPermanentModifier(residue);
                } else {
                    gravity.addTransientModifier(residue);
                }
                CelestialGravityController.applyMultiplier(gravity, 1.0D);
                assertNull(gravity.getModifier(CelestialGravityController.MODIFIER_ID));
                assertSame(foreign, gravity.getModifier(foreign.getId()));
                assertEquals(0.09D, gravity.getValue(), 1.0E-12D);
                assertEquals(0.08D, gravity.getBaseValue(), 1.0E-12D);
            }
        }
    }

    @Test
    void sameAmountWithWrongOperationIsReplaced() {
        for (AttributeModifier.Operation wrong : new AttributeModifier.Operation[]{
                AttributeModifier.Operation.ADDITION, AttributeModifier.Operation.MULTIPLY_BASE}) {
            AttributeInstance gravity = gravityAttribute();
            AttributeModifier residue = ownedModifier(-0.5D, wrong);
            gravity.addPermanentModifier(residue);
            CelestialGravityController.applyMultiplier(gravity, 0.5D);
            AttributeModifier replacement = gravity.getModifier(CelestialGravityController.MODIFIER_ID);
            assertNotSame(residue, replacement);
            assertEquals(AttributeModifier.Operation.MULTIPLY_TOTAL, replacement.getOperation());
            assertEquals(-0.5D, replacement.getAmount(), 0.0D);
            assertEquals(0.04D, gravity.getValue(), 1.0E-12D);
            assertFalse(gravity.removePermanentModifier(CelestialGravityController.MODIFIER_ID));
        }
    }

    @Test
    void matchingPermanentResidueBecomesTransientAndThenStaysIdempotent() {
        AtomicInteger dirty = new AtomicInteger();
        AttributeInstance gravity = new AttributeInstance(
                new RangedAttribute("test.gravity", 0.08D, 0.0D, 1.0D), ignored -> dirty.incrementAndGet());
        AttributeModifier residue = ownedModifier(0.165D - 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        gravity.addPermanentModifier(residue);
        int beforeConversion = dirty.get();

        CelestialGravityController.applyMultiplier(gravity, 0.165D);
        AttributeModifier converted = gravity.getModifier(CelestialGravityController.MODIFIER_ID);
        assertNotSame(residue, converted);
        assertEquals(beforeConversion + 2, dirty.get());
        assertFalse(gravity.removePermanentModifier(CelestialGravityController.MODIFIER_ID));
        assertSame(converted, gravity.getModifier(CelestialGravityController.MODIFIER_ID));

        int afterConversion = dirty.get();
        CelestialGravityController.applyMultiplier(gravity, 0.165D);
        assertSame(converted, gravity.getModifier(CelestialGravityController.MODIFIER_ID));
        assertEquals(afterConversion, dirty.get());
        assertEquals(0.08D * 0.165D, gravity.getValue(), 1.0E-12D);
    }

    @Test
    void changesAndCleanupPreserveBaseAndEveryForeignOperation() {
        AttributeInstance gravity = gravityAttribute();
        gravity.setBaseValue(0.1D);
        AttributeModifier addition = foreignModifier(AttributeModifier.Operation.ADDITION, 0.01D);
        AttributeModifier base = foreignModifier(AttributeModifier.Operation.MULTIPLY_BASE, 0.2D);
        AttributeModifier total = foreignModifier(AttributeModifier.Operation.MULTIPLY_TOTAL, 0.25D);
        gravity.addPermanentModifier(addition);
        gravity.addTransientModifier(base);
        gravity.addPermanentModifier(total);
        double foreignValue = gravity.getValue();

        for (double factor : new double[]{0.165D, 0.38D, 0.0D, 4.0D, 1.0D}) {
            CelestialGravityController.applyMultiplier(gravity, factor);
            assertSame(addition, gravity.getModifier(addition.getId()));
            assertSame(base, gravity.getModifier(base.getId()));
            assertSame(total, gravity.getModifier(total.getId()));
            assertEquals(0.1D, gravity.getBaseValue(), 1.0E-12D);
            assertEquals(foreignValue * factor, gravity.getValue(), 1.0E-12D);
        }
        assertTrue(gravity.removePermanentModifier(addition.getId()));
        assertTrue(gravity.removePermanentModifier(total.getId()));
        assertFalse(gravity.removePermanentModifier(base.getId()));
        assertSame(base, gravity.getModifier(base.getId()));
    }

    @Test
    void additiveConstructorRejectsMissingDependencies() {
        CelestialEnvironmentService environments = new CelestialEnvironmentService(new CelestialCatalogManager());
        CelestialGravityController.PositionGravity position = (level, pos) -> OptionalDouble.empty();
        CelestialGravityController.PlayerGravity player = ignored -> OptionalDouble.empty();
        assertDoesNotThrow(() -> new CelestialGravityController(environments, position, player, () -> false));
        assertThrows(NullPointerException.class,
                () -> new CelestialGravityController(null, position, player, () -> true));
        assertThrows(NullPointerException.class,
                () -> new CelestialGravityController(environments, null, player, () -> true));
        assertThrows(NullPointerException.class,
                () -> new CelestialGravityController(environments, position, null, () -> true));
        assertThrows(NullPointerException.class,
                () -> new CelestialGravityController(environments, position, player, null));
    }

    private static AttributeModifier ownedModifier(double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(CelestialGravityController.MODIFIER_ID, "ARCE residue fixture", amount, operation);
    }

    private static AttributeModifier foreignModifier(AttributeModifier.Operation operation, double amount) {
        return new AttributeModifier(UUID.randomUUID(), "foreign fixture", amount, operation);
    }

    private static AttributeInstance gravityAttribute() {
        return new AttributeInstance(
                new RangedAttribute("test.gravity", 0.08D, 0.0D, 1.0D),
                ignored -> {
                }
        );
    }
}
