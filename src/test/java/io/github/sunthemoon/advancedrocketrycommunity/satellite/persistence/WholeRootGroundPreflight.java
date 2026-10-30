package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.BoundedSavedDataIo;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Independent copy of the pre-W216 whole-root oracle, including encoding residents before edits. */
final class WholeRootGroundPreflight {
    private WholeRootGroundPreflight() { }

    static boolean fits(SatelliteMissionRegistry registry, GroundSurveyJob job, AsteroidInstance instance,
            boolean remove, ResearchAccount account, long now, long observed) {
        try {
            BoundedSavedDataIo.validatePayload(edited(registry, job, instance, remove, account, now, observed),
                    ManagedSavedDataType.SATELLITE_MISSIONS, SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
            return true;
        } catch (ArithmeticException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static CompoundTag edited(SatelliteMissionRegistry registry, GroundSurveyJob job, AsteroidInstance instance,
            boolean remove, ResearchAccount account, long now, long observed) {
        CompoundTag root = SatelliteRegistryPayload.encodeUnchecked(registry, new CompoundTag());
        root.getCompound("clock").putLong("logical_game_time", now);
        root.getCompound("clock").putLong("last_observed_game_time", observed);
        root.putLong("save_epoch", Math.addExact(registry.epochs().ceiling(), 1));
        replace(root, "ground_surveys", "job_id", job.jobId(), GroundSurveyCodec.encode(job));
        if (instance != null) {
            replace(root, "instances", "instance_id", instance.instanceId(),
                    remove ? null : SatelliteNbtCodec.encodeInstance(instance));
        }
        if (account != null) {
            replace(root, "research_accounts", "owner_id", account.ownerId(), SatelliteNbtCodec.encodeAccount(account));
        }
        return root;
    }

    private static void replace(CompoundTag root, String section, String key, UUID id, CompoundTag next) {
        var list = root.getList(section, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID(key).equals(id)) {
                if (next == null) { list.remove(i); } else { list.set(i, next); }
                return;
            }
        }
        if (next != null) { list.add(next); }
    }
}
