package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/**
 * Applies the current Level profile to living entities through Forge's gravity attribute, and scales their falls
 * by the same applied multiplier (ADR-066 section 3.3).
 * Players retain area-field (ADR-058), position-override (ADR-041), then Level precedence.
 */
public final class CelestialGravityController {
    public static final UUID MODIFIER_ID = UUID.fromString("6fef66cc-a721-4b58-9be5-c8b07831eb0f");
    private static final String MODIFIER_NAME = "ARCE celestial gravity";
    // Forge 1.20.1's native slow-falling attribute modifier, installed by LivingEntity.travel.
    static final UUID SLOW_FALLING_ID = UUID.fromString("a5b6cf2a-2f7c-31ef-9022-7c3e7d5e6aba");
    // Transient only: no saved identity or migration. Keep the body modifier separate for fall-damage scaling.
    static final UUID SLOW_FALL_FLOOR_ID = UUID.fromString("9d9af356-3dea-4c82-92ab-52e9d092201a");
    static final double MIN_SLOW_FALL_GRAVITY = 0.0031D;

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
        applySlowFallFloor(gravity, multiplier > 0.0D && multiplier < 1.0D
                && entity.hasEffect(MobEffects.SLOW_FALLING) && entity.getDeltaMovement().y <= 0.0D
                && !entity.isNoGravity() && !entity.isInWaterOrBubble() && !entity.isInLava());
    }

    /**
     * Lift only positive, scaled slow-fall gravity above aiStep's 0.003 dead zone, including travel's 0.98 drag.
     * The native modifier can appear after our first tick; the next tick corrects it without touching foreign
     * modifiers, the base, or the body's fall-damage factor. Zero gravity and ordinary/rising travel are unchanged.
     */
    static void applySlowFallFloor(AttributeInstance gravity, boolean active) {
        AttributeModifier existing = gravity.getModifier(SLOW_FALL_FLOOR_ID);
        if (existing != null && (existing.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL
                || !Double.isFinite(existing.getAmount()) || existing.getAmount() < 0.0D)) {
            gravity.removeModifier(existing);
            existing = null;
        }
        if (!active || gravity.getModifier(SLOW_FALLING_ID) == null) {
            if (existing != null) { gravity.removeModifier(existing); }
            return;
        }
        double scaled = existing == null ? gravity.getValue() : gravityWithoutFloor(gravity);
        double amount = MIN_SLOW_FALL_GRAVITY / scaled - 1.0D;
        if (scaled <= 0.0D || scaled >= MIN_SLOW_FALL_GRAVITY || !Double.isFinite(amount)) {
            if (existing != null) { gravity.removeModifier(existing); }
            return;
        }
        if (existing != null && Math.abs(existing.getAmount() - amount) <= 1.0E-12D * Math.max(1.0D, amount)
                && !gravity.removePermanentModifier(SLOW_FALL_FLOOR_ID)) {
            return; // Do not dirty/synchronize an unchanged attribute every tick due to round-off.
        }
        if (existing != null) { gravity.removeModifier(existing); }
        gravity.addTransientModifier(new AttributeModifier(SLOW_FALL_FLOOR_ID, "ARCE slow-fall gravity floor", amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Attribute operations without our floor. Dividing getValue loses information when the attribute was clamped. */
    private static double gravityWithoutFloor(AttributeInstance gravity) {
        double base = gravity.getBaseValue();
        for (var modifier : gravity.getModifiers(AttributeModifier.Operation.ADDITION)) { base += modifier.getAmount(); }
        double value = base;
        for (var modifier : gravity.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) { value += base * modifier.getAmount(); }
        for (var modifier : gravity.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) {
            if (!modifier.getId().equals(SLOW_FALL_FLOOR_ID)) { value *= 1.0D + modifier.getAmount(); }
        }
        return gravity.getAttribute().sanitizeValue(value);
    }

    /**
     * Leaf B: scales a landing once, before vanilla computes the damage, by the multiplier {@link #onLivingTick}
     * applied. A passenger with a living entity anywhere below it already received that entity's scaled distance
     * through {@code Entity.causeFallDamage}, so it is left alone (W38 design note). The switch covers every entity.
     */
    public void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.isCanceled() || !(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !livingGravityEnabled.getAsBoolean() || ridesLivingEntity(entity)) {
            return;
        }
        double multiplier = appliedMultiplier(entity);
        if (Double.compare(multiplier, 1.0D) == 0) {
            return;
        }
        float scaled = scaledFallDistance(event.getDistance(), multiplier);
        if (!Float.isNaN(scaled)) {
            event.setDistance(scaled);
        }
    }

    /** The multiplier this controller last applied to the entity's gravity; 1 when it applied none. */
    public static double appliedMultiplier(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        AttributeModifier applied = gravity == null ? null : gravity.getModifier(MODIFIER_ID);
        return applied == null || applied.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL
                || !Double.isFinite(applied.getAmount()) ? 1.0D : 1.0D + applied.getAmount();
    }

    /**
     * {@code distance * multiplier} in double precision, saturated at {@link Float#MAX_VALUE}. NaN, meaning "leave the
     * event unchanged", when the distance is not finite and non-negative or the multiplier is outside the model bounds.
     */
    public static float scaledFallDistance(float distance, double multiplier) {
        if (!Float.isFinite(distance) || distance < 0.0F || !Double.isFinite(multiplier) || multiplier < 0.0D
                || multiplier > CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER) {
            return Float.NaN;
        }
        return (float) Math.min(distance * multiplier, Float.MAX_VALUE);
    }

    /** Vehicle chains are acyclic: {@code Entity.startRiding} refuses a vehicle that the rider already carries. */
    private static boolean ridesLivingEntity(Entity entity) {
        for (Entity vehicle = entity.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
            if (vehicle instanceof LivingEntity) {
                return true;
            }
        }
        return false;
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
