package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** W97's observation-only cuts. Fixture setup uses the existing per-device admission gate; recovery does not. */
final class EscrowLoaderCutProbe {
    static final String PREFIX = "LF_M_";
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64);
    private static final BlockPos A = new BlockPos(66, 100, 64);
    private static final BlockPos B = new BlockPos(67, 100, 64);
    private static final List<BlockPos> CAPACITY_LOADERS = List.of(B, new BlockPos(66, 100, 65), new BlockPos(67, 100, 65),
            new BlockPos(68, 100, 65), new BlockPos(69, 100, 65));
    private final MinecraftServer server;
    private final String cut, boundary;
    private final boolean recovery, bi, creation, chain;
    private final AtomicBoolean paused = new AtomicBoolean();
    private final List<FluidFuelLoaderBlockEntity> loaders = new ArrayList<>();
    private final EscrowLoaderStorageProbe storage;
    private final BlockPos origin, source;
    private final Area unloadArea;
    private EscrowOperation unknownCandidate;
    private long unloadedAt = -1;
    private RocketEntity rocket;
    private boolean firstTick, done, retrying, pendingObserved, primed;
    private volatile boolean armed;
    private volatile boolean latestProved;
    private boolean secondQueued;
    private volatile String firstStore = "NONE";
    private int step, stores, failures;
    private long began = -1;
    private java.util.function.Consumer<net.minecraftforge.event.level.ChunkDataEvent.Save> saves;

    static boolean selected(String cut) { return cut != null && cut.startsWith(PREFIX); }

    EscrowLoaderCutProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Matrix uses only per-device gated admission");
        this.server = server; this.cut = cut; this.recovery = recovery;
        bi = cut.endsWith("_BI"); boundary = boundary(cut);
        boolean unload = boundary.equals("R1_UNKNOWN_UNLOAD");
        origin = unload ? new BlockPos(2048, 100, 2048) : ORIGIN;
        source = unload ? origin.offset(2, 0, 0) : A;
        unloadArea = unload ? new Area(server.overworld(), 128, 128) : null;
        if (unloadArea != null) { unloadArea.hold(); } // Fixed fixture setup, never a recovery-request position.
        chain = boundary.matches("CHAIN_.*");
        creation = boundary.startsWith("N");
        storage = !recovery && EscrowLoaderStorageProbe.selected(boundary)
                ? new EscrowLoaderStorageProbe(server, cut, boundary, this::pause, this::unloadUnknown) : null;
        EscrowTags.require((EscrowLoaderStorageProbe.selected(boundary) || boundary.matches("N[01]|N2_(DEFINITE|UNKNOWN)|N3_(FULL|ELIGIBILITY|NOT_LOADED|PREFLIGHT)|K([0-9]|10)|K2_ACK|K3_DEBIT|K6_FORCED|K10_ACK|C3_FROZEN|C4_BEFORE|C4_AFTER|C6_BEFORE|C6_AFTER|CHAIN_(PREFIX|PROVED|REPLACED|RELEASED)"))
                && (!bi || boundary.matches("K[269]|CHAIN_REPLACED")), "Unknown loader cut");
        if (!recovery && (primeTerrain(boundary) || chain)) {
            saves = event -> {
                if (step == 0 || event.getLevel() != server.overworld() || !event.getChunk().getPos().equals(new ChunkPos(A))) { return; }
                var trace = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                        .filter(frame -> frame.getClassName().contains("ChunkMap") || frame.getClassName().contains("Escrow"))
                        .map(StackTraceElement::toString).toList();
                var saved = event.getData().getList("block_entities", 10).stream().map(tag -> (CompoundTag) tag)
                        .filter(tag -> tag.getInt("x") == A.getX() && tag.getInt("y") == A.getY() && tag.getInt("z") == A.getZ())
                        .findFirst().orElseThrow().getCompound("arce_fluid_fuel_loader");
                int amount = saved.getCompound("tank").getInt("Amount");
                long epoch = saved.getCompound("resource_escrow").getLong("resource_epoch");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_SAVE cut={} tick={} stores={} first_store={} tank={} epoch={} trace={}",
                        cut, server.getTickCount(), stores, firstStore, amount, epoch, trace);
                if (primeTerrain(boundary) && !armed && !primed
                        && trace.stream().anyMatch(frame -> frame.contains("net.minecraft.server.level.ChunkMap."))) {
                    EscrowTags.require(amount == 5_000 && epoch == 0 && stores == 0, "Priming save was not the unchanged baseline");
                    primed = true;
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PRIMED cut={} tank=5000 epoch=0 stores=0", cut);
                }
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(saves);
        }
    }

    static boolean primeTerrain(String boundary) { return boundary.equals("K5") || boundary.equals("C4_BEFORE"); }

    static String boundary(String cut) {
        String name = cut.substring(PREFIX.length()).replace("_BI", "");
        return switch (name) {
            case "E4L1_BEFORE" -> "K1";
            case "E4L1_REPLACED" -> "K2";
            case "E4L1_ACK" -> "K2_ACK";
            case "E4L2_BEFORE" -> "K8";
            case "E4L2_REPLACED" -> "K9";
            case "E4L2_ACK" -> "K10_ACK";
            case "D2_C0" -> "K0";
            case "D2_C1_BEFORE" -> "K1";
            case "D2_C1_REPLACED" -> "K2";
            case "D2_C1_ACK" -> "K2_ACK";
            case "D2_C2" -> "K3";
            case "D2_C3" -> "C3_FROZEN";
            case "D2_C4_BEFORE" -> "C4_BEFORE";
            case "D2_C4_AFTER" -> "C4_AFTER";
            case "D2_C5_BEFORE" -> "K6";
            case "D2_C5_AFTER" -> "K6_FORCED";
            case "D2_C6_BEFORE" -> "C6_BEFORE";
            case "D2_C6_AFTER" -> "C6_AFTER";
            case "D2_C6_JOINT" -> "K7";
            case "D2_C7_BEFORE" -> "K8";
            case "D2_C7_REPLACED" -> "K9";
            case "D2_C7_ACK" -> "K10_ACK";
            case "D2_C8" -> "K10";
            case "C1" -> "CHAIN_PREFIX";
            case "C2" -> "CHAIN_PROVED";
            case "C2_PARTIAL_REPLACED" -> "CHAIN_REPLACED";
            case "C2_PARTIAL_RELEASED" -> "CHAIN_RELEASED";
            default -> name;
        };
    }

    /** Generation selection only; the existing ambiguous committer still performs the injection behind its gate. */
    static boolean baselineCandidate(CompoundTag payload) {
        return payload.getLong("writer_generation") >= 1 && payload.getLong("issued_through") == 0
                && payload.getList("records", 10).isEmpty();
    }

    void startTick() {
        if (firstTick) { return; }
        firstTick = true;
        var delegate = EscrowRuntime.of(server).propulsion(server);
        var data = ResourceEscrowSavedData.existing(server);
        long issued = data == null ? 0 : data.ledger().issuedThrough();
        int records = data == null ? 0 : data.ledger().records().size();
        int pending = delegate.authority().pendingCount();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIRST cut={} recovery={} delegate={} issued={} records={} pending={}",
                cut, recovery, delegate.state(), issued, records, pending);
        EscrowTags.require(pending == records, "Restart reservations do not match durable records");
        if (!recovery) { EscrowTags.require(data == null, "Matrix needs a fresh world"); }
    }

    void checkpoint(String name) {
        if (done || paused.get() || !recovery && step == 0) { return; }
        if (name.equals("LOADER_PREPARATIONS_QUEUED")) {
            // Close only the fixture's admission gate, not any transaction decision or lease.
            var data = ResourceEscrowSavedData.existing(server);
            for (var loader : loaders) {
                if (!data.bind(server).unresolvedNaming(device(loader)).isEmpty()) {
                    EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), false);
                }
            }
            if (recovery) { return; }
            if (armed && !pendingObserved) {
                EscrowTags.require(loader().escrowProgress() == FluidFuelLoaderStatus.PREPARING
                        && loader().displayStatus() == FluidFuelLoaderStatus.PREPARING, "Pending baseline/L1 is not PREPARING");
                pendingObserved = true; observe("pending");
            }
            if (armed && (boundary.equals("N0") || boundary.equals("K0") && data.ledger().writerGeneration() > 0)) {
                pause(name); return;
            }
        }
        if (recovery || !armed) { return; }
        if (storage != null) { storage.checkpoint(name, loader(), rocket); }
        if (chain) { chainCheckpoint(name); }
        if (boundary.equals("N2_DEFINITE") && (name.equals("BASELINE_STAGED") || name.equals("K8"))) {
            // A definite failure cancels its request, so a later baseline attempt has a later writer generation.
            EscrowReleaseProbe.definiteFailure();
        }
        if (boundary.startsWith("N2_") && name.equals(boundary.equals("N2_UNKNOWN") ? "WRITE_UNKNOWN" : "WRITE_FAILED_BEFORE_REPLACE")) {
            baselineFailure(name); return;
        }
        if (server.isSameThread() && (name.startsWith("C2_") || name.equals("C3_FROZEN") || name.equals("C6_JOINT_READBACK")
                || name.equals("C7_ACKNOWLEDGED") || name.equals("C8_RELEASED"))) { observe(name); }
        if (name.equals("C4_SOURCE_SELECTED") || name.equals("C4_DESTINATION_SELECTED")) {
            stores++;
            if (stores == 1) { firstStore = name.equals("C4_SOURCE_SELECTED") ? "SOURCE" : "DESTINATION"; }
        }
        boolean match = switch (boundary) {
            case "N1" -> name.equals("BASELINE_REPLACED");
            case "K1", "K2", "K8", "K9" -> name.equals(boundary);
            case "K2_ACK" -> name.equals("C2_SOURCE_BEFORE");
            case "K3_DEBIT" -> name.equals("C2_SOURCE_AFTER");
            case "K3" -> name.equals("C2_DESTINATION_AFTER");
            // Observe whichever side the unmodified driver actually stores first. Never reorder its map or proofs.
            case "K4", "K5" -> stores == 1 && name.equals("BARRIER_FORCE_AFTER");
            case "K6" -> stores == 2 && name.equals("BARRIER_FORCE_BEFORE");
            case "K6_FORCED" -> stores == 2 && name.equals("BARRIER_FORCE_AFTER");
            case "K7" -> name.equals("C6_JOINT_READBACK");
            case "K10_ACK" -> name.equals("C7_ACKNOWLEDGED");
            case "K10" -> name.equals("C8_RELEASED");
            case "C3_FROZEN" -> name.equals("C3_FROZEN");
            case "C4_BEFORE" -> stores == 2 && name.equals("BARRIER_STORE_BEFORE");
            case "C4_AFTER" -> stores == 2 && name.equals("BARRIER_STORE_AFTER");
            case "C6_BEFORE" -> stores == 2 && name.equals("BARRIER_READBACK_BEFORE");
            case "C6_AFTER" -> stores == 2 && name.equals("BARRIER_READBACK_AFTER");
            default -> false;
        };
        if (match) { pause(name); }
    }

    private void chainCheckpoint(String name) {
        var data = ResourceEscrowSavedData.existing(server);
        if (name.equals("C2_DESTINATION_AFTER") && !secondQueued && rocket.escrowSection().epoch() == 1) {
            secondQueued = true;
            open(loaders.get(1)); // A real competing request, not a fabricated operation or proof.
            EscrowRuntime.of(server).fills(server).visit();
            EscrowTags.require(loaders.get(1).lastAttempt() == FluidFuelLoaderStatus.PREPARING,
                    "Second real loader did not enter the chain");
        }
        if (name.equals("C2_SOURCE_AFTER") && secondQueued && data.ledger().records().size() == 2
                && section(loaders.get(1)).epoch() == 1) {
            EscrowTags.require(rocket.escrowSection().epoch() == 1 && amount(loaders.get(0)) == 4_000
                    && amount(loaders.get(1)) == 4_000, "Unequal chain prefixes not reached");
            observe("chain_unequal");
            if (boundary.equals("CHAIN_PREFIX")) { pause(name); }
        }
        if (name.equals("C6_JOINT_READBACK") && data.ledger().records().size() == 2) {
            EscrowTags.require(rocket.escrowSection().epoch() == 2 && rocket.escrowSection().markers().size() == 2,
                    "Chain proof did not cover the latest frontier");
            var op = data.ledger().records().get(1);
            EscrowTags.require(oppositeRefused(op), "Opposite-direction request was accepted");
            latestProved = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_CHAIN cut={} latest=2 reverse_refused=true records=2", cut);
            if (boundary.equals("CHAIN_PROVED")) { pause(name); }
        }
        // A partial L2 still contains B, so the existing writer calls its replacement K2, not K9.
        if (latestProved && boundary.equals("CHAIN_REPLACED") && name.equals("K2")) { pause(name); }
        if (latestProved && name.equals("C8_RELEASED") && data.ledger().records().size() == 1) {
            var remaining = data.ledger().records().get(0);
            EscrowTags.require(remaining.sequence() == 2 && data.bind(server).logicalLocked(rocket.assemblyTransactionId().orElseThrow())
                    && !loaders.get(0).escrowLocked() && loaders.get(1).escrowLocked()
                    && rocket.escrowSection().epoch() == 2 && rocket.escrowSection().markers().size() == 1,
                    "Partial retirement released the suffix or changed its frontier");
            observe("chain_partial_release");
            if (boundary.equals("CHAIN_RELEASED")) { pause(name); }
        }
    }

    /** A reversed native request must fail validation, without the model-only constructor inventing a binding. */
    static boolean oppositeRefused(EscrowOperation op) {
        try {
            new EscrowOperation(UUID.randomUUID(), op.sequence(), op.owner(), op.createdTick(),
                    EscrowOperation.Phase.PREPARED, EscrowReason.NONE, op.payload(), op.payloadHash(),
                    op.destination(), op.source(), op.guard(), op.kind(), op.settlement(), op.nativeBinding());
            return false;
        } catch (IllegalArgumentException refused) { return true; }
    }

    private void pause(String hook) {
        if (!paused.compareAndSet(false, true)) { return; }
        if (storage != null) { storage.close(); }
        if (saves != null) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(saves); saves = null; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PAUSED cut={} hook={} first_store={} thread={}",
                cut, hook, firstStore, Thread.currentThread().getName());
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Loader matrix forced stop did not arrive within 30 seconds");
    }

    void tick() {
        var level = server.overworld();
        if (done || paused.get()) { return; }
        if (began >= 0) { EscrowTags.require(server.getTickCount() - began <= 2_400, "Loader matrix exceeded its bounded wait"); }
        if (unknownCandidate != null && observeUnloaded()) { return; }
        if (!level.areEntitiesLoaded(new ChunkPos(origin).toLong()) || !level.isPositionEntityTicking(origin)) { return; }
        if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Loader matrix exceeded its bounded wait");
        if (recovery) { recover(); return; }
        if (storage != null && step > 0) { storage.tick(loader(), rocket); }
        if (step == 0) {
            var owner = UUID.randomUUID();
            rocket = rocket(level, origin, owner, bi ? bipropellant(2_000, 2_000) : mono(boundary.equals("N3_PREFLIGHT") ? 8_000 : 2_000));
            EscrowTags.require(level.addFreshEntity(rocket), "Native rocket publication failed");
            loaders.add(FluidFuelLoaderEscrowFixtures.loader(level, source, owner, tank(bi ? HYDROGEN : ROCKET_FUEL, 5_000), binding(rocket)));
            if (bi || chain) { loaders.add(FluidFuelLoaderEscrowFixtures.loader(level, B, owner, tank(bi ? OXYGEN : ROCKET_FUEL, 5_000), binding(rocket))); }
            if (boundary.equals("N3_PREFLIGHT")) {
                for (var pos : CAPACITY_LOADERS) {
                    loaders.add(FluidFuelLoaderEscrowFixtures.loader(level, pos, owner, tank(ROCKET_FUEL, 5_000), binding(rocket)));
                }
            }
            EscrowTags.require(server.saveEverything(false, true, true), "Fixture baseline refused");
            step = 1; armed = (chain || !bi) && !primeTerrain(boundary);
            if (primeTerrain(boundary)) {
                // A normal save before admission starts vanilla's existing 10-second chunk-save cooldown. Never
                // suppress saves, change that cooldown or alter dirty state after admission. This prevents the
                // fixture's first incremental save from racing the destination-first barrier with the source AFTER.
                level.getChunkAt(A).setUnsaved(true); return;
            }
            if (boundary.equals("N3_PREFLIGHT")) { preflight(); return; }
            if (boundary.startsWith("N3_")) { refuse(); return; }
            open(loaders.get(0));
        } else if (primeTerrain(boundary) && !armed && primed) {
            step = 2; armed = true; open(loader());
        } else if (boundary.equals("N2_DEFINITE") && failures > 0 && failures < 3) {
            var data = ResourceEscrowSavedData.existing(server);
            if (data.bind(server).writerIdle() && data.bind(server).unresolvedNaming(device(loader())).isEmpty()) { open(loader()); }
        } else if (bi && !chain && step == 1 && settled(1)) {
            // Establish the primary fill normally, before cutting the oxidizer fill. This is not a chain test.
            EscrowTags.require(server.saveEverything(false, true, true), "Bipropellant primary baseline refused");
            step = 2; armed = true; open(loaders.get(1));
        }
    }

    private void unloadUnknown() {
        if (unknownCandidate != null) { return; }
        unknownCandidate = ResourceEscrowSavedData.existing(server).bind(server).unresolvedNaming(device(loader())).get(0);
        unloadArea.release(); // Drop only this fixture's ticket. The production queue/writer keeps its own work.
    }

    private boolean observeUnloaded() {
        var level = server.overworld();
        if (level.getChunkSource().getChunkNow(128, 128) != null || level.areEntitiesLoaded(new ChunkPos(origin).toLong())) {
            EscrowTags.require(unloadedAt < 0, "Pending UNKNOWN work forced the unloaded fixture back in"); return false;
        }
        if (unloadedAt < 0) { unloadedAt = server.getTickCount(); }
        if (server.getTickCount() - unloadedAt < 20) { return true; }
        var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
        EscrowTags.require(loader().isRemoved() && rocket.isRemoved() && coordinator.writerUnknown()
                && !coordinator.writerIdle() && data.ledger().issuedThrough() == 0 && data.ledger().records().isEmpty()
                && coordinator.admissionAuthority().pendingCount() == 1
                && coordinator.unresolvedNaming(device(loader())).equals(List.of(unknownCandidate)),
                "Unload discarded, changed or acknowledged UNKNOWN L1");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_UNLOADED cut={} ticks=20 records=0 pending=1 issued=0 same_candidate=true", cut);
        pause("R1_UNKNOWN_UNLOAD"); return true;
    }

    private void baselineFailure(String outcome) {
        var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
        failures++;
        boolean unknown = boundary.equals("N2_UNKNOWN");
        EscrowTags.require(failures <= 3 && data.ledger().writerGeneration() == 0 && data.ledger().issuedThrough() == 0
                && data.ledger().records().isEmpty() && coordinator.creationBaselinePending()
                && coordinator.writerUnknown() == unknown && coordinator.writerIdle() != unknown
                && coordinator.admissionAuthority().pendingCount() == (unknown ? 1 : 0)
                && amount(loader()) == 5_000 && bank(rocket, false) == 0 && section(loader()).epoch() == 0
                && rocket.escrowSection().epoch() == 0, "Baseline failure changed authority or resources");
        if (failures == 3) {
            EscrowTags.require(coordinator.writerUnavailable(), "Three unresolved failures did not make writer unavailable");
            // Definite failure has no named preparation left; query the real attempt with its gate open.
            if (!unknown) {
                EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader()), true);
                EscrowTags.require(loader().attempt() == FluidFuelLoaderStatus.READY, "Unavailable attempt did not enter queue");
                EscrowRuntime.of(server).fills(server).visit();
                EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader()), false);
            }
            EscrowTags.require(loader().displayStatus() == FluidFuelLoaderStatus.LEDGER_UNAVAILABLE,
                    "Unavailable baseline did not show LEDGER_UNAVAILABLE");
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_BASELINE_FAILURE cut={} failure={} outcome={} pending={} unavailable={} status={}",
                cut, failures, outcome, coordinator.admissionAuthority().pendingCount(), coordinator.writerUnavailable(), loader().displayStatus());
        observe("baseline_failure");
        if (failures == 3) { pause(outcome); }
    }

    private void preflight() {
        var runtime = EscrowRuntime.of(server);
        for (var extra : loaders.subList(1, loaders.size())) {
            open(extra); runtime.fills(server).visit(); runtime.loaderAdmissionForTests(server, device(extra), false);
            EscrowTags.require(extra.lastAttempt() == FluidFuelLoaderStatus.PREPARING, "Capacity filler was not admitted");
        }
        var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
        var before = counts(server, rocket.assemblyTransactionId().orElseThrow());
        var source = loader().saveWithFullMetadata(); var destination = EscrowNativeEnvelope.rocket(rocket);
        EscrowTags.require(before.pending() == 5 && before.next() == 6 && data.ledger().writerGeneration() == 0
                && data.ledger().issuedThrough() == 0 && coordinator.writerIdle(), "Fixture did not reach five real queued fills");
        open(loader()); runtime.fills(server).visit(); runtime.loaderAdmissionForTests(server, device(loader()), false);
        EscrowTags.require(loader().lastAttempt() == FluidFuelLoaderStatus.ESCROW_FULL && before.equals(counts(server, rocket.assemblyTransactionId().orElseThrow()))
                && source.equals(loader().saveWithFullMetadata()) && destination.equals(EscrowNativeEnvelope.rocket(rocket))
                && coordinator.unresolvedNaming(device(loader())).isEmpty(), "Candidate preflight changed resources, issued a sequence or reserved a slot");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PREFLIGHT cut={} admitted=5 refused=ESCROW_FULL next=6 pending=5 issued=0 records=0", cut);
        observe("preflight_refused"); pause("N3_PREFLIGHT");
    }

    private FluidFuelLoaderBlockEntity loader() { return loaders.get(bi && !chain ? 1 : 0); }
    private void open(FluidFuelLoaderBlockEntity loader) {
        EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), true);
        EscrowTags.require(loader.attempt() == FluidFuelLoaderStatus.READY, "Fixture admission did not enter the fair queue");
    }

    private void refuse() {
        var runtime = EscrowRuntime.of(server); var authority = runtime.propulsion(server).authority();
        var source = loader().saveWithFullMetadata(); var destination = EscrowNativeEnvelope.rocket(rocket);
        var pending = new ArrayList<EscrowAdmissionAuthority.Pending>();
        EscrowAdmissionAuthority.Delegate bay = null;
        if (boundary.equals("N3_FULL")) {
            for (int i = 0; i < EscrowLimits.OPERATIONS; i++) {
                pending.add(new EscrowAdmissionAuthority.Pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        EscrowAdmissionAuthority.Consumer.BAY));
            }
            authority.registerIndex(EscrowAdmissionAuthority.Consumer.BAY, () -> new EscrowAdmissionAuthority.Index(true, pending));
        } else if (boundary.equals("N3_NOT_LOADED")) {
            bay = authority.register(EscrowAdmissionAuthority.Consumer.BAY);
        }
        runtime.loaderAdmissionForTests(server, device(loader()), true);
        FluidFuelLoaderStatus status;
        if (boundary.equals("N3_ELIGIBILITY")) {
            // A transient rider is a real eligibility refusal; it never changes either saved resource envelope.
            var rider = new ArmorStand(server.overworld(), 64, 100, 64);
            EscrowTags.require(rider.startRiding(rocket, true), "Eligibility rider setup refused");
            status = loader().attempt(); rider.stopRiding(); rider.discard();
            EscrowTags.require(status == FluidFuelLoaderStatus.ROCKET_BUSY, "Occupied rocket passed eligibility");
        } else {
            EscrowTags.require(loader().attempt() == FluidFuelLoaderStatus.READY, "Refusal did not visit the queue");
            runtime.fills(server).visit(); status = loader().lastAttempt();
            EscrowTags.require(status == (bay == null ? FluidFuelLoaderStatus.ESCROW_FULL : FluidFuelLoaderStatus.LEDGER_UNAVAILABLE),
                    "Wrong shared-authority refusal");
        }
        runtime.loaderAdmissionForTests(server, device(loader()), false);
        EscrowTags.require(source.equals(loader().saveWithFullMetadata()) && destination.equals(EscrowNativeEnvelope.rocket(rocket))
                && ResourceEscrowSavedData.existing(server) == null && runtime.fills(server).size() == 0
                && !runtime.fills(server).preparing() && authority.pendingCount() == pending.size(), "Refusal changed resources or reservations");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_REFUSED cut={} status={} bay={} pending={}",
                cut, status, bay == null ? "TEST_INDEX" : bay.state(), authority.pendingCount());
        pending.clear(); if (bay != null) { bay.noFile(); }
        EscrowTags.require(authority.pendingCount() == 0, "Fixture bay left a reservation");
        observe("refused"); pause(boundary);
    }

    private void recover() {
        if (rocket == null) {
            var found = server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(origin).inflate(8));
            if (found.isEmpty() || !(server.overworld().getBlockEntity(source) instanceof FluidFuelLoaderBlockEntity a)) { return; }
            EscrowTags.require(found.size() == 1, "Native restart duplicated rocket");
            rocket = found.get(0); loaders.add(a);
            if (bi || chain) { loaders.add((FluidFuelLoaderBlockEntity) server.overworld().getBlockEntity(B)); }
            if (boundary.equals("N3_PREFLIGHT")) {
                for (var pos : CAPACITY_LOADERS) { loaders.add((FluidFuelLoaderBlockEntity) server.overworld().getBlockEntity(pos)); }
            }
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (creation && !retrying) {
            EscrowTags.require(data == null || data.ledger().records().isEmpty() && data.ledger().issuedThrough() == 0,
                    "Creation restart issued a sequence");
            EscrowTags.require(amount(loader()) == 5_000 && bank(rocket, false) == 0 && section(loader()).epoch() == 0
                    && section(loader()).markers().isEmpty() && rocket.escrowSection().epoch() == 0
                    && rocket.escrowSection().markers().isEmpty()
                    && EscrowRuntime.of(server).propulsion(server).authority().pendingCount() == 0
                    && loaders.stream().allMatch(l -> amount(l) == 5_000 && section(l).epoch() == 0 && section(l).markers().isEmpty()),
                    "Creation restart changed resources");
            observe("creation_restart");
            if (Boolean.getBoolean("advancedrocketrycommunity.escrowLoaderRetry")) {
                retrying = true; open(loader()); return;
            }
            done = true; result(); return;
        }
        long total = loaders.stream().mapToLong(FluidFuelLoaderEscrowFixtures::amount).sum()
                + bank(rocket, false) + bank(rocket, true) + held(server, rocket, loaders);
        EscrowTags.require(total == loaders.size() * 5_000L, "Native restart violated raw control total");
        long expected = boundary.equals("K0") || boundary.equals("K1") || boundary.startsWith("E4L1_")
                || boundary.equals("R1_UNKNOWN_UNLOAD") ? 0 : bi || chain ? 2 : 1;
        if (server.getTickCount() - began >= 100 && settled(expected)) { observe("recovered"); done = true; result(); }
    }

    private boolean settled(long fills) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.operational() || !data.bind(server).writerIdle() || !data.ledger().records().isEmpty()
                || !rocket.escrowSection().markers().isEmpty() || loaders.stream().anyMatch(l -> !section(l).markers().isEmpty())) { return false; }
        if (data.ledger().issuedThrough() != fills) { return false; }
        EscrowTags.require(bank(rocket, false) == (chain && !bi ? fills * 1_000 : fills > 0 ? 1_000 : 0)
                && bank(rocket, true) == (bi && fills > 1 ? 1_000 : 0)
                && rocket.escrowSection().epoch() == fills && data.bind(server).admissionAuthority().pendingCount() == 0,
                "Retirement changed bank totals, epochs or reservations");
        for (int i = 0; i < loaders.size(); i++) {
            int moved = fills > i ? 1 : 0;
            EscrowTags.require(amount(loaders.get(i)) == 5_000 - moved * 1_000 && section(loaders.get(i)).epoch() == moved,
                    "Retirement changed loader amount or epoch");
        }
        return true;
    }

    private void observe(String phase) {
        var data = ResourceEscrowSavedData.existing(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_STATE cut={} phase={} tanks={} banks={},{} epochs={} rocket_epoch={} source_markers={} rocket_markers={} issued={} records={} pending={} status={}",
                cut, phase, loaders.stream().map(FluidFuelLoaderEscrowFixtures::amount).toList(), bank(rocket, false), bank(rocket, true),
                loaders.stream().map(l -> section(l).epoch()).toList(), rocket.escrowSection().epoch(),
                loaders.stream().map(l -> section(l).markers().size()).toList(), rocket.escrowSection().markers().size(),
                data == null ? 0 : data.ledger().issuedThrough(), data == null ? 0 : data.ledger().records().size(),
                EscrowRuntime.of(server).propulsion(server).authority().pendingCount(), loader().displayStatus());
    }

    private void result() { AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_RESULT cut={}", cut); }
}
