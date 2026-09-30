package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Applies a position override (such as a station region, ADR-041) or else the active Level
 * profile through Forge's synchronized gravity attribute.
 */
public final class CelestialGravityController {
    public static final UUID MODIFIER_ID = UUID.fromString("6fef66cc-a721-4b58-9be5-c8b07831eb0f");
    private static final String MODIFIER_NAME = "ARCE celestial gravity";

    private final CelestialEnvironmentService environments;
    private final PositionGravity override;

    public CelestialGravityController(CelestialEnvironmentService environments) {
        this(environments, (level, position) -> OptionalDouble.empty());
    }

    public CelestialGravityController(CelestialEnvironmentService environments, PositionGravity override) {
        this.environments = environments;
        this.override = Objects.requireNonNull(override, "override");
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        OptionalDouble local = override.at(player.serverLevel(), player.blockPosition());
        double multiplier = local.isPresent() ? local.getAsDouble()
                : environments.forLevel(player.serverLevel().dimension())
                        .map(CelestialEnvironmentService.EnvironmentProfile::gravityMultiplier)
                        .orElse(1.0D);
        applyMultiplier(player.getAttribute(ForgeMod.ENTITY_GRAVITY.get()), multiplier);
    }

    /** Bounded, constant-time gravity for a position; empty falls back to the Level profile. */
    @FunctionalInterface
    public interface PositionGravity {
        OptionalDouble at(ServerLevel level, BlockPos position);
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
