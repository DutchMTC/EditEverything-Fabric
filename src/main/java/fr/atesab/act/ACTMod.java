package fr.atesab.act;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import fr.atesab.act.command.ModdedCommand;
import fr.atesab.act.command.ModdedCommandACT;
import fr.atesab.act.command.ModdedCommandGamemode;
import fr.atesab.act.command.ModdedCommandGamemodeQuick;
import fr.atesab.act.command.arguments.ConnectionPlayerArgument;
import fr.atesab.act.command.arguments.PlayerListArgumentType;
import fr.atesab.act.command.arguments.StringListArgumentType;
import fr.atesab.act.config.Configuration;
import fr.atesab.act.gui.GuiGiver;
import fr.atesab.act.gui.modifier.GuiModifier;
import fr.atesab.act.gui.modifier.nbt.GuiNBTModifier;
import fr.atesab.act.gui.selector.GuiButtonListSelector;
import fr.atesab.act.internalcommand.InternalCommandExecutor;
import fr.atesab.act.utils.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ACTMod implements ModInitializer {
    public enum ACTState {
        RELEASE(null), BETA(ChatFormatting.GOLD), ALPHA(ChatFormatting.DARK_RED);

        private final ChatFormatting color;

        ACTState(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting getColor() {
            return color;
        }

        public boolean isShow() {
            return color != null;
        }
    }

    public static final ACTState MOD_STATE = ACTState.RELEASE;
    public static final String MOD_ID = "act";
    private static String modName = null;
    private static String modVersion = null;
    private static final String modLittleName = "ACT-Mod";
    private static final String[] modAuthorsArray = {"ATE47"};
    private static final String modAuthors = String.join(", ", modAuthorsArray);
    private static final String modLicense = "GNU GPL 3";
    private static final String modLicenseLink = "https://www.gnu.org/licenses/gpl-3.0.en.html";
    private static final String modLink = "https://www.curseforge.com/minecraft/mc-mods/advanced-extended-creative-mode";

    @Deprecated
    public static final String MOD_FACTORY = "fr.atesab.act.gui.ModGuiFactory";
    private static ModdedCommandACT modCommand;
    private static boolean instantMineEnabled = false;
    private static boolean instantPlaceEnabled = false;
    public static final AdvancedCreativeTab ADVANCED_CREATIVE_TAB = new AdvancedCreativeTab();
    public static final String TEMPLATE_TAG_NAME = "TemplateData";
    public static final Random RANDOM = new Random();
    public static final RandomSource RANDOM_SOURCE = RandomSource.create();
    
    // Client-side only field, but kept here for now to avoid breaking too many refs. 
    // Should be accessed carefully or moved.
    // private static final PoseStack STACK = new PoseStack(); 

    @SuppressWarnings("unchecked")
    public static final String[] DEFAULT_CUSTOM_ITEMS = {
            ItemUtils.getGiveCode(
                    ItemUtils.buildStack(Blocks.PINK_WOOL, 42, ChatFormatting.LIGHT_PURPLE + "Pink verity",
                            new String[]{"" + ChatFormatting.GOLD + ChatFormatting.BOLD + "42 is life",
                                    "" + ChatFormatting.GOLD + ChatFormatting.BOLD + "wait what ?"},
                            new Tuple[0]))};
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID.toUpperCase());
    
    // KeyMappings are client-side. We'll expose getters but they will be initialized in ClientModInitializer
    // or we can keep them here if we are careful. Ideally they should be in ACTModClient.
    // For now, I'll keep the static references but initialize them in ACTModClient.
    // Actually, let's move them to ACTModClient and provide accessors if needed, 
    // OR keep them here as public static fields initialized by the client.
    // I'll keep them here for compatibility with existing code structure, but they will be null on server.
    // public static KeyMapping giver, menu, edit; 
    
    private static final List<ItemStack> templates = new ArrayList<>();
    private static final Map<String, Map<String, Consumer<StringModifier>>> stringModifier = new HashMap<>();
    private static final Configuration config = new Configuration();
    private static final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private static CommandDispatcher<SharedSuggestionProvider> SharedSuggestionProvider;
    private static final InternalCommandExecutor internalCommandExecutor = new InternalCommandExecutor();
    public static final Component HIDE_COMPONENT = Component.literal("%HIDE_COMPONENT%");

    public static boolean doesDisableToolTip() {
        return config.doesDisableToolTip();
    }

    public static SyncList<String> getCustomItems() {
        return config.getCustomitems();
    }

    public static CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }

    public static Map<String, Map<String, Consumer<StringModifier>>> getStringModifier() {
        return stringModifier;
    }

    public static String getTemplateData(ItemStack template) {
        return ItemUtils.getCustomTag(template, TEMPLATE_TAG_NAME, null);
    }

    public static Stream<ItemStack> getTemplates() {
        return templates.stream().map(is -> {
            String lang = ItemUtils.getCustomTag(is, TEMPLATE_TAG_NAME + "Lang", null);
            Component display = (lang != null ? Component.translatable(lang) : is.getDisplayName());
            display.copy().withStyle(ChatFormatting.AQUA);
            ItemStack copy = is.copy();
            copy.set(DataComponents.CUSTOM_NAME, display);
            return copy;
        });
    }

    // Client-side method
    public static boolean isKeyDown(int key) {
        return ACTModClient.isKeyDown(key);
    }

    // Client-side method
    public static void openGiver() {
        ACTModClient.openGiver();
    }

    public static void registerStringModifier(String name, Consumer<StringModifier> modifier) {
        registerStringModifier(name, "", modifier);
    }

    public static void registerStringModifier(String name, String category, Consumer<StringModifier> modifier) {
        stringModifier.computeIfAbsent(category, k -> new HashMap<>()).put(name, modifier);
    }

    public static void registerTemplate(String lang, ItemStack icon, String data) {
        templates.add(ItemUtils.setCustomTag(ItemUtils.setCustomTag(icon.copy(), TEMPLATE_TAG_NAME, data),
                TEMPLATE_TAG_NAME + "Lang", lang));
    }

    public static void saveConfigs() {
        config.save();
    }

    public static void saveItem(String code) {
        LOGGER.info("Adding : {}", code);
        config.getCustomitems().add(0, code);
    }

    public static void setDoesDisableToolTip(boolean doesDisableToolTip) {
        config.setDoesDisableToolTip(doesDisableToolTip);
    }

    // Client-side method
    public static void drawString(Font renderer, String str, int x, int y, int color) {
        ACTModClient.drawString(renderer, str, x, y, color);
    }

    // Client-side method
    public static void spectatorTeleport(PlayerInfo to) {
        spectatorTeleport(to.getProfile().getId());
    }

    // Client-side method
    public static void spectatorTeleport(UUID to) {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        var mode = Objects.requireNonNull(Objects.requireNonNull(Minecraft.getInstance().getConnection()).getPlayerInfo(p.getGameProfile().getId())).getGameMode();
        if (mode != GameType.SPECTATOR) {
            ModdedCommand.sendSigned("/gamemode " + GameType.SPECTATOR.getName());
        }
        p.connection.send(new ServerboundTeleportToEntityPacket(to));
        if (mode != GameType.SPECTATOR) {
            ModdedCommand.sendSigned("/gamemode " + mode.getName());
        }
    }

    public static ACTState getModState() {
        return MOD_STATE;
    }

    public static String getModId() {
        return MOD_ID;
    }

    public static String getModName() {
        return Optional.ofNullable(modName).orElseThrow(() -> new RuntimeException("mod not init"));
    }

    public static String getModVersion() {
        return Optional.ofNullable(modVersion).orElseThrow(() -> new RuntimeException("mod not init"));
    }

    public static String getModLittleName() {
        return modLittleName;
    }

    public static String[] getModAuthorsArray() {
        return modAuthorsArray;
    }

    public static String getModAuthors() {
        return modAuthors;
    }

    public static String getModLicense() {
        return modLicense;
    }

    public static String getModLicenseLink() {
        return modLicenseLink;
    }

    public static String getModLink() {
        return modLink;
    }

    public static boolean isInstantMineEnabled() {
        return instantMineEnabled;
    }

    public static void setInstantMineEnabled(boolean instantMineEnabled) {
        ACTMod.instantMineEnabled = instantMineEnabled;
    }

    public static boolean isInstantPlaceEnabled() {
        return instantPlaceEnabled;
    }

    public static void setInstantPlaceEnabled(boolean instantPlaceEnabled) {
        ACTMod.instantPlaceEnabled = instantPlaceEnabled;
    }

    public static ModdedCommandACT getModCommand() {
        return modCommand;
    }

    @Override
    public void onInitialize() {
        // Common Setup
        internalCommandExecutor.registerModule(ACTUtils.class);
        internalCommandExecutor.registerModule(ChatUtils.class);
        internalCommandExecutor.registerModule(CommandUtils.class);
        internalCommandExecutor.registerModule(FileUtils.class);
        internalCommandExecutor.registerModule(GuiUtils.class);
        internalCommandExecutor.registerModule(ItemUtils.class);
        internalCommandExecutor.registerModule(ReflectionUtils.class);

        // Register Creative Tab
        ADVANCED_CREATIVE_TAB.register();

        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isPresent()) {
            ModMetadata metadata = container.get().getMetadata();
            modName = metadata.getName();
            modVersion = metadata.getVersion().getFriendlyString();
            modCommand = new ModdedCommandACT();
        }

        // Config
        config.sync(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
        config.addCustomItemsCallback(this::syncItemConfig);

        // Register Argument Types
        ArgumentTypeRegistry.registerArgumentType(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "player_list"),
                PlayerListArgumentType.class,
                SingletonArgumentInfo.contextFree(PlayerListArgumentType::new));

        ArgumentTypeRegistry.registerArgumentType(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "string_list"),
                StringListArgumentType.class,
                SingletonArgumentInfo.contextFree(() -> new StringListArgumentType(Collections::emptyList, Collections.emptyList(), true)));

        ArgumentTypeRegistry.registerArgumentType(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "connection_player"),
                ConnectionPlayerArgument.class,
                SingletonArgumentInfo.contextFree(ConnectionPlayerArgument::player));

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            registerCommandDispatcher(dispatcher, context);
            // Also register to our internal dispatcher if needed, or just use the main one
            registerCommandDispatcher(ACTMod.dispatcher, context);
        });

        // Register Templates
        registerTemplate("gui.act.menu.template.empty", new ItemStack(Items.PAPER), "");
        registerTemplate("gui.act.menu.template.stone", new ItemStack(Blocks.STONE),
                ItemUtils.getGiveCode(new ItemStack(Blocks.STONE)));
        registerTemplate("gui.act.menu.template.potion", new ItemStack(Items.POTION),
                ItemUtils.getGiveCode(new ItemStack(Items.POTION)));
        registerTemplate("gui.act.menu.template.fireworks", new ItemStack(Items.FIREWORK_ROCKET),
                ItemUtils.getGiveCode(new ItemStack(Items.FIREWORK_ROCKET)));
        registerTemplate(Items.PLAYER_HEAD.getDescriptionId(), new ItemStack(Items.PLAYER_HEAD),
                ItemUtils.getGiveCode(new ItemStack(Items.PLAYER_HEAD)));
        registerTemplate("gui.act.menu.template.command", new ItemStack(Blocks.COMMAND_BLOCK),
                ItemUtils.getGiveCode(new ItemStack(Blocks.COMMAND_BLOCK)));
        registerTemplate(Items.EGG.getDescriptionId(), new ItemStack(Items.EGG),
                ItemUtils.getGiveCode(new ItemStack(Items.EGG)));

        // Register Modifiers (using BuiltInRegistries)
        BuiltInRegistries.ITEM.entrySet().forEach(e -> {
            var registryName = e.getKey().location();
            var i = e.getValue();
            registerStringModifier(i.getDescriptionId() + ".name", "registry.items",
                    sm -> sm.setString(registryName.toString()));
        });

        BuiltInRegistries.BLOCK.entrySet().forEach(e -> {
            var registryName = e.getKey().location();
            var b = e.getValue();
            registerStringModifier(b.getName().getString(), "registry.blocks",
                    sm -> sm.setString(registryName.toString()));
        });

        BuiltInRegistries.POTION.entrySet().forEach(e -> {
            var registryName = e.getKey().location();
            var p = e.getValue();
            registerStringModifier(registryName.toString(), "registry.potions",
                    sm -> sm.setString(registryName.toString()));
        });

        // BuiltInRegistries.BIOME.keySet().forEach(k -> registerStringModifier(k.toString(), "registry.biomes",
        //         sm -> sm.setString(k.toString())));

        BuiltInRegistries.SOUND_EVENT.keySet().forEach(s -> registerStringModifier(s.toString(), "registry.sounds",
                sm -> sm.setString(s.toString())));

        BuiltInRegistries.VILLAGER_PROFESSION.forEach(vp -> registerStringModifier(vp.name(), "registry.villagerProfessions",
                sm -> sm.setString(vp.toString())));

        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(ee -> registerStringModifier(ee.getValue().getDescriptionId(), "registry.entities",
                sm -> sm.setString(ee.getKey().location().toString())));

        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(ee -> registerStringModifier(ee.getValue().getDescriptionId(), "attributes",
                sm -> sm.setString(ee.getKey().location().toString())));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            registerStringModifier("item.modifiers." + slot.getName(), "attributes.slot",
                    sm -> sm.setString(slot.getName()));
        }

        // Client-side modifiers (Giver, NBT, etc.) - These might need to be moved or guarded
        // Since they use Gui classes, they MUST be guarded or moved.
        // I'll move them to ACTModClient or a separate ClientSetup class.
        // For now, I'll comment them out here and move them to ACTModClient.
    }

    private void syncItemConfig(List<String> itemConfig) {
        // force the regeneration of the tabs
        // CreativeModeTabs.CACHED_ENABLED_FEATURES = null; // Not available/needed in Fabric usually
        // We might need to trigger a refresh of the creative tab
        ADVANCED_CREATIVE_TAB.refresh();
    }

    private void registerCommandDispatcher(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        new ModdedCommandACT().register(dispatcher, context);
        new ModdedCommandGamemode("gm").register(dispatcher, context);
        new ModdedCommandGamemodeQuick("gmc", GameType.CREATIVE).register(dispatcher, context);
        new ModdedCommandGamemodeQuick("gma", GameType.ADVENTURE).register(dispatcher, context);
        new ModdedCommandGamemodeQuick("gms", GameType.SURVIVAL).register(dispatcher, context);
        new ModdedCommandGamemodeQuick("gmsp", GameType.SPECTATOR).register(dispatcher, context);
    }

    // Helper for suggestions (kept from original)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void createSuggestion(CommandNode<CommandSourceStack> dispatcher,
                                  CommandNode<SharedSuggestionProvider> rootCommandNode, CommandSourceStack player,
                                  Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> suggestions) {
        for (CommandNode<CommandSourceStack> child : dispatcher.getChildren()) {
            ArgumentBuilder<SharedSuggestionProvider, ?> argumentbuilder = (ArgumentBuilder) child.createBuilder();
            argumentbuilder.requires((ctx) -> true);
            if (argumentbuilder.getCommand() != null) {
                argumentbuilder.executes((ctx) -> 0);
            }

            if (argumentbuilder instanceof RequiredArgumentBuilder) {
                RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredargumentbuilder = (RequiredArgumentBuilder) argumentbuilder;
                if (requiredargumentbuilder.getSuggestionsProvider() != null) {
                    requiredargumentbuilder
                            .suggests(SuggestionProviders.safelySwap(requiredargumentbuilder.getSuggestionsProvider()));
                }
            }

            if (argumentbuilder.getRedirect() != null) {
                argumentbuilder.redirect(suggestions.get(argumentbuilder.getRedirect()));
            }

            CommandNode<SharedSuggestionProvider> commandnode1 = argumentbuilder.build();
            suggestions.put(child, commandnode1);
            rootCommandNode.addChild(commandnode1);
            if (!child.getChildren().isEmpty()) {
                createSuggestion(child, commandnode1, player, suggestions);
            }
        }
    }
    
    public static CommandDispatcher<SharedSuggestionProvider> getSharedSuggestionProvider() {
        return SharedSuggestionProvider;
    }

    public static void setSharedSuggestionProvider(CommandDispatcher<SharedSuggestionProvider> sharedSuggestionProvider) {
        SharedSuggestionProvider = sharedSuggestionProvider;
    }
}
