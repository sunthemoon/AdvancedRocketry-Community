package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Two sources sharing one vanilla bank. Only the release-test property can instantiate this probe. */
final class EscrowChainReleaseProbe {
    private static final BlockPos A = new BlockPos(64, 100, 64), B = new BlockPos(80, 100, 64);
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String cut;
    private final boolean recovery;
    private final BlockPos secondPosition;
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private EscrowTestEndpoints.Fixture first, second;
    private final List<EscrowOperation> operations = new ArrayList<>();
    private final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
    private int step;
    private boolean waiting, done;

    EscrowChainReleaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(cut.equals("C1") || cut.equals("C2") || cut.equals("C3"), "Unknown chain case");
        secondPosition = cut.equals("C3") ? A.offset(4, 0, 0) : B;
        recovery = System.getProperty("advancedrocketrycommunity.escrowScenario").equals("recover");
        for (BlockPos position : List.of(A, secondPosition)) {
            ChunkPos chunk = new ChunkPos(position);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z); // Fixed test setup, not admission input.
        }
    }
    void tick() {
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(A).toLong())) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server);
            if (recovery) { restore(); } else {
                first = EscrowTestEndpoints.create(level, A, data.ledger().id());
                second = EscrowTestEndpoints.chainSource(first, secondPosition);
            }
        }
        switch (step) {
            case 0 -> {
                if (!server.saveEverything(false, true, true)) { throw new IllegalStateException("Chain baseline save refused"); }
                data.setDirty(); coordinator.autosave(); step = 1;
            }
            case 1 -> {
                if (!coordinator.writerIdle() || data.ledger().writerGeneration() == 0) { return; }
                prepare(first, 2);
            }
            case 2 -> { first.apply(operations.get(0)); step = 3; }
            case 3 -> prove(operations.get(0).destination(), first.bankState(), false, 4);
            case 4 -> prepare(second, 5);
            case 5 -> {
                second.applySource(operations.get(1));
                coordinator.freeze(operations.get(0).source().key(), first.sourceState());
                coordinator.freeze(operations.get(1).source().key(), second.sourceState()); step = 6;
            }
            case 6 -> {
                if (cut.equals("C3")) { proveSources(8); }
                else { prove(operations.get(0).source(), first.sourceState(), true, 7); }
            }
            case 7 -> prove(operations.get(1).source(), second.sourceState(), true, 8);
            case 8 -> {
                if (!recovery && cut.equals("C1")) { pause(); }
                second.applyBank(operations.get(1));
                coordinator.freeze(operations.get(1).destination().key(), second.bankState()); step = 9;
            }
            case 9 -> prove(operations.get(1).destination(), second.bankState(), true, 10);
            case 10 -> {
                boolean holeRefused = false, reverseRefused = false;
                var op = operations.get(1);
                try { coordinator.retire(Set.of(op.id()), proofs, result -> { }); }
                catch (IllegalArgumentException expected) { holeRefused = true; }
                try {
                    new EscrowOperation(UUID.randomUUID(), coordinator.nextSequence(), op.owner(), level.getGameTime(),
                            EscrowOperation.Phase.PREPARED, EscrowReason.NONE, op.payload(), op.payloadHash(),
                            op.destination(), op.source(), op.guard());
                } catch (IllegalArgumentException expected) { reverseRefused = true; }
                EscrowTags.require(holeRefused && reverseRefused, "Chain hole or opposite-direction request was accepted");
                if (!recovery && cut.equals("C2")) { pause(); }
                Set<UUID> retired = cut.equals("C3") ? Set.of(operations.get(0).id()) : Set.of(operations.get(0).id(), op.id());
                waiting = coordinator.retire(retired, proofs, result -> {
                    EscrowTags.require(result, "Chain retirement failed");
                    if (cut.equals("C3")) {
                        EscrowTestEndpoints.clearRetired(first, retired); EscrowTestEndpoints.clearRetired(second, retired);
                        EscrowTags.require(first.sourceState().markers().isEmpty() && second.sourceState().markers().size() == 1
                                && first.bankState().markers().size() == 1 && first.bankState().epoch() == 2
                                && coordinator.locked(op.destination().key()) && !coordinator.locked(operations.get(0).source().key()), "Partial retirement lost a later marker or lock");
                    }
                    waiting = false; step = cut.equals("C3") ? 12 : 11;
                });
            }
            case 12 -> {
                var last = operations.get(operations.size() - 1);
                coordinator.freeze(last.source().key(), second.sourceState());
                coordinator.freeze(last.destination().key(), first.bankState()); proveSources(13);
            }
            case 13 -> prove(operations.get(operations.size() - 1).destination(), first.bankState(), true, 14);
            case 14 -> { if (!recovery) { pause(); } step = 15; }
            case 15 -> {
                var op = operations.get(operations.size() - 1);
                waiting = coordinator.retire(Set.of(op.id()), proofs, result -> {
                    EscrowTags.require(result, "Partial chain successor retirement failed");
                    EscrowTestEndpoints.clearRetired(second, Set.of(op.id())); waiting = false; step = 11;
                });
            }
            case 11 -> {
                EscrowTestEndpoints.clearRetired(first); EscrowTestEndpoints.clearRetired(second);
                int a = EscrowTestEndpoints.amount(first.source().getPersistentData());
                int b = EscrowTestEndpoints.amount(second.source().getPersistentData());
                int bank = EscrowTestEndpoints.amount(first.bank().getPersistentData());
                EscrowTags.require(a == 4_000 && b == 4_000 && bank == 2_000 && data.ledger().records().isEmpty()
                        && first.sourceState().epoch() == 1 && second.sourceState().epoch() == 1 && first.bankState().epoch() == 2,
                        "Chain conservation, epochs or retirement failed");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_CHAIN_RESULT source_a={} source_b={} bank={} records={} epochs=1,1,2", a, b, bank, data.ledger().records().size());
                done = true;
                proofs.values().forEach(EscrowBarrier.Proof::release);
            }
            default -> throw new IllegalStateException("Unknown chain stage");
        }
    }
    private void prepare(EscrowTestEndpoints.Fixture fixture, int next) {
        if (operations.size() < (next == 2 ? 1 : 2)) {
            var op = fixture.operation(coordinator.nextSequence()); operations.add(op);
            fixture.prepare(coordinator, op, result -> { EscrowTags.require(result, "Chain L1 failed"); waiting = false; step = next; });
        }
        waiting = coordinator.commitPreparations();
    }
    private void prove(EscrowEndpoint endpoint, EscrowPrefix.State frontier, boolean retain, int next) {
        var runtime = EscrowRuntime.of(server);
        waiting = runtime.barrier().begin(runtime.store(level), endpoint, frontier, EscrowTestEndpoints.validator(endpoint, frontier),
                server.getTickCount(), result -> {
                    EscrowTags.require(result.confirmed(), "Chain barrier failed: " + result.reason());
                    if (retain) { proofs.put(endpoint.key(), result.proof()); } else { result.proof().release(); }
                    waiting = false; step = next;
                });
    }
    private void proveSources(int next) {
        var a = first.operation(1).source(); var b = second.operation(2).source(); var runtime = EscrowRuntime.of(server);
        waiting = runtime.barrier().beginJoint(runtime.store(level), List.of(
                new EscrowBarrier.Target(a, first.sourceState(), EscrowTestEndpoints.validator(a, first.sourceState())),
                new EscrowBarrier.Target(b, second.sourceState(), EscrowTestEndpoints.validator(b, second.sourceState()))), server.getTickCount(), result -> {
                    EscrowTags.require(result.confirmed(), "Joint source barrier failed: " + result.reason());
                    result.proofs().forEach((key, proof) -> { var old = proofs.put(key, proof); if (old != null) { old.release(); } });
                    waiting = false; step = next;
                });
    }
    private void restore() {
        var banks = level.getEntitiesOfClass(ArmorStand.class, new AABB(A).inflate(8), entity -> true);
        EscrowTags.require(level.getBlockEntity(A) != null && level.getBlockEntity(secondPosition) != null && banks.size() == 1, "Missing chain fixtures");
        var bank = banks.get(0); UUID logical = bank.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity");
        first = new EscrowTestEndpoints.Fixture(level, level.getBlockEntity(A), bank, data.ledger().id(), logical);
        second = new EscrowTestEndpoints.Fixture(level, level.getBlockEntity(secondPosition), bank, data.ledger().id(), logical);
        operations.addAll(data.ledger().records());
        if (operations.isEmpty()) { step = 11; return; }
        if (cut.equals("C3") && operations.size() == 1) {
            Set<UUID> retained = operations.stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet());
            Set<UUID> retired = java.util.stream.Stream.concat(first.sourceState().markers().stream(), first.bankState().markers().stream())
                    .map(EscrowPrefix.Marker::operation).filter(id -> !retained.contains(id)).collect(java.util.stream.Collectors.toSet());
            EscrowTestEndpoints.clearRetired(first, retired); EscrowTestEndpoints.clearRetired(second, retired);
            second.apply(operations.get(0)); step = 12; return;
        }
        EscrowTags.require(operations.size() == 2, "Unexpected chain ledger");
        first.apply(operations.get(0)); second.apply(operations.get(1));
        coordinator.freeze(operations.get(0).source().key(), first.sourceState());
        coordinator.freeze(operations.get(1).source().key(), second.sourceState()); step = 6;
    }
    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_PAUSED cut={} thread={}", cut, Thread.currentThread().getName());
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        throw new IllegalStateException("Chain checkpoint kill did not arrive within 30 seconds");
    }
}
