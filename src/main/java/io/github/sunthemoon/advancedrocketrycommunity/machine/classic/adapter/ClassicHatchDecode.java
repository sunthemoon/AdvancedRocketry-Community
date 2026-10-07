package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

final class ClassicHatchDecode {
    private final ClassicDecodeStatus status;
    private final ClassicHatchState supported;
    private final ClassicRootBundle roots;
    private final ClassicOwnerState owner;

    ClassicHatchDecode(ClassicDecodeStatus status, ClassicHatchState supported, ClassicRootBundle roots, ClassicOwnerState owner) {
        ClassicValueChecks.require((status == ClassicDecodeStatus.SUPPORTED) == (supported != null), "Decode status/value");
        this.status = status; this.supported = supported; this.roots = roots; this.owner = owner;
    }

    ClassicDecodeStatus status() { return status; }
    Optional<ClassicHatchState> supported() { return Optional.ofNullable(supported); }
    boolean requiresSaveRefusal() { return roots.requiresSaveRefusal(false); }
    void emitRetainedRoots(CompoundTag destination, ClassicRawPermit permit) {
        if (permit.state() != owner) { throw new IllegalStateException("Foreign hatch raw lease"); }
        roots.emitRetainedRoots(destination, false, permit);
    }
}
