package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalConditions;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalProtection;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.PlayerEnvironmentalService;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * ADR-066 section 3.3: atmosphere effects for non-player living entities, evaluated on each entity's own tick at
 * most once per {@link NonPlayerAtmosphere#INTERVAL} ticks, and the natural/spawner spawn admission. Players stay
 * with {@link PlayerLifeSupportService}. No entity or chunk is scanned and no per-entity state is kept.
 */
public final class NonPlayerAtmosphereService {
    /** Server entity-type tag, empty by default; it exempts these atmospheric effects only. */
    public static final TagKey<EntityType<?>> ATMOSPHERE_BYPASS =
            TagKey.create(Registries.ENTITY_TYPE, ModIdentity.id("atmosphere_bypass"));

    private final AtmosphereManager atmosphere;
    private final Predicate<EntityType<?>> bypass;
    private final DoubleSupplier vacuumDamage;

    public NonPlayerAtmosphereService(AtmosphereManager atmosphere) {
        this(atmosphere, type -> type.is(ATMOSPHERE_BYPASS), CommonConfig::vacuumDamage);
    }

    public NonPlayerAtmosphereService(AtmosphereManager atmosphere, Predicate<EntityType<?>> bypass,
                                      DoubleSupplier vacuumDamage) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.bypass = Objects.requireNonNull(bypass, "bypass");
        this.vacuumDamage = Objects.requireNonNull(vacuumDamage, "vacuumDamage");
    }

    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player) && entity.level() instanceof ServerLevel level
                && level.getServer().isSameThread()) {
            tick(entity, level);
        }
    }

    /** The attempt made on this entity tick; {@link NonPlayerAtmosphere.Attempt#NONE} outside its scheduled tick. */
    public NonPlayerAtmosphere.Attempt tick(LivingEntity entity) {
        if (entity instanceof Player || !(entity.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Non-player atmosphere requires a non-player entity on the logical server thread");
        }
        return tick(entity, level);
    }

    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.SPAWNER && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        Mob mob = event.getEntity();
        ServerLevel level = event.getLevel().getLevel();
        // Chunk generation passes a WorldGenRegion for a chunk the live atmosphere has not seen, possibly on a
        // world-generation thread: it uses the catalog only, so no room protects it.
        boolean live = type != MobSpawnType.CHUNK_GENERATION && event.getLevel() == level
                && level.getServer().isSameThread();
        if (!admit(mob, level, live)) {
            event.setResult(Event.Result.DENY);
        }
    }

    private NonPlayerAtmosphere.Attempt tick(LivingEntity entity, ServerLevel level) {
        if (!NonPlayerAtmosphere.scheduled(entity.tickCount, entity.getId()) || !eligible(entity)) {
            return NonPlayerAtmosphere.Attempt.NONE;
        }
        boolean base = atmosphere.baseAtmosphereBreathable(level);
        EnvironmentalConditions conditions = atmosphere.surfaceExposure(level);
        if (base && !conditions.enabled()) {
            return NonPlayerAtmosphere.Attempt.NONE;
        }
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        int hazards = conditions.enabled()
                ? EnvironmentalExposure.hazards(conditions, EnvironmentalProtection.read(entity),
                        atmosphere.controlledAt(level, eye), PlayerEnvironmentalService.directSunlight(level, eye))
                : 0;
        NonPlayerAtmosphere.Attempt attempt = NonPlayerAtmosphere.attempt(base,
                atmosphere.breathabilityAt(level, eye), hazards, vacuumDamage.getAsDouble());
        switch (attempt.kind()) {
            case VACUUM -> entity.hurt(ModDamageTypes.vacuum(level), attempt.damage());
            case ENVIRONMENTAL -> entity.hurt(ModDamageTypes.environmental(level, attempt.hazards()), attempt.damage());
            case NONE -> { }
        }
        return attempt;
    }

    /**
     * The vacuum damage type bypasses invulnerability, so an explicitly invulnerable entity is left alone, and an
     * armor stand, which treats such damage as a kill command, is not a breathing entity.
     */
    private boolean eligible(LivingEntity entity) {
        return entity.isAlive() && !entity.isInvulnerable() && !(entity instanceof ArmorStand)
                && !bypass.test(entity.getType());
    }

    private boolean admit(Mob mob, ServerLevel level, boolean live) {
        // A tagged bypass type passes this policy; vanilla spawn rules still decide.
        if (bypass.test(mob.getType())) {
            return true;
        }
        BlockPos eye = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
        boolean loaded = live && level.hasChunkAt(eye);
        boolean base = atmosphere.profileBreathable(level.dimension());
        Optional<BreathabilityState> state = loaded
                ? Optional.of(atmosphere.breathabilityAt(level, eye)) : Optional.empty();
        EnvironmentalConditions conditions = atmosphere.surfaceExposure(level);
        int hazards = 0;
        if (conditions.enabled()) {
            // Only a supplied sealed room protects a spawn; equipment is chosen later. Unknown sunlight is exposed.
            hazards = EnvironmentalExposure.hazards(conditions, EnvironmentalExposure.Protection.NONE,
                    loaded && atmosphere.controlledAt(level, eye),
                    !loaded || PlayerEnvironmentalService.directSunlight(level, eye));
        }
        return NonPlayerAtmosphere.admitSpawn(base, state, hazards);
    }
}
