package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartBindingVerifierTest {
    private static final BlockPos CONTROLLER = new BlockPos(32, 70, -16);
    private static final UUID INSTANCE = UUID.randomUUID();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void wrongLevelAndUnloadedControllerNeverResolveControllerState() {
        AtomicInteger loadChecks = new AtomicInteger();
        AtomicInteger resolutions = new AtomicInteger();

        assertEquals(PartBindingValidationStatus.WRONG_LEVEL, PartBindingVerifier.verify(
                binding(),
                Level.NETHER,
                ignored -> {
                    loadChecks.incrementAndGet();
                    return true;
                },
                ignored -> {
                    resolutions.incrementAndGet();
                    return Optional.empty();
                }
        ));
        assertEquals(0, loadChecks.get());
        assertEquals(0, resolutions.get());

        assertEquals(PartBindingValidationStatus.CONTROLLER_UNLOADED, PartBindingVerifier.verify(
                binding(),
                Level.OVERWORLD,
                ignored -> {
                    loadChecks.incrementAndGet();
                    return false;
                },
                ignored -> {
                    resolutions.incrementAndGet();
                    return Optional.empty();
                }
        ));
        assertEquals(1, loadChecks.get());
        assertEquals(0, resolutions.get());
    }

    @Test
    void rejectsMissingWrongStaleAndInactiveController() {
        assertEquals(PartBindingValidationStatus.CONTROLLER_MISSING, verify(Optional.empty()));
        assertEquals(PartBindingValidationStatus.WRONG_INSTANCE, verify(Optional.of(
                new ControllerBindingView(UUID.randomUUID(), 4, MultiblockFormationState.FORMED)
        )));
        assertEquals(PartBindingValidationStatus.STALE_GENERATION, verify(Optional.of(
                new ControllerBindingView(INSTANCE, 5, MultiblockFormationState.FORMED)
        )));
        assertEquals(PartBindingValidationStatus.CONTROLLER_INACTIVE, verify(Optional.of(
                new ControllerBindingView(INSTANCE, 4, MultiblockFormationState.UNFORMED)
        )));
    }

    @Test
    void acceptsExactActiveBinding() {
        assertEquals(PartBindingValidationStatus.VALID, verify(Optional.of(
                new ControllerBindingView(INSTANCE, 4, MultiblockFormationState.FORMED)
        )));
        assertEquals(PartBindingValidationStatus.VALID, verify(Optional.of(
                new ControllerBindingView(INSTANCE, 4, MultiblockFormationState.WAITING_UNLOADED)
        )));
    }

    private static PartBindingValidationStatus verify(Optional<ControllerBindingView> view) {
        return PartBindingVerifier.verify(binding(), Level.OVERWORLD, ignored -> true, ignored -> view);
    }

    private static MultiblockPartBinding binding() {
        return new MultiblockPartBinding(1, Level.OVERWORLD, CONTROLLER, INSTANCE, 4);
    }
}
