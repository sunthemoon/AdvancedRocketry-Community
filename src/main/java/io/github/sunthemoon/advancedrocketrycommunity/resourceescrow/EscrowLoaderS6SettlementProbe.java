package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Real-loader operator decisions on a deliberately held, fully applied fill. Release-gated fixture only.
 *
 * <p>W373C (LF_S6_AC_ cases, W357-DESIGN section 7.3): PRUNE_BEFORE stops once the released survivors' markers are
 * pruned in memory, PRUNE_AFTER after that pruning is saved. LOSS and GAIN change the held loader's tank by -100 mB
 * or +100 mB before the operator's preview and stop after O1's replacement, so O1 freezes the signed discrepancy.
 * W391 adds a water substitution, bank edits and one-sided pruning saves using the existing storage helpers.
 */
final class EscrowLoaderS6SettlementProbe {
    static final String BEFORE = "LF_S6_O1_BEFORE", REPLACED = "LF_S6_O1_REPLACED", ACK = "LF_S6_O1_ACK";
    static final String PRUNE_BEFORE = "LF_S6_AC_PRUNE_BEFORE", PRUNE_AFTER = "LF_S6_AC_PRUNE_AFTER";
    static final String LOSS = "LF_S6_AC_LOSS", GAIN = "LF_S6_AC_GAIN";
    static final String SUBSTITUTE = "LF_S6_AC_SUBSTITUTE", BANK_LOSS = "LF_S6_AC_BANK_LOSS", BANK_GAIN = "LF_S6_AC_BANK_GAIN";
    static final String SPLIT_TANK = "LF_S6_AC_PRUNE_SPLIT_TANK", SPLIT_BANK = "LF_S6_AC_PRUNE_SPLIT_BANK";
    static final int ADJUSTMENT = 100;
    private static final List<String> CASES = List.of(BEFORE, REPLACED, ACK,
            "LF_S6_O2_BEFORE_0", "LF_S6_O2_AFTER_0", "LF_S6_O2_BEFORE_1", "LF_S6_O2_AFTER_1",
            "LF_S6_O3_BEFORE", "LF_S6_O3_REPLACED", "LF_S6_O3_ACK", "LF_S6_O4_BEFORE",
            "LF_S6_O4_REPLACED", "LF_S6_O4_ACK", "LF_S6_O3J_BEFORE", "LF_S6_O3J_REPLACED", "LF_S6_O3J_ACK",
            PRUNE_BEFORE, PRUNE_AFTER, LOSS, GAIN, SUBSTITUTE, BANK_LOSS, BANK_GAIN, SPLIT_TANK, SPLIT_BANK);
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private static final BlockPos DEVICE = new BlockPos(66, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery, named;
    private final EscrowLoaderCutProbe setup;
    private final EscrowLoaderS6NamedSetup namedSetup;
    private boolean held, baseline, queued, done, adjusted;
    private volatile boolean writingIntent;
    private volatile String ledgerPhase = "";
    private long began = -1;

    static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }
    static boolean boundary(String cut, String hook) {
        return BEFORE.equals(cut) && "K1".equals(hook) || REPLACED.equals(cut) && "K2".equals(hook)
                || ACK.equals(cut) && "O1_ACK".equals(hook) || changesStore(cut) && "K2".equals(hook);
    }
    /** The signed change of the held loader's tank before the preview; zero for every other case. */
    static int tankDelta(String cut) { return LOSS.equals(cut) ? -ADJUSTMENT : GAIN.equals(cut) ? ADJUSTMENT : 0; }
    static int bankDelta(String cut) { return BANK_LOSS.equals(cut) ? -ADJUSTMENT : BANK_GAIN.equals(cut) ? ADJUSTMENT : 0; }
    static boolean changesStore(String cut) { return tankDelta(cut) != 0 || bankDelta(cut) != 0 || SUBSTITUTE.equals(cut); }
    static boolean prunes(String cut) { return PRUNE_BEFORE.equals(cut) || PRUNE_AFTER.equals(cut) || SPLIT_TANK.equals(cut) || SPLIT_BANK.equals(cut); }
    /** The pruning cuts stop only after release, with no record left and both survivors' markers pruned in memory. */
    static boolean pruned(String cut, int records, boolean released, int loaderMarkers, int rocketMarkers) {
        return prunes(cut) && records == 0 && released && loaderMarkers == 0 && rocketMarkers == 0;
    }
    static boolean manualHoldDurable(EscrowReason reason) {
        // Idle is not acknowledgement: the ordinary ten-tick writer pace can defer this transition.
        if (reason == EscrowReason.NONE) { return false; }
        EscrowTags.require(reason == EscrowReason.OTHER_STATE, "Settlement manual repair setup was not durable");
        return true;
    }
    static boolean completionBoundary(String cut, String hook, String phase) {
        if (!selected(true, cut)) { return false; }
        if (cut.startsWith("LF_S6_O2_")) { return cut.equals("LF_S6_" + hook); }
        return switch (cut) {
            case "LF_S6_O3_BEFORE" -> phase.equals("O3") && hook.equals("K1");
            case "LF_S6_O3_REPLACED" -> phase.equals("O3") && hook.equals("K2");
            case "LF_S6_O3_ACK" -> phase.equals("O3") && hook.equals("O3_AFTER");
            case "LF_S6_O4_BEFORE" -> phase.equals("O4") && hook.equals("K8");
            case "LF_S6_O4_REPLACED" -> phase.equals("O4") && hook.equals("K9");
            case "LF_S6_O4_ACK" -> phase.equals("O4") && hook.equals("O4_AFTER");
            case "LF_S6_O3J_ACK" -> phase.equals("O3") && hook.equals("O3J_AFTER");
            default -> false;
        };
    }
    EscrowLoaderS6SettlementProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Settlement requires its exact per-device release gate");
        this.server = server; this.cut = cut; this.recovery = recovery; named = cut.startsWith("LF_S6_O3J_");
        setup = recovery || named ? null : new EscrowLoaderCutProbe(server, "LF_M_K7", false);
        namedSetup = recovery || !named ? null : new EscrowLoaderS6NamedSetup(server);
    }
    void startTick() {
        if (!held) {
            if (setup != null) { setup.startTick(); }
            if (namedSetup != null) { namedSetup.startTick(); }
        }
    }
    void checkpoint(String hook) {
        if (recovery || done) { return; }
        if (writingIntent && boundary(cut, hook)) { pause(hook); }
        if (held) {
            if (!writingIntent && queued) {
                if (hook.equals("O3_CAPTURE") || hook.equals("O4_CAPTURE")) { ledgerPhase = hook.substring(0, 2); }
                if (completionBoundary(cut, hook, ledgerPhase)) { pause(hook); }
            }
            return;
        }
        if (setup != null) { setup.checkpoint(hook); }
        if (hook.equals("C2_DESTINATION_AFTER")) {
            var data = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(data != null && data.ledger().records().size() == 1, "Settlement setup lost its real fill");
            var coordinator = data.bind(server);
            coordinator.transition(data.ledger().records().get(0).id(), EscrowReason.OTHER_STATE);
            coordinator.persistTransitions(); held = true;
        }
    }
    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 settlement exceeded the existing fixture bound");
        if (named && (recovery || namedSetup.observing())) {
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            EscrowTags.require(moon != null, "Named operator fixture lost its old source Level");
            EscrowLoaderS6NamedSetup.sourceReady(true, moon.getChunkSource().getChunkNow(4096, 4096) == null,
                    !moon.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4096, 4096)));
        }
        if (!recovery && !held) {
            if (setup != null) { setup.tick(); } else { namedSetup.tick(); }
            return;
        }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(DEVICE) || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity loader)) { return; }
        var root = loader.root().orElseThrow();
        if (!(level.getEntity(root.binding().physicalRocket()) instanceof RocketEntity rocket)) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null, "Settlement lost its ledger");
        var coordinator = data.bind(server);
        int tank = 4_000 + (recovery || adjusted ? tankDelta(cut) : 0);
        int bank = 1_000 + (recovery || adjusted ? bankDelta(cut) : 0);
        var fluid = SUBSTITUTE.equals(cut) && (recovery || adjusted) ? net.minecraft.resources.ResourceLocation.tryParse("minecraft:water")
                : net.minecraft.resources.ResourceLocation.tryParse("advancedrocketrycommunity:rocket_fuel");
        EscrowTags.require(amount(loader) == tank && root.tank().fluid().equals(fluid) && bank(rocket, false) == bank && bank(rocket, true) == 0
                && section(loader).epoch() == 1 && rocket.escrowSection().epoch() == 1 && data.ledger().issuedThrough() == 1,
                "Settlement changed its exact resources or epochs");
        if (!coordinator.writerIdle()) { return; }
        if (recovery) {
            if (BEFORE.equals(cut)) {
                EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().tombstones().isEmpty(), "Pre-O1 invented a decision");
                var op = data.ledger().records().get(0);
                EscrowTags.require(op.settlement().isEmpty() && op.reason() == EscrowReason.OTHER_STATE
                        && loader.escrowLocked() && !RocketEscrowGate.idle(rocket), "Pre-O1 released its manual hold");
            } else {
                if (!data.ledger().records().isEmpty() || data.ledger().tombstones().isEmpty()
                        || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
                EscrowTags.require(data.ledger().tombstones().size() == 1 && data.ledger().tombstones().get(0).getBoolean("released")
                        && !loader.escrowLocked() && RocketEscrowGate.idle(rocket), "Settlement failed to release its chosen survivors");
                if (named) { EscrowTags.require(RocketTransferSavedData.get(server).entries().isEmpty(), "O3J retained its named journal"); }
            }
            if (server.getTickCount() - began < 100) { return; }
            done = true;
            // The W373C cases also report the receipt's knowledge; the earlier cases keep their exact line.
            String accounting = cut.startsWith("LF_S6_AC_") ? " accounting=" + data.ledger().tombstones().get(0).getString("accounting_status") : "";
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_O_RESULT cut={} tank={} bank={} epochs=1,1 records={} receipts={} state={}{}",
                    cut, tank, bank, data.ledger().records().size(), data.ledger().tombstones().size(), BEFORE.equals(cut) ? "PRE_O1" : "RELEASED", accounting);
            return;
        }
        if (!baseline) {
            EscrowTags.require(data.ledger().records().size() == 1, "Settlement lost its pending manual hold");
            if (!manualHoldDurable(data.ledger().records().get(0).reason())) { return; }
            if (named) {
                var op = data.ledger().records().get(0);
                var journal = RocketTransferSavedData.get(server);
                var guard = journal.acknowledgedGuard(op.guard().rocket()).orElseThrow();
                EscrowTags.require(guard.transfer() != null && guard.transfer().equals(op.guard().transfer())
                        && guard.recordDigest().equals(op.guard().journalDigest())
                        && journal.find(guard.transfer()).orElseThrow().phase()
                        == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.COMMITTED,
                        "Named operator baseline differs from its acknowledged journal guard");
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_O3J_BASELINE cut={} transfer={} generation={} digest={} phase=COMMITTED",
                        cut, op.guard().transfer(), op.guard().journalGeneration(), op.guard().journalDigest());
            }
            // Seed for the operator experiment, before O1: not a post-cut save or a repair of its oracle.
            EscrowTags.require(server.saveEverything(false, true, true), "Settlement pre-decision baseline save refused");
            baseline = true; return;
        }
        if (queued) {
            var ledger = data.ledger();
            if (!writingIntent && pruned(cut, ledger.records().size(), !ledger.tombstones().isEmpty() && ledger.tombstones().get(0).getBoolean("released"),
                    section(loader).markers().size(), rocket.escrowSection().markers().size())) {
                if (PRUNE_AFTER.equals(cut)) {
                    EscrowTags.require(server.saveEverything(false, true, true), "Settlement pruned-survivor save refused");
                } else if (SPLIT_TANK.equals(cut)) {
                    // Chunk and entity storage are independent: write only the loader side, then join its worker.
                    EscrowReleaseProbe.newerSave(level, DEVICE);
                    level.getChunkSource().chunkMap.worker.synchronize(true).join();
                } else if (SPLIT_BANK.equals(cut)) {
                    EscrowReleaseProbe.newerEntitySave(level, rocket.chunkPosition());
                    level.entityManager.permanentStorage.flush(true);
                }
                pause(cut.substring("LF_S6_AC_".length()));
            }
            return;
        }
        if (changesStore(cut) && !adjusted) {
            // The held loader changes outside any fill; the operator's preview then proposes the signed discrepancy.
            if (bankDelta(cut) != 0) { changeBank(rocket, 1_000 + bankDelta(cut)); }
            else {
                var next = SUBSTITUTE.equals(cut) ? io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank.of(
                        net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 4_000) : root.tank().withAmount(4_000 + tankDelta(cut));
                changeTank(loader, next);
            }
            adjusted = true;
            EscrowTags.require(server.saveEverything(false, true, true), "Settlement changed-tank save refused");
            return;
        }
        var selected = data.ledger().records().get(0).id();
        var operator = EscrowRuntime.of(server).settlement();
        var preview = operator.preview(server, selected, EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "Server");
        writingIntent = true;
        queued = operator.confirm(server, selected, preview.choice(), ACTOR, preview.digest(), committed -> {
            EscrowTags.require(committed, "S6 operator intent was not acknowledged");
            if (boundary(cut, "O1_ACK")) { pause("O1_ACK"); }
            writingIntent = false;
        });
        if (!queued) { writingIntent = false; }
    }
    /**
     * Replaces only the tank of the held loader's root; its identity, binding and escrow section stay as they
     * are. A live protected loader refuses a replaced root (ARCE_FLUID_FUEL_LOADER_LIVE_REPLACEMENT_REFUSED), so the
     * stored block entity is replaced as a whole, as an edit outside the game would do.
     */
    private void changeTank(FluidFuelLoaderBlockEntity loader, io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank tank) {
        var root = loader.root().orElseThrow(); var level = server.overworld();
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.put(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot.KEY,
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot(
                        root.owner(), root.device(), root.binding(), tank, root.escrow()).encode());
        level.removeBlockEntity(DEVICE);
        EscrowTags.require(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity fresh && fresh != loader,
                "W373C loader replacement did not create a fresh block entity");
        var replaced = (FluidFuelLoaderBlockEntity) level.getBlockEntity(DEVICE);
        replaced.load(tag); replaced.setChanged();
        EscrowTags.require(replaced.root().isPresent() && replaced.root().orElseThrow().device().equals(root.device())
                && replaced.root().orElseThrow().equals(new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot(
                        root.owner(), root.device(), root.binding(), tank, root.escrow())),
                "W373C loader replacement was refused");
    }
    /** Edit a detached stored envelope, not the live protected resource setter; retain all identities and markers. */
    private void changeBank(RocketEntity rocket, int amount) {
        var tag = rocket.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var section = rocket.escrowSection();
        changeStoredBank(tag, amount);
        var fresh = (RocketEntity) java.util.Objects.requireNonNull(rocket.getType().create(server.overworld()));
        fresh.load(tag);
        EscrowTags.require(bank(fresh, false) == amount && fresh.getUUID().equals(rocket.getUUID())
                && fresh.escrowSection().equals(section)
                && fresh.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("RocketEntityData").equals(tag.getCompound("RocketEntityData")),
                "W391 bank replacement failed to decode exactly");
        rocket.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        EscrowTags.require(server.overworld().addFreshEntity(fresh), "W391 bank replacement publication refused");
    }
    static void changeStoredBank(net.minecraft.nbt.CompoundTag tag, int amount) {
        var fuel = tag.getCompound("RocketEntityData").getCompound("flight_data").getCompound("fuel");
        fuel.putLong("amount", amount); // Required codec projection of the primary bank, not another resource.
        fuel.getCompound("propulsion").getCompound("primary").putLong("amount", amount);
    }
    private void pause(String hook) {
        if (List.of(SUBSTITUTE, BANK_LOSS, BANK_GAIN, SPLIT_TANK, SPLIT_BANK).contains(cut)) {
            // Report the observed cut only. The stopped world's independent reader supplies the numeric result.
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_AC_RESULT cut={} hook={}", cut, hook);
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_O_PAUSED cut={} hook={}", cut, hook);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 operator forced stop did not arrive within 30 seconds");
    }
}
