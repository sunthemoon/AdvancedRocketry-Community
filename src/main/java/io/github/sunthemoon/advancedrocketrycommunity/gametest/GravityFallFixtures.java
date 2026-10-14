package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Shared W38 fixtures: a landing column, native falls, the vanilla damage formulas and a fall-event probe. */
final class GravityFallFixtures {
    /** Fall distance a player's server fall builds; vanilla adds no distance on the landing move itself. */
    static final int HEIGHT = 40;
    static final double MOON = 0.165D;
    static final float HEALTH = 100.0F;
    private static final int LANDING_Y = 180;
    private static final int HIGHEST_LANDING_Y = 270;
    private static final int FALL_TICKS = 600;

    private GravityFallFixtures() {
    }

    static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key);
        return level;
    }

    /** A stone block in this test's own column, high above the structures, with clear air above it; undo with clear. */
    static BlockPos landing(GameTestHelper helper, ServerLevel level) {
        BlockPos test = helper.absolutePos(new BlockPos(2, 2, 2));
        // Explicit fixture setup, not production fall loading. ServerPlayer.doCheckFallDamage returns before any fall
        // check while the player's box, inflated by one block, touches an unloaded chunk (W37G), so the neighbouring
        // chunks are loaded too. Otherwise the result depends on where the batch runner places this test.
        ChunkPos centre = new ChunkPos(test);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(centre.x + dx, centre.z + dz);
            }
        }
        // Generated terrain can reach this high, so take the lowest open column in a bounded range.
        for (int y = LANDING_Y; y <= HIGHEST_LANDING_Y; y++) {
            BlockPos pos = new BlockPos(test.getX(), y, test.getZ());
            if (open(level, pos)) {
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                return pos;
            }
        }
        throw new GameTestAssertException("No open fall column in " + level.dimension().location() + " above " + test);
    }

    private static boolean open(ServerLevel level, BlockPos landing) {
        for (int dy = 0; dy <= HEIGHT + 3; dy++) {
            if (!level.getBlockState(landing.above(dy)).isAir()) {
                return false;
            }
        }
        return true;
    }

    static void clear(ServerLevel level, BlockPos landing) {
        if (landing != null) { level.setBlockAndUpdate(landing, Blocks.AIR.defaultBlockState()); }
    }

    /** Feet position {@code height} blocks above the landing block's top face. */
    static Vec3 above(BlockPos landing, double height) {
        return new Vec3(landing.getX() + 0.5D, landing.getY() + 1.0D + height, landing.getZ() + 0.5D);
    }

    /** Where every fall here starts: one block above {@link #HEIGHT}, so the distance before landing is about HEIGHT. */
    static Vec3 start(BlockPos landing) {
        return above(landing, HEIGHT + 1);
    }

    /** Raises maximum health so that every fall here is survived and the health lost is the whole damage. */
    static <T extends LivingEntity> T sturdy(T entity) {
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        heal(entity);
        return entity;
    }

    /** Full health, no damage cooldown and no fall distance, so the next hurt counts in full. */
    static void heal(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
        entity.invulnerableTime = 0;
        entity.resetFallDistance();
    }

    static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }

    /** The registered tick listeners, as leaf A's tests post them: here they apply the production gravity. */
    static void registeredTick(LivingEntity entity) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(entity));
    }

    /** Native travel from rest at {@code start} until the entity lands; true when it landed within the bound. */
    static boolean travelUntilLanded(LivingEntity entity, Vec3 start) {
        rest(entity, start);
        for (int tick = 0; tick < FALL_TICKS && !entity.onGround(); tick++) {
            entity.travel(Vec3.ZERO);
        }
        return entity.onGround();
    }

    /** Full native entity ticks from {@code start} and {@code motion} until it lands; true when it landed in time. */
    static boolean tickUntilLanded(Entity entity, Vec3 start, Vec3 motion) {
        rest(entity, start);
        entity.setDeltaMovement(motion);
        for (int tick = 0; tick < FALL_TICKS && !entity.onGround(); tick++) {
            entity.tick();
        }
        return entity.onGround();
    }

    private static void rest(Entity entity, Vec3 start) {
        entity.setPos(start);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
        entity.resetFallDistance();
    }

    /**
     * The server's own fall routine for a player, {@code ServerPlayer.doCheckFallDamage}, called exactly as
     * {@code handleMovePlayer} calls it after accepting a move: {@link #HEIGHT} one-block airborne moves, then the
     * landing move onto the block.
     */
    static void serverFall(ServerPlayer player, BlockPos landing) {
        Vec3 top = start(landing);
        player.resetFallDistance();
        player.setPos(top);
        for (int step = HEIGHT; step >= 0; step--) {
            player.setPos(top.x, landing.getY() + 1.0D + step, top.z);
            player.doCheckFallDamage(0.0D, -1.0D, 0.0D, step == 0);
        }
    }

    /** {@code LivingEntity.calculateFallDamage} at multiplier 1 without jump boost; 0 when vanilla does not hurt. */
    static int livingDamage(float distance) {
        return Math.max(0, Mth.ceil(distance - 3.0F));
    }

    /** {@code AbstractHorse.calculateFallDamage} at multiplier 1. */
    static int horseDamage(float distance) {
        return Math.max(0, Mth.ceil(distance * 0.5F - 3.0F));
    }

    /** The distance after {@code times} applications of the production scaling. */
    static float scaled(float distance, double factor, int times) {
        float result = distance;
        for (int i = 0; i < times; i++) {
            result = CelestialGravityController.scaledFallDistance(result, factor);
        }
        return result;
    }

    /** The health lost matches one scaling of {@code incoming}, and zero or two scalings would have differed. */
    static void assertOneScaling(GameTestHelper helper, LivingEntity entity, float incoming, double factor, String what) {
        int once = livingDamage(scaled(incoming, factor, 1));
        helper.assertTrue(once != livingDamage(incoming) && once != livingDamage(scaled(incoming, factor, 2)),
                what + ": the fixture cannot tell one scaling from zero or two");
        expect(helper, lost(entity), once, what + " damage is not exactly one scaling of " + incoming);
    }

    static void expect(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6D, message + ": " + actual + " != " + expected);
    }

    /** One LivingFallEvent as the first and the last listener saw it; vanilla uses {@code outgoing}. */
    record Fall(float incoming, float outgoing, boolean canceled) {
    }

    /** Records the LivingFallEvents of the watched entities before and after every other listener, canceled or not. */
    static final class FallProbe implements AutoCloseable {
        private final Set<Entity> watched = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Entity, Float> pending = new IdentityHashMap<>();
        private final Map<Entity, List<Fall>> falls = new IdentityHashMap<>();
        private final Consumer<LivingFallEvent> first = event -> {
            if (watched.contains(event.getEntity())) { pending.put(event.getEntity(), event.getDistance()); }
        };
        private final Consumer<LivingFallEvent> last = event -> {
            Float incoming = pending.remove(event.getEntity());
            if (incoming != null) {
                falls.computeIfAbsent(event.getEntity(), ignored -> new ArrayList<>())
                        .add(new Fall(incoming, event.getDistance(), event.isCanceled()));
            }
        };

        FallProbe(Entity... entities) {
            watched.addAll(List.of(entities));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, LivingFallEvent.class, first);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingFallEvent.class, last);
        }

        List<Fall> of(Entity entity) {
            return List.copyOf(falls.getOrDefault(entity, List.of()));
        }

        /** The one event the entity has seen since the last {@link #reset}; fails the test otherwise. */
        Fall only(GameTestHelper helper, Entity entity, String what) {
            List<Fall> seen = of(entity);
            helper.assertTrue(seen.size() == 1, what + " saw " + seen.size() + " fall events: " + seen);
            return seen.get(0);
        }

        void reset() {
            pending.clear();
            falls.clear();
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(first);
            MinecraftForge.EVENT_BUS.unregister(last);
        }
    }
}
