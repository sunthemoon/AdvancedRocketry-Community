package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record ClassicAssignment(BlockPos position, ClassicHatchKind kind,
                                Optional<ClassicBankKey> bankKey) {
    static final Comparator<ClassicAssignment> ORDER = Comparator
            .comparingInt((ClassicAssignment a) -> a.position().getY())
            .thenComparingInt(a -> a.position().getZ()).thenComparingInt(a -> a.position().getX())
            .thenComparing(a -> a.kind().blockId().toString());

    public ClassicAssignment {
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(bankKey, "bankKey");
        ClassicValueChecks.require(bankKey.isPresent() == kind.bankKind().isPresent(), "Assignment bank role");
        if (bankKey.isPresent()) {
            ClassicBankKey key = bankKey.orElseThrow();
            ClassicValueChecks.require(kind.bankKind().orElseThrow() == key.kind()
                    && key.x() == position.getX() && key.y() == position.getY() && key.z() == position.getZ(),
                    "Assignment bank position or kind differs");
        }
    }
}
