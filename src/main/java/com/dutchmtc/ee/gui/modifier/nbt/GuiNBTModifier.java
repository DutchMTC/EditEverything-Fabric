package com.dutchmtc.ee.gui.modifier.nbt;

import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.GuiStringModifier;
import com.dutchmtc.ee.gui.modifier.nbtelement.NBTElement;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GuiNBTModifier extends GuiListModifier<CompoundTag> {
    private final CompoundTag originalTag;

    public static final BiConsumer<Integer, GuiListModifier<?>> ADD_ELEMENT = (i, lm) -> {
        final GuiStringModifier modifier = new GuiStringModifier(lm, Component.translatable("gui.ee.modifier.name"),
                "", null);
        modifier.setSetter(key -> {
            if (!key.isEmpty())
                modifier.setParent(addElement(i == null ? lm.getElements().size() - 1 : i, lm, key));
        });
        lm.getMinecraft().gui.setScreen(modifier);
    };

    public static GuiButtonListSelector<Tag> addElement(int i, GuiListModifier<?> lm, String key) {
        return new GuiButtonListSelector<>(lm, Component.translatable("gui.ee.modifier.tag.editor"), Arrays.asList(
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.tag"), new CompoundTag()),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.string"), StringTag.valueOf("")),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.int"), IntTag.valueOf(0)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.long"), LongTag.valueOf(0L)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.float"), FloatTag.valueOf(0F)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.double"), DoubleTag.valueOf(0D)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.short"), ShortTag.valueOf((short) 0)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.intArray"), new IntArrayTag(new int[0])),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.longArray"),
                        new LongArrayTag(new long[0])),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.byte"), ByteTag.valueOf((byte) 0)),
                new Tuple<>(I18n.get("gui.ee.modifier.tag.editor.list"), new ListTag())), base -> {
            lm.addListElement(i, NBTElement.getElementByBase(lm, key, base));
            return null;
        });
    }

    public static Tag getDefaultElement(int id) {
        return switch (id) {
            case 1 -> ByteTag.valueOf((byte) 0);
            case 2 -> ShortTag.valueOf((short) 0);
            case 3 -> IntTag.valueOf(0);
            case 4 -> LongTag.valueOf(0L);
            case 5 -> FloatTag.valueOf(0F);
            case 6 -> DoubleTag.valueOf(0D);
            case 8 -> StringTag.valueOf("");
            case 9 -> new ListTag();
            case 10 -> new CompoundTag();
            case 11 -> new IntArrayTag(new int[0]);
            case 12 -> new LongArrayTag(new long[0]);
            default -> null;
        };
    }

    public GuiNBTModifier(Screen parent, Consumer<CompoundTag> setter, CompoundTag tag) {
        this(Component.literal("/"), parent, setter, tag);
    }

    @SuppressWarnings("unchecked")
    public GuiNBTModifier(Component title, Screen parent, Consumer<CompoundTag> setter, CompoundTag tag) {
        super(parent, title, new ArrayList<>(), setter, true, true, new Tuple[0]);
        this.originalTag = tag.copy();
        addListElement(new ButtonElementList(200, 21, 200, 20, Component.literal("+").withStyle(ChatFormatting.GREEN),
                () -> ADD_ELEMENT.accept(null, this), null));
        tag.forEach(this::addElement);
        setPaddingLeft(5);
        setPaddingTop(13 + Minecraft.getInstance().font.lineHeight);
        setNoAdaptativeSize(true);
    }

    @Override
    public boolean isModified() {
        return !get().equals(originalTag);
    }

    private void addElement(int i, String key, Tag base) {
        if (getElements().stream().anyMatch(le -> le instanceof NBTElement && ((NBTElement) le).getKey().equals(key))) {
            addElement(key + "_", base);
            return;
        }
        addListElement(i, NBTElement.getElementByBase(this, key, base));

    }

    private void addElement(String key, Tag base) {
        addElement(getElements().size() - 1, key, base);
    }

    @Override
    protected CompoundTag get() {
        CompoundTag tag = new CompoundTag();
        getElements().stream().filter(le -> le instanceof NBTElement).forEach(le -> {
            NBTElement elem = ((NBTElement) le);
            tag.put(elem.getKey(), elem.get());
        });
        return tag;
    }

}
