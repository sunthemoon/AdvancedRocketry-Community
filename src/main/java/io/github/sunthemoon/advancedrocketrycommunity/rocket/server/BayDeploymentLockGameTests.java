package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W128R-R2 A1 on the real installed services. A durable INTENT locks a rocket waiting in TRANSIT with its PREPARED T:
 * mount, teleport, {@code /kill}, {@code /data}, resources, loaders, disassembly and launch are refused, while a vanilla
 * dismount is not cancelled (R49 Q2) and leaves the seat assigned in T and in the source. N5: a REJECTED receipt does not
 * lock its rocket, so {@code /kill} removes the rocket and the receipt's retained slot leaks until the operator retires it.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentLockGameTests {
    static final String LOCK = "bay_deployment_lock";
    private BayDeploymentLockGameTests() { }

    @GameTest(template = TEMPLATE, batch = LOCK, timeoutTicks = 300)
    public static void anIntentLocksARocketInTransitAndAVanillaDismountLeavesTheSeatWithT(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        UUID owner = UUID.randomUUID();
        ItemStack pkg = dataPackage(owner);
        var player = new RocketLandedDisassemblyGameTestSupport.Requester(level, owner);
        var operator = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        Object[] state = new Object[2]; // the flight and the INTENT
        int[] step = {0, 0};
        helper.onEachTick(() -> {
            Flight flight = (Flight) state[0];
            try {
                helper.assertTrue(++step[1] < 280, "The TRANSIT lock fixture exceeded its bounded wait at step " + step[0]);
                if (step[0] == 0) {
                    BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
                    if (!level.isPositionEntityTicking(origin)) { return; }
                    flight = transit(helper, origin, level.dimension().location(), position(helper.absolutePos(new BlockPos(12, 2, 5))),
                            owner, List.of(pkg), RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), player.getUUID());
                    state[0] = flight;
                    RocketEntity rocket = flight.source();
                    RocketTransferRecord record = flight.record();
                    helper.assertTrue(RocketEscrowGate.idle(rocket) && player.startRiding(rocket, true), "The seated passenger did not board");
                    var intent = intent(server, record, pkg, 0);
                    state[1] = intent;
                    helper.assertTrue(BayReceiptTestHooks.put(server, intent, outcome -> { }).queued(), "INTENT refused");
                    RocketTransferSavedData.get(server).put(record); // T is held from its first classification
                    transitRows(helper, rocket, record, player, operator);
                    step[0] = 1;
                } else if (step[0] == 1) {
                    // Disassembly answers on later ticks: the rocket must still be whole, in TRANSIT, at altitude.
                    if (step[1] < 10 || !storeIdle(server)) { return; }
                    RocketEntity rocket = flight.source();
                    helper.assertTrue(rocket.isAlive() && !rocket.isRemoved() && RocketEscrowGate.bayHeld(rocket)
                            && rocket.flightData().orElseThrow().state() == RocketFlightState.TRANSIT, "The held rocket changed after disassembly");
                    helper.assertTrue(RocketTransferSavedData.get(server).find(flight.record().transferId()).orElseThrow().checksum()
                                    .equals(flight.record().checksum()),
                            "T changed while held");
                    assertNoDisassembly(helper, rocket, player);
                    helper.assertTrue(BayReceiptTestHooks.remove(server, ((BayReceipt) state[1]).op(), outcome -> { }).queued(), "Removal refused");
                    helper.assertTrue(RocketEscrowGate.bayHeld(rocket), "The lock ended before the removal's acknowledgement");
                    step[0] = 2;
                } else if (step[0] == 2) {
                    if (!storeIdle(server)) { return; }
                    helper.assertTrue(!RocketEscrowGate.bayHeld(flight.source()), "The exact removal did not end the bay part of the lock");
                    close(server, flight, player);
                    step[0] = 3;
                    helper.succeed();
                }
            } catch (RuntimeException | Error failure) { close(server, flight, player); step[0] = 3; throw failure; }
        });
    }

    private static void transitRows(GameTestHelper helper, RocketEntity rocket, RocketTransferRecord record,
                                    RocketLandedDisassemblyGameTestSupport.Requester player,
                                    CommandSourceStack operator) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(RocketEscrowGate.bayHeld(rocket) && !RocketEscrowGate.idle(rocket) && !RocketEscrowGate.resourcesIdle(rocket)
                && RocketEscrowGate.escrowIdle(rocket) && RocketEscrowGate.logicalLocked(server, record.logicalRocketId())
                && !RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId()), "The bay part did not hold L in TRANSIT");
        RocketFlightData before = rocket.flightData().orElseThrow();
        Vec3 position = rocket.position();
        helper.assertTrue(before.state() == RocketFlightState.TRANSIT
                && position.y == record.sourceSnapshot().sourceOrigin().y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, "Not at altitude in TRANSIT");
        // R49 Q2: a vanilla dismount is never cancelled; the passenger leaves while T keeps the seat.
        player.stopRiding();
        helper.assertTrue(!player.isPassenger() && rocket.getPassengers().isEmpty(), "The vanilla dismount was cancelled");
        helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isPresent()
                && record.sourceFlightData().passengers().assignment(player.getUUID()).isPresent(), "The dismount freed the seat");
        helper.assertTrue(!player.startRiding(rocket, true), "Mount was not refused in TRANSIT");
        var other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ARCEBayTransitOther"));
        helper.assertTrue(!other.startRiding(rocket, true), "A second rider mounted the held rocket");
        server.getCommands().performPrefixedCommand(operator, "tp " + rocket.getStringUUID() + " ~ ~5 ~");
        server.getCommands().performPrefixedCommand(operator, "data merge entity " + rocket.getStringUUID() + " {RocketEntityData:{}}");
        server.getCommands().performPrefixedCommand(operator, "kill " + rocket.getStringUUID());
        // Keep the direct resource guards beside the real loader and player-disassembly requests below.
        helper.assertTrue(!RocketEscrowGate.resourcesIdle(rocket), "The entity's resource guard admitted a held rocket in TRANSIT");
        helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Loaders and disassembly were admitted in TRANSIT");
        loaderRefusesTransit(helper, rocket);
        player.setPos(rocket.getX() + 2, rocket.getY(), rocket.getZ());
        helper.assertTrue(player.isAlive() && !player.isSpectator() && player.level() == rocket.level()
                        && rocket.ownerId().filter(player.getUUID()::equals).isPresent()
                        && RocketInteraction.withinRange(player, rocket.getX(), rocket.getY(), rocket.getZ()),
                "The disassembly requester is not the eligible owner in range");
        helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() > 0,
                "The fixture must distinguish refusal from a fueled confirmation offer");
        assertNoDisassembly(helper, rocket, player);
        RocketRuntime.requestDisassembly(player, rocket);
        helper.assertTrue(player.actionBar.equals(List.of(RocketValidationCode.REGION_BUSY.translationKey())),
                "Held TRANSIT disassembly did not report REGION_BUSY: " + player.actionBar);
        assertNoDisassembly(helper, rocket, player);
        UUID launch = UUID.randomUUID();
        var launched = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
        helper.assertTrue(launched.code() != RocketFlightRequestCode.SUCCESS && RocketTransferSavedData.get(server).find(launch).isEmpty(),
                "Launch was admitted in TRANSIT: " + launched.code());
        helper.assertTrue(rocket.isAlive() && rocket.position().equals(position) && !rocket.isPassenger(),
                "kill or teleport moved or removed the held rocket");
        helper.assertTrue(rocket.flightData().orElseThrow().equals(before), "A held rocket's flight data changed in TRANSIT");
    }

    private static void loaderRefusesTransit(GameTestHelper helper, RocketEntity rocket) {
        var level = helper.getLevel();
        var server = level.getServer();
        var flight = rocket.flightData().orElseThrow();
        var origin = flight.currentOrigin();
        BlockPos at = rocket.blockPosition().offset(2, 0, 0);
        UUID device = UUID.randomUUID();
        var binding = new LoaderBinding(flight.logicalRocketId(), level.dimension().location(), rocket.getUUID(),
                new BlockPos(origin.x(), origin.y(), origin.z()), null);
        var root = new LoaderRoot(rocket.ownerId().orElseThrow(), device, binding,
                LoaderTank.of(ModIdentity.id("rocket_fuel"), null, 1_000), LoaderEscrowSection.neutral());
        level.setBlockAndUpdate(at, ModBlocks.FLUID_FUEL_LOADER.get().defaultBlockState());
        var loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
        helper.assertTrue(loader != null, "The TRANSIT loader was not placed");
        var tag = new CompoundTag(); tag.put(LoaderRoot.KEY, root.encode()); loader.load(tag);
        var runtime = EscrowRuntime.of(server);
        try {
            runtime.loaderAdmissionForTests(server, device, true);
            helper.assertTrue(loader.live() && loader.root().orElseThrow().binding().equals(binding)
                            && rocket.getBoundingBox().distanceToSqr(Vec3.atCenterOf(at)) <= 36,
                    "The live loader is not correctly bound nearby");
            var deviceBefore = loader.saveWithFullMetadata();
            var rocketBefore = rocket.saveWithoutId(new CompoundTag());
            var authority = BayReceiptTestHooks.authority(server);
            int pending = authority.pendingCount();
            long forRocket = authority.pendingFor(flight.logicalRocketId());
            long retained = authority.retainedFor(root.owner());
            helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.ROCKET_BUSY,
                    "The TRANSIT loader attempt did not refuse the busy rocket");
            boolean captured = loader.admitFromQueue();
            helper.assertTrue(!captured && loader.lastAttempt() == FluidFuelLoaderStatus.ROCKET_BUSY,
                    "The TRANSIT loader queue visit was not refused: captured=" + captured + " status=" + loader.lastAttempt());
            helper.assertTrue(deviceBefore.equals(loader.saveWithFullMetadata())
                            && rocketBefore.equals(rocket.saveWithoutId(new CompoundTag())),
                    "The refused TRANSIT loader changed device or rocket resources");
            helper.assertTrue(authority.pendingCount() == pending && authority.pendingFor(flight.logicalRocketId()) == forRocket
                            && authority.retainedFor(root.owner()) == retained && !loader.escrowLocked(),
                    "The refused TRANSIT loader changed reservations or locked the device");
        } finally {
            runtime.loaderAdmissionForTests(server, device, false);
            clearBay(level, at);
        }
    }

    private static void assertNoDisassembly(GameTestHelper helper, RocketEntity rocket,
            RocketLandedDisassemblyGameTestSupport.Requester player) {
        helper.assertTrue(player.offers == 0 && player.token == null && player.command == null && player.messages.isEmpty(),
                "The refused disassembly offered a confirmation or reported disposal: " + player.messages);
        helper.assertTrue(!RocketRuntime.installedTransfers().landedDisassemblyPending(rocket)
                        && RocketRuntime.installedTransfers().landedDisassemblies().find(rocket.assemblyTransactionId().orElseThrow()) == null,
                "The refused disassembly created a pending operation");
        helper.assertTrue(RocketTransactionSavedData.get(helper.getLevel().getServer()).entries().stream()
                        .noneMatch(entry -> entry.snapshot().snapshotId().equals(rocket.snapshot().orElseThrow().snapshotId())),
                "The refused disassembly created a block transaction");
    }

    private static void close(net.minecraft.server.MinecraftServer server, Flight flight, FakePlayer player) {
        player.stopRiding();
        if (flight == null) { return; }
        var journal = RocketTransferSavedData.get(server);
        if (journal.find(flight.record().transferId()).isPresent()) { journal.remove(flight.record().transferId()); }
        RocketTransferEntities.releaseFlightTickets(server, flight.record());
        bays0(server).find(satellite0(flight)).filter(receipt -> receipt.phase() == BayReceipt.Phase.INTENT)
                .ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
        flight.source().discard();
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession bays0(net.minecraft.server.MinecraftServer server) {
        return RocketRuntime.installedBays().orElseThrow().sessionForTests();
    }

    private static UUID satellite0(Flight flight) {
        return BayDeploymentGameTestSupport.bayPayloads(flight.record().sourceSnapshot()).stream().findFirst()
                .map(root -> io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot.decode(root).contents().orElseThrow()
                        .packageTag().orElseThrow())
                .map(tag -> io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayPackage.decode(tag).identity().orElseThrow().satelliteId())
                .orElseThrow();
    }

    /** N5 (W27A-DESIGN section 5): the leak after {@code /kill} with a real rocket, then the operator's retirement. */
    @GameTest(template = TEMPLATE, batch = LOCK, timeoutTicks = 300)
    public static void aKilledRocketLeaksItsRejectedReceiptUntilTheOperatorRetiresIt(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        UUID owner = UUID.randomUUID();
        ItemStack pkg = dataPackage(owner);
        UUID satellite = satellite(pkg);
        var operator = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        RocketEntity[] rocket = {null};
        long[] retained = {0};
        int[] step = {0, 0};
        helper.onEachTick(() -> {
            try {
                helper.assertTrue(++step[1] < 280, "The N5 fixture exceeded its bounded wait at step " + step[0]);
                var session = bays(helper).sessionForTests();
                switch (step[0]) {
                    case 0 -> {
                        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 10));
                        if (!level.isPositionEntityTicking(origin)) { return; }
                        var flight = transit(helper, origin, helper.absolutePos(new BlockPos(12, 2, 10)), owner, List.of(pkg),
                                RocketEscrowSectionPatch.NEUTRAL);
                        flight.source().discard();
                        var record = flight.record();
                        rocket[0] = ModEntities.ROCKET.get().create(level);
                        helper.assertTrue(rocket[0] != null, "No rocket");
                        rocket[0].initialize(record.sourceSnapshot(), record.logicalRocketId(), owner);
                        RocketTransferEntities.positionAtOrigin(rocket[0], record.sourceSnapshot().sourceOrigin());
                        helper.assertTrue(level.addFreshEntity(rocket[0]), "Rocket spawn failed");
                        var rejected = intent(server, record, pkg, 0).withPhase(BayReceipt.Phase.REJECTED, "DEPLOYMENT_UNAVAILABLE",
                                server.getTickCount());
                        helper.assertTrue(BayReceiptTestHooks.put(server, rejected, outcome -> { }).queued(), "REJECTED fixture refused");
                        step[0] = 1;
                    }
                    case 1 -> {
                        var receipt = session.find(satellite).orElseThrow();
                        if (!storeIdle(server) || !session.acknowledged(receipt.op())) { return; }
                        helper.assertTrue(!RocketEscrowGate.bayHeld(rocket[0]) && RocketEscrowGate.idle(rocket[0]),
                                "A REJECTED receipt must not lock its rocket");
                        // This owner's slots only: other tests of the batch change the server's count concurrently.
                        retained[0] = BayReceiptTestHooks.authority(server).retainedFor(owner);
                        server.getCommands().performPrefixedCommand(operator, "kill " + rocket[0].getStringUUID());
                        helper.assertTrue(rocket[0].isRemoved(), "/kill did not remove the idle rocket");
                        step[0] = 2;
                    }
                    case 2 -> {
                        var receipt = session.find(satellite).orElseThrow();
                        helper.assertTrue(receipt.phase() == BayReceipt.Phase.REJECTED && session.acknowledged(receipt.op())
                                && BayReceiptTestHooks.authority(server).retainedFor(owner) == retained[0] && retained[0] == 1,
                                "The killed rocket's REJECTED receipt did not keep its retained slot (the N5 leak)");
                        helper.assertTrue(RocketTransferSavedData.get(server).findByLogicalRocket(receipt.rocket()).isEmpty(),
                                "A transfer record names the killed rocket");
                        int result = server.getCommands().performPrefixedCommand(operator, "arce bay receipt retire-rejected " + satellite);
                        helper.assertTrue(result == 1, "The operator's retirement was refused: " + result);
                        step[0] = 3;
                    }
                    case 3 -> {
                        if (!storeIdle(server) || session.find(satellite).isPresent()) { return; }
                        helper.assertTrue(BayReceiptTestHooks.authority(server).retainedFor(owner) == retained[0] - 1,
                                "The retirement did not free exactly the leaked slot");
                        step[0] = 4;
                        helper.succeed();
                    }
                    default -> { }
                }
            } catch (RuntimeException | Error failure) {
                if (rocket[0] != null) { rocket[0].discard(); }
                bays(helper).sessionForTests().find(satellite)
                        .ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
                step[0] = 4;
                throw failure;
            }
        });
    }
}
