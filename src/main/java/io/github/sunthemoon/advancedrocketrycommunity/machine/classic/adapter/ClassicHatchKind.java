package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKind;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Five frozen physical identities; power deliberately has no Item/Fluid bank. */
public enum ClassicHatchKind {
    ITEM_INPUT("item_input_hatch", "item_input", ClassicBankKind.ITEM_INPUT),
    ITEM_OUTPUT("item_output_hatch", "item_output", ClassicBankKind.ITEM_OUTPUT),
    FLUID_INPUT("fluid_input_hatch", "fluid_input", ClassicBankKind.FLUID_INPUT),
    FLUID_OUTPUT("fluid_output_hatch", "fluid_output", ClassicBankKind.FLUID_OUTPUT),
    POWER_INPUT("power_input_plug", "energy_input", null);

    private final ResourceLocation blockId;
    private final String role;
    private final ClassicBankKind bankKind;

    ClassicHatchKind(String path, String role, ClassicBankKind bankKind) {
        this.blockId = new ResourceLocation("advancedrocketrycommunity", path);
        this.role = role;
        this.bankKind = bankKind;
    }

    public ResourceLocation blockId() { return blockId; }
    public String patternRole() { return role; }
    public Optional<ClassicBankKind> bankKind() { return Optional.ofNullable(bankKind); }
    public static Optional<ClassicHatchKind> fromBlockId(ResourceLocation id) {
        for (ClassicHatchKind kind : values()) {
            if (kind.blockId.equals(id)) { return Optional.of(kind); }
        }
        return Optional.empty();
    }
}
