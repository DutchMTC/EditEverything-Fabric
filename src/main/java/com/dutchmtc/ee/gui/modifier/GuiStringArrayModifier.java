package com.dutchmtc.ee.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.gui.GuiValueButton;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class GuiStringArrayModifier extends GuiModifier<String[]> {
    private final List<String> values;
    private final List<String> originalValues;
    private EditBox[] tfs;
    private Button next, last;
    private GuiValueButton<Integer>[] btsDel, btsAdd;
    private int elms;
    private int page = 0;

    private void updateFieldVisibility() {
        if (tfs == null) return;
        int start = page * elms;
        int end = (page + 1) * elms;
        for (int i = 0; i < tfs.length; i++) {
            boolean onPage = i >= start && i < end;
            tfs[i].visible = onPage;
            tfs[i].active = onPage;
            if (!onPage) {
                tfs[i].setFocused(false);
            }
        }
    }

    public GuiStringArrayModifier(Screen parent, Component name, String[] values, Consumer<String[]> setter) {
        super(parent, name, setter);
        this.values = new ArrayList<>();
        this.originalValues = new ArrayList<>();
        for (String v : values) {
            String s = ChatUtils.untranslateColorCodes(v);
            this.values.add(s);
            this.originalValues.add(s);
        }
    }

    @Override
    public boolean isModified() {
        return !values.equals(originalValues);
    }

    @SuppressWarnings("unchecked")
    private void defineMenu() {
        clearWidgets();
        addRenderableWidget(new EEButton(width / 2 - 100, height - 21, 100, 20, Component.translatable("gui.ee.cancel"),
                b -> onCancel()));
        addRenderableWidget(
                new EEButton(width / 2 + 1, height - 21, 99, 20, Component.translatable("gui.done"), b -> {
                    String[] result = new String[values.size()];
                    for (int i = 0; i < result.length; i++)
                        result[i] = ChatUtils.translateColorCodes(values.get(i));
                    set(result);
                    mc.setScreen(parent);
                }));
        addRenderableWidget(last = new EEButton(width / 2 - 121, height - 21, 20, 20, Component.literal("<-"), b -> {
            page--;
            b.active = page != 0;
            next.active = page + 1 <= values.size() / elms;
            updateFieldVisibility();
        }) {

            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.translatable("gui.narrate.button", I18n.get("gui.ee.leftArrow"));
            }

        });
        addRenderableWidget(next = new EEButton(width / 2 + 101, height - 21, 20, 20, Component.literal("->"), b -> {
            page++;
            last.active = page != 0;
            b.active = page + 1 <= values.size() / elms;
            updateFieldVisibility();
        }) {

            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.translatable("gui.narrate.button", I18n.get("gui.ee.rightArrow"));
            }
        });
        last.active = page != 0;
        next.active = page + 1 <= values.size() / elms;
        tfs = new EditBox[values.size()];
        btsDel = new GuiValueButton[values.size()];
        btsAdd = new GuiValueButton[values.size() + 1];

        int i;
        for (i = 0; i < values.size(); i++) {
            tfs[i] = new EditBox(font, width / 2 - 178, 21 + 21 * i % (elms * 21) + 2, 340, 16, Component.literal(""));
            tfs[i].setMaxLength(Integer.MAX_VALUE);
            tfs[i].setValue(values.get(i));
            btsDel[i] =

                    addRenderableWidget(new GuiValueButton<Integer>(width / 2 + 165, 21 + 21 * i % (elms * 21), 20, 20,
                            Component.literal("-"), i, b -> {
                        values.remove(b.getValue().intValue());
                        defineMenu();
                    }) {

                        @Override
                        protected MutableComponent createNarrationMessage() {
                            return Component.translatable("gui.narrate.button", I18n.get("gui.ee.delete"));
                        }
                    });
            btsDel[i].setMessage(Component.literal("-").withStyle(ChatFormatting.RED));
            btsAdd[i] = addRenderableWidget(new GuiValueButton<Integer>(width / 2 + 187, 21 + 21 * i % (elms * 21), 20,
                    20, Component.literal("+"), i, b -> {
                values.add(Objects.requireNonNull(b.getValue()), "");
                defineMenu();
            }) {

                @Override
                protected MutableComponent createNarrationMessage() {
                    return Component.translatable("gui.narrate.button", I18n.get("gui.ee.new"));
                }

            });
            btsAdd[i].setMessage(Component.literal("+").withStyle(ChatFormatting.GREEN));
            addRenderableWidget(tfs[i]);
        }
        updateFieldVisibility();
        btsAdd[i] = addRenderableWidget(new GuiValueButton<Integer>(width / 2 - 100, 21 + 21 * i % (elms * 21), 200, 20,
                Component.literal("+"), i, b -> {
            values.add(Objects.requireNonNull(b.getValue()), "");
            defineMenu();
        }) {

            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.translatable("gui.narrate.button", I18n.get("gui.ee.new"));
            }

        });
        btsAdd[i].setMessage(Component.literal("+").withStyle(ChatFormatting.GREEN));

    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        for (int i = page * elms; i < (page + 1) * elms && i < tfs.length; i++) {
            EditBox tf = tfs[i];
            GuiUtils.drawRightString(graphics, font, i + " : ", tf.getX(), tf.getY(), Objects.requireNonNull(ChatFormatting.WHITE.getColor()), tf.getHeight());
            // tf.render(graphics, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void init() {
        elms = (height - 42) / 21;
        defineMenu();
        super.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int mouseButton = event.button();

        int start = page * elms;
        int end = Math.min(values.size(), (page + 1) * elms);

        // Right-click: clear the hovered line and keep focus on it.
        if (mouseButton == 1) {
            for (int i = start; i < end; i++) {
                if (GuiUtils.isHover(tfs[i], mouseX, mouseY)) {
                    tfs[i].setValue("");
                    tfs[i].setFocused(true);
                    setFocused(tfs[i]);
                    return true;
                }
            }
        }

        boolean clickedField = false;
        for (int i = start; i < end; i++) {
            if (GuiUtils.isHover(tfs[i], mouseX, mouseY)) {
                clickedField = true;
                break;
            }
        }

        // If the click isn't on a visible field, clear the screen's focused element so fields can be re-focused.
        if (!clickedField) {
            for (int i = start; i < end; i++) {
                tfs[i].setFocused(false);
            }
            setFocused(null);
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        for (int i = page * elms; i < (page + 1) * elms && i < values.size(); i++) {
            values.set(i, tfs[i].getValue());
        }
        updateFieldVisibility();

        for (int i = 0; i < btsAdd.length; i++)
            if (i < (page + 1) * elms && i >= page * elms) {
                if (btsAdd[i] != null)
                    btsAdd[i].visible = true;
                if (i < btsDel.length && btsDel[i] != null)
                    btsDel[i].visible = true;
            } else {
                if (btsAdd[i] != null)
                    btsAdd[i].visible = false;
                if (i < btsDel.length && btsDel[i] != null)
                    btsDel[i].visible = false;
            }
        super.tick();
    }
}
