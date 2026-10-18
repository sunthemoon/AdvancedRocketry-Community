package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.RefillPacer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W95, leaf B3-E: a world's first admitted fill creates the ledger (W11B3-DESIGN section 2). The test runs alone in its
 * batch: it detaches the shared GameTest ledger through the runtime's retarget method and sets its file aside, so the
 * world is NO_FILE. Before any fill is admitted, a closed switch (W95-R4) and then a full fair queue (W95-R5) create
 * nothing and show their refusal. A gated real loader's fair-queue visit then creates a fresh production ledger, whose
 * empty generation-1 baseline reaches the disk before L1. Production reconciliation completes the fill, and the
 * original ledger and its file are restored exactly, by the after-batch cleanup if the test fails or times out (W95-R2).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderLedgerCreationGameTests {
    static final String BATCH = "fluid_fuel_loader_creation";
    /** The batch's one scenario, for the after-batch cleanup. */
    private static Creation running;
    private FluidFuelLoaderLedgerCreationGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1_200)
    public static void aWorldsFirstFillCreatesTheLedgerAfterAnEmptyBaseline(GameTestHelper helper) {
        new Creation(helper).start();
    }

    /**
     * W95-R2: runs after a failure or a timeout too. Unless the test finished, it closes the device gate, stops the
     * fillers, clears the fixtures and restores the original ledger and its file. The retarget refuses while the created
     * ledger holds queued, captured or UNKNOWN work; then the shared ledger stays detached, and one line naming the batch
     * says so, so that any later failure is attributable.
     */
    @AfterBatch(batch = BATCH)
    public static void afterBatch(ServerLevel level) {
        var creation = running; running = null;
        if (creation == null || creation.finished) { return; }
        creation.finished = true;
        try {
            boolean restored = creation.undo(level.getServer());
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_W95_AFTER_BATCH batch={} restored={} backup={}; {}", BATCH, restored, creation.backup,
                    restored ? "the unfinished test's fixtures were removed and the shared ledger restored"
                            : "the created ledger still holds work, so the shared ledger stays detached and its file stays set aside");
        } catch (RuntimeException failure) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_W95_AFTER_BATCH batch={} restored=false backup={}; the cleanup failed", BATCH,
                    creation.backup, failure);
        }
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
        /** The fixture holds the server's ledger slot, and the ledger file holds no original bytes; undo restores both. */
        boolean detached, fileAside;
        /** W95-R5's inert entries renew themselves while this is set and the phase's deadline has not passed. */
        boolean fillers;
        long fullUntil;
        boolean baselineSeen, finished;
        int step, closedTicks;
        Creation(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            area = new Area(level, 47_040, 47_000);
        }
        void start() { running = this; area.hold(); helper.onEachTick(this::tick); }
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
                    detached = true;
                    file = ResourceEscrowSavedData.file(server);
                    backup = file.resolveSibling(file.getFileName() + ".w95-backup");
                    try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } }
                    catch (java.io.IOException failure) { throw new IllegalStateException("Fixture could not set the ledger file aside", failure); }
                    fileAside = true;
                    helper.assertTrue(ResourceEscrowSavedData.existing(server) == null && !Files.exists(file)
                            && delegate.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE, "The world is not NO_FILE");
                    rocket = FluidFuelLoaderEscrowFixtures.rocket(level, area.at(8, 100, 8), owner, mono(2_000));
                    helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
                    loader = FluidFuelLoaderEscrowFixtures.loader(level, area.at(6, 100, 8), owner, tank(ROCKET_FUEL, 1_000), binding(rocket));
                    step = 1;
                }
                case 1 -> {
                    // W95-R4: the switch stays closed (no device hook, no property), so the loader path creates nothing.
                    nothingCreated(server, delegate);
                    helper.assertTrue(!EscrowLoaderAdmission.enabled(server, device(loader)), "The switch opened without the device's hook");
                    if (++closedTicks <= 2 * RefillPacer.INTERVAL_TICKS || loader.lastAttempt() != FluidFuelLoaderStatus.ADMISSION_DISABLED) { return; }
                    // Both entry points once more, synchronously: the paced attempt and the fair-queue visit.
                    var envelope = loader.saveWithFullMetadata(); var native_ = rocket.saveWithoutId(new CompoundTag());
                    helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED && !loader.admitFromQueue()
                            && loader.lastAttempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED
                            && loader.displayStatus() == FluidFuelLoaderStatus.ADMISSION_DISABLED, "A closed switch did not show ADMISSION_DISABLED");
                    helper.assertTrue(envelope.equals(loader.saveWithFullMetadata()) && native_.equals(rocket.saveWithoutId(new CompoundTag())),
                            "A closed switch changed the loader's or the rocket's envelope");
                    nothingCreated(server, delegate);
                    step = 2;
                }
                case 2 -> {
                    // W95-R5: inert entries fill the fair queue before the device's switch opens.
                    var fills = runtime.fills(server);
                    helper.assertTrue(fills.size() == 0, "Another device waits in the fair queue");
                    fillers = true; fullUntil = level.getGameTime() + 3L * RefillPacer.INTERVAL_TICKS;
                    for (int i = 0; i < EscrowFillQueue.CAPACITY; i++) {
                        var id = new UUID(0x5795FL, i);
                        helper.assertTrue(fills.offer(id, filler(fills, id)), "The fair queue refused filler " + i);
                    }
                    runtime.loaderAdmissionForTests(server, device(loader), true);
                    helper.assertTrue(fills.size() == EscrowFillQueue.CAPACITY, "The fair queue is not full");
                    step = 3;
                }
                case 3 -> {
                    nothingCreated(server, delegate);
                    var fills = runtime.fills(server); var last = loader.lastAttempt();
                    helper.assertTrue(last.refusal(), "A device met the full fair queue with " + last);
                    if (last != FluidFuelLoaderStatus.ESCROW_FULL) {
                        helper.assertTrue(level.getGameTime() < fullUntil, "No paced attempt met the full fair queue: " + last);
                        return;
                    }
                    helper.assertTrue(loader.displayStatus() == FluidFuelLoaderStatus.ESCROW_FULL && fills.size() == EscrowFillQueue.CAPACITY,
                            "A full fair queue showed " + loader.displayStatus());
                    // The fillers leave at the queue's own pace; the device then joins and is admitted.
                    fillers = false; step = 4;
                }
                case 4 -> {
                    var data = ResourceEscrowSavedData.existing(server);
                    if (data == null) {
                        // Ordinary queries, paced attempts and a waiting device create nothing.
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
                    operation = named.get(0).id(); step = 5;
                }
                case 5 -> {
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
                    step = 6;
                }
                case 6 -> {
                    long held = held(server, rocket, List.of(loader));
                    helper.assertTrue(amount(loader) + bank(rocket, false) + held == 1_000, "Conservation failed");
                    var coordinator = created.bind(server);
                    if (amount(loader) != 0 || bank(rocket, false) != 1_000 || !coordinator.unresolvedNaming(rocket.getUUID()).isEmpty()
                            || !rocket.escrowSection().markers().isEmpty() || !section(loader).markers().isEmpty()
                            || coordinator.admissionAuthority().pendingFor(rocket.assemblyTransactionId().orElseThrow()) != 0) { return; }
                    helper.assertTrue(created.ledger().id().equals(rocket.escrowSection().ledger())
                            && created.ledger().id().equals(section(loader).ledger()), "The endpoints are not bound to the created ledger");
                    step = 7;
                }
                default -> restore(server, delegate);
            }
        }
        /** Before the first admitted fill: no ledger, no file, NO_FILE, both resource stores untouched, the device not queued. */
        void nothingCreated(MinecraftServer server, EscrowAdmissionAuthority.Delegate delegate) {
            helper.assertTrue(ResourceEscrowSavedData.existing(server) == null && !Files.exists(file)
                    && delegate.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE, "A ledger exists before the first admitted fill");
            helper.assertTrue(amount(loader) == 1_000 && bank(rocket, false) == 0 && section(loader).equals(LoaderEscrowSection.neutral())
                    && rocket.escrowSection().markers().isEmpty(), "A resource moved before the first admitted fill");
            helper.assertTrue(!EscrowRuntime.of(server).fills(server).waiting(device(loader)), "The device was queued");
        }
        /** An inert entry: it captures nothing and, while the full-queue phase lasts, joins the back of the queue again. */
        EscrowFillQueue.Attempt filler(EscrowFillQueue fills, UUID id) {
            return new EscrowFillQueue.Attempt() {
                @Override public boolean visit() {
                    if (fillers && level.getGameTime() < fullUntil) { fills.offer(id, this); }
                    return false;
                }
            };
        }
        /** The disk's ledger, or null while the IO thread replaces it. */
        EscrowLedger readable() {
            try { return CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).map(EscrowCodec::decode).orElse(null); }
            catch (RuntimeException replacing) { return null; }
        }
        void restore(MinecraftServer server, EscrowAdmissionAuthority.Delegate delegate) {
            if (delegate.holdsWork() || !created.bind(server).writerIdle()) { return; }
            helper.assertTrue(undo(server), "The original ledger was not restored");
            helper.assertTrue(ResourceEscrowSavedData.existing(server) == original, "The shared ledger did not return");
            finished = true; helper.succeed();
        }
        /**
         * Undoes the fixture: the device gate and the fillers, the loader, the rocket and the ticket, then the original
         * ledger and its file. It returns false, leaving the ledger and both files as they are, while the created ledger
         * holds queued, captured or UNKNOWN work, because the retarget then refuses. Running it again changes nothing.
         */
        boolean undo(MinecraftServer server) {
            var runtime = EscrowRuntime.of(server);
            fillers = false;
            if (loader != null) {
                loader.root().ifPresent(root -> runtime.loaderAdmissionForTests(server, root.device(), false));
                clear(level, loader.getBlockPos());
            }
            if (rocket != null) { rocket.discard(); }
            area.release();
            if (!detached) { return true; }
            var current = ResourceEscrowSavedData.existing(server);
            // Taken while it is still the server's ledger: binding a detached root would attach a new coordinator.
            var replaced = current == null || current == original ? null : current.bind(server);
            if (!runtime.replaceLedgerForTests(server, original)) { return false; }
            if (replaced != null) { replaced.close(); }
            if (fileAside) {
                try {
                    Files.deleteIfExists(file);
                    if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); }
                } catch (java.io.IOException failure) { throw new IllegalStateException("Fixture could not restore the ledger file", failure); }
                fileAside = false;
            }
            detached = false;
            return true;
        }
    }
}
