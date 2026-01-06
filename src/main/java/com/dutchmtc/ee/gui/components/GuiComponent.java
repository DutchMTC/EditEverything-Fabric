package com.dutchmtc.ee.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;

public class GuiComponent {
    private int x, y;
    private int width, height;
    private GuiComponentContainer container;

    private void recomputeContainer() {
        if (container != null)
            container.recomputeSizes();
    }

    public void setWidth(int width) {
        this.width = width;
        recomputeContainer();
    }

    public void setHeight(int height) {
        this.height = height;
        recomputeContainer();
    }

    public void setX(int x) {
        this.x = x;
        recomputeContainer();
    }

    public void setY(int y) {
        this.y = y;
        recomputeContainer();
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
        recomputeContainer();
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        recomputeContainer();
    }

    public void setDimension(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        recomputeContainer();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    void setContainer(GuiComponentContainer container) {
        if (this.container != null)
            throw new IllegalStateException("container already registered");
        this.container = container;
    }

    public GuiComponentContainer getContainer() {
        return container;
    }

    public void init() {
        //
    }

    public void draw(PoseStack stack, int offsetX, int offsetY, int mouseX, int mouseY, float partialTick) {
        //
    }

    public boolean mouseClick(int mouseX, int mouseY, int button) {
        return false;
    }
}
