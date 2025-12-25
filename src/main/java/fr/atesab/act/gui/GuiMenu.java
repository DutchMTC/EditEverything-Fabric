package fr.atesab.act.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.ACTMod;
import fr.atesab.act.gui.modifier.GuiItemStackModifier;
import fr.atesab.act.gui.modifier.GuiListModifier;
import fr.atesab.act.gui.selector.GuiTypeListSelector;
import fr.atesab.act.utils.ChatUtils;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiMenu extends GuiListModifier<Object> {
    private static class MenuListElement extends ListElement {
        private final GuiMenu parent;
        private ItemStack stack;

        public MenuListElement(GuiMenu parent, ItemStack stack) {
            super(24, 24);
            this.parent = parent;
            this.stack = stack;
        }

        @Override
        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            GuiUtils.drawItemStack(graphics, stack, offsetX + 1, offsetY + 1);
            super.draw(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }

        @Override
        public void drawNext(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
            if (GuiUtils.isHover(0, 0, 18, 18, mouseX, mouseY)) {
                GuiUtils.drawRect(graphics, offsetX, offsetY, offsetX + 18, offsetY + 18, 0x55cccccc);
                graphics.renderTooltip(parent.getMinecraft().font, stack, mouseX + offsetX, mouseY + offsetY);
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
        public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
            if (GuiUtils.isHover(0, 0, 18, 18, (int) mouseX, (int) mouseY)) {
                playClick();
                if (mouseButton == 0) {
                    if (ACTMod.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                        int i = parent.getElements().indexOf(this);
                        parent.addListElement(i, new MenuListElement(parent, stack.copy()));
                    } else
                        mc.setScreen(new GuiGiver(parent, stack, s -> {
                            ItemStack is = ItemUtils.getFromGiveCode(s);
                            if (is != null)
                                stack = is;
                            else
                                parent.removeListElement(this);
                        }, true));
                } else if (mouseButton == 1)
                    if (ACTMod.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT))
                        parent.removeListElement(this);
                    else
                        ItemUtils.give(stack);
            }
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private final Consumer<String> ADD_STACK = i -> {
        ItemStack is = ItemUtils.getFromGiveCode(i.replaceAll("&", String.valueOf(ChatUtils.MODIFIER)));
        if (is != null)
            addListElement(getElements().size() - 1, new MenuListElement(this, is));
        else
            ACTMod.LOGGER.warn("Menu - Can't parse : " + i);
    };

    @SuppressWarnings("unchecked")
    public GuiMenu(Screen parent) {
        super(parent, Component.translatable("gui.act.menu"), new ArrayList<>(), o -> {
        }, true, false, new Tuple[0]);
        LocalPlayer player = getMinecraft().player;
        Tuple<?, ?> btn1 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("cmd.act.edit"), new Tuple<>(() -> {
            assert player != null;
            final int slot = player.getInventory().selected;
            getMinecraft().setScreen(new GuiItemStackModifier(this, player.getMainHandItem().copy(),
                    is -> ItemUtils.give(is, 36 + slot)));
        }, () -> {
        }));
        Tuple<?, ?> btn2 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("key.act.giver"),
                new Tuple<>(() -> Minecraft.getInstance().setScreen(new GuiGiver(this)), () -> {
                }));

        Tuple<?, ?> btn3 = new Tuple<String, Tuple<Runnable, Runnable>>(I18n.get("gui.act.config"),
                new Tuple<>(() -> mc.setScreen(new GuiConfig(this)), null));
        buttons = player == null ? new Tuple[]{btn2, btn3}
                : new Tuple[]{btn1, btn2, btn3};
        Runnable ADD = () -> getMinecraft().setScreen(
                new GuiTypeListSelector(this, Component.translatable("gui.act.modifier.attr.type"), is -> {
                    GuiGiver giver = new GuiGiver(this, (ItemStack) null, ADD_STACK, false);
                    if (getMinecraft().screen instanceof GuiTypeListSelector)
                        ((GuiTypeListSelector) getMinecraft().screen).setParent(giver);
                    giver.setPreText(ItemUtils.getCustomTag(is, ACTMod.TEMPLATE_TAG_NAME, ""));
                    return null;
                }, ACTMod.getTemplates()));
        addListElement(new ButtonElementList(24, 24, 20, 20, Component.literal("+").withStyle(ChatFormatting.GREEN),
                ADD, null));
        ACTMod.getCustomItems().forEach(ADD_STACK);
    }

    @Override
    protected Object get() {
        ACTMod.getCustomItems().applyUpdate(lst -> {
            lst.clear();
            for (ListElement element : getElements()) {
                if (element instanceof MenuListElement m) {
                    lst.add(ItemUtils.getGiveCode(m.stack));
                }
            }
        });
        ACTMod.saveConfigs();
        return null;
    }

    @Override
    public void onClose() {
        get();
        super.onClose();
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        entries.add(devInfo(ACTMod.MOD_ID, ACTMod.getModVersion() + " " + ACTMod.MOD_STATE.name(), ACTMod.getModLicense()));
        super.generateDev(entries, mouseX, mouseY);
    }

}
