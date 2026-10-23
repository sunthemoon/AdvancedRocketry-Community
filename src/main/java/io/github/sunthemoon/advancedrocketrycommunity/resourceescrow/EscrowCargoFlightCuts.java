package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * C-S5 creation and flight cuts (FF_ cases) over a real fluid cargo load of 1,000 mB from a 5,000 mB loader into an
 * empty tank: cut-N1 with the production first-ledger creation; D2-F1, D2-F6 and D2-F8 of an uncrewed governed flight
 * after the load; two ordinary cargo-mutated flights with the successor's own load between them; exact-source
 * cancellation; a governed record over the real cargo result with missing and with terminal ARMED authority.
 */
final class EscrowCargoFlightCuts extends EscrowCargoCutProbe {
    static final Map<String, String> CASES = Map.of(
            "FF_N1", "BASELINE_REPLACED",
            "FF_F1_AFTER_CARGO", "ARMED_REPLACED",
            "FF_F6_AFTER_CARGO", "F6_JOINT_PROVED",
            "FF_F8_AFTER_CARGO", "TERMINAL_PRUNE_REPLACED",
            "FF_TWO_FLIGHTS", "SUCCESSOR_CHECKPOINT",
            "FF_CANCEL_AFTER_CARGO", "CANCELLED_REPLACED",
            "FF_GOVERNED_MISSING", "GOVERNED_HELD",
            "FF_GOVERNED_TERMINAL", "GOVERNED_DURABLE");
    /** The successor's second loader stands four blocks from the pad, inside the ticketed source chunk. */
    static final BlockPos SECOND = PAD.offset(-4, 0, 0);
    private final String boundary;
    private final Path ledgerFile;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketFluidCargoBlockEntity device, second;
    private RocketStructureSnapshot before;
    private EscrowCargoCutLaunch flight;
    private UUID transfer;
    private long began, since;
    private int step;
    private boolean seen, cancelled, attempted;
    private volatile boolean armWriting, landed, terminalAcknowledged;
    private final GovernedBaseline governedBaseline = new GovernedBaseline();
    private RocketTransferRecord governedRecord;
    private EscrowLedger governedLedger;
    private CompoundTag governedJournal;

    EscrowCargoFlightCuts(MinecraftServer server) {
        super(server);
        boundary = CASES.get(name);
        EscrowTags.require(boundary != null, "Unknown W142 flight cut");
        ledgerFile = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }

    /** Ledger-writer boundaries only; individual barrier callbacks are not the joint F6 checkpoint. */
    static boolean pauses(String boundary, String label, boolean armWriting, boolean landed, boolean terminalAcknowledged) {
        return switch (boundary) {
            case "BASELINE_REPLACED" -> label.equals("BASELINE_REPLACED");
            case "ARMED_REPLACED" -> armWriting && (label.equals("K9") || label.equals("K2"));
            default -> false;
        };
    }

    static boolean jointF6Ready(String boundary, boolean landed, boolean terminalAcknowledged, BooleanSupplier jointProof) {
        return boundary.equals("F6_JOINT_PROVED") && landed && !terminalAcknowledged && jointProof.getAsBoolean();
    }

    /** After barrier.tick delivers every completed proof, before flight execution can queue F7. */
    @Override void beforeReconciliation() {
        if (recovery || done || stopped()) { return; }
        if (jointF6Ready(boundary, landed, terminalAcknowledged, () -> {
            var journal = RocketTransferSavedData.get(server);
            var records = journal.entries();
            return records.size() == 1 && EscrowRuntime.of(server).terminal().checkpoint(server, records.get(0));
        })) { pause(boundary); }
    }

    @Override void checkpoint(String label) {
        if (recovery || done || stopped()) { return; }
        if (label.equals("F1_ARM_CAPTURE")) { armWriting = true; }
        if (label.equals("F6_LANDED")) { landed = true; }
        if (label.equals("F7_ACK")) { terminalAcknowledged = true; }
        if (pauses(boundary, label, armWriting, landed, terminalAcknowledged)) { pause(boundary); return; }
        if (label.equals("K9") && (boundary.equals("TERMINAL_PRUNE_REPLACED") || boundary.equals("CANCELLED_REPLACED"))) {
            // As EscrowTerminalProbe: the replaced file itself names the terminal outcome or its pruning.
            var root = EscrowCodec.decode(CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            if (root.issuedLaunchSerial() != 1) { return; }
            boolean pruned = terminalAcknowledged && root.armedFlights().isEmpty();
            boolean cancelledEntry = root.armedFlights().size() == 1 && root.armedFlights().get(0).outcome() == EscrowFlightTables.Outcome.CANCELLED;
            if (boundary.equals("TERMINAL_PRUNE_REPLACED") ? pruned : cancelledEntry) { pause(boundary); }
        }
    }

    @Override void tick() {
        if (done || stopped() || !loaded()) { return; }
        if (began == 0) { began = server.getTickCount(); }
        if (data == null) {
            if (boundary.equals("BASELINE_REPLACED")) { data = ResourceEscrowSavedData.existing(server); }
            else { data = ResourceEscrowSavedData.forTests(server); if (!recovery) { data.setDirty(); data.bind(server).autosave(); } }
        }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || data != null && !data.bind(server).operational()) { return; }
        EscrowTags.require(server.getTickCount() - began <= 9_000, "W142 flight fixture exceeded its bounded 9,000 ticks");
        if (recovery) { recover(journal); return; }
        switch (step) {
            case 0 -> create();
            case 1 -> { if (admitted(device)) { step = 2; } }
            case 2 -> { if (retired(device, rocket)) { afterLoad(journal); } }
            case 3 -> fly(journal);
            case 4 -> {
                if (observesAdmission(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery, server.getTickCount())) {
                    observeAdmission(journal);
                }
                if (admitted(second)) { step = 5; }
            }
            case 5 -> successor();
            case 6 -> governed(journal);
            default -> { }
        }
    }

    private void create() {
        if (data != null && (data.ledger().writerGeneration() == 0 || !data.bind(server).writerIdle())) { data.bind(server).autosave(); return; }
        rocket = cargoRocket(0); before = rocket.snapshot().orElseThrow();
        device = device(DEVICE, rocket, RocketCargoRole.LOAD, 5_000);
        save(); // The BEFORE baseline; for cut-N1 the world still has no ledger file.
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(device), true); step = 1;
    }

    private boolean admitted(RocketFluidCargoBlockEntity value) {
        var current = ResourceEscrowSavedData.existing(server); if (current == null) { return false; }
        data = current;
        var op = current.ledger().records().stream().filter(record -> record.device().identity().equals(id(value))).findFirst().orElse(null);
        if (op == null) { return false; }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(value), false);
        EscrowTags.require(op.phase() == EscrowOperation.Phase.PREPARED && op.guard().transfer() == null, "W142 load was not an unguarded PREPARED");
        return true;
    }

    static boolean observesAdmission(boolean hooks, String name, boolean recovery, int tick) {
        return hooks && "FF_TWO_FLIGHTS".equals(name) && !recovery && tick % 100 == 0;
    }

    /** Bounded fixture-only observation; do not attempt admission or alter any queue/turn while diagnosing. */
    private void observeAdmission(RocketTransferSavedData journal) {
        var runtime = EscrowRuntime.of(server); var coordinator = data.bind(server); var root = data.ledger();
        var queue = runtime.fills(server); var turns = coordinator.transferTurns();
        var logical = rocket.assemblyTransactionId().orElseThrow();
        var admission = (EscrowFluidCargoAdmission) field(second, "admission");
        var pacer = field(second, "pacer"); var firstPacer = field(device, "pacer");
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_SECOND_CARGO_ADMISSION tick={} game_time={} device={} logical={} physical={} live={} root={} refusal={} status={} enabled={} pacer_anchor={} first_pacer_anchor={} players={} forced_chunks={} entity_ticking={} queued={} ticket={} queue_active={} queue_waiting={} preparing={} turns_next={} turns_active={} logical_locked={} cargo_preparing={} records={} pending={} floors={} tombstones={} authorities={} armed={} journal={} journal_current={} resource_allowed={} writer_idle={} operational={}",
                server.getTickCount(), level.getGameTime(), id(second), logical, rocket.getUUID(), second.live(), second.dataStatus(),
                second.deviceRefusal(), admission.status(), EscrowCargoSwitches.enabled(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(second)),
                pacer == null ? null : field(pacer, "anchor"), firstPacer == null ? null : field(firstPacer, "anchor"),
                level.players().size(), net.minecraftforge.common.world.ForgeChunkManager.hasForcedChunks(level),
                level.isPositionEntityTicking(SECOND), field(admission, "queuedDevice"), admission.active(), field(queue, "active"),
                ((java.util.Map<?, ?>) field(queue, "waiting")).keySet(), queue.preparing(), field(turns, "next"), field(turns, "active"),
                coordinator.logicalLocked(logical), runtime.cargoPreparing(logical), root.records(), coordinator.admissionAuthority().pendingCount(),
                root.floors().size(), root.tombstones().size(), root.rocketAuthorities().size(), root.armedFlights().size(),
                journal.entries().size(), journal.acknowledgedCurrent(), RocketRuntime.resourceMutationAllowed(rocket),
                coordinator.writerIdle(), coordinator.operational());
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Second cargo diagnostic field unavailable", failure); }
    }

    /** The ordinary C0-C8 retirement: no record names the device and both endpoints are pruned. */
    private boolean retired(RocketFluidCargoBlockEntity value, RocketEntity target) {
        var root = data.ledger();
        return root.records().stream().noneMatch(record -> record.device().identity().equals(id(value)))
                && section(value).markers().isEmpty() && target.escrowSection().markers().isEmpty() && data.bind(server).writerIdle();
    }

    private void afterLoad(RocketTransferSavedData journal) {
        EscrowTags.require(cargo(rocket) == 1_000 && tank(device) == 4_000 && rocket.escrowSection().epoch() == 1
                && data.ledger().tombstones().isEmpty(), "The ordinary load did not conserve 1,000 mB");
        save(); // Complete the cargo fixture baseline, including both pruned wrappers, before testing a flight cut.
        if (boundary.startsWith("GOVERNED")) {
            governedRecord = EscrowCargoTransferFixtures.governed(rocket, before, data.ledger().id(),
                    Math.addExact(data.ledger().writerGeneration(), 1));
            transfer = governedRecord.transferId();
            journal.put(governedRecord); governedJournal = journal.save(new CompoundTag()); journal.flushChecked(server);
            since = server.getTickCount(); step = 6; return;
        }
        flight = new EscrowCargoCutLaunch(level, PAD, this::loaded); step = 3;
    }

    private void fly(RocketTransferSavedData journal) {
        if (!flight.tick(rocket)) { return; }
        if (boundary.equals("CANCELLED_REPLACED") && !cancelled) {
            var record = journal.entries().stream().findFirst().orElse(null);
            if (record == null || rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN || !journal.actionAuthorityAvailable()) { return; }
            var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(rocket.ownerId().orElseThrow(), "W142Owner"));
            player.setPos(rocket.position());
            RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.CANCEL,
                    rocket.flightData().orElseThrow().plan().orElseThrow().destinationTarget(), UUID.randomUUID());
            EscrowTags.require(EscrowRuntime.of(server).returns().handles(record, rocket), "W142 exact-source cancellation was refused");
            cancelled = true; return;
        }
        if (!boundary.equals("SUCCESSOR_CHECKPOINT")) { return; }
        var arrived = arrival(journal, PAD); if (arrived == null) { return; }
        EscrowTags.require(cargo(arrived) == 1_000 && arrived.escrowSection().epoch() == 0, "The first flight lost the latest cargo");
        rocket = arrived; second = device(SECOND, rocket, RocketCargoRole.LOAD, 5_000);
        queueSuccessor(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery, () -> {
            EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(second), true);
            observeAdmission(journal);
        }, second::attempt);
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_SECOND_CARGO_QUEUED tick={} device={} status=READY queue_size={}", server.getTickCount(), id(second),
                EscrowRuntime.of(server).fills(server).size());
        step = 4;
    }

    /** The empty fixture Level stops block ticks after 300 ticks; use ordinary admission, not a queue bypass. */
    static void queueSuccessor(boolean hooks, String name, boolean recovery, Runnable enable,
                               java.util.function.Supplier<RocketCargoStatus> attempt) {
        if (!hooks || !"FF_TWO_FLIGHTS".equals(name) || recovery) { return; }
        enable.run();
        EscrowTags.require(attempt.get() == RocketCargoStatus.READY, "Successor cargo fixture admission was refused");
    }

    /** The successor's own ordinary load retired; its refuel is a native mutation and the cut follows it. */
    private void successor() {
        if (!retired(second, rocket)) { return; }
        EscrowTags.require(cargo(rocket) == 2_000 && tank(second) == 4_000 && rocket.escrowSection().epoch() == 1,
                "The successor's load did not advance its own epoch with 1,000 mB more");
        save(); // Checkpoint the successor's completed cargo mutation before the next launch.
        refuel(rocket);
        EscrowTags.require(rocket.escrowSection().epoch() == 2, "The successor's refuel did not advance its epoch");
        pause(boundary);
    }

    private void governed(RocketTransferSavedData journal) {
        boolean ready = governedBaseline.advance(journal.checkedWriteResult(), completion -> data.bind(server).flightWrite(
                governedRecord.logicalRocketId(), (root, generation) -> {
                    var next = governedRoot(root, governedRecord, boundary.equals("GOVERNED_DURABLE"));
                    governedLedger = next.generation(generation);
                    return next;
                }, () -> true, committed -> {
                    completion.accept(committed);
                    // Stop on the owner-thread acknowledgement, before production terminal cleanup can consume it.
                    if (boundary.equals("GOVERNED_DURABLE")) { governed(journal); }
                }), () -> {
                    if (boundary.equals("GOVERNED_HELD") && server.getTickCount() - since < 200) { return false; }
                    var savedLedger = CheckedSavedDataFile.readPayload(ledgerFile, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
                    var journalFile = ledgerFile.resolveSibling(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
                    var savedJournal = CheckedSavedDataFile.readPayload(journalFile, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
                    requireGovernedReadback(governedLedger, governedJournal, savedLedger, savedJournal);
                    io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                            "ARCE_RELEASE_CARGO_GOVERNED_BASELINE cut={} ledger=COMMITTED journal=COMMITTED generation={} armed={} serial={} readback=exact",
                            name, governedLedger.writerGeneration(), governedLedger.armedFlights().size(), governedLedger.issuedLaunchSerial());
                    return true;
                });
        if (!ready) { return; }
        if (boundary.equals("GOVERNED_DURABLE")) { pause(boundary); return; }
        requireHeld(journal); pause(boundary);
    }

    /** A gated fixture candidate, never an in-memory acknowledgement or a second ledger writer. */
    static EscrowLedger governedRoot(EscrowLedger root, RocketTransferRecord record, boolean terminal) {
        EscrowTags.require(root.armedFlights().isEmpty() && root.issuedLaunchSerial() == 0, "Governed fixture is not a fresh flight baseline");
        if (!terminal) { return root; }
        var a = record.nativeTransfer().orElseThrow().armed();
        var handedOff = new EscrowFlightTables.Armed(a.ledger(), a.logical(), a.owner(), a.source(), a.epoch(), a.transfer(), a.serial(),
                a.generation(), a.binding(), a.launch(), a.sourceEnvelope(), a.destinationEnvelope(), a.destination(), a.journal(),
                a.anchor(), a.releaseGeneration(), EscrowFlightTables.Outcome.HANDED_OFF);
        return new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), root.records(), root.floors(), root.tombstones(),
                a.serial(), root.rocketAuthorities(), List.of(handedOff));
    }

    static void requireGovernedReadback(EscrowLedger expectedLedger, CompoundTag expectedJournal, CompoundTag ledger, CompoundTag journal) {
        EscrowTags.require(EscrowCodec.encode(expectedLedger).equals(ledger), "Governed fixture ledger readback differs");
        EscrowTags.require(expectedJournal.equals(journal), "Governed fixture journal readback differs");
    }

    /** Request acceptance, successful completion and current journal acknowledgement are distinct steps. */
    static final class GovernedBaseline {
        private boolean requested, committed;

        boolean advance(RocketTransferSavedData.CheckedWriteResult journal, Function<Consumer<Boolean>, Boolean> request,
                        BooleanSupplier readback) {
            if (journal.outcome() != RocketTransferSavedData.WriteOutcome.COMMITTED || !journal.acknowledgedCurrent()) { return false; }
            if (!requested) { requested = request.apply(this::acknowledge); return false; }
            return committed && readback.getAsBoolean();
        }

        private void acknowledge(boolean success) {
            EscrowTags.require(success, "Governed fixture ledger write was not COMMITTED");
            committed = true;
        }
    }

    private void recover(RocketTransferSavedData journal) {
        if (server.getTickCount() - began < 100) { return; }
        if (data == null) { data = ResourceEscrowSavedData.existing(server); if (data == null) { return; } }
        var root = data.ledger(); var found = rockets();
        EscrowTags.require(found.size() <= 1, "W142 restart duplicated the cargo rocket");
        device = existingDevice(DEVICE); second = existingDevice(SECOND);
        if (found.isEmpty() || device == null) { return; }
        rocket = found.get(0);
        switch (boundary) {
            case "BASELINE_REPLACED" -> created();
            case "ARMED_REPLACED", "CANCELLED_REPLACED" -> {
                if (!idle(journal, root) || rocket.getX() != ROCKET.getX() + 0.5D) { return; }
                var flightData = rocket.flightData().orElseThrow();
                EscrowTags.require(cargo(rocket) == 1_000 && flightData.fuel().totalAmount() == 1_000 && rocket.escrowSection().epoch() == 1
                        && flightData.state().acceptsFuel() && root.issuedLaunchSerial() == 1 && tank(device) == 4_000,
                        "The exact source did not survive unchanged with the latest cargo");
                result("SOURCE_KEPT", "rocket=1000 device=4000 fuel=1000 epoch=1 serial=1 armed=0 journal=idle");
            }
            case "F6_JOINT_PROVED", "TERMINAL_PRUNE_REPLACED" -> {
                if (!idle(journal, root) || rocket.getX() != PAD.getX() + 0.5D) { return; }
                requireLanded(root, 1_000, 1);
                result("HANDED_OFF", "rocket=1000 device=4000 fuel=700 epoch=0 serial=1 armed=0 journal=idle");
            }
            case "SUCCESSOR_CHECKPOINT" -> secondFlight(journal, root);
            case "GOVERNED_HELD" -> {
                if (server.getTickCount() - began < 300) { return; }
                requireHeld(journal);
                result("GOVERNED_HELD", "rocket=1000 epoch=1 journal=retained legacy=none");
            }
            case "GOVERNED_DURABLE" -> governedTerminal(journal, root);
            default -> throw new IllegalStateException("Unknown W142 flight boundary");
        }
    }

    /** cut-N1: a valid empty ledger or none, unchanged endpoints and no issued sequence; then the next attempt succeeds. */
    private void created() {
        var root = data.ledger();
        if (attempted && root.issuedThrough() > 0) {
            EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(device), false);
        }
        if (cargo(rocket) == 0 && root.issuedThrough() == 0) {
            EscrowTags.require(root.records().isEmpty() && root.writerGeneration() >= 1 && tank(device) == 5_000
                    && rocket.escrowSection().equals(RocketEscrowSection.neutral()) && section(device).epoch() == 0,
                    "cut-N1 restart did not keep a valid empty ledger and unchanged endpoints");
            if (!attempted) { EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(device), true); attempted = true; }
            return;
        }
        if (!root.records().isEmpty() || !retired(device, rocket)) { return; }
        if (attempted) { EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, id(device), false); }
        EscrowTags.require(cargo(rocket) == 1_000 && tank(device) == 4_000 && rocket.escrowSection().epoch() == 1 && section(device).epoch() == 1
                && root.issuedThrough() == 1 && root.tombstones().isEmpty(), "The attempt after cut-N1 did not conserve exactly once");
        result("CREATED", "rocket=1000 device=4000 epoch=1 issued=1 attempt=" + (attempted ? "after_restart" : "durable"));
    }

    /** Restart 1 refuels when the unsaved refuel was lost, then flies back to the first pad; restart 2 checks it. */
    private void secondFlight(RocketTransferSavedData journal, EscrowLedger root) {
        if (rocket.getX() == ROCKET.getX() + 0.5D) {
            if (!idle(journal, root)) { return; }
            requireLanded(root, 2_000, 2);
            EscrowTags.require(second != null && tank(second) == 4_000, "The successor's device changed");
            result("SECOND_FLIGHT", "rocket=2000 devices=4000,4000 fuel=700 epoch=0 serial=2 armed=0 journal=idle receipts=0");
            return;
        }
        if (flight == null) {
            EscrowTags.require(cargo(rocket) == 2_000 && second != null && tank(second) == 4_000 && root.issuedLaunchSerial() == 1
                    && root.armedFlights().isEmpty() && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty(),
                    "The successor's durable cargo mutation differs after restart");
            if (rocket.flightData().orElseThrow().fuel().totalAmount() < 1_000) { refuel(rocket); }
            flight = new EscrowCargoCutLaunch(level, ROCKET, this::loaded);
        }
        flight.tick(rocket);
    }

    private void governedTerminal(RocketTransferSavedData journal, EscrowLedger root) {
        if (server.getTickCount() - began < 300) { return; }
        boolean cleaned = journal.find(transferOf(journal, root)).isEmpty() && root.armedFlights().isEmpty();
        EscrowTags.require(rocket.getX() == ROCKET.getX() + 0.5D && cargo(rocket) == 1_000 && rocket.escrowSection().epoch() == 1
                && rocket.isAlive() && tank(device) == 4_000, "Terminal governed authority changed the latest cargo rocket");
        result(cleaned ? "GOVERNED_CLEANED" : "GOVERNED_CLEANUP_HELD", "rocket=1000 epoch=1 journal=" + journal.entries().size()
                + " armed=" + root.armedFlights().size() + " diagnostics=" + diagnostics(journal));
    }

    private UUID transferOf(RocketTransferSavedData journal, EscrowLedger root) {
        if (transfer == null && !journal.entries().isEmpty()) { transfer = journal.entries().get(0).transferId(); }
        if (transfer == null && !root.armedFlights().isEmpty()) { transfer = root.armedFlights().get(0).transfer(); }
        return transfer == null ? new UUID(0, 0) : transfer;
    }

    private void requireHeld(RocketTransferSavedData journal) {
        var root = data.ledger(); var record = journal.entries().size() == 1 ? journal.entries().get(0) : null;
        EscrowTags.require(record != null && record.nativeTransfer().isPresent() && root.armedFlights().isEmpty()
                && rocket.getX() == ROCKET.getX() + 0.5D && rocket.isAlive() && cargo(rocket) == 1_000 && rocket.escrowSection().epoch() == 1
                && EscrowGovernedDispatch.decide(root, record).route() == EscrowGovernedDispatch.Route.HOLD,
                "A governed record with missing authority moved, discarded or replaced the latest cargo rocket");
    }

    private void requireLanded(EscrowLedger root, int cargo, long serial) {
        var flightData = rocket.flightData().orElseThrow();
        EscrowTags.require(flightData.state() == RocketFlightState.LANDED && cargo(rocket) == cargo && flightData.fuel().totalAmount() == 700
                && rocket.escrowSection().equals(new RocketEscrowSection(root.id(), 0, List.of())) && RocketEscrowGate.idle(rocket)
                && root.issuedLaunchSerial() == serial && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty() && tank(device) == 4_000,
                "The handoff did not keep exactly the latest cargo with fresh governed authority");
    }

    private boolean idle(RocketTransferSavedData journal, EscrowLedger root) {
        return journal.entries().isEmpty() && root.armedFlights().isEmpty() && journal.actionAuthorityAvailable() && RocketEscrowGate.idle(rocket);
    }

    private RocketEntity arrival(RocketTransferSavedData journal, BlockPos pad) {
        var found = rockets().stream().filter(value -> value.getX() == pad.getX() + 0.5D).findFirst().orElse(null);
        return found != null && journal.entries().isEmpty() && data.ledger().armedFlights().isEmpty() && journal.actionAuthorityAvailable()
                && RocketEscrowGate.idle(found) ? found : null;
    }

    private void refuel(RocketEntity value) {
        var flightData = value.flightData().orElseThrow();
        value.updateFlightData(flightData.withFuel(flightData.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
    }

    private static String diagnostics(RocketTransferSavedData journal) {
        if (journal.entries().isEmpty()) { return "none"; }
        var text = RocketRuntime.transferFailureDiagnostics(journal.entries().get(0).transferId()).replace(' ', '_');
        return text.length() <= 240 ? text : text.substring(0, 240);
    }
}
