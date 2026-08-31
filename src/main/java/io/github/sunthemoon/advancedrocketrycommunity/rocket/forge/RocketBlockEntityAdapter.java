package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface RocketBlockEntityAdapter {
    ResourceLocation id();

    boolean supports(BlockEntity blockEntity);

    RocketBlockEntityPayload capture(BlockEntity blockEntity);

    boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload);
}
