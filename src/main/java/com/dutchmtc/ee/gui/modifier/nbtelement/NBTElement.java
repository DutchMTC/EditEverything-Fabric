package com.dutchmtc.ee.gui.modifier.nbtelement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.modifier.GuiListModifier.AddElementButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier.ListElement;
import com.dutchmtc.ee.gui.modifier.GuiListModifier.RemoveElementButton;
import com.dutchmtc.ee.gui.modifier.GuiListModifier.RunElementButton;
import com.dutchmtc.ee.gui.modifier.GuiStringModifier;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public abstract class NBTElement extends ListElement implements Cloneable {
    private static boolean isList(Object object) {
        return object.getClass().isAnnotationPresent(GuiNBTList.class);
    }

    protected String key;

    protected GuiListModifier<?> parent;

    public NBTElement(GuiListModifier<?> parent, String key, int sizeX, int sizeY) {
        super(sizeX + 82, Math.max(isList(parent) ? 22 : 43, sizeY));
        this.key = key;
        this.parent = parent;
        buttonList.add(new RemoveElementButton(parent, sizeX + 1, 0, 20, 20, this));
        buttonList.add(new AddElementButton(parent, sizeX + 22, 0, 20, 20,
                Component.literal("+").withStyle(ChatFormatting.GREEN), this, i -> {
            GuiNBTModifier.ADD_ELEMENT.accept(i, parent);
            return null;
        }));
        buttonList.add(new AddElementButton(parent, sizeX + 43, 0, 37, 20,
                Component.translatable("gui.ee.give.copy"), this, i -> {
            parent.addListElement(i, getElementByBase(parent, key, this.get()));
            return null;
        }));
        if (!isList(parent))
            buttonList
                    .add(new RunElementButton(sizeX + 1, 21, 79, 20, Component.translatable("gui.ee.config.name"),
                            () -> mc.setScreen(new GuiStringModifier(parent,
                                    Component.translatable("gui.ee.config.name"), getKey(), nk -> this.key = nk)),
                            null));
    }

    @Override
    public abstract NBTElement clone();

    @Override
    public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        GuiUtils.drawGradientRect(graphics, offsetX - 2, offsetY - (6 + font.lineHeight), offsetX + getSizeX() - 1,
                offsetY - 2, 0x88dddddd, 0x88aaaaaa);
        GuiUtils.drawGradientRect(graphics, offsetX - 2, offsetY - 2, offsetX + getSizeX() - 1,
                offsetY + getSizeY() + 2, 0x88000000, 0x88000000);
        String s = getType();
        if (!isList(parent))
            s = key + " (" + s + ")";
        GuiUtils.drawString(graphics, font, s, offsetX + 2, offsetY - font.lineHeight - 4, 0xffffffff, font.lineHeight + 2);
        super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
    }

    /**
     * Create a {@link Tag} from this {@link NBTElement}
     *
     * @return the base
     */
    public abstract Tag get();

    public String getKey() {
        return key;
    }

    /**
     * Get the type of the NBTElement
     *
     * @return the type name
     * @since 2.1
     */
    public abstract String getType();

    @Override
    public boolean match(String search) {
        return (key + " (" + I18n.get("gui.ee.modifier.tag.editor." + getType()) + ")").toLowerCase()
                .contains(search.toLowerCase());
    }

    /**
     * Get a {@link NBTElement} with a base
     *
     * @param parent parent modifier
     * @param key    the key of the tag
     * @param base   the tag
     * @return the {@link NBTElement} from this tag
     * @since 2.1
     */
    public static NBTElement getElementByBase(GuiListModifier<?> parent, String key, Tag base) {
        return switch (base.getId()) {
            case Tag.TAG_END -> new NBTTagElement(parent, key, new CompoundTag());
            case Tag.TAG_BYTE -> new NBTByteElement(parent, key, ((ByteTag) base).value());
            case Tag.TAG_SHORT -> new NBTShortElement(parent, key, ((ShortTag) base).value());
            case Tag.TAG_INT -> new NBTIntegerElement(parent, key, ((IntTag) base).value());
            case Tag.TAG_LONG -> new NBTLongElement(parent, key, ((LongTag) base).value());
            case Tag.TAG_FLOAT -> new NBTFloatElement(parent, key, ((FloatTag) base).value());
            case Tag.TAG_DOUBLE -> new NBTDoubleElement(parent, key, ((DoubleTag) base).value());
            case Tag.TAG_STRING -> new NBTStringElement(parent, key, ((StringTag) base).value());
            case Tag.TAG_LIST -> new NBTListElement(parent, key, ((ListTag) base));
            case Tag.TAG_COMPOUND -> new NBTTagElement(parent, key, (CompoundTag) base);
            case Tag.TAG_INT_ARRAY -> new NBTIntArrayElement(parent, key, (IntArrayTag) base);
            case Tag.TAG_LONG_ARRAY -> new NBTLongArrayElement(parent, key, (LongArrayTag) base);
            default -> new NBTUnknownElement(parent, key, base);
        };
    }

    /**
     * Say to {@link NBTElement} in an {@link GuiListModifier} with this annotation
     * that key isn't needed
     *
     * @author ATE47
     * @since 2.1
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface GuiNBTList {
    }
}
