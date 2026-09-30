package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-051: instance lifecycle, starts, claims, cancels, reconciliation and the 64 rebind orderings. */
final class ResourceMissionsTest {
    private static final ResourceLocation SYSTEM = ModIdentity.id("sol");
    private static final ResourceLocation TYPE = ModIdentity.id("small_asteroid");
    private static final String VERSION = "0123456789abcdef";
    private static final List<RewardEntry> YIELD = List.of(
            new RewardEntry(new ResourceLocation("minecraft", "iron_ore"), 30),
            new RewardEntry(new ResourceLocation("minecraft", "cobblestone"), 170));
    private static final long TTL = 168_000L;
    private static final UUID T1 = new UUID(0L, 1L);
    private static final UUID T2 = new UUID(0L, 2L);

    private final SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L);
    private final ResourceMissions resources = registry.resources();
    private final UUID owner = UUID.randomUUID();

    @Test
    void aSurveyCreatesHiddenInstancesThatBecomeAvailableAndExpire() {
        UUID survey = craft(SatelliteKind.SURVEY);
        UUID missionId = UUID.randomUUID();
        SatelliteOperationResult started = resources.startSurvey(surveyStart(survey, missionId, 4), 100L);
        assertEquals(SatelliteOperationCode.SUCCESS, started.code());
        assertEquals(4, resources.liveInstances(owner));
        List<AsteroidInstance> created = resources.owned(owner);
        assertTrue(created.stream().allMatch(instance -> instance.state() == InstanceState.PENDING));
        assertEquals(List.of(), resources.available(owner, SYSTEM), "PENDING instances are hidden");
        assertEquals(SatelliteOperationCode.MISSION_BUSY, resources.startSurvey(surveyStart(survey,
                UUID.randomUUID(), 4), 101L).code());

        assertEquals(SatelliteOperationCode.NOT_READY, resources.claimSurvey(missionId, owner, TTL, 1_099L).code());
        SatelliteOperationResult claimed = resources.claimSurvey(missionId, owner, TTL, 1_100L);
        assertEquals(SatelliteOperationCode.SUCCESS, claimed.code());
        assertEquals(MissionStatus.CLAIMED, claimed.mission().orElseThrow().status());
        assertTrue(registry.satellite(survey).orElseThrow().currentMissionId().isEmpty(), "the satellite is released");
        List<AsteroidInstance> available = resources.available(owner, SYSTEM);
        assertEquals(4, available.size());
        assertTrue(available.stream().allMatch(instance -> instance.expiresAt().equals(OptionalLong.of(1_100L + TTL))));
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, resources.claimSurvey(missionId, owner, TTL, 1_200L).code());

        SchedulerPass expiry = registry.completeDue(1_100L + TTL);
        assertEquals(4, expiry.instanceChanges());
        assertTrue(expiry.changed());
        assertTrue(resources.owned(owner).stream().allMatch(instance -> instance.state() == InstanceState.EXPIRED));
        assertEquals(0, resources.liveInstances(owner));
        registry.completeDue(1_100L + TTL + InstanceLedger.REMOVE_AFTER_TICKS - 1L);
        assertEquals(4, resources.owned(owner).size());
        registry.completeDue(1_100L + TTL + InstanceLedger.REMOVE_AFTER_TICKS);
        assertEquals(List.of(), resources.owned(owner), "spent records are removed 1,200 ticks later");
        assertEquals(0L, registry.reservedBytes("INSTANCES"));
    }

    @Test
    void surveysRespectTheLiveInstanceLimitAndCancelDeletesPendingInstances() {
        registry.applyLimits(limits(6));
        UUID first = craft(SatelliteKind.SURVEY);
        UUID second = craft(SatelliteKind.SURVEY);
        UUID third = craft(SatelliteKind.SURVEY);
        assertEquals(SatelliteOperationCode.SUCCESS, resources.startSurvey(surveyStart(first, UUID.randomUUID(), 4),
                0L).code());
        UUID partial = UUID.randomUUID();
        SatelliteOperationResult limited = resources.startSurvey(surveyStart(second, partial, 4), 0L);
        assertEquals(SatelliteOperationCode.SUCCESS, limited.code());
        assertEquals(2, ((MissionPayload.Survey) limited.mission().orElseThrow().payload()).instances().size(),
                "a survey is limited to the owner's remaining capacity");
        assertEquals(SatelliteOperationCode.OWNER_LIMIT, resources.startSurvey(surveyStart(third,
                UUID.randomUUID(), 4), 0L).code());

        SatelliteOperationResult cancelled = registry.cancel(partial, owner, false, 10L);
        assertEquals(SatelliteOperationCode.SUCCESS, cancelled.code());
        assertEquals(4, resources.liveInstances(owner));
        assertEquals(4, resources.owned(owner).size(), "the cancelled survey's PENDING instances are deleted");
        assertEquals(SatelliteOperationCode.SUCCESS, resources.startSurvey(surveyStart(third,
                UUID.randomUUID(), 4), 11L).code());
    }

    @Test
    void anAsteroidRewardIsPaidOnceAtItsBoundTerminalAfterADurableStart() {
        AsteroidInstance instance = availableInstance();
        UUID miner = craft(SatelliteKind.ASTEROID_MINER);
        UUID missionId = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, resources.startResource(asteroidStart(miner, missionId,
                instance.instanceId(), T1), 2_000L).code());
        assertEquals(InstanceState.ALLOCATED, registry.instance(instance.instanceId()).orElseThrow().state());
        assertEquals(SatelliteOperationCode.MISSION_BUSY, resources.startResource(asteroidStart(miner,
                UUID.randomUUID(), instance.instanceId(), T1), 2_001L).code());

        assertEquals(SatelliteOperationCode.NOT_READY, claim(missionId, T1, 2_100L).code());
        SatelliteOperationResult unsaved = claim(missionId, T1, 3_000L);
        assertEquals(SatelliteOperationCode.AWAITING_WORLD_SAVE, unsaved.code(),
                "save_epoch must exceed start_epoch (ADR-051 section 6)");
        assertTrue(unsaved.changed(), "the lazy completion is still a change");
        assertEquals(SatelliteOperationCode.WRONG_TERMINAL, claim(missionId, T2, 3_000L).code());
        assertEquals(SatelliteOperationCode.DELIVERY_BUFFER_FULL, resources.claimResource(missionId, owner, T1,
                reward -> SatelliteOperationCode.DELIVERY_BUFFER_FULL, 3_001L).code());
        persist();
        SatelliteOperationResult paid = claim(missionId, T1, 3_002L);
        assertEquals(SatelliteOperationCode.SUCCESS, paid.code());
        MissionPayload.Resource resource = (MissionPayload.Resource) paid.mission().orElseThrow().payload();
        assertEquals(Optional.of(T1), resource.paidTerminal());
        assertFalse(resource.acknowledged());
        assertEquals(List.of(new RewardEntry(new ResourceLocation("minecraft", "iron_ore"), 30),
                new RewardEntry(new ResourceLocation("minecraft", "cobblestone"), 34)), resource.reward(),
                "the yield truncated to one cargo unit (64 items)");
        AsteroidInstance depleted = registry.instance(instance.instanceId()).orElseThrow();
        assertEquals(InstanceState.DEPLETED, depleted.state());
        assertTrue(registry.satellite(miner).orElseThrow().currentMissionId().isEmpty());
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, claim(missionId, T1, 3_003L).code());
        assertEquals(SatelliteOperationCode.TARGET_NOT_ALLOWED, resources.startResource(asteroidStart(miner,
                UUID.randomUUID(), instance.instanceId(), T1), 3_004L).code(), "a depleted instance never pays again");
    }

    @Test
    void cancellationReturnsOrHoldsTheInstance() {
        AsteroidInstance instance = availableInstance();
        UUID miner = craft(SatelliteKind.ASTEROID_MINER);
        UUID owned = UUID.randomUUID();
        resources.startResource(asteroidStart(miner, owned, instance.instanceId(), T1), 2_000L);
        assertEquals(SatelliteOperationCode.WRONG_TERMINAL, registry.cancel(owned, owner, false, 2_001L).code());
        assertEquals(SatelliteOperationCode.WRONG_TERMINAL, resources.cancel(owned, owner, false, Optional.of(T2),
                2_001L).code());
        assertEquals(SatelliteOperationCode.SUCCESS, resources.cancel(owned, owner, false, Optional.of(T1),
                2_002L).code());
        AsteroidInstance returned = registry.instance(instance.instanceId()).orElseThrow();
        assertEquals(InstanceState.AVAILABLE, returned.state());
        assertTrue(returned.allocatedMission().isEmpty());

        UUID operated = UUID.randomUUID();
        resources.startResource(asteroidStart(miner, operated, instance.instanceId(), T1), 2_003L);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.cancel(operated, UUID.randomUUID(), true,
                2_004L).code());
        assertEquals(InstanceState.QUARANTINED, registry.instance(instance.instanceId()).orElseThrow().state(),
                "an operator cancel never frees the instance");
        assertEquals(SatelliteOperationCode.SUCCESS, resources.releaseInstance(instance.instanceId(), TTL,
                2_005L).code());
        assertEquals(InstanceState.AVAILABLE, registry.instance(instance.instanceId()).orElseThrow().state());
        assertEquals(SatelliteOperationCode.IDEMPOTENT, resources.releaseInstance(instance.instanceId(), TTL,
                2_006L).code());
    }

    @Test
    void reconciliationRecoversAcknowledgesAndReleasesReceipts() {
        UUID missionId = startedAsteroid(T1);
        persist();
        // The chunk was ahead: the registry still says ACTIVE, the terminal holds a receipt.
        ResourceMissions.Reconciled recovered = resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_500L);
        assertEquals(DeliveryReconciliation.Action.SET_CLAIMED_PAID_HERE, recovered.action());
        assertTrue(recovered.changed());
        MissionState claimed = recovered.mission().orElseThrow();
        assertEquals(MissionStatus.CLAIMED, claimed.status());
        assertEquals(OptionalLong.of(claimed.completesAtLogicalTime()), claimed.readyAtLogicalTime());
        assertEquals(0, registry.scheduledCount(), "the deadline entry is removed");

        ResourceMissions.Reconciled acknowledged = resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_600L);
        assertEquals(DeliveryReconciliation.Action.ACKNOWLEDGE, acknowledged.action());
        long ackEpoch = registry.saveEpoch();
        assertEquals(DeliveryReconciliation.Action.WAIT, resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_601L).action());
        persist();
        assertEquals(ackEpoch + 1L, registry.saveEpoch());
        assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_602L).action());
        assertEquals(2, registry.finishedMissionCount(), "the claimed survey and the claimed asteroid mission");
        registry.applyLimits(new RegistryLimits(1_024, 64, 1, 3_072, 4_096, 256, 2_048, 16, 10, 2));
        UUID second = startedAsteroid(T1);
        persist();
        resources.claimResource(second, owner, T1, reward -> null, 5_000L);
        registry.completeDue(2_602L + MissionRetention.ELIGIBLE_AFTER_TICKS + 5_000L);
        assertTrue(registry.mission(missionId).isEmpty(), "a durable acknowledgement makes the record prunable");
        assertTrue(registry.mission(second).isPresent(), "an unacknowledged claim is never pruned");
    }

    @Test
    void aClaimThatReachedOnlyTheRegistryIsRematerializedOnceAtThePayingTerminal() {
        UUID missionId = startedAsteroid(T1);
        persist();
        claim(missionId, T1, 3_000L);
        assertEquals(DeliveryReconciliation.Action.REMATERIALIZE, resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.NONE, Optional.empty(), 3_001L).action());
        assertEquals(DeliveryReconciliation.Action.WAIT, resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.UNPERSISTED, Optional.empty(), 3_002L).action());
        assertEquals(DeliveryReconciliation.Action.NONE, resources.reconcile(T2, missionId,
                DeliveryReconciliation.ReceiptView.NONE, Optional.empty(), 3_003L).action(),
                "only the paying terminal may rematerialize");
        assertEquals(DeliveryReconciliation.Action.KEEP_AUDIT_DOUBLE_PAY, resources.reconcile(T2, missionId,
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 3_004L).action());
        assertEquals(SatelliteOperationCode.SUCCESS, resources.purge(missionId).code());
        assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, resources.reconcile(T1, missionId,
                DeliveryReconciliation.ReceiptView.UNPERSISTED, Optional.empty(), 3_005L).action());
    }

    @Test
    void twoOwnersSettleInTheSameTickIndependently() {
        UUID first = startedAsteroid(T1);
        ResourceMissionsTest other = new ResourceMissionsTest();
        // A second owner in the same registry, bound to another terminal.
        UUID secondOwnerMission = startedFor(other.owner, T2);
        persist();
        SatelliteOperationResult one = claim(first, T1, 5_000L);
        SatelliteOperationResult two = resources.claimResource(secondOwnerMission, other.owner, T2, reward -> null,
                5_000L);
        assertEquals(SatelliteOperationCode.SUCCESS, one.code());
        assertEquals(SatelliteOperationCode.SUCCESS, two.code());
        assertEquals(Optional.of(T1), ((MissionPayload.Resource) one.mission().orElseThrow().payload()).paidTerminal());
        assertEquals(Optional.of(T2), ((MissionPayload.Resource) two.mission().orElseThrow().payload()).paidTerminal());
        assertEquals(SatelliteOperationCode.UNAUTHORIZED, resources.claimResource(first, other.owner, T1,
                reward -> null, 5_000L).code(), "another owner cannot settle a mission");
    }

    /** An asteroid mission of {@code missionOwner} in this registry. */
    private UUID startedFor(UUID missionOwner, UUID terminal) {
        UUID survey = craftFor(missionOwner, SatelliteKind.SURVEY);
        UUID miner = craftFor(missionOwner, SatelliteKind.ASTEROID_MINER);
        UUID surveyMission = UUID.randomUUID();
        resources.startSurvey(new ResourceMissions.SurveyStart(survey, surveyMission, missionOwner, SYSTEM, VERSION, 1,
                1_000, 7L, (created, time) -> List.of(new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION,
                UUID.randomUUID(), missionOwner, SYSTEM, TYPE, VERSION, VERSION, 1L, YIELD, time, OptionalLong.empty(),
                InstanceState.PENDING, surveyMission, Optional.empty()))), 0L);
        resources.claimSurvey(surveyMission, missionOwner, TTL, 1_000L);
        UUID instanceId = ((MissionPayload.Survey) registry.mission(surveyMission).orElseThrow().payload())
                .instances().get(0);
        UUID missionId = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, resources.startResource(new ResourceMissions.ResourceStart(miner,
                missionId, missionOwner, MissionKind.ASTEROID, SYSTEM, Optional.of(instanceId), YIELD,
                "asteroid-v1/" + TYPE + "/" + VERSION, 1_000, 9L, terminal, Optional.empty()), 2_000L).code());
        return missionId;
    }

    private UUID craftFor(UUID craftOwner, SatelliteKind kind) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id(kind.id() + "_primary")), false, new SatelliteStats(4, 10_720, 1_000, 4, 10));
        registry.launchIdle(time -> SatelliteState.launchIdle(satelliteId, ModIdentity.id(kind.id()), craftOwner, time,
                ModIdentity.id("earth"), blueprint, state), 0L);
        return satelliteId;
    }

    @Test
    void everyRebindOrderingPaysAndHoldsAsTheVectorsSay() throws Exception {
        JsonObject examples = JsonParser.parseString(Files.readString(
                Path.of("docs/work/v1.6.0-preparation/examples.json"))).getAsJsonObject();
        int orderings = 0;
        int doublePays = 0;
        for (JsonElement element : examples.getAsJsonArray("rebind_orderings")) {
            JsonObject row = element.getAsJsonObject();
            List<String> events = new ArrayList<>();
            row.getAsJsonArray("events").forEach(event -> events.add(event.getAsString()));
            RebindOutcome outcome = new ResourceMissionsTest().replay(events);
            assertEquals(row.get("payments").getAsInt(), outcome.payments(), events.toString());
            assertEquals(InstanceState.valueOf(row.get("instance").getAsString()), outcome.instance(),
                    events.toString());
            orderings++;
            doublePays += outcome.payments() == 2 ? 1 : 0;
        }
        assertEquals(64, orderings);
        assertEquals(21, doublePays, "exactly the residual orderings (new terminal claims first) pay twice");
    }

    /**
     * ADR-051 section 9 premise (crash cut 2): T1 paid and holds a persisted receipt, the registry reverted to
     * READY bound to T1, and an operator rebinds to T2.
     */
    private RebindOutcome replay(List<String> events) {
        UUID missionId = startedAsteroid(T1);
        persist();
        registry.completeDue(4_000L);
        assertEquals(SatelliteOperationCode.SUCCESS, resources.rebind(missionId, T2, Optional.empty(), 4_001L).code());
        Map<UUID, DeliveryReconciliation.ReceiptView> receipts = new HashMap<>(Map.of(
                T1, DeliveryReconciliation.ReceiptView.PERSISTED, T2, DeliveryReconciliation.ReceiptView.NONE));
        int[] payments = {1};
        long time = 4_002L;
        for (String event : events) {
            UUID terminal = event.equals("t1_return") ? T1 : T2;
            DeliveryReconciliation.Action action = resources.reconcile(terminal, missionId, receipts.get(terminal),
                    Optional.empty(), time++).action();
            if (action == DeliveryReconciliation.Action.REMATERIALIZE) {
                payments[0]++;
                receipts.put(terminal, DeliveryReconciliation.ReceiptView.UNPERSISTED);
            } else if (action == DeliveryReconciliation.Action.DROP_RECEIPT) {
                receipts.put(terminal, DeliveryReconciliation.ReceiptView.NONE);
            }
            if (event.equals("t2_claim") && claim(missionId, T2, time++).code() == SatelliteOperationCode.SUCCESS) {
                payments[0]++;
                receipts.put(T2, DeliveryReconciliation.ReceiptView.UNPERSISTED);
            } else if (event.equals("owner_cancel_t2")) {
                resources.cancel(missionId, owner, false, Optional.of(T2), time++);
            }
        }
        AsteroidInstance instance = registry.instance(registry.mission(missionId).orElseThrow().instanceId()
                .orElseThrow()).orElseThrow();
        return new RebindOutcome(payments[0], instance.state());
    }

    private record RebindOutcome(int payments, InstanceState instance) {
    }

    private UUID startedAsteroid(UUID terminal) {
        AsteroidInstance instance = availableInstance();
        UUID miner = craft(SatelliteKind.ASTEROID_MINER);
        UUID missionId = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, resources.startResource(asteroidStart(miner, missionId,
                instance.instanceId(), terminal), 2_000L).code());
        return missionId;
    }

    private SatelliteOperationResult claim(UUID missionId, UUID terminal, long time) {
        return resources.claimResource(missionId, owner, terminal, reward -> null, time);
    }

    private void persist() {
        registry.markChanged();
        registry.markPersisted();
    }

    /** One AVAILABLE instance, made by a claimed one-instance survey. */
    private AsteroidInstance availableInstance() {
        UUID survey = craft(SatelliteKind.SURVEY);
        UUID missionId = UUID.randomUUID();
        resources.startSurvey(surveyStart(survey, missionId, 1), 0L);
        resources.claimSurvey(missionId, owner, TTL, 1_000L);
        MissionPayload.Survey payload = (MissionPayload.Survey) registry.mission(missionId).orElseThrow().payload();
        return registry.instance(payload.instances().get(0)).orElseThrow();
    }

    private ResourceMissions.SurveyStart surveyStart(UUID satelliteId, UUID missionId, int count) {
        return new ResourceMissions.SurveyStart(satelliteId, missionId, owner, SYSTEM, VERSION, count, 1_000, 7L,
                (created, time) -> {
                    List<AsteroidInstance> instances = new ArrayList<>();
                    for (int index = 0; index < created; index++) {
                        instances.add(new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, UUID.randomUUID(),
                                owner, SYSTEM, TYPE, VERSION, VERSION, index, YIELD, time, OptionalLong.empty(),
                                InstanceState.PENDING, missionId, Optional.empty()));
                    }
                    return instances;
                });
    }

    private ResourceMissions.ResourceStart asteroidStart(UUID satelliteId, UUID missionId, UUID instanceId,
                                                         UUID terminal) {
        AsteroidInstance instance = registry.instance(instanceId).orElseThrow();
        List<RewardEntry> reward = List.of(new RewardEntry(new ResourceLocation("minecraft", "iron_ore"), 30),
                new RewardEntry(new ResourceLocation("minecraft", "cobblestone"), 34));
        return new ResourceMissions.ResourceStart(satelliteId, missionId, owner, MissionKind.ASTEROID,
                instance.system(), Optional.of(instanceId), reward, "asteroid-v1/" + TYPE + "/" + VERSION, 1_000, 9L,
                terminal, Optional.empty());
    }

    private UUID craft(SatelliteKind kind) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id(kind.id() + "_primary")), false, new SatelliteStats(4, 10_720, 1_000, 1, 10));
        SatelliteOperationResult launched = registry.launchIdle(time -> SatelliteState.launchIdle(satelliteId,
                ModIdentity.id(kind.id()), owner, time, ModIdentity.id("earth"), blueprint, state), 0L);
        assertEquals(SatelliteOperationCode.SUCCESS, launched.code());
        return satelliteId;
    }

    private static RegistryLimits limits(int instancesPerOwner) {
        return new RegistryLimits(1_024, 64, 128, 3_072, 4_096, 256, 2_048, instancesPerOwner, 10, 2);
    }
}
