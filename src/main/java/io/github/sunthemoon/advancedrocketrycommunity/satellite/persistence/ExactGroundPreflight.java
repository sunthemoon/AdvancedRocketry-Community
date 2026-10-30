package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RecordSizer.EncodingSize;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Exact current-root proof without reconstructing unchanged residents; see the W216 design record. */
final class ExactGroundPreflight {
    private ExactGroundPreflight() { }

    static EncodingSize measure(Object record) {
        CompoundTag tag;
        if (record instanceof SatelliteState value) { tag = SatelliteNbtCodec.encodeSatellite(value); }
        else if (record instanceof MissionState value) { tag = SatelliteNbtCodec.encodeMission(value); }
        else if (record instanceof AsteroidInstance value) { tag = SatelliteNbtCodec.encodeInstance(value); }
        else if (record instanceof GroundSurveyJob value) { tag = GroundSurveyCodec.encode(value); }
        else if (record instanceof ResearchAccount value) { tag = SatelliteNbtCodec.encodeAccount(value); }
        else { throw new IllegalArgumentException("Not a satellite registry record"); }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) { tag.write(output); }
            var counter = new NbtAccounter(Long.MAX_VALUE);
            try (var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                // outer -> data -> section list -> record: depth 3, with no named-root header.
                CompoundTag decoded = CompoundTag.TYPE.load(input, 3, counter);
                if (!tag.equals(decoded) || input.read() != -1) {
                    throw new IllegalArgumentException("Native record read changed the encoding");
                }
            }
            // The containing ListTag charges four bytes per resident in addition to its fixed header.
            return new EncodingSize(bytes.size(), Math.addExact(counter.getUsage(), 4));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot measure native record encoding", exception);
        }
    }

    static boolean fits(SatelliteMissionRegistry registry, GroundSurveyJob job, AsteroidInstance instance,
            boolean remove, ResearchAccount account) {
        EncodingSize edited = registry.residentEncoding();
        edited = edited.minus(registry.residentEncoding(registry.groundSurveys().job(job.jobId()).orElse(null)))
                .plus(measure(job));
        if (instance != null) {
            edited = edited.minus(registry.residentEncoding(registry.instance(instance.instanceId()).orElse(null)));
            if (!remove) { edited = edited.plus(measure(instance)); }
        }
        if (account != null) {
            edited = edited.minus(registry.residentEncoding(registry.account(account.ownerId()))).plus(measure(account));
        }
        return fits(edited);
    }

    static boolean fits(EncodingSize residents) {
        var type = ManagedSavedDataType.SATELLITE_MISSIONS;
        // A <=16 MiB canonical payload leaves 64 KiB for its wrapper and gzip block overhead. Each native
        // declaration is <=32 KiB (8192 list entries * 4), below the reader's remaining-byte/slack guard.
        // Per-record native reads check equality/depth; framing + element charges give the exact heap usage.
        return residents.known() && Math.addExact(Framing.SIZE.bytes(), residents.bytes())
                <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES
                && Math.addExact(Framing.SIZE.heapBytes(), residents.heapBytes())
                <= Math.multiplyExact(type.maxCompressedBytes(), type.heapAccountingFactor());
    }

    /** Lazily derive framing from the same current-root encoder; no world records or NBT are retained. */
    private static final class Framing {
        private static final EncodingSize SIZE = measure();

        private static EncodingSize measure() {
            CompoundTag root = SatelliteRegistryPayload.encodeSorted(0, 0, 1, List.of(), List.of(), List.of(),
                    List.of(), List.of(), new CompoundTag());
            CompoundTag outer = new CompoundTag();
            outer.put("data", root);
            try {
                var bytes = new ByteArrayOutputStream();
                try (var output = new DataOutputStream(bytes)) { NbtIo.write(outer, output); }
                var counter = new NbtAccounter(Long.MAX_VALUE);
                try (var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                    if (!outer.equals(NbtIo.read(input, counter)) || input.read() != -1) {
                        throw new IllegalArgumentException("Native framing read changed the encoding");
                    }
                }
                return new EncodingSize(SatelliteNbtSize.uncompressedBytes(root), counter.getUsage());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Cannot measure native root framing", exception);
            }
        }
    }
}
