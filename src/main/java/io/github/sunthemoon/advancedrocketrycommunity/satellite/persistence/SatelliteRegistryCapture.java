package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistrySections;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/**
 * ADR-074 items 2 and 3: the satellite registry's immutable capture and its IO side. The owner thread copies at most
 * 20,480 record references and four scalars and builds no NBT. The IO side sorts, encodes byte-identically to
 * {@code save(CompoundTag)}, checks the 16 MiB bound, replaces through the policy, reads back after a committer throw
 * within the same attempt and forces the directory only when a request asked for it. It reads nothing but the capture.
 */
final class SatelliteRegistryCapture {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;

    private SatelliteRegistryCapture() { }

    /**
     * The request attributes: the committer policy, of which a later request's replaces an earlier one, and the
     * directory force, which only a checkpoint asks for (W119-4) and which merging keeps. A null force asks none.
     */
    record Attributes(CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force) {
        Attributes {
            Objects.requireNonNull(policy, "policy");
            if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong registry write policy"); }
        }

        Attributes merge(Attributes later) {
            return new Attributes(later.policy, later.force != null ? later.force : force);
        }

        /** An attempt made with these attributes serviced a request for {@code requested}. */
        boolean services(Attributes requested) {
            return policy.equals(requested.policy) && (requested.force == null || force != null);
        }
    }

    /**
     * One attempt's immutable view: the mutation sequence s, the state-change count, the carried epoch w and the record
     * sections, or for a blocked registry its private preserved root, shared read-only and copied only on the IO side.
     * {@code cut} is the release-test CAPTURE seam; production captures carry null.
     */
    record Capture(long sequence, long stateSequence, long epoch, RegistrySections sections, CompoundTag blocked,
                   Runnable cut) {
        Capture {
            if ((sections == null) == (blocked == null)) {
                throw new IllegalArgumentException("A registry capture holds either sections or a blocked root");
            }
        }

        boolean operational() { return blocked == null; }
    }

    /** The identities that a committed capture acknowledges, built on the IO side, and the directory force's result. */
    record Result(Map<UUID, SatelliteRegistryProof.Identity> identities, boolean directoryForceFailed,
                  RuntimeException failure) { }

    /** Byte-identical to {@code save(CompoundTag)} at the captured state; a root over 16 MiB is refused here. */
    static CompoundTag encode(Capture capture) {
        if (!capture.operational()) { return capture.blocked().copy(); }
        RegistrySections sections = capture.sections();
        CompoundTag target = SatelliteRegistryPayload.encodeSorted(sections.logicalGameTime(),
                sections.lastObservedGameTime(), capture.epoch(), sorted(sections.satellites(), SatelliteState::satelliteId),
                sorted(sections.missions(), MissionState::missionId), sorted(sections.accounts(), ResearchAccount::ownerId),
                sorted(sections.instances(), AsteroidInstance::instanceId),
                sorted(sections.groundSurveys(), GroundSurveyJob::jobId), new CompoundTag());
        if (SatelliteNbtSize.uncompressedBytes(target) > SatelliteLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalStateException("Encoded satellite registry exceeds its fixed NBT bound");
        }
        return target;
    }

    /**
     * The IO side of one attempt, with W119's outcome rules unchanged: a failure before the committer is FAILED; after a
     * committer throw, a readable file equal to the candidate is COMMITTED, a different one FAILED and an unreadable one
     * UNKNOWN. A failed directory force is reported and leaves COMMITTED.
     */
    static CheckedRootWriter.Attempt<Result> write(Capture capture, Attributes attributes, Path file) {
        if (capture.cut() != null) { capture.cut().run(); }
        Map<UUID, SatelliteRegistryProof.Identity> identities = identities(capture);
        CompoundTag candidate;
        try {
            candidate = encode(capture);
        } catch (RuntimeException beforeReplacement) {
            return new CheckedRootWriter.Attempt<>(CheckedRootWriter.Status.FAILED,
                    new Result(identities, false, beforeReplacement));
        }
        CheckedSavedDataPolicy policy = attributes.policy();
        boolean[] attempted = {false};
        CheckedRootWriter.Status status;
        RuntimeException failure = null;
        try {
            policy.withCommitter((staged, target) -> {
                attempted[0] = true;
                policy.committer().commit(staged, target);
            }).replace(file, () -> candidate);
            status = CheckedRootWriter.Status.COMMITTED;
        } catch (RuntimeException thrown) {
            failure = thrown;
            status = CheckedRootWriter.Status.FAILED;
            if (attempted[0]) {
                try {
                    status = policy.read(file).filter(candidate::equals).isPresent()
                            ? CheckedRootWriter.Status.COMMITTED : CheckedRootWriter.Status.FAILED;
                } catch (RuntimeException unreadable) {
                    thrown.addSuppressed(unreadable);
                    status = CheckedRootWriter.Status.UNKNOWN;
                }
            }
        }
        boolean forceFailed = false;
        if (status == CheckedRootWriter.Status.COMMITTED && attributes.force() != null) {
            try { attributes.force().force(file.toAbsolutePath().getParent()); }
            catch (IOException | RuntimeException forceFailure) { forceFailed = true; }
        }
        return new CheckedRootWriter.Attempt<>(status, new Result(identities, forceFailed, failure));
    }

    private static Map<UUID, SatelliteRegistryProof.Identity> identities(Capture capture) {
        var identities = new LinkedHashMap<UUID, SatelliteRegistryProof.Identity>();
        if (capture.operational()) {
            capture.sections().satellites().forEach(state -> identities.put(state.satelliteId(),
                    SatelliteRegistryProof.Identity.of(state)));
        }
        return identities;
    }

    private static <T> List<T> sorted(List<T> records, Function<T, UUID> id) {
        var values = new ArrayList<>(records);
        values.sort(Comparator.comparing(id, SatelliteMissionRegistry.UUID_ORDER));
        return values;
    }
}
