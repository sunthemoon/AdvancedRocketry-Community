package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W363 (W358's B6.8 finding): a real governed launch of an escrow-bound rocket with two packaged bays, driven by the
 * installed dispatcher. At its ASCENT to TRANSIT change both bays are refused with BAY_SOURCE_PROTECTED and the bay
 * menu shows it; no claim, receipt or registration exists, the packages are byte-equal and the flight stays governed.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayGovernedFlightGameTests {
    private BayGovernedFlightGameTests() { }

    @GameTest(template = "empty", batch = "bay_governed_flight_refusal", timeoutTicks = 600)
    public static void aGovernedFlightRefusesItsBaysWithBaySourceProtectedAndKeepsItsPackages(GameTestHelper helper) {
        new Scenario(helper).start();
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final BlockPos position = new BlockPos(606_008, 100, 606_008), destination = position.offset(32, 0, 0);
        private final BlockPos menuBay = position.offset(3, 0, 3);
        private final UUID owner = UUID.randomUUID(), transfer = UUID.randomUUID();
        private final ItemStack data, solar;
        private RocketEntity rocket;
        private ResourceEscrowSavedData escrow;
        private RocketFlightData initial;
        private RocketEscrowSection wrapper;
        private EscrowGameTestLaunch launch;
        private List<CompoundTag> before;
        private BlockState menuTerrain;
        private boolean accepted, discarded, observedAscent;
        private int step;

        Scenario(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            data = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
            SatelliteItemData.write(data, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("data_satellite")));
            solar = new ItemStack(ModItems.SATELLITE_PACKAGE.get());
            SatelliteItemData.write(solar, new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("solar_satellite"),
                    SatelliteKind.SOLAR, List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("solar_transmitter_module"),
                            ModIdentity.id("satellite_solar_module"))));
        }

        void start() {
            for (var pos : List.of(position, destination)) {
                var chunk = new ChunkPos(pos); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
                level.getChunk(chunk.x, chunk.z);
            }
            helper.onEachTick(this::tick);
        }

        private void tick() {
            if (step == 2) { return; }
            pacer.pace();
            if (List.of(position, destination).stream().anyMatch(pos -> !level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                    || !level.isPositionEntityTicking(pos))) { return; }
            var server = level.getServer();
            var journal = RocketTransferSavedData.get(server);
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                prepare(); step = 1; return;
            }
            helper.assertTrue(!discarded, "Source preparation was discarded");
            launch.tick();
            if (!accepted) { return; }
            var flight = rocket.flightData().orElseThrow();
            UUID logical = initial.logicalRocketId();
            helper.assertTrue(!BayReceipts.holdsRocket(server, logical), "A governed flight's bays started a claim or a receipt");
            if (flight.state() != RocketFlightState.TRANSIT) {
                observedAscent |= flight.state() == RocketFlightState.ASCENT;
                for (var pkg : List.of(data, solar)) {
                    helper.assertTrue(BayReceipts.deploymentStatus(server, satellite(pkg)).isEmpty(), "A bay was refused before ascent completed");
                }
                return;
            }
            helper.assertTrue(observedAscent && flight.activeTransferId().filter(transfer::equals).isPresent(), "The source skipped its ascent or lost T");
            var record = journal.find(transfer).orElseThrow();
            helper.assertTrue(record.phase() == RocketTransferPhase.PREPARED && record.nativeTransfer().isPresent()
                    && escrow.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                    && wrapper.equals(rocket.escrowSection()), "The refusal changed the governed flight's authority");
            for (var pkg : List.of(data, solar)) {
                var status = BayReceipts.deploymentStatus(server, satellite(pkg)).orElse(null);
                helper.assertTrue(status != null && status.phase().equals("NONE") && status.reason().equals("BAY_SOURCE_PROTECTED"),
                        "D0 did not refuse the governed flight's bay with BAY_SOURCE_PROTECTED: " + status);
                helper.assertTrue(BayReceiptTestHooks.store(server).flatMap(store -> store.find(satellite(pkg))).isEmpty(),
                        "The refusal wrote a receipt");
                helper.assertTrue(SatelliteMissionSavedData.get(server).satellite(satellite(pkg)).isEmpty(), "The refusal registered a satellite");
            }
            helper.assertTrue(before.equals(payloads(record.sourceSnapshot())) && before.equals(payloads(rocket.snapshot().orElseThrow()))
                    && before.equals(payloads(record.destinationSnapshot())), "The packages changed");
            var bay = (SatelliteBayBlockEntity) level.getBlockEntity(menuBay);
            helper.assertTrue(bay.phase() == SatelliteBayStatus.Phase.NONE && bay.reason() == SatelliteBayStatus.Reason.BAY_SOURCE_PROTECTED,
                    "The bay menu reason is not shown");
            finish(journal);
        }

        private void prepare() {
            escrow = ResourceEscrowSavedData.forTests(level.getServer()); escrow.bind(level.getServer());
            long now = level.getGameTime();
            var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
            var bayState = new RocketBlockState(ResourceLocation.tryParse("advancedrocketrycommunity:rocket_satellite_bay"), Map.of());
            var blocks = new ArrayList<RocketBlock>();
            blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), iron));
            int height = 1;
            for (var pkg : List.of(data, solar)) {
                blocks.add(new RocketBlock(new RocketPosition(0, height++, 0), bayState,
                        new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, root(pkg))));
            }
            var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
            var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                    new RocketPosition(position.getX(), position.getY(), position.getZ()), blocks, List.of(new RocketPosition(0, 0, 0)),
                    new RocketStats(blocks.size(), 100, 1_000, 2_000, 1, 1, 1, 2), now, descriptor);
            rocket = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(rocket != null, "No source entity");
            rocket.initialize(snapshot, UUID.randomUUID(), owner);
            var base = rocket.flightData().orElseThrow();
            var fuel = base.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var tag = rocket.saveWithoutId(new CompoundTag());
            // The binding a real resource-loader fill leaves on the rocket; it is what selects the governed launch.
            wrapper = new RocketEscrowSection(escrow.ledger().id(), 7, List.of());
            tag.getCompound("RocketEntityData").put("resource_escrow", wrapper.encode());
            rocket.load(tag);
            helper.assertTrue(level.addFreshEntity(rocket), "Source was not published");
            initial = rocket.flightData().orElseThrow();
            before = payloads(rocket.snapshot().orElseThrow());
            helper.assertTrue(before.size() == 2, "The fixture rocket lost a bay");
            // A world bay holding the same data package: its menu status is the satellite's deployment status.
            helper.assertTrue(level.getBlockEntity(menuBay) == null, "The menu bay overlaps a block entity");
            menuTerrain = level.getBlockState(menuBay);
            level.setBlock(menuBay, ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState(), 3);
            CompoundTag outer = new CompoundTag();
            outer.put(SatelliteBayRoot.KEY, root(data));
            ((SatelliteBayBlockEntity) level.getBlockEntity(menuBay)).load(outer);
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                    new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W363 bay fixture")));
            launch = new EscrowGameTestLaunch(helper, escrow.ledger().id(), initial.logicalRocketId(), transfer,
                    (accept, discard) -> EscrowGovernedPreparation.begin(rocket,
                            initial.withPlan(plan).startCountdown(level.getGameTime()), to, record -> true, accept, discard),
                    record -> accepted = true, () -> discarded = true);
            launch.begin();
        }

        private void finish(RocketTransferSavedData journal) {
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            rocket.discard();
            level.removeBlockEntity(menuBay); level.setBlock(menuBay, menuTerrain, 3);
            for (var pos : List.of(position, destination)) {
                level.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(pos), 2, Unit.INSTANCE);
            }
            step = 2; helper.succeed();
        }

        private CompoundTag root(ItemStack pkg) {
            return SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, pkg.copyWithCount(1).serializeNBT(), null));
        }

        private static UUID satellite(ItemStack pkg) { return SatelliteItemData.rawSatelliteId(pkg).orElseThrow(); }

        /** The bay payloads of a snapshot, in block order. */
        private static List<CompoundTag> payloads(RocketStructureSnapshot snapshot) {
            var payloads = new ArrayList<CompoundTag>();
            for (RocketBlock block : snapshot.blocks()) {
                block.blockEntityPayload().filter(payload -> SatelliteBayRocketAdapter.ID.equals(payload.adapterId()))
                        .ifPresent(payload -> payloads.add(payload.data()));
            }
            return payloads;
        }
    }
}
