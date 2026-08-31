package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Applies the active Level profile through Forge's synchronized gravity attribute. */
public final class CelestialGravityController {
    public static final UUID MODIFIER_ID = UUID.fromString("6fef66cc-a721-4b58-9be5-c8b07831eb0f");
    private static final String MODIFIER_NAME = "ARCE celestial gravity";

    private final CelestialEnvironmentService environments;

    public CelestialGravityController(CelestialEnvironmentService environments) {
        this.environments = environments;
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        double multiplier = environments.forLevel(player.serverLevel().dimension())
                .map(CelestialEnvironmentService.EnvironmentProfile::gravityMultiplier)
                .orElse(1.0D);
        applyMultiplier(player.getAttribute(ForgeMod.ENTITY_GRAVITY.get()), multiplier);
    }

    static void applyMultiplier(AttributeInstance gravity, double multiplier) {
        if (gravity == null) {
            return;
        }
        if (!Double.isFinite(multiplier)
                || multiplier < 0.0D
                || multiplier > CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER) {
            throw new IllegalArgumentException("Gravity multiplier is outside the celestial model bounds");
        }
        double amount = multiplier - 1.0D;
        AttributeModifier existing = gravity.getModifier(MODIFIER_ID);
        if (existing != null && Double.compare(existing.getAmount(), amount) == 0) {
            return;
        }
        if (existing != null) {
            gravity.removeModifier(existing);
        }
        if (Double.compare(multiplier, 1.0D) != 0) {
            gravity.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID,
                    MODIFIER_NAME,
                    amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }
}
