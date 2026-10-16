package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankItem;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Design section 4: a {@code pressurized_tank} that holds no Fluid. A plain item ingredient matches the tank whatever
 * its item tag, so crafting would consume a filled tank and delete its Fluid; this one matches only a placeable tank
 * whose tag has no tank root or a root that decodes to an empty Fluid. It takes no parameters.
 */
public final class EmptyTankIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = ModIdentity.id("empty_pressurized_tank");

    public EmptyTankIngredient() {
        super(Stream.of(new Ingredient.ItemValue(new ItemStack(ModItems.PRESSURIZED_TANK.get()))));
    }

    /** An empty, placeable pressurized tank; a filled tank or one with an unreadable root never matches. */
    public static boolean emptyTank(@Nullable ItemStack stack) {
        if (stack == null || !stack.is(ModItems.PRESSURIZED_TANK.get()) || !PressurizedTankItem.placeable(stack)) {
            return false;
        }
        if (!stack.hasTag() || !stack.getTag().contains(TankSave.ROOT)) {
            return true;
        }
        try {
            return TankSave.decode(stack.getTag().get(TankSave.ROOT)).isEmpty();
        } catch (IllegalArgumentException refused) {
            return false;
        }
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return emptyTank(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<EmptyTankIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public EmptyTankIngredient parse(FriendlyByteBuf buffer) {
            return new EmptyTankIngredient();
        }

        @Override
        public EmptyTankIngredient parse(JsonObject json) {
            if (json.size() != 1) {
                throw new JsonParseException("The empty pressurized tank ingredient takes no fields");
            }
            return new EmptyTankIngredient();
        }

        @Override
        public void write(FriendlyByteBuf buffer, EmptyTankIngredient ingredient) {
        }
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
                CraftingHelper.register(ID, Serializer.INSTANCE);
            }
        }
    }
}
