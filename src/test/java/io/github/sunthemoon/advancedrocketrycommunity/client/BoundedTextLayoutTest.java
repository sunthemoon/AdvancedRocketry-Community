package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class BoundedTextLayoutTest {
    private static final ToIntFunction<String> WIDTH = text -> text.codePoints().map(c -> c == 'i' ? 2 : 6).sum();

    @Test void exactFitAndEmptyTextStayUnchanged() {
        assertEquals(new BoundedTextLayout.Line("wide", 1, false), BoundedTextLayout.fit("wide", 20, WIDTH));
        assertEquals(new BoundedTextLayout.Line("", 1, false), BoundedTextLayout.fit("", 1, WIDTH));
    }

    @Test void ellipsisIsIncludedInTheWidthAndThePrefixIsMaximal() {
        var fit = BoundedTextLayout.fit("iiiiWWWWWW", 32, WIDTH);
        assertEquals("iiiiW...", fit.text());
        assertTrue(WIDTH.applyAsInt(fit.text()) <= 32);
        assertTrue(WIDTH.applyAsInt("iiiiWW...") > 32);
        assertTrue(fit.shortened());
    }

    @Test void tinyBoxesDoNotDrawAnOverflowingEllipsis() {
        assertEquals("", BoundedTextLayout.fit("long text", 10, WIDTH).text());
        assertThrows(IllegalArgumentException.class, () -> BoundedTextLayout.fit("x", 0, WIDTH));
        assertThrows(IllegalArgumentException.class, () -> BoundedTextLayout.fit("x", -1, WIDTH));
    }

    @Test void unicodePrefixesNeverSplitASurrogatePair() {
        assertEquals("\uD83D\uDE80...", BoundedTextLayout.fit("\uD83D\uDE80\uD83D\uDE80abcdef", 24, WIDTH).text());
    }

    @Test void everyReasonCodeRemainsCompleteEvenWhenTheCodeAloneNeedsScaling() {
        for (EndgameCode code : EndgameCode.values()) { checkCode(code.name()); }
        for (SatelliteOperationCode code : SatelliteOperationCode.values()) { checkCode(code.name()); }
    }

    @Test void nonCodeBracketsAreOrdinaryText() {
        assertEquals("ordin...", BoundedTextLayout.fit("ordinary [not a code]", 44, WIDTH).text());
    }

    @Test void satelliteLabelsFitTheirIndependentMainAndDeliveryColumns() {
        String[] labels = {"Earth / DISCOVERED", "Terminal ready / operation completed [SUCCESS]",
                "Insert chassis, solar panel and components [INVALID_COMPONENTS]", "No instance or product"};
        int[] widths = {164, 164, 180, 82};
        for (int i = 0; i < labels.length; i++) {
            var line = BoundedTextLayout.fit(labels[i], widths[i], WIDTH);
            assertTrue(WIDTH.applyAsInt(line.text()) * line.scale() <= widths[i]);
            assertEquals(WIDTH.applyAsInt(labels[i]) > widths[i], line.shortened());
        }
        assertEquals("Build", BoundedTextLayout.fit("Build", 44 - 6, WIDTH).text());
    }

    private static void checkCode(String code) {
        for (int width : new int[]{40, 108, 160, 204, 1000}) {
            String full = "A lengthy translated reason [" + code + "]";
            var fit = BoundedTextLayout.fit(full, width, WIDTH);
            assertTrue(fit.text().endsWith("[" + code + "]"), fit.toString());
            assertTrue(WIDTH.applyAsInt(fit.text()) * fit.scale() <= width + 0.001F, fit.toString());
            assertTrue(fit.scale() > 0 && fit.scale() <= 1);
        }
    }
}
