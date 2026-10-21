package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * C-E3 A1: operator settlement O1 to O4 of real fluid cargo with real devices: a load held after its device published
 * (neutral rocket normalization behind a placement veto), an unload held after both published (both survivor proofs,
 * then a governed launch on READY), and an ABANDON with the rocket missing. Receipts and inspector output are pinned.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidCargoSettlementGameTests {
    enum Mode { LOAD, UNLOAD, ABANDON }
    private static Scenario running;
    private FluidCargoSettlementGameTests() { }

    @GameTest(template = "empty", batch = "fluid_cargo_settlement_load", timeoutTicks = 1_600)
    public static void heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears(GameTestHelper helper) { start(helper, Mode.LOAD); }
    @AfterBatch(batch = "fluid_cargo_settlement_load")
    public static void cleanupLoad(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "fluid_cargo_settlement_unload", timeoutTicks = 2_000)
    public static void heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady(GameTestHelper helper) { start(helper, Mode.UNLOAD); }
    @AfterBatch(batch = "fluid_cargo_settlement_unload")
    public static void cleanupUnload(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "fluid_cargo_settlement_abandon", timeoutTicks = 1_600)
    public static void abandonProvesTheMissingRocketAndTheSurvivingDevice(GameTestHelper helper) { start(helper, Mode.ABANDON); }
    @AfterBatch(batch = "fluid_cargo_settlement_abandon")
    public static void cleanupAbandon(ServerLevel level) { cleanup(); }

    private static void start(GameTestHelper helper, Mode mode) {
        running = new Scenario(helper, mode); helper.onEachTick(running::tick);
    }
    private static void cleanup() { if (running != null) { running.close(); running = null; } }

    static final class Scenario implements AutoCloseable {
        final GameTestHelper helper;
        final Mode mode;
        final GameTestTickPacer pacer;
        final BlockPos origin;
        final EscrowCargoGameTestWorld world;
        final UUID actor = UUID.randomUUID();
        EscrowCargoLaunch launch;
        RocketEntity rocket;
        RocketFluidCargoBlockEntity device;
        EscrowOperation op;
        EscrowSettlementIntent preview;
        String beforeHash, heldHash;
        int step, since, vetoes;
        volatile boolean cut;
        boolean queued, cutInstalled, done;

        Scenario(GameTestHelper helper, Mode mode) {
            this.helper = helper; this.mode = mode; pacer = new GameTestTickPacer(helper.getLevel().getServer());
            origin = new BlockPos(636_008 + 512 * mode.ordinal(), 100, 636_008);
            world = new EscrowCargoGameTestWorld(helper, List.of(origin, origin.offset(32, 0, 0)));
        }

        void tick() {
            if (done) { return; }
            pacer.pace();
            var server = helper.getLevel().getServer(); var runtime = EscrowRuntime.of(server);
            switch (step) {
                case 0 -> {
                    if (!world.ready()) { return; }
                    world.isolate();
                    boolean load = mode == Mode.LOAD;
                    rocket = world.rocket(origin, load ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000));
                    device = world.device(origin.offset(2, 0, 0), rocket, load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD, load ? 5_000 : 0);
                    beforeHash = rocket.snapshot().orElseThrow().contentHash();
                    if (mode == Mode.ABANDON) { world.pause(device, true); }
                    else {
                        // Hold the ordinary executor right after one endpoint (load) or both (unload) published.
                        String label = load ? "C2_SOURCE_AFTER" : "C2_DESTINATION_AFTER"; UUID id = EscrowCargoGameTestWorld.id(device);
                        runtime.terminalCutForTests(server, value -> {
                            if (!cut && value.equals(label) && server.isSameThread()) { cut = true; runtime.cargoSwitches.pause(id, true); }
                        });
                        cutInstalled = true;
                    }
                    world.enable(device, true); step = 1;
                }
                case 1 -> {
                    var data = world.data(); if (data == null) { return; }
                    var member = world.member(device); if (member == null) { return; }
                    if (op == null) {
                        op = member; world.enable(device, false);
                        check(op.phase() == EscrowOperation.Phase.PREPARED && op.nativeBinding().complete() && op.guard().transfer() == null,
                                "Cargo admission did not reach a complete unguarded PREPARED");
                    }
                    if (mode != Mode.ABANDON && !cut) { return; }
                    if (cutInstalled) { runtime.terminalCutForTests(server, null); cutInstalled = false; }
                    var coordinator = data.bind(server);
                    coordinator.transition(op.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 2;
                }
                case 2 -> held(server);
                case 3 -> confirm(server);
                case 4 -> veto(server);
                case 5 -> released(server);
                case 6 -> { if (launch.tick()) { finish(); } }
                default -> { }
            }
        }

        /** The durable manual conflict; then O1 freezes exactly the observed native states. */
        private void held(net.minecraft.server.MinecraftServer server) {
            var data = world.data(); var coordinator = data.bind(server);
            var record = data.ledger().records().stream().filter(value -> value.id().equals(op.id())).findFirst().orElse(null);
            if (record == null || record.phase() != EscrowOperation.Phase.REPAIR || !coordinator.writerIdle()) { return; }
            var deviceSection = EscrowCargoGameTestWorld.section(device);
            boolean deviceAfter = mode != Mode.ABANDON;
            boolean rocketAfter = mode == Mode.UNLOAD;
            check(deviceSection.epoch() == (deviceAfter ? 1 : 0) && deviceSection.markers().size() == (deviceAfter ? 1 : 0)
                    && rocket.escrowSection().epoch() == (rocketAfter ? 1 : 0) && rocket.escrowSection().markers().size() == (rocketAfter ? 1 : 0)
                    && (rocketAfter ? rocket.escrowSection().ledger() != null : rocket.escrowSection().equals(RocketEscrowSection.neutral())),
                    "The held native states differ from the cut: device=" + deviceSection + " rocket=" + rocket.escrowSection());
            if (heldHash == null) {
                heldHash = rocket.snapshot().orElseThrow().contentHash();
                check(rocketAfter != heldHash.equals(beforeHash), "The rocket's cargo content differs from its held side");
            }
            // ABANDON: the rocket goes missing a tick before the first observation.
            if (mode == Mode.ABANDON && !rocket.isRemoved()) { rocket.discard(); return; }
            var choice = mode == Mode.ABANDON ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT;
            preview = EscrowRuntime.of(server).settlement().preview(server, op.id(), choice, actor, "W131Cargo");
            check(EscrowOperatorDiagnostics.preview(preview).contains(" choice=" + choice + " members=1 ")
                    && EscrowOperatorDiagnostics.preview(preview).contains("accounting=UNKNOWN"), "Preview text changed");
            for (var observation : preview.observations()) {
                boolean isRocket = observation.endpoint().key().equals(op.rocket().key());
                if (isRocket && mode == Mode.ABANDON) {
                    check(observation.missing(), "ABANDON lost the missing rocket"); continue;
                }
                var expected = isRocket ? EscrowSignatureKind.FUEL_VECTOR_V1 : EscrowSignatureKind.FLUID_TANK_V1;
                check(observation.signatureKind() == expected && observation.bound() == (isRocket ? rocketAfter : deviceAfter)
                        && observation.quantity() == (isRocket ? rocket.flightData().orElseThrow().fuel().totalAmount()
                        : EscrowCargoGameTestWorld.tank(device)) && observation.invariantDigest().equals(isRocket
                        ? EscrowInvariant.rocket(rocket) : EscrowInvariant.reference(op.device())),
                        "O1 lost a native projection for " + observation.endpoint().type());
            }
            step = 3;
        }

        private void confirm(net.minecraft.server.MinecraftServer server) {
            if (queued || mode == Mode.ABANDON && server.getTickCount() - preview.previewTick() < 200) { return; }
            queued = EscrowRuntime.of(server).settlement().confirm(server, op.id(), preview.choice(), actor, preview.digest(), committed -> {
                check(committed, "O1 was not acknowledged");
                if (mode == Mode.LOAD) {
                    // The veto: the chosen neutral survivor no longer stands at its frozen placement.
                    rocket.setPos(rocket.getX() + 0.25D, rocket.getY(), rocket.getZ()); since = server.getTickCount(); step = 4;
                } else { step = 5; }
            });
        }

        /** Load only: the moved survivor holds normalization and every proof; restoring it releases the hold. */
        private void veto(net.minecraft.server.MinecraftServer server) {
            var data = world.data(); var runtime = EscrowRuntime.of(server);
            var record = data.ledger().records().stream().filter(value -> value.id().equals(op.id())).findFirst().orElseThrow();
            var intent = EscrowSettlementIntent.decode(record.settlement());
            var view = runtime.settlementRecovery().view(intent);
            check(rocket.escrowSection().equals(RocketEscrowSection.neutral()) && !EscrowSettlementTombstones.installed(data.ledger(), intent)
                    && record.phase() == EscrowOperation.Phase.SETTLING_ACCEPT && view.provedSlots() == 0,
                    "The vetoed survivor was normalized, proved or released");
            if (!view.reason().startsWith("Rocket placement")) {
                check(server.getTickCount() - since < 400, "The placement veto did not hold the settlement: " + view.reason()); return;
            }
            vetoes++;
            var origin = rocket.flightData().orElseThrow().currentOrigin();
            rocket.setPos(origin.x() + 0.5D, origin.y(), origin.z() + 0.5D); step = 5;
        }

        /** O4, then pruning of every survivor; the receipt, head and inspector output are pinned. */
        private void released(net.minecraft.server.MinecraftServer server) {
            var data = world.data(); var root = data.ledger();
            if (root.records().stream().anyMatch(value -> value.id().equals(op.id()))) { return; }
            var lines = EscrowOperatorDiagnostics.inspect(server, op.id());
            if (!lines.get(0).contains(" pruning=DONE ") || !EscrowCargoGameTestWorld.section(device).markers().isEmpty()
                    || mode != Mode.ABANDON && !rocket.escrowSection().markers().isEmpty()) { return; }
            boolean head = mode != Mode.ABANDON;
            var receipt = root.tombstones().stream().filter(tag -> op.id().equals(tag.getUUID("op_id"))).findFirst().orElseThrow();
            check(receipt.getBoolean("released") && receipt.getString("choice").equals(preview.choice().name())
                    && receipt.getInt("payload_amount") == 0 && receipt.getString("accounting_status").equals("UNKNOWN")
                    && receipt.getString("payload_hash").equals(op.payloadHash()) && receipt.getString("decision_digest").equals(preview.digest())
                    && receipt.getList("survivors", 10).size() == (head ? 2 : 1) && receipt.getList("missing", 10).size() == (head ? 0 : 1)
                    && receipt.equals(EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt))), "Receipt changed: " + receipt);
            for (var raw : receipt.getList("survivors", 10)) {
                var survivor = (CompoundTag) raw; var reference = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                boolean isRocket = reference.key().equals(op.rocket().key());
                check(survivor.getString("signature_kind").equals((isRocket ? EscrowSignatureKind.FUEL_VECTOR_V1 : EscrowSignatureKind.FLUID_TANK_V1).name())
                        && survivor.getBoolean("bound") == (isRocket || mode != Mode.ABANDON) && survivor.getLong("quantity")
                        == (isRocket ? rocket.flightData().orElseThrow().fuel().totalAmount() : EscrowCargoGameTestWorld.tank(device)),
                        "Receipt survivor projection changed: " + survivor);
            }
            check(lines.size() == 2 && lines.get(0).startsWith("group=" + preview.digest() + " members=1 omitted=0 pruning=DONE journal=")
                    && lines.get(0).endsWith(" accounting=UNKNOWN") && lines.get(1).equals("operation=" + op.id()
                    + " persisted=RETIRED runtime=RELEASED reason=NONE O2=not-retained pruning=DONE next_retry=none receipt=RELEASED head="
                    + (head ? "READY" : "NONE") + " accounting=UNKNOWN"), "Inspector output changed: " + lines);
            var logical = op.rocket().logicalRocket();
            var ready = root.rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
            if (!head) {
                check(ready == null && EscrowSettlementTombstones.logicalLocked(root, logical) && rocket.isRemoved()
                        && EscrowCargoGameTestWorld.tank(device) == 0 && EscrowCargoGameTestWorld.section(device).equals(RocketEscrowSection.neutral())
                        && !device.escrowLocked(), "ABANDON fabricated a head, unlocked the missing identity or changed the device");
                finish(); return;
            }
            boolean load = mode == Mode.LOAD;
            check(ready != null && ready.physical().equals(rocket.getUUID()) && ready.epoch() == (load ? 0 : 1)
                    && ready.kind() == EscrowSignatureKind.FUEL_VECTOR_V1 && ready.anchor().equals(op.id())
                    && ready.signature().equals(rocket.flightData().orElseThrow().fuel().resourceSignature())
                    && ready.invariant().equals(EscrowInvariant.rocket(rocket)) && ready.outcome() == EscrowFlightTables.Outcome.READY,
                    "O4 did not derive READY from the frozen fuel projection");
            check(rocket.escrowSection().equals(new RocketEscrowSection(root.id(), load ? 0 : 1, List.of()))
                    && EscrowCargoGameTestWorld.section(device).equals(new RocketEscrowSection(root.id(), 1, List.of()))
                    && rocket.snapshot().orElseThrow().contentHash().equals(heldHash)
                    && EscrowCargoGameTestWorld.cargo(rocket) == (load ? 0 : 4_000) && EscrowCargoGameTestWorld.tank(device) == (load ? 4_000 : 1_000)
                    && (!load || vetoes == 1), "Pruning or normalization changed resources, epochs or identities");
            check(RocketEscrowGate.idle(rocket) && !device.escrowLocked(), "The released survivors stayed locked");
            world.enable(device, true);
            check(device.attempt() == RocketCargoStatus.RECEIPT_BACKED && world.member(device) == null, "A receipt-backed rocket admitted new cargo");
            world.enable(device, false);
            if (load) { finish(); return; }
            launch = new EscrowCargoLaunch(helper, world, rocket, origin.offset(32, 0, 0), true);
            step = 6;
        }

        private void finish() { close(); done = true; helper.succeed(); }
        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        @Override public void close() {
            if (cutInstalled) { EscrowRuntime.of(helper.getLevel().getServer()).terminalCutForTests(helper.getLevel().getServer(), null); cutInstalled = false; }
            if (launch != null) { launch.close(); }
            world.close();
        }
    }
}
