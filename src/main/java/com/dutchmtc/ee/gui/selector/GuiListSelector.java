package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

public class GuiListSelector<T> extends GuiListModifier<T> {
    private final Function<T, Screen> selector;

    public GuiListSelector(Screen parent, Component name, List<ListElement> elements, Function<T, Screen> setter,
                           boolean doneButton, boolean cancelButton, Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        super(parent, name, elements, setter::apply, doneButton, cancelButton, buttons);
        this.selector = setter;
    }

    public GuiListSelector(Screen parent, Component name, List<ListElement> elements, Function<T, Screen> setter,
                           boolean doneButton, Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        super(parent, name, elements, setter::apply, doneButton, buttons);
        this.selector = setter;
    }

    public GuiListSelector(Screen parent, Component name, List<ListElement> elements, Function<T, Screen> setter,
                           Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        super(parent, name, elements, setter::apply, buttons);
        this.selector = setter;
    }

    @Override
    protected T get() {
        return null;
    }

    public void select(T t) {
        playClick();
        Screen screen = selector.apply(t);
        getMinecraft().gui.setScreen(screen == null ? parent : screen);
    }

}
