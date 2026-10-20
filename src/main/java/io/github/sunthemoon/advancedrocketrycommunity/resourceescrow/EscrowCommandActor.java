package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Typed command provenance; the reserved console UUID preserves the existing audit wire identity. */
record EscrowCommandActor(Kind kind, UUID id, String name) {
    enum Kind { PLAYER, CONSOLE }
    static final UUID CONSOLE_ID = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(StandardCharsets.US_ASCII));
    static final String REFUSAL = "Settlement requires an unsuppressed player source or the exact server console; functions, command blocks and RCON cannot settle.";
    EscrowCommandActor {
        Objects.requireNonNull(kind); Objects.requireNonNull(id); Objects.requireNonNull(name);
        EscrowTags.require((kind == Kind.CONSOLE) == CONSOLE_ID.equals(id), "Reserved console actor identity");
    }
    static Kind kind(UUID actor) { return CONSOLE_ID.equals(actor) ? Kind.CONSOLE : Kind.PLAYER; }
    static EscrowCommandActor require(CommandSourceStack stack) {
        // In the pinned API, suppressed stacks return themselves. alwaysAccepts also does so;
        // reject that ambiguous case instead of inferring provenance from output preferences.
        EscrowTags.require(stack.hasPermission(2) && stack.withSuppressedOutput() != stack, REFUSAL);
        if (stack.getEntity() instanceof ServerPlayer player && stack.source == player) {
            String name = player.getGameProfile().getName();
            EscrowSettlementIntent.requireNewActorName(name);
            return new EscrowCommandActor(Kind.PLAYER, player.getUUID(), name);
        }
        // DedicatedServer's console input uses server.createCommandSourceStack(). Tick/load
        // functions share its source/name/permissions but getGameLoopSender suppresses output.
        EscrowTags.require(stack.getEntity() == null && stack.getServer() != null && stack.source == stack.getServer(), REFUSAL);
        EscrowSettlementIntent.requireNewActorName("Server");
        return new EscrowCommandActor(Kind.CONSOLE, CONSOLE_ID, "Server");
    }
}
