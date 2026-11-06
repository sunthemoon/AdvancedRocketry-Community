package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNotice;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarpBodyLabelTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void controllerBodyNamesUseTheExistingBodyTranslationKeys() {
        for (String name : new String[]{"earth", "moon", "tau_ceti_f", "cygnus_x1"}) {
            ResourceLocation id = new ResourceLocation("advancedrocketrycommunity", name);
            var contents = (TranslatableContents) WarpControllerMenu.bodyName(id).getContents();
            assertEquals("body.advancedrocketrycommunity." + name, contents.getKey());
            assertEquals(id.toString(), contents.getFallback());
        }
    }

    @Test void countdownBodyIsTranslatedWithoutChangingTheNoticeEnvelope() {
        var id = new ResourceLocation("advancedrocketrycommunity", "tau_ceti_f");
        var notice = StationNotice.countdown("ArceTester1 Station", id, 10);
        var content = (TranslatableContents) StationNoticeService.component(notice).getContents();
        var body = (TranslatableContents) ((Component) content.getArgs()[1]).getContents();
        assertEquals("body.advancedrocketrycommunity.tau_ceti_f", body.getKey());
        assertEquals(id.toString(), notice.arguments().get(1));
        assertEquals("ArceTester1 Station", ((Component) content.getArgs()[0]).getString());
        assertEquals("10", ((Component) content.getArgs()[2]).getString());
    }
}
