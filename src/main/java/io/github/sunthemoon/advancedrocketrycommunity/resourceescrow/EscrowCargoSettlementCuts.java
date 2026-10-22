package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * C-S4 (FS_ cases): operator settlement of a real fluid cargo unload (5,000 mB rocket tank, empty device, 1,000 mB),
 * cut at each boundary of W11CD section 3.3. APPLIED holds the unload after both sides published; NEUTRAL, NAMED and
 * the ABANDON scenarios hold it at PREPARED; NAMED adds a COMMITTED landed record, so O3J removes it; VETO stores only
 * the device before the hold and then vetoes the rocket's O2 store; BIND_VETO moves the neutral survivor after O1.
 */
final class EscrowCargoSettlementCuts extends EscrowCargoCutProbe {
    enum Scenario { APPLIED, NEUTRAL, NAMED, ABANDON_ROCKET, ABANDON_DEVICE, VETO, BIND_VETO;
        boolean abandon() { return this == ABANDON_ROCKET || this == ABANDON_DEVICE; }
        boolean prepared() { return this != APPLIED && this != VETO; }
    }
    record Cut(Scenario scenario, String boundary) { }
    static final Map<String, Cut> CASES = Map.ofEntries(
            Map.entry("FS_O1_BEFORE", new Cut(Scenario.APPLIED, "O1_BEFORE")),
            Map.entry("FS_O1_AFTER", new Cut(Scenario.APPLIED, "O1_AFTER")),
            Map.entry("FS_BIND_BEFORE", new Cut(Scenario.NEUTRAL, "O_BIND_BEFORE")),
            Map.entry("FS_BIND_AFTER", new Cut(Scenario.NEUTRAL, "O_BIND_AFTER")),
            Map.entry("FS_O2_ROCKET_BEFORE", new Cut(Scenario.APPLIED, "O2_ROCKET_BEFORE")),
            Map.entry("FS_O2_ROCKET_AFTER", new Cut(Scenario.APPLIED, "O2_ROCKET_AFTER")),
            Map.entry("FS_O2_DEVICE_BEFORE", new Cut(Scenario.APPLIED, "O2_DEVICE_BEFORE")),
            Map.entry("FS_O2_DEVICE_AFTER", new Cut(Scenario.APPLIED, "O2_DEVICE_AFTER")),
            Map.entry("FS_O3_BEFORE", new Cut(Scenario.APPLIED, "O3_BEFORE")),
            Map.entry("FS_O3_AFTER", new Cut(Scenario.APPLIED, "O3_AFTER")),
            Map.entry("FS_O3J_BEFORE", new Cut(Scenario.NAMED, "O3J_BEFORE")),
            Map.entry("FS_O3J_AFTER", new Cut(Scenario.NAMED, "O3J_REPLACED")),
            Map.entry("FS_O4_BEFORE", new Cut(Scenario.APPLIED, "O4_BEFORE")),
            Map.entry("FS_O4_AFTER", new Cut(Scenario.APPLIED, "O4_AFTER")),
            Map.entry("FS_PRUNE_BEFORE", new Cut(Scenario.APPLIED, "PRUNE_BEFORE")),
            Map.entry("FS_PRUNE_AFTER", new Cut(Scenario.APPLIED, "PRUNE_AFTER")),
            Map.entry("FS_ABANDON_ROCKET", new Cut(Scenario.ABANDON_ROCKET, "O1_AFTER")),
            Map.entry("FS_ABANDON_DEVICE", new Cut(Scenario.ABANDON_DEVICE, "O1_AFTER")),
            Map.entry("FS_VETO_DESTINATION_SAVED", new Cut(Scenario.VETO, "O_VETO")),
            Map.entry("FS_BIND_VETO", new Cut(Scenario.BIND_VETO, "NORMALIZATION_VETO")));
    private final Cut cut;
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private RocketEntity rocket;
    private RocketFluidCargoBlockEntity device;
    private volatile UUID deviceId;
    private UUID opId, logical;
    private EscrowSettlementIntent preview;
    private long began, since;
    private int step;
    private volatile int rocketIndex = -1, deviceIndex = -1;
    private volatile boolean writingIntent, held, destinationSelected, destinationStored, vetoInstalled, holdSeen;
    private volatile String phase = "";
    private boolean executorPaused, heldChecked, moved, movedSaved, restored;
    private EscrowCargoCutLaunch flight;

    EscrowCargoSettlementCuts(MinecraftServer server) {
        super(server);
        cut = CASES.get(name);
        EscrowTags.require(cut != null, "Unknown W142 settlement cut");
    }

    /** Which checkpoint ends the start run; the PRUNE and veto boundaries are decided by the owner tick instead. */
    static boolean pauses(String boundary, String label, String phase, boolean writingIntent, int rocket, int device) {
        return switch (boundary) {
            case "O1_BEFORE" -> writingIntent && label.equals("K1");
            case "O1_AFTER" -> writingIntent && label.equals("K2");
            case "O_BIND_BEFORE", "O_BIND_AFTER" -> label.equals(boundary);
            case "O2_ROCKET_BEFORE" -> rocket >= 0 && label.equals("O2_BEFORE_" + rocket);
            case "O2_ROCKET_AFTER" -> rocket >= 0 && label.equals("O2_AFTER_" + rocket);
            case "O2_DEVICE_BEFORE" -> device >= 0 && label.equals("O2_BEFORE_" + device);
            case "O2_DEVICE_AFTER" -> device >= 0 && label.equals("O2_AFTER_" + device);
            case "O3_BEFORE" -> phase.equals("O3") && label.equals("K1");
            case "O3_AFTER" -> phase.equals("O3") && label.equals("K2");
            case "O4_BEFORE" -> phase.equals("O4") && (label.equals("K1") || label.equals("K8"));
            case "O4_AFTER" -> phase.equals("O4") && (label.equals("K2") || label.equals("K9"));
            default -> false;
        };
    }
    /** VETO's ordinary reconciliation stores only the device (the destination); every other run keeps production order. */
    static boolean storeAllowed(boolean selecting, boolean ours, boolean destination) { return !selecting || !ours || destination; }

    @Override void checkpoint(String label) {
        if (recovery) {
            if (label.startsWith("O2_AFTER_") || label.equals("O3J_AFTER") || label.equals("O4_CAPTURE")) {
                var existing = ResourceEscrowSavedData.existing(server);
                if (existing != null && existing.ledger().records().size() == 1) {
                    var op = existing.ledger().records().get(0);
                    var intent = EscrowSettlementIntent.decode(op.settlement());
                    var view = EscrowRuntime.of(server).settlementRecovery().view(intent);
                    io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                            "ARCE_RELEASE_CARGO_CUT_PROOF cut={} checkpoint={} digest={} proved={} waiting={}",
                            name, label, intent.digest(), view.provedSlots(), view.waitingSlots());
                }
            }
            return;
        }
        if (done || stopped()) { return; }
        if (cut.scenario() == Scenario.APPLIED && !held && label.equals("C2_DESTINATION_AFTER") && deviceId != null && server.isSameThread()) {
            // Both sides published: hold the ordinary executor before any barrier store.
            EscrowRuntime.of(server).cargoSwitches.pause(deviceId, true); held = true;
        }
        if (cut.scenario() == Scenario.VETO) {
            if (label.equals("CARGO_STORE_DESTINATION_SELECTED")) { destinationSelected = true; }
            if (label.equals("BARRIER_FORCE_AFTER") && destinationSelected) { destinationStored = true; }
        }
        if (label.equals("O3_CAPTURE") || label.equals("O4_CAPTURE")) { phase = label.substring(0, 2); }
        if (label.equals("O_VETO") && vetoInstalled) { holdSeen = true; }
        if (pauses(cut.boundary(), label, phase, writingIntent, rocketIndex, deviceIndex)) { pause(cut.boundary()); }
    }

    @Override void beforeReconciliation() {
        if (recovery || done || stopped() || cut.scenario() != Scenario.VETO || !destinationStored || executorPaused) { return; }
        EscrowRuntime.of(server).cargoSwitches.pause(deviceId, true); executorPaused = true; since = server.getTickCount();
    }

    @Override boolean permitsStore(EscrowOperation op, EscrowEndpoint endpoint) {
        var id = deviceId;
        return storeAllowed(cut.scenario() == Scenario.VETO && !recovery, id != null && op.device().identity().equals(id),
                endpoint.key().equals(op.destination().key()));
    }

    @Override void tick() {
        if (done || stopped() || !loaded()) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); coordinator.autosave(); }
        }
        coordinator = data.bind(server);
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !coordinator.operational()) { return; }
        EscrowTags.require(server.getTickCount() - began <= 6_000, "W142 settlement fixture exceeded its bounded 6,000 ticks");
        if (recovery) { recover(journal); return; }
        switch (step) {
            case 0 -> create(journal);
            case 1 -> admitted();
            case 2 -> hold();
            case 3 -> held();
            case 4 -> confirm();
            case 5 -> observe();
            default -> { }
        }
    }

    private void create(RocketTransferSavedData journal) {
        if (data.ledger().writerGeneration() == 0 || !coordinator.writerIdle()) { coordinator.autosave(); return; }
        if (rocket == null) {
            rocket = cargoRocket(5_000); logical = rocket.assemblyTransactionId().orElseThrow();
            if (cut.scenario() == Scenario.NAMED) {
                journal.put(EscrowCargoTransferFixtures.landedRecord(rocket, rocket.snapshot().orElseThrow())); journal.flushChecked(server);
            }
            return;
        }
        if (cut.scenario() == Scenario.NAMED && (!journal.acknowledgedCurrent() || !RocketRuntime.resourceMutationAllowed(rocket))) { return; }
        device = device(DEVICE, rocket, RocketCargoRole.UNLOAD, 0); deviceId = id(device);
        save(); // The BEFORE baseline; nothing saves an applied endpoint before the hold.
        if (cut.scenario().prepared()) { EscrowCargoSwitches.pauseReconciliationForTests(server, deviceId, true); }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, deviceId, true); step = 1;
    }

    private void admitted() {
        var op = member(); if (op == null) { return; }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, deviceId, false);
        EscrowTags.require(op.phase() == EscrowOperation.Phase.PREPARED && op.nativeBinding().complete()
                && (cut.scenario() == Scenario.NAMED) == (op.guard().transfer() != null), "W142 cargo admission did not reach the expected PREPARED");
        opId = op.id(); step = 2;
    }

    private void hold() {
        if (cut.scenario() == Scenario.APPLIED && !held) { return; }
        if (cut.scenario() == Scenario.VETO && (!executorPaused || server.getTickCount() - since < 40)) { return; }
        coordinator.transition(opId, EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
    }

    /** The durable manual conflict; ABANDON first removes its absent endpoint, VETO installs its entity-store veto. */
    private void held() {
        var op = member();
        if (op == null || op.phase() != EscrowOperation.Phase.REPAIR || !coordinator.writerIdle()) { return; }
        if (!heldChecked) {
            boolean applied = !cut.scenario().prepared();
            requireRocket(rocket, applied ? 4_000 : 5_000, applied ? 1 : 0, applied ? 1 : 0, applied);
            requireDevice(device, applied ? 1_000 : 0, applied ? 1 : 0, applied ? 1 : 0);
            heldChecked = true;
        }
        if (cut.scenario() == Scenario.ABANDON_ROCKET && !rocket.isRemoved()) { rocket.discard(); return; }
        if (cut.scenario() == Scenario.ABANDON_DEVICE && level.getBlockEntity(DEVICE) != null) {
            level.removeBlockEntity(DEVICE); level.setBlock(DEVICE, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); return;
        }
        if (cut.scenario() == Scenario.VETO) {
            // The rocket's entity slot refuses capture, so O2 can never store the rocket (the source) after the device.
            var poison = new net.minecraft.world.entity.decoration.ArmorStand(level, ROCKET.getX() + 2.5D, ROCKET.getY(), ROCKET.getZ() + 2.5D) {
                @Override public boolean save(CompoundTag tag) { return false; }
            };
            poison.setNoGravity(true); EscrowTags.require(level.addFreshEntity(poison), "W142 veto entity was not added"); vetoInstalled = true;
        } else { save(); }
        var choice = cut.scenario().abandon() ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT;
        preview = EscrowRuntime.of(server).settlement().preview(server, opId, choice, ACTOR, "Server");
        for (int i = 0; i < preview.observations().size(); i++) {
            var key = preview.observations().get(i).endpoint().key();
            if (key.equals(op.rocket().key())) { rocketIndex = i; } else { deviceIndex = i; }
        }
        step = 4;
    }

    private void confirm() {
        if (cut.scenario().abandon() && server.getTickCount() - preview.previewTick() < 200) { return; }
        writingIntent = true;
        boolean queued = EscrowRuntime.of(server).settlement().confirm(server, opId, preview.choice(), ACTOR, preview.digest(), committed -> {
            EscrowTags.require(committed, "W142 O1 was not acknowledged"); writingIntent = false;
            if (cut.scenario() == Scenario.BIND_VETO) {
                // In the same owner callback, before normalization can run: the chosen neutral survivor moves.
                rocket.setPos(rocket.getX() + 0.25D, rocket.getY(), rocket.getZ()); moved = true; vetoInstalled = true;
            }
        });
        // A recent checked write can still be inside the coordinator's ten-tick pacing window.
        // Retry the same preview, as the command caller does; do not bypass writer admission.
        if (queued) { step = 5; } else { writingIntent = false; }
    }

    private void observe() {
        var root = data.ledger();
        if (moved && !movedSaved) { save(); movedSaved = true; } // The moved placement becomes durable before the cut.
        if (holdSeen) {
            var intent = EscrowSettlementIntent.decode(member().settlement());
            var view = EscrowRuntime.of(server).settlementRecovery().view(intent);
            if (view.reason().equals("NONE") || cut.scenario() == Scenario.BIND_VETO && !movedSaved) { return; }
            if (cut.scenario() == Scenario.VETO) {
                EscrowTags.require(view.reason().equals("BARRIER_VETO") && root.tombstones().isEmpty() && view.provedSlots() <= 1,
                        "The rocket store veto did not hold O2: " + view.reason());
            } else {
                EscrowTags.require(view.reason().startsWith("Rocket placement") && root.tombstones().isEmpty() && view.provedSlots() == 0
                        && rocket.escrowSection().equals(RocketEscrowSection.neutral()), "The placement veto did not hold normalization: " + view.reason());
            }
            pause(cut.boundary()); return;
        }
        if (cut.boundary().startsWith("PRUNE_")) {
            if (!root.records().isEmpty() || !released(root) || !rocket.escrowSection().markers().isEmpty() || !section(device).markers().isEmpty()) { return; }
            if (cut.boundary().equals("PRUNE_AFTER")) { save(); }
            pause(cut.boundary());
        }
    }

    private void recover(RocketTransferSavedData journal) {
        if (server.getTickCount() - began < 100 || !coordinator.writerIdle()) { return; }
        var root = data.ledger(); var found = rockets();
        if (flight != null) {
            // In flight both ends can exist for a moment; follow the arrival once it stands on the pad.
            var arrived = found.stream().filter(value -> value.getX() == PAD.getX() + 0.5D).findFirst();
            if (arrived.isPresent()) { rocket = arrived.get(); } else if (rocket != null && !rocket.isAlive()) { return; }
            launch(RocketTransferSavedData.get(server), root); return;
        }
        EscrowTags.require(found.size() <= 1, "W142 restart duplicated the cargo rocket");
        rocket = found.isEmpty() ? null : found.get(0); device = existingDevice(DEVICE);
        if (rocket == null && cut.scenario() != Scenario.ABANDON_ROCKET || device == null && cut.scenario() != Scenario.ABANDON_DEVICE) { return; }
        if (cut.boundary().equals("O1_BEFORE")) {
            var op = single(root);
            EscrowTags.require(op.phase() == EscrowOperation.Phase.REPAIR && op.reason() == EscrowReason.OTHER_STATE && op.settlement().isEmpty()
                    && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty(), "Pre-O1 restart invented or lost a decision");
            requireRocket(rocket, 4_000, 1, 1, true); requireDevice(device, 1_000, 1, 1); requireLocked();
            result("PRE_O1", "records=1 intent=absent rocket=4000 device=1000 locked=true"); return;
        }
        if (cut.scenario() == Scenario.VETO) {
            var view = view(single(root)); if (view.reason().equals("NONE")) { return; }
            EscrowTags.require(view.reason().equals("Frozen settlement endpoint changed") && root.tombstones().isEmpty(),
                    "The O-veto restart released or changed its hold: " + view.reason());
            // Raw stores hold 5,000 + 1,000 mB, and both endpoints stay locked: 6,000 mB never becomes available.
            requireRocket(rocket, 5_000, 0, 0, false); requireDevice(device, 1_000, 1, 1); requireLocked();
            result("HELD", "reason=FROZEN_ENDPOINT_CHANGED rocket=5000 device=1000 raw=6000 spendable=0 locked=true"); return;
        }
        if (cut.scenario() == Scenario.BIND_VETO && !root.records().isEmpty()) {
            if (restored) { return; }
            var view = view(single(root)); if (view.reason().equals("NONE")) { return; }
            EscrowTags.require(view.reason().startsWith("Rocket placement") && root.tombstones().isEmpty() && view.provedSlots() == 0
                    && rocket.getX() == ROCKET.getX() + 0.75D && rocket.escrowSection().equals(RocketEscrowSection.neutral()) && cargo(rocket) == 5_000,
                    "The durable placement veto did not hold normalization: " + view.reason());
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_RELEASE_CARGO_CUT_HELD cut={} reason=PLACEMENT normalized=false proved=0 receipts=0", name);
            rocket.setPos(ROCKET.getX() + 0.5D, ROCKET.getY(), ROCKET.getZ() + 0.5D); restored = true; return;
        }
        if (!root.records().isEmpty() || !released(root) || rocket != null && !rocket.escrowSection().markers().isEmpty()
                || device != null && !section(device).markers().isEmpty()) { return; }
        if (cut.boundary().equals("O3_AFTER")) { launch(journal, root); return; }
        released(root, journal);
        result("RELEASED", "rocket=" + (rocket == null ? "absent" : cargo(rocket)) + " device=" + (device == null ? "absent" : tank(device))
                + " receipts=1 accounting=UNKNOWN");
    }

    /** The receipt, head and both survivors exactly as section 3.3 derives them from the frozen O1 choice. */
    private void released(EscrowLedger root, RocketTransferSavedData journal) {
        var scenario = cut.scenario(); boolean applied = scenario == Scenario.APPLIED;
        var receipt = root.tombstones().get(0);
        int survivors = scenario.abandon() ? 1 : 2;
        EscrowTags.require(root.tombstones().size() == 1 && receipt.getBoolean("released") && receipt.getInt("payload_amount") == 0
                && receipt.getString("accounting_status").equals("UNKNOWN")
                && receipt.getString("choice").equals((scenario.abandon() ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT).name())
                && receipt.getList("survivors", 10).size() == survivors && receipt.getList("missing", 10).size() == 2 - survivors
                && root.floors().isEmpty() && root.armedFlights().isEmpty() && journal.entries().isEmpty(), "W142 receipt changed: " + receipt);
        for (var raw : receipt.getList("survivors", 10)) {
            var survivor = (CompoundTag) raw; var reference = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
            boolean isRocket = reference.kind() == EscrowEndpoint.Kind.ENTITY;
            EscrowTags.require(survivor.getString("signature_kind").equals((isRocket ? EscrowSignatureKind.FUEL_VECTOR_V1 : EscrowSignatureKind.FLUID_TANK_V1).name())
                    && (!isRocket || survivor.getString("invariant_digest").equals(EscrowInvariant.rocket(rocket))
                        && survivor.getLong("quantity") == rocket.flightData().orElseThrow().fuel().totalAmount())
                    && (isRocket || survivor.getLong("quantity") == tank(device)), "W142 receipt survivor projection changed: " + survivor);
        }
        if (rocket == null) {
            EscrowTags.require(root.rocketAuthorities().isEmpty() && EscrowSettlementTombstones.logicalLocked(root, device.root().orElseThrow().binding().logicalRocket())
                    && tank(device) == 0 && section(device).equals(RocketEscrowSection.neutral()) && !device.escrowLocked(),
                    "ABANDON fabricated a head, unlocked the missing rocket or changed the device");
            return;
        }
        var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(rocket.assemblyTransactionId().orElseThrow())).findFirst().orElseThrow();
        EscrowTags.require(head.physical().equals(rocket.getUUID()) && head.epoch() == (applied ? 1 : 0) && head.kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                && head.signature().equals(rocket.flightData().orElseThrow().fuel().resourceSignature()) && head.invariant().equals(EscrowInvariant.rocket(rocket))
                && head.outcome() == EscrowFlightTables.Outcome.READY, "O4 did not derive READY from the frozen fuel projection");
        requireRocket(rocket, applied ? 4_000 : 5_000, applied ? 1 : 0, 0, true);
        EscrowTags.require(RocketEscrowGate.idle(rocket), "The released rocket stayed locked");
        if (device != null) {
            requireDevice(device, applied ? 1_000 : 0, applied ? 1 : 0, 0);
            EscrowTags.require(!device.escrowLocked() && cargo(rocket) + tank(device) == 5_000, "The released device stayed locked or broke conservation");
        }
    }

    /** O3-after: restart 1 completes O4 and pruning from durable data, then launches; restart 2 checks the landed successor. */
    private void launch(RocketTransferSavedData journal, EscrowLedger root) {
        if (rocket.getX() == PAD.getX() + 0.5D) {
            // F6 to F8 still run after the arrival appears; the successor is checked once the journal is idle.
            if (journal.entries().isEmpty() && root.armedFlights().isEmpty() && journal.actionAuthorityAvailable() && RocketEscrowGate.idle(rocket)) {
                landed(journal, root);
            }
            return;
        }
        if (flight == null) { released(root, journal); flight = new EscrowCargoCutLaunch(level, PAD, this::loaded); }
        flight.tick(rocket);
    }

    private void landed(RocketTransferSavedData journal, EscrowLedger root) {
        var flight = rocket.flightData().orElseThrow();
        EscrowTags.require(root.rocketAuthorities().size() == 1, "O3 launch head identity: expected one successor head");
        var head = root.rocketAuthorities().get(0);
        EscrowTags.require(head.outcome() == EscrowFlightTables.Outcome.HANDED_OFF,
                "O3 launch head outcome: expected HANDED_OFF, got " + head.outcome());
        EscrowTags.require(head.logical().equals(flight.logicalRocketId()) && head.physical().equals(rocket.getUUID())
                && head.owner().equals(rocket.ownerId().orElseThrow())
                && head.epoch() == 0 && head.kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                && head.signature().equals(flight.fuel().resourceSignature()) && head.invariant().equals(EscrowInvariant.rocket(rocket))
                && head.issued() == 1 && head.completed() == 1, "O3 launch head identity: latest successor identity differs");
        EscrowTags.require(cargo(rocket) == 4_000 && tank(device) == 1_000 && flight.fuel().totalAmount() == 700,
                "O3 launch native quantities: rocket=" + cargo(rocket) + " device=" + tank(device) + " fuel=" + flight.fuel().totalAmount());
        EscrowTags.require(root.tombstones().size() == 1, "O3 launch receipt: expected one retained receipt");
        var receipt = root.tombstones().get(0);
        EscrowTags.require(receipt.getBoolean("released") && receipt.getString("accounting_status").equals("UNKNOWN")
                && receipt.getInt("payload_amount") == 0 && receipt.getString("choice").equals("ACCEPT_CURRENT")
                && receipt.getList("survivors", 10).size() == 2 && receipt.getList("missing", 10).isEmpty()
                && head.anchor().equals(receipt.getUUID("anchor_op")) && head.releaseGeneration() == receipt.getLong("release_generation"),
                "O3 launch receipt: audit or successor anchor changed");
        EscrowTags.require(rocket.escrowSection().equals(new RocketEscrowSection(root.id(), 0, List.of()))
                && section(device).epoch() == 1 && section(device).markers().isEmpty(), "O3 launch markers: successor or device section differs");
        EscrowTags.require(flight.state() == RocketFlightState.LANDED && RocketEscrowGate.idle(rocket) && !device.escrowLocked()
                && root.records().isEmpty() && root.floors().isEmpty() && root.armedFlights().isEmpty()
                && root.issuedLaunchSerial() == 1 && journal.entries().isEmpty(), "O3 launch lifecycle: landed successor is not idle");
        result("LANDED", "rocket=4000 device=1000 fuel=700 serial=1 armed=0 journal=idle");
    }

    private void requireRocket(RocketEntity value, int cargo, long epoch, int markers, boolean bound) {
        var section = value.escrowSection();
        EscrowTags.require(cargo(value) == cargo && section.epoch() == epoch && section.markers().size() == markers
                && (bound ? data.ledger().id().equals(section.ledger()) : section.ledger() == null), "W142 rocket state differs: cargo=" + cargo(value) + " " + section);
    }
    private void requireDevice(RocketFluidCargoBlockEntity value, int amount, long epoch, int markers) {
        var section = section(value);
        EscrowTags.require(tank(value) == amount && section.epoch() == epoch && section.markers().size() == markers,
                "W142 device state differs: tank=" + tank(value) + " " + section);
    }
    private void requireLocked() {
        EscrowTags.require(!RocketEscrowGate.idle(rocket) && device.escrowLocked(), "A held endpoint was released");
    }
    private EscrowOperation member() {
        var id = deviceId;
        return data.ledger().records().stream().filter(op -> id != null && op.device().identity().equals(id)).findFirst().orElse(null);
    }
    private static EscrowOperation single(EscrowLedger root) {
        EscrowTags.require(root.records().size() == 1, "W142 held settlement lost or added a record"); return root.records().get(0);
    }
    private EscrowSettlementRecovery.View view(EscrowOperation op) {
        return EscrowRuntime.of(server).settlementRecovery().view(EscrowSettlementIntent.decode(op.settlement()));
    }
    private static boolean released(EscrowLedger root) { return !root.tombstones().isEmpty() && root.tombstones().get(0).getBoolean("released"); }
}
