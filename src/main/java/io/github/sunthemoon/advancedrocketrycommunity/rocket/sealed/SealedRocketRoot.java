package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable ownership of a native loaded tag, independently of whether this version can decode it. */
public final class SealedRocketRoot {
    private final Tag held;
    private final Optional<SealedRocketMember> member;

    private SealedRocketRoot(Tag held, Optional<SealedRocketMember> member) {
        this.held = held;
        this.member = member;
    }

    public static SealedRocketRoot absent() { return new SealedRocketRoot(null, Optional.empty()); }

    /** Decode is bounded before any copy. Native-loaded invalid data is preserved, not made into an empty member. */
    public static SealedRocketRoot load(CompoundTag outer) {
        Tag raw = outer.get(SealedRocketMember.ROOT);
        var decoded = SealedRocketMember.decode(raw);
        return new SealedRocketRoot(raw == null ? null : raw.copy(), decoded);
    }

    /** All staging/framing validation happens here, before a world mutation can be attempted. */
    public static SealedRocketRoot stage(SealedRocketMember member) {
        return new SealedRocketRoot(Objects.requireNonNull(member).encode(), Optional.of(member));
    }

    public Optional<SealedRocketMember> member() { return member; }

    /** Native list writing may normalize its argument, so even a save must not receive our retained object. */
    public void save(CompoundTag outer) {
        if (held == null) { outer.remove(SealedRocketMember.ROOT); }
        else { outer.put(SealedRocketMember.ROOT, held.copy()); }
    }
}
