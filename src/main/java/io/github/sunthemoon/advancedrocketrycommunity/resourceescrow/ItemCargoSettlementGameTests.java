package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * D-E2 A1: operator settlement O1 to O4 of real item cargo with real devices: a load held after its device published
 * (neutral rocket normalization behind a placement veto), an unload held after both published (both survivor proofs,
 * then a governed launch on READY), a neutral unload source with a normalization veto, and an ABANDON with the rocket
 * missing. Receipts and inspector output are pinned.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemCargoSettlementGameTests {
    enum Mode { LOAD, UNLOAD, ABANDON, UNLOAD_NEUTRAL, ABANDON_DEVICE }
    private static Scenario running;
    private ItemCargoSettlementGameTests() { }

    @GameTest(template = "empty", batch = "item_cargo_settlement_load", timeoutTicks = 1_600)
    public static void heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears(GameTestHelper helper) { start(helper, Mode.LOAD); }
    @AfterBatch(batch = "item_cargo_settlement_load")
    public static void cleanupLoad(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "item_cargo_settlement_unload", timeoutTicks = 2_000)
    public static void heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady(GameTestHelper helper) { start(helper, Mode.UNLOAD); }
    @AfterBatch(batch = "item_cargo_settlement_unload")
    public static void cleanupUnload(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "item_cargo_settlement_unload_neutral", timeoutTicks = 1_600)
    public static void heldUnloadNormalizesItsNeutralSourceOnlyAfterTheVetoClears(GameTestHelper helper) { start(helper, Mode.UNLOAD_NEUTRAL); }
    @AfterBatch(batch = "item_cargo_settlement_unload_neutral")
    public static void cleanupNeutralUnload(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "item_cargo_settlement_abandon", timeoutTicks = 1_600)
    public static void abandonProvesTheMissingRocketAndTheSurvivingDevice(GameTestHelper helper) { start(helper, Mode.ABANDON); }
    @AfterBatch(batch = "item_cargo_settlement_abandon")
    public static void cleanupAbandon(ServerLevel level) { cleanup(); }

    @GameTest(template = "empty", batch = "item_cargo_settlement_abandon_device", timeoutTicks = 1_600)
    public static void abandonProvesTheMissingDeviceAndSurvivingRocket(GameTestHelper helper) { start(helper, Mode.ABANDON_DEVICE); }
    @AfterBatch(batch = "item_cargo_settlement_abandon_device")
    public static void cleanupAbandonDevice(ServerLevel level) { cleanup(); }

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
        RocketItemCargoBlockEntity device;
        EscrowOperation op;
        EscrowSettlementIntent preview;
        EscrowSettlementIntent frozen;
        final java.util.Set<String> proofs = new java.util.HashSet<>();
        final java.util.List<String> order = new java.util.ArrayList<>();
        boolean normalized, jointlyProved;
        String beforeHash, heldHash;
        int step, since, vetoes;
        volatile boolean cut;
        boolean queued, cutInstalled, done;

        Scenario(GameTestHelper helper, Mode mode) {
            this.helper = helper; this.mode = mode; pacer = new GameTestTickPacer(helper.getLevel().getServer());
            origin = new BlockPos(646_008 + 512 * mode.ordinal(), 100, 646_008);
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
                    rocket = world.itemRocket(origin, load ? 60 : 10);
                    device = world.itemDevice(origin.offset(2, 0, 0), rocket, load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD, load ? 10 : 60);
                    beforeHash = rocket.snapshot().orElseThrow().contentHash();
                    if (abandon() || mode == Mode.UNLOAD_NEUTRAL) { world.pause(device, true); }
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
                    if (!abandon() && mode != Mode.UNLOAD_NEUTRAL && !cut) { return; }
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
            boolean deviceAfter = mode == Mode.LOAD || mode == Mode.UNLOAD;
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
            if (mode == Mode.ABANDON_DEVICE && world.level.getBlockEntity(device.getBlockPos()) != null) {
                world.level.removeBlockEntity(device.getBlockPos());
                world.level.setBlockAndUpdate(device.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); return;
            }
            var choice = abandon() ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT;
            preview = EscrowRuntime.of(server).settlement().preview(server, op.id(), choice, actor, "W134Cargo");
            check(EscrowOperatorDiagnostics.preview(preview).contains(" choice=" + choice + " members=1 ")
                    && EscrowOperatorDiagnostics.preview(preview).contains("accounting=UNKNOWN"), "Preview text changed");
            for (var observation : preview.observations()) {
                boolean isRocket = observation.endpoint().key().equals(op.rocket().key());
                if (isRocket && mode == Mode.ABANDON) {
                    check(observation.missing(), "ABANDON lost the missing rocket"); continue;
                }
                if (!isRocket && mode == Mode.ABANDON_DEVICE) {
                    check(observation.missing(), "ABANDON lost the missing device"); continue;
                }
                var expected = isRocket ? EscrowSignatureKind.FUEL_VECTOR_V1 : EscrowSignatureKind.ITEM_BUFFER_V1;
                check(observation.signatureKind() == expected && observation.bound() == (isRocket ? rocketAfter : deviceAfter)
                        && observation.quantity() == (isRocket ? rocket.flightData().orElseThrow().fuel().totalAmount()
                        : 0) && observation.invariantDigest().equals(isRocket
                        ? EscrowInvariant.rocket(rocket) : EscrowInvariant.reference(op.device())),
                        "O1 lost a native projection for " + observation.endpoint().type());
            }
            step = 3;
        }

        private void confirm(net.minecraft.server.MinecraftServer server) {
            if (queued || abandon() && server.getTickCount() - preview.previewTick() < 200) { return; }
            queued = EscrowRuntime.of(server).settlement().confirm(server, op.id(), preview.choice(), actor, preview.digest(), committed -> {
                check(committed, "O1 was not acknowledged");
                frozen = EscrowSettlementIntent.decode(world.member(device).settlement());
                EscrowRuntime.of(server).terminalCutForTests(server, boundary -> {
                    if (boundary.startsWith("O2_AFTER_") || List.of("O3_CAPTURE", "O3_AFTER", "O3J_AFTER", "O4_CAPTURE", "O4_AFTER").contains(boundary)) {
                        order.add(boundary);
                    }
                    if (mode == Mode.UNLOAD_NEUTRAL) { observeNeutralUnload(boundary); }
                });
                cutInstalled = true;
                if (mode == Mode.UNLOAD_NEUTRAL) {
                    frozen = EscrowSettlementIntent.decode(world.member(device).settlement());
                    check(frozen.observations().equals(preview.observations()) && frozen.observations().stream()
                            .filter(value -> value.endpoint().key().equals(op.source().key())).allMatch(value -> !value.bound()),
                            "O1 did not freeze the neutral unload source");
                }
                if (mode == Mode.LOAD || mode == Mode.UNLOAD_NEUTRAL) {
                    // The veto: the chosen neutral survivor no longer stands at its frozen placement.
                    rocket.setPos(rocket.getX() + 0.25D, rocket.getY(), rocket.getZ()); since = server.getTickCount(); step = 4;
                } else { step = 5; }
            });
        }

        /** The moved neutral survivor holds normalization and every proof; restoring it releases the hold. */
        private void veto(net.minecraft.server.MinecraftServer server) {
            var data = world.data(); var runtime = EscrowRuntime.of(server);
            var record = data.ledger().records().stream().filter(value -> value.id().equals(op.id())).findFirst().orElseThrow();
            var intent = EscrowSettlementIntent.decode(record.settlement());
            var view = runtime.settlementRecovery().view(intent);
            check(rocket.escrowSection().equals(RocketEscrowSection.neutral()) && !EscrowSettlementTombstones.installed(data.ledger(), intent)
                    && record.phase() == EscrowOperation.Phase.SETTLING_ACCEPT && view.provedSlots() == 0,
                    "The vetoed survivor was normalized, proved or released");
            if (mode == Mode.UNLOAD_NEUTRAL) {
                check(proofs.isEmpty() && !normalized && !jointlyProved && data.ledger().tombstones().isEmpty()
                        && data.ledger().rocketAuthorities().isEmpty(), "The unload veto allowed settlement progress");
                unchangedNeutralUnload();
            }
            if (!view.reason().startsWith("Rocket placement")) {
                check(server.getTickCount() - since < 400, "The placement veto did not hold the settlement: " + view.reason()); return;
            }
            vetoes++;
            var origin = rocket.flightData().orElseThrow().currentOrigin();
            rocket.setPos(origin.x() + 0.5D, origin.y(), origin.z() + 0.5D); step = 5;
        }

        private void unchangedNeutralUnload() {
            check(rocket.snapshot().orElseThrow().contentHash().equals(heldHash)
                    && ItemCargoReconciliationFixtures.amount(rocket) == 10 && ItemCargoReconciliationFixtures.amount(device.root().orElseThrow().buffer()) == 60
                    && EscrowCargoGameTestWorld.section(device).equals(RocketEscrowSection.neutral()),
                    "Neutral unload normalization or veto changed cargo or the device");
        }

        /** Observe the real normalization and both native O2 callbacks, without manufacturing proofs. */
        private void observeNeutralUnload(String boundary) {
            if (boundary.equals("O_BIND_AFTER")) {
                check(vetoes == 1 && rocket.escrowSection().equals(new RocketEscrowSection(frozen.ledger(), 0, List.of())),
                        "Unload source did not bind to its exact prescribed wrapper after the veto");
                unchangedNeutralUnload(); normalized = true;
            }
            if (boundary.startsWith("O2_AFTER_")) { check(normalized, "Unload O2 preceded normalization"); proofs.add(boundary); }
            if (boundary.equals("O3_CAPTURE")) {
                var view = EscrowRuntime.of(helper.getLevel().getServer()).settlementRecovery().view(frozen);
                check(proofs.equals(java.util.Set.of("O2_AFTER_0", "O2_AFTER_1")) && view.provedSlots() == 2
                        && view.waitingSlots() == 0, "Unload did not jointly prove its rocket source and device destination");
                jointlyProved = true;
            }
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
                    && receipt.getList("survivors", 10).size() == (abandon() ? 1 : 2) && receipt.getList("missing", 10).size() == (abandon() ? 1 : 0)
                    && receipt.equals(EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt))), "Receipt changed: " + receipt);
            for (var raw : receipt.getList("survivors", 10)) {
                var survivor = (CompoundTag) raw; var reference = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                boolean isRocket = reference.key().equals(op.rocket().key());
                check(survivor.getString("signature_kind").equals((isRocket ? EscrowSignatureKind.FUEL_VECTOR_V1 : EscrowSignatureKind.ITEM_BUFFER_V1).name())
                        && survivor.getBoolean("bound") == (isRocket || mode == Mode.LOAD || mode == Mode.UNLOAD) && survivor.getLong("quantity")
                        == (isRocket ? rocket.flightData().orElseThrow().fuel().totalAmount() : 0),
                        "Receipt survivor projection changed: " + survivor);
            }
            check(lines.size() == 2 && lines.get(0).startsWith("group=" + preview.digest() + " members=1 omitted=0 pruning=DONE journal=")
                    && lines.get(0).endsWith(" accounting=UNKNOWN") && lines.get(1).equals("operation=" + op.id()
                    + " persisted=RETIRED runtime=RELEASED reason=NONE O2=not-retained pruning=DONE next_retry=none receipt=RELEASED head="
                    + (head ? "READY" : "NONE") + " accounting=UNKNOWN"), "Inspector output changed: " + lines);
            var logical = op.rocket().logicalRocket();
            for (var boundary : List.of("O2_AFTER_0", "O2_AFTER_1")) {
                check(order.contains(boundary) && order.indexOf(boundary) < order.indexOf("O3_CAPTURE"), "O3 preceded a survivor/absence proof: " + order);
            }
            check(order.indexOf("O3_CAPTURE") < order.indexOf("O3_AFTER") && order.indexOf("O3_AFTER") < order.indexOf("O3J_AFTER")
                    && order.indexOf("O3J_AFTER") < order.indexOf("O4_CAPTURE") && order.indexOf("O4_CAPTURE") < order.indexOf("O4_AFTER"),
                    "Settlement publication order changed: " + order);
            var ready = root.rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
            if (mode == Mode.ABANDON_DEVICE) {
                check(ready != null && ready.outcome() == EscrowFlightTables.Outcome.READY && ready.epoch() == 0
                        && ready.invariant().equals(EscrowInvariant.rocket(rocket)) && ItemCargoReconciliationFixtures.amount(rocket) == 10
                        && rocket.escrowSection().equals(new RocketEscrowSection(root.id(), 0, List.of()))
                        && world.level.getBlockEntity(device.getBlockPos()) == null && RocketEscrowGate.idle(rocket),
                        "ABANDON changed or lost its exact rocket survivor");
                finish(); return;
            }
            if (mode == Mode.UNLOAD_NEUTRAL) {
                var chosen = frozen.checkpointed().stream().filter(value -> value.endpoint().key().equals(op.source().key())).findFirst().orElseThrow();
                check(normalized && jointlyProved && ready != null && ready.logical().equals(logical)
                        && ready.physical().equals(chosen.endpoint().identity()) && ready.owner().equals(op.owner())
                        && ready.epoch() == chosen.state().epoch() && ready.kind() == chosen.signatureKind()
                        && ready.signature().equals(chosen.state().signature()) && ready.invariant().equals(chosen.invariantDigest())
                        && ready.anchor().equals(op.id()) && ready.releaseGeneration() == receipt.getLong("release_generation")
                        && ready.outcome() == EscrowFlightTables.Outcome.READY
                        && rocket.escrowSection().equals(new RocketEscrowSection(root.id(), 0, List.of())),
                        "O4 did not preserve the frozen normalized unload source");
                unchangedNeutralUnload();
                check(RocketEscrowGate.idle(rocket) && !device.escrowLocked(), "Released neutral unload stayed locked");
                finish(); return;
            }
            if (!head) {
                check(ready == null && EscrowSettlementTombstones.logicalLocked(root, logical) && rocket.isRemoved()
                        && ItemCargoReconciliationFixtures.amount(device.root().orElseThrow().buffer()) == 60 && EscrowCargoGameTestWorld.section(device).equals(RocketEscrowSection.neutral())
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
                    && ItemCargoReconciliationFixtures.amount(rocket) == (load ? 60 : 6) && ItemCargoReconciliationFixtures.amount(device.root().orElseThrow().buffer()) == (load ? 6 : 64)
                    && (!load || vetoes == 1), "Pruning or normalization changed resources, epochs or identities");
            check(RocketEscrowGate.idle(rocket) && !device.escrowLocked(), "The released survivors stayed locked");
            world.enable(device, true);
            check(device.attempt() == RocketCargoStatus.RECEIPT_BACKED && world.member(device) == null, "A receipt-backed rocket admitted new cargo");
            world.enable(device, false);
            if (load) { finish(); return; }
            EscrowRuntime.of(server).terminalCutForTests(server, null); cutInstalled = false;
            launch = new EscrowCargoLaunch(helper, world, rocket, origin.offset(32, 0, 0), true, ItemCargoReconciliationFixtures::amount);
            step = 6;
        }

        private boolean abandon() { return mode == Mode.ABANDON || mode == Mode.ABANDON_DEVICE; }
        private void finish() { close(); done = true; helper.succeed(); }
        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        @Override public void close() {
            if (cutInstalled) { EscrowRuntime.of(helper.getLevel().getServer()).terminalCutForTests(helper.getLevel().getServer(), null); cutInstalled = false; }
            if (launch != null) { launch.close(); }
            world.close();
        }
    }
}
