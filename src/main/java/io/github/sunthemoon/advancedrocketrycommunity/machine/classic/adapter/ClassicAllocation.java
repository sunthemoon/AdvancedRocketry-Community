package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

record ClassicAllocation(boolean input, ProcessResourceKind kind, int recipeIndex,
                          String resourceId, long amount, List<ClassicAllocationPart> parts) {
    static final Comparator<ClassicAllocation> ORDER = Comparator
            .comparingInt((ClassicAllocation a) -> a.input() ? 0 : 1)
            .thenComparing(a -> a.kind()).thenComparingInt(a -> a.recipeIndex());

    ClassicAllocation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(parts, "parts");
        ClassicValueChecks.id(resourceId);
        boolean item = kind == ProcessResourceKind.ITEM;
        ClassicValueChecks.require(recipeIndex >= 0 && recipeIndex <= (item ? 3 : 1)
                && amount >= 1 && amount <= (item ? 64 : 16_000)
                && !parts.isEmpty() && parts.size() <= 64, "Allocation bounds");
        int previous = -1;
        long sum = 0;
        for (ClassicAllocationPart part : parts) {
            ClassicValueChecks.require(part.entryIndex() > previous, "Allocation part order/duplicate");
            previous = part.entryIndex();
            sum = Math.addExact(sum, part.amount());
        }
        ClassicValueChecks.require(sum == amount, "Allocation sum differs");
        parts = List.copyOf(parts);
    }
}
