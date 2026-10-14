package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.Context;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.SkyKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 section 7.1: the black-hole sky shows only for a server SINGULARITY context whose body the client
 * knows, follows the sky switch, spawns at most eight particles a tick and clears on a context change or reset.
 */
class SingularitySkyTest {
    private static final ResourceLocation CYGNUS = ModIdentity.id("cygnus_x1");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final StationOrbitalControls CONTROLS = new StationOrbitalControls(4, 270_000, 0, 0, 3, 0, -2, 100L);

    @Test void onlyAServerSingularityWithAKnownBodyAndTheSwitchOnIsShown() {
        var singularity = context(SkyKind.SINGULARITY, CYGNUS, 120L);
        assertEquals(Optional.of(singularity), SingularitySky.resolve(Optional.of(singularity), true, Optional.of(CYGNUS)));
        // The station sky switch off delegates to the dimension fallback.
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(singularity), false, Optional.of(CYGNUS)));
    }

    @Test void missingOtherOrUnknownContextsUseTheExistingSky() {
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.empty(), true, Optional.empty()));
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.empty(), true, Optional.of(CYGNUS)));
        var planetary = context(SkyKind.PLANETARY, CYGNUS, 120L);
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(planetary), true, Optional.of(CYGNUS)));
        var fallback = new Context(SkyKind.SPACE_FALLBACK, Optional.of(CYGNUS), CONTROLS, 120L);
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(fallback), true, Optional.of(CYGNUS)));
        var unnamed = new Context(SkyKind.SPACE_FALLBACK, Optional.empty(), CONTROLS, 120L);
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(unnamed), true, Optional.empty()));
        // A singularity body missing from the client's snapshot, or a different known body, is unknown here.
        var singularity = context(SkyKind.SINGULARITY, CYGNUS, 120L);
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(singularity), true, Optional.empty()));
        assertEquals(Optional.empty(), SingularitySky.resolve(Optional.of(singularity), true, Optional.of(EARTH)));
    }

    @Test void atMostEightParticlesAreSpawnedBetweenTwoTicks() {
        var particles = new Particles();
        var sky = new SingularitySky<>(particles);
        assertTrue(sky.tick(Optional.of(context(SkyKind.SINGULARITY, CYGNUS, 120L))));
        assertEquals(SingularitySky.MAX_PARTICLES_PER_TICK, sky.emit(1_000, particles::spawn));
        assertEquals(0, sky.emit(1, particles::spawn));
        assertEquals(8, particles.spawned.size());
        assertTrue(sky.tick(Optional.of(context(SkyKind.SINGULARITY, CYGNUS, 140L))));
        assertEquals(3, sky.emit(3, particles::spawn));
        assertEquals(5, sky.emit(Integer.MAX_VALUE, particles::spawn));
        assertEquals(0, sky.emit(Integer.MAX_VALUE, particles::spawn));
        assertEquals(16, sky.liveParticles());
        assertEquals(8, SingularitySky.MAX_PARTICLES_PER_TICK);
    }

    @Test void negativeAndFailedSpawnsCountWithoutLooping() {
        var particles = new Particles();
        var sky = new SingularitySky<>(particles);
        sky.tick(Optional.of(context(SkyKind.SINGULARITY, CYGNUS, 120L)));
        assertEquals(0, sky.emit(-5, particles::spawn));
        assertEquals(0, sky.emit(0, particles::spawn));
        int[] attempts = {0};
        assertEquals(0, sky.emit(1_000, () -> { attempts[0]++; return null; }));
        assertEquals(SingularitySky.MAX_PARTICLES_PER_TICK, attempts[0]);
        assertEquals(0, sky.emit(1, particles::spawn), "failed attempts use the tick's budget");
        assertEquals(0, sky.liveParticles());
    }

    @Test void nothingIsSpawnedWithoutAShownSingularity() {
        var particles = new Particles();
        var sky = new SingularitySky<>(particles);
        assertEquals(0, sky.emit(8, particles::spawn), "before the first tick");
        assertFalse(sky.tick(Optional.empty()));
        assertEquals(0, sky.emit(8, particles::spawn));
        assertTrue(particles.spawned.isEmpty());
    }

    @Test void liveParticlesStayBoundedAndExpiredOnesArePruned() {
        var particles = new Particles();
        var sky = new SingularitySky<>(particles);
        var context = Optional.of(context(SkyKind.SINGULARITY, CYGNUS, 120L));
        for (int tick = 0; tick < 40; tick++) {
            sky.tick(context);
            sky.emit(SingularitySky.MAX_PARTICLES_PER_TICK, particles::spawn);
            assertTrue(sky.liveParticles() <= SingularitySky.MAX_LIVE_PARTICLES);
        }
        assertEquals(SingularitySky.MAX_LIVE_PARTICLES, sky.liveParticles());
        assertEquals(SingularitySky.MAX_LIVE_PARTICLES, particles.spawned.size());
        particles.spawned.subList(0, 100).forEach(particle -> particle.alive = false);
        sky.tick(context);
        assertEquals(28, sky.liveParticles());
        assertEquals(8, sky.emit(8, particles::spawn));
        assertTrue(particles.spawned.stream().noneMatch(particle -> particle.removed), "expiry is not a removal");
    }

    @Test void aHeartbeatKeepsTheParticlesAndAContextChangeClearsThem() {
        var first = context(SkyKind.SINGULARITY, CYGNUS, 120L);
        var heartbeat = context(SkyKind.SINGULARITY, CYGNUS, 140L);
        var turned = new Context(SkyKind.SINGULARITY, Optional.of(CYGNUS),
                new StationOrbitalControls(4, 270_000, 0, 0, 0, 0, 0, 140L), 140L);
        var moved = new Context(SkyKind.SINGULARITY, Optional.of(CYGNUS),
                new StationOrbitalControls(60, 270_000, 0, 0, 3, 0, -2, 100L), 140L);
        var elsewhere = context(SkyKind.SINGULARITY, ModIdentity.id("other_hole"), 140L);
        for (var changed : List.of(turned, moved, elsewhere)) {
            var particles = new Particles();
            var sky = new SingularitySky<>(particles);
            sky.tick(Optional.of(first));
            sky.emit(4, particles::spawn);
            assertTrue(sky.tick(Optional.of(heartbeat)));
            assertEquals(4, sky.liveParticles(), "a heartbeat only moves the sample");
            assertEquals(Optional.of(first.basis()), sky.shown());
            assertTrue(sky.tick(Optional.of(changed)));
            assertEquals(0, sky.liveParticles());
            assertTrue(particles.spawned.stream().allMatch(particle -> particle.removed), changed.toString());
            assertEquals(Optional.of(changed.basis()), sky.shown());
        }
    }

    @Test void losingTheContextOrTheSwitchAndAResetClearEverything() {
        for (int path = 0; path < 3; path++) {
            var particles = new Particles();
            var sky = new SingularitySky<>(particles);
            var context = context(SkyKind.SINGULARITY, CYGNUS, 120L);
            sky.tick(Optional.of(context));
            sky.emit(6, particles::spawn);
            switch (path) {
                case 0 -> assertFalse(sky.tick(Optional.empty()), "NONE, another kind or an unknown body");
                case 1 -> assertFalse(sky.tick(SingularitySky.resolve(Optional.of(context), false, Optional.of(CYGNUS))),
                        "the station sky switch off");
                default -> sky.clear();
            }
            assertEquals(0, sky.liveParticles());
            assertEquals(Optional.empty(), sky.shown());
            assertEquals(6, particles.spawned.size());
            assertTrue(particles.spawned.stream().allMatch(particle -> particle.removed));
            assertEquals(0, sky.emit(8, particles::spawn), "nothing spawns until a singularity is shown again");
            assertTrue(sky.tick(Optional.of(context)), "the same context shows again after a reset");
            assertEquals(8, sky.emit(8, particles::spawn));
        }
    }

    @Test void handlesAreRequired() {
        assertThrows(NullPointerException.class, () -> new SingularitySky<FakeParticle>(null));
    }

    private static Context context(SkyKind kind, ResourceLocation body, long sample) {
        return new Context(kind, Optional.of(body), CONTROLS, sample);
    }

    private static final class FakeParticle {
        private boolean alive = true;
        private boolean removed;
    }

    /** Records every spawn and removal; no particle engine or client is created. */
    private static final class Particles implements SingularitySky.Handles<FakeParticle> {
        private final List<FakeParticle> spawned = new ArrayList<>();

        private FakeParticle spawn() {
            var particle = new FakeParticle();
            spawned.add(particle);
            return particle;
        }

        @Override public boolean alive(FakeParticle particle) { return particle.alive && !particle.removed; }

        @Override public void remove(FakeParticle particle) { particle.removed = true; }
    }
}
