package com.dutchmtc.ee;

import com.mojang.blaze3d.platform.InputConstants;
import com.dutchmtc.ee.command.ModdedCommand;
import com.dutchmtc.ee.gui.GuiEE;
import com.dutchmtc.ee.gui.GuiGiver;
import com.dutchmtc.ee.gui.GuiMenu;
import com.dutchmtc.ee.gui.modifier.GuiItemStackModifier;
import com.dutchmtc.ee.gui.modifier.GuiModifier;
import com.dutchmtc.ee.gui.modifier.nbt.GuiNBTModifier;
import com.dutchmtc.ee.gui.selector.GuiButtonListSelector;
import com.dutchmtc.ee.network.EEClientNetworking;
import com.dutchmtc.ee.utils.CommandUtils;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import com.dutchmtc.ee.utils.ReflectionUtils;
import com.dutchmtc.ee.utils.Tuple;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Environment(EnvType.CLIENT)
public class EEModClient implements ClientModInitializer {

    private static KeyMapping giver, menu, edit;
    private static final KeyMapping.Category ACT_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EEMod.MOD_ID, "key.act"));

    @Override
    public void onInitializeClient() {
        EEClientNetworking.initClient();

        // Register KeyMappings
        giver = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ee.giver", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, ACT_CATEGORY));
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ee.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, ACT_CATEGORY));
        edit = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ee.edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, ACT_CATEGORY));

        EEMod.registerInternalModule(GuiUtils.class);

        // Client Tick
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Screen Events
        ScreenEvents.AFTER_INIT.register(this::onInitGui);

        // Tooltips
        ItemTooltipCallback.EVENT.register(this::onRenderTooltip);

        // Register Client-side Modifiers
        registerClientModifiers();

        // Build sub items
        // EEMod.ADVANCED_CREATIVE_TAB.buildSubItems(); // Can't call this safely here if it uses server logic? 
        // Actually EditEverything is common.
    }

    private void registerClientModifiers() {
        // giver
        EEMod.registerStringModifier("gui.ee.modifier.string.giver", "",
                sm -> sm.setNextScreen(new GuiGiver(sm.getNextScreen(), sm.getString(), sm::setString, false)));

        // NBT editor
        EEMod.registerStringModifier("gui.ee.modifier.string.nbt", "", sm -> {
            try {
                sm.setNextScreen(new GuiNBTModifier(sm.getNextScreen(), nbt -> sm.setString(nbt.toString()),
                        TagParser.parseCompoundFully(sm.getString())));
            } catch (Exception ignore) {
            }
        });

        // players names
        EEMod.registerStringModifier("gui.ee.modifier.string.players", "", sm -> {
            List<String> plr;
            try {
                plr = CommandUtils.getPlayerList();
            } catch (Exception e) {
                plr = new ArrayList<>();
                plr.add(Minecraft.getInstance().getUser().getName());
            }
            List<Tuple<String, String>> btn = new ArrayList<>();
            plr.forEach(pn -> btn.add(new Tuple<>(pn, pn)));
            sm.setNextScreen(new GuiButtonListSelector<>(sm.getNextScreen(),
                    Component.translatable("gui.ee.modifier.string.players"), btn, s -> {
                sm.setString(s);
                return null;
            }));
        });

        // Base64
        EEMod.registerStringModifier("gui.ee.modifier.string.b64.encode", "b64", sm -> {
            try {
                sm.setString(new String(Base64.getEncoder().encode(sm.getString().getBytes())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        EEMod.registerStringModifier("gui.ee.modifier.string.b64.decode", "b64", sm -> {
            try {
                sm.setString(new String(Base64.getDecoder().decode(sm.getString().getBytes())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void onClientTick(Minecraft mc) {
        if (EEMod.isInstantPlaceEnabled()) {
            ReflectionUtils.setRightClickDelay(mc, 0);
        }
        if (EEMod.isInstantMineEnabled() && mc.gameMode != null) {
            ReflectionUtils.setDestroyDelay(mc.gameMode, 0);
        }

        if (mc.screen == null) {
            if (giver.consumeClick()) {
                GuiUtils.displayScreen(new GuiGiver(null));
            } else if (menu.consumeClick()) {
                GuiUtils.displayScreen(new GuiMenu(null));
            } else if (edit.consumeClick()) {
                openGiver();
            }
        }
    }

    private void onInitGui(Minecraft mc, Screen screen, int width, int height) {
        injectSuggestions();

        ScreenEvents.afterRender(screen).register((s, guiGraphics, mouseX, mouseY, tickDelta) -> {
            if (s instanceof GuiEE && EEMod.MOD_STATE.isShow()) {
                guiGraphics.drawString(mc.font, "Warning! Currently in " + EEMod.MOD_STATE.getColor() + EEMod.MOD_STATE.name(),
                        5, 5, 0xffffffff);
            }
        });
    }

    private void injectSuggestions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var current = mc.player.connection.getCommands();
            if (current != EEMod.getSharedSuggestionProvider()) {
                EEMod.setSharedSuggestionProvider(current);
            }
        }
    }

    private void onRenderTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag type, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();

        if (!(!(mc.screen instanceof GuiModifier) && KeyBindingHelper.getBoundKeyOf(giver).getValue() != 0
                && isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) && mc.screen instanceof GuiMenu) {
            lines.add(ModdedCommand
                    .createPrefix(I18n.get("gui.ee.leftClick"), ChatFormatting.YELLOW, ChatFormatting.GOLD)
                    .append(ModdedCommand.createText(
                            I18n.get(isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) ? "gui.ee.give.copy" : "gui.ee.give.editor"),
                            ChatFormatting.YELLOW)));
            if (isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                lines.add(ModdedCommand
                        .createPrefix(I18n.get("gui.ee.rightClick"), ChatFormatting.YELLOW,
                                ChatFormatting.GOLD)
                        .append(ModdedCommand.createText(I18n.get("gui.ee.delete"), ChatFormatting.YELLOW)));
            } else if (mc.player != null && mc.player.isCreative()) {
                lines.add(ModdedCommand
                        .createPrefix(I18n.get("gui.ee.rightClick"), ChatFormatting.YELLOW, ChatFormatting.GOLD)
                        .append(ModdedCommand.createText(I18n.get("gui.ee.give.give"), ChatFormatting.YELLOW)));
            }
        }

        if (EEMod.doesDisableToolTip() && !type.isAdvanced()) {
            return;
        }

        var containerData = ItemUtils.getContainerSize(stack);
        if (containerData != null && isControlDown() && isShiftDown()) {
            if (isKeyDown(KeyBindingHelper.getBoundKeyOf(giver).getValue())) {
                mc.setScreen(new GuiGiver(mc.screen, stack));
            }
            // displayInventory(ev); // TODO: Implement inventory display rendering
            lines.add(EEMod.HIDE_COMPONENT);
            return;
        }

        if (isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            CompoundTag compound = ItemUtils.getTag(stack);
            if (!type.isAdvanced()) {
                lines.add(
                        Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }
            // Tab lookup logic needs update for 1.21
            
             if (!(mc.screen instanceof GuiModifier)) {
                if (KeyBindingHelper.getBoundKeyOf(giver).getValue() != 0 && isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                    if (isKeyDown(KeyBindingHelper.getBoundKeyOf(giver).getValue())) {
                        mc.setScreen(new GuiGiver(mc.screen, stack));
                    }
                    lines.add(ModdedCommand
                            .createPrefix(KeyBindingHelper.getBoundKeyOf(giver).getDisplayName().getString(), ChatFormatting.YELLOW,
                                    ChatFormatting.GOLD)
                            .append(ModdedCommand.createTranslatedText("cmd.ee.opengiver", ChatFormatting.YELLOW)));
                }
                if (KeyBindingHelper.getBoundKeyOf(menu).getValue() != 0) {
                    if (isKeyDown(KeyBindingHelper.getBoundKeyOf(menu).getValue())) {
                        String code = ItemUtils.getGiveCode(stack).replace(com.dutchmtc.ee.utils.ChatUtils.MODIFIER, '&');
                        EEMod.saveItem(code);
                        mc.setScreen(new GuiMenu(mc.screen));
                    }
                    lines.add(ModdedCommand
                            .createPrefix(KeyBindingHelper.getBoundKeyOf(menu).getDisplayName().getString(), ChatFormatting.YELLOW,
                                    ChatFormatting.GOLD)
                            .append(ModdedCommand.createTranslatedText("gui.ee.save", ChatFormatting.YELLOW)));
                }
            }
        } else {
            lines.add(Component.literal("SHIFT ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("gui.ee.shift").withStyle(ChatFormatting.GOLD)));
        }
        if (containerData != null) {
            lines.add(Component.literal("SHIFT + CTRL ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("gui.ee.shiftctrl").withStyle(ChatFormatting.GOLD)));
        }
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    private static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isControlDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static void openGiver() {
        Minecraft mc = Minecraft.getInstance();
        assert mc.player != null;
        final int slot = mc.player.getInventory().getSelectedSlot();
        GuiUtils.displayScreen(new GuiItemStackModifier(null, mc.player.getMainHandItem().copy(),
                is -> ItemUtilsClient.give(is, 36 + slot)));
    }

    public static void drawString(Font renderer, String str, int x, int y, int color) {
        // Placeholder if needed, but should use GuiGraphics
    }
}
