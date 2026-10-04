package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

record ClassicAllocationPart(int entryIndex, long amount) {
    ClassicAllocationPart {
        ClassicValueChecks.require(entryIndex >= 0 && entryIndex <= 63 && amount >= 1 && amount <= 16_000,
                "Allocation part bounds");
    }
}
