package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import net.minecraft.world.level.block.Block;

/** Same saved endgame_casing block, with a unique key independent of namespace load order. */
public final class AdvancedMachineCasingBlock extends Block {
    public static final String DESCRIPTION_ID = "block.advancedrocketrycommunity.advanced_machine_casing";

    public AdvancedMachineCasingBlock(Properties properties) { super(properties); }

    @Override
    public String getDescriptionId() { return DESCRIPTION_ID; }
}
