package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

public enum RocketTransactionType {
    ASSEMBLY,
    DISASSEMBLY,
    /** Schema-2 discriminator: terrain authority consists of schema-1 sealed members, not native inventories. */
    SEALED_DISASSEMBLY;

    public boolean isDisassembly() { return this == DISASSEMBLY || this == SEALED_DISASSEMBLY; }
}
