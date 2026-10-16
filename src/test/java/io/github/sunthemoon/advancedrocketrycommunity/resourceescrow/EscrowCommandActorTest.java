package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCommandActorTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void reservedConsoleIdentityCannotBeAPlayerAndPlayerUuidIsUnchanged() {
        var id = UUID.randomUUID(); var actor = new EscrowCommandActor(EscrowCommandActor.Kind.PLAYER, id, "player");
        assertEquals(id, actor.id()); assertEquals(EscrowCommandActor.Kind.PLAYER, EscrowCommandActor.kind(id));
        var console = new EscrowCommandActor(EscrowCommandActor.Kind.CONSOLE, EscrowCommandActor.CONSOLE_ID, "Server");
        assertEquals(EscrowCommandActor.Kind.CONSOLE, EscrowCommandActor.kind(console.id()));
        assertThrows(IllegalArgumentException.class, () -> new EscrowCommandActor(EscrowCommandActor.Kind.CONSOLE, id, "Server"));
        assertThrows(IllegalArgumentException.class, () -> new EscrowCommandActor(EscrowCommandActor.Kind.PLAYER, console.id(), "Server"));
    }
    @Test void nameAndPermissionDoNotProveConsoleProvenance() {
        var stack = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "Server",
                Component.literal("Server"), null, null);
        for (var source : List.of(stack, stack.withSuppressedOutput(), stack.withPermission(2))) {
            assertEquals(EscrowCommandActor.REFUSAL, assertThrows(IllegalArgumentException.class,
                    () -> EscrowCommandActor.require(source)).getMessage());
        }
    }
    @Test void typedConsoleAuditRetainsTheIntentAndReceiptWireIdentity() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var observations = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowCommandActor.CONSOLE_ID, "Server", 0, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 1, observations);
        assertEquals(intent, EscrowSettlementIntent.decode(intent.encode()));
        var receipt = EscrowSettlementTombstones.create(op, intent);
        assertEquals(EscrowCommandActor.CONSOLE_ID, receipt.getUUID("actor"));
        assertEquals("Server", receipt.getString("actor_name"));
        assertTrue(EscrowOperatorDiagnostics.preview(preview).contains("actor_type=CONSOLE"));
    }
}
