package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof.*;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** W27 section 3: synchronous fresh captures; no retained candidate or delayed readback. */
final class SatelliteRegistryWriter {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private final Thread owner = Thread.currentThread();
    private final Object session = new Object();
    private final SatelliteMissionSavedData data;
    private final SatelliteMissionRegistry registry;
    private long mutationSequence, highestUnknownEpoch;
    private Acknowledgement acknowledged;
    private boolean saveFailureReported;
    private CheckedSavedDataPolicy policy = CheckedSavedDataPolicy.atomic(TYPE);

    SatelliteRegistryWriter(SatelliteMissionSavedData data, SatelliteMissionRegistry registry) {
        this.data = data; this.registry = registry; highestUnknownEpoch = registry.saveEpoch();
    }

    void mutated() { requireOwner(); mutationSequence = Math.addExact(mutationSequence, 1L); }
    long sequence() { return mutationSequence; }
    long epochToWrite() {
        long epoch = registry.saveEpoch();
        return registry.epochToWrite() == epoch ? epoch : Math.addExact(Math.max(epoch, highestUnknownEpoch), 1L);
    }
    Optional<Acknowledgement> acknowledgement() { requireOwner(); return Optional.ofNullable(acknowledged); }
    void policy(CheckedSavedDataPolicy policy) {
        requireOwner();
        if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong registry write policy"); }
        this.policy = policy;
    }
    CheckedSavedDataPolicy policy() { return policy; }

    Watch watch(UUID satellite) {
        requireOwner();
        return new Watch(session, mutationSequence, Identity.of(data.satellite(satellite).orElseThrow()));
    }
    /** A clean decoded current root is a disk observation in this session; restored roots need a new write. */
    void loaded() {
        requireOwner();
        if (data.operational() && !data.isDirty()) {
            acknowledged = new Acknowledgement(session, mutationSequence, registry.saveEpoch(), identities());
        }
    }
    private Map<UUID, Identity> identities() {
        var identities = new LinkedHashMap<UUID, Identity>();
        registry.satellites().forEach(state -> identities.put(state.satelliteId(), Identity.of(state)));
        return identities;
    }

    Completion checkpoint(Path file, List<Watch> watches, CheckedSavedDataPolicy policy,
                          CheckedSavedDataPolicy.DirectoryForce force) {
        requireOwner();
        if (data.operational() && !data.isDirty() && acknowledged != null
                && watches.stream().allMatch(acknowledged::proves)) {
            return new Completion(Outcome.COMMITTED, Optional.of(acknowledged));
        }
        return write(file, policy, force);
    }

    Completion write(Path file, CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force) {
        requireOwner();
        if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong registry write policy"); }
        data.pendingWrite(); // Eligibility is not a registry mutation.
        long sequence = mutationSequence;
        boolean[] attempted = {false};
        CompoundTag candidate = null;
        long epoch = registry.saveEpoch();
        Map<UUID, Identity> identities = Map.of();
        Outcome outcome;
        try {
            candidate = data.save(new CompoundTag());
            if (data.operational()) {
                epoch = candidate.getLong("save_epoch");
                identities = identities();
            }
            CompoundTag frozen = candidate;
            policy.withCommitter((staged, target) -> {
                attempted[0] = true;
                policy.committer().commit(staged, target);
                force.force(target.toAbsolutePath().getParent());
            }).replace(file, () -> frozen);
            outcome = Outcome.COMMITTED;
        } catch (RuntimeException failure) {
            outcome = Outcome.FAILED;
            if (attempted[0]) {
                try {
                    outcome = policy.read(file).filter(candidate::equals).isPresent() ? Outcome.COMMITTED : Outcome.FAILED;
                } catch (RuntimeException unreadable) {
                    outcome = Outcome.UNKNOWN;
                }
            }
        }
        if (outcome == Outcome.UNKNOWN) {
            highestUnknownEpoch = Math.max(highestUnknownEpoch, epoch);
            acknowledged = null;
        } else if (outcome == Outcome.COMMITTED) {
            if (data.operational()) {
                acknowledged = new Acknowledgement(session, sequence, epoch, identities);
                if (sequence == mutationSequence) {
                    registry.markPersisted(epoch);
                    highestUnknownEpoch = epoch;
                    data.persisted();
                } else {
                    // Defensive only: production captures and resolves on the same server-thread call stack.
                    highestUnknownEpoch = Math.max(highestUnknownEpoch, epoch);
                }
            } else {
                data.persisted(); // Raw blocked roots retain their exact payload, never a registration proof.
            }
            saveFailureReported = false;
        }
        return new Completion(outcome, outcome == Outcome.COMMITTED ? Optional.ofNullable(acknowledged) : Optional.empty());
    }

    void flush(Path file) {
        Completion completion = write(file, policy, directory -> { });
        if (completion.outcome() != Outcome.COMMITTED || data.isDirty()) {
            throw new IllegalStateException("Satellite registry write was not acknowledged: " + completion.outcome());
        }
    }
    void save(File file) {
        if (!data.isDirty()) { return; }
        try {
            flush(file.toPath());
        } catch (RuntimeException exception) {
            if (!saveFailureReported) {
                LogUtils.getLogger().error("Atomic SavedData save failed for {}; retained dirty state", TYPE.dataName(), exception);
                saveFailureReported = true;
            }
        }
    }
    private void requireOwner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Registry writes require the owner thread"); }
    }
}
