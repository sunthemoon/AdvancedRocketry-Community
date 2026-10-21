package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

record EscrowItemReconciliationFixture(EscrowLedger ledger, EscrowOperation op, CompoundTag before, CompoundTag after,
                                      CompoundTag deviceBefore, CompoundTag deviceAfter) {
    static EscrowItemReconciliationFixture create(boolean load) {
        var stack = EscrowItemCargoEligibilityTest.stone(-1); var tag = new CompoundTag();
        tag.putByte("b", (byte) 7); tag.putShort("s", (short) 7); tag.putInt("i", 7); tag.putLong("l", 7);
        tag.putByteArray("ba", new byte[] {1, 2}); tag.putIntArray("ia", new int[] {1, 2});
        tag.putLongArray("la", new long[] {1, 2}); stack.put("tag", tag);
        var c = EscrowItemCargoEligibilityTest.sample(load, 5, stack);
        var ledger = EscrowTestFixtures.ledger();
        var op = EscrowItemCargoEligibility.check(ledger, List.of(), c.op(), c.before(), c.after(),
                c.deviceBefore(), c.deviceAfter()).capture().operation();
        return new EscrowItemReconciliationFixture(ledger.append(List.of(op)), op, c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
    }
    static CompoundTag slot(CompoundTag... entries) {
        var slot = new CompoundTag(); slot.putInt("xPos", 0); slot.putInt("zPos", 0);
        var list = new ListTag(); for (var entry : entries) { list.add(entry); } slot.put("block_entities", list); return slot;
    }
}

