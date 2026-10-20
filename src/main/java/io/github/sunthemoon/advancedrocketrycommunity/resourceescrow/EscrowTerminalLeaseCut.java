package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Gated A1 owner-thread mutations while the real F7 writer is paused before its lease check. */
final class EscrowTerminalLeaseCut implements AutoCloseable {
    enum Fault { DISMOUNT, REMOVAL, LOGOUT, MOTION, NONE }
    private final MinecraftServer server;
    private final ResourceEscrowSavedData data;
    private final RocketTransferRecord record;
    private final EscrowFlightTables.Outcome outcome;
    private final Path file;
    private final CountDownLatch reached = new CountDownLatch(1), resume = new CountDownLatch(1);
    private final AtomicBoolean intercepted = new AtomicBoolean();
    private final java.util.function.Consumer<String> checkpoint;
    private final EscrowCoordinator coordinator;

    EscrowTerminalLeaseCut(MinecraftServer server, ResourceEscrowSavedData data, RocketTransferRecord record) {
        this(server, data, record, EscrowFlightTables.Outcome.HANDED_OFF);
    }

    EscrowTerminalLeaseCut(MinecraftServer server, ResourceEscrowSavedData data, RocketTransferRecord record,
                           EscrowFlightTables.Outcome outcome) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.data = data; this.record = record; this.outcome = outcome;
        EscrowTags.require(outcome != EscrowFlightTables.Outcome.ARMED, "Terminal cut requires a terminal outcome");
        coordinator = data.bind(server);
        EscrowTags.require(coordinator.writerIdle(), "Terminal cut requires an idle writer");
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        checkpoint = cut -> {
            if (!(cut.equals("K1") || cut.equals("K8")) || intercepted.get()) { return; }
            try {
                var staged = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file.resolveSibling(
                        ManagedSavedDataType.RESOURCE_ESCROW.fileName() + ".arce-pending"), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
                if (staged.armedFlights().stream().noneMatch(arm -> arm.transfer().equals(record.transferId())
                        && arm.outcome() == outcome)) { return; }
                intercepted.set(true); reached.countDown();
                if (!resume.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Terminal cut owner did not resume the writer"); }
            } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
        };
        EscrowRuntime.of(server).terminalCutForTests(server, checkpoint);
    }

    boolean ready() { return reached.getCount() == 0; }

    void exercise(GameTestHelper helper, Fault fault, RocketEntity survivor, ServerPlayer passenger) {
        helper.assertTrue(ready(), "F7 writer never reached the pre-replacement cut");
        helper.assertTrue(data.ledger().armedFlights().contains(record.nativeTransfer().orElseThrow().armed())
                && !RocketEscrowGate.resourcesIdle(survivor), "Queued F7 released its successor");
        try {
            switch (fault) {
                case DISMOUNT -> passenger.stopRiding();
                case REMOVAL -> survivor.discard();
                case LOGOUT -> server.getPlayerList().remove(passenger);
                case MOTION -> survivor.setDeltaMovement(new Vec3(0.125, 0, 0));
                case NONE -> { }
            }
            if (fault == Fault.DISMOUNT || fault == Fault.REMOVAL || fault == Fault.LOGOUT) {
                EscrowTerminalRetryCut.assertInvalidated(helper, server, record);
            }
        } finally { resume.countDown(); }
        // Await this real IO completion without another entity tick/save observation. This is
        // R13's explicitly accepted setter interval, not a simulated passage of world ticks.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!coordinator.writerIdle() && System.nanoTime() < deadline) {
            coordinator.tick(server.getTickCount());
            try { Thread.sleep(2); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
        }
        helper.assertTrue(coordinator.writerIdle(), "F7 cut did not reach a definite checked outcome");
        var arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(record.transferId())).findFirst().orElseThrow();
        boolean handoff = fault == Fault.MOTION || fault == Fault.NONE;
        helper.assertTrue(arm.outcome() == (handoff ? outcome : EscrowFlightTables.Outcome.ARMED),
                "F7 cut published the wrong terminal outcome for " + fault);
        if (handoff) {
            helper.assertTrue(RocketEscrowGate.resourcesIdle(survivor) && !RocketEscrowGate.idle(survivor)
                    && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(survivor),
                    "Exact F7 callback did not release only successor resources");
            if (fault == Fault.NONE) { cargoInterlocks(helper, survivor); }
        }
        helper.assertTrue(data.ledger().equals(EscrowCodec.decode(CheckedSavedDataFile.readPayload(file,
                ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow())), "F7 memory and disk outcomes disagree");
    }

    private static void cargoInterlocks(GameTestHelper helper, RocketEntity rocket) {
        var preparations = io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.cargoPreparations(rocket.getServer());
        var delta = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(rocket.snapshot().orElseThrow().snapshotId(),
                0, 0, 0, io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.ID.toString()),
                new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1)
                        .writeToNBT(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(preparations.begin(rocket, delta, true).isEmpty(), "Receipt-backed successor admitted fresh cargo");
        // Force a reconstruction lock through the replay entry, without reconstructing or publishing this fixture delta.
        try (var ticket = preparations.beginReplay(rocket, delta, true).orElseThrow()) {
            helper.assertTrue(ticket.valid() && !RocketEscrowGate.resourcesIdle(rocket), "Terminal successor bypassed cargo lock");
            helper.assertTrue(!io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket),
                    "Terminal successor allowed refuel while preparing cargo");
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport
                    .create(helper).disassembleForReleaseTest(rocket, -1)
                    == io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.REGION_BUSY,
                    "Terminal successor allowed disassembly while preparing cargo");
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.requestAdminFlight(rocket,
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.MOON, java.util.UUID.randomUUID()).code()
                    == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                    "Terminal successor allowed launch while preparing cargo");
            var flight = rocket.flightData().orElseThrow();
            var fuel = flight.fuel().withFluidBank(false, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket_fuel"),
                    null, flight.fuel().totalAmount() + 1);
            helper.assertTrue(!fuel.equals(flight.fuel()), "Terminal refuel fixture needs room for one unit");
            boolean refused = false;
            try { rocket.updateFlightData(flight.withFuel(fuel, helper.getLevel().getGameTime())); }
            catch (IllegalStateException expected) { refused = true; }
            helper.assertTrue(refused && rocket.flightData().orElseThrow() == flight, "Terminal refuel changed authoritative state");
        }
        helper.assertTrue(RocketEscrowGate.resourcesIdle(rocket), "Closing replay ticket retained terminal resource lock");
    }

    @Override public void close() {
        resume.countDown(); EscrowRuntime.of(server).terminalCutForTests(server, null);
    }
}
