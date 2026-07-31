package com.dutchmtc.ee;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.dutchmtc.ee.command.ModdedCommandEE;
import com.dutchmtc.ee.command.ModdedCommandGamemode;
import com.dutchmtc.ee.command.ModdedCommandGamemodeQuick;
import com.dutchmtc.ee.command.arguments.StringListArgumentType;
import com.dutchmtc.ee.config.Configuration;
import com.dutchmtc.ee.server.EventWorldAttributeResetter;
import com.dutchmtc.ee.network.EENetworking;
import com.dutchmtc.ee.internalcommand.InternalCommandExecutor;
import com.dutchmtc.ee.utils.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

public class EEMod implements ModInitializer {
    public enum EEState {
        RELEASE(null), BETA(ChatFormatting.GOLD), ALPHA(ChatFormatting.DARK_RED);

        private final ChatFormatting color;

        EEState(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting getColor() {
            return color;
        }

        public boolean isShow() {
            return color != null;
        }
    }

    public static final EEState MOD_STATE = EEState.RELEASE;
    public static final String MOD_ID = "ee";
    public static final char FORMAT_CHAR = '\u00a7';
    private static String modName = null;
    private static String modVersion = null;
    private static final String modLittleName = "EE-Mod";
    private static final String[] modAuthorsArray = {"DutchMTC", "ATE47 (Creator of AdvancedCreativeTab)"};
    private static final String modAuthors = String.join(", ", modAuthorsArray);
    private static final String modLicense = "GNU GPL 3";
    private static final String modLicenseLink = "https://www.gnu.org/licenses/gpl-3.0.en.html";
    private static final String modLink = "https://modrinth.com/project/edit-everything";

    @Deprecated
    public static final String MOD_FACTORY = "com.dutchmtc.ee.gui.ModGuiFactory";
    private static ModdedCommandEE modCommand;
    private static boolean instantMineEnabled = false;
    private static boolean instantPlaceEnabled = false;
    public static final EditEverything ADVANCED_CREATIVE_TAB = new EditEverything();
    public static final String TEMPLATE_TAG_NAME = "TemplateData";
    public static final Random RANDOM = new Random();
    public static final RandomSource RANDOM_SOURCE = RandomSource.create();
    
    // Client-side only field, but kept here for now to avoid breaking too many refs. 
    // Should be accessed carefully or moved.
    // private static final PoseStack STACK = new PoseStack(); 

    @SuppressWarnings("unchecked")
    public static String[] getDefaultCustomItems() {
        return new String[]{
                "minecraft:pink_wool 42"};
    }
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID.toUpperCase());
    
    // KeyMappings are client-side. We'll expose getters but they will be initialized in ClientModInitializer
    // or we can keep them here if we are careful. Ideally they should be in EEModClient.
    // For now, I'll keep the static references but initialize them in EEModClient.
    // Actually, let's move them to EEModClient and provide accessors if needed, 
    // OR keep them here as public static fields initialized by the client.
    // I'll keep them here for compatibility with existing code structure, but they will be null on server.
    // public static KeyMapping giver, menu, edit; 
    
    private static final List<ItemStack> templates = new ArrayList<>();
    private static boolean templatesInitialized = false;
    private static final Map<String, Map<String, Consumer<StringModifier>>> stringModifier = new HashMap<>();
    private static final Configuration config = new Configuration();
    private static final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private static CommandDispatcher<? extends SharedSuggestionProvider> sharedSuggestionProvider;
    private static final InternalCommandExecutor internalCommandExecutor = new InternalCommandExecutor();
    public static final Component HIDE_COMPONENT = Component.literal("%HIDE_COMPONENT%");

    public static boolean doesDisableToolTip() {
        return config.doesDisableToolTip();
    }

    public static boolean doesDisableEETooltips() {
        return config.doesDisableEETooltips();
    }

    public static boolean doesResetPlayerAttributesOnEventWorldExit() {
        return config.doesResetPlayerAttributesOnEventWorldExit();
    }

    public static Optional<Identifier> getEventWorldId() {
        return config.getEventWorldId();
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
        initTemplates();
        return templates.stream().map(is -> {
            String lang = ItemUtils.getCustomTag(is, TEMPLATE_TAG_NAME + "Lang", null);
            Component display = (lang != null ? Component.translatable(lang) : is.getDisplayName());
            display.copy().withStyle(ChatFormatting.AQUA);
            ItemStack copy = is.copy();
            copy.set(DataComponents.CUSTOM_NAME, display);
            return copy;
        });
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

    private static void initTemplates() {
        if (templatesInitialized) {
            return;
        }
        templatesInitialized = true;

        registerTemplate("gui.ee.menu.template.empty", new ItemStack(Items.PAPER), "");
        registerTemplate("gui.ee.menu.template.stone", new ItemStack(Blocks.STONE),
                ItemUtils.getGiveCode(new ItemStack(Blocks.STONE)));
        registerTemplate("gui.ee.menu.template.potion", new ItemStack(Items.POTION),
                ItemUtils.getGiveCode(new ItemStack(Items.POTION)));
        registerTemplate("gui.ee.menu.template.fireworks", new ItemStack(Items.FIREWORK_ROCKET),
                ItemUtils.getGiveCode(new ItemStack(Items.FIREWORK_ROCKET)));

        Identifier headId = BuiltInRegistries.ITEM.getKey(Items.PLAYER_HEAD);
        String headDesc = "item." + headId.getNamespace() + "." + headId.getPath().replace('/', '.');
        registerTemplate(headDesc, new ItemStack(Items.PLAYER_HEAD),
                ItemUtils.getGiveCode(new ItemStack(Items.PLAYER_HEAD)));

        registerTemplate("gui.ee.menu.template.command", new ItemStack(Blocks.COMMAND_BLOCK),
                ItemUtils.getGiveCode(new ItemStack(Blocks.COMMAND_BLOCK)));

        Identifier eggId = BuiltInRegistries.ITEM.getKey(Items.EGG);
        String eggDesc = "item." + eggId.getNamespace() + "." + eggId.getPath().replace('/', '.');
        registerTemplate(eggDesc, new ItemStack(Items.EGG),
                ItemUtils.getGiveCode(new ItemStack(Items.EGG)));
    }

    public static void saveConfigs() {
        config.save();
    }

    public static void saveItem(String code) {
        LOGGER.info("Adding : {}", code);
        config.getCustomitems().add(0, code);
        saveConfigs();
    }

    public static void setDoesDisableToolTip(boolean doesDisableToolTip) {
        config.setDoesDisableToolTip(doesDisableToolTip);
    }

    public static void setDoesDisableEETooltips(boolean doesDisableEETooltips) {
        config.setDoesDisableEETooltips(doesDisableEETooltips);
    }

    public static EEState getModState() {
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
        EEMod.instantMineEnabled = instantMineEnabled;
    }

    public static boolean isInstantPlaceEnabled() {
        return instantPlaceEnabled;
    }

    public static void setInstantPlaceEnabled(boolean instantPlaceEnabled) {
        EEMod.instantPlaceEnabled = instantPlaceEnabled;
    }

    public static ModdedCommandEE getModCommand() {
        return modCommand;
    }

    public static void registerInternalModule(Class<?> module) {
        internalCommandExecutor.registerModule(module);
    }

    @Override
    public void onInitialize() {
        EENetworking.initCommon();

        // Register Creative Tab
        ADVANCED_CREATIVE_TAB.register();

        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isPresent()) {
            ModMetadata metadata = container.get().getMetadata();
            modName = metadata.getName();
            modVersion = metadata.getVersion().getFriendlyString();
            modCommand = new ModdedCommandEE();
        }

        // Config
        config.sync(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
        config.addCustomItemsCallback(this::syncItemConfig);

        // Server-side safety: if configured, reset player attributes when leaving the event world.
        EventWorldAttributeResetter.init();

        // Register Argument Types
        ArgumentTypeRegistry.registerArgumentType(
                Identifier.fromNamespaceAndPath(MOD_ID, "string_list"),
                StringListArgumentType.class,
                SingletonArgumentInfo.contextFree(() -> new StringListArgumentType(Collections::emptyList, Collections.emptyList(), true)));

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            registerCommandDispatcher(dispatcher, context);
            // Also register to our internal dispatcher if needed, or just use the main one
            registerCommandDispatcher(EEMod.dispatcher, context);
        });

        // Register Modifiers (using BuiltInRegistries)
        BuiltInRegistries.ITEM.entrySet().forEach(e -> {
            var registryName = e.getKey().identifier();
            String desc = "item." + registryName.getNamespace() + "." + registryName.getPath().replace('/', '.');
            registerStringModifier(desc + ".name", "registry.items",
                    sm -> sm.setString(registryName.toString()));
        });

        BuiltInRegistries.BLOCK.entrySet().forEach(e -> {
            var registryName = e.getKey().identifier();
            var b = e.getValue();
            registerStringModifier(b.getName().getString(), "registry.blocks",
                    sm -> sm.setString(registryName.toString()));
        });

        BuiltInRegistries.POTION.entrySet().forEach(e -> {
            var registryName = e.getKey().identifier();
            var p = e.getValue();
            registerStringModifier(registryName.toString(), "registry.potions",
                    sm -> sm.setString(registryName.toString()));
        });

        // BuiltInRegistries.BIOME.keySet().forEach(k -> registerStringModifier(k.toString(), "registry.biomes",
        //         sm -> sm.setString(k.toString())));

        BuiltInRegistries.SOUND_EVENT.keySet().forEach(s -> registerStringModifier(s.toString(), "registry.sounds",
                sm -> sm.setString(s.toString())));

        BuiltInRegistries.VILLAGER_PROFESSION.entrySet().forEach(entry -> {
            Identifier id = entry.getKey().identifier();
            registerStringModifier(id.toString(), "registry.villagerProfessions",
                    sm -> sm.setString(id.toString()));
        });

        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(ee -> {
            Identifier id = ee.getKey().identifier();
            String desc = "entity." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            registerStringModifier(desc, "registry.entities",
                sm -> sm.setString(id.toString()));
        });

        BuiltInRegistries.ATTRIBUTE.entrySet().forEach(ee -> {
            Identifier id = ee.getKey().identifier();
            String descriptionId = "attribute." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            registerStringModifier(descriptionId, "attributes",
                sm -> sm.setString(id.toString()));
        });

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            registerStringModifier("item.modifiers." + slot.getName(), "attributes.slot",
                    sm -> sm.setString(slot.getName()));
        }

        // Internal command modules are client-only (they rely on client classes and reflection).
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            internalCommandExecutor.registerModule(EEUtils.class);
            internalCommandExecutor.registerModule(ChatUtils.class);
            internalCommandExecutor.registerModule(CommandUtils.class);
            internalCommandExecutor.registerModule(FileUtils.class);
            internalCommandExecutor.registerModule(ItemUtils.class);
            internalCommandExecutor.registerModule(ReflectionUtils.class);
        }
    }

    private void syncItemConfig(List<String> itemConfig) {
        // force the regeneration of the tabs
        // CreativeModeTabs.CACHED_ENABLED_FEATURES = null; // Not available/needed in Fabric usually
        // We might need to trigger a refresh of the creative tab
        ADVANCED_CREATIVE_TAB.refresh();
    }

    private void registerCommandDispatcher(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        new ModdedCommandEE().register(dispatcher, context);
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
                            .suggests(SuggestionProviders.cast(requiredargumentbuilder.getSuggestionsProvider()));
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
    
    public static CommandDispatcher<? extends SharedSuggestionProvider> getSharedSuggestionProvider() {
        return sharedSuggestionProvider;
    }

    public static void setSharedSuggestionProvider(CommandDispatcher<? extends SharedSuggestionProvider> dispatcher) {
        sharedSuggestionProvider = dispatcher;
    }
}
