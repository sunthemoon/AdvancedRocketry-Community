package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
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

/** N1: a current-ledger binding survives real O2/O3/O4 without becoming a permanent interlock. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSettlementIdleGameTests {
    private RocketSettlementIdleGameTests() { }

    @GameTest(template = "empty", batch = "rocket_settlement_idle", timeoutTicks = 1_200)
    public static void acknowledgedSettlementRestoresMountRefuelAndLaunch(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos position = new BlockPos(550_001, 100, 550_001);
        private final ChunkPos chunk = new ChunkPos(position);
        private final GameTestTickPacer pacer;
        private final UUID actor = UUID.randomUUID();
        private final UUID launch = UUID.randomUUID();
        private ResourceEscrowSavedData data;
        private EscrowCoordinator coordinator;
        private EscrowTestEndpoints.Fixture source;
        private RocketEntity rocket;
        private EscrowOperation operation;
        private int step;
        private boolean intentQueued;

        Scenario(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
        }
        void start() {
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
            var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is unavailable");
            var pad = new ChunkPos(SafeCelestialTravel.FIXED_FEET_POSITION);
            moon.getChunkSource().addRegionTicket(TicketType.START, pad, 2, Unit.INSTANCE);
            moon.getChunk(pad.x, pad.z);
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
                    helper.assertTrue(data.ledger().id().equals(rocket.escrowSection().ledger()) && rocket.escrowSection().epoch() == 1,
                            "O4 reset the permanent ledger binding or epoch");
                    helper.assertTrue(RocketEscrowGate.idle(rocket), "Settled current-ledger rocket remains locked");
                    exerciseMountAndRefuel(); step = 5;
                }
                case 5 -> {
                    var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
                    if (!moon.areEntitiesLoaded(new ChunkPos(SafeCelestialTravel.FIXED_FEET_POSITION).toLong())
                            || !moon.isPositionEntityTicking(SafeCelestialTravel.FIXED_FEET_POSITION)) { return; }
                    var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
                    RocketJournalGameTestSupport.assertPendingLaunch(helper, result, rocket); step = 6;
                }
                case 6 -> {
                    if (rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN) { return; }
                    helper.assertTrue(RocketTransferSavedData.get(level.getServer()).acknowledgedCurrent(), "Launch preceded its journal acknowledgement");
                    helper.assertTrue(data.ledger().id().equals(rocket.escrowSection().ledger()) && rocket.escrowSection().epoch() == 2,
                            "Launch reset the post-refuel escrow envelope");
                    rocket.discard(); source.bank().discard(); level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    RocketTransferSavedData.get(level.getServer()).remove(launch); RocketJournalGameTestSupport.acknowledgeFixture(helper);
                    level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
                    var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
                    moon.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(SafeCelestialTravel.FIXED_FEET_POSITION), 2, Unit.INSTANCE);
                    step = 7; helper.succeed();
                }
                default -> { }
            }
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
            operation = new EscrowOperation(sample.id(), sample.sequence(), sample.owner(), sample.createdTick(), sample.phase(), sample.reason(),
                    payload, RocketNativeResources.signature(payload), sample.source(), destination,
                    new EscrowGuard(source.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
            var parent = rocket.saveWithoutId(new CompoundTag()); var wrapper = parent.getCompound("RocketEntityData");
            wrapper.put("flight_data", RocketFlightNbtCodec.encode(before.withFuel(fuel, level.getGameTime())));
            wrapper.put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 1,
                    List.of(new EscrowPrefix.Marker(operation.id(), operation.sequence(), "DESTINATION", 1))).encode());
            rocket.load(parent); helper.assertTrue(rocket.operational() && level.addFreshEntity(rocket), "Credited rocket fixture failed");
            coordinator.prepareTest(operation, committed -> helper.assertTrue(committed, "L1 was not acknowledged")); step = 1;
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
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ClassicFluids.ROCKET_FUEL_BUCKET.get()));
            helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                    "Settled rocket cannot refuel through the hand exchange");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 2_000 && rocket.escrowSection().epoch() == 2
                    && player.getMainHandItem().is(Items.BUCKET) && RocketEscrowGate.idle(rocket), "Refuel lost resources, epoch or idle state");
        }
    }
    private static RocketEntity createRocket(ServerLevel level, BlockPos position, UUID logical) {
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
