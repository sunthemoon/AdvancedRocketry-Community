package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Built-in schema-1 cargo host. Capacity is configuration, never payload state. */
public final class PressurizedTankRocketAdapter implements RocketBlockEntityAdapter {
    public static final ResourceLocation ID = ModIdentity.id("pressurized_tank_v1");
    public static final ResourceLocation TYPE = ModIdentity.id("pressurized_tank");

    @Override public ResourceLocation id() { return ID; }

    @Override public boolean supports(BlockEntity entity) {
        return entity instanceof PressurizedTankBlockEntity
                && TYPE.equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(entity.getType()));
    }

    @Override public RocketBlockEntityPayload capture(BlockEntity entity) {
        if (!supports(entity) || ((PressurizedTankBlockEntity) entity).repairRequired()) {
            throw new IllegalArgumentException("Unsupported or retained cargo tank");
        }
        var data = new CompoundTag();
        data.put("tank", ((PressurizedTankBlockEntity) entity).resourceRoot());
        fluid(data);
        return new RocketBlockEntityPayload(ID, data);
    }

    /** Validate the inner TankSave root before decoding or copying it. */
    public static FluidStack fluid(CompoundTag data) {
        if (!data.getAllKeys().equals(Set.of("tank")) || !data.contains("tank", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid cargo tank frame");
        }
        var root = data.getCompound("tank");
        FluidStack fluid = TankSave.decode(root);
        if (!TankSave.encode(fluid).equals(root)) { throw new IllegalArgumentException("Noncanonical cargo tank"); }
        return fluid;
    }

    public static RocketBlockEntityPayload payload(FluidStack fluid) {
        var data = new CompoundTag(); data.put("tank", TankSave.encode(fluid));
        return new RocketBlockEntityPayload(ID, data);
    }

    @Override public boolean canRestorePayload(RocketBlockEntityPayload payload) {
        if (!ID.equals(payload.adapterId())) { return false; }
        try { fluid(payload.data()); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    @Override public boolean restore(BlockEntity entity, RocketBlockEntityPayload payload) {
        if (!supports(entity) || !canRestorePayload(payload)) { return false; }
        var outer = new CompoundTag(); outer.put(TankSave.ROOT, payload.data().getCompound("tank"));
        entity.load(outer);
        return !((PressurizedTankBlockEntity) entity).repairRequired();
    }
}
