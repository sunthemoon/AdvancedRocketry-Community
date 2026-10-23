package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Read-only test-artifact observation of bounded private scheduling state. */
final class W151NativeReturnsAccess {
    private W151NativeReturnsAccess() { }
    @SuppressWarnings("unchecked") static <T> T field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
