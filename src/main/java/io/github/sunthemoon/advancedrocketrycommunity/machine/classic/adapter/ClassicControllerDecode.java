package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

final class ClassicControllerDecode {
    private final ClassicDecodeStatus status;
    private final ClassicControllerFrame supported;
    private final ClassicRootBundle roots;
    private final ClassicOwnerState owner;
    private final boolean planRequiresJournal;

    ClassicControllerDecode(ClassicDecodeStatus status, ClassicControllerFrame supported,
            ClassicRootBundle roots, ClassicOwnerState owner, boolean planRequiresJournal) {
        ClassicValueChecks.require((status == ClassicDecodeStatus.SUPPORTED) == (supported != null), "Decode status/value");
        this.status = status; this.supported = supported; this.roots = roots; this.owner = owner;
        this.planRequiresJournal = planRequiresJournal;
    }

    ClassicDecodeStatus status() { return status; }
    Optional<ClassicControllerFrame> supported() { return Optional.ofNullable(supported); }
    boolean requiresSaveRefusal() { return roots.requiresSaveRefusal(planRequiresJournal); }
    void emitRetainedRoots(CompoundTag destination, ClassicRawPermit permit) {
        if (permit.state() != owner) { throw new IllegalStateException("Foreign controller raw lease"); }
        roots.emitRetainedRoots(destination, planRequiresJournal, permit);
    }
}
