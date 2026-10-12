package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * ADR-066 section 5.2: a transient +20% movement-speed modifier while sprinting in correctly worn leggings with
 * {@code bionic_leg_upgrade}. Only this fixed UUID is ever added or removed, so other mods' modifiers are kept.
 */
public final class BionicLegs {
    public static final UUID MODIFIER_ID = UUID.fromString("5b0c7f3e-5e0d-4d0b-9d4a-6a1f1c2b7e31");
    public static final double BONUS = 0.2;
    private static final String NAME = "ARCE bionic leg sprint";

    /** Adds or removes only the owned modifier; returns whether it is present afterwards. */
    public static boolean apply(AttributeInstance speed, boolean wanted) {
        if (speed == null) { return false; }
        AttributeModifier existing = speed.getModifier(MODIFIER_ID);
        if (!wanted) {
            if (existing != null) { speed.removeModifier(MODIFIER_ID); }
            return false;
        }
        if (existing != null && Double.compare(existing.getAmount(), BONUS) == 0
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
                // False keeps a valid transient copy; true has just removed a permanent one, which is replaced.
                && !speed.removePermanentModifier(MODIFIER_ID)) {
            return true;
        }
        if (speed.getModifier(MODIFIER_ID) != null) { speed.removeModifier(MODIFIER_ID); }
        speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, NAME, BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        return true;
    }

    /** The equipment check runs only while sprinting, so idle players cost one map lookup. */
    public static boolean wanted(ServerPlayer player) {
        return player.isAlive() && player.isSprinting() && CommonConfig.classicEquipmentEnabled()
                && WornModules.installed(player, EquipmentSlot.LEGS, ModuleKind.BIONIC);
    }

    public static boolean update(ServerPlayer player) {
        return apply(player.getAttribute(Attributes.MOVEMENT_SPEED), wanted(player));
    }

    private BionicLegs() { }
}
