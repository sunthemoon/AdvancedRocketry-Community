package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import net.minecraft.nbt.CompoundTag;

/** Entire twelve-root raw handoff. It is never a supported or publishable frame. */
final class ClassicPendingLoad {
    private final ClassicRootBundle roots;
    private final boolean planRequiresJournal;
    private final ClassicOwnerState owner;

    private ClassicPendingLoad(ClassicRootBundle roots, boolean planRequiresJournal, ClassicOwnerState owner) {
        this.roots = roots; this.planRequiresJournal = planRequiresJournal; this.owner = owner;
    }

    static ClassicPendingLoad capture(CompoundTag parent, ClassicRootBundle.OwnerType type, ClassicRawPermit permit) {
        permit.requireCurrent();
        if (permit.purpose() != ClassicRawPurpose.CAPTURE) { throw new IllegalStateException("Not a capture lease"); }
        ClassicRootBundle roots = ClassicRootBundle.captureBounded(parent, type, permit);
        boolean plan = roots.controllerPlanRequiresJournal();
        permit.requireCurrent(); return new ClassicPendingLoad(roots, plan, permit.state());
    }

    boolean requiresSaveRefusal() { return roots.requiresSaveRefusal(planRequiresJournal); }
    ClassicRootBundle roots() { return roots; }
    boolean planRequiresJournal() { return planRequiresJournal; }
    boolean ownedBy(ClassicOwnerState state) { return owner == state; }
    void emit(CompoundTag destination, ClassicRawPermit permit) {
        if (permit.state() != owner) { throw new IllegalStateException("Foreign pending-load emission"); }
        roots.emitRetainedRoots(destination, planRequiresJournal, permit);
    }
}
