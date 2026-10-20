package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.WorldDataMigrationService;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R39 for fluids (W115): durable fluid payloads decode structurally; the native check holds only the acting operation. */
class EscrowFluidDurableValidationTest {
    private static final String MISSING = "w115:unregistered_fluid";
    private static final String RENAMED = "w115:renamed_water";
    private static final List<EscrowOperation.Kind> KINDS = List.of(EscrowOperation.Kind.FLUID_PROPULSION_FILL,
            EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD);
    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        // A mod that renames its Fluid registers an alias: the old saved name then loads a Fluid with another canonical name.
        @SuppressWarnings("unchecked") var fluids = (ForgeRegistry<Fluid>) ForgeRegistries.FLUIDS;
        fluids.addAlias(ResourceLocation.tryParse(RENAMED), ResourceLocation.tryParse("minecraft:water"));
    }
    @TempDir Path temp;

    @Test void unregisteredFluidDoesNotRefuseLedgerStartup() throws Exception {
        assertTrue(FluidStack.loadFluidStackFromNBT(fluid(MISSING)).isEmpty());
        startsUnchangedButNativeRefuses(MISSING);
    }

    @Test void renamedFluidDoesNotRefuseLedgerStartup() throws Exception {
        var loaded = FluidStack.loadFluidStackFromNBT(fluid(RENAMED));
        assertEquals(Fluids.WATER, loaded.getFluid());
        assertEquals("minecraft:water", loaded.writeToNBT(new CompoundTag()).getString("FluidName"), "The canonical form changed");
        startsUnchangedButNativeRefuses(RENAMED);
    }

    private void startsUnchangedButNativeRefuses(String name) throws Exception {
        for (var kind : KINDS) {
            for (boolean settling : List.of(false, true)) {
                var baseline = EscrowCodec.encode(ledger(operation(kind)));
                var prepared = baseline.copy();
                var record = prepared.getList("records", Tag.TAG_COMPOUND).getCompound(0);
                var payload = record.getCompound("payload"); fluidOf(kind, payload).putString("FluidName", name);
                record.putString("payload_hash", RocketNativeResources.signature(payload));
                // Freeze the actual changed record, rather than corrupting an already frozen digest.
                var encoded = settling ? settle(kind, prepared) : prepared;
                var world = Files.createDirectories(temp.resolve(name.replace(':', '-') + "-" + kind + "-" + settling));
                var file = Files.createDirectories(world.resolve("data")).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
                var outer = new CompoundTag(); outer.put("data", encoded); NbtIo.writeCompressed(outer, file.toFile());
                byte[] before = Files.readAllBytes(file);
                assertEquals(1, assertDoesNotThrow(() -> new WorldDataMigrationService().migrate(world)).managedFileCount());
                assertArrayEquals(before, Files.readAllBytes(file));
                assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
                var decoded = assertDoesNotThrow(() -> EscrowCodec.decode(encoded));
                assertEquals(encoded, EscrowCodec.encode(decoded));
                var data = assertDoesNotThrow(() -> ResourceEscrowSavedData.load(encoded));
                assertTrue(data.operational()); assertFalse(data.isDirty());
                try (var writer = new EscrowLedgerWriter(world.resolve("writer.dat"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                    assertEquals(decoded, coordinator.ledger()); assertTrue(writer.capturedOperations().isEmpty());
                }
                var op = decoded.records().get(0);
                assertEquals(settling ? EscrowOperation.Phase.SETTLING_ACCEPT : EscrowOperation.Phase.PREPARED, op.phase());
                assertEquals(payload, op.payload());
                // The native check refuses the same record, wherever an operation would act on it.
                assertThrows(IllegalArgumentException.class, op::nativeFluid);
                assertFalse(EscrowTransferAdmission.nativeFluid(op));
                assertThrows(IllegalArgumentException.class, () -> EscrowFluidCodec.validateNative(fluidOf(kind, op.payload())));
                assertDoesNotThrow(() -> EscrowFluidCodec.validateStructure(fluidOf(kind, op.payload())));
                if (kind.cargo()) {
                    assertEquals(EscrowCargoFixtures.cursor(), EscrowCargoPayload.validateStructure(op.payload()));
                    assertThrows(IllegalArgumentException.class, () -> EscrowCargoPayload.decode(op.payload()),
                            "Acting on a cargo delta still needs the native check");
                }
                // Settlement and the inspector read only structure, identity, phase and reason.
                var control = EscrowCodec.decode(settling ? settle(kind, baseline) : baseline);
                assertEquals(EscrowOperatorDiagnostics.status(control, value -> value.reason().name()),
                        EscrowOperatorDiagnostics.status(decoded, value -> value.reason().name()));
                if (settling) {
                    var intent = EscrowSettlementIntent.decode(op.settlement());
                    var receipt = EscrowSettlementTombstones.create(op, intent);
                    assertEquals(kind == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? 1_000 : 0, receipt.getInt("payload_amount"));
                    assertEquals(op.payloadHash(), receipt.getString("payload_hash"));
                    try (var recovery = new EscrowSettlementRecovery()) {
                        var lines = EscrowOperatorDiagnostics.inspect(decoded, op.id(), recovery::view, id -> fail(), "UNKNOWN");
                        assertEquals(2, lines.size()); assertTrue(lines.get(1).contains("persisted=SETTLING_ACCEPT"), lines.get(1));
                    }
                } else {
                    var view = new EscrowReconciliationScheduler.View("QUEUED", "NONE", -1, 0, false);
                    assertEquals(EscrowOperatorDiagnostics.inspect(control, op.id(), ignored -> fail(), id -> view, "ACKNOWLEDGED"),
                            EscrowOperatorDiagnostics.inspect(decoded, op.id(), ignored -> fail(), id -> view, "ACKNOWLEDGED"));
                }
            }
        }
    }

    @Test void structuralValidationKeepsExactFieldsAmountsAndBoundsWithoutTheRegistry() {
        var valid = fluid(MISSING); var tagged = fluid(MISSING); tagged.put("Tag", new CompoundTag());
        for (var good : List.of(valid, tagged, fluid(RENAMED), fluid("w:" + "f".repeat(126)))) {
            assertDoesNotThrow(() -> EscrowFluidCodec.validateStructure(good));
        }
        var bad = new ArrayList<CompoundTag>();
        for (String id : List.of("water", "minecraft:Water", "minecraft:wat er", "w:" + "f".repeat(127), "")) { bad.add(fluid(id)); }
        for (int amount : List.of(Integer.MIN_VALUE, -1, 0, 1_001)) { var value = fluid(MISSING); value.putInt("Amount", amount); bad.add(value); }
        var unknown = fluid(MISSING); unknown.putInt("Slot", 0); bad.add(unknown);
        var longAmount = fluid(MISSING); longAmount.putLong("Amount", 1_000); bad.add(longAmount);
        var numericName = fluid(MISSING); numericName.putInt("FluidName", 1); bad.add(numericName);
        var stringTag = fluid(MISSING); stringTag.putString("Tag", "invalid"); bad.add(stringTag);
        var noName = fluid(MISSING); noName.remove("FluidName"); bad.add(noName);
        var noAmount = fluid(MISSING); noAmount.remove("Amount"); bad.add(noAmount);
        for (var value : bad) {
            var original = value.copy();
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCodec.validateStructure(value), value.toString());
            assertEquals(original, value);
        }
        // The unchanged bound: 8,192 bytes, depth 12 and 384 nodes, measured exactly as before.
        var crossed = new java.util.HashSet<String>();
        for (int blob = 8_100; blob <= 8_200; blob++) {
            var value = fluid(MISSING); var tag = new CompoundTag(); tag.putByteArray("b", new byte[blob]); value.put("Tag", tag);
            assertBound(value, "bytes", crossed);
        }
        for (int depth = 8; depth <= 13; depth++) {
            var value = fluid(MISSING); var child = new CompoundTag(); value.put("Tag", child);
            for (int i = 0; i < depth; i++) { var next = new CompoundTag(); child.put("c", next); child = next; }
            assertBound(value, "depth", crossed);
        }
        for (int nodes = 375; nodes <= 385; nodes++) {
            var value = fluid(MISSING); var tag = new CompoundTag(); value.put("Tag", tag);
            for (int i = 0; i < nodes; i++) { tag.putByte("n" + i, (byte) 1); }
            assertBound(value, "nodes", crossed);
        }
        assertEquals(java.util.Set.of("bytes-true", "bytes-false", "depth-true", "depth-false", "nodes-true", "nodes-false"), crossed,
                "Each range crosses the bound");
        // Malformed durable records still refuse the ledger, as before; only the native checks moved.
        for (var kind : KINDS) {
            var encoded = EscrowCodec.operation(operation(kind));
            var malformed = encoded.copy(); fluidOf(kind, malformed.getCompound("payload")).putInt("Amount", 1_001);
            malformed.putString("payload_hash", RocketNativeResources.signature(malformed.getCompound("payload")));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(malformed));
            var noncanonical = encoded.copy(); fluidOf(kind, noncanonical.getCompound("payload")).putString("FluidName", "minecraft:Water");
            noncanonical.putString("payload_hash", RocketNativeResources.signature(noncanonical.getCompound("payload")));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(noncanonical));
        }
    }

    /** The C2 consumer holds only the fill whose Fluid fails, as sticky REPAIR, and publishes nothing for it. */
    @Test void c2HoldsOnlyTheFillWhoseFluidIsUnavailable() throws Exception {
        for (String name : List.of(MISSING, RENAMED)) {
            var f = EscrowReconciliationBindingBoundaryTest.sample(); var a = f.a(); var held = withFluid(f.b(), name);
            var root = new EscrowLedger(f.ledger().id(), 2, 1, List.of(a, held), List.of(), List.of());
            // The projections themselves refuse before acting, whichever caller reaches them.
            assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationChange.rocket(root.id(), held, f.first()));
            assertThrows(IllegalArgumentException.class, () -> EscrowReconciliationChange.source(root.id(), held, chest(2, root.id(), false)));
            assertEquals(EscrowReason.OTHER_STATE, EscrowReconciliationValidation.action(root, held, f.first(), f.after(), true));
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            var file = Files.createDirectories(temp.resolve("c2-" + name.replace(':', '-'))).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            var io = new AwaitedLedgerIo(); var published = new ArrayList<UUID>(); var attempts = new ArrayList<UUID>();
            try (var action = new LogCapture(EscrowReconciliationAction.class); var scheduled = new LogCapture(EscrowReconciliationScheduler.class);
                 var writer = io.writer(file, 1, CheckedSavedDataFile::atomicMove); var coordinator = new EscrowCoordinator(data, writer)) {
                var scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition, (op, records, done) -> {
                    attempts.add(op.id());
                    var source = chest(op.sequence(), root.id(), false);
                    var sourceState = EscrowTestEndpoints.state(source.getCompound("ForgeData"), root.id(), false);
                    var wrapper = RocketEscrowSection.decode(f.before().getCompound("wrapper"));
                    var destinationState = new EscrowPrefix.State(root.id(), wrapper.epoch(), a.destination().beforeSignature(), wrapper.markers());
                    done.accept(EscrowReconciliationDriver.applyNext(coordinator, coordinator.ledger(), op, EscrowPrefix.chains(records),
                            endpoint -> endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? destinationState : sourceState,
                            endpoint -> endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? f.before() : source, () -> true,
                            (destination, permit) -> published.add(op.id())));
                });
                scheduler.tick(1);
                assertEquals(List.of(a.id(), held.id()), attempts);
                assertEquals(List.of(a.id()), published, "The other fill still reaches its C2 debit");
                assertEquals(EscrowReason.NONE, coordinator.currentReason(a));
                assertEquals(EscrowReason.OTHER_STATE, coordinator.currentReason(held));
                assertTrue(coordinator.operational()); assertEquals(root, coordinator.ledger(), "Nothing changes before the checked write");
                assertEquals(List.of("WARN ARCE_ESCROW_FLUID_UNAVAILABLE op=" + held.id() + " fluid=" + name), action.lines());
                scheduler.tick(2_000); scheduler.tick(4_000);
                assertFalse(attempts.subList(2, attempts.size()).contains(held.id()), "A manual record is never revisited");
                assertFalse(published.contains(held.id()));
                assertEquals(1, action.lines().size());
                assertEquals(1, scheduled.lines().stream().filter(line -> line.startsWith("WARN") && line.contains(held.id().toString())).count());
                coordinator.persistTransitions(); io.tickUntil(coordinator::tick, 1, writer::idle);
                assertTrue(writer.idle()); assertFalse(data.isDirty());
                var persisted = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
                assertEquals(List.of(a, held.withReason(EscrowReason.OTHER_STATE)), persisted.records());
                assertEquals(EscrowOperation.Phase.REPAIR, persisted.records().get(1).phase());
                assertEquals(root.id(), persisted.id()); assertEquals(root.issuedThrough(), persisted.issuedThrough());
                assertEquals(root.floors(), persisted.floors()); assertEquals(root.tombstones(), persisted.tombstones());
                // After a restart the hold persists and the scheduler still visits only the other fill.
                var restarted = ResourceEscrowSavedData.load(EscrowCodec.encode(persisted)); assertTrue(restarted.operational());
                var visited = new ArrayList<UUID>();
                new EscrowReconciliationScheduler(restarted::ledger, (id, reason) -> fail("No transition"), (op, records, done) -> {
                    visited.add(op.id()); done.accept(EscrowReconciliationScheduler.Result.deferred());
                }).tick(0);
                assertEquals(List.of(a.id()), visited);
            }
        }
    }

    /** Admission keeps the full native check before any sequence, reservation, queue entry or write. */
    @Test void admissionRefusesAnUnavailableFluidBeforeAnySideEffect() throws Exception {
        for (String name : List.of(MISSING, RENAMED)) {
            var f = EscrowReconciliationBindingBoundaryTest.sample(); var fresh = withFluid(f.a(), name);
            var wrapper = RocketEscrowSection.decode(f.before().getCompound("wrapper"));
            assertTrue(EscrowCoordinator.invalid(fresh)); assertTrue(EscrowTransferAdmission.invalid(fresh));
            assertFalse(EscrowCoordinator.invalid(f.a())); assertFalse(EscrowTransferAdmission.invalid(f.a()));
            var empty = new EscrowLedger(f.ledger().id(), 0, 0, List.of(), List.of(), List.of());
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
            var replies = new ArrayList<Boolean>();
            try (var writer = new EscrowLedgerWriter(temp.resolve("fill-" + name.replace(':', '-') + ".dat"), 0);
                 var coordinator = new EscrowCoordinator(data, writer)) {
                assertEquals(EscrowCoordinator.Transfer.INVALID, coordinator.prepareTransfer(new EscrowNativeBinding.Admission(fresh, wrapper), replies::add));
                assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(fresh, replies::add));
                assertEquals(1, coordinator.nextSequence()); assertTrue(coordinator.unresolvedIds().isEmpty());
                assertFalse(coordinator.admitting(fresh.source().key())); assertFalse(coordinator.admitting(fresh.destination().key()));
                assertFalse(data.isDirty()); assertTrue(writer.idle()); assertTrue(replies.isEmpty());
                assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(new EscrowNativeBinding.Admission(f.a(), wrapper), replies::add),
                        "The refusal consumed no sequence or reservation");
            }
            for (boolean load : List.of(true, false)) {
                // Cargo eligibility acts on the delta, so it materializes it natively before the mutation or device change.
                var c = EscrowCargoEligibilityTest.sample(load, 1_000, -1); var cargo = withFluid(c.op(), name);
                var inputs = List.of(c.before().copy(), c.after().copy(), c.deviceBefore().copy(), c.deviceAfter().copy());
                var refused = EscrowCargoEligibility.check(ledger(), List.of(), cargo, c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000);
                assertEquals(RocketCargoSelection.Status.INVALID_DATA, refused.status()); assertNull(refused.capture());
                assertEquals(inputs, List.of(c.before(), c.after(), c.deviceBefore(), c.deviceAfter()));
                assertEquals(RocketCargoSelection.Status.ELIGIBLE, EscrowCargoEligibility.check(ledger(), List.of(), c.op(),
                        c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000).status());
            }
            for (var kind : EscrowCargoFixtures.KINDS) {
                var valid = EscrowCargoFixtures.operation(kind, 1, 99); var cargo = withFluid(valid, name);
                assertTrue(EscrowTransferAdmission.invalid(cargo)); assertFalse(EscrowTransferAdmission.invalid(valid));
                var cargoData = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(id(500))));
                try (var writer = new EscrowLedgerWriter(temp.resolve(kind + "-" + name.replace(':', '-') + ".dat"), 0);
                     var coordinator = new EscrowCoordinator(cargoData, writer)) {
                    assertEquals(EscrowCoordinator.Transfer.INVALID, coordinator.prepareTransfer(
                            new EscrowNativeBinding.Admission(cargo, EscrowCargoFixtures.wrapper(0)), replies::add));
                    assertEquals(1, coordinator.nextSequence()); assertTrue(coordinator.unresolvedIds().isEmpty());
                    assertFalse(cargoData.isDirty()); assertTrue(writer.idle()); assertTrue(replies.isEmpty());
                }
            }
        }
    }

    private static EscrowOperation operation(EscrowOperation.Kind kind) {
        return kind == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? EscrowTestFixtures.operation(1) : EscrowCargoFixtures.operation(kind, 1, 99);
    }
    private static CompoundTag fluidOf(EscrowOperation.Kind kind, CompoundTag payload) {
        return kind == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? payload : payload.getCompound("fluid");
    }
    private static CompoundTag fluid(String name) {
        var fluid = new CompoundTag(); fluid.putString("FluidName", name); fluid.putInt("Amount", 1_000); return fluid;
    }
    private static void assertBound(CompoundTag value, String kind, java.util.Set<String> crossed) {
        boolean fits = BoundedNbt.fits(value, EscrowLimits.FLUID_BYTES, 12, 384), structural;
        try { EscrowFluidCodec.validateStructure(value); structural = true; }
        catch (IllegalArgumentException refused) { structural = false; }
        assertEquals(fits, structural, kind); crossed.add(kind + "-" + fits);
    }
    private static EscrowOperation withFluid(EscrowOperation op, String name) {
        var payload = op.payload(); fluidOf(op.kind(), payload).putString("FluidName", name);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), payload,
                RocketNativeResources.signature(payload), op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), op.nativeBinding());
    }
    private static CompoundTag settle(EscrowOperation.Kind kind, CompoundTag prepared) {
        var op = EscrowCodec.decode(prepared).records().get(0).withReason(EscrowReason.OTHER_STATE); var root = ledger(op);
        EscrowSettlementIntent intent;
        if (kind.cargo()) { intent = EscrowCargoFixtures.intent(root, op, 0, 0, false); }
        else {
            var observations = EscrowSettlementIntentTest.observations(root, false);
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observations);
            intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
        }
        return EscrowCodec.encode(ledger(op.settle(intent)));
    }
    /** The gated chest fixture's envelope, as in EscrowReconciliationBindingBoundaryTest. */
    private static CompoundTag chest(long sequence, UUID ledger, boolean applied) {
        var identity = id(1_000 + sequence); int amount = applied ? 4_900 : 5_000;
        var fixture = new CompoundTag(); fixture.putUUID("identity", identity); fixture.putInt("amount", amount); fixture.putInt("capacity", 16_000);
        var persistent = new CompoundTag(); persistent.put("arce_escrow_fixture", fixture);
        EscrowTestEndpoints.putState(persistent, new EscrowPrefix.State(ledger, applied ? 1 : 0,
                RocketNativeResources.hash("arce-escrow-fixture-1:" + identity + ":" + amount),
                applied ? List.of(new EscrowPrefix.Marker(id(sequence), sequence, "SOURCE", 1)) : List.of()), false);
        var tag = new CompoundTag(); tag.putString("id", "minecraft:chest"); tag.putInt("x", (int) sequence); tag.putInt("y", 64); tag.putInt("z", 0);
        tag.put("ForgeData", persistent); return tag;
    }
    private static final class LogCapture extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
        private final List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final String source;
        private final org.apache.logging.log4j.core.LoggerContext context;
        private final org.apache.logging.log4j.core.config.LoggerConfig config;
        LogCapture(Class<?> type) {
            super("arce-escrow-log-" + UUID.randomUUID(), null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(type.getName());
            source = logger.getName(); context = logger.getContext(); config = context.getConfiguration().getLoggerConfig(source);
            start(); config.addAppender(this, null, null); context.updateLoggers();
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            if (source.equals(event.getLoggerName())) { lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage()); }
        }
        List<String> lines() { return List.copyOf(lines); }
        @Override public void close() { config.removeAppender(getName()); context.updateLoggers(); stop(); }
    }
}
