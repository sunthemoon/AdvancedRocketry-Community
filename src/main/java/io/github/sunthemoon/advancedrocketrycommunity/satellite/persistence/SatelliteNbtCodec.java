package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionQuarantine;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict bounded codec for independently versioned satellite runtime records (ADR-049 §7, ADR-050 §3). */
final class SatelliteNbtCodec {
    private static final Set<String> SATELLITE_V2_KEYS = Set.of("kind", "orbit_body", "blueprint", "kind_state");
    private static final Set<String> MISSION_V2_KEYS = Set.of(
            "kind", "seed", "start_epoch", "reward_version", "instance_id", "quarantine");
    private static final Set<String> DATA_KEYS = Set.of("research_yield", "discovery_cost", "discovery_required");
    private static final Set<String> SURVEY_KEYS = Set.of("instances", "candidate_fingerprint");
    private static final Set<String> RESOURCE_KEYS = Set.of("reward", "bound_terminal", "terminal_level",
            "terminal_pos", "rebound", "paid_terminal", "acknowledged", "ack_epoch");

    private SatelliteNbtCodec() {
    }

    static CompoundTag encodeSatellite(SatelliteState state) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", state.schemaVersion());
        target.putUUID("satellite_id", state.satelliteId());
        target.putString("definition_id", state.definitionId().toString());
        target.putUUID("owner_id", state.ownerId());
        target.putLong("launched_at", state.launchedAtLogicalTime());
        target.putString("status", state.status().name().toLowerCase(Locale.ROOT));
        state.currentMissionId().ifPresent(value -> target.putUUID("current_mission_id", value));
        target.putString("kind", state.kind().id());
        state.orbitBody().ifPresent(body -> target.putString("orbit_body", body.toString()));
        target.put("blueprint", encodeBlueprint(state.blueprint()));
        target.put("kind_state", encodeKindState(state.kindState()));
        requireBound(target, SatelliteLimits.MAX_SATELLITE_RECORD_NBT_BYTES);
        return target;
    }

    static SatelliteState decodeSatellite(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_SATELLITE_RECORD_NBT_BYTES);
        SatelliteKind kind = SatelliteKind.parse(requireString(source, "kind"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown satellite kind"));
        return new SatelliteState(
                requireSchema(source, SatelliteLimits.SATELLITE_SCHEMA_VERSION, "satellite"),
                requireUuid(source, "satellite_id"),
                requireLocation(source, "definition_id"),
                requireUuid(source, "owner_id"),
                requireNonNegativeLong(source, "launched_at"),
                requireEnum(source, "status", SatelliteStatus.class),
                optionalUuid(source, "current_mission_id"),
                kind,
                optionalLocation(source, "orbit_body"),
                decodeBlueprint(requireCompound(source, "blueprint")),
                decodeKindState(kind, requireCompound(source, "kind_state"))
        );
    }

    /** Copies a schema-1 satellite record into schema 2 with the legacy {@code data} label. */
    static CompoundTag upgradeLegacySatellite(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_RECORD_NBT_BYTES);
        requireSchema(source, SatelliteLimits.LEGACY_SATELLITE_SCHEMA_VERSION, "legacy satellite");
        for (String key : SATELLITE_V2_KEYS) {
            if (source.contains(key)) {
                throw new IllegalArgumentException("Legacy satellite record already carries " + key);
            }
        }
        CompoundTag upgraded = source.copy();
        upgraded.putInt("schema_version", SatelliteLimits.SATELLITE_SCHEMA_VERSION);
        upgraded.putString("kind", SatelliteKind.DATA.id());
        upgraded.put("blueprint", encodeBlueprint(SatelliteBlueprint.LEGACY_DATA));
        upgraded.put("kind_state", new CompoundTag());
        decodeSatellite(upgraded);
        return upgraded;
    }

    static CompoundTag encodeMission(MissionState state) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", state.schemaVersion());
        target.putUUID("mission_id", state.missionId());
        target.putUUID("satellite_id", state.satelliteId());
        target.putUUID("owner_id", state.ownerId());
        target.putString("definition_id", state.definitionId().toString());
        target.putString("target_body_id", state.targetBodyId().toString());
        target.putLong("started_at", state.startedAtLogicalTime());
        target.putLong("completes_at", state.completesAtLogicalTime());
        target.putString("status", state.status().name().toLowerCase(Locale.ROOT));
        state.readyAtLogicalTime().ifPresent(value -> target.putLong("ready_at", value));
        state.resolvedAtLogicalTime().ifPresent(value -> target.putLong("resolved_at", value));
        target.putString("kind", state.kind().id());
        target.putLong("seed", state.seed());
        target.putLong("start_epoch", state.startEpoch());
        target.putString("reward_version", state.rewardVersion());
        state.instanceId().ifPresent(value -> target.putUUID("instance_id", value));
        state.quarantine().ifPresent(value -> target.put("quarantine", encodeQuarantine(value)));
        encodePayload(state.payload(), target);
        requireBound(target, SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES);
        return target;
    }

    static MissionState decodeMission(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES);
        MissionKind kind = MissionKind.parse(requireString(source, "kind"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mission kind"));
        Optional<CompoundTag> quarantine = source.contains("quarantine")
                ? Optional.of(requireCompound(source, "quarantine")) : Optional.empty();
        return new MissionState(
                requireSchema(source, SatelliteLimits.MISSION_SCHEMA_VERSION, "mission"),
                requireUuid(source, "mission_id"),
                requireUuid(source, "satellite_id"),
                requireUuid(source, "owner_id"),
                requireLocation(source, "definition_id"),
                kind,
                requireLocation(source, "target_body_id"),
                optionalUuid(source, "instance_id"),
                requireLong(source, "seed"),
                requireNonNegativeLong(source, "started_at"),
                requireNonNegativeLong(source, "completes_at"),
                requireNonNegativeLong(source, "start_epoch"),
                requireEnum(source, "status", MissionStatus.class),
                optionalLong(source, "ready_at"),
                optionalLong(source, "resolved_at"),
                requireBoundedString(source, "reward_version", SatelliteLimits.MAX_REWARD_VERSION_CHARS),
                quarantine.map(SatelliteNbtCodec::decodeQuarantine),
                decodePayload(kind, source)
        );
    }

    /** Copies a schema-1 mission record into schema 2 as a legacy {@code data} mission (ADR-050 §3). */
    static CompoundTag upgradeLegacyMission(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_RECORD_NBT_BYTES);
        requireSchema(source, SatelliteLimits.LEGACY_MISSION_SCHEMA_VERSION, "legacy mission");
        for (String key : MISSION_V2_KEYS) {
            if (source.contains(key)) {
                throw new IllegalArgumentException("Legacy mission record already carries " + key);
            }
        }
        CompoundTag upgraded = source.copy();
        upgraded.putInt("schema_version", SatelliteLimits.MISSION_SCHEMA_VERSION);
        upgraded.putString("kind", MissionKind.DATA.id());
        upgraded.putLong("seed", 0L);
        upgraded.putLong("start_epoch", 0L);
        upgraded.putString("reward_version", MissionState.LEGACY_DATA_REWARD_VERSION);
        decodeMission(upgraded);
        return upgraded;
    }

    static CompoundTag encodeInstance(AsteroidInstance instance) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", instance.schemaVersion());
        target.putUUID("instance_id", instance.instanceId());
        target.putUUID("owner_id", instance.ownerId());
        target.putString("system", instance.system().toString());
        target.putString("asteroid_type", instance.asteroidType().toString());
        target.putString("table_version", instance.tableVersion());
        target.putString("candidate_fingerprint", instance.candidateFingerprint());
        target.putLong("seed", instance.seed());
        target.put("yield", encodeEntries(instance.yield()));
        target.putLong("created_at", instance.createdAt());
        instance.expiresAt().ifPresent(value -> target.putLong("expires_at", value));
        target.putString("state", instance.state().name().toLowerCase(Locale.ROOT));
        target.putUUID("source_mission", instance.sourceMission());
        instance.allocatedMission().ifPresent(value -> target.putUUID("allocated_mission", value));
        requireBound(target, SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES);
        return target;
    }

    static AsteroidInstance decodeInstance(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES);
        return new AsteroidInstance(
                requireSchema(source, SatelliteLimits.INSTANCE_SCHEMA_VERSION, "asteroid instance"),
                requireUuid(source, "instance_id"),
                requireUuid(source, "owner_id"),
                requireLocation(source, "system"),
                requireLocation(source, "asteroid_type"),
                requireBoundedString(source, "table_version", 16),
                requireBoundedString(source, "candidate_fingerprint", 16),
                requireLong(source, "seed"),
                decodeEntries(requireList(source, "yield", Tag.TAG_COMPOUND)),
                requireNonNegativeLong(source, "created_at"),
                optionalLong(source, "expires_at"),
                requireEnum(source, "state", InstanceState.class),
                requireUuid(source, "source_mission"),
                optionalUuid(source, "allocated_mission")
        );
    }

    static CompoundTag encodeAccount(ResearchAccount account) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", account.schemaVersion());
        target.putUUID("owner_id", account.ownerId());
        target.putInt("balance", account.balance());
        target.putLong("lifetime_earned", account.lifetimeEarned());
        target.putLong("lifetime_spent", account.lifetimeSpent());
        requireBound(target, SatelliteLimits.MAX_RECORD_NBT_BYTES);
        return target;
    }

    static ResearchAccount decodeAccount(CompoundTag source) {
        requireBound(source, SatelliteLimits.MAX_RECORD_NBT_BYTES);
        return new ResearchAccount(
                requireSchema(source, SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION, "research account"),
                requireUuid(source, "owner_id"),
                requireInt(source, "balance"),
                requireNonNegativeLong(source, "lifetime_earned"),
                requireNonNegativeLong(source, "lifetime_spent")
        );
    }

    private static CompoundTag encodeBlueprint(SatelliteBlueprint blueprint) {
        CompoundTag target = new CompoundTag();
        target.putBoolean("legacy", blueprint.legacy());
        ListTag components = new ListTag();
        blueprint.components().forEach(id -> components.add(StringTag.valueOf(id.toString())));
        target.put("components", components);
        SatelliteStats stats = blueprint.stats();
        target.putInt("power", stats.power());
        target.putInt("battery", stats.battery());
        target.putInt("data", stats.data());
        target.putInt("cargo", stats.cargo());
        target.putInt("rating", stats.rating());
        return target;
    }

    private static SatelliteBlueprint decodeBlueprint(CompoundTag source) {
        ListTag raw = requireList(source, "components", Tag.TAG_STRING);
        if (raw.size() > SatelliteLimits.MAX_BLUEPRINT_COMPONENTS) {
            throw new IllegalArgumentException("Satellite blueprint lists too many components");
        }
        List<ResourceLocation> components = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            components.add(parseLocation(raw.getString(index), "blueprint component"));
        }
        return new SatelliteBlueprint(
                components,
                requireBoolean(source, "legacy"),
                new SatelliteStats(
                        requireInt(source, "power"),
                        requireInt(source, "battery"),
                        requireInt(source, "data"),
                        requireInt(source, "cargo"),
                        requireInt(source, "rating")
                )
        );
    }

    private static CompoundTag encodeKindState(SatelliteKindState state) {
        CompoundTag target = new CompoundTag();
        if (state instanceof SatelliteKindState.Survey survey) {
            target.putLong("charge", survey.charge());
            target.putLong("charge_time", survey.chargeTime());
            target.putInt("scan_energy", survey.scanEnergy());
            target.putInt("scan_radius", survey.scanRadius());
            target.putInt("scan_cell", survey.scanCell());
        } else if (state instanceof SatelliteKindState.Solar solar) {
            target.putInt("output_multiplier", solar.outputMultiplierPercent());
            solar.receiver().ifPresent(value -> target.putUUID("receiver", value));
        }
        return target;
    }

    private static SatelliteKindState decodeKindState(SatelliteKind kind, CompoundTag source) {
        return switch (kind) {
            case SURVEY -> new SatelliteKindState.Survey(
                    requireNonNegativeLong(source, "charge"),
                    requireNonNegativeLong(source, "charge_time"),
                    requireInt(source, "scan_energy"),
                    requireInt(source, "scan_radius"),
                    requireInt(source, "scan_cell")
            );
            case SOLAR -> new SatelliteKindState.Solar(
                    requireInt(source, "output_multiplier"),
                    optionalUuid(source, "receiver")
            );
            default -> {
                if (!source.isEmpty()) {
                    throw new IllegalArgumentException("Satellite kind " + kind.id() + " carries no state");
                }
                yield new SatelliteKindState.Plain(kind);
            }
        };
    }

    private static void encodePayload(MissionPayload payload, CompoundTag target) {
        if (payload instanceof MissionPayload.Data data) {
            target.putInt("research_yield", data.researchYield());
            target.putInt("discovery_cost", data.discoveryCost());
            target.putBoolean("discovery_required", data.discoveryRequired());
        } else if (payload instanceof MissionPayload.Survey survey) {
            ListTag instances = new ListTag();
            survey.instances().forEach(id -> instances.add(NbtUtils.createUUID(id)));
            target.put("instances", instances);
            target.putString("candidate_fingerprint", survey.candidateFingerprint());
        } else if (payload instanceof MissionPayload.Resource resource) {
            target.put("reward", encodeEntries(resource.reward()));
            target.putUUID("bound_terminal", resource.boundTerminal());
            resource.boundTerminalDisplay().ifPresent(display -> {
                target.putString("terminal_level", display.level().toString());
                target.putLong("terminal_pos", display.pos().asLong());
            });
            target.putBoolean("rebound", resource.rebound());
            resource.paidTerminal().ifPresent(value -> target.putUUID("paid_terminal", value));
            target.putBoolean("acknowledged", resource.acknowledged());
            resource.ackEpoch().ifPresent(value -> target.putLong("ack_epoch", value));
        }
    }

    private static MissionPayload decodePayload(MissionKind kind, CompoundTag source) {
        Set<String> own = switch (kind) {
            case DATA -> DATA_KEYS;
            case SURVEY -> SURVEY_KEYS;
            case ASTEROID, GAS -> RESOURCE_KEYS;
        };
        for (Set<String> keys : List.of(DATA_KEYS, SURVEY_KEYS, RESOURCE_KEYS)) {
            if (keys == own) {
                continue;
            }
            for (String key : keys) {
                if (source.contains(key)) {
                    throw new IllegalArgumentException("Mission of kind " + kind.id() + " carries " + key);
                }
            }
        }
        return switch (kind) {
            case DATA -> new MissionPayload.Data(
                    requireInt(source, "research_yield"),
                    requireInt(source, "discovery_cost"),
                    requireBoolean(source, "discovery_required")
            );
            case SURVEY -> {
                ListTag raw = requireList(source, "instances", Tag.TAG_INT_ARRAY);
                if (raw.size() > SatelliteLimits.MAX_INSTANCES_PER_SURVEY) {
                    throw new IllegalArgumentException("Survey lists too many instances");
                }
                List<UUID> instances = new ArrayList<>(raw.size());
                for (Tag tag : raw) {
                    instances.add(NbtUtils.loadUUID(tag));
                }
                yield new MissionPayload.Survey(instances, requireBoundedString(source, "candidate_fingerprint", 16));
            }
            case ASTEROID, GAS -> {
                boolean hasLevel = source.contains("terminal_level");
                if (hasLevel != source.contains("terminal_pos")) {
                    throw new IllegalArgumentException("Bound terminal display is incomplete");
                }
                Optional<MissionPayload.TerminalLocation> display = hasLevel
                        ? Optional.of(new MissionPayload.TerminalLocation(
                                requireLocation(source, "terminal_level"),
                                BlockPos.of(requireLong(source, "terminal_pos"))))
                        : Optional.empty();
                yield new MissionPayload.Resource(
                        kind,
                        decodeEntries(requireList(source, "reward", Tag.TAG_COMPOUND)),
                        requireUuid(source, "bound_terminal"),
                        display,
                        requireBoolean(source, "rebound"),
                        optionalUuid(source, "paid_terminal"),
                        requireBoolean(source, "acknowledged"),
                        optionalLong(source, "ack_epoch")
                );
            }
        };
    }

    private static CompoundTag encodeQuarantine(MissionQuarantine quarantine) {
        CompoundTag target = new CompoundTag();
        target.putString("reason", quarantine.reason());
        target.putString("previous_status", quarantine.previousStatus().name().toLowerCase(Locale.ROOT));
        target.putBoolean("receipt_seen", quarantine.receiptSeen());
        return target;
    }

    private static MissionQuarantine decodeQuarantine(CompoundTag source) {
        return new MissionQuarantine(
                requireBoundedString(source, "reason", 48),
                requireEnum(source, "previous_status", MissionStatus.class),
                requireBoolean(source, "receipt_seen")
        );
    }

    private static ListTag encodeEntries(List<RewardEntry> entries) {
        ListTag list = new ListTag();
        for (RewardEntry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("item", entry.item().toString());
            tag.putInt("count", entry.count());
            list.add(tag);
        }
        return list;
    }

    private static List<RewardEntry> decodeEntries(ListTag raw) {
        if (raw.size() > SatelliteLimits.MAX_REWARD_ENTRIES) {
            throw new IllegalArgumentException("Reward list is outside its bound");
        }
        List<RewardEntry> entries = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            CompoundTag tag = raw.getCompound(index);
            entries.add(new RewardEntry(requireLocation(tag, "item"), requireInt(tag, "count")));
        }
        return entries;
    }

    private static void requireBound(CompoundTag source, int bound) {
        if (SatelliteNbtSize.uncompressedBytes(source) > bound) {
            throw new IllegalArgumentException("Satellite runtime record exceeds its fixed NBT bound");
        }
    }

    private static int requireSchema(CompoundTag source, int expected, String recordName) {
        int value = requireInt(source, "schema_version");
        if (value != expected) {
            throw new IllegalArgumentException("Unsupported " + recordName + " schema " + value);
        }
        return value;
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing satellite integer " + key);
        }
        return source.getInt(key);
    }

    private static long requireLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing satellite long " + key);
        }
        return source.getLong(key);
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        long value = requireLong(source, key);
        if (value < 0L) {
            throw new IllegalArgumentException("Satellite long " + key + " cannot be negative");
        }
        return value;
    }

    private static boolean requireBoolean(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Missing satellite boolean " + key);
        }
        return source.getBoolean(key);
    }

    private static String requireString(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing satellite string " + key);
        }
        return source.getString(key);
    }

    private static String requireBoundedString(CompoundTag source, String key, int maxChars) {
        String value = requireString(source, key);
        if (value.isEmpty() || value.length() > maxChars) {
            throw new IllegalArgumentException("Satellite string " + key + " is outside its bound");
        }
        return value;
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing satellite compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key, int elementType) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IllegalArgumentException("Missing or invalid satellite list " + key);
        }
        return list;
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.hasUUID(key)) {
            throw new IllegalArgumentException("Missing satellite UUID " + key);
        }
        return source.getUUID(key);
    }

    private static Optional<UUID> optionalUuid(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(requireUuid(source, key));
    }

    private static OptionalLong optionalLong(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(requireNonNegativeLong(source, key));
    }

    private static Optional<ResourceLocation> optionalLocation(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(requireLocation(source, key));
    }

    private static ResourceLocation requireLocation(CompoundTag source, String key) {
        return parseLocation(requireString(source, key), key);
    }

    private static ResourceLocation parseLocation(String raw, String name) {
        if (raw.isEmpty() || raw.length() > 128) {
            throw new IllegalArgumentException("Satellite identifier " + name + " is outside its bound");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Satellite identifier " + name + " is invalid");
        }
        return parsed;
    }

    private static <E extends Enum<E>> E requireEnum(
            CompoundTag source,
            String key,
            Class<E> type
    ) {
        String raw = requireString(source, key);
        if (raw.isEmpty() || raw.length() > 64) {
            throw new IllegalArgumentException("Satellite enum " + key + " is outside its bound");
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown satellite enum " + key + ": " + raw, exception);
        }
    }
}
