package com.dutchmtc.ee.gui.modifier;

/** Layout in Minecraft GUI pixels, including the smallest supported 320 x 240 viewport. */
record EntityEditorLayout(int left, int top, int right, int bottom, int navigationWidth, int previewWidth,
                          int contentX, int contentWidth, int viewTop, int viewBottom) {
    static EntityEditorLayout of(int width, int height) {
        int panelWidth = Math.min(500, width - 16);
        int panelHeight = Math.min(300, height - 16);
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        int right = left + panelWidth, bottom = top + panelHeight;
        int navigationWidth = (panelWidth - 26) / 4;
        int previewWidth = panelWidth >= 440 ? 106 : panelWidth >= 350 ? 88 : 72;
        int contentX = left + previewWidth + 22;
        return new EntityEditorLayout(left, top, right, bottom, navigationWidth, previewWidth,
                contentX, right - contentX - 10, top + 60, bottom - 44);
    }
    int navigationX(int index) { return left + 10 + index * (navigationWidth + 2); }
    int navigationY() { return top + 32; }
    int previewX() { return left + 10; }
    int previewY() { return viewTop + 14; }
    int previewHeight() { return viewBottom - previewY() - 36; }
}
