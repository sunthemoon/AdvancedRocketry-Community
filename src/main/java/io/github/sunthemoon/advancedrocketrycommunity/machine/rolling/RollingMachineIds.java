package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.resources.ResourceLocation;

/** Stable runtime identities for the v1.2 Rolling Machine vertical slice. */
public final class RollingMachineIds {
    public static final ResourceLocation MACHINE = ModIdentity.id("rolling_machine");
    public static final ResourceLocation RECIPE = ModIdentity.id("rolling");
    public static final ResourceLocation PORT_BLOCK_ENTITY = ModIdentity.id("rolling_machine_port");

    private RollingMachineIds() {
    }
}
