package com.dutchmtc.ee.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.EEModClient;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.modifier.GuiListModifier;
import com.dutchmtc.ee.gui.selector.GuiTypeListSelector;
import com.dutchmtc.ee.utils.ChatUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import com.dutchmtc.ee.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiMenu extends GuiListModifier<Object> {
    private HolderLookup.Provider registryAccess() {
        return mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
    }

    private static class MenuListElement extends ListElement {
        private final GuiMenu parent;
        private ItemStack stack;

        public MenuListElement(GuiMenu parent, ItemStack stack) {
            super(24, 24);
            this.parent = parent;
            this.stack = stack;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawItemStack(graphics, stack, offsetX + 1, offsetY + 1);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void drawNext(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (GuiUtils.isHover(0, 0, 18, 18, mouseX, mouseY)) {
                GuiUtils.drawRect(graphics, offsetX, offsetY, offsetX + 18, offsetY + 18, 0x55cccccc);
                GuiUtils.setTooltipForNextFrame(graphics, parent.getMinecraft().font,
                        stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(parent.getMinecraft().level), parent.getMinecraft().player, net.minecraft.world.item.TooltipFlag.NORMAL),
                        stack.getTooltipImage(),
                        mouseX + offsetX, mouseY + offsetY);
            }
            super.drawNext(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean match(String search) {
            search = search.toLowerCase();
            return stack.getDisplayName().getString().toLowerCase().contains(search)
                    || stack.getItem().getDefaultInstance().getDisplayName().getString().toLowerCase().contains(search);
        }

        @Override
        public void mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int mouseButton = event.button();
            if (GuiUtils.isHover(0, 0, 18, 18, (int) mouseX, (int) mouseY)) {
                playClick();
                if (mouseButton == 0) {
                    if (EEModClient.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                        int i = parent.getElements().indexOf(this);
                        parent.addListElement(i, new MenuListElement(parent, stack.copy()));
                    } else
                        mc.gui.setScreen(new GuiGiver(parent, stack, s -> {
                            ItemStack is = ItemUtils.getFromGiveCode(s, parent.registryAccess());
                            if (is != null)
                                stack = is;
                            else
                                parent.removeListElement(this);
                        }, true));
                } else if (mouseButton == 1)
                    if (EEModClient.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT))
                        parent.openDeleteConfirmation(this);
                    else
                        ItemUtilsClient.give(stack);
            }
            super.mouseClicked(event, doubleClick);
        }
    }

    private boolean initialized = false;
    private void openDeleteConfirmation(MenuListElement element) {
        getMinecraft().gui.setScreen(new GuiConfirmation(this,
                Component.translatable("gui.ee.menu.delete_question"),
                Component.translatable("gui.ee.delete"),
                () -> {
                    removeListElement(element);
                    getMinecraft().gui.setScreen(this);
                },
                () -> getMinecraft().gui.setScreen(this)));
    }

    private final Consumer<String> ADD_STACK = i -> {
        ItemStack is = ItemUtils.getFromGiveCode(ChatUtils.translateColorCodes(i), registryAccess());
        if (is != null)
            addListElement(getElements().size() - 1, new MenuListElement(this, is));
        else
            EEMod.LOGGER.warn("Menu - Can't parse : " + i);
    };

    @SuppressWarnings("unchecked")
    public GuiMenu(Screen parent) {
        super(parent, Component.translatable("gui.ee.menu"), new ArrayList<>(), o -> {
        }, true, false, new Tuple[0]);
        LocalPlayer player = getMinecraft().player;
        Tuple<?, ?> btn1 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("cmd.ee.edit"), new Tuple<>(() -> {
            assert player != null;
            final int slot = player.getInventory().getSelectedSlot();
            getMinecraft().gui.setScreen(new GuiItemStackModifier(this, player.getMainHandItem().copy(),
                    is -> ItemUtilsClient.give(is, 36 + slot)));
        }, () -> {
        }));
        Tuple<?, ?> btn2 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("key.ee.giver"),
                new Tuple<>(() -> Minecraft.getInstance().gui.setScreen(new GuiGiver(this)), () -> {
                }));

        Tuple<?, ?> btn3 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("gui.ee.config"),
                new Tuple<>(() -> mc.gui.setScreen(new GuiConfig(this)), null));
        buttons = player == null ? new Tuple[]{btn2, btn3}
                : new Tuple[]{btn1, btn2, btn3};
        Runnable ADD = () -> getMinecraft().gui.setScreen(
                new GuiTypeListSelector(this, Component.translatable("gui.ee.modifier.attr.type"), is -> {
                    GuiGiver giver = new GuiGiver(this, (ItemStack) null, ADD_STACK, false);
                    if (getMinecraft().gui.screen() instanceof GuiTypeListSelector)
                        ((GuiTypeListSelector) getMinecraft().gui.screen()).setParent(giver);
                    giver.setPreText(ItemUtils.getCustomTag(is, EEMod.TEMPLATE_TAG_NAME, ""));
                    return null;
                }, EEMod.getTemplates()));
        addListElement(new ButtonElementList(24, 24, 20, 20, Component.literal("+").withStyle(ChatFormatting.GREEN),
                ADD, null));
        EEMod.getCustomItems().forEach(ADD_STACK);
        initialized = true;
    }

    @Override
    public void addListElement(int i, ListElement elem) {
        super.addListElement(i, elem);
        if (initialized) get();
    }

    @Override
    public void addListElement(ListElement elem) {
        super.addListElement(elem);
        if (initialized) get();
    }

    @Override
    public void removeListElement(ListElement elem) {
        super.removeListElement(elem);
        if (initialized) get();
    }

    @Override
    protected Object get() {
        EEMod.getCustomItems().applyUpdate(lst -> {
            lst.clear();
            for (ListElement element : getElements()) {
                if (element instanceof MenuListElement m) {
                    lst.add(ItemUtils.getGiveCode(m.stack, registryAccess()));
                }
            }
        });
        EEMod.saveConfigs();
        return null;
    }

    @Override
    public void onClose() {
        get();
        super.onClose();
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        entries.add(devInfo(EEMod.MOD_ID, EEMod.getModVersion() + " " + EEMod.MOD_STATE.name(), EEMod.getModLicense()));
        super.generateDev(entries, mouseX, mouseY);
    }

}
