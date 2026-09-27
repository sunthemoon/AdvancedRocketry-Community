package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import org.junit.jupiter.api.Test;

class AmbientControllerTest {
    @Test void minimumCadenceAndOneActivePlaybackAreEnforced() {
        var controller = new AmbientController();
        var output = new Playback();
        var world = new Object();
        for (int tick = 0; tick < 399; tick++) { controller.tick(world, selection("mars"), true, output); }
        assertEquals(0, output.starts);
        controller.tick(world, selection("mars"), true, output);
        assertEquals(1, output.starts);
        for (int tick = 0; tick < 1000; tick++) { controller.tick(world, selection("mars"), true, output); }
        assertEquals(1, output.starts);
        output.playing = false;
        controller.tick(world, selection("mars"), true, output);
        assertEquals(2, output.starts);
        assertEquals(1, output.maximumPlaying);
    }

    @Test void worldProfileCoverLogoutAndReloadTransitionsStopWithoutResettingBudget() {
        var controller = new AmbientController();
        var output = new Playback();
        var world = new Object();
        for (int tick = 0; tick < 400; tick++) { controller.tick(world, selection("mars"), true, output); }
        assertTrue(output.playing);
        controller.tick(world, selection("venus"), true, output);
        assertFalse(output.playing);
        for (int tick = 0; tick < 100; tick++) {
            controller.reset(output);
            controller.tick(new Object(), selection(tick % 2 == 0 ? "mars" : "venus"), true, output);
        }
        assertEquals(1, output.starts);
        for (int tick = 0; tick < 299; tick++) { controller.tick(world, selection("venus"), true, output); }
        assertEquals(2, output.starts);
        controller.tick(world, selection("venus"), false, output);
        assertFalse(output.playing);
        controller.reset(output);
        assertFalse(output.playing);
        controller.tick(null, null, false, output);
        assertEquals(2, output.starts);
    }

    @Test void AirlessAndUnavailableProfilesNeverStartAdditionalAudio() {
        var controller = new AmbientController();
        var output = new Playback();
        var world = new Object();
        for (int tick = 0; tick < 1000; tick++) {
            controller.tick(world, selection(tick % 2 == 0 ? "moon" : "space"), true, output);
            controller.tick(world, null, false, output);
        }
        assertEquals(0, output.starts);
        controller.tick(world, selection("mars"), true, output);
        assertEquals(1, output.starts);
    }

    private static SkySelection.Selection selection(String name) {
        return new SkySelection.Selection(ModIdentity.id(name), ModIdentity.id(name),
                SkyProfiles.builtins().get(ModIdentity.id(name)), 1, 1);
    }

    private static final class Playback implements AmbientController.Playback {
        private boolean playing;
        private int starts;
        private int maximumPlaying;
        @Override public boolean active() { return playing; }
        @Override public void stop() { playing = false; }
        @Override public void play(SkySelection.Selection selected) {
            assertFalse(playing, "A second sound started while one was active");
            playing = true;
            starts++;
            maximumPlaying = 1;
        }
    }
}
