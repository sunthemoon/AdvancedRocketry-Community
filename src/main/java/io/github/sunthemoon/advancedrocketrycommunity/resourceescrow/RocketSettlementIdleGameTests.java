package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** N1/F3: settlement and ordinary checked L2 each permit two complete passenger flights. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSettlementIdleGameTests {
    private enum Release { SETTLEMENT, L2, NORMALIZED }
    private static final TicketType<UUID> FLIGHT_FIXTURE = TicketType.create("arce_n1_fixture", UUID::compareTo);
    /**
     * W137's reproduction, off unless a release-test run names one {@link Release} here: that scenario's first launch
     * loses its source at F1's acknowledged ARMED checkpoint, so production discards it after the ARMED write.
     */
    static final String DISCARD_PROPERTY = "advancedrocketrycommunity.settlementIdleDiscard";
    private RocketSettlementIdleGameTests() { }

    @GameTest(template = "empty", batch = "rocket_settlement_idle", timeoutTicks = 1_200)
    public static void acknowledgedSettlementRestoresMountRefuelAndLaunch(GameTestHelper helper) {
        new Scenario(helper, Release.SETTLEMENT).start();
    }

    @GameTest(template = "empty", batch = "rocket_l2_complete_flights", timeoutTicks = 1_200)
    public static void ordinaryL2WithoutReceiptCompletesTwoPassengerFlights(GameTestHelper helper) {
        new Scenario(helper, Release.L2).start();
    }

    @GameTest(template = "empty", batch = "rocket_normalized_complete_flights", timeoutTicks = 1_200)
    public static void normalizedSettlementSurvivorCompletesTwoPassengerFlights(GameTestHelper helper) {
        new Scenario(helper, Release.NORMALIZED).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Release release;
        private final BlockPos position;
        private final ChunkPos chunk;
        private final GameTestTickPacer pacer;
        private final UUID actor = UUID.randomUUID();
        private UUID launch;
        private final java.util.ArrayList<UUID> completed = new java.util.ArrayList<>();
        private ResourceEscrowSavedData data;
        private EscrowCoordinator coordinator;
        private EscrowTestEndpoints.Fixture source;
        private RocketEntity rocket;
        private EscrowOperation operation;
        private int step;
        private boolean intentQueued;
        private ServerPlayer passenger;
        private RocketEntity predecessor, successor;
        private RocketFlightData departure;
        private RocketEscrowSection departureWrapper;
        private RocketTransferRecord terminalRecord;
        private EscrowTerminalLeaseCut leaseCut;
        private final java.util.EnumSet<RocketFlightState> sourceStates = java.util.EnumSet.noneOf(RocketFlightState.class);
        private final java.util.EnumSet<RocketTransferPhase> phases = java.util.EnumSet.noneOf(RocketTransferPhase.class);
        private boolean sawDescent, sawF6, sawF7;
        private List<CompoundTag> receipts;
        private final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> l2Proofs = new java.util.LinkedHashMap<>();
        private boolean proving, retiring, discardHook;
        private final Map<ServerLevel, java.util.Set<ChunkPos>> flightChunks = new java.util.LinkedHashMap<>();

        Scenario(GameTestHelper helper, Release release) {
            this.helper = helper; this.release = release; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            position = new BlockPos(550_001 + 256 * release.ordinal(), 100, 550_001); chunk = new ChunkPos(position);
        }
        void start() {
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
            var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is unavailable");
            // Register exactly once; no scheduling from a scheduled callback.
            helper.onEachTick(this::tick);
        }
        private void tick() {
            pacer.pace();
            if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) { return; }
            switch (step) {
                case 0 -> prepare();
                case 1 -> { if (coordinator.commitPreparations()) { step = 2; } }
                case 2 -> {
                    if (!coordinator.writerIdle() || data.ledger().records().stream().noneMatch(op -> op.id().equals(operation.id()))) { return; }
                    helper.assertTrue(!RocketEscrowGate.idle(rocket), "Pending current-ledger rocket is idle");
                    source.applySource(operation);
                    if (release == Release.L2) { step = 20; return; }
                    coordinator.transition(operation.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
                }
                case 3 -> {
                    if (!coordinator.writerIdle() || data.ledger().records().stream().noneMatch(op -> op.id().equals(operation.id())
                            && op.reason() == EscrowReason.OTHER_STATE)) { return; }
                    if (intentQueued) { return; }
                    var operator = EscrowRuntime.of(level.getServer()).settlement();
                    var preview = operator.preview(level.getServer(), operation.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "N1GameTest");
                    intentQueued = operator.confirm(level.getServer(), operation.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                            actor, preview.digest(), committed -> {
                                helper.assertTrue(committed, "O1 was not acknowledged"); step = 4;
                            });
                }
                case 4 -> {
                    if (data.ledger().records().stream().anyMatch(op -> op.id().equals(operation.id()))) { return; }
                    var receipt = data.ledger().tombstones().stream().filter(tag -> operation.id().equals(tag.getUUID("op_id"))).findFirst().orElseThrow();
                    helper.assertTrue(receipt.getBoolean("released"), "Record disappeared before acknowledged O4");
                    if (!rocket.escrowSection().markers().isEmpty()) { return; }
                    helper.assertTrue(data.ledger().id().equals(rocket.escrowSection().ledger())
                            && rocket.escrowSection().epoch() == (release == Release.NORMALIZED ? 0 : 1),
                            "O4 reset the permanent ledger binding or epoch");
                    helper.assertTrue(RocketEscrowGate.idle(rocket), "Settled current-ledger rocket remains locked");
                    released();
                }
                case 5 -> {
                    var target = completed.isEmpty() ? level.getServer().getLevel(CelestialIds.MOON_LEVEL) : level;
                    if (!readyLaunch(target)) { return; }
                    predecessor = rocket; successor = null;
                    departure = rocket.flightData().orElseThrow(); departureWrapper = rocket.escrowSection();
                    sourceStates.clear(); phases.clear(); sawDescent = false; sawF6 = false; sawF7 = false;
                    if (completed.isEmpty()) { discardAfterArmed(); }
                    var result = RocketRuntime.requestAdminFlight(rocket, completed.isEmpty() ? RocketDestination.MOON : RocketDestination.EARTH, launch);
                    RocketJournalGameTestSupport.assertPendingLaunch(helper, result, rocket); step = 6;
                }
                case 6 -> flight();
                case 7 -> terminal();
                case 20 -> ordinaryL2();
                default -> { }
            }
        }

        private boolean readyLaunch(ServerLevel target) {
            var journal = RocketTransferSavedData.get(level.getServer());
            if (!journal.retryChecked(level.getServer())) { return false; }
            helper.assertTrue(coordinator.governedAvailable(source.logical()) && RocketEscrowGate.idle(rocket)
                    && RocketRuntime.resourceMutationAllowed(rocket), "Released source lost its launch authority");
            if (launch == null) { launch = UUID.randomUUID(); }
            var selected = new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector().select(
                    target, rocket.snapshot().orElseThrow(), launch, journal.entries().stream()
                            .map(RocketTransferRecord::destinationSnapshot)
                            .map(io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion::fromSnapshot).toList(),
                    target.getGameTime());
            helper.assertTrue(selected.success(), "N1 fixture has no available server-owned landing pad");
            // The shared test world may select a noncentral pad. Load its exact election margin,
            // not just the fixed pad's center; production F1 still performs its unchanged checks.
            boolean sourceReady = readyRegion((ServerLevel) rocket.level(), rocket.snapshot().orElseThrow());
            boolean destinationReady = readyRegion(target, selected.snapshot().orElseThrow());
            return sourceReady && destinationReady;
        }

        private boolean readyRegion(ServerLevel end, RocketStructureSnapshot snapshot) {
            var region = io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(snapshot);
            int minX = (region.minimum().x() - 1) >> 4, maxX = (region.maximum().x() + 2) >> 4;
            int minZ = (region.minimum().z() - 1) >> 4, maxZ = (region.maximum().z() + 2) >> 4;
            helper.assertTrue((long) (maxX - minX + 1) * (maxZ - minZ + 1)
                    <= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_LANDING_CHUNKS,
                    "N1 fixture election region exceeds the flight bound");
            var retained = flightChunks.computeIfAbsent(end, ignored -> new java.util.LinkedHashSet<>());
            boolean ready = true;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var slot = new ChunkPos(x, z);
                    if (retained.add(slot)) { end.getChunkSource().addRegionTicket(FLIGHT_FIXTURE, slot, 2, actor); }
                    end.getChunk(x, z);
                    ready &= end.areEntitiesLoaded(slot.toLong())
                            && end.isPositionEntityTicking(new BlockPos(x << 4, snapshot.sourceOrigin().y(), z << 4));
                }
            }
            return ready;
        }

        /** W137 reproduction ({@link #DISCARD_PROPERTY}): the boundary after ARMED then fails on the removed source. */
        private void discardAfterArmed() {
            if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS) || !release.name().equals(System.getProperty(DISCARD_PROPERTY))) { return; }
            var server = level.getServer(); var armed = rocket;
            discardHook = true;
            EscrowRuntime.of(server).terminalCutForTests(server, cut -> {
                if (!"F1_ARM_ACK".equals(cut) || !discardHook) { return; }
                discardHook = false; EscrowRuntime.of(server).terminalCutForTests(server, null);
                armed.discard();
            });
        }

        private void released() {
            receipts = data.ledger().tombstones();
            helper.assertTrue(release != Release.L2 || !EscrowSettlementTombstones.history(data.ledger(), source.logical()),
                    "Ordinary L2 fabricated settlement history");
            exerciseMountAndRefuel();
            var server = level.getServer();
            passenger = new ServerPlayer(server, level, new GameProfile(rocket.ownerId().orElseThrow(), "N1Flight")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection); server.getPlayerList().placeNewPlayer(connection, passenger);
            passenger.teleportTo(level, rocket.getX(), rocket.getY(), rocket.getZ(), 0, 0);
            RocketRuntime.requestFlightIntent(passenger, rocket.getId(), RocketFlightAction.BOARD,
                    new TravelTarget.BodySurface(CelestialIds.EARTH_ID), UUID.randomUUID());
            helper.assertTrue(passenger.getVehicle() == rocket
                    && rocket.flightData().orElseThrow().passengers().assignment(passenger.getUUID()).isPresent(),
                    "Released source did not board its connected passenger");
            step = 5;
        }

        /** Test the existing L2 core; C32 owns the native C0-C8 adapter and its automatic pruning. */
        private void ordinaryL2() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            if (proving || retiring || !journal.actionAuthorityAvailable()) { return; }
            if (l2Proofs.values().stream().anyMatch(proof -> !proof.valid())) {
                l2Proofs.values().forEach(EscrowBarrier.Proof::release); l2Proofs.clear();
            }
            var observations = EscrowSettlementObserver.component(server, data.ledger(), operation.id());
            for (var observation : observations) {
                var endpoint = observation.endpoint();
                if (l2Proofs.containsKey(endpoint.key())) { continue; }
                coordinator.freeze(endpoint.key(), observation.state()); proving = true;
                var runtime = EscrowRuntime.of(server);
                boolean started = runtime.barrier().begin(runtime.store(level), endpoint, observation.state(),
                        EscrowSettlementDisk.validator(observation, data.ledger().id()), server.getTickCount(), result -> {
                            proving = false;
                            if (result.confirmed()) { l2Proofs.put(endpoint.key(), result.proof()); }
                        });
                if (!started) { proving = false; }
                return;
            }
            helper.assertTrue(!RocketEscrowGate.idle(rocket) && journal.acknowledgedGuard(source.logical()).orElseThrow().transfer() == null,
                    "L2 fixture lost its checked journal or released before retirement");
            retiring = coordinator.retire(java.util.Set.of(operation.id()), l2Proofs, acknowledged -> {
                retiring = false;
                if (!acknowledged) { l2Proofs.clear(); return; }
                helper.assertTrue(data.ledger().records().stream().noneMatch(op -> op.id().equals(operation.id()))
                        && data.ledger().issuedThrough() >= operation.sequence() && journal.actionAuthorityAvailable(),
                        "L2 callback is not its exact checked retirement");
                var envelope = EscrowNativeEnvelope.rocket(rocket);
                var encoded = rocket.saveWithoutId(new CompoundTag());
                var section = rocket.escrowSection();
                var pruned = new RocketEscrowSection(section.ledger(), section.epoch(), section.markers().stream()
                        .filter(marker -> !marker.operation().equals(operation.id())).toList());
                encoded.getCompound("RocketEntityData").put("resource_escrow", pruned.encode());
                // Explicit fixture setup after real L2, not a production C8 API or an unproved release.
                rocket.discard(); rocket = ModEntities.ROCKET.get().create(level);
                helper.assertTrue(rocket != null, "Post-L2 fixture could not reload its survivor");
                rocket.load(encoded); helper.assertTrue(level.addFreshEntity(rocket), "Post-L2 survivor did not rejoin");
                envelope.put("wrapper", pruned.encode());
                helper.assertTrue(envelope.equals(EscrowNativeEnvelope.rocket(rocket)) && pruned.epoch() == 1
                        && pruned.markers().isEmpty() && RocketEscrowGate.idle(rocket), "Post-L2 setup changed more than its retired marker");
                EscrowTestEndpoints.clearRetired(source, java.util.Set.of(operation.id()));
                released();
            });
        }

        private void flight() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            var sourceFlight = predecessor.flightData().orElseThrow();
            sourceStates.add(sourceFlight.state());
            helper.assertTrue(sourceFlight.fuel().equals(departure.fuel()) && predecessor.escrowSection().equals(departureWrapper),
                    "Flight rewrote its credited source fuel or retained epoch");
            var record = journal.find(launch).orElse(null);
            if (record == null) { return; }
            // A phase's exact acknowledgement can immediately queue the next phase in the same
            // owner tick. Observe every staged phase; COMMITTED below requires checked authority.
            phases.add(record.phase());
            if (sourceFlight.state() == RocketFlightState.COUNTDOWN) {
                helper.assertTrue(journal.acknowledgedCurrent() && departureWrapper.epoch() == (completed.isEmpty() ? 2 : 1),
                        "Launch preceded its acknowledgement or reset the post-refuel envelope");
            }
            var arrivalLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    record.destinationSnapshot().sourceDimension()));
            var arrival = arrivalLevel.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
            if (!(arrival instanceof RocketEntity found)) { return; }
            successor = found;
            var flight = successor.flightData().orElseThrow();
            var expectedDebits = new java.util.ArrayList<>(completed); expectedDebits.add(launch);
            helper.assertTrue(!successor.getUUID().equals(predecessor.getUUID()) && successor.escrowSection().epoch() == 0
                    && successor.escrowSection().markers().isEmpty() && data.ledger().id().equals(successor.escrowSection().ledger())
                    && flight.fuel().equals(record.destinationFlightData().fuel())
                    && flight.fuel().committedDebits().equals(expectedDebits)
                    && departure.fuel().debit(record.sourceFlightData().plan().orElseThrow()).state().equals(flight.fuel()),
                    "Destination lost its fixed identity, independent epoch or exact one-time debit");
            sawDescent |= flight.state() == RocketFlightState.DESCENT;
            if (record.phase() != RocketTransferPhase.COMMITTED || !journal.actionAuthorityAvailable()) { return; }
            helper.assertTrue(predecessor.isRemoved() && predecessor.getPassengers().isEmpty()
                    && ((ServerLevel) predecessor.level()).getEntity(predecessor.getUUID()) == null && passenger.getVehicle() == successor
                    && successor.getPassengers().equals(List.of(passenger)) && passenger.connection.connection.isConnected(),
                    "COMMITTED did not follow source removal and connected passenger handoff");
            if (flight.state() != RocketFlightState.LANDED) { return; }
            helper.assertTrue(sawDescent && sourceStates.containsAll(java.util.EnumSet.of(RocketFlightState.COUNTDOWN,
                    RocketFlightState.ASCENT, RocketFlightState.TRANSIT)) && phases.containsAll(java.util.EnumSet.allOf(RocketTransferPhase.class))
                    && flight.plan().isEmpty() && flight.activeTransferId().isEmpty() && !RocketEscrowGate.resourcesIdle(successor),
                    "Flight skipped a checked phase or released its landed successor before F7");
            terminalRecord = record; leaseCut = new EscrowTerminalLeaseCut(server, data, record); step = 7;
        }

        private void terminal() {
            var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            if (EscrowRuntime.of(server).terminal().checkpoint(server, terminalRecord)) { sawF6 = true; }
            if (leaseCut != null && leaseCut.ready()) {
                try { leaseCut.exercise(helper, EscrowTerminalLeaseCut.Fault.NONE, successor, passenger); }
                finally { leaseCut.close(); leaseCut = null; }
                sawF7 = true;
                var arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(launch)).findFirst().orElseThrow();
                var head = data.ledger().rocketAuthorities().stream().filter(value -> value.logical().equals(source.logical())).findFirst().orElse(null);
                helper.assertTrue(sawF6 && arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF
                        && arm.epoch() == departureWrapper.epoch() && receipts.equals(data.ledger().tombstones()),
                        "Handoff lost its proof, source epoch or immutable receipts");
                if (release == Release.L2) {
                    helper.assertTrue(head == null && arm.anchor() == null, "Ordinary flight invented a receipt-backed head");
                } else {
                    helper.assertTrue(head != null && head.physical().equals(successor.getUUID()) && head.epoch() == 0
                            && head.issued() == arm.serial() && head.completed() == arm.serial()
                            && head.kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                            && head.signature().equals(successor.flightData().orElseThrow().fuel().resourceSignature())
                            && head.invariant().equals(EscrowInvariant.rocket(successor)), "F7 did not advance its typed READY head");
                }
            }
            if (data.ledger().armedFlights().stream().anyMatch(value -> value.transfer().equals(launch))) { return; }
            helper.assertTrue(sawF6 && sawF7 && journal.actionAuthorityAvailable() && journal.find(launch).isEmpty()
                    && journal.acknowledgedGuard(source.logical()).orElseThrow().transfer() == null
                    && RocketEscrowGate.idle(successor) && RocketRuntime.resourceMutationAllowed(successor)
                    && passenger.getVehicle() == successor && successor.getPassengers().equals(List.of(passenger))
                    && ((ServerLevel) predecessor.level()).getEntity(predecessor.getUUID()) == null
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests.flightTickets(server, launch) == 0,
                    "F8 did not leave exactly one available passenger rocket and an idle checked journal");
            completed.add(launch); rocket = successor;
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().committedDebits().equals(completed)
                    && receipts.equals(data.ledger().tombstones()), "Cleanup repeated a debit or changed historical receipts");
            if (completed.size() == 1) {
                var before = rocket.flightData().orElseThrow();
                rocket.updateFlightData(before.withFuel(before.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 2_000),
                        rocket.level().getGameTime()));
                helper.assertTrue(rocket.escrowSection().epoch() == 1 && rocket.flightData().orElseThrow().fuel().totalAmount() == 2_000
                        && rocket.flightData().orElseThrow().fuel().committedDebits().equals(completed), "Successor refuelling lost its new epoch or debit history");
                helper.assertTrue(EscrowRuntime.of(server).flightPhases().allowsNative(rocket, null),
                        "Completed F8 retained an old F5 observation across successor refuelling");
                launch = null; step = 5; return;
            }
            helper.assertTrue(completed.size() == 2, "Required two-flight case did not complete");
            passenger.stopRiding(); server.getPlayerList().remove(passenger); rocket.discard(); source.bank().discard();
            level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            flightChunks.forEach((end, slots) -> slots.forEach(slot ->
                    end.getChunkSource().removeRegionTicket(FLIGHT_FIXTURE, slot, 2, actor)));
            step = 8; helper.succeed();
        }

        private void prepare() {
            var journal = RocketTransferSavedData.get(level.getServer()); if (!journal.retryChecked(level.getServer())) { return; }
            data = ResourceEscrowSavedData.forTests(level.getServer()); coordinator = data.bind(level.getServer());
            source = EscrowTestEndpoints.create(level, position, data.ledger().id()); source.bank().discard();
            rocket = createRocket(level, position, source.logical());
            var sample = source.operation(coordinator.nextSequence());
            var before = rocket.flightData().orElseThrow(); var fuel = before.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000);
            var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), source.logical(),
                    ModIdentity.id("rocket").toString(), chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY, 0,
                    before.fuel().resourceSignature(), fuel.resourceSignature());
            var payload = sample.payload(); payload.putString("FluidName", ModIdentity.id("rocket_fuel").toString());
            var guard = journal.acknowledgedGuard(source.logical()).orElseThrow();
            operation = new EscrowOperation(sample.id(), sample.sequence(), rocket.ownerId().orElseThrow(), sample.createdTick(), sample.phase(), sample.reason(),
                    payload, RocketNativeResources.signature(payload), sample.source(), destination,
                    new EscrowGuard(source.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
            var capture = EscrowNativeBinding.captureTest(operation, rocket, source.source(), before.fuel(), fuel, data.ledger());
            operation = capture.operation();
            if (release != Release.NORMALIZED) {
                var parent = rocket.saveWithoutId(new CompoundTag()); var wrapper = parent.getCompound("RocketEntityData");
                wrapper.put("flight_data", RocketFlightNbtCodec.encode(before.withFuel(fuel, level.getGameTime())));
                wrapper.put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 1,
                        List.of(new EscrowPrefix.Marker(operation.id(), operation.sequence(), "DESTINATION", 1))).encode());
                rocket.load(parent);
            }
            helper.assertTrue(rocket.operational() && level.addFreshEntity(rocket), "Credited rocket fixture failed");
            coordinator.prepareTest(operation, capture.before(), committed -> helper.assertTrue(committed, "L1 was not acknowledged")); step = 1;
        }
        private void exerciseMountAndRefuel() {
            var owner = new FakePlayer(level, new GameProfile(rocket.ownerId().orElseThrow(), "N1Board")); owner.setPos(rocket.position());
            var manager = new RocketManager(); var target = new TravelTarget.BodySurface(CelestialIds.EARTH_ID);
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.BOARD, target, UUID.randomUUID());
            helper.assertTrue(owner.getVehicle() == rocket && rocket.flightData().orElseThrow().passengers().assignment(owner.getUUID()).isPresent(),
                    "Settled rocket cannot mount");
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LEAVE, target, UUID.randomUUID());
            helper.assertTrue(owner.getVehicle() == null && rocket.flightData().orElseThrow().passengers().assignment(owner.getUUID()).isEmpty(),
                    "Settled rocket cannot dismount");
            var player = new BucketPlayer(level, rocket.ownerId().orElseThrow()); player.setPos(rocket.position());
            if (release == Release.NORMALIZED) {
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
                helper.assertTrue(rocket.escrowSection().epoch() == 1, "First normalized resource change did not advance epoch 0");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ClassicFluids.ROCKET_FUEL_BUCKET.get()));
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                    "Settled rocket cannot refuel through the hand exchange");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 2_000 && rocket.escrowSection().epoch() == 2
                    && player.getMainHandItem().is(Items.BUCKET) && RocketEscrowGate.idle(rocket), "Refuel lost resources, epoch or idle state");
        }
    }
    static RocketEntity createRocket(ServerLevel level, BlockPos position, UUID logical) {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(position.getX(), position.getY(), position.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron), new RocketBlock(new RocketPosition(0, 1, 0), iron)),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 0), level.getGameTime(), descriptor);
        var rocket = ModEntities.ROCKET.get().create(level); java.util.Objects.requireNonNull(rocket).initialize(snapshot, logical, UUID.randomUUID());
        return rocket;
    }
    private static final class BucketPlayer extends ServerPlayer {
        BucketPlayer(ServerLevel level, UUID owner) { super(level.getServer(), level, new GameProfile(owner, "N1Refuel")); }
        @Override public void displayClientMessage(Component message, boolean actionBar) { }
    }
}
