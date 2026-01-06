package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class GuiDataComponentModifier extends GuiListModifier<ItemStack> {
    private static final int LIST_WIDTH = 420;
    private static final String WRAP_KEY = "value";

    private static class ComponentListElement extends ListElement {
        private final GuiDataComponentModifier parent;
        private final DataComponentType<?> type;
        private final String id;

        public ComponentListElement(GuiDataComponentModifier parent, DataComponentType<?> type, String id) {
            super(LIST_WIDTH, 34);
            this.parent = parent;
            this.type = type;
            this.id = id;

            int buttonY = 7;
            buttonList.add(new EEButton(LIST_WIDTH - 166, buttonY, 60, 20,
                    Component.translatable("gui.ee.modifier.meta.dataComponents.clone"),
                    b -> parent.openCloneSelector(type)));
            buttonList.add(new EEButton(LIST_WIDTH - 104, buttonY, 60, 20,
                    Component.translatable("gui.ee.modifier.meta.dataComponents.edit"),
                    b -> parent.openEditor(type)));
            buttonList.add(new EEButton(LIST_WIDTH - 42, buttonY, 40, 20,
                    Component.literal("-").withStyle(ChatFormatting.RED),
                    b -> parent.removeComponent(type)));
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            String title = id;
            int maxWidth = getSizeX() - 4 - 174;
            if (maxWidth > 0 && font.width(title) > maxWidth) {
                int dots = font.width("...");
                title = font.plainSubstrByWidth(title, Math.max(0, maxWidth - dots)) + "...";
            }

            GuiUtils.drawString(graphics, font, title, offsetX + 4, offsetY, Color.WHITE.getRGB(), 16);

            String summary = parent.describeComponentValue(type);
            if (summary != null && !summary.isBlank()) {
                String shown = summary;
                if (maxWidth > 0 && font.width(shown) > maxWidth) {
                    int dots = font.width("...");
                    shown = font.plainSubstrByWidth(shown, Math.max(0, maxWidth - dots)) + "...";
                }
                GuiUtils.drawString(graphics, font, shown, offsetX + 4, offsetY + 16, Color.GRAY.getRGB(), 16);
            }

            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean match(String search) {
            return id.toLowerCase().contains(search.toLowerCase());
        }
    }

    private static class AddComponentListElement extends ListElement {
        public AddComponentListElement(GuiDataComponentModifier parent) {
            super(LIST_WIDTH, 24);
            buttonList.add(new EEButton(0, 2, LIST_WIDTH, 20,
                    Component.translatable("gui.ee.modifier.meta.dataComponents.add"),
                    b -> parent.openAddSelector()));
        }
    }

    private final ItemStack stack;
    private final ItemStack originalStack;
    private final List<ListElement> listElements;

    private static final class RedirectScreen extends Screen {
        private final Supplier<Screen> next;

        private RedirectScreen(Supplier<Screen> next) {
            super(Component.literal(""));
            this.next = next;
        }

        @Override
        public void init() {
            if (minecraft != null) {
                minecraft.setScreen(next.get());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public GuiDataComponentModifier(Screen parent, ItemStack stack) {
        this(parent, stack, new ArrayList<>());
    }

    private GuiDataComponentModifier(Screen parent, ItemStack stack, List<ListElement> elements) {
        super(parent, Component.translatable("gui.ee.modifier.meta.dataComponents"), elements, s -> {
        }, new Tuple[0]);
        this.stack = stack;
        this.originalStack = stack.copy();
        this.listElements = elements;

        rebuildElements();
        setNoAdaptativeSize(true);
        setPaddingLeft(5);
        setPaddingTop(13 + getMinecraft().font.lineHeight);
    }

    @Override
    public boolean isModified() {
        return !ItemStack.matches(stack, originalStack);
    }

    @Override
    protected ItemStack get() {
        return stack;
    }

    private void rebuildElements() {
        listElements.clear();

        List<DataComponentType<?>> present = new ArrayList<>();
        for (TypedDataComponent<?> typed : stack.getComponents()) {
            present.add((DataComponentType<?>) typed.type());
        }

        present.sort(Comparator.comparing(this::componentId, String.CASE_INSENSITIVE_ORDER));
        for (DataComponentType<?> type : present) {
            listElements.add(new ComponentListElement(this, type, componentId(type)));
        }
        listElements.add(new AddComponentListElement(this));
    }

    private String componentId(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id != null ? id.toString() : String.valueOf(type);
    }

    private net.minecraft.core.HolderLookup.Provider registryAccess() {
        if (getMinecraft().level != null) {
            return getMinecraft().level.registryAccess();
        }
        return VanillaRegistries.createLookup();
    }

    private String describeComponentValue(DataComponentType<?> type) {
        Object value = ItemUtils.getComponent(stack, (DataComponentType<Object>) type);
        if (value == null) {
            return "";
        }
        try {
            var encoded = ItemUtils.encodeComponentToNbt(registryAccess(), (DataComponentType<Object>) type, value);
            Optional<Tag> tag = encoded.result();
            if (tag.isPresent()) {
                return tag.get().toString();
            }
            return "<" + I18n.get("gui.ee.modifier.meta.dataComponents.encodeError") + ">";
        } catch (Throwable t) {
            return "<" + I18n.get("gui.ee.modifier.meta.dataComponents.encodeError") + ">";
        }
    }

    private void removeComponent(DataComponentType<?> type) {
        ItemUtils.setComponent(stack, (DataComponentType<Object>) type, null);
        getMinecraft().setScreen(new GuiDataComponentModifier(parent, stack));
    }

    private void openAddSelector() {
        List<Tuple<String, DataComponentType<?>>> list = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (type == null) continue;
            list.add(new Tuple<>(id.toString(), type));
        }
        list.sort(Comparator.comparing(t -> t.a, String.CASE_INSENSITIVE_ORDER));

        getMinecraft().setScreen(new GuiButtonListSelector<>(this,
                Component.translatable("gui.ee.modifier.meta.dataComponents.add"),
                list,
                type -> buildEditorScreen(type)));
    }

    private void openEditor(DataComponentType<?> type) {
        getMinecraft().setScreen(buildEditorScreen(type));
    }

    private void openCloneSelector(DataComponentType<?> type) {
        getMinecraft().setScreen(new com.dutchmtc.ee.gui.selector.GuiTypeListSelector(this,
                Component.translatable("gui.ee.modifier.meta.dataComponents.cloneFrom"),
                example -> {
                    Object exampleValue = ItemUtils.getComponent(example, (DataComponentType<Object>) type);
                    if (exampleValue == null) {
                        ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.cloneMissing"));
                        return this;
                    }
                    Tag encoded;
                    try {
                        encoded = ItemUtils.encodeComponentToNbt(registryAccess(), (DataComponentType<Object>) type, exampleValue)
                                .getOrThrow(IllegalStateException::new);
                    } catch (Throwable t) {
                        ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.encodeError") + ": " + t.getMessage());
                        return this;
                    }
                    return buildEditorScreen(type, encoded, true);
                }, BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .filter(s -> ItemUtils.getComponent(s, (DataComponentType<Object>) type) != null)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Screen buildEditorScreen(DataComponentType<?> type) {
        return buildEditorScreen(type, null, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Screen buildEditorScreen(DataComponentType<?> type, Tag encodedOverride, boolean forceGeneric) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        String idStr = id != null ? id.toString() : String.valueOf(type);

        Screen back = new RedirectScreen(() -> new GuiDataComponentModifier(GuiDataComponentModifier.this.parent, stack));

        // Optional: route common components to dedicated UIs for better UX.
        if (!forceGeneric && type == DataComponents.POTION_CONTENTS) {
            return new GuiPotionModifier(back, info -> ItemUtils.setPotionInformation(stack, info),
                    ItemUtils.getPotionInformation(stack));
        }
        if (!forceGeneric && type == DataComponents.FIREWORKS) {
            return new GuiFireworksModifer(back, tag -> ItemUtils.setFireworksFromTag(stack, tag),
                    ItemUtils.getFireworksTag(stack));
        }
        if (!forceGeneric && type == DataComponents.ENCHANTMENTS) {
            return new GuiEnchModifier(back, ItemUtils.getEnchantments(stack),
                    list -> ItemUtils.setEnchantments(list, stack, false,
                            getMinecraft().level != null ? getMinecraft().level.registryAccess() : null));
        }
        if (!forceGeneric && type == DataComponents.STORED_ENCHANTMENTS) {
            return new GuiEnchModifier(back, ItemUtils.getEnchantments(stack, true),
                    list -> ItemUtils.setEnchantments(list, stack, true,
                            getMinecraft().level != null ? getMinecraft().level.registryAccess() : null));
        }

        Object value = ItemUtils.getComponent(stack, (DataComponentType<Object>) type);

        Tag encodedTag = null;
        if (encodedOverride != null) {
            encodedTag = encodedOverride;
        } else if (value != null) {
            try {
                var encoded = ItemUtils.encodeComponentToNbt(registryAccess(), (DataComponentType<Object>) type, value);
                encodedTag = encoded.result().orElse(null);
                if (encodedTag == null && encoded.error().isPresent()) {
                    ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.encodeError") + ": "
                            + encoded.error().get().message());
                }
            } catch (Throwable t) {
                ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.encodeError") + ": " + t.getMessage());
            }
        }

        CompoundTag wrapper = new CompoundTag();
        wrapper.put(WRAP_KEY, Objects.requireNonNullElseGet(encodedTag, CompoundTag::new));

        return new GuiNBTModifier(Component.literal(idStr), back, editedWrapper -> {
            Tag valueTag = extractWrappedValue(editedWrapper);
            if (valueTag == null) {
                ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.missingValue"));
                return;
            }

            var decoded = ItemUtils.decodeComponentFromNbt(registryAccess(), (DataComponentType<Object>) type, valueTag);
            Optional<?> out = decoded.result();
            if (out.isEmpty()) {
                String msg = decoded.error().map(e -> e.message()).orElse(I18n.get("gui.ee.modifier.meta.dataComponents.decodeError"));
                ChatUtils.error(I18n.get("gui.ee.modifier.meta.dataComponents.decodeError") + ": " + msg);
                return;
            }

            ItemUtils.setComponent(stack, (DataComponentType<Object>) type, (Object) out.get());
        }, wrapper);
    }

    private static Tag extractWrappedValue(CompoundTag wrapper) {
        if (wrapper.contains(WRAP_KEY)) {
            return wrapper.get(WRAP_KEY);
        }
        var keys = wrapper.keySet();
        if (keys.size() == 1) {
            return wrapper.get(keys.iterator().next());
        }
        return null;
    }
}
