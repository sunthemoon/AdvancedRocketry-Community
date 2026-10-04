package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import java.util.Objects;
import java.util.UUID;

public record ClassicUncertainDrop(ClassicBankKey bankKey, int slot, UUID entityId) {
    public ClassicUncertainDrop {
        Objects.requireNonNull(bankKey, "bankKey");
        Objects.requireNonNull(entityId, "entityId");
        ClassicValueChecks.require(bankKey.kind().isItem() && slot >= 0 && slot <= 3, "Uncertain Item slot");
    }
}
