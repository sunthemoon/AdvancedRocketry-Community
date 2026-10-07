package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Data-only bridge. A supplied check does not create owner or publication authority. */
public final class ClassicGuardedResourceAccess {
    public static ClassicResourcesDecode decode(Tag raw, UUID expectedMachineId, Runnable requireWitnesses) {
        check(requireWitnesses);
        ClassicResourcesDecode result = ClassicResourcesCodec.decode(raw, expectedMachineId, requireWitnesses);
        check(requireWitnesses);
        return result;
    }

    public static CompoundTag encode(ClassicResources resources, Runnable requireWitnesses) {
        check(requireWitnesses);
        CompoundTag result = ClassicResourcesCodec.encode(resources);
        check(requireWitnesses);
        return result;
    }

    static void check(Runnable requireWitnesses) {
        Objects.requireNonNull(requireWitnesses, "requireWitnesses");
        try { requireWitnesses.run(); }
        catch (WitnessFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw new WitnessFailure(failure); }
    }

    static <T> T nativeCall(Runnable requireWitnesses, Supplier<T> operation) {
        check(requireWitnesses);
        T result = operation.get();
        check(requireWitnesses);
        return result;
    }

    /** Never converted to INVALID_DATA by the ordinary native validation catch. */
    static final class WitnessFailure extends RuntimeException {
        private WitnessFailure(RuntimeException cause) { super("Resource witness failed", cause); }
    }

    private ClassicGuardedResourceAccess() { }
}
