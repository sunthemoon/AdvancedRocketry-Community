package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
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

/** R39 is checked before normalization/O2 and repeated readback, without holding another rocket's work. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemCargoSettlementNativeGameTests {
    private static Scenario running;
    @GameTest(template = "empty", batch = "item_cargo_settlement_r39", timeoutTicks = 1_600)
    public static void unavailableNativeItemHoldsOnlyItsSettlementBeforeO2(GameTestHelper helper) {
        running = new Scenario(helper); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = "item_cargo_settlement_r39")
    public static void cleanup(ServerLevel level) { if (running != null) { running.world.close(); running = null; } }

    private static final class Scenario {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final BlockPos at = new BlockPos(650_104, 100, 646_008);
        final EscrowCargoGameTestWorld world;
        final UUID actor = UUID.randomUUID();
        RocketEntity heldRocket, otherRocket;
        RocketItemCargoBlockEntity heldDevice, otherDevice;
        EscrowOperation held;
        EscrowSettlementIntent intent;
        CompoundTag rocketBefore, deviceBefore;
        int step, since;
        boolean queued, otherAdmitted, done;
        Scenario(GameTestHelper helper) {
            this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer());
            world = new EscrowCargoGameTestWorld(helper, List.of(at, at.offset(32, 0, 0)));
        }
        void tick() {
            if (done) { return; } pacer.pace();
            var server = world.server;
            if (step == 0) {
                if (!world.ready()) { return; } world.isolate();
                heldRocket = world.itemRocket(at, 60); otherRocket = world.itemRocket(at.offset(32, 0, 0), 60);
                heldDevice = world.itemDevice(at.offset(2, 0, 0), heldRocket, RocketCargoRole.LOAD, 10);
                otherDevice = world.itemDevice(at.offset(34, 0, 0), otherRocket, RocketCargoRole.LOAD, 10);
                rocketBefore = EscrowNativeEnvelope.rocket(heldRocket); deviceBefore = heldDevice.saveWithFullMetadata();
                world.pause(heldDevice, true); world.enable(heldDevice, true); step = 1;
            } else if (step == 1) {
                var op = world.member(heldDevice); if (op == null || !world.data().bind(server).writerIdle()) { return; }
                world.enable(heldDevice, false);
                var encoded = EscrowCodec.operation(op.withReason(EscrowReason.OTHER_STATE));
                encoded.getCompound("payload").getCompound("stack").putString("id", "w134:missing");
                encoded.putString("payload_hash", RocketNativeResources.signature(encoded.getCompound("payload")));
                held = EscrowCodec.operation(encoded);
                var root = world.data().ledger();
                world.data().acknowledged(root.replace(List.of(held), root.floors(), root.tombstones()), true);
                step = 2;
            } else if (step == 2) {
                if (queued) { return; }
                var operator = EscrowRuntime.of(server).settlement();
                var preview = operator.preview(server, held.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "W134Native");
                queued = operator.confirm(server, held.id(), preview.choice(), actor, preview.digest(), committed -> {
                    check(committed, "O1 failed"); intent = EscrowSettlementIntent.decode(world.member(heldDevice).settlement());
                    since = server.getTickCount(); world.enable(otherDevice, true); step = 3;
                });
            } else if (step == 3) {
                var other = world.member(otherDevice);
                if (other != null) { otherAdmitted = true; world.enable(otherDevice, false); }
                var op = world.member(heldDevice); var root = world.data().ledger();
                check(op != null && op.phase() == EscrowOperation.Phase.SETTLING_ACCEPT && op.payload().equals(held.payload())
                        && op.settlement().equals(intent.encode()) && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty()
                        && rocketBefore.equals(EscrowNativeEnvelope.rocket(heldRocket)) && deviceBefore.equals(heldDevice.saveWithFullMetadata()),
                        "Native refusal changed its frozen operation, endpoint or receipt");
                var view = EscrowRuntime.of(server).settlementRecovery().view(intent);
                check(view.provedSlots() == 0, "Unavailable native item acquired an O2 proof");
                if (!otherAdmitted || other != null || server.getTickCount() - since < 220 || !world.data().bind(server).writerIdle()) { return; }
                check(view.reason().equals("OTHER_STATE") && root.records().size() == 1 && world.data().bind(server).operational()
                        && ItemCargoReconciliationFixtures.amount(otherRocket) == 64
                        && ItemCargoReconciliationFixtures.amount(otherDevice.root().orElseThrow().buffer()) == 6
                        && otherRocket.escrowSection().markers().isEmpty() && EscrowCargoGameTestWorld.section(otherDevice).markers().isEmpty()
                        && RocketEscrowGate.idle(otherRocket), "R39 held the unrelated operation or lost its own hold");
                world.close(); done = true; helper.succeed();
            }
        }
        private void check(boolean value, String message) { helper.assertTrue(value, message); }
    }
}
