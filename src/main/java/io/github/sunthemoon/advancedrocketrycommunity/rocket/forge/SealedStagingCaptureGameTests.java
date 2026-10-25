package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.function.Function;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;

/** Host-side wiring for the independent fixture's platform probe, active only during its GameTest batch. */
@GameTestHolder(ModIdentity.MOD_ID)
public final class SealedStagingCaptureGameTests {
    @BeforeBatch(batch = "w152_probe")
    public static void install(ServerLevel level) {
        var adapter = new VanillaContainerRocketAdapter();
        setCapture(target -> adapter.capture(target).data());
    }

    @AfterBatch(batch = "w152_probe")
    public static void clear(ServerLevel level) { setCapture(null); }

    private static void setCapture(Function<BlockEntity, CompoundTag> capture) {
        try {
            Class.forName("io.github.sunthemoon.arceadaptertest.SealedStagingProbeGameTests")
                    .getMethod("setBuiltInContainerCapture", Function.class).invoke(null, capture);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("The sealed staging probe fixture is unavailable", failure);
        }
    }

    private SealedStagingCaptureGameTests() { }
}
