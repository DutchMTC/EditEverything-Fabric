package fr.atesab.act;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import fr.atesab.act.command.ModdedCommand;
import fr.atesab.act.gui.GuiACT;
import fr.atesab.act.gui.GuiGiver;
import fr.atesab.act.gui.GuiMenu;
import fr.atesab.act.gui.modifier.GuiItemStackModifier;
import fr.atesab.act.gui.modifier.GuiModifier;
import fr.atesab.act.gui.modifier.nbt.GuiNBTModifier;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.utils.CommandUtils;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import fr.atesab.act.utils.ReflectionUtils;
import fr.atesab.act.utils.Tuple;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ACTModClient implements ClientModInitializer {

    private static KeyMapping giver, menu, edit;

    @Override
    public void onInitializeClient() {
        // Register KeyMappings
        giver = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.act.giver", GLFW.GLFW_KEY_Y, "key.act"));
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.act.menu", GLFW.GLFW_KEY_N, "key.act"));
        edit = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.act.edit", GLFW.GLFW_KEY_H, "key.act"));

        // Client Tick
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Screen Events
        ScreenEvents.AFTER_INIT.register(this::onInitGui);

        // Tooltips
        ItemTooltipCallback.EVENT.register(this::onRenderTooltip);

        // Register Client-side Modifiers
        registerClientModifiers();

        // Build sub items
        // ACTMod.ADVANCED_CREATIVE_TAB.buildSubItems(); // Can't call this safely here if it uses server logic? 
        // Actually AdvancedCreativeTab is common.
    }

    private void registerClientModifiers() {
        // giver
        ACTMod.registerStringModifier("gui.act.modifier.string.giver", "",
                sm -> sm.setNextScreen(new GuiGiver(sm.getNextScreen(), sm.getString(), sm::setString, false)));

        // NBT editor
        ACTMod.registerStringModifier("gui.act.modifier.string.nbt", "", sm -> {
            try {
                sm.setNextScreen(new GuiNBTModifier(sm.getNextScreen(), nbt -> sm.setString(nbt.toString()),
                        TagParser.parseTag(sm.getString())));
            } catch (Exception ignore) {
            }
        });

        // players names
        ACTMod.registerStringModifier("gui.act.modifier.string.players", "", sm -> {
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
                    Component.translatable("gui.act.modifier.string.players"), btn, s -> {
                sm.setString(s);
                return null;
            }));
        });

        // Base64
        ACTMod.registerStringModifier("gui.act.modifier.string.b64.encode", "b64", sm -> {
            try {
                sm.setString(new String(Base64.getEncoder().encode(sm.getString().getBytes())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        ACTMod.registerStringModifier("gui.act.modifier.string.b64.decode", "b64", sm -> {
            try {
                sm.setString(new String(Base64.getDecoder().decode(sm.getString().getBytes())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void onClientTick(Minecraft mc) {
        if (ACTMod.isInstantPlaceEnabled()) {
            ReflectionUtils.setRightClickDelay(mc, 0);
        }
        if (ACTMod.isInstantMineEnabled() && mc.gameMode != null) {
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
            if (s instanceof GuiACT && ACTMod.MOD_STATE.isShow()) {
                guiGraphics.drawString(mc.font, "Warning! Currently in " + ACTMod.MOD_STATE.getColor() + ACTMod.MOD_STATE.name(),
                        5, 5, 0xffffffff);
            }
        });
    }

    private void injectSuggestions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CommandDispatcher<SharedSuggestionProvider> current = mc.player.connection.getCommands();
            if (current != ACTMod.getSharedSuggestionProvider()) {
                ACTMod.setSharedSuggestionProvider(current);

                Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map = new HashMap<>();
                RootCommandNode<SharedSuggestionProvider> root = ACTMod.getSharedSuggestionProvider().getRoot();
                map.put(ACTMod.getDispatcher().getRoot(), root);
                ACTMod.createSuggestion(ACTMod.getDispatcher().getRoot(), root, mc.player.createCommandSourceStack(), map);
            }
        }
    }

    private void onRenderTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag type, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();

        if (!(!(mc.screen instanceof GuiModifier) && KeyBindingHelper.getBoundKeyOf(giver).getValue() != 0
                && isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) && mc.screen instanceof GuiMenu) {
            lines.add(ModdedCommand
                    .createPrefix(I18n.get("gui.act.leftClick"), ChatFormatting.YELLOW, ChatFormatting.GOLD)
                    .append(ModdedCommand.createText(
                            I18n.get(isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) ? "gui.act.give.copy" : "gui.act.give.editor"),
                            ChatFormatting.YELLOW)));
            if (isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                lines.add(ModdedCommand
                        .createPrefix(I18n.get("gui.act.rightClick"), ChatFormatting.YELLOW,
                                ChatFormatting.GOLD)
                        .append(ModdedCommand.createText(I18n.get("gui.act.delete"), ChatFormatting.YELLOW)));
            } else if (mc.player != null && mc.player.isCreative()) {
                lines.add(ModdedCommand
                        .createPrefix(I18n.get("gui.act.rightClick"), ChatFormatting.YELLOW, ChatFormatting.GOLD)
                        .append(ModdedCommand.createText(I18n.get("gui.act.give.give"), ChatFormatting.YELLOW)));
            }
        }

        if (ACTMod.doesDisableToolTip() && !type.isAdvanced()) {
            return;
        }

        var containerData = ItemUtils.getContainerSize(stack);
        if (containerData != null && Screen.hasControlDown() && Screen.hasShiftDown()) {
            if (isKeyDown(KeyBindingHelper.getBoundKeyOf(giver).getValue())) {
                mc.setScreen(new GuiGiver(mc.screen, stack));
            }
            // displayInventory(ev); // TODO: Implement inventory display rendering
            lines.add(ACTMod.HIDE_COMPONENT);
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
                            .append(ModdedCommand.createTranslatedText("cmd.act.opengiver", ChatFormatting.YELLOW)));
                }
                if (KeyBindingHelper.getBoundKeyOf(menu).getValue() != 0) {
                    if (isKeyDown(KeyBindingHelper.getBoundKeyOf(menu).getValue())) {
                        String code = ItemUtils.getGiveCode(stack).replace(fr.atesab.act.utils.ChatUtils.MODIFIER, '&');
                        ACTMod.saveItem(code);
                        mc.setScreen(new GuiMenu(mc.screen));
                    }
                    lines.add(ModdedCommand
                            .createPrefix(KeyBindingHelper.getBoundKeyOf(menu).getDisplayName().getString(), ChatFormatting.YELLOW,
                                    ChatFormatting.GOLD)
                            .append(ModdedCommand.createTranslatedText("gui.act.save", ChatFormatting.YELLOW)));
                }
            }
        } else {
            lines.add(Component.literal("SHIFT ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("gui.act.shift").withStyle(ChatFormatting.GOLD)));
        }
        if (containerData != null) {
            lines.add(Component.literal("SHIFT + CTRL ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.translatable("gui.act.shiftctrl").withStyle(ChatFormatting.GOLD)));
        }
    }

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    public static void openGiver() {
        Minecraft mc = Minecraft.getInstance();
        assert mc.player != null;
        final int slot = mc.player.getInventory().selected;
        GuiUtils.displayScreen(new GuiItemStackModifier(null, mc.player.getMainHandItem().copy(),
                is -> ItemUtils.give(is, 36 + slot)));
    }

    public static void drawString(Font renderer, String str, int x, int y, int color) {
        // Placeholder if needed, but should use GuiGraphics
    }
}
