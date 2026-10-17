package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Empty dedicated-server fixture for ordinary C0-C8, never settlement or model-bank reconciliation. */
final class EscrowReconciliationProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private final java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean();
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private BlockEntity source, second;
    private volatile boolean l1Writing, firstAcknowledged, done;
    private volatile boolean laterFrontierProved;
    private volatile String selected = "";
    private boolean secondPrepared;
    private int step;
    private long began;

    EscrowReconciliationProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        EscrowTags.require(cut.startsWith("CR_"), "Unknown ordinary reconciliation fixture");
    }

    void checkpoint(String name) {
        if (recovery || done || paused.get()) { return; }
        if (cut.equals("CR_STALE_BIND") && name.equals("C7_ACKNOWLEDGED")) { assertStaleBindingRefused(); }
        if (cut.equals("CR_LATER_L2_REPLACED") && name.equals("C6_JOINT_READBACK") && data.ledger().records().size() == 2) {
            laterFrontierProved = true;
        }
        if (name.equals("C4_SOURCE_SELECTED")) { selected = "SOURCE"; }
        if (name.equals("C4_DESTINATION_SELECTED")) { selected = "DESTINATION"; }
        if (l1Writing && (cut.equals("CR_L1_BEFORE") && name.equals("K1") || cut.equals("CR_L1_REPLACED") && name.equals("K2"))
                || firstAcknowledged && (cut.equals("CR_L2_BEFORE") && name.equals("K8") || cut.equals("CR_L2_REPLACED") && name.equals("K9"))
                || cut.equals("CR_" + name)
                || name.startsWith("BARRIER_") && cut.equals("CR_" + selected + "_" + name.substring("BARRIER_".length()))
                || cut.equals("CR_STALE_BIND") && name.equals("C7_ACKNOWLEDGED")
                // The writer labels a nonempty replacement K2, including A's partial L2 with B retained.
                || cut.equals("CR_LATER_L2_REPLACED") && laterFrontierProved && name.equals("K2")) { pause(); }
        if (cut.equals("CR_LATER_WAIT") && name.equals("C3_LATER_MEMBER_WAIT") && data.ledger().records().size() == 2) {
            var coordinator = data.bind(server); var endpoint = data.ledger().records().get(0).destination();
            EscrowTags.require(!coordinator.frozen(endpoint.key()) && rocket.escrowSection().epoch() == 1,
                    "A retired/froze before the acknowledged later member applied"); pause();
        }
    }

    private void pause() {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RECONCILIATION_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Ordinary reconciliation forced stop did not arrive within 30 seconds");
    }

    void tick() {
        var level = server.overworld();
        if (done || paused.get() || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong()) || !level.isPositionEntityTicking(POSITION)) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); data.bind(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); data.bind(server).autosave(); }
        }
        var coordinator = data.bind(server); var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !coordinator.operational()) { return; }
        if (server.getTickCount() - began > 2_000) { throw new IllegalStateException("Ordinary reconciliation fixture exceeded its bounded wait"); }
        if (recovery) { recover(); return; }
        if (step == 0) {
            if (data.ledger().writerGeneration() == 0 || !coordinator.writerIdle()) { coordinator.autosave(); return; }
            var fixture = EscrowTestEndpoints.create(level, POSITION, data.ledger().id()); source = fixture.source();
            if (chain()) { second = EscrowTestEndpoints.chainSource(fixture, POSITION.offset(4, 0, 0)).source(); }
            fixture.bank().discard();
            rocket = EscrowRocketAuditProbe.createRocket(level, fixture.logical(), true);
            EscrowTags.require(level.addFreshEntity(rocket), "Native C0 source publication refused");
            // Persist only the initial world/BE/entity baseline. No fixture save follows any C0-C8 action or cut.
            EscrowTags.require(server.saveEverything(false, true, true), "Initial native endpoint baseline refused");
            if (cut.equals("CR_C0")) { pause(); }
            prepare(source, false); step = 1;
        }
        if (step == 1) {
            coordinator.commitPreparations();
            if (firstAcknowledged && (cut.equals("CR_LATER_WAIT") || cut.equals("CR_LATER_L2_REPLACED")) && !secondPrepared
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000) {
                prepare(second, true); secondPrepared = true;
            }
            if (mismatch() && data.ledger().records().stream().anyMatch(op -> op.reason() == EscrowReason.OTHER_STATE)
                    && coordinator.writerIdle()) { pause(); }
        }
    }

    private boolean chain() { return cut.equals("CR_LATER_WAIT") || cut.equals("CR_STALE_BIND") || cut.equals("CR_LATER_L2_REPLACED"); }
    private boolean mismatch() { return cut.equals("CR_C2_MISMATCH") || cut.equals("CR_C2_DESTINATION_MISMATCH"); }

    void beforeReconciliation() {
        if (!recovery) { return; }
        var existing = ResourceEscrowSavedData.existing(server);
        if (existing != null && existing.operational()) {
            var coordinator = existing.bind(server);
            for (var op : existing.ledger().records()) { coordinator.registerReconciliationTestSource(server, op.source()); }
        }
    }

    private void prepare(BlockEntity from, boolean later) {
        var coordinator = data.bind(server); var operation = operation(from);
        var sourceState = EscrowTestEndpoints.state(from.getPersistentData(), data.ledger().id(), false);
        if (recovery && cut.equals("CR_STALE_BIND")) {
            EscrowTags.require(sourceState.markers().isEmpty() && rocket.escrowSection().markers().isEmpty(), "C0 incorporated stale retired markers");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RECONCILIATION_BIND sequence={} markers=0", operation.sequence());
        }
        l1Writing = true;
        java.util.function.Consumer<Boolean> acknowledged = committed -> {
            EscrowTags.require(committed, "Native C1 preparation failed"); l1Writing = false;
            if (!later) {
                firstAcknowledged = true;
                if (!recovery && cut.equals("CR_L1_ACK")) { pause(); }
                if (!recovery && cut.equals("CR_C2_MISMATCH")) {
                    source.getPersistentData().putString("c37_capture_fault", "changed after L1");
                }
            }
        };
        if (cut.equals("CR_C2_DESTINATION_MISMATCH")) {
            // Deliberately inconsistent section commitment; canonical C0 remains valid, real C2 must refuse.
            var admission = EscrowNativeBinding.captureCurrentTest(operation, rocket, from, data.ledger());
            coordinator.registerReconciliationTestSource(server, operation.source());
            coordinator.prepareTest(destinationMismatch(admission.operation()), admission.before(), acknowledged);
        } else { coordinator.prepareNativeTest(operation, rocket, from, acknowledged); }
    }

    static EscrowOperation destinationMismatch(EscrowOperation operation) {
        var encoded = EscrowCodec.operation(operation);
        EscrowFlightTables.digest(encoded.getCompound("native_binding"), "after_wrapper", RocketNativeResources.hash("C40 destination fault"));
        return EscrowCodec.operation(encoded);
    }

    private EscrowOperation operation(BlockEntity from) {
        var coordinator = data.bind(server); var logical = rocket.assemblyTransactionId().orElseThrow();
        var fuel = rocket.flightData().orElseThrow().fuel();
        var after = fuel.withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, fuel.amount() + 1_000);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, rocket.level().dimension().location().toString(), rocket.getUUID(), logical,
                EscrowAdapterIds.ROCKET, rocket.chunkPosition().x, 0, rocket.chunkPosition().z, EscrowEndpoint.Unit.PRIMARY,
                rocket.escrowSection().epoch(), fuel.resourceSignature(), after.resourceSignature());
        var payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
        var guard = RocketTransferSavedData.get(server).acknowledgedGuard(logical).orElseThrow();
        return new EscrowOperation(UUID.randomUUID(), coordinator.nextSequence(), rocket.ownerId().orElseThrow(), server.overworld().getGameTime(),
                EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload, RocketNativeResources.signature(payload), reference(from), destination,
                new EscrowGuard(logical, null, Math.max(1, guard.sequence()), guard.rootDigest()));
    }

    private void assertStaleBindingRefused() {
        EscrowTags.require(!rocket.escrowSection().markers().isEmpty(), "Stale-binding cut lost its unpruned marker");
        try { EscrowNativeBinding.captureCurrentTest(operation(second), rocket, second, data.ledger()); }
        catch (IllegalArgumentException refused) {
            EscrowTags.require("C0 waits for stale marker pruning".equals(refused.getMessage()), "Wrong stale-binding refusal");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RECONCILIATION_STALE_REFUSED markers={}", rocket.escrowSection().markers().size());
            return;
        }
        throw new IllegalStateException("Native binding accepted an unpruned retired marker");
    }

    private EscrowEndpoint reference(BlockEntity from) {
        var state = EscrowTestEndpoints.state(from.getPersistentData(), data.ledger().id(), false); var pos = from.getBlockPos();
        var identity = from.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity");
        return new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, from.getLevel().dimension().location().toString(), identity, null, "minecraft:chest",
                pos.getX(), pos.getY(), pos.getZ(), EscrowEndpoint.Unit.TANK, state.epoch(), state.signature(),
                RocketNativeResources.hash("arce-escrow-fixture-1:" + identity + ":" + (EscrowTestEndpoints.amount(from.getPersistentData()) - 1_000)));
    }

    private void recover() {
        var level = server.overworld(); var coordinator = data.bind(server);
        if (rocket == null) {
            var rockets = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(8));
            if (rockets.isEmpty()) { return; }
            EscrowTags.require(rockets.size() == 1, "Restart duplicated the native rocket"); rocket = rockets.get(0);
            source = level.getBlockEntity(POSITION); second = chain() ? level.getBlockEntity(POSITION.offset(4, 0, 0)) : null;
            EscrowTags.require(source != null && (!chain() || second != null), "Restart lost a fixture source");
        }
        for (var op : data.ledger().records()) { coordinator.registerReconciliationTestSource(server, op.source()); }
        if (cut.equals("CR_STALE_BIND") && data.ledger().issuedThrough() == 1 && data.ledger().records().isEmpty()
                && coordinator.writerIdle() && !secondPrepared && rocket.escrowSection().markers().isEmpty()) {
            prepare(second, true); secondPrepared = true;
        }
        coordinator.commitPreparations();
        boolean before = cut.equals("CR_C0") || cut.equals("CR_L1_BEFORE"), repair = mismatch();
        if (!coordinator.writerIdle() || server.getTickCount() - began < 100 || !repair && !data.ledger().records().isEmpty()) { return; }
        long amount = before || repair ? 0 : chain() ? 2_000 : 1_000;
        EscrowTags.require(rocket.flightData().orElseThrow().fuel().totalAmount() == amount
                && EscrowTestEndpoints.amount(source.getPersistentData()) == (amount == 0 && !cut.equals("CR_C2_DESTINATION_MISMATCH") ? 5_000 : 4_000)
                && (second == null || EscrowTestEndpoints.amount(second.getPersistentData()) == 4_000)
                && rocket.escrowSection().epoch() == amount / 1_000 && rocket.escrowSection().markers().isEmpty()
                && sourceMarkersMatch(source, retainedSourceMarker())
                && (second == null || sourceMarkersMatch(second, false))
                && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty(), "Restart violated native conservation, epoch or pruning");
        if (repair) {
            EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().records().get(0).phase() == EscrowOperation.Phase.REPAIR
                    && data.ledger().records().get(0).reason() == EscrowReason.OTHER_STATE && coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow()),
                    "C2 mismatch did not remain sticky REPAIR");
        } else { EscrowTags.require(!coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow()), "Completed ordinary prefix stayed locked"); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_RECONCILIATION_RESULT cut={} fuel={} records={} epoch={} source={}",
                cut, amount, data.ledger().records().size(), rocket.escrowSection().epoch(), rocket.getUUID()); done = true;
    }

    private boolean retainedSourceMarker() {
        return List.of("CR_L2_REPLACED", "CR_C7_ACKNOWLEDGED", "CR_C8_PRUNE_BEFORE", "CR_C8_PRUNE_AFTER",
                "CR_C8_RELEASED", "CR_STALE_BIND", "CR_LATER_L2_REPLACED").contains(cut);
    }

    private boolean sourceMarkersMatch(BlockEntity from, boolean retained) {
        var state = EscrowTestEndpoints.state(from.getPersistentData(), data.ledger().id(), false);
        if (cut.equals("CR_C2_DESTINATION_MISMATCH")) {
            var op = data.ledger().records().get(0);
            return state.epoch() == op.source().afterEpoch() && state.signature().equals(op.source().afterSignature())
                    && state.markers().equals(List.of(new EscrowPrefix.Link(op, op.source(), "SOURCE").marker()));
        }
        if (!retained) { return state.markers().isEmpty(); }
        return state.markers().size() == 1 && state.markers().get(0).sequence() == 1
                && state.markers().get(0).role().equals("SOURCE") && state.markers().get(0).epochAfter() == state.epoch();
    }
}
