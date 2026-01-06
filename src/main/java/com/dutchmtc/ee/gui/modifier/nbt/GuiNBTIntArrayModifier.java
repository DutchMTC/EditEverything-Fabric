package com.dutchmtc.ee.gui.modifier.nbt;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.nbtelement.NBTElement.GuiNBTList;
import com.dutchmtc.ee.gui.modifier.nbtelement.NBTIntegerElement;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GuiNBTList
public class GuiNBTIntArrayModifier extends GuiListModifier<IntArrayTag> {
    private final List<Integer> list;

    @SuppressWarnings("unchecked")
    public GuiNBTIntArrayModifier(Component title, Screen parent, Consumer<IntArrayTag> setter, IntArrayTag array) {
        super(parent, title, new ArrayList<>(), setter, new Tuple[0]);
        this.list = new ArrayList<>();
        String k = "...";
        for (int i : array.getAsIntArray()) {
            addListElement(new NBTIntegerElement(this, k, i));
            list.add(i);
        }
        addListElement(new AddElementList(this, () -> new NBTIntegerElement(this, k, 0)));
        setPaddingLeft(5);
        setPaddingTop(13 + Minecraft.getInstance().font.lineHeight);
        setNoAdaptativeSize(true);
    }

    @Override
    protected IntArrayTag get() {
        return new IntArrayTag(list.stream().mapToInt(Integer::intValue).toArray());
    }

}
