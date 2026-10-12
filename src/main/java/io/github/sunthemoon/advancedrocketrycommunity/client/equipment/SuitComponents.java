package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The visible suit mounts of one wearer (ADR-066 section 7.1): a jetpack, up to two pressure tanks with their gas,
 * and a visor plate. Only module kinds and tank gases are kept, read from the server-synchronized worn chestplate and
 * helmet through the bounded equipment codec; a refused root shows nothing. At most {@link #MAX_ATTACHMENTS}
 * attachments.
 */
public record SuitComponents(boolean jetpack, List<TankData.Gas> tanks, ModuleKind visor) {
    public static final int MAX_ATTACHMENTS = 8;
    public static final int MAX_TANKS = 2;
    public static final SuitComponents NONE = new SuitComponents(false, List.of(), null);

    public SuitComponents {
        tanks = List.copyOf(tanks);
        if (tanks.size() > MAX_TANKS) {
            throw new IllegalArgumentException("At most " + MAX_TANKS + " tanks are mounted");
        }
        if (visor != null && visor != ModuleKind.ANTI_FOG && visor != ModuleKind.EARTHBRIGHT) {
            throw new IllegalArgumentException("Only visors have a visor plate: " + visor);
        }
    }

    public boolean empty() { return !jetpack && tanks.isEmpty() && visor == null; }

    /** Drawn parts: the jetpack frame, its two tanks and two nozzles, loose tanks without it, and the visor. */
    public int attachments() {
        return (jetpack ? 5 : tanks.size()) + (visor == null ? 0 : 1);
    }

    /** The tank gas at a jetpack or back position, EMPTY when no tank is installed there. */
    public TankData.Gas tank(int index) {
        Objects.checkIndex(index, MAX_TANKS);
        return index < tanks.size() ? tanks.get(index) : TankData.Gas.EMPTY;
    }

    public static SuitComponents of(LivingEntity wearer) {
        boolean jetpack = false;
        List<TankData.Gas> tanks = new ArrayList<>(MAX_TANKS);
        List<ArmorRootCodec.Module> chest = worn(wearer, EquipmentSlot.CHEST);
        chest.sort(Comparator.comparingInt(ArmorRootCodec.Module::position));
        for (ArmorRootCodec.Module module : chest) {
            if (module.kind() == ModuleKind.JETPACK) {
                jetpack = true;
            } else if (module.kind() == ModuleKind.PRESSURE_TANK && tanks.size() < MAX_TANKS) {
                TankData tank = TankData.read(module.stack().getCompound("tag").get(TankData.KEY),
                        TankData.baseCapacity(module.stack().getString("id")), 1);
                tanks.add(tank == null ? TankData.Gas.EMPTY : tank.gas());
            }
        }
        ModuleKind visor = null;
        for (ArmorRootCodec.Module module : worn(wearer, EquipmentSlot.HEAD)) {
            if (module.kind() == ModuleKind.EARTHBRIGHT || module.kind() == ModuleKind.ANTI_FOG && visor == null) {
                visor = module.kind();
            }
        }
        return jetpack || !tanks.isEmpty() || visor != null ? new SuitComponents(jetpack, tanks, visor) : NONE;
    }

    private static List<ArmorRootCodec.Module> worn(LivingEntity wearer, EquipmentSlot slot) {
        ItemStack stack = wearer.getItemBySlot(slot);
        if (!(stack.getItem() instanceof SpaceSuitArmorItem armor) || armor.getEquipmentSlot() != slot) {
            return new ArrayList<>();
        }
        List<ArmorRootCodec.Module> modules = ArmorEquipment.modules(stack);
        return modules == null ? new ArrayList<>() : new ArrayList<>(modules);
    }
}
