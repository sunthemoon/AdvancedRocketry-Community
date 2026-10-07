package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtStatus;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Full roots/cuts/native validation under actual private owner authority. */
final class ClassicFrameCodec {
    static ClassicControllerDecode decodeController(CompoundTag parent, ResourceLocation actualKind,
            ResourceLocation actualLevel, BlockPos actualPosition, GuardTicket ticket) {
        ticket.requireValid(); ClassicControllerBlockEntity owner = ticket.controller();
        ClassicValueChecks.require(owner.machineKind().equals(actualKind) && owner.getBlockPos().equals(actualPosition)
                && ticket.level().dimension().location().equals(actualLevel), "Foreign actual controller");
        ClassicRootBundle roots = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.CONTROLLER);
        boolean planRequired = roots.controllerPlanRequiresJournal();
        if (!roots.bounded()) { return refused(ClassicDecodeStatus.UNBOUNDED, roots, owner, planRequired); }
        if (roots.missingRequired(planRequired)) { return refused(ClassicDecodeStatus.MISSING_REQUIRED, roots, owner, planRequired); }
        if (roots.forbiddenPresent()) { return refused(ClassicDecodeStatus.OWNER_CONFLICT, roots, owner, planRequired); }
        parent = roots.forDecode(ticket); // only the managed roots, not vanilla/unowned parent fields
        try {
            if (future(parent, ClassicRootBundle.MACHINE) || future(parent, ClassicRootBundle.RESOURCES)
                    || future(parent, ClassicRootBundle.MARKER)
                    || (parent.contains(ClassicRootBundle.JOURNAL) && future(parent, ClassicRootBundle.JOURNAL))) {
                return refused(ClassicDecodeStatus.UNSUPPORTED_SCHEMA, roots, owner, planRequired);
            }
            ClassicValueChecks.require(preflightController(parent), "Unsupported combined projection");
            ClassicMachineState structural = ClassicMachineCodec.preflight(parent.getCompound(ClassicRootBundle.MACHINE));
            if (!structural.machineKind().equals(actualKind) || !structural.ownerLevel().equals(actualLevel)
                    || !structural.ownerPosition().equals(actualPosition)) {
                return refused(ClassicDecodeStatus.OWNER_CONFLICT, roots, owner, planRequired);
            }
            for (ClassicAssignment assignment : structural.assignments()) {
                ClassicValueChecks.require(new ClassicLoadedWorld(ticket.level()).usable(assignment.position()), "Assignment world bounds");
            }
            var journal = preflightJournal(parent);
            ClassicSignatureMarker signature = marker(parent.getCompound(ClassicRootBundle.MARKER));
            ClassicMachineState machine = ClassicMachineCodec.decode(parent.getCompound(ClassicRootBundle.MACHINE), ticket);
            ClassicResourcesDecode resource = ClassicGuardedResourceAccess.decode(parent.get(ClassicRootBundle.RESOURCES),
                    machine.machineId(), ticket::requireValid);
            ticket.requireValid();
            if (resource.value().isEmpty()) {
                ClassicDecodeStatus status = resource.status() == ClassicResourcesDecode.Status.OWNER_CONFLICT
                        ? ClassicDecodeStatus.OWNER_CONFLICT : ClassicDecodeStatus.INVALID_DATA;
                return refused(status, roots, owner, planRequired);
            }
            ClassicControllerFrame frame = new ClassicControllerFrame(machine, resource.value().orElseThrow(), journal,
                    signature);
            try { ClassicCheckpointChecks.validate(frame, ticket); }
            catch (IllegalArgumentException incoherent) {
                ticket.requireValid(); return refused(ClassicDecodeStatus.INCONSISTENT_CHECKPOINT, roots, owner, planRequired);
            }
            // Exact supported encoding catches no lossy field/default/coercion route.
            CompoundTag encoded = encodeController(frame, ticket);
            ClassicValueChecks.require(parent.equals(encoded), "Lossy managed controller decode");
            ticket.requireValid();
            return new ClassicControllerDecode(ClassicDecodeStatus.SUPPORTED, frame, roots, owner.ownerState(), planRequired);
        } catch (RuntimeException invalid) {
            // Witness failure must escape; never preserve it as a successful typed load.
            ticket.requireValid(); return refused(ClassicDecodeStatus.INVALID_DATA, roots, owner, planRequired);
        }
    }

    private static ClassicControllerDecode refused(ClassicDecodeStatus status, ClassicRootBundle roots,
            ClassicControllerBlockEntity owner, boolean planRequired) {
        return new ClassicControllerDecode(status, null, roots, owner.ownerState(), planRequired);
    }

    private static boolean future(CompoundTag parent, String key) {
        CompoundTag root = ClassicFieldCodec.compound(parent, key);
        return ClassicFieldCodec.integer(root, "schema_version") != 1;
    }

    static CompoundTag encodeController(ClassicControllerFrame frame, GuardTicket ticket) {
        ticket.requireValid(); ClassicControllerBlockEntity owner = ticket.controller();
        ClassicValueChecks.require(frame.machine().machineKind().equals(owner.machineKind())
                && frame.machine().ownerPosition().equals(owner.getBlockPos())
                && frame.machine().ownerLevel().equals(ticket.level().dimension().location()), "Foreign outgoing owner");
        ClassicCheckpointChecks.validate(frame, ticket);
        CompoundTag result = new CompoundTag();
        result.put(ClassicRootBundle.MACHINE, ClassicMachineCodec.encode(frame.machine(), ticket));
        result.put(ClassicRootBundle.RESOURCES, ClassicGuardedResourceAccess.encode(frame.resources(), ticket::requireValid));
        frame.journal().ifPresent(value -> result.put(ClassicRootBundle.JOURNAL, ProcessJournalPersistence.encode(value)));
        result.put(ClassicRootBundle.MARKER, marker(frame.signatureMarker()));
        ClassicValueChecks.require(preflightController(result), "Outgoing combined controller bound");
        ticket.requireValid(); return result;
    }

    static boolean preflightController(CompoundTag root) {
        if (root == null || root.getClass() != CompoundTag.class) { return false; }
        Set<String> required = Set.of(ClassicRootBundle.MACHINE, ClassicRootBundle.RESOURCES, ClassicRootBundle.MARKER);
        Set<String> allowed = Set.of(ClassicRootBundle.MACHINE, ClassicRootBundle.RESOURCES, ClassicRootBundle.MARKER,
                ClassicRootBundle.JOURNAL);
        if (!root.getAllKeys().containsAll(required) || !allowed.containsAll(root.getAllKeys())) { return false; }
        for (String key : root.getAllKeys()) {
            if (!root.contains(key, Tag.TAG_COMPOUND) || !ClassicNbtShape.fits(root.get(key), ClassicRootBundle.limits(key))) {
                return false;
            }
        }
        return ClassicNbtShape.fits(root, ClassicNbtLimits.CONTROLLER);
    }

    static ClassicHatchDecode decodeHatch(CompoundTag parent, ClassicHatchKind actualKind,
                                        BlockPos actualPosition, GuardTicket ticket) {
        ticket.requireValid(); ClassicHatchBlockEntity owner = ticket.hatch();
        ClassicValueChecks.require(owner.kind() == actualKind && owner.getBlockPos().equals(actualPosition), "Foreign actual hatch");
        ClassicRootBundle roots = ClassicRootBundle.captureData(parent, ClassicRootBundle.OwnerType.HATCH);
        ClassicDecodeStatus status;
        ClassicHatchState supported = null;
        if (!roots.bounded()) { status = ClassicDecodeStatus.UNBOUNDED; }
        else if (roots.missingRequired(false)) { status = ClassicDecodeStatus.MISSING_REQUIRED; }
        else if (roots.forbiddenPresent()) { status = ClassicDecodeStatus.OWNER_CONFLICT; }
        else {
            try {
                parent = roots.forDecode(ticket);
                CompoundTag root = ClassicFieldCodec.compound(parent, ClassicRootBundle.HATCH);
                if (ClassicFieldCodec.integer(root, "schema_version") != 1) { status = ClassicDecodeStatus.UNSUPPORTED_SCHEMA; }
                else {
                    ClassicHatchCheckpoint checkpoint = hatch(root);
                    ClassicHatchState view = checkpoint.view();
                    ClassicValueChecks.require(view.kind() == actualKind, "Hatch actual block kind");
                    view.binding().ifPresent(binding -> ClassicValueChecks.require(
                            binding.controllerLevel() == ticket.level().dimension()
                                    && new ClassicLoadedWorld(ticket.level()).usable(binding.controllerPosition()), "Hatch binding world bounds"));
                    view.bankKey().ifPresent(key -> ClassicValueChecks.require(key.x() == actualPosition.getX()
                            && key.y() == actualPosition.getY() && key.z() == actualPosition.getZ(), "Hatch actual coordinates"));
                    if (checkpoint.handoff().isPresent()) { status = ClassicDecodeStatus.INCONSISTENT_CHECKPOINT; }
                    else {
                        ClassicValueChecks.require(root.equals(encodeHatchCheckpoint(checkpoint, ticket)), "Lossy hatch decode");
                        supported = view; status = ClassicDecodeStatus.SUPPORTED;
                    }
                }
            } catch (RuntimeException invalid) { ticket.requireValid(); status = ClassicDecodeStatus.INVALID_DATA; }
        }
        ticket.requireValid(); return new ClassicHatchDecode(status, supported, roots, owner.ownerState());
    }

    private static ClassicHatchCheckpoint hatch(CompoundTag root) {
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.HATCH); ClassicFieldCodec.schema(root);
        ClassicFieldCodec.fields(root, new String[] {"schema_version", "kind"}, "binding", "bank_channel", "energy", "power_handoff");
        ClassicHatchKind kind = ClassicHatchKind.fromBlockId(ClassicFieldCodec.id(root, "kind")).orElseThrow();
        Optional<MultiblockPartBinding> binding = Optional.empty();
        if (root.contains("binding")) {
            CompoundTag raw = ClassicFieldCodec.compound(root, "binding");
            ClassicFieldCodec.fields(raw, new String[] {"schema_version", "controller_level", "controller", "machine_instance_id", "generation"});
            ClassicFieldCodec.schema(raw);
            binding = Optional.of(new MultiblockPartBinding(1, ResourceKey.create(Registries.DIMENSION,
                    ClassicFieldCodec.id(raw, "controller_level")),
                    ClassicFieldCodec.position(ClassicFieldCodec.compound(raw, "controller")),
                    ClassicValueChecks.uuid(ClassicFieldCodec.string(raw, "machine_instance_id", 36)),
                    ClassicFieldCodec.number(raw, "generation")));
        }
        ClassicHatchState view = new ClassicHatchState(kind, binding, root.contains("bank_channel")
                ? Optional.of(ClassicBankKey.parse(ClassicFieldCodec.string(root, "bank_channel", 64))) : Optional.empty(),
                root.contains("energy") ? OptionalInt.of(ClassicFieldCodec.integer(root, "energy")) : OptionalInt.empty());
        Optional<ClassicPowerHandoff> handoff = Optional.empty();
        if (root.contains("power_handoff")) {
            CompoundTag raw = ClassicFieldCodec.compound(root, "power_handoff");
            ClassicFieldCodec.fields(raw, new String[] {"schema_version", "operation_id", "phase", "expected_energy"}, "entity_id");
            ClassicFieldCodec.schema(raw);
            handoff = Optional.of(new ClassicPowerHandoff(ClassicFieldCodec.uuid(raw, "operation_id"),
                    ClassicFieldCodec.enumeration(ClassicPowerHandoffPhase.class, ClassicFieldCodec.string(raw, "phase", 32), true),
                    ClassicFieldCodec.integer(raw, "expected_energy"), raw.contains("entity_id")
                            ? Optional.of(ClassicFieldCodec.uuid(raw, "entity_id")) : Optional.empty()));
        }
        return new ClassicHatchCheckpoint(view, handoff);
    }

    static CompoundTag encodeHatch(ClassicHatchState hatch, GuardTicket ticket) {
        return encodeHatchCheckpoint(new ClassicHatchCheckpoint(hatch, Optional.empty()), ticket);
    }

    static CompoundTag encodeHatchCheckpoint(ClassicHatchCheckpoint checkpoint, GuardTicket ticket) {
        ticket.requireValid(); ClassicHatchState hatch = checkpoint.view();
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("kind", hatch.kind().blockId().toString());
        hatch.binding().ifPresent(binding -> {
            CompoundTag raw = new CompoundTag(); raw.putInt("schema_version", 1);
            raw.putString("controller_level", binding.controllerLevel().location().toString());
            raw.put("controller", ClassicFieldCodec.position(binding.controllerPosition()));
            raw.putString("machine_instance_id", binding.machineInstanceId().toString()); raw.putLong("generation", binding.generation());
            root.put("binding", raw);
        });
        hatch.bankKey().ifPresent(key -> root.putString("bank_channel", key.channel()));
        hatch.energy().ifPresent(value -> root.putInt("energy", value));
        checkpoint.handoff().ifPresent(value -> {
            CompoundTag raw = new CompoundTag(); raw.putInt("schema_version", 1); raw.putUUID("operation_id", value.operationId());
            raw.putString("phase", value.phase().name().toLowerCase(Locale.ROOT)); raw.putInt("expected_energy", value.expectedEnergy());
            value.entityId().ifPresent(id -> raw.putUUID("entity_id", id)); root.put("power_handoff", raw);
        });
        ClassicValueChecks.require(preflightHatch(root), "Outgoing hatch bound"); ticket.requireValid(); return root;
    }

    static boolean preflightHatch(Tag root) {
        return root instanceof CompoundTag && ClassicNbtShape.fits(root, ClassicNbtLimits.HATCH);
    }

    private static ClassicSignatureMarker marker(CompoundTag root) {
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.MARKER); ClassicFieldCodec.schema(root);
        ClassicFieldCodec.fields(root, new String[] {"schema_version", "format", "converted"}, "recipe_id");
        ClassicValueChecks.require(ClassicFieldCodec.string(root, "format", 16).equals("json_v1")
                && ClassicFieldCodec.tag(root, "converted", Tag.TAG_BYTE) != null && root.getByte("converted") == 0, "Marker format");
        return new ClassicSignatureMarker(root.contains("recipe_id") ? Optional.of(ClassicFieldCodec.id(root, "recipe_id")) : Optional.empty());
    }

    private static CompoundTag marker(ClassicSignatureMarker value) {
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("format", "json_v1");
        root.putByte("converted", (byte) 0); value.recipeId().ifPresent(id -> root.putString("recipe_id", id.toString())); return root;
    }

    private static void strictJournal(CompoundTag root) {
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.JOURNAL); ClassicFieldCodec.schema(root);
        ClassicFieldCodec.fields(root, new String[] {"schema_version", "transaction_id", "machine_id", "definition_id",
                "port_revision", "before_fingerprint", "phase", "before", "after"});
        for (String key : new String[] {"before", "after"}) {
            CompoundTag snapshot = ClassicFieldCodec.compound(root, key);
            ClassicFieldCodec.list(snapshot, "entries", 64);
        }
    }

    /** Quantity/schema data only; a returned journal is not a native checkpoint or ticket. */
    static Optional<io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal>
            preflightJournal(CompoundTag parent) {
        if (!parent.contains(ClassicRootBundle.JOURNAL)) { return Optional.empty(); }
        CompoundTag root = ClassicFieldCodec.compound(parent, ClassicRootBundle.JOURNAL);
        strictJournal(root);
        var result = ProcessJournalPersistence.decode(parent);
        ClassicValueChecks.require(result.status() == ProcessNbtStatus.SUPPORTED, "Invalid process journal");
        var value = result.value().orElseThrow();
        ClassicValueChecks.require(root.equals(ProcessJournalPersistence.encode(value)), "Lossy process journal data");
        return Optional.of(value);
    }

    private ClassicFrameCodec() { }
}
