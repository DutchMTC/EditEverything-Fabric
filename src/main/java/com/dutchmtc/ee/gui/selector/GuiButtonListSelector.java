package com.dutchmtc.ee.gui.selector;

import com.dutchmtc.ee.gui.GuiValueButton;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class GuiButtonListSelector<T> extends GuiListSelector<T> {

    static class SelectorListElement<T> extends ListElement {
        private final Button button;

        public SelectorListElement(GuiButtonListSelector<T> parent, Tuple<String, T> element) {
            super(201, 21);
            buttonList.add(button = new GuiValueButton<>(0, 0, Component.literal(element.a), element.b,
                    b -> parent.select(b.getValue())));
        }

        @Override
        public boolean match(String search) {
            return button.getMessage().getString().toLowerCase().contains(search.toLowerCase());
        }

    }

    @SuppressWarnings("unchecked")
    public GuiButtonListSelector(Screen parent, Component name, List<Tuple<String, T>> elements,
                                 Function<T, Screen> setter) {
        super(parent, name, new ArrayList<>(), setter, false, new Tuple[0]);
        if (elements != null)
            setElements(elements);
    }

    public void setElements(List<Tuple<String, T>> elements) {
        elements.forEach(tuple -> addListElement(new SelectorListElement<>(this, tuple)));
    }
}
