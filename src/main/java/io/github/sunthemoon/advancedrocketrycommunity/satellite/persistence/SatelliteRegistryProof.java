package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Session-local registration evidence, independent of mutable or retained mission history. */
public final class SatelliteRegistryProof {
    private SatelliteRegistryProof() { }

    public enum Outcome { COMMITTED, FAILED, UNKNOWN }

    public record Identity(UUID satellite, UUID owner, ResourceLocation definition, SatelliteKind kind) {
        public static Identity of(SatelliteState state) {
            return new Identity(state.satelliteId(), state.ownerId(), state.definitionId(), state.kind());
        }
    }

    /** Only the registry that observed the identity can create a watch. Restart invalidates old watches. */
    public static final class Watch {
        private final Object session;
        private final long sequence;
        private final Identity identity;

        Watch(Object session, long sequence, Identity identity) {
            this.session = session; this.sequence = sequence; this.identity = identity;
        }
        public long sequence() { return sequence; }
        public Identity identity() { return identity; }
    }

    public static final class Acknowledgement {
        private final Object session;
        private final long sequence, epoch;
        private final Map<UUID, Identity> identities;

        Acknowledgement(Object session, long sequence, long epoch, Map<UUID, Identity> identities) {
            this.session = session; this.sequence = sequence; this.epoch = epoch;
            this.identities = Map.copyOf(identities);
        }
        public long sequence() { return sequence; }
        public long epoch() { return epoch; }
        public boolean proves(Watch watch) {
            return watch != null && session == watch.session && sequence >= watch.sequence
                    && watch.identity.equals(identities.get(watch.identity.satellite()));
        }

        /** ADR-074 item 6: an acknowledgement of the same session with s >= m covers a removal token. */
        public boolean covers(Token token) {
            return token != null && session == token.session && sequence >= token.sequence;
        }
    }

    /**
     * A removal token (session, m) for a change that leaves no identity to watch: decommission and the data actions.
     * The sequence is fixed when the change is made; a later poll never replaces it with a moving current sequence.
     */
    public static final class Token {
        private final Object session;
        private final long sequence;

        Token(Object session, long sequence) { this.session = session; this.sequence = sequence; }
        public long sequence() { return sequence; }
    }

    /**
     * ADR-074 item 6: a checkpoint request at sequence m that asks for the directory force (W119-4). It is answered by
     * the first published completion with s >= m that either forced the directory or did not commit; the fast path
     * answers at once. A request refused or discarded at shutdown is never answered. Owner thread only.
     */
    public static final class Checkpoint {
        private final long sequence;
        private final boolean force;
        private Completion answer;
        private boolean discarded;

        Checkpoint(long sequence, boolean force) { this.sequence = sequence; this.force = force; }

        static Checkpoint answered(long sequence, Completion completion) {
            var checkpoint = new Checkpoint(sequence, false);
            checkpoint.answer = completion;
            return checkpoint;
        }

        public long sequence() { return sequence; }
        public Optional<Completion> answer() { return Optional.ofNullable(answer); }
        public boolean discarded() { return discarded; }

        /** Whether this publication answered the request; a commit without the asked directory force does not. */
        boolean answer(long captured, Completion completion, boolean forced) {
            if (answer != null || discarded || captured < sequence
                    || force && !forced && completion.outcome() == Outcome.COMMITTED) { return false; }
            answer = completion;
            return true;
        }

        void discard() { if (answer == null) { discarded = true; } }
    }

    /** A directory-force failure is visible but cannot undo an S2-proven replacement. */
    public record Completion(Outcome outcome, Optional<Acknowledgement> acknowledgement, boolean directoryForceFailed) {
        public boolean proves(Watch watch) {
            return outcome == Outcome.COMMITTED && acknowledgement.filter(ack -> ack.proves(watch)).isPresent();
        }
    }
}
