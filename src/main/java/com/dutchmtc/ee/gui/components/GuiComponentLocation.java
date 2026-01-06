package com.dutchmtc.ee.gui.components;

public class GuiComponentLocation {
    public int x, y;
    private final GuiComponent component;

    public GuiComponentLocation(GuiComponent c) {
        this.component = c;
    }

    public GuiComponent getComponent() {
        return component;
    }
}
