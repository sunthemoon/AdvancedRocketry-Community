package consumer;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterSuitEquipmentEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitEquipmentRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public final class SuitApiConsumer implements SuitOxygenProvider {
    public static void register(RegisterSuitEquipmentEvent event, ResourceLocation id) {
        SuitEquipmentRegistrar registrar = event::register;
        registrar.register(id, Map.of(id, EquipmentSlot.CHEST), 1, new SuitApiConsumer());
    }

    public OptionalInt readOxygen(CompoundTag data) {
        return OptionalInt.of(data.getInt("oxygen"));
    }

    public CompoundTag writeOxygen(CompoundTag data, int oxygenUnits) {
        data.putInt("oxygen", oxygenUnits);
        return data;
    }
}
