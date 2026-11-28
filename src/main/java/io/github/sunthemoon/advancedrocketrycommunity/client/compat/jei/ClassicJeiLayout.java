package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

/** Logical-pixel bounds shared by the classic category and its headless geometry checks. */
final class ClassicJeiLayout {
    static final int WIDTH = 156;
    static final int HEIGHT = 104;
    static final int CENTRIFUGE_HEIGHT = 130;

    static int costY(boolean centrifuge) { return centrifuge ? 78 : 84; }
    static int chanceX(int index) { return 4 + index % 8 * 18; }
    static int chanceY(int index) { return 92 + index / 8 * 18; }

    private ClassicJeiLayout() { }
}
