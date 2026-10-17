package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W95, leaf B3-E: a world's first admitted fill creates the ledger (W11B3-DESIGN section 2). The test runs alone in its
 * batch: it detaches the shared GameTest ledger through the runtime's retarget method and sets its file aside, so the
 * world is NO_FILE; a gated real loader's fair-queue visit then creates a fresh production ledger, whose empty
 * generation-1 baseline reaches the disk before L1. Production reconciliation completes the fill, and the original
 * ledger and its file are restored exactly.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderLedgerCreationGameTests {
    static final String BATCH = "fluid_fuel_loader_creation";
    private FluidFuelLoaderLedgerCreationGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1_200)
    public static void aWorldsFirstFillCreatesTheLedgerAfterAnEmptyBaseline(GameTestHelper helper) {
        new Creation(helper).start();
    }

    private static final class Creation {
        final GameTestHelper helper;
        final ServerLevel level;
        final GameTestTickPacer pacer;
        final Area area;
        final UUID owner = UUID.randomUUID();
        ResourceEscrowSavedData original, created;
        Path file, backup;
        RocketEntity rocket;
        FluidFuelLoaderBlockEntity loader;
        UUID operation;
        boolean baselineSeen, finished;
        int step;
        Creation(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            area = new Area(level, 47_040, 47_000);
        }
        void start() { area.hold(); helper.onEachTick(this::tick); }
        void tick() {
            if (finished) { return; }
            pacer.pace(); var server = level.getServer(); var runtime = EscrowRuntime.of(server); var delegate = runtime.propulsion(server);
            switch (step) {
                case 0 -> {
                    if (!area.ticking() || !RocketTransferSavedData.get(server).retryChecked(server)) { area.hold(); return; }
                    original = ResourceEscrowSavedData.existing(server);
                    if (original != null) {
                        if (delegate.holdsWork()) { return; }
                        helper.assertTrue(runtime.replaceLedgerForTests(server, null), "The idle shared ledger could not be detached");
                    }
                    file = ResourceEscrowSavedData.file(server);
                    backup = file.resolveSibling(file.getFileName() + ".w95-backup");
                    try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } }
                    catch (java.io.IOException failure) { throw new IllegalStateException("Fixture could not set the ledger file aside", failure); }
                    helper.assertTrue(ResourceEscrowSavedData.existing(server) == null && !Files.exists(file)
                            && delegate.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE, "The world is not NO_FILE");
                    rocket = FluidFuelLoaderEscrowFixtures.rocket(level, area.at(8, 100, 8), owner, mono(2_000));
                    helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
                    loader = FluidFuelLoaderEscrowFixtures.loader(level, area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                    runtime.loaderAdmissionForTests(server, device(loader), true);
                    step = 1;
                }
                case 1 -> {
                    var data = ResourceEscrowSavedData.existing(server);
                    if (data == null) {
                        // Ordinary queries, paced attempts and an unbound or waiting device create nothing.
                        helper.assertTrue(!Files.exists(file) && delegate.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE
                                && amount(loader) == 1_000, "Something but the first admitted fill created a ledger");
                        return;
                    }
                    created = data; var coordinator = created.bind(server);
                    var named = coordinator.unresolvedNaming(device(loader));
                    helper.assertTrue(created != original && delegate.state() == EscrowAdmissionAuthority.Delegate.State.LOADED
                            && named.size() == 1 && !coordinator.durable(named.get(0).id())
                            && coordinator.admissionAuthority().pendingFor(rocket.assemblyTransactionId().orElseThrow()) == 1,
                            "Creation did not attach the ledger and queue exactly the reserved fill");
                    helper.assertTrue(named.get(0).sequence() == 1 && created.ledger().records().isEmpty() && amount(loader) == 1_000
                            && loader.displayStatus() == FluidFuelLoaderStatus.PREPARING, "The first fill moved or showed progress early");
                    operation = named.get(0).id(); step = 2;
                }
                case 2 -> {
                    var coordinator = created.bind(server);
                    var disk = readable();
                    if (disk != null && disk.writerGeneration() == 1) {
                        helper.assertTrue(disk.id().equals(created.ledger().id()) && disk.records().isEmpty() && disk.issuedThrough() == 0,
                                "The creation baseline was not an empty root");
                        baselineSeen = true;
                    }
                    if (!coordinator.durable(operation)) {
                        helper.assertTrue(amount(loader) == 1_000 && bank(rocket, false) == 0, "Fuel moved before L1");
                        return;
                    }
                    helper.assertTrue(baselineSeen && !coordinator.creationBaselinePending(), "L1 was not preceded by the empty baseline");
                    step = 3;
                }
                case 3 -> {
                    long held = held(server, rocket, List.of(loader));
                    helper.assertTrue(amount(loader) + bank(rocket, false) + held == 1_000, "Conservation failed");
                    var coordinator = created.bind(server);
                    if (amount(loader) != 0 || bank(rocket, false) != 1_000 || !coordinator.unresolvedNaming(rocket.getUUID()).isEmpty()
                            || !rocket.escrowSection().markers().isEmpty() || !section(loader).markers().isEmpty()
                            || coordinator.admissionAuthority().pendingFor(rocket.assemblyTransactionId().orElseThrow()) != 0) { return; }
                    helper.assertTrue(created.ledger().id().equals(rocket.escrowSection().ledger())
                            && created.ledger().id().equals(section(loader).ledger()), "The endpoints are not bound to the created ledger");
                    step = 4;
                }
                default -> restore(server, runtime, delegate);
            }
        }
        /** The disk's ledger, or null while the IO thread replaces it. */
        EscrowLedger readable() {
            try { return CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).map(EscrowCodec::decode).orElse(null); }
            catch (RuntimeException replacing) { return null; }
        }
        void restore(net.minecraft.server.MinecraftServer server, EscrowRuntime runtime, EscrowAdmissionAuthority.Delegate delegate) {
            var coordinator = created.bind(server);
            if (delegate.holdsWork() || !coordinator.writerIdle()) { return; }
            runtime.loaderAdmissionForTests(server, device(loader), false);
            clear(level, loader.getBlockPos()); rocket.discard();
            helper.assertTrue(runtime.replaceLedgerForTests(server, original), "The original ledger was not restored");
            coordinator.close();
            try {
                Files.deleteIfExists(file);
                if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); }
            } catch (java.io.IOException failure) { throw new IllegalStateException("Fixture could not restore the ledger file", failure); }
            helper.assertTrue(ResourceEscrowSavedData.existing(server) == original, "The shared ledger did not return");
            area.release(); finished = true; helper.succeed();
        }
    }
}
