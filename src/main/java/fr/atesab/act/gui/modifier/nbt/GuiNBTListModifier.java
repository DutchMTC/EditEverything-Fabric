package fr.atesab.act.gui.modifier.nbt;

import fr.atesab.act.gui.modifier.GuiListModifier;
import fr.atesab.act.gui.modifier.nbtelement.NBTElement;
import fr.atesab.act.gui.modifier.nbtelement.NBTElement.GuiNBTList;
import fr.atesab.act.utils.Tuple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.function.Consumer;

@GuiNBTList
public class GuiNBTListModifier extends GuiListModifier<ListTag> {
    private final ListTag list;

    @SuppressWarnings("unchecked")
    public GuiNBTListModifier(Component title, Screen parent, Consumer<ListTag> setter, ListTag list) {
        super(parent, title, new ArrayList<>(), setter, new Tuple[0]);
        this.list = list.copy();
        String k = "...";
        for (Tag base : list) {
            addListElement(NBTElement.getElementByBase(this, k, base));
        }
        addListElement(new AddElementList(this, () -> {
            byte elementType = this.list.isEmpty() ? 0 : this.list.get(0).getId();
            if (elementType != 0) {
                Tag base = GuiNBTModifier.getDefaultElement(elementType);
                if (base != null)
                    addListElement(getElements().size() - 1, NBTElement.getElementByBase(this, k, base));
            } else
                getMinecraft().setScreen(GuiNBTModifier.addElement(getElements().size() - 1, this, k));
            return null;
        }));
        setPaddingLeft(5);
        setPaddingTop(13 + Minecraft.getInstance().font.lineHeight);
        setNoAdaptativeSize(true);
    }

    @Override
    public void addListElement(int i, ListElement elem) {
        if (elem instanceof NBTElement)
            list.add(((NBTElement) elem).get());
        super.addListElement(i, elem);
    }

    @Override
    protected ListTag get() {
        ListTag list = new ListTag();
        getElements().stream().filter(le -> le instanceof NBTElement).forEach(le -> list.add(((NBTElement) le).get()));
        return list;
    }

}
