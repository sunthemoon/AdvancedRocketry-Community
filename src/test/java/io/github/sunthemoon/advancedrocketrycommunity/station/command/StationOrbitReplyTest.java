package io.github.sunthemoon.advancedrocketrycommunity.station.command;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StationOrbitReplyTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void successUsesExistingTranslationAndPreservesOperatorDetails() {
        for (var code : StationManagementCode.values()) {
            if (!code.success()) { continue; }
            var reply = StationOrbitCommands.successMessage(code, "; station=example control=details");
            assertEquals(code.translationKey(), ((TranslatableContents) reply.getContents()).getKey());
            assertEquals("; station=example control=details", reply.getSiblings().get(0).getString());
        }
    }
}
