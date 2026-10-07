package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Applies the current Level profile to living entities through Forge's gravity attribute.
 * Players retain area-field (ADR-058), position-override (ADR-041), then Level precedence.
 */
public final class CelestialGravityController {
    public static final UUID MODIFIER_ID = UUID.fromString("6fef66cc-a721-4b58-9be5-c8b07831eb0f");
    private static final String MODIFIER_NAME = "ARCE celestial gravity";

    private final CelestialEnvironmentService environments;
    private final PositionGravity override;
    private final PlayerGravity fields;
    private final BooleanSupplier livingGravityEnabled;

    public CelestialGravityController(CelestialEnvironmentService environments) {
        this(environments, (level, position) -> OptionalDouble.empty());
    }

    public CelestialGravityController(CelestialEnvironmentService environments, PositionGravity override) {
        this(environments, override, player -> OptionalDouble.empty());
    }

    /** ADR-058 section 4: the field layer goes in front of the position override; the override is unchanged. */
    public CelestialGravityController(CelestialEnvironmentService environments, PositionGravity override,
                                      PlayerGravity fields) {
        this(environments, override, fields, () -> true);
    }

    /** ADR-066: the switch controls only the new non-player Level gravity. */
    public CelestialGravityController(CelestialEnvironmentService environments, PositionGravity override,
                                      PlayerGravity fields, BooleanSupplier livingGravityEnabled) {
        this.environments = Objects.requireNonNull(environments, "environments");
        this.override = Objects.requireNonNull(override, "override");
        this.fields = Objects.requireNonNull(fields, "fields");
        this.livingGravityEnabled = Objects.requireNonNull(livingGravityEnabled, "livingGravityEnabled");
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return;
        }
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return;
        }
        double multiplier = !(entity instanceof ServerPlayer) && !livingGravityEnabled.getAsBoolean()
                ? 1.0D : multiplierAt(entity, level);
        applyMultiplier(gravity, multiplier);
    }

    private double multiplierAt(LivingEntity entity, ServerLevel level) {
        if (entity instanceof ServerPlayer player) {
            OptionalDouble field = fields.at(player);
            OptionalDouble local = field.isPresent() ? field : override.at(level, player.blockPosition());
            if (local.isPresent()) {
                return local.getAsDouble();
            }
        }
        return environments.forLevel(level.dimension())
                .map(CelestialEnvironmentService.EnvironmentProfile::gravityMultiplier)
                .orElse(1.0D);
    }

    /** A bounded, per-player layer (an area field the player is affected by); empty falls through. */
    @FunctionalInterface
    public interface PlayerGravity {
        OptionalDouble at(ServerPlayer player);
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
        AttributeModifier existing = gravity.getModifier(MODIFIER_ID);
        if (Double.compare(multiplier, 1.0D) == 0) {
            if (existing != null) {
                gravity.removeModifier(existing);
            }
            return;
        }
        double amount = multiplier - 1.0D;
        if (existing != null && Double.compare(existing.getAmount(), amount) == 0
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
            // False leaves a valid transient modifier untouched; true already removes permanent residue.
            if (!gravity.removePermanentModifier(MODIFIER_ID)) {
                return;
            }
        } else if (existing != null) {
            gravity.removeModifier(existing);
        }
        gravity.addTransientModifier(new AttributeModifier(
                MODIFIER_ID,
                MODIFIER_NAME,
                amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }
}
