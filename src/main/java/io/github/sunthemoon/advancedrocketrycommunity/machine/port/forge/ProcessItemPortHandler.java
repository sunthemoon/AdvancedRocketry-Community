package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Range-, mode-, filter- and lock-enforcing Item capability view. */
public final class ProcessItemPortHandler implements IItemHandler {
    private final IItemHandler delegate;
    private final ProcessPortDefinition port;
    private final BooleanSupplier processLocked;
    private final ProcessPortRevision revision;

    public ProcessItemPortHandler(
            IItemHandler delegate,
            ProcessPortDefinition port,
            BooleanSupplier processLocked,
            ProcessPortRevision revision
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.port = Objects.requireNonNull(port, "port");
        this.processLocked = Objects.requireNonNull(processLocked, "processLocked");
        this.revision = Objects.requireNonNull(revision, "revision");
        if (port.kind() != ProcessPortKind.ITEM || port.range().endExclusive() > delegate.getSlots()) {
            throw new IllegalArgumentException("item port does not fit its delegate");
        }
    }

    @Override
    public int getSlots() {
        return port.range().count();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(toDelegateSlot(slot));
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!port.canInsert(processLocked.getAsBoolean()) || !allows(stack)) {
            return stack;
        }
        ItemStack remainder = delegate.insertItem(toDelegateSlot(slot), stack, simulate);
        if (!simulate && remainder.getCount() < stack.getCount()) {
            revision.recordMutation();
        }
        return remainder;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !port.canExtract(processLocked.getAsBoolean())) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = delegate.extractItem(toDelegateSlot(slot), amount, simulate);
        if (!simulate && !extracted.isEmpty()) {
            revision.recordMutation();
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(toDelegateSlot(slot));
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return port.canInsert(processLocked.getAsBoolean())
                && allows(stack)
                && delegate.isItemValid(toDelegateSlot(slot), stack);
    }

    private boolean allows(ItemStack stack) {
        return stack.isEmpty() || port.filter().allows(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private int toDelegateSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new IndexOutOfBoundsException("port slot is outside the exposed range");
        }
        return port.range().first() + slot;
    }
}
