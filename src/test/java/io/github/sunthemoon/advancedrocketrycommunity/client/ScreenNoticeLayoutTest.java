package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScreenNoticeLayoutTest {
    @Test void scaleThreeNoticeIsInsideTheFrameAndBelowThreeStatusRows() {
        int width = 427, height = 240, panelWidth = 256, panelHeight = 222;
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        int contentBottom = top + 170 + 3 * 9;
        var bounds = ScreenNoticeLayout.forPanel(width, height, left, top, panelWidth, panelHeight, contentBottom).orElseThrow();
        assertTrue(bounds.x() > left);
        assertTrue(bounds.x() + bounds.width() < left + panelWidth - 1);
        assertTrue(bounds.y() >= contentBottom + 4);
        assertTrue(bounds.y() + bounds.height() < top + panelHeight - 1);
    }

    @Test void scaleTwoKeepsTheOriginalBelowPanelPosition() {
        var bounds = ScreenNoticeLayout.forPanel(640, 360, 192, 69, 256, 222, 266).orElseThrow();
        assertEquals(ScreenNoticeLayout.bottom(640, 360), bounds);
        assertTrue(bounds.y() > 69 + 222);
    }

    @Test void aDenseStatusOrTooSmallWindowNeverGetsCoveredByTheDuplicateNotice() {
        assertTrue(ScreenNoticeLayout.forPanel(427, 240, 85, 9, 256, 222, 9 + 215).isEmpty());
        assertTrue(ScreenNoticeLayout.forPanel(200, 180, -28, -21, 256, 222, 149).isEmpty());
    }
}
