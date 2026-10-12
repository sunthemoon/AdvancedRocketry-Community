package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ADR-066 section 7.1 hydrogen and suit HUD panels, physical client only. The summary is recomputed every client
 * tick from the local player's server-synchronized, correctly worn suit pieces with the bounded equipment codec, and
 * only while the server authorizes classic equipment. It is display truth only: the HUD never predicts gas use.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SuitHudClient {
    private static final List<EquipmentSlot> SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static Summary current = Summary.EMPTY;

    /** The worn hydrogen reserve and the distinct installed module kinds, in {@link ModuleKind} order. */
    public record Summary(int hydrogen, int hydrogenMaximum, List<ModuleKind> modules) {
        public static final Summary EMPTY = new Summary(0, 0, List.of());

        public Summary {
            modules = List.copyOf(modules);
            if (hydrogen < 0 || hydrogenMaximum < 0 || hydrogen > hydrogenMaximum || hydrogenMaximum > 32_000
                    || modules.size() > ModuleKind.values().length || modules.size() != Set.copyOf(modules).size()) {
                throw new IllegalArgumentException("Suit HUD summary is outside its finite range");
            }
        }

        public boolean showsHydrogen() { return hydrogenMaximum > 0 || modules.contains(ModuleKind.JETPACK); }
        public boolean showsSuit() { return !modules.isEmpty(); }
    }

    public static Summary current() { return current; }

    /** The summary for one entity's worn suit; pieces in the wrong slot or with refused roots contribute nothing. */
    public static Summary summarize(LivingEntity wearer) {
        EnumSet<ModuleKind> kinds = EnumSet.noneOf(ModuleKind.class);
        ArmorEquipment.Reserve hydrogen = new ArmorEquipment.Reserve(0, 0);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = wearer.getItemBySlot(slot);
            if (!(stack.getItem() instanceof SpaceSuitArmorItem armor) || armor.getEquipmentSlot() != slot) { continue; }
            var modules = ArmorEquipment.modules(stack);
            if (modules == null) { continue; }
            modules.forEach(module -> kinds.add(module.kind()));
            if (slot == EquipmentSlot.CHEST) { hydrogen = ArmorEquipment.hydrogen(stack); }
        }
        return new Summary(hydrogen.stored(), hydrogen.maximum(), List.copyOf(kinds));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        var player = Minecraft.getInstance().player;
        current = player == null || Minecraft.getInstance().level == null || !SuitVisorClient.equipmentAuthorized()
                ? Summary.EMPTY : summarize(player);
    }

    @SubscribeEvent public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) { current = Summary.EMPTY; }

    @SubscribeEvent public static void levelChanged(ClientPlayerNetworkEvent.Clone event) { current = Summary.EMPTY; }

    private SuitHudClient() { }
}
