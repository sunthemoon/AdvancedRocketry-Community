package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * ADR-052 section 7 {@code mission verify}: recomputes an instance or mission reward from its stored seed and
 * inputs. It never changes state. A current table version or fingerprint that differs means it cannot
 * recompute; a pruned instance, a removed satellite or a removed table means the inputs are unavailable.
 */
public final class RewardVerifier {
    public enum Outcome { MATCH, VERSION_CHANGED, INPUTS_UNAVAILABLE, MISMATCH }

    /** The outcome and one bounded line of detail for the audit log. */
    public record Report(Outcome outcome, String detail) {
        public Report {
            Objects.requireNonNull(outcome, "outcome");
            detail = detail.length() <= 256 ? detail : detail.substring(0, 253) + "...";
        }
    }

    private RewardVerifier() {
    }

    public static Report verifyInstance(AsteroidInstance instance, ResourceTables tables) {
        AsteroidType type = tables.asteroidType(instance.asteroidType()).orElse(null);
        if (type == null) {
            return new Report(Outcome.INPUTS_UNAVAILABLE, "asteroid type " + instance.asteroidType() + " is gone");
        }
        if (!type.tableVersion().equals(instance.tableVersion())) {
            return new Report(Outcome.VERSION_CHANGED, "type " + type.id() + " version " + type.tableVersion()
                    + " differs from the recorded " + instance.tableVersion());
        }
        return ResourceAlgorithms.asteroidYield(type, instance.seed()).equals(instance.yield())
                ? new Report(Outcome.MATCH, "asteroid-v1 yield of " + type.id())
                : new Report(Outcome.MISMATCH, "asteroid-v1 yield of " + type.id() + " differs from the record");
    }

    public static Report verifyMission(
            MissionState mission,
            Function<UUID, Optional<AsteroidInstance>> instances,
            Function<UUID, Optional<SatelliteState>> satellites,
            ResourceTables tables
    ) {
        return switch (mission.kind()) {
            case DATA -> new Report(Outcome.INPUTS_UNAVAILABLE,
                    "a data mission has no seeded reward (" + mission.rewardVersion() + ")");
            case SURVEY -> verifySurvey(mission, (MissionPayload.Survey) mission.payload(), instances, tables);
            case ASTEROID -> verifyAsteroid(mission, (MissionPayload.Resource) mission.payload(), instances, satellites,
                    tables);
            case GAS -> verifyGas(mission, (MissionPayload.Resource) mission.payload(), satellites, tables);
        };
    }

    private static Report verifySurvey(MissionState mission, MissionPayload.Survey survey,
                                       Function<UUID, Optional<AsteroidInstance>> instances, ResourceTables tables) {
        List<AsteroidType> candidates = ResourceAlgorithms.candidates(tables.asteroidTypes(), mission.targetBodyId());
        if (candidates.isEmpty() || !ResourceAlgorithms.fingerprint(candidates).equals(survey.candidateFingerprint())) {
            return new Report(Outcome.VERSION_CHANGED, "the candidate fingerprint of " + mission.targetBodyId()
                    + " differs from the recorded " + survey.candidateFingerprint());
        }
        List<ResourceAlgorithms.GeneratedInstance> expected =
                ResourceAlgorithms.survey(mission.seed(), candidates, survey.instances().size());
        for (int index = 0; index < expected.size(); index++) {
            AsteroidInstance stored = instances.apply(survey.instances().get(index)).orElse(null);
            if (stored == null) {
                return new Report(Outcome.INPUTS_UNAVAILABLE, "instance " + survey.instances().get(index) + " was pruned");
            }
            ResourceAlgorithms.GeneratedInstance generated = expected.get(index);
            if (!generated.type().id().equals(stored.asteroidType()) || generated.seed() != stored.seed()
                    || !generated.yield().equals(stored.yield())) {
                return new Report(Outcome.MISMATCH, "survey-v1 instance " + index + " differs from the record");
            }
        }
        return new Report(Outcome.MATCH, "survey-v1 over " + candidates.size() + " candidates");
    }

    private static Report verifyAsteroid(MissionState mission, MissionPayload.Resource resource,
                                         Function<UUID, Optional<AsteroidInstance>> instances,
                                         Function<UUID, Optional<SatelliteState>> satellites, ResourceTables tables) {
        AsteroidInstance instance = mission.instanceId().flatMap(instances).orElse(null);
        SatelliteState satellite = satellites.apply(mission.satelliteId()).orElse(null);
        if (instance == null || satellite == null) {
            return new Report(Outcome.INPUTS_UNAVAILABLE, instance == null ? "the instance was pruned"
                    : "the satellite was decommissioned");
        }
        String version = ResourceAlgorithms.ASTEROID_V1 + "/" + instance.asteroidType() + "/" + instance.tableVersion();
        List<RewardEntry> expected = ResourceAlgorithms.truncate(instance.yield(), satellite.blueprint().stats().cargo());
        if (!version.equals(mission.rewardVersion()) || !expected.equals(resource.reward())) {
            return new Report(Outcome.MISMATCH, "the reward is not the instance yield truncated to the cargo");
        }
        Report yield = verifyInstance(instance, tables);
        return yield.outcome() == Outcome.MISMATCH ? yield
                : new Report(Outcome.MATCH, "truncated to cargo " + satellite.blueprint().stats().cargo()
                        + "; instance yield " + yield.outcome());
    }

    private static Report verifyGas(MissionState mission, MissionPayload.Resource resource,
                                    Function<UUID, Optional<SatelliteState>> satellites, ResourceTables tables) {
        GasTable table = tables.gasTable(mission.targetBodyId()).orElse(null);
        SatelliteState satellite = satellites.apply(mission.satelliteId()).orElse(null);
        if (table == null || satellite == null) {
            return new Report(Outcome.INPUTS_UNAVAILABLE, table == null ? "the gas table is gone"
                    : "the satellite was decommissioned");
        }
        String version = ResourceAlgorithms.GAS_V1 + "/" + table.id() + "/" + table.tableVersion();
        if (!version.equals(mission.rewardVersion())) {
            return new Report(Outcome.VERSION_CHANGED, "gas table " + table.id() + " is now " + table.tableVersion());
        }
        if (resource.reward().size() != 1) {
            return new Report(Outcome.MISMATCH, "a gas reward has exactly one product");
        }
        RewardEntry reward = resource.reward().get(0);
        GasTable.Product product = table.products().stream().filter(candidate -> candidate.item().equals(reward.item()))
                .findFirst().orElse(null);
        if (product == null) {
            return new Report(Outcome.MISMATCH, "the table has no product " + reward.item());
        }
        int amount = ResourceAlgorithms.gas(product.amountPer1000Ticks(), satellite.blueprint().stats().rating(),
                satellite.blueprint().stats().cargo(), 100).amount();
        return amount == reward.count()
                ? new Report(Outcome.MATCH, "gas-v1 amount " + amount)
                : new Report(Outcome.MISMATCH, "gas-v1 amount " + amount + " differs from " + reward.count());
    }
}
