package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/**
 * ADR-066 section 5.2: correctly worn {@code padded_landing_boots} cancel the fall itself on the server, never
 * another damage type and never the client's fall distance. {@code lowGravityBoots} narrows this to an applied
 * ARCE gravity multiplier below 1.
 */
public final class PaddedBoots {
    public static boolean cancels(ServerPlayer player) {
        return CommonConfig.classicEquipmentEnabled() && WornModules.installed(player, EquipmentSlot.FEET, ModuleKind.PADDED)
                && (!CommonConfig.lowGravityBoots() || gravityMultiplier(player) < 1.0);
    }

    /** The multiplier {@link CelestialGravityController} last applied; 1 when it applied none. */
    public static double gravityMultiplier(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        AttributeModifier applied = gravity == null ? null : gravity.getModifier(CelestialGravityController.MODIFIER_ID);
        return applied == null || applied.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL
                || !Double.isFinite(applied.getAmount()) ? 1.0 : 1.0 + applied.getAmount();
    }

    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide() && cancels(player)) {
            event.setCanceled(true);
        }
    }

    private PaddedBoots() { }
}
