package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.Objects;
import java.util.OptionalInt;

record ClassicNativeEntry(ClassicBankKey bankKey, OptionalInt slot, String resourceId,
                          long capacity, OwnedNativeTag before, OwnedNativeTag after) {
    ClassicNativeEntry {
        Objects.requireNonNull(bankKey, "bankKey");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        ClassicValueChecks.id(resourceId);
        boolean item = bankKey.kind().isItem();
        ClassicValueChecks.require(slot.isPresent() == item
                && (!item || (slot.getAsInt() >= 0 && slot.getAsInt() <= 3)), "Native entry slot");
        ClassicValueChecks.require(item ? capacity >= 1 && capacity <= 64 : capacity == 16_000,
                "Native entry capacity");
        ClassicValueChecks.require(before.item() == item && after.item() == item
                && before.amount() <= capacity && after.amount() <= capacity
                && (before.amount() > 0 || after.amount() > 0), "Native entry payload amounts");
        ClassicValueChecks.require((before.amount() == 0 || resourceId.equals(before.resourceId()))
                && (after.amount() == 0 || resourceId.equals(after.resourceId())), "Native entry identity");
        ClassicValueChecks.require(bankKey.kind().isInput() ? before.amount() >= after.amount()
                : before.amount() <= after.amount(), "Native entry direction");
        if (before.amount() > 0 && after.amount() > 0) {
            ClassicValueChecks.require(before.sameMetadata(after), "Native metadata changed during remainder/merge");
        }
    }

    ProcessResourceKey quantitativeKey() {
        return new ProcessResourceKey(bankKey.kind().isItem() ? ProcessResourceKind.ITEM : ProcessResourceKind.FLUID,
                bankKey.channel() + (slot.isPresent() ? ".s" + slot.getAsInt() : ""), resourceId);
    }
}
