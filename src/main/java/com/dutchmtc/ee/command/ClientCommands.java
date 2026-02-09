package com.dutchmtc.ee.command;

import com.dutchmtc.ee.EEMod;
import com.dutchmtc.ee.EEModClient;
import com.dutchmtc.ee.network.EENetworking;
import com.dutchmtc.ee.utils.ItemReader;
import com.dutchmtc.ee.utils.ItemUtils;
import com.dutchmtc.ee.utils.ItemUtilsClient;
import com.dutchmtc.ee.utils.VersionCompat;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.dutchmtc.ee.utils.Tuple;

public class ClientCommands {
    private static volatile boolean pickMethodSearched;
    private static volatile Method pickMethod;
    private static volatile boolean crosshairFieldSearched;
    private static volatile List<Field> minecraftEntityFields;
    private static volatile boolean tickHookRegistered;
    private static volatile PendingArmorStandOpen pendingArmorStandOpen;

    private record PendingArmorStandOpen(int entityId, boolean showGetAsItemButton) {
    }

    public static void register() {
        if (!tickHookRegistered) {
            tickHookRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(ClientCommands::onEndClientTick);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /ee
            LiteralArgumentBuilder<FabricClientCommandSource> ee = ClientCommandManager.literal("ee");
            
            // /ee menu | /ee om
            registerMenu(ee);
            
            // /ee give | /ee g
            registerGive(ee, registryAccess);
            
            // /ee edit | /ee e
            registerEdit(ee);
            
            // /ee opengiver
            registerOpenGiver(ee);
            
            // /ee instantclick
            registerInstantClick(ee);
            
            // /ee instantplace
            registerInstantPlace(ee);
            
            // /ee color
            registerColor(ee);
            
            // /ee enchant
            registerEnchant(ee, registryAccess);
            
            // /ee rename
            registerRename(ee);
            
            // /ee unbreakable
            registerUnbreakable(ee);
            
            // /ee head
            registerHead(ee);
            
            // /ee randomfireworks | /ee rfw
            registerRandomFireworks(ee);
            
            // /ee info
            registerInfo(ee);
            
            // /ee format
            registerFormat(ee);
            
            // /ee palette
            registerPalette(ee);
            
            // /ee armorstand | /ee as
            registerArmorStand(ee);

            // /ee spectatortp | /ee sptp
            registerSpTp(ee);

            // /ee help
            ee.then(ClientCommandManager.literal("help").executes(c -> {
                showHelp(c.getSource());
                return 1;
            }));
            
            // /ee (no args)
            ee.executes(c -> {
                showHelp(c.getSource());
                return 1;
            });

            // Register /ee and aliases
            LiteralCommandNode<FabricClientCommandSource> eeNode = dispatcher.register(ee);
            dispatcher.register(ClientCommandManager.literal("editeverything").redirect(eeNode));
            
            // /gm
            registerGamemode(dispatcher);
        });
    }

    private static void registerMenu(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var menu = ClientCommandManager.literal("menu")
                .executes(c -> {
                    openMenu("");
                    return 1;
                })
                .then(ClientCommandManager.argument("menuoptions", StringArgumentType.greedyString())
                        .executes(c -> {
                            openMenu(StringArgumentType.getString(c, "menuoptions"));
                            return 1;
                        }));
        
        LiteralCommandNode<FabricClientCommandSource> menuNode = menu.build();
        root.then(menu);
        
        // Alias /ee om
        root.then(ClientCommandManager.literal("om").redirect(menuNode));
    }
    
    private static void openMenu(String options) {
        Minecraft.getInstance().execute(() -> {
             com.dutchmtc.ee.utils.GuiUtils.displayScreen(new com.dutchmtc.ee.gui.GuiMenu(null));
        });
    }

    private static void registerGive(LiteralArgumentBuilder<FabricClientCommandSource> root, net.minecraft.commands.CommandBuildContext registryAccess) {
        var give = ClientCommandManager.literal("give")
                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .executes(c -> {
                            String args = StringArgumentType.getString(c, "args");
                            ItemStack stack = new ItemReader(registryAccess).readItem(args);
                            if (stack != null && !stack.isEmpty()) {
                                ItemUtilsClient.give(stack);
                                return 1;
                            }
                            c.getSource().sendError(Component.literal("Invalid item: " + args));
                            return 0;
                        }));
        
        LiteralCommandNode<FabricClientCommandSource> giveNode = give.build();
        root.then(give);
        // Alias g
        root.then(ClientCommandManager.literal("g").redirect(giveNode));
    }
    
    private static void registerEdit(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var edit = ClientCommandManager.literal("edit")
                .executes(c -> {
                    Minecraft.getInstance().execute(EEModClient::openGiver);
                    return 1;
                });
        
        LiteralCommandNode<FabricClientCommandSource> editNode = edit.build();
        root.then(edit);
        root.then(ClientCommandManager.literal("e").redirect(editNode));
    }
    
    private static void registerOpenGiver(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("opengiver")
                .executes(c -> {
                    Minecraft.getInstance().execute(() -> {
                        com.dutchmtc.ee.utils.GuiUtils.displayScreen(new com.dutchmtc.ee.gui.GuiGiver(null));
                    });
                    return 1;
                }));
    }
    
    private static void registerInstantClick(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("instantclick")
                .executes(c -> {
                    EEMod.setInstantMineEnabled(!EEMod.isInstantMineEnabled());
                    c.getSource().sendFeedback(Component.literal("Instant Click: " + EEMod.isInstantMineEnabled()));
                    return 1;
                }));
    }
    
    private static void registerInstantPlace(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("instantplace")
                .executes(c -> {
                    EEMod.setInstantPlaceEnabled(!EEMod.isInstantPlaceEnabled());
                    c.getSource().sendFeedback(Component.literal("Instant Place: " + EEMod.isInstantPlaceEnabled()));
                    return 1;
                }));
    }
    
    private static void registerColor(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("color")
                .executes(c -> {
                    Minecraft.getInstance().execute(() -> {
                        try {
                            var mc = Minecraft.getInstance();
                            if (mc.player == null) return;
                            var is = mc.player.getMainHandItem();
                            int currentColor = ItemUtils.getGlobalColor(is).orElse(0xFFFFFF);
                            com.dutchmtc.ee.gui.modifier.GuiColorModifier screen = new com.dutchmtc.ee.gui.modifier.GuiColorModifier(null, (newColor) -> {
                                if (mc.player == null) {
                                    return;
                                }
                                var stack = mc.player.getMainHandItem();
                                int slot = 36 + mc.player.getInventory().getSelectedSlot();
                                ItemUtilsClient.give(ItemUtils.setGlobalColor(stack, newColor), slot);
                            }, currentColor);
                            Minecraft.getInstance().setScreen(screen);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return 1;
                }));
    }
    
    private static void registerEnchant(LiteralArgumentBuilder<FabricClientCommandSource> root, net.minecraft.commands.CommandBuildContext registryAccess) {
        root.then(ClientCommandManager.literal("enchant")
                .then(ClientCommandManager.argument("enchantment", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(registryAccess.lookupOrThrow(Registries.ENCHANTMENT).listElementIds().map(ResourceKey::identifier), b))
                        .then(ClientCommandManager.argument("level", IntegerArgumentType.integer())
                                .executes(c -> {
                                    String idStr = StringArgumentType.getString(c, "enchantment");
                                    Identifier id = Identifier.tryParse(idStr);
                                    if (id == null) {
                                        c.getSource().sendError(Component.literal("Invalid identifier: " + idStr));
                                        return 0;
                                    }
                                    var registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
                                    var key = ResourceKey.create(Registries.ENCHANTMENT, id);
                                    if (registry.get(key).isEmpty()) {
                                        c.getSource().sendError(Component.literal("Unknown enchantment: " + id));
                                        return 0;
                                    }
                                    Holder<Enchantment> enchantment = registry.get(key).get();
                                    int level = IntegerArgumentType.getInteger(c, "level");
                                    applyEnchantment(enchantment, level);
                                    return 1;
                                }))
                        .executes(c -> {
                            String idStr = StringArgumentType.getString(c, "enchantment");
                            Identifier id = Identifier.tryParse(idStr);
                            if (id == null) {
                                c.getSource().sendError(Component.literal("Invalid identifier: " + idStr));
                                return 0;
                            }
                            var registry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
                            var key = ResourceKey.create(Registries.ENCHANTMENT, id);
                            if (registry.get(key).isEmpty()) {
                                c.getSource().sendError(Component.literal("Unknown enchantment: " + id));
                                return 0;
                            }
                            Holder<Enchantment> enchantment = registry.get(key).get();
                            applyEnchantment(enchantment, 1);
                            return 1;
                        })));
    }
    
    private static void applyEnchantment(Holder<Enchantment> enchantment, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack item = mc.player.getMainHandItem();
        if (item.isEmpty()) return;
        
        boolean book = item.getItem().equals(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        List<Tuple<Enchantment, Integer>> enchants = ItemUtils.getEnchantments(item, book);
        enchants.add(new Tuple<>(enchantment.value(), level));
        ItemUtils.setEnchantments(enchants, item, book, mc.player.level().registryAccess());
        
        int slot = 36 + mc.player.getInventory().getSelectedSlot();
        ItemUtilsClient.give(item, slot);
    }
    
    private static void registerRename(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("rename")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            ItemStack item = mc.player.getMainHandItem();
                            if (item.isEmpty()) return 0;
                            
                            item.set(DataComponents.CUSTOM_NAME, com.dutchmtc.ee.utils.ChatUtils.parseLegacyFormattingComponent(name));
                            int slot = 36 + mc.player.getInventory().getSelectedSlot();
                            ItemUtilsClient.give(item, slot);
                            return 1;
                        })));
    }

    private static void registerFormat(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("format")
                .executes(c -> {
                    MutableComponent text = Component.literal("");
                    int element = 0;
                    int line = 0;
                    for (ChatFormatting format : ChatFormatting.values()) {
                        HoverEvent he = new HoverEvent.ShowText(Component.literal(
                                format.getName() + " (&" + format.toString().substring(1) + ")").withStyle(ChatFormatting.YELLOW));
                        text = text.append(
                                Component.literal("&" + format.toString().substring(1) + " ").withStyle(s -> {
                                    s.withHoverEvent(he);
                                    return s;
                                }).withStyle(ChatFormatting.RESET));
                        text = text.append(Component.literal("&" + format.toString().substring(1)).withStyle(s -> {
                                    s.withHoverEvent(he);
                                    return s;
                                }).withStyle(format)
                        ).append(Component.literal(" ").withStyle(ChatFormatting.RESET));
                        if (++element == 8) {
                            c.getSource().sendFeedback(text);
                            text = Component.literal("");
                            element = 0;
                            line++;
                        }
                    }
                    if (element != 0) {
                        c.getSource().sendFeedback(text);
                    }
                    return 1;
                }));
    }

    private static void registerPalette(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("palette")
                .executes(c -> {
                    c.getSource().sendFeedback(Component.translatable("cmd.ee.palette").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(":").withStyle(ChatFormatting.DARK_GRAY)));
                    
                    MutableComponent row = Component.literal("");
                    int count = 0;
                    for (var cf : ChatFormatting.values()) {
                        if (!cf.isColor()) continue;
                        
                        String code = "&" + cf.getChar();
                        MutableComponent colorBlock = Component.literal(" \u2588 ").withStyle(cf);
                        MutableComponent codeText = Component.literal(code).withStyle(ChatFormatting.WHITE);
                        
                        MutableComponent entry = Component.literal("[")
                                .withStyle(ChatFormatting.DARK_GRAY)
                                .append(colorBlock)
                                .append(codeText)
                                .append(Component.literal("] "))
                                .withStyle(ChatFormatting.DARK_GRAY);
                                
                        entry.withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(code))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy " + code))));
                        
                        row.append(entry);
                        count++;
                        if (count % 4 == 0) {
                            c.getSource().sendFeedback(row);
                            row = Component.literal("");
                        }
                    }
                    if (count % 4 != 0) {
                        c.getSource().sendFeedback(row);
                    }
                    return 1;
                }));
    }

    private static void registerArmorStand(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var armorStand = ClientCommandManager.literal("armorstand")
                .executes(c -> {
                    // Don't forward via sendCommand("ee ..."): Fabric client commands will intercept it and recurse.
                    // Instead, do the raytrace client-side and open the GUI directly.
                    var mc = Minecraft.getInstance();
                    Player player = mc.player;
                    if (player == null) {
                        return 0;
                    }

                    ArmorStand stand = null;
                    refreshClientHitResult(mc, 6.0D);
                    HitResult hitResult = mc.hitResult;
                    if (hitResult instanceof EntityHitResult ehr) {
                        Entity hitEntity = ehr.getEntity();
                        if (hitEntity instanceof ArmorStand as) {
                            stand = as;
                        }
                    }
                    if (stand == null) {
                        stand = findLookedAtArmorStand(player, 6.0D);
                    }
                    if (stand == null) {
                        stand = findCrosshairArmorStand(mc);
                    }
                    if (stand == null) {
                        c.getSource().sendError(Component.translatable("cmd.ee.armorstand.no_target")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    }

                    int entityId = stand.getId();
                    // Always defer opening to the end of the next client tick; otherwise the chat screen can close
                    // after the command executes and overwrite setScreen() (observed on 1.21.10).
                    pendingArmorStandOpen = new PendingArmorStandOpen(entityId, true);
                    return 1;
                });

        LiteralCommandNode<FabricClientCommandSource> armorStandNode = armorStand.build();
        root.then(armorStand);

        // Alias /ee as
        root.then(ClientCommandManager.literal("as").redirect(armorStandNode));
    }

    private static ArmorStand findLookedAtArmorStand(Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(reach));

        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                e -> e instanceof ArmorStand, (float) reach);
        if (hit == null) {
            return null;
        }
        return hit.getEntity() instanceof ArmorStand a ? a : null;
    }

    private static void onEndClientTick(Minecraft client) {
        PendingArmorStandOpen pending = pendingArmorStandOpen;
        if (pending == null) {
            return;
        }
        pendingArmorStandOpen = null;

        client.execute(() -> {
            Screen parent = client.screen;
            client.setScreen(new com.dutchmtc.ee.gui.GuiArmorStandEditor(parent, pending.entityId(), pending.showGetAsItemButton()));
        });
    }

    private static void refreshClientHitResult(Minecraft mc, double reach) {
        if (mc == null) {
            return;
        }
        Method method = pickMethod;
        if (!pickMethodSearched) {
            pickMethodSearched = true;
            method = findPickMethod(mc.getClass());
            pickMethod = method;
        }
        if (method == null) {
            return;
        }
        try {
            method.invoke(mc, reach, 1.0F, false);
        } catch (Throwable ignored) {
        }
    }

    private static Method findPickMethod(Class<?> mcClass) {
        for (Method method : mcClass.getMethods()) {
            if (!HitResult.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 3 || params[0] != double.class || params[1] != float.class || params[2] != boolean.class) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static ArmorStand findCrosshairArmorStand(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        List<Field> fields = minecraftEntityFields;
        if (!crosshairFieldSearched) {
            crosshairFieldSearched = true;
            fields = new ArrayList<>();
            for (Field field : mc.getClass().getDeclaredFields()) {
                if (!Entity.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(field);
            }
            minecraftEntityFields = fields;
        }
        if (fields == null) {
            return null;
        }
        for (Field field : fields) {
            try {
                Object value = field.get(mc);
                if (value instanceof ArmorStand as) {
                    return as;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void registerSpTp(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var sptp = ClientCommandManager.literal("spectatortp")
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                        .executes(c -> {
                            String playerName = StringArgumentType.getString(c, "player");
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.getConnection() != null) {
                                mc.getConnection().sendCommand("tp " + playerName);
                            }
                            return 1;
                        }));
        root.then(sptp);
        root.then(ClientCommandManager.literal("sptp").redirect(sptp.build()));
    }
    
    private static void registerUnbreakable(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("unbreakable")
                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                        .executes(c -> {
                            boolean value = BoolArgumentType.getBool(c, "value");
                            setUnbreakable(value);
                            return 1;
                        }))
                .executes(c -> {
                    setUnbreakable(true);
                    return 1;
                }));
    }
    
    private static void setUnbreakable(boolean value) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack item = mc.player.getMainHandItem();
        if (item.isEmpty()) return;
        
        ItemUtils.setUnbreakable(item, value);
        int slot = 36 + mc.player.getInventory().getSelectedSlot();
        ItemUtilsClient.give(item, slot);
    }
    
    private static void registerHead(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("head")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            try {
                                ItemStack head = ItemUtils.getHead(name);
                                ItemUtilsClient.give(head);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return 1;
                        }))
                .executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        try {
                            ItemStack head = ItemUtils.getHead(mc.player.getScoreboardName());
                            ItemUtilsClient.give(head);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return 1;
                }));
    }
    
    private static void registerRandomFireworks(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        var rfw = ClientCommandManager.literal("randomfireworks")
                .executes(c -> {
                    ItemUtilsClient.give(ItemUtils.getRandomFireworks());
                    return 1;
                });
        
        LiteralCommandNode<FabricClientCommandSource> rfwNode = rfw.build();
        root.then(rfw);
        root.then(ClientCommandManager.literal("rfw").redirect(rfwNode));
    }
    
    private static void registerInfo(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("info")
                .executes(c -> {
                    FabricClientCommandSource src = c.getSource();
                    src.sendFeedback(Component.translatable("cmd.ee.info.title").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(EEMod.getModName()).withStyle(ChatFormatting.WHITE)));
                    src.sendFeedback(Component.translatable("cmd.ee.info.version").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(EEMod.getModVersion()).withStyle(ChatFormatting.WHITE)));
                    src.sendFeedback(Component.translatable("cmd.ee.info.authors").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(EEMod.getModAuthors()).withStyle(ChatFormatting.WHITE)));
                    src.sendFeedback(Component.translatable("cmd.ee.info.licence").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(EEMod.getModLicense()).withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.ee.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(EEMod.getModLicenseLink())))
                                    .withColor(ChatFormatting.WHITE))));
                    src.sendFeedback(Component.translatable("cmd.ee.info.link").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("modrinth.com").withStyle(s -> s
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cmd.ee.info.link.open")
                                            .withStyle(ChatFormatting.YELLOW)))
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(EEMod.getModLink())))
                                    .withColor(ChatFormatting.GREEN))));
                    return 1;
                }));
    }

    private static void registerGamemode(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /gm <gamemode>
        var gm = ClientCommandManager.literal("gm");
        
        for (GameType gametype : GameType.values()) {
            gm.then(clientGamemodeLiteral(gametype.getName(), gametype.getName()));
        }
        
        // /gm <int>
        gm.then(ClientCommandManager.argument("gamemodeid", IntegerArgumentType.integer(0, GameType.values().length - 1))
                .executes(c -> {
                    int id = IntegerArgumentType.getInteger(c, "gamemodeid");
                    GameType type = GameType.byId(id);
                    sendGamemodeCommand(type.getName());
                    return 1;
                })
                .then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
                    int id = IntegerArgumentType.getInteger(c, "gamemodeid");
                    GameType type = GameType.byId(id);
                    String player = StringArgumentType.getString(c, "player");
                    sendGamemodeCommand(type.getName(), player);
                    return 1;
                })));
                
        // Shortcuts: gmc, gms, gma, gmsp
        dispatcher.register(clientGamemodeLiteral("gmc", "creative"));
        dispatcher.register(clientGamemodeLiteral("gms", "survival"));
        dispatcher.register(clientGamemodeLiteral("gma", "adventure"));
        dispatcher.register(clientGamemodeLiteral("gmsp", "spectator"));
        
        // Aliases for /gm <gamemode>
        gm.then(clientGamemodeLiteral("c", "creative"));
        gm.then(clientGamemodeLiteral("1", "creative"));
        gm.then(clientGamemodeLiteral("creative", "creative"));
        
        gm.then(clientGamemodeLiteral("s", "survival"));
        gm.then(clientGamemodeLiteral("0", "survival"));
        gm.then(clientGamemodeLiteral("survival", "survival"));
        
        gm.then(clientGamemodeLiteral("a", "adventure"));
        gm.then(clientGamemodeLiteral("2", "adventure"));
        gm.then(clientGamemodeLiteral("adventure", "adventure"));
        
        gm.then(clientGamemodeLiteral("sp", "spectator"));
        gm.then(clientGamemodeLiteral("3", "spectator"));
        gm.then(clientGamemodeLiteral("spectator", "spectator"));

        dispatcher.register(gm);
    }
    
    private static void sendGamemodeCommand(String gamemode) {
        sendGamemodeCommand(gamemode, null);
    }

    private static void sendGamemodeCommand(String gamemode, String player) {
        String command = "gamemode " + gamemode;
        if (player != null && !player.isBlank()) {
            command += " " + player;
        }
        Minecraft.getInstance().getConnection().sendCommand(command);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> clientGamemodeLiteral(String literal,
                                                                                           String gamemode) {
        return ClientCommandManager.literal(literal)
                .executes(c -> {
                    sendGamemodeCommand(gamemode);
                    return 1;
                })
                .then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
                    String player = StringArgumentType.getString(c, "player");
                    sendGamemodeCommand(gamemode, player);
                    return 1;
                }));
    }

    private static void showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("================ ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("cmd.ee.help", ChatFormatting.YELLOW, EEMod.getModName()))
                .append(Component.literal(" ================").withStyle(ChatFormatting.GOLD)));

        List<String> commands = new ArrayList<>();
        commands.add("menu");
        commands.add("give");
        commands.add("edit");
        commands.add("opengiver");
        commands.add("instantclick");
        commands.add("instantplace");
        commands.add("color");
        commands.add("enchant");
        commands.add("rename");
        commands.add("unbreakable");
        commands.add("head");
        commands.add("randomfireworks");
        commands.add("info");
        commands.add("format");
        commands.add("palette");
        commands.add("armorstand");
        commands.add("spectatortp");

        for (String cmd : commands) {
            MutableComponent component = Component.literal(" > ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(cmd).withStyle(ChatFormatting.YELLOW));
            
            component.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/ee " + cmd + " "))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to suggest command"))));
            
            source.sendFeedback(component);
        }
    }
}
