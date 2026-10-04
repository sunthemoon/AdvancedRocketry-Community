package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import java.util.Optional;

/** Fixed-size observation only: no world, equipment, resource or persistence authority. */
public record AnalyzerReading(State state, Optional<String> bodyId, Optional<Locus> locus,
                              Optional<String> ambientBodyId, Optional<Ambient> ambient, boolean supplied) {
    public static final int MAX_ID_CHARS = 128;

    public AnalyzerReading {
        Objects.requireNonNull(state, "state");
        bodyId = Objects.requireNonNull(bodyId, "bodyId");
        locus = Objects.requireNonNull(locus, "locus");
        ambientBodyId = Objects.requireNonNull(ambientBodyId, "ambientBodyId");
        ambient = Objects.requireNonNull(ambient, "ambient");
        bodyId.ifPresent(AnalyzerReading::requireId);
        ambientBodyId.ifPresent(AnalyzerReading::requireId);
        boolean known = state == State.BREATHABLE || state == State.NON_BREATHABLE || state == State.PENDING;
        if (known != (bodyId.isPresent() && locus.isPresent() && ambientBodyId.isPresent() && ambient.isPresent())) {
            throw new IllegalArgumentException("Known readings require a complete context and paired ambient values");
        }
        if (!known && (bodyId.isPresent() || locus.isPresent() || ambientBodyId.isPresent() || ambient.isPresent())) {
            throw new IllegalArgumentException("Unavailable/disabled readings cannot carry partial metadata");
        }
        if (supplied && state != State.BREATHABLE) {
            throw new IllegalArgumentException("Only an authoritative breathable reading may claim supplied air");
        }
    }

    public static AnalyzerReading unavailable() { return empty(State.UNAVAILABLE); }
    public static AnalyzerReading disabled() { return empty(State.DISABLED); }

    private static AnalyzerReading empty(State state) {
        return new AnalyzerReading(state, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    static void requireId(String id) {
        if (id.length() > MAX_ID_CHARS || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid bounded body identifier");
        }
    }

    public enum State { BREATHABLE, NON_BREATHABLE, PENDING, UNAVAILABLE, DISABLED }
    public enum Locus { SURFACE, ORBIT }

    public record Ambient(double pressure, double temperatureKelvin) {
        public Ambient {
            if (!Double.isFinite(pressure) || pressure < 0.0D || pressure > 10.0D
                    || !Double.isFinite(temperatureKelvin) || temperatureKelvin < 0.0D || temperatureKelvin > 2_000.0D) {
                throw new IllegalArgumentException("Ambient values exceed the celestial finite bounds");
            }
        }
    }
}
