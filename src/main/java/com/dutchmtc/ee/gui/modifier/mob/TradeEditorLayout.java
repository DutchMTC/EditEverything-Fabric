package com.dutchmtc.ee.gui.modifier.mob;

/** Geometry shared with the headless layout checks. All values are Minecraft GUI pixels. */
record TradeEditorLayout(int left, int top, int right, int bottom, boolean compact,
                         int listX, int listWidth, int listTop, int listBottom,
                         int tradeX, int tradeWidth, int optionsX, int optionsWidth) {
    static TradeEditorLayout of(int screenWidth, int screenHeight) {
        int width = Math.min(560, screenWidth - 16);
        int height = Math.min(290, screenHeight - 16);
        int left = (screenWidth - width) / 2, top = (screenHeight - height) / 2;
        int right = left + width, bottom = top + height;
        boolean compact = width < 520;
        int optionsWidth = compact ? Math.min(180, (width - 32) / 2) : 190;
        int optionsX = right - 10 - optionsWidth;
        int tradeX = left + 10 + (compact ? 0 : 124);
        int tradeWidth = optionsX - 14 - tradeX;
        return new TradeEditorLayout(left, top, right, bottom, compact,
                left + 10, compact ? tradeWidth : 112, top + (compact ? 130 : 46),
                bottom - 70, tradeX, tradeWidth, optionsX, optionsWidth);
    }
    int slotsX() { return tradeX + (tradeWidth - 126) / 2; }
    int slotsY() { return top + (compact ? 42 : 58); }
    int visibleRows() { return Math.max(1, (listBottom - listTop) / 24); }
}
