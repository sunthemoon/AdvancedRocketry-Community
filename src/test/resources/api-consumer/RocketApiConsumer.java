package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketAdaptersEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketAdapterRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.IModBusEvent;

import java.util.Set;

public final class RocketApiConsumer implements RocketBlockEntityAdapter {
    @Override
    public boolean canMove(BlockEntity blockEntity) {
        return blockEntity != null;
    }

    @Override
    public CompoundTag capture(BlockEntity blockEntity) {
        return new CompoundTag();
    }

    @Override
    public boolean restore(BlockEntity blockEntity, CompoundTag data) {
        return blockEntity != null && data != null;
    }

    public static boolean invokeAdapter(RocketBlockEntityAdapter adapter, BlockEntity blockEntity,
                                        CompoundTag data) {
        boolean movable = adapter.canMove(blockEntity);
        CompoundTag captured = adapter.capture(blockEntity);
        boolean restored = adapter.restore(blockEntity, data);
        return movable && captured != null && restored;
    }

    @SubscribeEvent
    public static void register(RegisterRocketAdaptersEvent event) {
        event.register(new ResourceLocation("consumer", "container_v1"),
                Set.of(new ResourceLocation("consumer", "container")), 1, new RocketApiConsumer());
    }

    public static void registerDirectly(RocketAdapterRegistrar registrar) {
        registrar.register(new ResourceLocation("consumer", "container_v1"),
                Set.of(new ResourceLocation("consumer", "container")), 1, new RocketApiConsumer());
    }

    public static IModBusEvent eventForHarness(RocketAdapterRegistrar registrar) {
        return new RegisterRocketAdaptersEvent(registrar);
    }

    public static Event forgeEvent(RegisterRocketAdaptersEvent event) {
        return event;
    }
}
