package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowRuntimeCargoStopTest {
    @BeforeAll static void bootstrap() throws Exception {
        MinecraftBootstrap.initialize(); EscrowTestBootstrap.initialize();
    }
    @TempDir Path temp;

    @Test void offThreadCrashStopReachesSettlementCleanupWithCargoAllocated() throws Exception {
        Checks.crashStop(temp);
    }

    @Test void offThreadProviderInvalidationStillInvalidatesCapability() throws Exception {
        Checks.invalidate();
    }

    // As in the audit fixture, defer runtime type resolution until the capability bootstrap has defined it.
    private static final class Checks {
    static void crashStop(Path temp) throws Exception {
        var server = UnstartedServer.owned();
        var runtime = new EscrowRuntime();
        var cargo = runtime.cargoPreparations();
        var level = UnstartedServer.allocate(StopLevel.class);
        level.storage = TestDataStorage.over(temp); level.runtime = LazyOptional.of(() -> runtime);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        EscrowSettlementOperator.PreviewCache cache = UnstartedServer.get(EscrowSettlementOperator.class, runtime.settlement(), "previews");
        Map<java.util.UUID, EscrowSettlementIntent> entries = UnstartedServer.get(EscrowSettlementOperator.PreviewCache.class, cache, "entries");
        entries.put(java.util.UUID.randomUUID(), null); // Only close/clear is exercised; no preview is consumed.
        var stop = EscrowRuntime.class.getDeclaredMethod("crashStop", MinecraftServer.class, ServerLevel.class);
        stop.setAccessible(true);
        offThread(() -> stop.invoke(null, server, level));
        assertEquals(0, cache.size(), "Cargo close interrupted settlement cleanup");
        assertTrue(UnstartedServer.<Boolean>get(EscrowRuntime.class, runtime, "stopping"));
        cargo.close();
    }

    static void invalidate() throws Exception {
        var type = Class.forName(EscrowRuntime.class.getName() + "$Provider");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        var provider = constructor.newInstance();
        EscrowRuntime runtime = UnstartedServer.get(type, provider, "runtime");
        var cargo = runtime.cargoPreparations();
        LazyOptional<EscrowRuntime> value = UnstartedServer.get(type, provider, "value");
        assertTrue(value.isPresent());
        var invalidate = type.getDeclaredMethod("invalidate"); invalidate.setAccessible(true);
        offThread(() -> invalidate.invoke(provider));
        assertFalse(value.isPresent(), "Cargo close interrupted capability invalidation");
        cargo.close();
    }

    private static void offThread(Throwing action) throws InterruptedException {
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        worker.start(); worker.join(5_000);
        assertFalse(worker.isAlive()); assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    private static final class StopLevel extends ServerLevel {
        DimensionDataStorage storage;
        LazyOptional<EscrowRuntime> runtime;
        StopLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == EscrowRuntime.CAPABILITY ? runtime.cast() : LazyOptional.empty();
        }
    }
    }
}
