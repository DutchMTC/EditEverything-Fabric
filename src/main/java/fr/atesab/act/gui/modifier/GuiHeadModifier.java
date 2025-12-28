package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GuiHeadModifier extends GuiModifier<ItemStack> {
    private final ItemStack stack;
    private EditBox name;
    private EditBox uuid;
    private EditBox link;
    private Button save, loadName, loadLink;
    private Thread saveThread = null;
    private final AtomicReference<String> errType = new AtomicReference<>(null);
    private final AtomicReference<String> err = new AtomicReference<>(null);

    public GuiHeadModifier(Screen parent, Consumer<ItemStack> setter, ItemStack stack) {
        super(parent, Component.translatable("gui.act.modifier.head"), setter);
        this.stack = stack.copy();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        List<String> err = new ArrayList<>();
        boolean flagLink = !((!link.getValue().isEmpty()
                && link.getValue().matches("http://textures.minecraft.net/texture/[a-zA-Z\\d]+"))
                || link.getValue().isEmpty());
        boolean flagUuid = !((!uuid.getValue().isEmpty()
                && uuid.getValue().matches("[\\dA-Fa-f]+-[\\dA-Fa-f]+-[\\dA-Fa-f]+-[\\dA-Fa-f]+-[\\dA-Fa-f]+"))
                || uuid.getValue().isEmpty());
        boolean flagName = !((!name.getValue().isEmpty() && name.getValue().matches("(\\w|(-))+"))
                || name.getValue().isEmpty());
        if (flagLink) {
            err.add(I18n.get("gui.act.modifier.head.link.warning"));
        }
        if (flagUuid) {
            err.add(I18n.get("gui.act.modifier.head.uuid.warning"));
        }
        if (flagName) {
            err.add(I18n.get("gui.act.modifier.head.name.warning"));
        }
        if (this.err.get() != null) {
            err.add(this.err.get());
        }
        if (this.errType.get() != null) {
            err.add(this.errType.get() + ": ");
        }
        for (int i = 0; i < err.size(); i++)
            GuiUtils.drawCenterString(graphics, font, err.get(i), width / 2, name.getY() - 2 - (font.lineHeight + 1) * (i + 1),
                    Color.RED.getRGB());
        graphics.drawString(font, I18n.get("gui.act.config.name") + " : ", width / 2 - 178,
                name.getY() + 10 - font.lineHeight / 2, (flagName ? Color.RED : Color.WHITE).getRGB());
        graphics.drawString(font, I18n.get("gui.act.uuid") + " : ", width / 2 - 178, uuid.getY() + 10 - font.lineHeight / 2,
                (flagUuid ? Color.RED : Color.WHITE).getRGB());
        graphics.drawString(font, I18n.get("gui.act.link") + " : ", width / 2 - 178, link.getY() + 10 - font.lineHeight / 2,
                (flagLink ? Color.RED : Color.WHITE).getRGB());
        // name.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        // uuid.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        // link.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        GuiUtils.drawItemStack(graphics, stack, uuid.getX() + uuid.getWidth() + 10,
                uuid.getY() + uuid.getHeight() / 2 - 8);
        if (GuiUtils.isHover(uuid.getX() + uuid.getWidth() + 10, uuid.getY() + uuid.getHeight() / 2 - 16 / 2, 16, 16, mouseX,
                mouseY))
            GuiUtils.renderTooltip(graphics, font, stack, mouseX, mouseY);
    }

    @Override
    public void init() {

        int l = Math.max(font.width(I18n.get("gui.act.config.name") + " : "),
                Math.max(font.width(I18n.get("gui.act.uuid") + " : "), font.width(I18n.get("gui.act.link") + " : ")))
                + 3;
        name = new EditBox(font, width / 2 - 178 + l, height / 2 - 61, 356 - l, 16, Component.literal(""));
        uuid = new EditBox(font, width / 2 - 178 + l, height / 2 - 40, 356 - l, 16, Component.literal(""));
        link = new EditBox(font, width / 2 - 178 + l, height / 2 - 19, 356 - l, 16, Component.literal(""));
        name.setMaxLength(16);
        link.setMaxLength(Integer.MAX_VALUE);
        uuid.setMaxLength(Integer.MAX_VALUE);
        addRenderableWidget(name);
        addRenderableWidget(link);
        addRenderableWidget(uuid);
        addRenderableWidget(new ACTButton(width / 2 - 180, height / 2, 180, 20,
                Component.translatable("gui.act.modifier.head.me"), b -> name.setValue(getMinecraft().getUser().getName())));
        addRenderableWidget(save = new ACTButton(width / 2 + 1, height / 2, 179, 20,
                Component.translatable("gui.act.modifier.head.saveSkin"), b -> {
            if (!(saveThread != null && saveThread.isAlive()))
                (saveThread = new Thread(() -> {
                    try {
                        err.set(ChatFormatting.GOLD + I18n.get("gui.act.modifier.head.loading") + "...");
                        URL url = new URL(link.getValue());
                        byte[] buffer;
                        try (InputStream stream = url.openStream()) {
                            buffer = IOUtils.readFully(stream, stream.available());
                        }
                        File f = new File(mc.gameDirectory, "skin_" + name.getValue() + ".png");
                        try (OutputStream writer = new FileOutputStream(f)) {
                            writer.write(buffer);
                        }
                        errType.set(ChatFormatting.GREEN + I18n.get("gui.act.modifier.head.fileSaved"));
                        String s = mc.gameDirectory.getCanonicalFile().getName() + File.separator + f.getName();
                        if (s.length() > 200) {
                            s = s.substring(0, 50) + "...";
                        }
                        err.set(ChatFormatting.GREEN + s);
                    } catch (Exception e) {
                        errType.set(e instanceof FileNotFoundException
                                ? I18n.get("gui.act.modifier.head.fileNotFound")
                                : e.getClass().getSimpleName());
                        String s = e.getMessage();
                        if (s.length() > 50) {
                            s = s.substring(0, 50) + "...";
                        }
                        err.set(s);
                    }
                })).start();
        }));
        addRenderableWidget(loadName = new ACTButton(width / 2 - 180, height / 2 + 21, 180, 20,
                Component.translatable("gui.act.modifier.head.load.name"), b -> {
            try {
                err.set(ChatFormatting.GOLD + I18n.get("gui.act.modifier.head.loading") + "...");
                ItemUtils.getHead(stack, name.getValue());
                loadHead();
            } catch (Exception e) {
                errType.set(e.getClass().getSimpleName());
                String s = e.getMessage();
                if (s.length() > 50) {
                    s = s.substring(0, 50) + "...";
                }
                err.set(s);
            }
        }));
        addRenderableWidget(loadLink = new ACTButton(width / 2 + 1, height / 2 + 21, 179, 20,
                Component.translatable("gui.act.modifier.head.load.link"), b -> {
            err.set(ChatFormatting.GOLD + I18n.get("gui.act.modifier.head.loading") + "...");
            ItemUtils.getHead(stack, uuid.getValue(), link.getValue(),
                    name.getValue().isEmpty() ? null : name.getValue());
            loadHead();
        }));
        if (setter != null)
            addRenderableWidget(new ACTButton(width / 2 - 180, height / 2 + 42, 180, 20,
                    Component.translatable("gui.act.cancel"), b -> getMinecraft().setScreen(parent)));
        addRenderableWidget(
                new ACTButton(width / 2 + 1, height / 2 + 42, 179, 20, Component.translatable("gui.done"), b -> {
                    set(stack);
                    getMinecraft().setScreen(parent);
                }));
        loadHead();
        super.init();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    private void loadHead() {
        ResolvableProfile profile = ItemUtils.getComponent(stack, DataComponents.PROFILE);
        if (profile != null) {
            GameProfile gameProfile = profile.partialProfile();
            if (gameProfile.name() != null) {
                name.setValue(gameProfile.name());
            }
            if (gameProfile.id() != null) {
                uuid.setValue(gameProfile.id().toString());
            }
            for (Property property : gameProfile.properties().get("textures")) {
                String value = property.value();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                try {
                    String s = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                    CompoundTag texCompound = TagParser.parseCompoundFully(s);
                    texCompound.getString("profileName").ifPresent(name::setValue);
                    if (texCompound.getCompound("textures").isPresent()) {
                        CompoundTag textures = texCompound.getCompound("textures").orElseThrow();
                        if (textures.getCompound("SKIN").isPresent()) {
                            CompoundTag skin = textures.getCompound("SKIN").orElseThrow();
                            skin.getString("url").ifPresent(link::setValue);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        } else {
            // Legacy fallback (pre-data-components heads)
            CompoundTag root = ItemUtils.getTag(stack);
            if (root.contains("SkullOwner") && root.getCompound("SkullOwner").isPresent()) {
                CompoundTag tag = root.getCompound("SkullOwner").orElseThrow();
                tag.getString("Name").ifPresent(name::setValue);
                tag.getString("Id").ifPresent(uuid::setValue);
                if (tag.contains("Properties") && tag.getCompound("Properties").isPresent()) {
                    CompoundTag properties = tag.getCompound("Properties").orElseThrow();
                    if (properties.contains("textures") && properties.getList("textures").isPresent()) {
                        ListTag list = properties.getList("textures").orElseThrow();
                        for (int i = 0; i < list.size(); i++) {
                            CompoundTag text = list.getCompoundOrEmpty(i);
                            String value = text.getString("Value").orElse("");
                            if (!value.isEmpty()) {
                                try {
                                    String s = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                                    CompoundTag texCompound = TagParser.parseCompoundFully(s);
                                    texCompound.getString("profileName").ifPresent(name::setValue);
                                    if (texCompound.getCompound("textures").isPresent()) {
                                        CompoundTag textures = texCompound.getCompound("textures").orElseThrow();
                                        if (textures.getCompound("SKIN").isPresent()) {
                                            CompoundTag skin = textures.getCompound("SKIN").orElseThrow();
                                            skin.getString("url").ifPresent(link::setValue);
                                        }
                                    }
                                } catch (Exception ignore) {
                                }
                            }
                        }
                    }
                }
            }
        }
        err.set(null);
        errType.set(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        if (GuiUtils.isHover(link, (int) mouseX, (int) mouseY) && mouseButton == 1)
            link.setValue("");
        if (GuiUtils.isHover(uuid, (int) mouseX, (int) mouseY) && mouseButton == 1)
            uuid.setValue("");
        if (GuiUtils.isHover(name, (int) mouseX, (int) mouseY) && mouseButton == 1)
            name.setValue("");
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        // name.tick();
        // uuid.tick();
        // name.tick();
        loadName.active = !name.getValue().isEmpty();
        loadLink.active = !uuid.getValue().isEmpty() && !link.getValue().isEmpty();
        save.active = !link.getValue().isEmpty();
        super.tick();
    }

}
