package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClassicDropQuarantine(UUID operationId, boolean controllerScope,
                                    List<ClassicUncertainDrop> uncertain) {
    public ClassicDropQuarantine {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(uncertain, "uncertain");
        ClassicValueChecks.require(!uncertain.isEmpty() && uncertain.size() <= 256, "Quarantine count");
        var slots = new HashSet<String>();
        var entities = new HashSet<UUID>();
        String previousChannel = null;
        int previousSlot = -1;
        for (ClassicUncertainDrop entry : uncertain) {
            String channel = entry.bankKey().channel();
            ClassicValueChecks.require(slots.add(channel + ".s" + entry.slot()) && entities.add(entry.entityId()),
                    "Duplicate uncertain slot or entity");
            ClassicValueChecks.require(previousChannel == null || previousChannel.compareTo(channel) < 0
                    || (previousChannel.equals(channel) && previousSlot < entry.slot()), "Quarantine order");
            previousChannel = channel;
            previousSlot = entry.slot();
        }
        if (!controllerScope) {
            var bankKey = uncertain.get(0).bankKey();
            ClassicValueChecks.require(uncertain.stream().allMatch(e -> e.bankKey().equals(bankKey)),
                    "Hatch quarantine spans banks");
        }
        uncertain = List.copyOf(uncertain);
    }
}
