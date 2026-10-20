package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native C-P capture fixtures; neither sequence issuance nor production admission is enabled. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowCargoNativeCaptureGameTests {
    @GameTest(template = "rocket_test", batch = "cargo_native_capture", timeoutTicks = 200)
    public static void nativeFactoryCapturesExactEnvelopesAndRejectsChangedFinalInputs(GameTestHelper helper) {
        var scenario = new Scenario(helper);
        helper.onEachTick(scenario::tick);
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final EscrowLedger root = new EscrowLedger(UUID.randomUUID(), 0, 0, List.of(), List.of(), List.of());
        private RocketEntity rocket;
        private RocketCargoPreparations.Ticket ticket;
        private EscrowCargoPreparedCapture capture;
        private EscrowOperation operation;
        private CompoundTag deviceBefore, deviceAfter;
        private int phase, attempt;
        private boolean deviceCurrent = true;
        Scenario(GameTestHelper helper) { this.helper = helper; }

        void tick() {
            if (phase == 2) { return; }
            try {
                var server = helper.getLevel().getServer();
                if (rocket == null) {
                    var origin = helper.absolutePos(new BlockPos(2, 2, 2));
                    if (!helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())) { return; }
                    var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server);
                    if (!journal.retryChecked(server)) { return; }
                    rocket = EscrowCargoPreparationGameTests.create(helper, origin, false);
                    operation = EscrowCargoPreparationGameTests.capture(rocket,
                            EscrowCargoPreparationGameTests.delta(rocket.snapshot().orElseThrow()), root);
                    deviceBefore = device(operation, root, false); deviceAfter = device(operation, root, true);
                }
                var preparations = RocketRuntime.cargoPreparations(server);
                if (ticket == null) {
                    ticket = preparations.begin(rocket, EscrowCargoPayload.decode(operation.payload()), true).orElse(null);
                    if (ticket == null) { return; }
                }
                if (phase == 0) {
                if (!ticket.advance()) { return; }
                if (capture == null) {
                    capture = EscrowCargoPreparedCapture.nativeCapture(ticket, root.id(), operation,
                            () -> deviceBefore.copy(), () -> deviceAfter.copy(), () -> deviceCurrent, 64_000);
                }
                if (!capture.advance(server.getTickCount(), preparations.budget(), root, List.of())) { return; }
                helper.assertTrue(capture.result().status() == Status.ELIGIBLE, "Native capture refused valid fixture");
                var expected = EscrowCargoEligibility.check(root, List.of(), operation, EscrowNativeEnvelope.rocket(rocket),
                        EscrowNativeEnvelope.rocket(rocket, ticket.cursor().after(), rocket.flightData().orElseThrow(),
                                new RocketEscrowSection(root.id(), 1, List.of(new EscrowPrefix.Link(operation, operation.rocket(), "DESTINATION").marker()))),
                        deviceBefore, deviceAfter, 64_000);
                helper.assertTrue(capture.result().equals(expected), "Native factory differs from synchronous capture");
                // Final admission needs a fresh whole-tick reservation. Mutate only after capture completed.
                phase = 1; return;
                }
                float yaw = rocket.getYRot();
                if (attempt == 0) { rocket.setYRot(yaw + 1); } // Frame changes do not invalidate Ticket.valid().
                else { deviceCurrent = false; }
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
                try (var writer = new EscrowLedgerWriter(server.getWorldPath(LevelResource.ROOT).resolve("cargo-native-capture"), 0);
                     var coordinator = new EscrowCoordinator(data, writer)) {
                    var result = coordinator.prepareCargo(capture, server.getTickCount(), preparations.budget(), () -> true,
                            ignored -> { throw new AssertionError("Stale capture issued a completion"); });
                    helper.assertTrue(result == EscrowCoordinator.Transfer.STALE, "Native capture accepted changed final inputs");
                    helper.assertTrue(coordinator.nextSequence() == 1 && coordinator.admissionAuthority().pendingCount() == 0
                            && !data.isDirty(), "Stale native capture changed admission state");
                } finally { rocket.setYRot(yaw); }
                helper.assertTrue(!RocketRuntime.cargoPreparing(rocket), "Stale native capture retained ticket");
                if (attempt == 1) { rocket.discard(); phase = 2; helper.succeed(); }
                else { ticket = null; capture = null; attempt++; phase = 0; }
            } catch (Throwable failure) {
                if (ticket != null) { ticket.close(); }
                if (rocket != null && rocket.isAlive()) { rocket.discard(); }
                throw failure;
            }
        }
    }

    private static CompoundTag device(EscrowOperation operation, EscrowLedger ledger, boolean after) {
        var endpoint = operation.device();
        var tag = new CompoundTag(); tag.putString("id", endpoint.type());
        tag.putInt("x", endpoint.x()); tag.putInt("y", endpoint.y()); tag.putInt("z", endpoint.z());
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("owner", operation.owner());
        root.putUUID("device", endpoint.identity()); root.putString("role", "LOAD");
        root.put("tank", (after ? LoaderTank.EMPTY : LoaderTank.of(new FluidStack(Fluids.WATER, 1))).encode());
        root.put("resource_escrow", (after ? new RocketEscrowSection(ledger.id(), 1,
                List.of(new EscrowPrefix.Link(operation, endpoint, "SOURCE").marker())) : RocketEscrowSection.neutral()).encode());
        tag.put(EscrowCargoEligibility.DEVICE_ROOT, root); return tag;
    }
}
