package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Empty gated native server: real rocket/resource stores, hard cuts and repeatable Level-lifetime veto. */
final class EscrowSettlementCompletionProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String cut;
    private final String boundary;
    private final boolean recovery, chain, abandon, floor, veto, normalization;
    private EscrowCoordinator coordinator;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private EscrowTestEndpoints.Fixture source;
    private final List<EscrowTestEndpoints.Fixture> sources = new ArrayList<>();
    private EscrowSettlementIntent preview;
    private UUID selected;
    private long startTick;
    private boolean waiting, done, vetoInstalled;
    private int step;
    private volatile boolean writingIntent;
    private volatile String ledgerPhase = "";
    EscrowSettlementCompletionProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        cut = System.getProperty("advancedrocketrycommunity.escrowCase", "");
        String[] parts = cut.substring(3).split("__", 2);
        EscrowTags.require(parts.length == 2 && List.of("ACCEPT", "ABANDON", "CHAIN_ACCEPT", "CHAIN_ABANDON", "FLOOR", "NORMALIZE").contains(parts[0]),
                "Unknown settlement completion case");
        boundary = parts[1]; chain = parts[0].startsWith("CHAIN"); abandon = parts[0].endsWith("ABANDON");
        floor = parts[0].equals("FLOOR"); veto = boundary.equals("VETO");
        normalization = parts[0].equals("NORMALIZE");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
    }
    void checkpoint(String name) {
        if (done) { return; }
        if (name.equals("O3_CAPTURE") || name.equals("O4_CAPTURE")) { ledgerPhase = name.substring(0, 2); return; }
        if (veto && name.equals("O_VETO") && vetoInstalled) {
            EscrowTags.require(!data.ledger().records().isEmpty() && data.ledger().tombstones().isEmpty(), "Veto permitted retirement");
            if (recovery) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SETTLEMENT_COMPLETION_RESULT cut={} state=HELD records={} locked=true", cut, data.ledger().records().size());
                done = true;
            } else { pause("O_VETO"); }
        }
        if (recovery || veto || writingIntent) { return; }
        if (name.equals(boundary)) { pause(name); }
        if ((name.equals("K1") || name.equals("K8")) && boundary.equals(ledgerPhase + "_BEFORE")) {
            pause(boundary);
        }
    }
    private void pause(String name) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SETTLEMENT_COMPLETION_PAUSED cut={} boundary={}", cut, name);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
        throw new IllegalStateException("Settlement completion forced stop did not arrive within 30 seconds");
    }
    void tick() {
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong()) || !level.isPositionEntityTicking(POSITION)) { return; }
        if (data == null) { data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server); startTick = server.getTickCount(); }
        if (recovery) {
            if (veto && !vetoInstalled && !data.ledger().records().isEmpty()) { installVeto(); }
            if (!veto && data.ledger().records().isEmpty() && !data.ledger().tombstones().isEmpty()) { completed(); }
            return;
        }
        switch (step) {
            case 0 -> {
                var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
                if (floor) { if (!createFloor(journal)) { return; } step = 3; return; }
                source = EscrowTestEndpoints.create(level, POSITION, data.ledger().id());
                sources.add(source);
                rocket = EscrowRocketAuditProbe.createRocket(level, source.logical(), true); source.bank().discard();
                var operations = new ArrayList<EscrowOperation>(); operations.add(operation(source, coordinator.nextSequence(), 0));
                if (chain) {
                    var second = EscrowTestEndpoints.chainSource(source, POSITION.offset(1, 0, 0)); sources.add(second);
                    operations.add(operation(second, coordinator.nextSequence() + 1, 1));
                }
                var parent = rocket.saveWithoutId(new CompoundTag()); var wrapper = parent.getCompound("RocketEntityData");
                var fuel = rocket.flightData().orElseThrow().fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, operations.size() * 1_000L);
                wrapper.put("flight_data", io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec.encode(
                        rocket.flightData().orElseThrow().withFuel(fuel, level.getGameTime())));
                wrapper.put("resource_escrow", new RocketEscrowSection(data.ledger().id(), operations.size(), operations.stream()
                        .map(op -> new EscrowPrefix.Marker(op.id(), op.sequence(), "DESTINATION", op.destination().afterEpoch())).toList()).encode());
                if (!normalization) { rocket.load(parent); }
                EscrowTags.require(rocket.operational(), "Marked credited fixture is invalid");
                selected = operations.get(0).id();
                for (var op : operations) { coordinator.prepareTest(op, committed -> EscrowTags.require(committed, "Completion fixture L1 failed")); }
                EscrowTags.require(level.addFreshEntity(rocket), "Completion fixture rocket failed to publish");
                if (veto) {
                    EscrowTags.require(server.saveEverything(false, true, true), "Veto baseline save refused");
                    level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
                }
                step = 1;
            }
            case 1 -> { if (coordinator.commitPreparations()) { step = 2; } }
            case 2 -> {
                if (!coordinator.writerIdle() || data.ledger().records().size() != (chain ? 2 : 1)) { return; }
                // Refuse vanilla's intervening saves as well as the later O2 own-store attempt.
                if (veto && !vetoInstalled) { installVeto(); }
                for (int i = 0; i < sources.size(); i++) { sources.get(i).applySource(data.ledger().records().get(i)); }
                coordinator.transition(selected, EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
            }
            case 3 -> {
                if (!coordinator.writerIdle() || floor && !RocketTransferSavedData.get(server).retryChecked(server)
                        || !floor && data.ledger().records().get(0).reason() != EscrowReason.OTHER_STATE) { return; }
                if (abandon) { level.setBlockAndUpdate(POSITION, Blocks.AIR.defaultBlockState()); }
                if (!veto || floor) { EscrowTags.require(server.saveEverything(false, true, true), "Completion fixture baseline save refused"); }
                level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
                preview = EscrowRuntime.of(server).settlement().preview(server, selected, choice(), ACTOR, "Server"); step = 4;
            }
            case 4 -> {
                if ((abandon || floor) && server.getTickCount() - preview.previewTick() < 200) { return; }
                writingIntent = true;
                waiting = EscrowRuntime.of(server).settlement().confirm(server, selected, choice(), ACTOR, preview.digest(), committed -> {
                    EscrowTags.require(committed, "Completion O1 was not acknowledged"); writingIntent = false; waiting = false; step = 5;
                    if (veto) { installVeto(); }
                });
            }
            case 5 -> {
                if (veto && !vetoInstalled) { installVeto(); }
            }
            default -> throw new IllegalStateException("Unknown completion fixture stage");
        }
        EscrowTags.require(server.getTickCount() - startTick < 4_000, "Completion fixture exceeded bounded 4,000 ticks");
    }
    private EscrowSettlementIntent.Choice choice() {
        return floor ? EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE
                : abandon ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT;
    }
    private EscrowOperation operation(EscrowTestEndpoints.Fixture fixture, long sequence, long epoch) {
        var sample = fixture.operation(sequence); var fuel = rocket.flightData().orElseThrow().fuel();
        var before = epoch == 0 ? fuel : fuel.withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
        var after = fuel.withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, (epoch + 1) * 1_000);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), source.logical(),
                ModIdentity.id("rocket").toString(), 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, epoch, before.resourceSignature(), after.resourceSignature());
        var guard = RocketTransferSavedData.get(server).acknowledgedGuard(source.logical()).orElseThrow();
        var op = new EscrowOperation(sample.id(), sequence, rocket.ownerId().orElseThrow(), sample.createdTick(), sample.phase(), sample.reason(), sample.payload(), sample.payloadHash(),
                sample.source(), destination, new EscrowGuard(source.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        return EscrowNativeBinding.captureTest(op, rocket, fixture.source(), before, after, data.ledger());
    }
    private boolean createFloor(RocketTransferSavedData journal) {
        BlockPos target = POSITION.offset(16, 0, 0);
        if (!level.areEntitiesLoaded(new ChunkPos(target).toLong()) || !level.isPositionEntityTicking(target)) { return false; }
        // Keep concrete absence slots available for inspection after vanilla's ordinary empty-slot suppression.
        for (var position : List.of(POSITION, target)) {
            var anchor = new net.minecraft.world.entity.decoration.ArmorStand(level, position.getX() + 1.5, 100, position.getZ() + 1.5);
            anchor.setNoGravity(true); EscrowTags.require(level.addFreshEntity(anchor), "Floor absence anchor was not added");
        }
        UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), identity = UUID.randomUUID(); selected = transfer;
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), java.util.Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), java.util.Map.of())));
        var from = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), new RocketPosition(64, 100, 64), blocks,
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 1_000, 1, 1, 1, 0), 0, propulsion);
        var to = from.relocated(UUID.randomUUID(), level.dimension().location(), new RocketPosition(80, 100, 64), 0);
        var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                level.dimension().location(), level.dimension().location(), 500, 0);
        var flight = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(), from.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), to.sourceOrigin(), 160);
        var record = RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), from, to, flight, arrival, 500, 0)
                .destinationSpawned(identity).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        // Publish the test floor in the same owner-thread call, before flushing journal authority.
        journal.put(record);
        var floorTag = new CompoundTag(); floorTag.putInt("schema_version", 2); floorTag.putUUID("rocket_id", logical); floorTag.putUUID("transfer_id", transfer);
        floorTag.putLong("journal_generation", 1); floorTag.putString("journal_digest", io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest.of(
                journal.save(new CompoundTag()).getList("transfers", 10).getCompound(0), RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES, 35));
        floorTag.putUUID("entity_id", identity); floorTag.putLong("resource_epoch", 1); floorTag.putString("signature", fuel.resourceSignature()); floorTag.putLong("highest_seq", 1);
        var reference = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, to.sourceDimension().toString(), identity, logical, ModIdentity.id("rocket").toString(),
                to.sourceOrigin().x() >> 4, 0, to.sourceOrigin().z() >> 4, EscrowEndpoint.Unit.PRIMARY, 1, fuel.resourceSignature(), fuel.resourceSignature());
        floorTag.putString("signature_kind", EscrowSignatureKind.FUEL_VECTOR_V1.name()); floorTag.putString("invariant_digest", EscrowInvariant.rocket(reference, record.ownerId(), to));
        data.acknowledged(new EscrowLedger(data.ledger().id(), 1, data.ledger().writerGeneration(), List.of(), List.of(floorTag), List.of()), true);
        journal.flushChecked(server); coordinator.autosave(); return true;
    }
    private void installVeto() {
        var op = data.ledger().records().get(0);
        var endpoint = floor ? op.destination() : op.source();
        ChunkPos pos = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? new ChunkPos(endpoint.x(), endpoint.z())
                : new ChunkPos(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
        // Floor absence uses the entity store, so refuse its capture with an actual saveable entity.
        if (floor) {
            var poison = new net.minecraft.world.entity.decoration.ArmorStand(level, pos.getMinBlockX() + 1.5, 100, pos.getMinBlockZ() + 1.5) {
                @Override public boolean save(CompoundTag tag) { return false; }
            };
            poison.setNoGravity(true); EscrowTags.require(level.addFreshEntity(poison), "Veto entity was not added");
        } else { GuardedChunkSaves.recordObservedDenial(level, pos, "Release-test O-veto source chunk"); }
        vetoInstalled = true;
    }
    private void completed() {
        var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
        EscrowTags.require(data.ledger().floors().isEmpty() && data.ledger().tombstones().size() == (chain ? 2 : 1)
                && data.ledger().tombstones().stream().allMatch(tag -> tag.getBoolean("released")), "Incomplete acknowledged release");
        for (var tag : data.ledger().tombstones()) {
            for (var raw : EscrowTags.list(tag, "survivors", 2)) {
                var endpoint = EscrowCodec.endpoint(((CompoundTag) raw).getCompound("endpoint"));
                if (endpoint.kind() != EscrowEndpoint.Kind.BLOCK_ENTITY) { continue; }
                var chunk = level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4);
                if (chunk == null) { return; }
                var source = chunk.getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
                if (source == null || !EscrowTestEndpoints.state(source.getPersistentData(), data.ledger().id(), false).markers().isEmpty()) { return; }
            }
        }
        for (var tag : data.ledger().tombstones()) {
            for (var raw : EscrowTags.list(tag, "guards", 1)) {
                var guard = EscrowCodec.guard((CompoundTag) raw); EscrowTags.require(guard.transfer() == null || journal.find(guard.transfer()).isEmpty(), "Released matching journal remains");
            }
            for (var raw : EscrowTags.list(tag, "survivors", 2)) {
                var endpoint = EscrowCodec.endpoint(((CompoundTag) raw).getCompound("endpoint"));
                if (endpoint.kind() != EscrowEndpoint.Kind.ENTITY) { continue; }
                if (!(level.getEntity(endpoint.identity()) instanceof RocketEntity survivor)) { return; }
                survivor.pruneSettledEscrow();
                if (normalization && survivor.escrowSection().epoch() == 0) {
                    EscrowTags.require(survivor.flightData().orElseThrow().fuel().totalAmount() == 0
                            && data.ledger().id().equals(survivor.escrowSection().ledger()) && RocketEscrowGate.idle(survivor), "Neutral normalization did not release intact");
                    for (int epoch = 1; epoch <= 2; epoch++) {
                        var flight = survivor.flightData().orElseThrow();
                        survivor.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                                net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, epoch * 1_000L), level.getGameTime()));
                        EscrowTags.require(survivor.escrowSection().epoch() == epoch, "Normalized native mutation did not advance its epoch");
                    }
                    EscrowTags.require(server.saveEverything(false, true, true), "Normalized successor save refused");
                    level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
                }
                EscrowTags.require(survivor.flightData().orElseThrow().fuel().totalAmount() == (chain ? 2_000 : 1_000)
                        + (normalization ? 1_000 : 0)
                        && survivor.escrowSection().epoch() == (chain || normalization ? 2 : 1) && survivor.escrowSection().markers().isEmpty()
                        && RocketEscrowGate.idle(survivor), "Chosen survivor did not unlock intact");
                var unproved = EscrowRocketAuditProbe.createRocket(level, endpoint.logicalRocket(), true);
                EscrowTags.require(!RocketEscrowGate.idle(unproved), "Unproved returning physical identity unlocked");
                unproved.setUUID(survivor.getUUID());
                EscrowTags.require(!RocketEscrowGate.idle(unproved), "Stale same-UUID identity unlocked");
                EscrowTags.require(EscrowRecoveryFence.known(data.ledger(), endpoint.logicalRocket(), UUID.randomUUID())
                        && "TOMBSTONE".equals(EscrowRecoveryFence.reason(data.ledger(), endpoint.logicalRocket(), UUID.randomUUID(),
                                RocketTransferRecoveryAction.REBUILD_DESTINATION, List.of(), List.of())), "Empty returned journal election was allowed");
                var currentCopy = EscrowRocketAuditProbe.createRocket(level, endpoint.logicalRocket(), true);
                currentCopy.load(survivor.saveWithoutId(new CompoundTag()));
                EscrowTags.require(currentCopy.operational() && RocketEscrowGate.idle(currentCopy), "Exact chosen state copy is invalid");
                var previous = currentCopy.flightData().orElseThrow();
                var changed = previous.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, (chain || normalization ? 2_000 : 1_000) - 100);
                currentCopy.updateFlightData(previous.withFuel(changed, level.getGameTime()));
                EscrowTags.require(currentCopy.escrowSection().epoch() == survivor.escrowSection().epoch() + 1 && RocketEscrowGate.idle(currentCopy),
                        "Approved native resource mutation did not advance the chosen epoch");
            }
            for (var raw : EscrowTags.list(tag, "missing", 2)) {
                var endpoint = EscrowCodec.endpoint((CompoundTag) raw);
                EscrowTags.require(coordinator.locked(endpoint.key()), "Returning missing identity was released");
            }
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SETTLEMENT_COMPLETION_RESULT cut={} state=RELEASED records=0 tombstones={} locked=false", cut, data.ledger().tombstones().size());
        done = true;
    }
}
