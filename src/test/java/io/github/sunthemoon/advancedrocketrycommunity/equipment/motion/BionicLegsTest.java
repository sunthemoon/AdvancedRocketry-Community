package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

class BionicLegsTest {
    private static final UUID FOREIGN = UUID.fromString("8d6a0c1e-0000-4000-8000-000000000001");

    private static AttributeInstance speed() {
        return new AttributeInstance(new RangedAttribute("test.movement_speed", 0.1, 0, 1024), instance -> { });
    }

    @Test void addsOneTransientTwentyPercentModifierAndRemovesOnlyIt() {
        AttributeInstance speed = speed();
        AttributeModifier foreign = new AttributeModifier(FOREIGN, "other mod", 0.5, AttributeModifier.Operation.ADDITION);
        speed.addPermanentModifier(foreign);
        assertTrue(BionicLegs.apply(speed, true));
        AttributeModifier owned = speed.getModifier(BionicLegs.MODIFIER_ID);
        assertNotNull(owned);
        assertEquals(0.2, owned.getAmount());
        assertEquals(AttributeModifier.Operation.MULTIPLY_TOTAL, owned.getOperation());
        assertFalse(speed.getModifiers().isEmpty());
        assertFalse(speed.removePermanentModifier(BionicLegs.MODIFIER_ID), "the owned modifier is transient");
        assertEquals((0.1 + 0.5) * 1.2, speed.getValue(), 1.0E-12);
        assertTrue(BionicLegs.apply(speed, true), "idempotent");
        assertEquals(2, speed.getModifiers().size());
        assertFalse(BionicLegs.apply(speed, false));
        assertNull(speed.getModifier(BionicLegs.MODIFIER_ID));
        assertSame(foreign, speed.getModifier(FOREIGN), "other mods' modifiers are kept");
        assertEquals(0.6, speed.getValue(), 1.0E-12);
        assertFalse(BionicLegs.apply(speed, false), "removing twice is harmless");
    }

    @Test void aStaleOrPermanentCopyIsReplacedByTheTransientOne() {
        AttributeInstance speed = speed();
        speed.addPermanentModifier(new AttributeModifier(BionicLegs.MODIFIER_ID, "stale", 0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        assertTrue(BionicLegs.apply(speed, true));
        assertFalse(speed.removePermanentModifier(BionicLegs.MODIFIER_ID), "a permanent copy would be saved");
        assertNotNull(speed.getModifier(BionicLegs.MODIFIER_ID));
        speed.removeModifier(BionicLegs.MODIFIER_ID);
        speed.addTransientModifier(new AttributeModifier(BionicLegs.MODIFIER_ID, "wrong", 0.9,
                AttributeModifier.Operation.ADDITION));
        assertTrue(BionicLegs.apply(speed, true));
        assertEquals(0.2, speed.getModifier(BionicLegs.MODIFIER_ID).getAmount());
        assertFalse(BionicLegs.apply(null, true), "an entity without the attribute");
    }
}
