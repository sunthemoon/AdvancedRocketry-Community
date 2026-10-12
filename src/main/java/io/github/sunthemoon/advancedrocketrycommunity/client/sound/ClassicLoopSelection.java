package io.github.sunthemoon.advancedrocketrycommunity.client.sound;

import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which classic loops a client plays (ADR-066 section 7.1). Pure and Minecraft-free: at most {@link #MAX_LOOPS}
 * sources, nearest first; a source starts only within {@link #START_DISTANCE} blocks and a playing one stops beyond
 * {@link #STOP_DISTANCE}, so a listener standing between the two neither starts nor restarts it repeatedly.
 */
public final class ClassicLoopSelection {
    public static final int MAX_LOOPS = 32;
    public static final double START_DISTANCE = 30.0;
    public static final double STOP_DISTANCE = 32.0;
    /** Candidates considered per selection; the adapter's scan budget keeps it far below this. */
    public static final int MAX_CANDIDATES = 4_096;

    private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::distanceSquared)
            .thenComparing(Candidate::sound)
            .thenComparing(Candidate::key);

    private ClassicLoopSelection() {
    }

    /** A loaded source: a block position or an entity in the current client Level. */
    public record Key(boolean entity, long value) implements Comparable<Key> {
        public static Key block(long packedPosition) { return new Key(false, packedPosition); }
        public static Key entity(int entityId) { return new Key(true, entityId); }

        @Override
        public int compareTo(Key other) {
            int kind = Boolean.compare(entity, other.entity);
            return kind != 0 ? kind : Long.compare(value, other.value);
        }
    }

    /** An active source, its squared distance from the listener and whether its loop is already playing. */
    public record Candidate(Key key, ClassicSound sound, double distanceSquared, boolean playing) {
        public Candidate {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(sound, "sound");
            if (!(distanceSquared >= 0.0)) {
                throw new IllegalArgumentException("Distance must be finite and not negative");
            }
            if (!sound.loop()) {
                throw new IllegalArgumentException(sound.id() + " is not a loop");
            }
        }

        boolean eligible() {
            double limit = playing ? STOP_DISTANCE : START_DISTANCE;
            return distanceSquared <= limit * limit;
        }
    }

    /** The loops to play, nearest first; one per key; deterministic for any input order. */
    public static List<Candidate> select(List<Candidate> candidates) {
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Too many loop candidates: " + candidates.size());
        }
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.eligible()) {
                eligible.add(candidate);
            }
        }
        eligible.sort(ORDER);
        List<Candidate> selected = new ArrayList<>(Math.min(MAX_LOOPS, eligible.size()));
        Set<Key> keys = new HashSet<>();
        for (Candidate candidate : eligible) {
            if (selected.size() == MAX_LOOPS) {
                break;
            }
            if (keys.add(candidate.key())) {
                selected.add(candidate);
            }
        }
        return List.copyOf(selected);
    }
}
