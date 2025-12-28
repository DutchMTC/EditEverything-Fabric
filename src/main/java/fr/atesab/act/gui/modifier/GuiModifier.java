package fr.atesab.act.gui.modifier;

import fr.atesab.act.gui.GuiACT;
import fr.atesab.act.gui.GuiConfirmation;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class GuiModifier<T> extends GuiACT {

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

    protected void reRenderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        for (net.minecraft.client.gui.components.events.GuiEventListener listener : children()) {
            if (listener instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTicks);
            }
        }
    }

    public boolean isModified() {
        return false;
    }

    public void onCancel() {
        if (isModified()) {
            getMinecraft().setScreen(new GuiConfirmation(this, Component.translatable("gui.act.discard_changes_question"),
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
