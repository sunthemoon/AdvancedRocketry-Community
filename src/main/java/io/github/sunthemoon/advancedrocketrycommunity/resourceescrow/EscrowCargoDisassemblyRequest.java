package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;

/** Gated W142 caller of the live player's request and consent command, never the scalar release-test helper. */
final class EscrowCargoDisassemblyRequest {
    private static final String CONFIRM = "/arce rocket-disassembly confirm ";
    private EscrowCargoDisassemblyRequest() { }

    static void run(RocketEntity rocket, String cut) {
        var server = rocket.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(EscrowCargoCutProbe.selected(true, System.getProperty("advancedrocketrycommunity.escrowScenario"), cut)
                && EscrowCargoRecoveryCuts.disassembly(EscrowCargoRecoveryCuts.CASES.getOrDefault(cut, "")),
                "Disassembly request requires a W142 disassembly case");
        var player = new Requester((ServerLevel) rocket.level(), rocket.ownerId().orElseThrow(), cut);
        player.setPos(rocket.position());
        RocketRuntime.requestDisassembly(player, rocket);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_DISASSEMBLY_REQUEST cut={} return=void offers={}", cut, player.offers);
        EscrowTags.require(player.offers == 1, "W142 production disassembly did not issue exactly one consent offer");
        confirm(player.command, command -> server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command),
                code -> AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_DISASSEMBLY_RETURN cut={} code={}", cut, code));
    }

    /** Report the real return before failing; never substitute success for a refused or missing offer. */
    static void confirm(String command, ToIntFunction<String> dispatch, IntConsumer report) {
        EscrowTags.require(command != null && command.startsWith(CONFIRM), "Missing production disassembly consent command");
        UUID token = UUID.fromString(command.substring(CONFIRM.length()));
        EscrowTags.require(command.equals(CONFIRM + token), "Malformed production disassembly consent command");
        int code = dispatch.applyAsInt(command);
        report.accept(code);
        EscrowTags.require(code == 1, "W142 production disassembly confirmation returned " + code);
    }

    private static final class Requester extends FakePlayer {
        private final String cut;
        private String command;
        private int offers;

        Requester(ServerLevel level, UUID owner, String cut) {
            super(level, new GameProfile(owner, "W142Disassembly")); this.cut = cut;
        }

        @Override public void displayClientMessage(Component message, boolean overlay) { record("action", message); }

        @Override public void sendSystemMessage(Component message) {
            record("system", message);
            var click = message.getStyle().getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                command = click.getValue(); offers++;
            }
        }

        private void record(String kind, Component message) {
            String text = message.getContents() instanceof TranslatableContents translated ? translated.getKey() : message.getString();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_DISASSEMBLY_MESSAGE cut={} kind={} message={}", cut, kind, text);
        }
    }
}
