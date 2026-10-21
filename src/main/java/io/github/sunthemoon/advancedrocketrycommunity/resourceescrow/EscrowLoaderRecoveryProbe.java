package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** W107B (Root decision R36): gated fixtures of the loader matrix. They write, observe or pause; they decide nothing. */
final class EscrowLoaderRecoveryProbe {
    private EscrowLoaderRecoveryProbe() { }

    /**
     * R36 item 1: an autosave arriving at C1's stop point. B's terrain chunk (which also holds A) and the rocket's entity
     * chunk are written through vanilla's own serializer, Save event and workers, then joined, before the forced stop.
     */
    static void prefixSave(MinecraftServer server, String cut, List<FluidFuelLoaderBlockEntity> loaders, RocketEntity rocket) {
        EscrowTestEndpoints.requireEnabled(server);
        var level = server.overworld(); var terrain = new ChunkPos(loaders.get(1).getBlockPos()); var entities = rocket.chunkPosition();
        EscrowReleaseProbe.newerSave(level, loaders.get(1).getBlockPos());
        EscrowReleaseProbe.newerEntitySave(level, entities);
        EscrowReleaseProbe.flushFixtureSaves(level);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIXTURE_SAVE cut={} terrain={},{} entities={},{} tanks={} epochs={} source_markers={} bank={} rocket_epoch={} rocket_markers={}",
                cut, terrain.x, terrain.z, entities.x, entities.z, loaders.stream().map(FluidFuelLoaderEscrowFixtures::amount).toList(),
                loaders.stream().map(l -> section(l).epoch()).toList(), loaders.stream().map(l -> section(l).markers().size()).toList(),
                bank(rocket, false), rocket.escrowSection().epoch(), rocket.escrowSection().markers().size());
    }

    /**
     * W107C (R36 item 2): R3_LEDGER_BEHIND and R3_LEDGER_REPLACED keep the creation baseline's exact bytes, an older copy
     * captured earlier in the same run. The writer's own IO thread copies its file right after the generation-1 move, so
     * no other reader ever holds the ledger open across a replacement. A failed copy is logged, never thrown into the
     * writer; the harness then refuses the cut for its missing capture.
     */
    static void captureLedger(MinecraftServer server, String cut, String name) {
        if (!name.equals("BASELINE_REPLACED") || !cut.equals("LF_M_R3_LEDGER_BEHIND") && !cut.equals("LF_M_R3_LEDGER_REPLACED")
                || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) { return; }
        var target = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("arce_w107c_fixture")
                .resolve("ledger-generation-1.dat");
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(ResourceEscrowSavedData.file(server), target);
            var bytes = java.nio.file.Files.readAllBytes(target);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_LEDGER_CAPTURED cut={} generation=1 bytes={} sha256={}", cut, bytes.length,
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_LOADER_MATRIX_LEDGER_CAPTURE_FAILED cut={} failure={}", cut, failure.toString());
        }
    }

    /**
     * W107C (Root decision R43 items 3 and 5): the restart observation of a cut whose fill must stay held, separate from
     * the matrix's settlement-expecting recovery. E2_EXTERNAL keeps its durable L1, STALLED with the persisted refusal; an
     * R3 endpoint edit reaches a persisted manual REPAIR reason; an R3 ledger edit leaves both endpoints locked by orphan
     * markers, with no record that could carry a reason. It repairs nothing, creates no ledger, loads no chunk and opens
     * no lock; its one fill attempt must be refused unchanged. Reasons are logged as observed, for the harness to compare.
     */
    static final class Hold {
        /** R3_ENDPOINT_RELOCATED's offline edit moves loader A's block and root from x 66 to x 68. */
        static final BlockPos RELOCATED = new BlockPos(68, 100, 64);
        private final MinecraftServer server;
        private final String cut, name;
        private final BlockPos origin, source;

        Hold(MinecraftServer server, String cut, BlockPos origin, BlockPos source) {
            EscrowTestEndpoints.requireEnabled(server);
            this.server = server; this.cut = cut; name = cut.substring(EscrowLoaderCutProbe.PREFIX.length());
            this.origin = origin; this.source = source;
        }

        static boolean selected(String cut) { return cut.equals("LF_M_E2_EXTERNAL") || cut.startsWith("LF_M_R3_"); }

        /** True once the held state is observed and logged; {@code elapsed} counts ticks since the fixture slot ticked. */
        boolean tick(long elapsed) {
            var level = server.overworld();
            var rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(origin).inflate(8));
            if (rockets.isEmpty()) { return timeout(elapsed, "rocket"); }
            EscrowTags.require(rockets.size() == 1, "Native restart duplicated rocket");
            var rocket = rockets.get(0);
            boolean moved = name.equals("R3_ENDPOINT_RELOCATED");
            var loader = level.getBlockEntity(moved ? RELOCATED : source) instanceof FluidFuelLoaderBlockEntity found ? found : null;
            if (moved || name.equals("R3_ENDPOINT_MISSING")) {
                EscrowTags.require(level.getBlockEntity(source) == null, "The edited source position still holds a block entity");
            }
            if (name.equals("E2_EXTERNAL")) { return stalled(rocket, loader, elapsed); }
            return name.startsWith("R3_LEDGER_") ? orphan(rocket, loader, elapsed) : repair(rocket, loader, elapsed);
        }

        private boolean stalled(RocketEntity rocket, FluidFuelLoaderBlockEntity loader, long elapsed) {
            var data = ResourceEscrowSavedData.existing(server); var barrier = EscrowRuntime.of(server).barrier();
            EscrowTags.require(data != null && loader != null, "Held restart lost its ledger or loader");
            var coordinator = data.bind(server); var records = data.ledger().records();
            EscrowTags.require(amount(loader) + bank(rocket, false) + held(server, rocket, List.of(loader)) == 5_000
                    && records.size() == 1 && data.ledger().issuedThrough() == 1, "Held restart violated the control total or retired");
            var op = records.get(0);
            if (barrier.refusals() + barrier.ioFailures() == 0 || !coordinator.writerIdle() || op.phase() != EscrowOperation.Phase.STALLED) {
                return timeout(elapsed, op.phase() + "/" + op.reason());
            }
            locked(coordinator, op, loader, rocket);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_HELD cut={} reason={} phase={} records=1 issued=1 pending=1 refusals={} io_failures={} tank={} bank={}",
                    cut, op.reason(), op.phase(), barrier.refusals(), barrier.ioFailures(), amount(loader), bank(rocket, false));
            return true;
        }

        private boolean repair(RocketEntity rocket, FluidFuelLoaderBlockEntity loader, long elapsed) {
            var data = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(data != null && (loader == null) == name.equals("R3_ENDPOINT_MISSING"), "Repair restart lost its ledger or loader");
            var coordinator = data.bind(server); var records = data.ledger().records();
            EscrowTags.require(records.size() == 1 && data.ledger().issuedThrough() == 1, "Repair restart retired, replayed or issued");
            var op = records.get(0);
            if (!coordinator.writerIdle() || op.phase() != EscrowOperation.Phase.REPAIR || elapsed < 100) {
                return timeout(elapsed, op.phase() + "/" + op.reason());
            }
            EscrowTags.require(op.reason().manual(), "REPAIR without a manual reason");
            locked(coordinator, op, loader, rocket);
            var status = loader == null ? "NONE" : attempt(loader, rocket).name();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_REPAIR cut={} reason={} phase=REPAIR records=1 issued=1 pending=1 loader={} tank={} loader_markers={} rocket_markers={} bank={} attempt={}",
                    cut, op.reason(), loader == null ? "ABSENT" : loader.getBlockPos().getX(), loader == null ? -1 : amount(loader),
                    loader == null ? -1 : section(loader).markers().size(), rocket.escrowSection().markers().size(), bank(rocket, false), status);
            return true;
        }

        /** The durable record keeps both locks and its reservation, and its endpoints keep their applied prefixes. */
        private void locked(EscrowCoordinator coordinator, EscrowOperation op, FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
            EscrowTags.require(coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                    && !coordinator.reconciliationCheckpointProved(op.id()) && coordinator.admissionAuthority().pendingCount() == 1
                    && !RocketEscrowGate.idle(rocket) && rocket.escrowSection().markers().size() == 1 && rocket.escrowSection().epoch() == 1
                    && bank(rocket, false) == 1_000 && (loader == null || loader.escrowLocked() && section(loader).markers().size() == 1),
                    "A held record released, retired or changed an endpoint");
        }

        private boolean orphan(RocketEntity rocket, FluidFuelLoaderBlockEntity loader, long elapsed) {
            if (loader == null) { return timeout(elapsed, "loader"); }
            var data = ResourceEscrowSavedData.existing(server); var section = section(loader);
            EscrowTags.require(section.markers().size() == 1 && section.ledger() != null && section.epoch() == 1 && amount(loader) == 4_000
                    && rocket.escrowSection().markers().size() == 1 && rocket.escrowSection().epoch() == 1 && bank(rocket, false) == 1_000
                    && loader.escrowLocked() && !RocketEscrowGate.idle(rocket), "Orphan endpoints were released, repaired or changed");
            long issued = data == null ? 0 : data.ledger().issuedThrough(); long marker = section.markers().get(0).sequence();
            String ledger = name.substring("R3_LEDGER_".length());
            boolean expected = switch (ledger) {
                case "MISSING" -> data == null && !java.nio.file.Files.exists(ResourceEscrowSavedData.file(server))
                        && EscrowRuntime.of(server).propulsion(server).state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE;
                case "REPLACED" -> data != null && data.ledger().records().isEmpty() && !data.ledger().id().equals(section.ledger());
                case "BEHIND" -> data != null && data.ledger().records().isEmpty() && data.ledger().id().equals(section.ledger()) && issued < marker;
                default -> false;
            };
            EscrowTags.require(expected, "The restarted ledger is not the offline-edited state");
            if (elapsed < 100) { return false; }
            var status = attempt(loader, rocket);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_ORPHAN cut={} ledger={} binding={} issued={} marker_seq={} records=0 loader_markers=1 rocket_markers=1 tank={} bank={} attempt={}",
                    cut, ledger, data == null ? "NONE" : data.ledger().id().equals(section.ledger()) ? "SAME" : "OTHER", issued, marker,
                    amount(loader), bank(rocket, false), status);
            return true;
        }

        /** One real paced attempt with this device's gate open; it must be refused with nothing queued or changed. */
        private FluidFuelLoaderStatus attempt(FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
            var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
            var before = loader.saveWithFullMetadata(); var rocketBefore = EscrowNativeEnvelope.rocket(rocket);
            long next = data == null ? 1 : data.bind(server).nextSequence(); int pending = runtime.propulsion(server).authority().pendingCount();
            FluidFuelLoaderStatus status;
            runtime.loaderAdmissionForTests(server, device(loader), true);
            try { status = loader.attempt(); }
            finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
            var after = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(status != FluidFuelLoaderStatus.READY && status != FluidFuelLoaderStatus.PREPARING
                    && runtime.fills(server).size() == 0 && before.equals(loader.saveWithFullMetadata())
                    && rocketBefore.equals(EscrowNativeEnvelope.rocket(rocket)) && after == data
                    && (data == null || data.bind(server).nextSequence() == next)
                    && runtime.propulsion(server).authority().pendingCount() == pending, "A held endpoint's attempt entered admission or changed state");
            return status;
        }

        /** The matrix's 2,400-tick bound would throw; report the unreached state instead, so both restarts are collected. */
        private boolean timeout(long elapsed, String state) {
            if (elapsed < 2_300) { return false; }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_HOLD_TIMEOUT cut={} ticks={} state={}", cut, elapsed, state);
            return true;
        }
    }
}
