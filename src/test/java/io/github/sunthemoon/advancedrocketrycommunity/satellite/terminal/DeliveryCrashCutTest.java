package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.TerminalObservations;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-051 section 11 crash cuts with the real registry codec and the real terminal delivery section: each cut
 * restarts from a registry file and a chunk tag written at different moments around one claim. After
 * reconciliation the reward exists exactly once and the instance never pays twice.
 */
final class DeliveryCrashCutTest {
    private static final ResourceLocation SYSTEM = ModIdentity.id("sol");
    private static final List<RewardEntry> REWARD = List.of(new RewardEntry(new ResourceLocation("minecraft", "iron_ore"),
            30), new RewardEntry(new ResourceLocation("minecraft", "cobblestone"), 34));
    private static final UUID TERMINAL_OWNER = UUID.randomUUID();

    private static final String BLOCK_ENTITY = "advancedrocketrycommunity:satellite_terminal";
    private static final net.minecraft.core.BlockPos POSITION = new net.minecraft.core.BlockPos(4096, 70, -4096);

    @TempDir
    Path files;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private final UUID owner = TERMINAL_OWNER;
    private UUID missionId;
    private UUID instanceId;
    private UUID terminalId;
    private CompoundTag registryBefore;
    private CompoundTag registryAfter;
    private CompoundTag chunkBefore;
    private CompoundTag chunkAfter;

    @Test
    void aClaimInNeitherStoreIsClaimedAgain() {
        prepare();
        Restart restart = restart(registryBefore, chunkBefore);
        assertEquals(DeliveryReconciliation.Action.NONE, restart.reconcile());
        assertEquals(SatelliteOperationCode.SUCCESS, restart.claim());
        assertEquals(1, restart.payments());
    }

    @Test
    void aChunkAheadOfTheRegistryRecoversTheClaimWithoutItems() {
        prepare();
        Restart restart = restart(registryBefore, chunkAfter);
        assertEquals(DeliveryReconciliation.Action.SET_CLAIMED_PAID_HERE, restart.reconcile());
        assertEquals(MissionStatus.CLAIMED, restart.data.mission(missionId).orElseThrow().status());
        assertEquals(InstanceState.DEPLETED, restart.data.instance(instanceId).orElseThrow().state());
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, restart.claimCode());
        assertEquals(1, restart.payments());
    }

    @Test
    void aRegistryAheadOfTheChunkRematerializesOnce() {
        prepare();
        Restart restart = restart(registryAfter, chunkBefore);
        assertEquals(DeliveryReconciliation.Action.REMATERIALIZE, restart.reconcile());
        assertEquals(DeliveryReconciliation.Action.WAIT, restart.reconcile());
        assertEquals(1, restart.payments());
    }

    @Test
    void bothStoresSavedAcknowledgeThenReleaseTheReceipt() {
        prepare();
        Restart restart = restart(registryAfter, chunkAfter);
        assertEquals(DeliveryReconciliation.Action.ACKNOWLEDGE, restart.reconcile());
        restart.flush();
        assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, restart.reconcile());
        assertTrue(restart.terminal.receiptIds().isEmpty());
        assertEquals(1, restart.payments(), "the delivered items stay; only the receipt is released");
    }

    @Test
    void aBlockedRegistryChangesNothingAndKeepsTheReceipt() {
        prepare();
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        Restart restart = restart(future, chunkAfter);
        assertFalse(restart.data.operational());
        assertEquals(DeliveryReconciliation.Action.NONE_REGISTRY_BLOCKED, restart.reconcile());
        assertEquals(List.of(missionId), List.copyOf(restart.terminal.receiptIds()));
        assertEquals(1, restart.payments());
    }

    @Test
    void anOperatorCancelOfARevertedClaimHoldsTheInstance() {
        prepare();
        Restart restart = restart(registryBefore, chunkAfter);
        assertEquals(SatelliteOperationCode.SUCCESS, restart.data.cancel(missionId, UUID.randomUUID(), true,
                20_000L).code());
        assertEquals(InstanceState.QUARANTINED, restart.data.instance(instanceId).orElseThrow().state());
        assertEquals(DeliveryReconciliation.Action.KEEP_AUDIT_PAID_THEN_CANCELLED, restart.reconcile());
        assertEquals("PAID_THEN_CANCELLED", restart.lastEvent);
        assertEquals(DeliveryReconciliation.Action.KEEP_AUDIT_PAID_THEN_CANCELLED, restart.reconcile());
        assertEquals(null, restart.lastEvent, "the conflict is reported once");
        assertEquals(1, restart.payments());
    }

    @Test
    void aLostChunkWriteAfterADurableAcknowledgementLosesButNeverDuplicates() {
        prepare();
        Restart first = restart(registryAfter, chunkAfter);
        first.reconcile();
        first.flush();
        CompoundTag acknowledged = first.data.save(new CompoundTag());
        Restart restart = restart(acknowledged, chunkBefore);
        assertEquals(DeliveryReconciliation.Action.NONE, restart.reconcile());
        assertEquals(0, restart.payments(), "the documented residual: lost, not duplicated");
    }

    /** One registry, one terminal, one asteroid mission bound to it, READY and durable; then one claim. */
    private void prepare() {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0L);
        UUID survey = craft(data, SatelliteKind.SURVEY);
        UUID miner = craft(data, SatelliteKind.ASTEROID_MINER);
        UUID surveyMission = UUID.randomUUID();
        instanceId = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, data.resources(missions -> missions.startSurvey(
                new ResourceMissions.SurveyStart(survey, surveyMission, owner, SYSTEM, "0123456789abcdef", 1, 100, 1L,
                        (count, time) -> List.of(new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION,
                                instanceId, owner, SYSTEM, ModIdentity.id("small_asteroid"), "0123456789abcdef",
                                "0123456789abcdef", 5L, REWARD, time, OptionalLong.empty(), InstanceState.PENDING,
                                surveyMission, Optional.empty()))), 10L)).code());
        assertEquals(SatelliteOperationCode.SUCCESS, data.resources(missions -> missions.claimSurvey(surveyMission,
                owner, 168_000L, 200L)).code());
        TerminalDelivery terminal = TerminalDelivery.create();
        terminalId = terminal.terminalId();
        missionId = UUID.randomUUID();
        assertEquals(SatelliteOperationCode.SUCCESS, data.resources(missions -> missions.startResource(
                new ResourceMissions.ResourceStart(miner, missionId, owner, MissionKind.ASTEROID, SYSTEM,
                        Optional.of(instanceId), REWARD, "asteroid-v1/test/0123456789abcdef", 1_000, 9L, terminalId,
                        Optional.empty()), 300L)).code());
        data.flush(files.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        data.completeDue(2_000L);
        data.flush(files.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        registryBefore = data.save(new CompoundTag());
        chunkBefore = write(terminal);

        assertEquals(SatelliteOperationCode.SUCCESS, data.resources(missions -> missions.claimResource(missionId,
                owner, terminalId, terminal::room, 2_100L)).code());
        terminal.pay(missionId, REWARD);
        registryAfter = data.save(new CompoundTag());
        chunkAfter = write(terminal);
    }

    /**
     * A restart: the registry file is loaded, and the terminal's saved section goes through the production
     * chunk-load path (C9-L8(d)): {@code TerminalChunkEvents.entries} parses the chunk tag into a
     * {@code TerminalObservations} record, the block-entity load starts unpersisted, and the terminal then
     * consumes the observation at its position.
     */
    private Restart restart(CompoundTag registry, CompoundTag chunk) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", BLOCK_ENTITY);
        entry.putInt("x", POSITION.getX());
        entry.putInt("y", POSITION.getY());
        entry.putInt("z", POSITION.getZ());
        entry.put(SatelliteTerminalBlockEntity.DATA_KEY, chunk.copy());
        net.minecraft.nbt.ListTag blockEntities = new net.minecraft.nbt.ListTag();
        blockEntities.add(entry);
        CompoundTag chunkTag = new CompoundTag();
        chunkTag.put(TerminalChunkEvents.BLOCK_ENTITIES, blockEntities);
        TerminalObservations observations = new TerminalObservations();
        TerminalChunkEvents.entries(chunkTag, BLOCK_ENTITY).forEach(observed -> observations.record(
                observed.terminalId(), observed.pos(), observed.receipts()));
        TerminalDelivery terminal = TerminalDelivery.read(chunk);
        assertTrue(terminal.unpersisted(), "a block-entity load persists nothing by itself");
        observations.consume(terminal.terminalId(), POSITION)
                .ifPresent(observation -> terminal.observed(terminal.terminalId(), observation.receipts()));
        assertTrue(terminal.idPersisted(), "the chunk-load observation persists the terminal ID");
        return new Restart(SatelliteMissionSavedData.load(registry.copy()), terminal);
    }

    private final class Restart {
        private final SatelliteMissionSavedData data;
        private final TerminalDelivery terminal;
        private String lastEvent;
        private long time = 30_000L;

        private Restart(SatelliteMissionSavedData data, TerminalDelivery terminal) {
            this.data = data;
            this.terminal = terminal;
        }

        DeliveryReconciliation.Action reconcile() {
            ResourceMissions.Reconciled reconciled = data.reconcile(terminal.terminalId(), missionId,
                    terminal.receipt(missionId), Optional.empty(), time++);
            lastEvent = terminal.apply(reconciled.action(), missionId, reconciled.mission());
            return reconciled.action();
        }

        SatelliteOperationCode claim() {
            SatelliteOperationCode code = claimCode();
            if (code == SatelliteOperationCode.SUCCESS) {
                terminal.pay(missionId, REWARD);
            }
            return code;
        }

        SatelliteOperationCode claimCode() {
            return data.resources(missions -> missions.claimResource(missionId, owner, terminal.terminalId(),
                    terminal::room, time++)).code();
        }

        void flush() {
            data.flush(files.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        }

        /** Rewards materialized at the terminal, in whole rewards. */
        int payments() {
            int items = terminal.bufferItems();
            int reward = REWARD.stream().mapToInt(RewardEntry::count).sum();
            assertEquals(0, items % reward);
            return items / reward;
        }
    }

    private static CompoundTag write(TerminalDelivery terminal) {
        CompoundTag tag = new CompoundTag();
        terminal.write(tag);
        return tag;
    }

    private UUID craft(SatelliteMissionSavedData data, SatelliteKind kind) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id(kind.id() + "_primary")), false, new SatelliteStats(4, 10_720, 1_000, 1, 10));
        data.launchIdle(time -> SatelliteState.launchIdle(satelliteId, ModIdentity.id(kind.id()), owner, time,
                ModIdentity.id("earth"), blueprint, state), 0L);
        return satelliteId;
    }
}
