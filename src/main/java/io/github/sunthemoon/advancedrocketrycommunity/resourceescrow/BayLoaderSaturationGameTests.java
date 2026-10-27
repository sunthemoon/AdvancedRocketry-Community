package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W128R-R2, risk entry C's "loaders stop for every player": real bay INTENT receipts of rockets that never fly fill the
 * shared 64 pending slots through the real receipt store, and a real loader of an unrelated owner's idle rocket is then
 * refused at its fair-queue visit with ESCROW_FULL, both envelopes and the counts unchanged. Once the receipts are
 * exactly removed, the same loader is admitted and its fill completes. Nothing here uses a synthetic BAY index.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayLoaderSaturationGameTests {
    static final String BATCH = "bay_loader_saturation";
    private static final UUID FILL_OWNER = UUID.fromString("00000000-0000-4000-8000-000000128e01");
    private BayLoaderSaturationGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void bayIntentsFillingTheSharedPendingSlotsStopALoaderUntilTheyResolve(GameTestHelper helper) {
        new Saturation(helper).start();
    }

    private static final class Saturation {
        final GameTestHelper helper;
        final ServerLevel level;
        final Area area;
        final UUID owner = UUID.randomUUID();
        final List<BayReceipt> fills = new ArrayList<>();
        RocketEntity rocket;
        FluidFuelLoaderBlockEntity loader;
        int step, waits, baseline;
        boolean done;

        Saturation(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); area = new Area(level, 47_128, 47_000);
        }
        void start() { area.hold(); helper.onEachTick(this::tick); }

        void tick() {
            if (done) { return; }
            try {
                helper.assertTrue(++waits < 2_300, "The saturation fixture exceeded its bounded wait at step " + step);
                var server = level.getServer();
                var authority = BayReceiptTestHooks.authority(server);
                switch (step) {
                    case 0 -> {
                        if (!area.ticking() || !RocketTransferSavedData.get(server).retryChecked(server)) { area.hold(); return; }
                        baseline = authority.pendingCount();
                        helper.assertTrue(baseline < EscrowLimits.OPERATIONS, "Fixture: the shared pending slots are already full");
                        rocket = FluidFuelLoaderEscrowFixtures.rocket(level, area.at(8, 100, 8), owner, mono(2_000));
                        helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
                        loader = FluidFuelLoaderEscrowFixtures.loader(level, area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                        fill(server, EscrowLimits.OPERATIONS - baseline);
                        step = 1;
                    }
                    case 1 -> {
                        if (!idle(server)) { return; }
                        helper.assertTrue(authority.pendingCount() == EscrowLimits.OPERATIONS, "The bay receipts did not fill the shared slots: "
                                + authority.pendingCount());
                        var logical = rocket.assemblyTransactionId().orElseThrow();
                        var envelope = loader.saveWithFullMetadata(); var native_ = rocket.saveWithoutId(new CompoundTag());
                        var before = counts(server, logical);
                        gate(server, true);
                        loader.admitFromQueue();
                        gate(server, false);
                        helper.assertTrue(loader.lastAttempt() == FluidFuelLoaderStatus.ESCROW_FULL, "Saturated loader: " + loader.lastAttempt());
                        helper.assertTrue(envelope.equals(loader.saveWithFullMetadata()) && native_.equals(rocket.saveWithoutId(new CompoundTag()))
                                && before.equals(counts(server, logical)) && !loader.escrowLocked(),
                                "The refused loader changed an envelope, the queue or a count");
                        for (var receipt : fills) {
                            helper.assertTrue(BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }).queued(), "Fill removal refused");
                        }
                        step = 2;
                    }
                    case 2 -> {
                        if (!idle(server)) { return; }
                        helper.assertTrue(authority.pendingCount() == baseline, "The exact removals did not free the shared slots");
                        fills.clear();
                        gate(server, true);
                        boolean captured = loader.admitFromQueue();
                        gate(server, false);
                        helper.assertTrue(captured && loader.lastAttempt() == FluidFuelLoaderStatus.PREPARING,
                                "The loader was not admitted once the slots were free: " + loader.lastAttempt());
                        step = 3;
                    }
                    case 3 -> {
                        var data = ResourceEscrowSavedData.existing(server);
                        boolean settled = data != null && data.operational() && data.bind(server).unresolvedNaming(rocket.getUUID()).isEmpty()
                                && rocket.escrowSection().markers().isEmpty() && section(loader).markers().isEmpty();
                        if (bank(rocket, false) != 1_000 || amount(loader) != 0 || !settled) { return; }
                        cleanup(server);
                        helper.succeed();
                    }
                    default -> { }
                }
            } catch (RuntimeException | Error failure) { cleanup(level.getServer()); throw failure; }
        }

        /** Inert INTENT receipts, eight per invented rocket, through the real store and its shared admission. */
        void fill(MinecraftServer server, int count) {
            String hash = RocketNativeResources.hash("w128r-saturation");
            long tick = server.getTickCount();
            UUID invented = UUID.randomUUID();
            for (int n = 0; n < count; n++) {
                if (n % 8 == 0) { invented = UUID.randomUUID(); }
                var receipt = new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(server), invented,
                        new RocketPosition(0, n % 8, 0), UUID.randomUUID(), FILL_OWNER, ModIdentity.id("data_satellite").toString(),
                        SatelliteKind.DATA, ModIdentity.id("earth").toString(), hash, UUID.randomUUID(), hash, null, hash, null,
                        BayReceipt.Phase.INTENT, "NONE", false, false, 0, tick, tick, 0);
                helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Fill " + n + " was refused");
                fills.add(receipt);
            }
        }

        boolean idle(MinecraftServer server) {
            return !BayReceiptTestHooks.store(server).map(BayReceiptStore::holdsWork).orElse(false);
        }

        void gate(MinecraftServer server, boolean open) {
            EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), open);
        }

        void cleanup(MinecraftServer server) {
            if (done) { return; }
            done = true;
            if (loader != null) { gate(server, false); clear(level, loader.getBlockPos()); }
            if (rocket != null) { rocket.discard(); }
            for (var receipt : fills) { BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }); }
            area.release();
        }
    }
}
