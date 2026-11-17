package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

final class EscrowTestFixtures {
    static UUID id(long value) { return new UUID(11, value); }
    /**
     * The checked journal owner that floor-retirement fixtures pass explicitly (W238-R8). Their operator id(700) also owns
     * the transfer, so the encoded operations, receipts and counts stay as they were.
     */
    static final UUID FLOOR_OWNER = id(700);
    static String hash(long value) { return RocketNativeResources.hash("test-" + value); }
    static EscrowOperation operation(long sequence) { return operation(sequence, sequence, 0); }
    static EscrowOperation operation(long sequence, long rocket, long epoch) {
        CompoundTag payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
        return new EscrowOperation(id(sequence), sequence, id(99), 1, EscrowOperation.Phase.PREPARED,
                EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", id(1_000 + sequence), null,
                        "minecraft:chest", (int) sequence, 64, 0, EscrowEndpoint.Unit.TANK, 0, hash(sequence), hash(sequence + 100)),
                new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", id(2_000 + rocket), id(rocket),
                        "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, epoch, hash(epoch), hash(epoch + 1)),
                new EscrowGuard(id(rocket), null, 1, hash(400)));
    }
    static EscrowLedger ledger(EscrowOperation... records) {
        return new EscrowLedger(id(500), java.util.Arrays.stream(records).mapToLong(EscrowOperation::sequence).max().orElse(0),
                0, List.of(records), List.of(), List.of());
    }
    /** Historical format input only; runtime tests must carry captured accounting rows instead. */
    static EscrowLedger root2(EscrowLedger old) {
        if (!old.fluidAccounts().isEmpty() || old.records().stream().anyMatch(op -> op.accountingBaseline() != null)) {
            throw new IllegalArgumentException("Historical fixture cannot discard accounting rows");
        }
        return new EscrowLedger(old.id(), old.issuedThrough(), old.writerGeneration(), old.records(), old.floors(), old.tombstones(),
                old.issuedLaunchSerial(), old.rocketAuthorities(), old.armedFlights(), 2, List.of());
    }
    /** Typed fixture inputs passed through production capture, for admission/size tests without native publication. */
    static EscrowNativeBinding.Admission capturedModel(EscrowOperation op, RocketEscrowSection wrapper) {
        if (!op.kind().fluid()) { return new EscrowNativeBinding.Admission(op, wrapper); }
        var fluid = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? op.payload() : op.payload().getCompound("fluid");
        var moved = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(fluid);
        org.junit.jupiter.api.Assertions.assertFalse(moved.isEmpty(), "Fixture capture needs an available native fluid");
        var source = moved.copy(); source.setAmount(5000);
        if (op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL) {
            var descriptor = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Mode.MONO, 16000, 0, 1, 1,
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(
                            java.util.Map.of(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Role.MONO,
                                    List.of(fluid.getString("FluidName")),
                                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(),
                                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Role.OXIDIZER, List.of(),
                                    io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Role.WORKING_FLUID, List.of())));
            var before = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(descriptor);
            long amount = op.rocket().beforeEpoch() * moved.getAmount();
            var fluidId = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(moved.getFluid());
            if (amount > 0) { before = before.withFluidBank(false, fluidId, moved.getTag(), amount); }
            var after = before.withFluidBank(false, fluidId, moved.getTag(), amount + moved.getAmount());
            var tank = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank.of(source);
            return EscrowAccountingCapture.fuel(op, wrapper, tank, tank.withAmount(5000 - moved.getAmount()), before, after);
        }
        boolean loading = op.kind().cargoLoad();
        var before = cargoSnapshot(op, loading ? net.minecraftforge.fluids.FluidStack.EMPTY : source);
        var after = io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation.apply(
                before, EscrowCargoPayload.decode(op.payload()), loading);
        var deviceBefore = cargoDevice(op, loading ? source : net.minecraftforge.fluids.FluidStack.EMPTY);
        var deviceAfterStack = moved.copy(); deviceAfterStack.setAmount(loading ? 5000 - moved.getAmount() : moved.getAmount());
        return EscrowAccountingCapture.bind(op, wrapper, new CompoundTag(), new CompoundTag(), deviceBefore,
                cargoDevice(op, deviceAfterStack), before, after);
    }
    private static io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot cargoSnapshot(
            EscrowOperation op, net.minecraftforge.fluids.FluidStack stack) {
        var cursor = EscrowCargoPayload.validateStructure(op.payload());
        var block = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(cursor.x(), cursor.y(), cursor.z()),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.TYPE, java.util.Map.of()),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.payload(stack));
        var base = io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.snapshot(List.of(block));
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot.create(cursor.snapshot(),
                base.sourceDimension(), base.sourceOrigin(), base.blocks(), base.passengerAnchors(), base.stats(), 0,
                base.propulsion().orElseThrow());
    }
    private static CompoundTag cargoDevice(EscrowOperation op, net.minecraftforge.fluids.FluidStack stack) {
        var role = op.kind().cargoLoad()
                ? io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.LOAD
                : io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.UNLOAD;
        var root = new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot(
                op.owner(), op.device().identity(), role, null,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank.of(stack), RocketEscrowSection.neutral());
        var envelope = new CompoundTag(); envelope.putString("id", "advancedrocketrycommunity:" + role.id());
        envelope.put(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot.KEY, root.encode());
        return envelope;
    }
    static EscrowOperation accountedModel(EscrowOperation op) {
        if (!op.kind().fluid()) { return op; }
        var capture = capturedModel(nativeModel(op, RocketEscrowSection.neutral()), RocketEscrowSection.neutral());
        return op.accounting(capture.account().baseline());
    }
    static EscrowLedger accountedModel(EscrowLedger root) {
        var operations = new java.util.ArrayList<EscrowOperation>();
        var rows = new java.util.ArrayList<EscrowFluidAccount.Row>();
        for (var op : root.records()) {
            if (!op.kind().fluid()) { operations.add(op); continue; }
            var capture = capturedModel(nativeModel(op, RocketEscrowSection.neutral()), RocketEscrowSection.neutral());
            operations.add(op.accounting(capture.account().baseline())); rows.add(capture.account());
        }
        return root.accounting(operations, rows);
    }
    static EscrowLedger appendModel(EscrowLedger root, List<EscrowOperation> additions) {
        var operations = new java.util.ArrayList<EscrowOperation>();
        var rows = new java.util.HashMap<UUID, EscrowFluidAccount.Row>();
        for (var op : additions) {
            if (!op.kind().fluid()) { operations.add(op); continue; }
            var capture = capturedModel(nativeModel(op, RocketEscrowSection.neutral()), RocketEscrowSection.neutral());
            operations.add(op.accounting(capture.account().baseline())); rows.put(op.id(), capture.account());
        }
        return EscrowAccountingCapture.append(root, operations, rows);
    }
    static EscrowOperation admissionOperation(long sequence) {
        return admissionOperation(sequence, sequence, 0);
    }
    static EscrowOperation admissionOperation(long sequence, long rocket, long epoch) {
        return accountedModel(EscrowSettlementCapacityTest.operation(sequence, rocket, epoch, true));
    }
    static EscrowLedger admissionLedger(EscrowOperation... ops) {
        return appendModel(ledger(), List.of(ops));
    }
    static void prepareCapturedModel(EscrowCoordinator coordinator, EscrowOperation op, java.util.function.Consumer<Boolean> completion) {
        var result = coordinator.prepareTransfer(capturedModel(op, RocketEscrowSection.neutral()), completion);
        if (result != EscrowCoordinator.Transfer.QUEUED) { throw new IllegalArgumentException(result.name()); }
    }
    /** R469: replace all of the candidate's ceilings with its actual codec-measured O1, receipt and frozen row. */
    static long realPreview(EscrowLedger root, EscrowOperation candidate, List<EscrowSettlementIntent.Observation> observations) {
        var op = candidate.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
        var local = root.replace(root.records().stream().map(member -> member.id().equals(op.id()) ? op : member).toList(),
                root.floors(), root.tombstones());
        var numbers = new java.util.HashMap<EscrowEndpoint.Key, java.util.Map<UUID, EscrowFluidAccount.Vector>>();
        if (op.accountingBaseline() != null) {
            var baseline = EscrowAccountingReconciliation.baseline(root, op);
            numbers.put(op.source().key(), java.util.Map.of(op.id(), baseline.sourceAfter()));
            numbers.put(op.destination().key(), java.util.Map.of(op.id(), baseline.destinationAfter()));
        }
        var rows = EscrowSettlementAccounting.project(local, List.of(op), observations, numbers);
        var preview = EscrowSettlementIntent.preview(local, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                id(700), "~".repeat(16), 10, observations);
        if (!rows.isEmpty()) { preview = preview.accounting(rows); }
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
        var row = rows.isEmpty() ? null : rows.get(0);
        long operationBytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.operation(op.settle(intent))) - 3L;
        long receiptBytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(
                EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(op, intent, row))) - 3L;
        long rowBytes = row == null ? 0 : io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(
                EscrowFluidAccountCodec.encode(row, EscrowFluidAccountCodec.SAVED).value()) - 3L;
        System.out.println("R469_PREVIEW kind=" + op.kind() + " operation=" + operationBytes + " receipt=" + receiptBytes + " row=" + rowBytes);
        return operationBytes + receiptBytes + rowBytes;
    }
    /** Keep global byte/count-pressure tests below R244's owner cap without changing their encoded sizes. */
    static EscrowOperation distributedOwner(EscrowOperation op) {
        var owner = id(99 + (op.sequence() - 1) / 16); var b = op.nativeBinding();
        var binding = new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), owner, b.snapshot(), b.snapshotHash(), b.invariant(),
                b.sourceReference(), b.destinationReference(), b.beforeWrapper(), b.afterWrapper(), b.beforeFrontier(), b.afterFrontier(),
                b.sourceBefore(), b.sourceAfter(), b.destinationBefore(), b.destinationAfter(), b.complete());
        return new EscrowOperation(op.id(), op.sequence(), owner, op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding, op.accountingBaseline());
    }
    /** Reconstruct a historical encoded decision without submitting its actor name as a new preview. */
    static EscrowSettlementIntent storedActor(EscrowSettlementIntent preview, String name) {
        var encoded = preview.encode(); encoded.putString("actor_name", name);
        var base = encoded.copy(); base.remove("confirmed_tick"); base.remove("digest");
        encoded.putString("digest", RocketNativeResources.signature(base));
        return EscrowSettlementIntent.decode(encoded);
    }
    /** Explicit synthetic envelope hashes for codec/prefix tests, never a live native permit. */
    static EscrowOperation nativeModel(EscrowOperation op) {
        return nativeModel(op, null);
    }
    static EscrowOperation nativeModel(EscrowOperation op, RocketEscrowSection observed) {
        var b = op.nativeBinding();
        var binding = new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), b.owner(), id(900), hash(900), b.invariant(),
                b.sourceReference(), b.destinationReference(), observed == null ? b.beforeWrapper() : EscrowNativeEnvelope.digest(observed.encode()),
                b.afterWrapper(), b.beforeFrontier(), b.afterFrontier(),
                b.sourceBefore(), b.sourceAfter(), b.destinationBefore(), b.destinationAfter(), true);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding, op.accountingBaseline());
    }
    static CompoundTag floor(long id) {
        CompoundTag tag = EscrowCodec.guard(new EscrowGuard(id(id), null, 1, hash(id)));
        tag.putInt("schema_version", 2); tag.putUUID("entity_id", id(id + 1)); tag.putLong("resource_epoch", 1);
        tag.putString("signature_kind", EscrowSignatureKind.FIXTURE_RESOURCE_V1.name()); tag.putString("invariant_digest", hash(id));
        tag.putString("signature", hash(0)); tag.putLong("highest_seq", 1); return tag;
    }
    static CompoundTag receipt(int id) {
        var op = operation(10_000 + id).withReason(EscrowReason.OTHER_STATE); var root = root2(ledger(op));
        var observed = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        return EscrowSettlementTombstones.create(op, intent);
    }
}
