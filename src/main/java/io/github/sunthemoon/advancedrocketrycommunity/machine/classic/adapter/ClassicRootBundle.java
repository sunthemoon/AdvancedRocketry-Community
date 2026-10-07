package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Private raw twelve-key data snapshot. Never a supported or publishable controller frame. */
final class ClassicRootBundle {
    enum OwnerType { CONTROLLER, HATCH }
    static final String HATCH = "arce_classic_hatch";
    static final String RESOURCES = "arce_classic_resources";
    static final String MACHINE = "arce_classic_machine";
    static final String JOURNAL = "arce_process_journal";
    static final String MARKER = "arce_recipe_signature";
    static final List<String> KEYS = List.of(HATCH, RESOURCES, MACHINE, JOURNAL, MARKER,
            "arce_multiblock", "arce_part_binding", "arce_process", "arce_precision_resources",
            "arce_machine", "arce_rolling_port", "arce_precision_port");
    private static final Set<String> CONTROLLER_KEYS = Set.of(MACHINE, RESOURCES, JOURNAL, MARKER);
    private static final ClassicNbtLimits LEGACY = new ClassicNbtLimits(65_536, 20, 4_096);
    private static final ClassicNbtLimits MULTIBLOCK = new ClassicNbtLimits(1_310_720, 20, 32_768);

    private final Map<String, Tag> roots;
    private final OwnerType ownerType;
    private final boolean bounded;

    private ClassicRootBundle(Map<String, Tag> roots, OwnerType ownerType, boolean bounded) {
        this.roots = Map.copyOf(roots);
        this.ownerType = ownerType;
        this.bounded = bounded;
    }

    static ClassicRootBundle captureData(CompoundTag parent, OwnerType type) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(type, "type");
        ClassicValueChecks.require(parent.getClass() == CompoundTag.class, "Not a native parent compound");
        // Capture every presence/absence before any recursive preflight or copying.
        Map<String, Tag> captured = new LinkedHashMap<>();
        for (String key : KEYS) {
            if (parent.contains(key)) { captured.put(key, parent.get(key)); }
        }
        boolean bounded = true;
        for (Map.Entry<String, Tag> entry : captured.entrySet()) {
            if (!canCopyLosslessly(entry.getValue(), limits(entry.getKey()))) { bounded = false; }
        }
        if (bounded) {
            CompoundTag projection = new CompoundTag();
            captured.forEach(projection::put);
            bounded = canCopyLosslessly(projection, ClassicNbtLimits.REJECTED);
        }
        if (bounded) {
            Map<String, Tag> detached = new LinkedHashMap<>();
            captured.forEach((key, value) -> detached.put(key, value.copy()));
            captured = detached;
        }
        return new ClassicRootBundle(captured, type, bounded);
    }

    private static boolean canCopyLosslessly(Tag root, ClassicNbtLimits limits) {
        // Shape screens foreign/malformed data before the native getters in lossless preflight.
        return ClassicNbtShape.fits(root, limits)
                && BoundedNbt.fits(root, limits.bytes(), limits.depth(), limits.nodes());
    }

    static ClassicNbtLimits limits(String key) {
        return switch (key) {
            case HATCH -> ClassicNbtLimits.HATCH;
            case RESOURCES -> ClassicNbtLimits.RESOURCES;
            case MACHINE -> ClassicNbtLimits.MACHINE;
            case JOURNAL -> ClassicNbtLimits.JOURNAL;
            case MARKER -> ClassicNbtLimits.MARKER;
            case "arce_multiblock" -> MULTIBLOCK;
            default -> {
                ClassicValueChecks.require(KEYS.contains(key), "Unknown authority key");
                yield LEGACY;
            }
        };
    }

    boolean bounded() { return bounded; }
    boolean present(String key) {
        ClassicValueChecks.require(KEYS.contains(key), "Unknown authority key");
        return roots.containsKey(key);
    }
    boolean forbiddenPresent() {
        return roots.keySet().stream().anyMatch(key -> ownerType == OwnerType.HATCH
                ? !key.equals(HATCH) : !CONTROLLER_KEYS.contains(key));
    }
    boolean missingRequired(boolean typedPlanRequiresJournal) {
        if (ownerType == OwnerType.HATCH) { return !present(HATCH); }
        return !present(MACHINE) || !present(RESOURCES) || !present(MARKER)
                || (typedPlanRequiresJournal && !present(JOURNAL));
    }
    boolean requiresSaveRefusal(boolean typedPlanRequiresJournal) {
        return !bounded || missingRequired(typedPlanRequiresJournal);
    }

    /** Pure data emission; the later guarded codec must supply the typed plan/journal requirement. */
    void emitData(CompoundTag destination, boolean typedPlanRequiresJournal) {
        Objects.requireNonNull(destination, "destination");
        if (requiresSaveRefusal(typedPlanRequiresJournal)) {
            throw new IllegalStateException("Whole-chunk save refusal required; no partial emission");
        }
        ClassicValueChecks.require(destination.getClass() == CompoundTag.class, "Not a native destination");
        // All owned roots were preflighted as one bundle; return new copies, never owned references.
        Map<String, Tag> emitted = new LinkedHashMap<>();
        roots.forEach((key, value) -> emitted.put(key, value.copy()));
        KEYS.forEach(destination::remove);
        emitted.forEach(destination::put);
    }

    boolean retainsBorrowedIdentity(String key, Tag original) {
        return !bounded && present(key) && roots.get(key) == original;
    }

    static ClassicRootBundle captureBounded(CompoundTag parent, OwnerType type, ClassicRawPermit permit) {
        permit.requireCurrent();
        if (permit.purpose() != ClassicRawPurpose.CAPTURE) { throw new IllegalStateException("Not a capture lease"); }
        ClassicRootBundle result = captureData(parent, type);
        permit.requireCurrent(); return result;
    }

    void emitRetainedRoots(CompoundTag destination, boolean planRequiresJournal, ClassicRawPermit permit) {
        permit.requireCurrent();
        if (permit.purpose() != ClassicRawPurpose.EMIT) { throw new IllegalStateException("Not an emission lease"); }
        emitData(destination, planRequiresJournal); permit.requireCurrent();
    }

    CompoundTag forDecode(GuardTicket ticket) {
        ticket.requireValid();
        if (!bounded) { throw new IllegalStateException("Unbounded roots cannot be copied for decode"); }
        CompoundTag result = new CompoundTag(); roots.forEach((key, value) -> result.put(key, value.copy()));
        ticket.requireValid(); return result;
    }

    boolean controllerPlanRequiresJournal() {
        if (ownerType != OwnerType.CONTROLLER || !bounded) { return false; }
        Tag value = roots.get(MACHINE);
        return value instanceof CompoundTag root && root.contains("schema_version", Tag.TAG_INT)
                && root.getInt("schema_version") == 1 && root.contains("native_plan", Tag.TAG_COMPOUND);
    }
}
