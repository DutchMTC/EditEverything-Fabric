package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.GuiEE;
import com.dutchmtc.ee.gui.GuiConfirmation;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class GuiModifier<T> extends GuiEE {

    protected Consumer<T> setter;

    public GuiModifier(Screen parent, Component name, Consumer<T> setter) {
        super(parent, name);
        this.setter = setter;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    public void set(T value) {
        setter.accept(value);
    }

    public void setSetter(Consumer<T> setter) {
        this.setter = setter;
    }

    public boolean isModified() {
        return false;
    }

    public void onCancel() {
        if (isModified()) {
            getMinecraft().setScreen(new GuiConfirmation(this, Component.translatable("gui.ee.discard_changes_question"),
                    () -> getMinecraft().setScreen(parent),
                    () -> getMinecraft().setScreen(this)));
        } else {
            getMinecraft().setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onCancel();
            return true;
        }
        return super.keyPressed(event);
    }
}
