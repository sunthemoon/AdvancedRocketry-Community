package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.Context;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket.SkyKind;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-066 section 7.1: the black-hole sky's client state. The server alone chooses
 * {@link SkyKind#SINGULARITY} (ADR-065 section 11.1); this class only decides whether to show it and owns its
 * bounded particle accent. It supplements ADR-057's controller effect and attracts nothing. No Minecraft client
 * type is used, so it is tested without a client; {@code P} is the particle handle.
 */
final class SingularitySky<P> {
    static final int MAX_PARTICLES_PER_TICK = 8;
    /** Particles that may still be alive; a spawn beyond it is skipped, so the tracking stays bounded. */
    static final int MAX_LIVE_PARTICLES = 128;

    private final Handles<P> handles;
    private final ArrayDeque<P> live = new ArrayDeque<>();
    private Context shown;
    private int spawnedThisTick;

    SingularitySky(Handles<P> handles) {
        this.handles = Objects.requireNonNull(handles, "handles");
    }

    /**
     * The context to draw as a singularity, or empty for the existing station sky. It needs the sky switch,
     * a server SINGULARITY context and its body known to the client's celestial snapshot. A missing context,
     * any other kind or a body the client does not know keeps the existing planetary sky or its fallback.
     */
    static Optional<Context> resolve(Optional<Context> context, boolean enabled, Optional<ResourceLocation> knownBody) {
        if (!enabled) {
            return Optional.empty();
        }
        return context.filter(value -> value.kind() == SkyKind.SINGULARITY)
                .filter(value -> value.body().isPresent() && value.body().equals(knownBody));
    }

    /**
     * One client tick with the resolved context. A different context (kind, body or controls; a heartbeat
     * only moves the sample) or none removes the particles first. Returns whether a singularity is shown.
     */
    boolean tick(Optional<Context> resolved) {
        spawnedThisTick = 0;
        Context basis = resolved.map(Context::basis).orElse(null);
        if (!Objects.equals(basis, shown)) {
            clear();
            shown = basis;
        }
        live.removeIf(particle -> !handles.alive(particle));
        return shown != null;
    }

    /**
     * Spawns up to {@code requested} particles while a singularity is shown, never more than
     * {@value #MAX_PARTICLES_PER_TICK} attempts between two ticks or {@value #MAX_LIVE_PARTICLES} alive.
     * A spawn that yields nothing still counts. Returns the number spawned.
     */
    int emit(int requested, Supplier<P> spawner) {
        if (shown == null) {
            return 0;
        }
        int spawned = 0;
        while (spawned < requested && spawnedThisTick < MAX_PARTICLES_PER_TICK && live.size() < MAX_LIVE_PARTICLES) {
            spawnedThisTick++;
            P particle = spawner.get();
            if (particle == null) {
                continue;
            }
            live.add(particle);
            spawned++;
        }
        return spawned;
    }

    /** A Level change, a disconnect or a resource reload: removes the particles and forgets the context. */
    void clear() {
        for (P particle : live) {
            handles.remove(particle);
        }
        live.clear();
        shown = null;
    }

    Optional<Context> shown() {
        return Optional.ofNullable(shown);
    }

    int liveParticles() {
        return live.size();
    }

    interface Handles<P> {
        boolean alive(P particle);

        void remove(P particle);
    }
}
