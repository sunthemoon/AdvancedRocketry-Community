package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowOperation;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W170-R24 A1: a restarted REGISTERED group whose exact source is absent must pass the present source's escrow
 * eligibility before the bay-resume constructor publishes anything. With an escrow floor naming L and T, an escrow
 * operation locking L, or an unavailable escrow root, the claim holds for repair (BAY_SOURCE_PROTECTED) and no source
 * entity, journal record or receipt changes. Each condition swaps the world's ledger (with its file set aside, as W131's
 * cargo world does), so each runs alone in its own batch and restores the original ledger and file at its end.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayRegisteredEscrowGameTests {
    private BayRegisteredEscrowGameTests() { }

    private enum Condition { FLOOR, LOCKED, UNAVAILABLE }

    @GameTest(template = TEMPLATE, batch = "bay_registered_floor", timeoutTicks = 600)
    public static void anEscrowFloorNamingTheRocketRefusesTheAbsentSourceRebuildWithNoChange(GameTestHelper helper) {
        new Case(helper, Condition.FLOOR).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_registered_locked", timeoutTicks = 600)
    public static void anEscrowOperationLockingTheRocketRefusesTheAbsentSourceRebuildWithNoChange(GameTestHelper helper) {
        new Case(helper, Condition.LOCKED).start();
    }

    @GameTest(template = TEMPLATE, batch = "bay_registered_unavailable", timeoutTicks = 600)
    public static void anUnavailableEscrowRootRefusesTheAbsentSourceRebuildWithNoChange(GameTestHelper helper) {
        new Case(helper, Condition.UNAVAILABLE).start();
    }

    private static final class Case {
        final GameTestHelper helper;
        final Condition condition;
        final UUID owner = UUID.randomUUID();
        final List<ItemStack> packages;
        Flight flight;
        List<BayReceipt> receipts = List.of();
        BayClaim claim;
        String checksum;
        ResourceEscrowSavedData original, installed;
        Path file, backup;
        boolean detached, aside;
        int stage, waits, held;
        boolean closed;

        Case(GameTestHelper helper, Condition condition) {
            this.helper = helper; this.condition = condition;
            packages = List.of(solarPackage(owner), solarPackage(owner));
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 560, "Fixture exceeded its bounded wait at stage " + stage + " " + describe());
                switch (stage) {
                    case 0 -> begin();
                    case 1 -> install();
                    default -> observe();
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        /** T and its REGISTERED group, put as a crashed session left them, and the source gone. */
        void begin() {
            var server = helper.getLevel().getServer();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3)), target = helper.absolutePos(new BlockPos(14, 2, 3));
            if (!helper.getLevel().isPositionEntityTicking(origin) || !helper.getLevel().isPositionEntityTicking(target)) { return; }
            flight = transit(helper, origin, target, owner, packages, RocketEscrowSectionPatch.NEUTRAL);
            RocketTransferSavedData.get(server).put(flight.record());
            checksum = flight.record().checksum();
            var intents = new ArrayList<BayReceipt>();
            long sequence = BayReceiptTestHooks.nextSequence(server);
            for (int i = 0; i < packages.size(); i++) {
                intents.add(BayRegisteredGameTests.sequenced(intent(server, flight.record(), packages.get(i), i), sequence + i));
            }
            var after = BayRegisteredGameTests.consumed(flight.record(), intents);
            var list = new ArrayList<BayReceipt>();
            intents.forEach(intent -> list.add(BayRegisteredGameTests.registered(intent, after.source().contentHash(),
                    after.destination().contentHash())));
            receipts = List.copyOf(list);
            receipts.forEach(receipt -> helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Receipt refused"));
            flight.source().discard();
            stage = 1;
        }

        /** Once T and the receipts are acknowledged, install the escrow condition, then run the restart rows. */
        void install() {
            var server = helper.getLevel().getServer();
            var session = bays(helper).sessionForTests();
            if (!storeIdle(server) || !receipts.stream().allMatch(receipt -> session.acknowledged(receipt.op()))
                    || !RocketTransferSavedData.get(server).actionable(logical())
                    || helper.getLevel().getEntity(flight.record().sourceEntityId()) != null) { return; }
            var runtime = EscrowRuntime.of(server);
            original = ResourceEscrowSavedData.existing(server);
            if (!runtime.replaceLedgerForTests(server, null)) { return; } // the shared ledger still holds work: wait
            detached = true;
            file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            backup = file.resolveSibling(file.getFileName() + ".w170-r24-backup");
            try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } aside = true; }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            var ledger = ledger();
            // A ledger with a floor or a record is a checked baseline only when its file holds exactly its bytes.
            if (condition != Condition.UNAVAILABLE) {
                CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(ledger));
            }
            installed = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger));
            helper.assertTrue(runtime.replaceLedgerForTests(server, installed), "The condition ledger was refused");
            if (condition == Condition.UNAVAILABLE) { installed.stop(); }
            helper.assertTrue(installed.operational() == (condition != Condition.UNAVAILABLE),
                    "The condition ledger's availability is not the one under test");
            var begun = bays(helper).restartForTests(receipts);
            helper.assertTrue(begun.size() == 1, "One restart claim for L");
            claim = begun.get(0);
            helper.assertTrue(claim.stage() == BayClaim.Stage.RESUME, "Wrong restart stage " + claim.stage());
            stage = 2;
        }

        EscrowLedger ledger() {
            RocketTransferRecord record = flight.record();
            return switch (condition) {
                case FLOOR -> new EscrowLedger(UUID.randomUUID(), 1, 1, List.of(), List.of(floor(record)), List.of());
                case LOCKED -> new EscrowLedger(UUID.randomUUID(), 1, 1, List.of(lock(record)), List.of(), List.of());
                case UNAVAILABLE -> EscrowLedger.empty(UUID.randomUUID());
            };
        }

        /** Every tick: no source was published; once held, nothing durable changed and the hold stays for 20 ticks. */
        void observe() {
            var server = helper.getLevel().getServer();
            var level = helper.getLevel();
            helper.assertTrue(level.getEntity(flight.record().sourceEntityId()) == null, "The absent source was rebuilt: " + describe());
            var origin = flight.record().sourceSnapshot().sourceOrigin();
            helper.assertTrue(level.getEntities(ModEntities.ROCKET.get(), new AABB(origin.x() - 2, level.getMinBuildHeight(), origin.z() - 2,
                    origin.x() + 3, level.getMaxBuildHeight(), origin.z() + 3), rocket -> rocket.assemblyTransactionId()
                    .filter(logical()::equals).isPresent()).isEmpty(), "An image of L was published at the source end");
            helper.assertTrue(claim(helper, logical()).isPresent(), "The claim ended: " + describe());
            var session = bays(helper).sessionForTests();
            helper.assertTrue(record().checksum().equals(checksum) && receipts.stream().allMatch(receipt ->
                    session.acknowledged(receipt.op()) && session.current(receipt.op()).orElseThrow().equals(receipt)),
                    "The hold changed T or a receipt");
            if (claim.hold() != BayClaim.Hold.REPAIR) { return; }
            helper.assertTrue(claim.reason().equals("BAY_SOURCE_PROTECTED") && claim.stage() == BayClaim.Stage.RESUME
                    && claim.source().isEmpty(), "The rebuild must be refused before publication: " + describe());
            if (++held >= 20) { close(); helper.succeed(); }
        }

        UUID logical() { return flight.record().logicalRocketId(); }
        RocketTransferRecord record() {
            return RocketTransferSavedData.get(helper.getLevel().getServer()).find(flight.record().transferId()).orElseThrow();
        }
        String describe() { return condition + " " + (claim == null ? "no claim" : claim.stage() + " " + claim.hold() + " " + claim.reason()); }

        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            if (flight == null) { return; }
            bays(helper).forgetForTests(logical());
            try {
                if (installed != null && installed.operational()) { installed.stop(); }
                if (aside) {
                    Files.deleteIfExists(file);
                    if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); }
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            finally {
                if (detached && !EscrowRuntime.of(server).replaceLedgerForTests(server, original)) {
                    throw new IllegalStateException("W170-R24 ledger restore refused");
                }
            }
            var journal = RocketTransferSavedData.get(server);
            journal.find(flight.record().transferId()).ifPresent(current -> journal.remove(current.transferId()));
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
            var source = helper.getLevel().getEntity(flight.record().sourceEntityId());
            if (source != null) { source.discard(); }
            flight.source().discard();
            var session = bays(helper).sessionForTests();
            for (var pkg : packages) {
                session.find(satellite(pkg)).ifPresent(receipt -> BayReceiptTestHooks.remove(server, receipt.op(), outcome -> { }));
            }
        }
    }

    /** A schema-2 escrow floor naming L and T (the shape of {@code RocketTransferFenceGameTests}' isolated floor). */
    static CompoundTag floor(RocketTransferRecord record) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 2); tag.putUUID("rocket_id", record.logicalRocketId());
        tag.putUUID("transfer_id", record.transferId()); tag.putLong("journal_generation", 1); tag.putString("journal_digest", record.checksum());
        tag.putUUID("entity_id", UUID.randomUUID()); tag.putLong("resource_epoch", 1);
        tag.putString("signature_kind", "FUEL_VECTOR_V1"); tag.putString("invariant_digest", record.checksum());
        tag.putString("signature", record.checksum()); tag.putLong("highest_seq", 1); return tag;
    }

    /**
     * An unresolved escrow operation guarding L, held in manual REPAIR so that neither reconciliation nor a transfer turn
     * acts on it: the coordinator's logical lock (W11b2), with endpoints shaped as {@code EscrowTestEndpoints}' fixture.
     */
    static EscrowOperation lock(RocketTransferRecord record) {
        String level = record.sourceSnapshot().sourceDimension().toString();
        var origin = record.sourceSnapshot().sourceOrigin();
        var from = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level, UUID.randomUUID(), null, "minecraft:chest",
                origin.x(), origin.y(), origin.z(), EscrowEndpoint.Unit.TANK, 0, RocketNativeResources.hash("w170-r24 device before"),
                RocketNativeResources.hash("w170-r24 device after"));
        var to = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level, UUID.randomUUID(), record.logicalRocketId(), "minecraft:armor_stand",
                origin.x() >> 4, 0, origin.z() >> 4, EscrowEndpoint.Unit.PRIMARY, 0, RocketNativeResources.hash("w170-r24 rocket before"),
                RocketNativeResources.hash("w170-r24 rocket after"));
        var payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
        return new EscrowOperation(UUID.randomUUID(), 1, record.ownerId(), 0, EscrowOperation.Phase.REPAIR, EscrowReason.MISSING_ENDPOINT,
                payload, RocketNativeResources.signature(payload), from, to,
                new EscrowGuard(record.logicalRocketId(), null, 1, RocketNativeResources.hash("w170-r24 checked no-transfer")));
    }
}
