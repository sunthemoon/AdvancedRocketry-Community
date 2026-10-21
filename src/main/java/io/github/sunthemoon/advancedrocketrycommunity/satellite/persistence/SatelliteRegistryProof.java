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
    }

    /** A directory-force failure is visible but cannot undo an S2-proven replacement. */
    public record Completion(Outcome outcome, Optional<Acknowledgement> acknowledgement, boolean directoryForceFailed) {
        public boolean proves(Watch watch) {
            return outcome == Outcome.COMMITTED && acknowledgement.filter(ack -> ack.proves(watch)).isPresent();
        }
    }
}
