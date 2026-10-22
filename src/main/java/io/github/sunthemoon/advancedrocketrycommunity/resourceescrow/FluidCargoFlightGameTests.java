package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketCargoGovernedRecognitionGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * C-E3 A1 end-to-end: C-I's governed record over a real cargo result with missing and terminal ARMED authority; two
 * successive ordinary cargo-mutated flights, the second after a mutation by the successor; then exactly-once
 * latest-cargo disassembly, where a quote taken before the last cargo operation is refused.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidCargoFlightGameTests {
    private static Scenario running;
    private FluidCargoFlightGameTests() { }

    @GameTest(template = "empty", batch = "fluid_cargo_flights", timeoutTicks = 3_000)
    public static void twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce(GameTestHelper helper) {
        running = new Scenario(helper); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = "fluid_cargo_flights")
    public static void cleanup(ServerLevel level) { if (running != null) { running.close(); running = null; } }

    static final class Scenario implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final BlockPos origin = new BlockPos(636_008 + 512 * 3, 100, 636_008);
        final EscrowCargoGameTestWorld world;
        RocketEntity rocket;
        RocketStructureSnapshot before;
        RocketFluidCargoBlockEntity device;
        EscrowOperation op;
        EscrowCargoLaunch flight;
        RocketLandedDisassemblyGameTestSupport support;
        RocketLandedDisassemblyGameTestSupport.Requester requester;
        UUID quote;
        long firstSerial, quoteTick, since;
        int step;
        boolean done;

        Scenario(GameTestHelper helper) {
            this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer());
            world = new EscrowCargoGameTestWorld(helper, List.of(origin, origin.offset(32, 0, 0), origin.offset(64, 0, 0)));
        }

        void tick() {
            if (done) { return; }
            pacer.pace();
            var server = helper.getLevel().getServer();
            switch (step) {
                case 0 -> {
                    if (!world.ready()) { return; }
                    world.isolate();
                    rocket = world.rocket(origin, FluidStack.EMPTY); before = rocket.snapshot().orElseThrow();
                    cargo(origin.offset(2, 0, 0), RocketCargoRole.LOAD, 5_000); step = 1;
                }
                case 1 -> {
                    if (!retired()) { return; }
                    check(EscrowCargoGameTestWorld.cargo(rocket) == 1_000 && EscrowCargoGameTestWorld.tank(device) == 4_000
                            && rocket.escrowSection().epoch() == 1, "The first ordinary load did not conserve 1,000 mB");
                    governed();
                    flight = new EscrowCargoLaunch(helper, world, rocket, origin.offset(32, 0, 0), false); step = 2;
                }
                case 2 -> {
                    if (!flight.tick()) { return; }
                    firstSerial = flight.arm().serial(); var first = device; rocket = flight.survivor(); flight.close(); flight = null;
                    check(EscrowCargoGameTestWorld.cargo(rocket) == 1_000, "The first flight lost the latest cargo");
                    stale(first);
                    // The successor's own mutation: a second ordinary load at the arrival pad.
                    cargo(origin.offset(34, 0, 0), RocketCargoRole.LOAD, 5_000); step = 3;
                }
                case 3 -> {
                    if (!retired()) { return; }
                    check(EscrowCargoGameTestWorld.cargo(rocket) == 2_000 && rocket.escrowSection().epoch() == 1,
                            "The successor's load did not advance its own epoch with 1,000 mB more");
                    // A landed successor refuels before it may accept a plan; the fuel change advances its epoch again.
                    var landed = rocket.flightData().orElseThrow();
                    rocket.updateFlightData(landed.withFuel(landed.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000),
                            helper.getLevel().getGameTime()));
                    check(rocket.escrowSection().epoch() == 2 && EscrowCargoGameTestWorld.cargo(rocket) == 2_000,
                            "Refuelling after the cargo change lost the epoch or the cargo");
                    flight = new EscrowCargoLaunch(helper, world, rocket, origin.offset(64, 0, 0), false); step = 4;
                }
                case 4 -> {
                    if (!flight.tick()) { return; }
                    check(flight.arm().serial() > firstSerial, "The second flight reused the first flight's governed authority");
                    rocket = flight.survivor(); flight.close(); flight = null;
                    check(EscrowCargoGameTestWorld.cargo(rocket) == 2_000 && world.data().ledger().rocketAuthorities().isEmpty()
                            && world.data().ledger().tombstones().isEmpty(), "Ordinary flights invented receipts or lost cargo");
                    // A fueled disassembly quote taken before the last cargo operation.
                    support = RocketLandedDisassemblyGameTestSupport.create(helper);
                    requester = new RocketLandedDisassemblyGameTestSupport.Requester(helper.getLevel(), rocket.ownerId().orElseThrow());
                    requester.setPos(rocket.position()); support.requestDisassembly(requester, rocket);
                    check(requester.offers == 1 && requester.token != null, "No fueled disassembly quote was offered");
                    quote = requester.token; quoteTick = server.getTickCount();
                    cargo(origin.offset(66, 0, 0), RocketCargoRole.UNLOAD, 0); step = 5;
                }
                case 5 -> {
                    if (!retired()) { return; }
                    check(EscrowCargoGameTestWorld.cargo(rocket) == 1_000 && EscrowCargoGameTestWorld.tank(device) == 1_000,
                            "The last unload did not conserve 1,000 mB");
                    disassemble(server);
                }
                case 6 -> {
                    if (device.displayStatus() != RocketCargoStatus.STALE_BINDING) {
                        check(server.getTickCount() - since < 200, "The disassembled rocket's device did not show STALE_BINDING"); return;
                    }
                    var lines = EscrowOperatorDiagnostics.status(server);
                    check(lines.get(0).startsWith("escrow admission=disabled operations=0/64 held=0 settling_members=0 released=0 pruning_groups=0 ")
                            && lines.get(1).startsWith("receipts=0/64 receipts_reserved=0 accept_current_members=0 abandon_members=0 floor_members=0 ")
                            && lines.get(1).endsWith(" ready_heads=0/64 armed=0/64"), "Status output changed: " + lines);
                    close(); done = true; helper.succeed();
                }
                default -> { }
            }
        }

        /** A device bound to the current rocket, admitted once; ordinary C0-C8 then retires it. */
        private void cargo(BlockPos at, RocketCargoRole role, int amount) {
            device = world.device(at, rocket, role, amount); op = null; world.enable(device, true);
        }

        private boolean retired() {
            var data = world.data(); if (data == null) { return false; }
            if (op == null) {
                op = world.member(device); if (op == null) { return false; }
                world.enable(device, false);
                check(op.guard().transfer() == null && op.phase() == EscrowOperation.Phase.PREPARED, "Cargo was admitted under a transfer guard");
            }
            return data.ledger().records().isEmpty() && EscrowCargoGameTestWorld.section(device).markers().isEmpty()
                    && rocket.escrowSection().markers().isEmpty() && data.ledger().tombstones().isEmpty();
        }

        /** C-I over the real cargo result: recognized, yet held when its ARMED authority is missing or terminal. */
        private void governed() {
            var root = world.data().ledger();
            var record = EscrowCargoTransferFixtures.governed(rocket, before, root.id());
            var missing = EscrowGovernedDispatch.decide(root, record);
            check(missing.route() == EscrowGovernedDispatch.Route.HOLD && "GOVERNED_ARM_MISSING".equals(missing.reason()),
                    "A missing ARMED authority was not held: " + missing);
            RocketCargoGovernedRecognitionGameTestSupport.verify(helper, rocket, record, missing);
            var a = record.nativeTransfer().orElseThrow().armed();
            var handedOff = new EscrowFlightTables.Armed(a.ledger(), a.logical(), a.owner(), a.source(), a.epoch(), a.transfer(), a.serial(),
                    a.generation(), a.binding(), a.launch(), a.sourceEnvelope(), a.destinationEnvelope(), a.destination(), a.journal(),
                    a.anchor(), a.releaseGeneration(), EscrowFlightTables.Outcome.HANDED_OFF);
            var terminal = new EscrowLedger(root.id(), root.issuedThrough(), Math.max(root.writerGeneration(), a.generation()), root.records(),
                    root.floors(), root.tombstones(), Math.max(root.issuedLaunchSerial(), a.serial()), root.rocketAuthorities(), List.of(handedOff));
            var cleanup = EscrowGovernedDispatch.decide(terminal, record);
            check(cleanup.route() == EscrowGovernedDispatch.Route.CLEANUP, "A terminal ARMED authority was not routed to cleanup only: " + cleanup);
            RocketCargoGovernedRecognitionGameTestSupport.verify(helper, rocket, record, cleanup);
        }

        /** The device bound to a departed rocket keeps its root and reports STALE_BINDING. */
        private void stale(RocketFluidCargoBlockEntity first) {
            first.attempt();
            check(first.displayStatus() == RocketCargoStatus.STALE_BINDING && !first.escrowLocked(), "A departed rocket's device is not stale");
        }

        private void disassemble(net.minecraft.server.MinecraftServer server) {
            var level = helper.getLevel(); var at = rocket.blockPosition();
            check(server.getTickCount() - quoteTick < 200, "Inconclusive: the pre-cargo quote expired before the cargo operation retired");
            check(!support.confirmDisassembly(requester, quote) && rocket.isAlive() && level.getBlockState(at).isAir(),
                    "A quote taken before the cargo operation materialized the rocket");
            support.requestDisassembly(requester, rocket);
            check(requester.offers == 2 && !quote.equals(requester.token), "No fresh quote was offered for the latest cargo");
            var token = requester.token;
            check(support.confirmDisassembly(requester, token) && rocket.isRemoved(), "The latest-cargo disassembly was refused");
            check(level.getBlockEntity(at) instanceof PressurizedTankBlockEntity tank && tank.fluidState().getFluid() == Fluids.WATER
                    && tank.fluidState().getAmount() == 1_000 && level.getBlockState(at.above()).is(Blocks.IRON_BLOCK),
                    "Disassembly did not materialize the latest cargo");
            check(!support.confirmDisassembly(requester, token) && level.getBlockEntity(at) instanceof PressurizedTankBlockEntity again
                    && again.fluidState().getAmount() == 1_000 && helper.getLevel().getEntities(io.github.sunthemoon.advancedrocketrycommunity
                    .registry.ModEntities.ROCKET.get(), new net.minecraft.world.phys.AABB(at).inflate(8), entity -> true).isEmpty(),
                    "A repeated confirmation materialized the cargo again");
            since = server.getTickCount(); step = 6;
        }

        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        @Override public void close() { if (flight != null) { flight.close(); } world.close(); }
    }
}
