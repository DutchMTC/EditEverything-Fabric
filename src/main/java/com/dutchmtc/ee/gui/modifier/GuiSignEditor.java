package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.gui.components.EETheme;

import com.dutchmtc.ee.gui.GuiConfirmation;
import com.dutchmtc.ee.gui.components.EEButton;
import com.dutchmtc.ee.gui.components.StyledTextField;
import com.dutchmtc.ee.mixin.BlockEntityRenderDispatcherAccessor;
import com.dutchmtc.ee.mixin.EditBoxAccessor;
import com.dutchmtc.ee.utils.GuiUtils;
import com.dutchmtc.ee.utils.SignItemData;
import com.dutchmtc.ee.utils.SignClickActions;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.block.entity.SignTextSlot;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;
import java.util.Optional;

/** Edits both sign faces while previewing the actual placed block and vanilla sign text. */
public final class GuiSignEditor extends Screen {
    private static final long FLIP_NANOS = 390_000_000L;
    private static final int TEXT = 0xFF303030, MUTED = 0xFF626262;
    private static final int PALETTE_COLUMNS = 4, SWATCH_SIZE = 24;
    private static final int PALETTE_WIDTH = 112, PALETTE_HEIGHT = 119;
    private final Screen parent;
    private final Consumer<ItemStack> setter;
    private final ItemStack original;
    private final SignText[] sides = new SignText[2];
    private final StyledTextDocument[][] lines = new StyledTextDocument[2][4];
    private final boolean hanging;
    private final EditBox[] lineBoxes = new EditBox[4];
    private final TextSelectionToolbar[] toolbars = new TextSelectionToolbar[4];
    private EEButton saveButton;
    private float textScale;
    private final int maxLineWidth;
    private boolean waxed, flipping, colorPaletteOpen;
    private int side, left, top, panelWidth, panelHeight, previewX, previewY, previewW, previewH;
    private int glowButtonX, glowButtonY;
    private long flipStart;

    public static boolean supports(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof SignBlock;
    }

    public GuiSignEditor(Screen parent, ItemStack stack, Consumer<ItemStack> setter) {
        super(Component.literal("Sign editor"));
        this.parent = parent;
        this.setter = setter;
        original = stack.copy();
        sides[0] = SignItemData.read(stack, SignTextSlot.FRONT, registries());
        sides[1] = SignItemData.read(stack, SignTextSlot.BACK, registries());
        for (int face = 0; face < 2; face++)
            for (int line = 0; line < 4; line++)
                lines[face][line] = new StyledTextDocument(SignClickActions.textOnly(sides[face].getMessages(false).get(line)));
        waxed = SignItemData.waxed(stack);
        hanging = ((BlockItem) stack.getItem()).getBlock() instanceof HangingSignBlock;
        maxLineWidth = hanging ? 60 : 90;
    }

    @Override protected void init() {
        colorPaletteOpen = false;
        panelWidth = Math.min(490, width - 16);
        panelHeight = Math.min(325, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        previewX = left + 12;
        previewY = top + 43;
        previewW = panelWidth - 24;
        previewH = panelHeight - 119;

        int controlsY = top + panelHeight - 73;
        int controlW = (previewW - 8) / 3;
        addRenderableWidget(new EEButton(previewX, controlsY, controlW, 20,
                Component.literal("Flip sign"), b -> startFlip()));
        int signSize = previewScale();
        textScale = signSize / 96f;
        int boardTop = previewY + previewH / 2 - Math.round(signSize * 0.58f);
        if (hanging) boardTop += Math.round(signSize * 0.47f);
        int lineStep = Math.max(12, Math.round(signSize * 0.105f));
        int inputW = maxLineWidth + 4;
        for (int i = 0; i < 4; i++) {
            final int line = i;
            StyledTextField input = new StyledTextField(font, previewX + (previewW - inputW) / 2,
                    boardTop + 6 + i * lineStep, inputW, 11, Component.literal("Line " + (i + 1)),
                    lines[side][line], 384);
            input.setPixelLimit(maxLineWidth);
            input.setBordered(false);
            input.setCentered(false);
            input.setTextShadow(false);
            input.setTextColor(signInk(sides[side]));
            input.setStyledResponder(value -> syncLine(side, line));
            lineBoxes[i] = addWidget(input);
            toolbars[i] = new TextSelectionToolbar(this, input, lines[side][line], font,
                    left + 8, left + panelWidth - 8, () -> syncLine(side, line), textScale,
                    () -> signInk(sides[side]), maxLineWidth, true);
        }

        glowButtonX = previewX + controlW + 4;
        glowButtonY = controlsY;
        EEButton glow = new EEButton(glowButtonX, controlsY, controlW, 20,
                Component.empty(), b -> {
                    if (!flipping) sides[side] = sides[side].withGlowingText(!sides[side].hasGlowingText());
                }) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean on = sides[displaySide()].hasGlowingText();
                bevel(g, getX(), getY(), getWidth(), getHeight(),
                        EETheme.controlColor(on, isHoveredOrFocused(), active), false);
                drawDyeSwatch(g, getX() + 5, getY() + 3, 15, sides[displaySide()].getColor());
                box(g, getX() + 26, getY() + 2, 1, 16, on ? 0xFFB5D9AB : 0xFF777777);
                String label = on ? "Glow On" : "Glow Off";
                g.text(font, label, getX() + 28 + (getWidth() - 28 - font.width(label)) / 2,
                        getY() + 6, on ? 0xFFFFFFFF : TEXT, false);
            }
        };
        glow.setTooltip(Tooltip.create(Component.literal("Click the color swatch to choose a dye; click Glow to toggle it.")));
        addRenderableWidget(glow);
        EEButton wax = new EEButton(previewX + 2 * (controlW + 4), controlsY, controlW, 20,
                Component.empty(), b -> waxed = !waxed) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                stateButton(g, this, "Waxed", waxed);
            }
        };
        wax.setTooltip(Tooltip.create(Component.literal("Prevent edits after the sign is placed.")));
        addRenderableWidget(wax);

        addRenderableWidget(new EEButton(previewX, controlsY + 24, previewW, 20,
                actionsLabel(side), b -> {
            if (!flipping) minecraft.gui.setScreen(new GuiSignActionsEditor(this, sides[side], side == 1,
                    registries(), updated -> sides[side] = updated));
        }) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                setMessage(actionsLabel(displaySide()));
                super.extractContents(g, mx, my, delta);
            }
        });

        int footerY = top + panelHeight - 25, half = (panelWidth - 28) / 2;
        addRenderableWidget(new EEButton(left + 12, footerY, half, 20,
                Component.literal("Cancel"), b -> cancel()));
        saveButton = addRenderableWidget(new EEButton(left + 16 + half, footerY, half, 20,
                Component.literal("Save sign"), b -> {
            if (anyLineTooWide()) return;
            setter.accept(result()); minecraft.gui.setScreen(parent);
        }));
        updateSaveValidity();
    }

    @Override protected void setInitialFocus() {}

    private Component actionsLabel(int face) {
        return Component.literal("Click actions · " + SignClickActions.count(sides[face]) + " / 4");
    }

    private int previewScale() {
        return Math.round(Math.min(previewW * 0.68f, previewH * 0.76f));
    }

    private void syncLine(int face, int line) {
        sides[face] = sides[face].asMutable().setLine(line,
                SignClickActions.withText(sides[face], line, lines[face][line].component())).asImmutable();
        updateSaveValidity();
    }

    private void updateSaveValidity() {
        if (saveButton == null) return;
        saveButton.active = !anyLineTooWide();
        saveButton.setTooltip(saveButton.active ? null : Tooltip.create(Component.literal(
                "The resulting text exceeds a sign line's " + maxLineWidth + " px limit. Shorten it or remove bold.")));
    }

    private static int signInk(SignText text) {
        int rgb = text.hasGlowingText() ? text.getColor().getTextColor()
                : AbstractSignRenderer.getDarkColor(text);
        return 0xFF000000 | rgb;
    }

    private static int outlineInk(SignText text) {
        return 0xFF000000 | AbstractSignRenderer.getDarkColor(text);
    }

    private static Component withoutTextColors(Component value) {
        MutableComponent result = Component.empty();
        value.visit((style, segment) -> {
            result.append(Component.literal(segment).withStyle(style.withColor((TextColor) null)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private void drawSignText(GuiGraphicsExtractor g, Component value, int x, int y, SignText signText) {
        if (signText.hasGlowingText()) {
            Component outline = withoutTextColors(value);
            int color = outlineInk(signText);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0) g.text(font, outline, x + dx, y + dy, color, false);
        }
        g.text(font, value, x, y, signInk(signText), false);
    }

    private int paletteX() { return glowButtonX; }
    private int paletteY() { return glowButtonY - PALETTE_HEIGHT - 4; }

    private void drawDyeSwatch(GuiGraphicsExtractor g, int x, int y, int size, DyeColor dye) {
        int bright = 0xFF000000 | dye.getTextColor();
        int dark = outlineInk(sides[side].withColor(dye).withGlowingText(true));
        box(g, x, y, size, size, 0xFF202020);
        box(g, x + 1, y + 1, size - 2, size - 2, dark);
        box(g, x + 4, y + 4, size - 8, size - 8, bright);
    }

    private void renderColorPalette(GuiGraphicsExtractor g, int mx, int my) {
        int x = paletteX(), y = paletteY();
        bevel(g, x, y, PALETTE_WIDTH, PALETTE_HEIGHT, 0xFFC6C6C6, false);
        g.text(font, "Sign text color", x + 6, y + 6, TEXT, false);
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            int sx = x + 8 + (i % PALETTE_COLUMNS) * SWATCH_SIZE;
            int sy = y + 19 + (i / PALETTE_COLUMNS) * SWATCH_SIZE;
            if (dyes[i] == sides[side].getColor()) box(g, sx - 2, sy - 2, 22, 22, 0xFFFFFFFF);
            drawDyeSwatch(g, sx, sy, 18, dyes[i]);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18)
                g.setTooltipForNextFrame(font, Component.literal(dyes[i].getName().replace('_', ' ')), mx, my);
        }
    }

    private int activeLine() {
        for (int i = 0; i < lineBoxes.length; i++)
            if (lineBoxes[i] != null && lineBoxes[i].isFocused()) return i;
        return -1;
    }

    private boolean insideVisual(EditBox input, double x, double y) {
        int center = input.getX() + input.getWidth() / 2;
        return x >= center - input.getWidth() * textScale / 2f
                && x < center + input.getWidth() * textScale / 2f
                && y >= input.getY() && y < input.getY() + input.getHeight() * textScale;
    }

    private int indexAt(int line, double screenX) {
        StyledTextDocument document = lines[side][line];
        EditBox input = lineBoxes[line];
        int center = input.getX() + input.getWidth() / 2;
        double x = center + (screenX - center) / textScale;
        if (document.legacyMode()) {
            ((StyledTextField) input).keepCursorVisible();
            int first = Math.min(((EditBoxAccessor) input).ee$getDisplayPos(), input.getValue().length());
            int previous = 0;
            double relative = x - input.getX();
            for (int index = first; index < input.getValue().length();) {
                int next = index + Character.charCount(input.getValue().codePointAt(index));
                int width = font.width(net.minecraft.util.FormattedCharSequence.forward(
                        input.getValue().substring(first, next), Style.EMPTY));
                if (relative < (previous + width) / 2.0) return index;
                index = next;
                previous = width;
            }
            return input.getValue().length();
        }
        int left = center - font.width(document.editingComponent()) / 2;
        int previousWidth = 0;
        String value = document.text();
        for (int index = 0; index < value.length();) {
            int next = index + Character.charCount(value.codePointAt(index));
            int nextWidth = font.width(document.editingComponent(0, next));
            if (x < left + (previousWidth + nextWidth) / 2.0) return index;
            index = next;
            previousWidth = nextWidth;
        }
        return value.length();
    }

    private void startFlip() {
        if (!flipping) {
            colorPaletteOpen = false;
            flipping = true;
            flipStart = System.nanoTime();
            for (EditBox input : lineBoxes) input.setVisible(false);
        }
    }
    private float flipProgress() {
        return flipping ? Math.min(1f, (System.nanoTime() - flipStart) / (float) FLIP_NANOS) : 0f;
    }
    private int displaySide() { return flipping && flipProgress() >= 0.5f ? 1 - side : side; }
    @Override public void tick() {
        super.tick();
        if (flipping && flipProgress() >= 1f) {
            side = 1 - side;
            flipping = false;
            rebuildWidgets();
        }
        for (TextSelectionToolbar toolbar : toolbars) if (toolbar != null) toolbar.update();
        for (EditBox input : lineBoxes) if (input != null) input.setTextColor(signInk(sides[side]));
        updateSaveValidity();
    }

    private boolean anyLineTooWide() {
        for (StyledTextDocument[] face : lines)
            for (StyledTextDocument line : face)
                if (line.text().length() > 384 || font.width(line.editingComponent()) > maxLineWidth) return true;
        return false;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (colorPaletteOpen) {
            int mx = (int) event.x(), my = (int) event.y();
            int x = paletteX(), y = paletteY();
            if (mx >= x && mx < x + PALETTE_WIDTH && my >= y && my < y + PALETTE_HEIGHT) {
                int col = (mx - x - 8) / SWATCH_SIZE, row = (my - y - 19) / SWATCH_SIZE;
                if (mx >= x + 8 && my >= y + 19 && col >= 0 && col < PALETTE_COLUMNS
                        && row >= 0 && row < 4) {
                    int sx = x + 8 + col * SWATCH_SIZE, sy = y + 19 + row * SWATCH_SIZE;
                    int index = row * PALETTE_COLUMNS + col;
                    if (mx < sx + 18 && my < sy + 18 && index < DyeColor.values().length) {
                        sides[side] = sides[side].withColor(DyeColor.values()[index]);
                        colorPaletteOpen = false;
                    }
                }
                return true;
            }
            if (!(mx >= glowButtonX && mx < glowButtonX + 27
                    && my >= glowButtonY && my < glowButtonY + 20)) colorPaletteOpen = false;
        }
        if (!flipping && event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= glowButtonX && event.x() < glowButtonX + 27
                && event.y() >= glowButtonY && event.y() < glowButtonY + 20) {
            colorPaletteOpen = !colorPaletteOpen;
            return true;
        }
        for (TextSelectionToolbar toolbar : toolbars)
            if (toolbar != null && toolbar.click(event)) return true;
        if (!flipping) for (int line = 0; line < lineBoxes.length; line++) {
            EditBox input = lineBoxes[line];
            if (input != null && insideVisual(input, event.x(), event.y())) {
                setFocused(input);
                setDragging(true);
                int position = indexAt(line, event.x());
                input.moveCursorTo(position, event.hasShiftDown());
                if (doubleClick) {
                    int wordStart = input.getWordPosition(-1);
                    int wordEnd = input.getWordPosition(1);
                    input.moveCursorTo(wordStart, false);
                    input.moveCursorTo(wordEnd, true);
                }
                for (TextSelectionToolbar toolbar : toolbars) if (toolbar != null) toolbar.update();
                return true;
            }
        }
        boolean result = super.mouseClicked(event, doubleClick);
        if (!result) setFocused(null);
        for (TextSelectionToolbar toolbar : toolbars) if (toolbar != null) toolbar.update();
        return result;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int active = activeLine();
        if (active >= 0 && isDragging()) {
            lineBoxes[active].moveCursorTo(indexAt(active, event.x()), true);
            toolbars[active].update();
            return true;
        }
        boolean result = super.mouseDragged(event, dx, dy);
        for (TextSelectionToolbar toolbar : toolbars) if (toolbar != null) toolbar.update();
        return result;
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        GuiUtils.drawGradientRect(g, 0, 0, width, height, 0xA0101010, 0xC0101010);
        bevel(g, left, top, panelWidth, panelHeight, 0xFFC6C6C6, false);
        g.item(original, left + 10, top + 9);
        g.text(font, "Sign editor", left + 32, top + 13, TEXT, false);
        g.text(font, displaySide() == 0 ? "Front side" : "Back side", previewX, top + 30, MUTED, false);
        int activeLine = activeLine();
        String limit = activeLine < 0 ? "Click a line to type  ·  " + maxLineWidth + " px max"
                : "Line " + (activeLine + 1) + "  " + font.width(lines[side][activeLine].editingComponent()) + " / " + maxLineWidth + " px";
        int limitColor = activeLine >= 0 && font.width(lines[side][activeLine].editingComponent()) > maxLineWidth
                ? EETheme.ERROR_LABEL : MUTED;
        g.text(font, limit, left + panelWidth - 12 - font.width(limit), top + 30, limitColor, false);
        box(g, previewX, previewY, previewW, previewH, 0xFF343B39);
        box(g, previewX + 1, previewY + 1, previewW - 2, previewH - 2, 0xFF4A5250);
        addSignPreview(g);
        if (anyLineTooWide())
            g.text(font, "Shorten text to save", previewX + 8, previewY + previewH - 16, EETheme.ERROR, false);
        super.extractRenderState(g, mx, my, delta);
        if (!flipping) for (int i = 0; i < 4; i++) {
            if (i == activeLine) continue;
            Component message = lines[side][i].component();
            if (message.getString().isEmpty()) continue;
            EditBox input = lineBoxes[i];
            int center = input.getX() + input.getWidth() / 2;
            g.pose().pushMatrix();
            g.pose().translate(center, input.getY());
            g.pose().scale(textScale, textScale);
            g.pose().translate(-center, -input.getY());
            drawSignText(g, message, center - font.width(message) / 2, input.getY(), sides[side]);
            g.pose().popMatrix();
        }
        if (!flipping && activeLine >= 0) {
            EditBox input = lineBoxes[activeLine];
            StyledTextDocument document = lines[side][activeLine];
            int center = input.getX() + input.getWidth() / 2;
            int textX = center - font.width(document.editingComponent()) / 2;
            int cursor = input.getCursorPosition();
            int anchor = ((EditBoxAccessor) input).ee$getHighlightPos();
            g.pose().pushMatrix();
            g.pose().translate(center, input.getY());
            g.pose().scale(textScale, textScale);
            g.pose().translate(-center, -input.getY());
            if (document.legacyMode()) {
                box(g, input.getX() - 2, input.getY() - 2, input.getWidth() + 4, 15, 0xFF343B39);
                input.setTextColor(0xFFE2E6DC);
                input.extractWidgetRenderState(g, mx, my, delta);
            } else {
                drawSignText(g, document.editingComponent(), textX, input.getY(), sides[side]);
                if (cursor != anchor) {
                    int from = Math.min(cursor, anchor), to = Math.max(cursor, anchor);
                    int selectedLeft = textX + font.width(document.editingComponent(0, from));
                    int selectedRight = textX + font.width(document.editingComponent(0, to));
                    g.textHighlight(selectedLeft, input.getY() - 1, selectedRight,
                            input.getY() + font.lineHeight, false);
                } else if ((System.currentTimeMillis() / 500L) % 2 == 0) {
                    int cursorX = textX + font.width(document.editingComponent(0, cursor));
                    GuiUtils.drawRect(g, cursorX, input.getY() - 1, cursorX + 1,
                            input.getY() + font.lineHeight, signInk(sides[side]));
                }
            }
            g.pose().popMatrix();
        }
        for (TextSelectionToolbar toolbar : toolbars) if (toolbar != null) toolbar.render(g, mx, my);
        if (colorPaletteOpen && !flipping) renderColorPalette(g, mx, my);
    }

    private void addSignPreview(GuiGraphicsExtractor g) {
        if (minecraft.level == null) return;
        SignBlock block = (SignBlock) ((BlockItem) original.getItem()).getBlock();
        BlockState state = block.defaultBlockState();
        BlockModelRenderState model = new BlockModelRenderState();
        ((BlockEntityRenderDispatcherAccessor) minecraft.getBlockEntityRenderDispatcher()).ee$getBlockModelResolver()
                .update(model, state, BlockDisplayContext.create());
        float rotation = (float) (Math.PI * (side + (flipping ? flipProgress() : 0f)));
        int x0 = previewX + 2, y0 = previewY + 2;
        int x1 = previewX + previewW - 2, y1 = previewY + previewH - 2;
        float scale = previewScale();
        g.guiRenderState.addPicturesInPictureState(new SignBlockPreviewState(model, rotation,
                x0, y0, x1, y1, scale, g.scissorStack.peek()));
    }

    private ItemStack result() {
        ItemStack result = original.copy();
        SignItemData.write(result, sides[0], sides[1], waxed, registries());
        return result;
    }
    private HolderLookup.Provider registries() {
        return minecraft.level == null ? VanillaRegistries.createWorldLookup() : minecraft.level.registryAccess();
    }
    private void cancel() {
        if (!sides[0].equals(SignItemData.read(original, SignTextSlot.FRONT, registries()))
                || !sides[1].equals(SignItemData.read(original, SignTextSlot.BACK, registries()))
                || waxed != SignItemData.waxed(original))
            minecraft.gui.setScreen(new GuiConfirmation(this,
                    Component.translatable("gui.ee.discard_changes_question"),
                    () -> minecraft.gui.setScreen(parent), () -> minecraft.gui.setScreen(this)));
        else minecraft.gui.setScreen(parent);
    }
    @Override public void onClose() { cancel(); }

    private void stateButton(GuiGraphicsExtractor g, EEButton button, String label, boolean on) {
        bevel(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                EETheme.controlColor(on, button.isHoveredOrFocused(), button.active), false);
        String value = label + (on ? " On" : " Off");
        g.text(font, value, button.getX() + (button.getWidth() - font.width(value)) / 2,
                button.getY() + 6, on ? 0xFFFFFFFF : TEXT, false);
    }
    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        GuiUtils.drawRect(g, x, y, x + w, y + h, color);
    }
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, boolean inset) {
        EETheme.bevel(g, x, y, w, h, fill, inset);
    }
}
