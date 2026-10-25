package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native-test-JAR-only fault: fail detached restore after filling the target, before any publication. */
final class W152NativeFailure implements RocketBlockEntityAdapter {
    private final RocketBlockEntityAdapter delegate;
    private BlockEntity target;
    private boolean injected;

    private W152NativeFailure(RocketBlockEntityAdapter delegate) { this.delegate = delegate; }
    @SuppressWarnings("unchecked")
    static W152NativeFailure install() {
        try {
            var manager = W152NativeProbe.field(RocketRuntime.class, "service").get(null);
            var executor = (RocketTransactionExecutor) W152NativeProbe.field(RocketManager.class, "transactions").get(manager);
            var adapters = executor.adapters(); var field = W152NativeProbe.field(RocketBlockEntityAdapters.class, "byId");
            var map = new HashMap<>((Map<ResourceLocation, RocketBlockEntityAdapter>) field.get(adapters));
            var fault = new W152NativeFailure(map.get(VanillaContainerRocketAdapter.ID));
            if (fault.delegate == null) { throw new IllegalStateException("Native container adapter absent"); }
            map.put(fault.id(), fault); field.set(adapters, Map.copyOf(map)); return fault;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    void verifyAborted() {
        if (!injected || target == null || !target.isRemoved() || target.getLevel() != null || !((Container) target).isEmpty()) {
            throw new IllegalStateException("Failed native preparation did not abort empty and detached");
        }
    }
    @Override public ResourceLocation id() { return delegate.id(); }
    @Override public boolean supports(BlockEntity value) { return delegate.supports(value); }
    @Override public boolean canRestorePayload(RocketBlockEntityPayload value) { return delegate.canRestorePayload(value); }
    @Override public RocketBlockEntityPayload capture(BlockEntity value) { return delegate.capture(value); }
    @Override public boolean restore(BlockEntity value, RocketBlockEntityPayload payload) { return delegate.restore(value, payload); }
    @Override public RocketBlockEntityPayload captureDetached(Thread owner, BlockEntity value) { return delegate.captureDetached(owner, value); }
    @Override public boolean restoreDetached(Thread owner, BlockEntity value, RocketBlockEntityPayload payload) {
        boolean restored = delegate.restoreDetached(owner, value, payload);
        if (!injected) {
            if (!restored || ((Container) value).isEmpty()) { throw new IllegalStateException("Native failure fixture has no restored resource"); }
            target = value; injected = true; return false;
        }
        return restored;
    }
}
