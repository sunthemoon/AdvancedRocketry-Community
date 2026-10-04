package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import java.util.Optional;
import net.minecraft.nbt.Tag;

/** Refusal is explicit: no rejected/missing input is replaced by fresh empty resources. */
public final class ClassicResourcesDecode {
    public enum Status { SUPPORTED, MISSING, UNBOUNDED, UNSUPPORTED_SCHEMA, INVALID_DATA, OWNER_CONFLICT }

    private final Status status;
    private final ClassicResources value;
    private final Tag retained;

    private ClassicResourcesDecode(Status status, ClassicResources value, Tag retained) {
        this.status = status;
        this.value = value;
        // A structurally/byte-invalid root cannot safely be recursively copied. Borrow only
        // its identity, never expose it for mutation; its adapter must preserve the parent.
        this.retained = retained == null || status == Status.UNBOUNDED ? retained : retained.copy();
    }

    static ClassicResourcesDecode supported(ClassicResources value) {
        return new ClassicResourcesDecode(Status.SUPPORTED, value, null);
    }

    static ClassicResourcesDecode refused(Status status, Tag raw) {
        return new ClassicResourcesDecode(status, null, raw);
    }

    public Status status() { return status; }
    public Optional<ClassicResources> value() { return Optional.ofNullable(value); }
    public boolean requiresSaveRefusal() { return status == Status.MISSING || status == Status.UNBOUNDED; }

    /** True only for the borrowed oversized input, which this object never copies or changes. */
    public boolean retainsUnboundedIdentity(Tag raw) { return status == Status.UNBOUNDED && retained == raw; }

    /** Pure NBT snapshot, not a chunk writer. UNBOUNDED/MISSING must veto ordinary save upstream. */
    public Tag encodeForSave() {
        if (requiresSaveRefusal()) { throw new IllegalStateException("Resource root requires repair or guarded save refusal"); }
        return status == Status.SUPPORTED ? ClassicResourcesCodec.encode(value) : retained.copy();
    }
}
