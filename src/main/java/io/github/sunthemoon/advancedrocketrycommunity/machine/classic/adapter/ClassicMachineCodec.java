package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Typed machine fields only. A result is not a publishable controller checkpoint. */
final class ClassicMachineCodec {
    static ClassicMachineState decode(CompoundTag root, GuardTicket ticket) {
        ticket.requireValid();
        ClassicMachineState structural = preflight(root);
        Optional<ClassicNativePlan> plan = root.contains("native_plan")
                ? Optional.of(ClassicPlanCodec.decode(ClassicFieldCodec.compound(root, "native_plan"), ticket))
                : Optional.empty();
        ticket.requireValid();
        return new ClassicMachineState(structural.machineKind(), structural.machineId(), structural.ownerLevel(),
                structural.ownerPosition(), structural.generation(), structural.rotation(), structural.formationState(),
                structural.assignments(), structural.batchOrdinal(), structural.refusal(), structural.process(), plan,
                structural.lastAppliedTransaction(), structural.dropQuarantine());
    }

    static ClassicMachineState preflight(CompoundTag root) {
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.MACHINE);
        ClassicFieldCodec.schema(root);
        ClassicFieldCodec.fields(root, new String[] {"schema_version", "machine_kind", "machine_uuid",
                "owner_level", "owner_position", "generation", "rotation", "formation_state", "assignments",
                "batch_ordinal", "refusal", "process"}, "native_plan", "last_applied_transaction", "drop_quarantine");
        PatternRotation rotation = null;
        int degrees = ClassicFieldCodec.integer(root, "rotation");
        for (PatternRotation option : PatternRotation.values()) { if (option.degrees() == degrees) { rotation = option; } }
        ClassicValueChecks.require(rotation != null, "Unsupported rotation");
        var assignments = new ArrayList<ClassicAssignment>();
        for (Tag element : ClassicFieldCodec.list(root, "assignments", 64)) {
            CompoundTag entry = (CompoundTag) element;
            ClassicFieldCodec.fields(entry, new String[] {"x", "y", "z", "kind"}, "bank_channel");
            BlockPos position = new BlockPos(ClassicFieldCodec.integer(entry, "x"),
                    ClassicFieldCodec.integer(entry, "y"), ClassicFieldCodec.integer(entry, "z"));
            ClassicHatchKind kind = ClassicHatchKind.fromBlockId(ClassicFieldCodec.id(entry, "kind")).orElseThrow();
            assignments.add(new ClassicAssignment(position, kind, entry.contains("bank_channel")
                    ? Optional.of(ClassicBankKey.parse(ClassicFieldCodec.string(entry, "bank_channel", 64)))
                    : Optional.empty()));
        }
        CompoundTag refusal = ClassicFieldCodec.compound(root, "refusal");
        ClassicFieldCodec.fields(refusal, new String[] {"code", "subject"});
        return new ClassicMachineState(ClassicFieldCodec.id(root, "machine_kind"),
                ClassicFieldCodec.uuid(root, "machine_uuid"), ClassicFieldCodec.id(root, "owner_level"),
                ClassicFieldCodec.position(ClassicFieldCodec.compound(root, "owner_position")),
                ClassicFieldCodec.number(root, "generation"), rotation,
                ClassicFieldCodec.enumeration(MultiblockFormationState.class,
                        ClassicFieldCodec.string(root, "formation_state", 64), false), assignments,
                ClassicFieldCodec.number(root, "batch_ordinal"),
                new ClassicRefusal(ClassicFieldCodec.string(refusal, "code", 64),
                        ClassicFieldCodec.string(refusal, "subject", 128)),
                process(ClassicFieldCodec.compound(root, "process")), root.contains("native_plan")
                        ? Optional.of(ClassicPlanCodec.preflight(ClassicFieldCodec.compound(root, "native_plan")))
                        : Optional.empty(), root.contains("last_applied_transaction")
                        ? Optional.of(ClassicFieldCodec.uuid(root, "last_applied_transaction")) : Optional.empty(),
                root.contains("drop_quarantine")
                        ? Optional.of(quarantine(ClassicFieldCodec.compound(root, "drop_quarantine"))) : Optional.empty());
    }

    private static ClassicProcessFrame process(CompoundTag root) {
        String phase = ClassicFieldCodec.string(root, "phase", 16);
        if (phase.equals("idle")) {
            ClassicFieldCodec.fields(root, new String[] {"phase"});
            return new ClassicProcessFrame.Idle();
        }
        ClassicValueChecks.require(phase.equals("work"), "Unknown process phase");
        ClassicFieldCodec.fields(root, new String[] {"phase", "recipe_id", "json_signature", "duration_ticks",
                "energy_per_tick", "progress_ticks", "consumed_energy", "ordinal", "state"});
        return new ClassicProcessFrame.Work(ClassicFieldCodec.id(root, "recipe_id"),
                ClassicFieldCodec.string(root, "json_signature", 64), ClassicFieldCodec.integer(root, "duration_ticks"),
                ClassicFieldCodec.integer(root, "energy_per_tick"), ClassicFieldCodec.integer(root, "progress_ticks"),
                ClassicFieldCodec.number(root, "consumed_energy"), ClassicFieldCodec.number(root, "ordinal"),
                ClassicFieldCodec.enumeration(ProcessMachineState.class, ClassicFieldCodec.string(root, "state", 64), false));
    }

    private static ClassicDropQuarantine quarantine(CompoundTag root) {
        ClassicFieldCodec.fields(root, new String[] {"operation_id", "scope", "uncertain"});
        String scope = ClassicFieldCodec.string(root, "scope", 16);
        ClassicValueChecks.require(scope.equals("controller") || scope.equals("item_hatch"), "Quarantine scope");
        var entries = new ArrayList<ClassicUncertainDrop>();
        for (Tag value : ClassicFieldCodec.list(root, "uncertain", 256)) {
            CompoundTag entry = (CompoundTag) value;
            ClassicFieldCodec.fields(entry, new String[] {"bank_channel", "slot", "entity_uuid"});
            entries.add(new ClassicUncertainDrop(ClassicBankKey.parse(ClassicFieldCodec.string(entry, "bank_channel", 64)),
                    ClassicFieldCodec.integer(entry, "slot"), ClassicFieldCodec.uuid(entry, "entity_uuid")));
        }
        return new ClassicDropQuarantine(ClassicFieldCodec.uuid(root, "operation_id"), scope.equals("controller"), entries);
    }

    static CompoundTag encode(ClassicMachineState machine, GuardTicket ticket) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 1);
        root.putString("machine_kind", machine.machineKind().toString()); root.putUUID("machine_uuid", machine.machineId());
        root.putString("owner_level", machine.ownerLevel().toString());
        root.put("owner_position", ClassicFieldCodec.position(machine.ownerPosition()));
        root.putLong("generation", machine.generation()); root.putInt("rotation", machine.rotation().degrees());
        root.putString("formation_state", machine.formationState().name());
        ListTag assignments = new ListTag();
        for (ClassicAssignment assignment : machine.assignments()) {
            CompoundTag entry = ClassicFieldCodec.position(assignment.position());
            entry.putString("kind", assignment.kind().blockId().toString());
            assignment.bankKey().ifPresent(key -> entry.putString("bank_channel", key.channel()));
            assignments.add(entry);
        }
        root.put("assignments", assignments); root.putLong("batch_ordinal", machine.batchOrdinal());
        CompoundTag refusal = new CompoundTag();
        refusal.putString("code", machine.refusal().code()); refusal.putString("subject", machine.refusal().subject());
        root.put("refusal", refusal); root.put("process", process(machine.process()));
        machine.nativePlan().ifPresent(plan -> root.put("native_plan", ClassicPlanCodec.encode(plan, ticket)));
        machine.lastAppliedTransaction().ifPresent(id -> root.putUUID("last_applied_transaction", id));
        machine.dropQuarantine().ifPresent(value -> root.put("drop_quarantine", quarantine(value)));
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.MACHINE);
        return root;
    }

    private static CompoundTag process(ClassicProcessFrame process) {
        CompoundTag root = new CompoundTag();
        if (process instanceof ClassicProcessFrame.Work work) {
            root.putString("phase", "work"); root.putString("recipe_id", work.recipeId().toString());
            root.putString("json_signature", work.jsonSignature()); root.putInt("duration_ticks", work.durationTicks());
            root.putInt("energy_per_tick", work.energyPerTick()); root.putInt("progress_ticks", work.progressTicks());
            root.putLong("consumed_energy", work.consumedEnergy()); root.putLong("ordinal", work.ordinal());
            root.putString("state", work.state().name());
        } else { root.putString("phase", "idle"); }
        return root;
    }

    private static CompoundTag quarantine(ClassicDropQuarantine quarantine) {
        CompoundTag root = new CompoundTag(); root.putUUID("operation_id", quarantine.operationId());
        root.putString("scope", quarantine.controllerScope() ? "controller" : "item_hatch");
        ListTag uncertain = new ListTag();
        for (ClassicUncertainDrop value : quarantine.uncertain()) {
            CompoundTag entry = new CompoundTag(); entry.putString("bank_channel", value.bankKey().channel());
            entry.putInt("slot", value.slot()); entry.putUUID("entity_uuid", value.entityId()); uncertain.add(entry);
        }
        root.put("uncertain", uncertain); return root;
    }

    private ClassicMachineCodec() { }
}
