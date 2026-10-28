package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionQuarantine;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-050 section 3 / ADR-052 section 2 record-fit check: the largest record a table can produce, encoded
 * with its real item IDs and 128-character values for every field the table does not decide. Identifiers count
 * in their canonical form, which the content parsers and the registry decoder bound at 128 characters, so a
 * definition, target or terminal level ID can never be longer than its filler. A table whose worst case
 * exceeds a record bound is rejected when it loads, so no save can fail on size.
 */
public final class SatelliteRecordFit {
    /** Returned when the encoder itself refuses the record because it exceeds its bound. */
    public static final int DOES_NOT_FIT = Integer.MAX_VALUE;
    private static final int MAX_ID_CHARS = BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS;
    private static final UUID ANY = new UUID(-1L, -1L);
    private static final String HEX16 = "0123456789abcdef";
    private static final String LONGEST_REASON = "MISSING_SATELLITE_AND_A_LONGER_REASON_CODE_12345";

    private SatelliteRecordFit() {
    }

    /** The worst-case encoded instance of a type with these yield items (ores in file order, then the base). */
    public static int instanceBytes(ResourceLocation type, List<ResourceLocation> yieldItems) {
        AsteroidInstance instance = new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, ANY, ANY,
                filler('s'), type, HEX16, HEX16, Long.MIN_VALUE, entries(yieldItems, 4_096), Long.MAX_VALUE - 1,
                OptionalLong.of(Long.MAX_VALUE), InstanceState.ALLOCATED,
                io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceOrigin.survey(ANY), Optional.of(ANY));
        try {
            return SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeInstance(instance));
        } catch (IllegalArgumentException overBound) {
            return DOES_NOT_FIT;
        }
    }

    /** The worst-case encoded asteroid or gas mission with this reward and reward version. */
    public static int resourceMissionBytes(MissionKind kind, String rewardVersion, List<ResourceLocation> rewardItems) {
        // A gas table's canonical file ID contributes to the version; reject before constructing the specimen.
        if (rewardVersion.isEmpty() || rewardVersion.length() > SatelliteLimits.MAX_REWARD_VERSION_CHARS) {
            return DOES_NOT_FIT;
        }
        // The encoder writes an item's canonical form, and the registry decoder refuses it over the bound.
        if (rewardItems.stream().anyMatch(item -> item.toString().length() > MAX_ID_CHARS)) {
            return DOES_NOT_FIT;
        }
        MissionState mission = new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, ANY, ANY, ANY, filler('d'),
                kind, filler('t'), kind == MissionKind.ASTEROID ? Optional.of(ANY) : Optional.empty(),
                Long.MIN_VALUE, 0L, Long.MAX_VALUE - 1, Long.MAX_VALUE, MissionStatus.QUARANTINED,
                OptionalLong.of(Long.MAX_VALUE - 1), OptionalLong.empty(), rewardVersion,
                Optional.of(new MissionQuarantine(LONGEST_REASON, MissionStatus.READY, true)),
                new MissionPayload.Resource(kind, entries(rewardItems, SatelliteLimits.MAX_REWARD_ITEMS), ANY,
                        Optional.of(new MissionPayload.TerminalLocation(filler('l'),
                                new BlockPos(30_000_000, 319, -30_000_000))),
                        true, Optional.of(ANY), true, OptionalLong.of(Long.MAX_VALUE)));
        try {
            return SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(mission));
        } catch (IllegalArgumentException overBound) {
            return DOES_NOT_FIT;
        }
    }

    private static List<RewardEntry> entries(List<ResourceLocation> items, int total) {
        List<RewardEntry> entries = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            entries.add(new RewardEntry(items.get(index), index == 0 ? total - (items.size() - 1) : 1));
        }
        return entries;
    }

    /** The longest canonical identifier, distinct per {@code tag}. */
    private static ResourceLocation filler(char tag) {
        return ResourceLocation.tryParse("f:" + tag + "x".repeat(MAX_ID_CHARS - 3));
    }
}
