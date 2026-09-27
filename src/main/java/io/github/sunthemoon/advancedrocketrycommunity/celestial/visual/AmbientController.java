package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import java.util.Objects;

/** One playback owner with a transition-independent rate budget. Tick only while unpaused. */
public final class AmbientController {
    private final AmbientCadence cadence = new AmbientCadence();
    private Object world;
    private SkySelection.Selection selected;

    public void tick(Object nextWorld, SkySelection.Selection next, boolean exposed, Playback playback) {
        if (nextWorld != world || !Objects.equals(next, selected) || !exposed) {
            playback.stop();
            world = nextWorld;
            selected = next;
        }
        boolean eligible = exposed && nextWorld != null && next != null
                && next.profile().ambientSound().isPresent() && next.profile().soundVolume() > 0;
        if (cadence.tick(eligible, playback.active())) {
            playback.stop();
            playback.play(next);
        }
    }

    public void reset(Playback playback) {
        playback.stop();
        world = null;
        selected = null;
    }

    public interface Playback {
        boolean active();
        void stop();
        void play(SkySelection.Selection selection);
    }
}
