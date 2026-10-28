package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W177 (W173 section 6.3): the resource-table fit check counts canonical identifiers, so a table it accepts at the
 * 4,096-byte mission bound gives missions whose every reachable state encodes, with the longest definition ID a
 * datapack can declare.
 */
final class SatelliteRecordFitTest {
    private static final int BOUND = SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES;
    private static final UUID OWNER = new UUID(51, 1);
    private static final UUID ANY = new UUID(-1L, -1L);
    private static final ResourceLocation BODY = qualified('b');
    private static final ResourceLocation LEVEL = qualified('l');
    private static final BlockPos FAR = new BlockPos(30_000_000, 319, -30_000_000);
    private static final String REASON = "Q".repeat(48);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aTableAtTheMissionBoundCoversEveryReachableStateOfTheLongestDefinition() {
        List<ResourceLocation> items = new ArrayList<>();
        List<RewardEntry> reward = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_REWARD_ENTRIES; index++) {
            items.add(qualified((char) ('a' + index)));
            reward.add(new RewardEntry(items.get(index), 1));
        }
        String version = versionWithFit(items, BOUND);
        assertEquals(BOUND, SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version, items));
        assertEquals(SatelliteRecordFit.DOES_NOT_FIT,
                SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version + "x", items));

        // The longest definition ID a datapack can declare: a bare name that gains "minecraft:".
        SatelliteKindDefinition longest = SatelliteKindDefinition.decode(gasHarvester("g" + "x".repeat(117)));
        assertEquals(128, longest.id().toString().length());
        assertThrows(IllegalArgumentException.class, () -> SatelliteKindDefinition.decode(gasHarvester("g" + "x".repeat(118))));

        Scenario scenario = Scenario.start(longest.id(), reward, version);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        scenario.reachable().forEach((name, state) -> sizes.put(name, bytes(SatelliteNbtCodec.encodeMission(state))));
        assertTrue(sizes.values().stream().allMatch(bytes -> bytes <= BOUND), sizes::toString);
        assertEquals(BOUND, Collections.max(sizes.values()), "the fit check measures the largest reachable state: " + sizes);
        assertTrue(SatelliteMissionSavedData.load(scenario.data().save(new CompoundTag())).operational());

        // The 138-character ID the spelling-bounded parser accepted: the same table no longer covers its missions.
        Scenario spellingBounded = Scenario.start(ResourceLocation.tryParse("g" + "x".repeat(127)), reward, version);
        assertThrows(IllegalArgumentException.class,
                () -> SatelliteNbtCodec.encodeMission(spellingBounded.reachable().get("quarantined_from_ready")));
    }

    @Test void anItemTheRegistryDecoderRefusesDoesNotFit() {
        String version = "gas-v1/t:gas/0123456789abcdef";
        ResourceLocation longest = ResourceLocation.tryParse("i" + "x".repeat(117));
        ResourceLocation over = ResourceLocation.tryParse("i" + "x".repeat(118));
        assertEquals(129, over.toString().length());
        assertTrue(SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version, List.of(longest)) < BOUND);
        assertEquals(SatelliteRecordFit.DOES_NOT_FIT,
                SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version, List.of(over)));

        // Such a mission encodes well within its bound, but the registry decoder refuses it at the next load.
        MissionState mission = Scenario.start(qualified('d'), List.of(new RewardEntry(over, 1)), version)
                .reachable().get("quarantined_from_ready");
        CompoundTag encoded = SatelliteNbtCodec.encodeMission(mission);
        assertTrue(bytes(encoded) < BOUND);
        assertThrows(IllegalArgumentException.class, () -> SatelliteNbtCodec.decodeMission(encoded));
    }

    /** A satellite launched from {@code definition} with one gas mission and every state that mission can reach. */
    private record Scenario(SatelliteMissionSavedData data, Map<String, MissionState> reachable) {
        static Scenario start(ResourceLocation definition, List<RewardEntry> reward, String version) {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0L);
            UUID harvester = new UUID(52, 1);
            assertEquals(SatelliteOperationCode.SUCCESS, data.launchIdle(time -> SatelliteState.launchIdle(harvester,
                    definition, OWNER, time, BODY, blueprint(), new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER)),
                    0L).code());
            SatelliteOperationResult started = data.resources(missions -> missions.startResource(
                    new ResourceMissions.ResourceStart(harvester, new UUID(54, 1), OWNER, MissionKind.GAS, BODY,
                            Optional.empty(), reward, version, 1_000, 3L, new UUID(53, 1),
                            MissionPayload.TerminalLocation.of(LEVEL, FAR)), 10L));
            assertEquals(SatelliteOperationCode.SUCCESS, started.code());
            MissionState mission = started.mission().orElseThrow();
            // The largest payload reconciliation can give it: a paid, rebound and acknowledged terminal.
            MissionPayload.Resource full = new MissionPayload.Resource(MissionKind.GAS, reward, ANY,
                    Optional.of(new MissionPayload.TerminalLocation(LEVEL, FAR)), true, Optional.of(ANY), true,
                    OptionalLong.of(Long.MAX_VALUE));
            MissionState ready = mission.withPayload(full).complete(Long.MAX_VALUE - 1);
            Map<String, MissionState> reachable = new LinkedHashMap<>();
            reachable.put("active", mission.withPayload(full));
            reachable.put("ready", ready);
            reachable.put("quarantined_from_active", mission.withPayload(full).quarantine(REASON, true));
            reachable.put("quarantined_from_ready", ready.quarantine(REASON, true));
            reachable.put("claimed", ready.claimWith(Long.MAX_VALUE, full));
            reachable.put("cancelled_from_ready", ready.cancel(Long.MAX_VALUE));
            return new Scenario(data, reachable);
        }
    }

    /** A reward version (at most 200 characters) whose table fit is exactly {@code fit} bytes. */
    private static String versionWithFit(List<ResourceLocation> items, int fit) {
        for (int bytes = 1; bytes <= 3 * SatelliteLimits.MAX_REWARD_VERSION_CHARS - 3; bytes++) {
            // U+0800 takes three bytes in modified UTF-8, so the version grows by one byte per step.
            String version = "ࠀ".repeat(bytes / 3) + "x".repeat(bytes % 3);
            if (SatelliteRecordFit.resourceMissionBytes(MissionKind.GAS, version, items) == fit) {
                return version;
            }
        }
        throw new AssertionError("no reward version gives a fit of " + fit + " bytes");
    }

    private static com.google.gson.JsonElement gasHarvester(String id) {
        return JsonParser.parseString("{\"schema_version\":2,\"id\":\"" + id + "\",\"kind\":\"gas_harvester\","
                + "\"primary_component\":\"advancedrocketrycommunity:gas_harvester_primary\","
                + "\"launch_targets\":[\"advancedrocketrycommunity:gas_giant\"]}");
    }

    private static SatelliteBlueprint blueprint() {
        return new SatelliteBlueprint(List.of(ResourceLocation.tryParse("advancedrocketrycommunity:satellite_chassis"),
                ResourceLocation.tryParse("advancedrocketrycommunity:gas_harvester_primary")), false,
                new SatelliteStats(4, 10_720, 1_000, 9, 10));
    }

    /** A qualified identifier of exactly 128 characters, distinct per {@code tag}. */
    private static ResourceLocation qualified(char tag) {
        return ResourceLocation.tryParse("f:" + tag + "x".repeat(125));
    }

    private static int bytes(CompoundTag tag) {
        return SatelliteNbtSize.uncompressedBytes(tag);
    }
}
