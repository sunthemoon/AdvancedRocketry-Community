package io.github.sunthemoon.advancedrocketrycommunity.client.sound;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlock;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicLoopSelection.Candidate;
import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicLoopSelection.Key;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ProcessingSoundSource;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * ADR-066 section 7.1 classic sounds, physical client only. Loops follow state the server already synchronizes to
 * nearby clients (the vent's {@code LIT} block state, the gravity controller's and laser drill's {@code active}
 * update tag, processing controllers' {@code active} update tag, the rocket's synchronized flight state);
 * one-shots play on an accepted client effect. Nothing is sent
 * to the server. Every ten client ticks a bounded scan of the loaded chunks within 32 blocks of the listener, nearest
 * chunks first and at most 2,048 block entities and 64 rockets, offers the active sources to
 * {@link ClassicLoopSelection}; each playing loop checks its own source every tick. All loops stop at logout, Level
 * change, resource reload and CLIENT configuration reload. No server world, chunk loading or persistence is touched.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClassicSoundClient {
    static final int SCAN_INTERVAL_TICKS = 10;
    static final int MAX_EXAMINED_BLOCK_ENTITIES = 2_048;
    static final int MAX_ROCKETS = 64;
    /** One-shots end after 40 ticks at most, whatever the file length. */
    static final int ONE_SHOT_TICKS = 40;
    private static final int CHUNK_RADIUS = 2;
    private static final int MAX_MISSING_REPORTS = 10;
    private static final double STOP_SQUARED = ClassicLoopSelection.STOP_DISTANCE * ClassicLoopSelection.STOP_DISTANCE;
    private static final List<int[]> CHUNK_OFFSETS = chunkOffsets();
    private static final Map<ClassicSound, Integer> ONE_SHOT_CAPS = new EnumMap<>(Map.of(
            ClassicSound.RAILGUN_BANG, 8, ClassicSound.ELECTRIC_SHOCK_SMALL, 1, ClassicSound.BASIC_LASER_GUN, 4));

    private static WeakReference<ClientLevel> level = new WeakReference<>(null);
    private static final Map<Key, ClassicLoopSound> LOOPS = new HashMap<>();
    private static final List<OneShot> ONE_SHOTS = new ArrayList<>();
    private static final Set<ClassicSound> MISSING = EnumSet.noneOf(ClassicSound.class);
    private static long ticks;

    private record OneShot(SoundInstance instance, ClassicSound sound, long started) { }

    private ClassicSoundClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current == null || minecraft.player == null) {
            stopAll();
            return;
        }
        if (level.get() != current) {
            stopAll();
            level = new WeakReference<>(current);
        }
        if (minecraft.isPaused()) {
            return;
        }
        ticks++;
        var sounds = minecraft.getSoundManager();
        ONE_SHOTS.removeIf(shot -> {
            boolean expired = ticks - shot.started() >= ONE_SHOT_TICKS;
            if (expired) {
                sounds.stop(shot.instance());
            }
            return expired || !sounds.isActive(shot.instance());
        });
        LOOPS.values().removeIf(loop -> loop.isStopped() || !sounds.isActive(loop));
        if (ticks % SCAN_INTERVAL_TICKS == 0) {
            scan(minecraft, current);
        }
    }

    /**
     * Plays a one-shot for an accepted client effect at a position, within the event's range and under its
     * simultaneous cap; a one-shot over the cap is dropped, not queued.
     */
    public static void oneShot(ClassicSound sound, double x, double y, double z) {
        Integer cap = ONE_SHOT_CAPS.get(sound);
        Minecraft minecraft = Minecraft.getInstance();
        if (cap == null || minecraft.level == null || minecraft.level != level.get() || !minecraft.isSameThread()) {
            return;
        }
        Vec3 position = new Vec3(x, y, z);
        if (position.distanceToSqr(listener(minecraft)) > (double) sound.range() * sound.range()
                || ONE_SHOTS.stream().filter(shot -> shot.sound() == sound).count() >= cap || !available(minecraft, sound)) {
            return;
        }
        SoundInstance instance = new SimpleSoundInstance(ModSounds.classic(sound), sound.source(), 1.0F,
                0.9F + minecraft.level.random.nextFloat() * 0.2F, SoundInstance.createUnseededRandom(), x, y, z);
        minecraft.getSoundManager().play(instance);
        ONE_SHOTS.add(new OneShot(instance, sound, ticks));
    }

    /** The loop's current source position, or null once it must stop. Called by the loop every sound tick. */
    static Vec3 validPosition(ClassicLoopSound loop) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current == null || current != level.get() || LOOPS.get(loop.key()) != loop) {
            return null;
        }
        Vec3 position = sourcePosition(current, loop.key(), loop.sound());
        return position != null && position.distanceToSqr(listener(minecraft)) <= STOP_SQUARED ? position : null;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
        level = new WeakReference<>(null);
    }

    @SubscribeEvent
    public static void levelChanged(ClientPlayerNetworkEvent.Clone event) {
        stopAll();
    }

    private static void scan(Minecraft minecraft, ClientLevel current) {
        Vec3 listener = listener(minecraft);
        List<Candidate> candidates = new ArrayList<>();
        Map<Key, Vec3> positions = new HashMap<>();
        int chunkX = SectionPos.blockToSectionCoord(listener.x);
        int chunkZ = SectionPos.blockToSectionCoord(listener.z);
        int examined = 0;
        chunks:
        for (int[] offset : CHUNK_OFFSETS) {
            LevelChunk chunk = current.getChunkSource().getChunk(chunkX + offset[0], chunkZ + offset[1], false);
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (++examined > MAX_EXAMINED_BLOCK_ENTITIES) {
                    break chunks;
                }
                ClassicSound sound = activeLoop(blockEntity);
                if (sound == null) {
                    continue;
                }
                Vec3 position = Vec3.atCenterOf(blockEntity.getBlockPos());
                offer(candidates, positions, Key.block(blockEntity.getBlockPos().asLong()), sound, position, listener);
            }
        }
        List<RocketEntity> rockets = new ArrayList<>(current.getEntitiesOfClass(RocketEntity.class,
                new AABB(listener, listener).inflate(ClassicLoopSelection.STOP_DISTANCE), ClassicSoundClient::loud));
        rockets.sort(Comparator.comparingDouble(rocket -> rocket.position().distanceToSqr(listener)));
        for (RocketEntity rocket : rockets.subList(0, Math.min(MAX_ROCKETS, rockets.size()))) {
            offer(candidates, positions, Key.entity(rocket.getId()), ClassicSound.COMBUSTION_ROCKET,
                    rocket.position(), listener);
        }

        List<Candidate> selected = ClassicLoopSelection.select(candidates);
        Map<Key, ClassicSound> keep = new HashMap<>();
        selected.forEach(candidate -> keep.put(candidate.key(), candidate.sound()));
        LOOPS.entrySet().removeIf(entry -> {
            boolean dropped = keep.get(entry.getKey()) != entry.getValue().sound();
            if (dropped) {
                entry.getValue().end();
            }
            return dropped;
        });
        for (Candidate candidate : selected) {
            if (!LOOPS.containsKey(candidate.key()) && available(minecraft, candidate.sound())) {
                ClassicLoopSound loop = new ClassicLoopSound(candidate.sound(), candidate.key(),
                        positions.get(candidate.key()));
                LOOPS.put(candidate.key(), loop);
                minecraft.getSoundManager().play(loop);
            }
        }
    }

    private static void offer(List<Candidate> candidates, Map<Key, Vec3> positions, Key key, ClassicSound sound,
            Vec3 position, Vec3 listener) {
        double distance = position.distanceToSqr(listener);
        if (distance > STOP_SQUARED) {
            return;
        }
        ClassicLoopSound playing = LOOPS.get(key);
        candidates.add(new Candidate(key, sound, distance, playing != null && playing.sound() == sound));
        positions.put(key, position);
    }

    /** The loop a loaded block entity's synchronized state asks for, or null. */
    private static ClassicSound activeLoop(BlockEntity blockEntity) {
        if (blockEntity.isRemoved()) {
            return null;
        }
        if (blockEntity instanceof OxygenVentBlockEntity) {
            BlockState state = blockEntity.getBlockState();
            return state.hasProperty(OxygenVentBlock.LIT) && state.getValue(OxygenVentBlock.LIT)
                    ? ClassicSound.AIR_HISS_LOOP : null;
        }
        if (blockEntity instanceof GravityFieldBlockEntity field) {
            return field.active() ? ClassicSound.GRAVITY_CONTROLLER : null;
        }
        if (blockEntity instanceof OrbitalLaserDrillBlockEntity drill) {
            return drill.activeForRender() ? ClassicSound.LASER_DRILL : null;
        }
        if (blockEntity instanceof ProcessingSoundSource machine) {
            return machine.activeProcessingSound();
        }
        return null;
    }

    /** A rocket is loud while its synchronized flight state is ascent or descent. */
    private static boolean loud(RocketEntity rocket) {
        RocketFlightState state = rocket.displayedFlightState();
        return rocket.isAlive() && (state == RocketFlightState.ASCENT || state == RocketFlightState.DESCENT);
    }

    private static Vec3 sourcePosition(ClientLevel current, Key key, ClassicSound sound) {
        if (key.entity()) {
            Entity entity = current.getEntity((int) key.value());
            return entity instanceof RocketEntity rocket && loud(rocket) ? rocket.position() : null;
        }
        BlockPos position = BlockPos.of(key.value());
        LevelChunk chunk = current.getChunkSource().getChunk(SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()), false);
        BlockEntity blockEntity = chunk == null ? null
                : chunk.getBlockEntity(position, LevelChunk.EntityCreationType.CHECK);
        return blockEntity != null && activeLoop(blockEntity) == sound ? Vec3.atCenterOf(position) : null;
    }

    /** Whether the current resources define the event; a missing definition is reported a bounded number of times. */
    private static boolean available(Minecraft minecraft, ClassicSound sound) {
        if (minecraft.getSoundManager().getSoundEvent(sound.location()) != null) {
            return true;
        }
        if (MISSING.size() < MAX_MISSING_REPORTS && MISSING.add(sound)) {
            AdvancedRocketryCommunity.LOGGER.warn("Classic sound {} has no definition in the current resources",
                    sound.location());
        }
        return false;
    }

    private static Vec3 listener(Minecraft minecraft) {
        var camera = minecraft.gameRenderer.getMainCamera();
        return camera.isInitialized() ? camera.getPosition() : minecraft.player.getEyePosition();
    }

    private static void stopAll() {
        var sounds = Minecraft.getInstance().getSoundManager();
        LOOPS.values().forEach(ClassicLoopSound::end);
        LOOPS.clear();
        ONE_SHOTS.forEach(shot -> sounds.stop(shot.instance()));
        ONE_SHOTS.clear();
    }

    /** Chunk offsets within the radius, nearest first, so a truncated scan keeps the nearest sources. */
    private static List<int[]> chunkOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0]).thenComparingInt(offset -> offset[1]));
        return List.copyOf(offsets);
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                stopAll();
                MISSING.clear();
            });
        }

        @SubscribeEvent
        public static void configReloaded(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == ClientConfig.SPEC) {
                Minecraft.getInstance().execute(ClassicSoundClient::stopAll);
            }
        }

        private Setup() {
        }
    }
}
