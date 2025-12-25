package fr.atesab.act.gui.modifier;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atesab.act.gui.GuiValueButton;
import fr.atesab.act.gui.components.ACTButton;
import fr.atesab.act.utils.GuiUtils;
import fr.atesab.act.utils.Tuple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class GuiListModifier<T> extends GuiModifier<T> {
    /**
     * a button to add an element to the list
     */
    public static class AddElementButton extends RunElementButton {
        public AddElementButton(GuiListModifier<?> parent, int x, int y, int widthIn, int heightIn, ListElement element,
                                Supplier<ListElement> builder) {
            this(parent, x, y, widthIn, heightIn, Component.literal("+"), element, builder);
        }

        public AddElementButton(GuiListModifier<?> parent, int x, int y, int widthIn, int heightIn, Component text,
                                ListElement element, Function<Integer, ListElement> builder) {
            super(x, y, widthIn, heightIn, text, () -> {
                for (int i = 0; i < parent.elements.size(); i++)
                    if (parent.elements.get(i).equals(element)) {
                        ListElement elm = builder.apply(i);
                        if (elm != null)
                            parent.addListElement(i, elm);
                        break;
                    }
            }, null);
            // setFGColor(Objects.requireNonNull(ChatFormatting.GREEN.getColor())); // TODO: Fix color
            setMessage(text.copy().withStyle(ChatFormatting.GREEN));
        }

        public AddElementButton(GuiListModifier<?> parent, int x, int y, int widthIn, int heightIn, Component text,
                                ListElement element, Supplier<ListElement> builder) {
            this(parent, x, y, widthIn, heightIn, text, element, i -> builder.get());
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", I18n.get("gui.act.new"));
        }

    }

    /**
     * a simple {@link ListElement} with only an {@link AddElementButton} in it
     */
    public static class AddElementList extends ListElement {

        public AddElementList(GuiListModifier<?> parent, Supplier<ListElement> builder) {
            this(parent, builder,
                    Math.min(200, parent.elements.stream().mapToInt(ListElement::getSizeX).max().orElse(100)), 21);
        }

        public AddElementList(GuiListModifier<?> parent, Supplier<ListElement> builder, int sizeX, int sizeY) {
            super(sizeX, Math.max(21, sizeY));
            buttonList.add(new AddElementButton(parent, 0, (getSizeY() - 20) / 2, getSizeX(), 20, this, builder));
        }
    }

    /**
     * a simple {@link ListElement} with only an {@link Button} in it
     */
    public static class ButtonElementList extends ListElement {
        private final Runnable right;

        public ButtonElementList(int sizeX, int sizeY, int buttonSizeX, int buttonSizeY, Component buttonText,
                                 Runnable leftAction, Runnable rightAction) {
            super(sizeX, sizeY);
            buttonList.add(new ACTButton(0, 0, buttonSizeX, buttonSizeY, buttonText, b -> {
                if (leftAction != null)
                    leftAction.run();
            }));
            this.right = rightAction;
        }

        @Override
        protected void otherActionPerformed(AbstractWidget button, int mouseButton) {
            if (mouseButton == 1 && right != null)
                right.run();
            super.otherActionPerformed(button, mouseButton);
        }

    }

    /**
     * a element of the list
     */
    public static abstract class ListElement {
        protected Font font;
        protected Minecraft mc;
        protected List<AbstractWidget> buttonList = new ArrayList<>();
        protected List<EditBox> fieldList = new ArrayList<>();
        private int sizeX;
        private int sizeY;

        public ListElement(int sizeX, int sizeY) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            mc = Minecraft.getInstance();
            font = mc.font;
        }

        public boolean charTyped(char key, int modifiers) {
            for (EditBox field : fieldList)
                if (field.isVisible() && field.charTyped(key, modifiers))
                    return true;
            return false;
        }

        public void draw(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
            buttonList.forEach(
                    b -> GuiUtils.drawRelative(graphics, b, offsetX, offsetY, mouseX, mouseY, partialTicks));
            fieldList.stream().filter(EditBox::isVisible).forEach(
                    tf -> GuiUtils.drawRelative(graphics, tf, offsetX, offsetY, mouseX, mouseY, partialTicks));
        }


        public int getSizeX() {
            return sizeX;
        }

        public int getSizeY() {
            return sizeY;
        }

        public void init() {
            fieldList.forEach(tf -> tf.setFocused(false));
        }

        /**
         * Check if this element is focused for key typing
         *
         * @return true if an element in this element is focused
         */
        public boolean isFocused() {
            for (EditBox field : fieldList)
                if (field.isFocused())
                    return true;
            return false;
        }

        public boolean keyPressed(int key, int scanCode, int modifiers) {
            for (EditBox field : fieldList)
                if (field.isVisible() && field.keyPressed(key, scanCode, modifiers))
                    return true;
            return false;
        }

        /**
         * Check if this element match with the text in the search bar
         *
         * @param search Search bar text
         * @return true if this element match
         */
        public boolean match(String search) {
            return true;
        }

        public void mouseClicked(double mouseX, double mouseY, int mouseButton) {
            buttonList.forEach(b -> {
                if (GuiUtils.isHover(b, (int) mouseX, (int) mouseY)) {
                    if (b instanceof RunElementButton) {
                        if (((RunElementButton) b).getLeft() != null && mouseButton == 0) {
                            playClick();
                            ((RunElementButton) b).getLeft().run();
                        } else if (((RunElementButton) b).getLeft() != null && mouseButton == 1) {
                            playClick();
                            ((RunElementButton) b).getRightButton().run();
                        }
                    } else if (mouseButton == 0) {
                        playClick();
                        b.onClick(mouseX, mouseY);
                    } else
                        otherActionPerformed(b, mouseButton);
                }
            });
            fieldList.stream().filter(EditBox::isVisible).forEach(tf -> {
                if (mouseButton == 1 && GuiUtils.isHover(tf, (int) mouseX, (int) mouseY)) {
                    tf.setValue("");
                    tf.setFocused(true);
                } else {
                    if (GuiUtils.isHover(tf, (int) mouseX, (int) mouseY)) {
                        tf.setFocused(true);
                    }
                    tf.mouseClicked(mouseX, mouseY, mouseButton);
                }
            });
        }

        protected void otherActionPerformed(AbstractWidget button, int mouseButton) {
        }

        public void drawNext(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                             float partialTicks) {
        }

        public void setSizeX(int sizeX) {
            this.sizeX = sizeX;
        }

        public void setSizeY(int sizeY) {
            this.sizeY = sizeY;
        }

        public void update() {
            // fieldList.stream().filter(EditBox::isVisible).forEach(EditBox::tick); // tick removed
        }
    }

    /**
     * a button to remove a {@link ListElement}
     */
    public static class RemoveElementButton extends RunElementButton {
        public RemoveElementButton(GuiListModifier<?> parent, int x, int y, int widthIn, int heightIn,
                                   ListElement element) {
            super(x, y, widthIn, heightIn, Component.literal("-"), () -> {
                parent.elements.remove(element);
                parent.needRedefine = true;
            }, null);
            // setFGColor(Objects.requireNonNull(ChatFormatting.RED.getColor()));
            setMessage(Component.literal("-").withStyle(ChatFormatting.RED));
        }

        public RemoveElementButton(GuiListModifier<?> parent, int x, int y, ListElement element) {
            this(parent, x, y, 200, 20, element);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", I18n.get("gui.act.delete"));
        }
    }

    /**
     * a button that run left and right mouse button action
     */
    public static class RunElementButton extends ACTButton {
        private final Runnable left;
        private final Runnable right;

        public RunElementButton(int x, int y, int widthIn, int heightIn, Component text, Runnable left,
                                Runnable right) {
            super(x, y, widthIn, heightIn, text, b -> {
            });
            this.left = left;
            this.right = right;
        }

        /**
         * @return the left mouse {@link Runnable}
         */
        public Runnable getLeft() {
            return left;
        }

        /**
         * @return the right mouse {@link Runnable}
         */
        public Runnable getRightButton() {
            return right;
        }
    }

    private final List<ListElement> elements;
    private final List<ListElement> searchedElements = new ArrayList<>();
    private List<ListElement>[] visibleElements;
    private final boolean doneButton;
    private final boolean cancelButton;
    private int page, maxPage, sizeX;

    private Button lastPage, nextPage;

    private final EditBox search;

    protected Tuple<String, Tuple<Runnable, Runnable>>[] buttons;

    private boolean needRedefine = false;

    private int dSize = 42, paddingLeft = 0, paddingTop = 0;
    private boolean noAdaptativeSize = false;

    private boolean justStart = true;

    public GuiListModifier(Screen parent, Component name, List<ListElement> elements, Consumer<T> setter,
                           boolean doneButton, boolean cancelButton, Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        super(parent, name, setter);
        this.elements = elements;
        this.buttons = buttons;
        this.doneButton = doneButton;
        this.cancelButton = cancelButton;
        this.setter = setter;
        this.parent = parent;
        search = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 0, Component.literal(""));
    }

    public GuiListModifier(Screen parent, Component name, List<ListElement> elements, Consumer<T> setter,
                           boolean doneButton, Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        this(parent, name, elements, setter, doneButton, true, buttons);
    }

    public GuiListModifier(Screen parent, Component name, List<ListElement> elements, Consumer<T> setter,
                           Tuple<String, Tuple<Runnable, Runnable>>[] buttons) {
        this(parent, name, elements, setter, true, buttons);
    }

    public void addListElement(int i, ListElement elem) {
        elements.add(i, elem);
        needRedefine = true;
    }

    public void addListElement(ListElement elem) {
        elements.add(elem);
        needRedefine = true;
    }

    @Override
    public boolean charTyped(char key, int modifiers) {
        boolean flag = false;
        for (List<ListElement> lel : visibleElements)
            for (ListElement le : lel)
                if (le.isFocused()) {
                    flag = true;
                    break;
                }
        
        boolean handled = false;
        for (List<ListElement> lel : visibleElements)
            for (ListElement le : lel)
                if (le.charTyped(key, modifiers))
                    handled = true;

        if (!flag && !justStart)
            search.setFocused(true);
        else {
            justStart = false;
            if (flag)
                search.setFocused(false);
        }
        return handled || super.charTyped(key, modifiers);

    }

    @SuppressWarnings("unchecked")
    private void define() {
        // removing current visible elements
        searchedElements.clear();
        // search...
        elements.stream().filter(le -> le.match(search.getValue())).forEach(searchedElements::add);
        sizeX = searchedElements.stream().mapToInt(ListElement::getSizeX).max().orElse(width - 20 - paddingLeft)
                + paddingLeft;
        visibleElements = new ArrayList[Math.max(1, (width - 20) / sizeX)];
        for (int i = 0; i < visibleElements.length; i++)
            visibleElements[i] = new ArrayList<>();
        maxPage = 0;
        int pageHeight = height - 63;
        int currentSize = 0;
        int i = 0;
        for (ListElement elm : searchedElements) {
            int sy = paddingTop + elm.sizeY;
            if (currentSize + sy > pageHeight) {
                if ((i = (i + 1) % visibleElements.length) == 0)
                    maxPage++;
                currentSize = sy;
            } else
                currentSize += sy;
            if (page == maxPage)
                visibleElements[i].add(elm);
        }
        maxPage = Math.max(1, maxPage + (currentSize == 0 ? 0 : 1));
        if (page >= maxPage) {
            page = maxPage - 1;
            define();
        }
        int j;
        boolean add = false;
        if (page == 0)
            mass:for (j = 0; j < visibleElements.length; j++) {
                if (visibleElements[j].isEmpty())
                    break;
                for (ListElement le : visibleElements[j])
                    if (le instanceof AddElementList) {
                        add = true;
                        break mass;
                    }
            }
        else
            j = visibleElements.length;
        dSize = (!(add || noAdaptativeSize) && j == 1 && maxPage == 1
                ? height / 2 - visibleElements[0].stream().mapToInt(ListElement::getSizeY).sum() / 2
                : 42) + paddingTop;
        lastPage.active = page != 0;
        nextPage.active = page + 1 < maxPage;
    }

    protected abstract T get();

    public List<ListElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    private int getOffsetX() {
        int i;
        if (page == 0)
            for (i = 0; i < visibleElements.length; i++) {
                if (visibleElements[i].isEmpty())
                    break;
            }
        else
            i = visibleElements.length;
        return (width - sizeX * (i - 1) + paddingLeft) / 2;
    }

    public int getPaddingLeft() {
        return paddingLeft;
    }

    public int getPaddingTop() {
        return paddingTop;
    }

    @Override
    public void init() {
        page = 0;
        int l = (doneButton ? 1 : 0);
        int d = (buttons.length + l + (cancelButton ? 1 : 0)) * 50;
        int dl = width / 2 - d;
        int dr = width / 2 + d;
        if (doneButton)
            addRenderableWidget(new ACTButton(dl, height - 21, 99, 20, Component.translatable("gui.done"), b -> {
                set(get());
                getMinecraft().setScreen(parent);
            }));
        int i;
        for (i = 0; i < buttons.length; i++)
            addRenderableWidget(new GuiValueButton<>(dl + 100 * (i + l), height - 21, 99, 20,
                    Component.literal(buttons[i].a), buttons[i].b, b -> b.getValue().a.run()));
        if (cancelButton)
            addRenderableWidget(new ACTButton(dl + 100 * (i + l), height - 21, 99, 20,
                    Component.translatable("gui.act.cancel"), b -> onCancel()));
        addRenderableWidget(lastPage = new ACTButton(dl - 21, height - 21, 20, 20, Component.literal("<-"), b -> {
            page--;
            define();
        }) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.translatable("gui.narrate.button", I18n.get("gui.act.leftArrow"));
            }
        });
        addRenderableWidget(nextPage = new ACTButton(dr, height - 21, 20, 20, Component.literal("->"), b -> {
            page++;
            define();
        }) {

            @Override
            protected MutableComponent createNarrationMessage() {
                return Component.translatable("gui.narrate.button", I18n.get("gui.act.rightArrow"));
            }
        });

        int m = font.width(I18n.get("gui.act.search") + " : ");
        int n = Math.min(600, width - 20);
        search.setX((width - n) / 2 + 6 + m);
        search.setY(18);
        search.setWidth(n - 18 - m);
        search.setHeight(18);
        search.setResponder(s -> {
            page = 0;
            define();
        });
        addRenderableWidget(search);
        elements.forEach(ListElement::init);

        define();
        super.init();

    }

    public boolean isNoAdaptativeSize() {
        return noAdaptativeSize;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean flag = false;
        for (List<ListElement> lel : visibleElements)
            for (ListElement le : lel)
                if (le.isFocused()) {
                    flag = true;
                    break;
                }
        
        boolean handled = false;
        for (List<ListElement> lel : visibleElements)
            for (ListElement le : lel)
                if (le.keyPressed(key, scanCode, modifiers))
                    handled = true;

        if (!flag)
            search.setFocused(true);
        return handled || super.keyPressed(key, scanCode, modifiers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton))
            return true;
        boolean elementClicked = false;
        for (int i = 0; i < visibleElements.length; i++) {
            int currentSize = dSize;
            for (ListElement le : visibleElements[i]) {
                int offsetX = getOffsetX() + sizeX * i - le.getSizeX() / 2;
                le.mouseClicked(mouseX - offsetX, mouseY - currentSize, mouseButton);
                if (le.isFocused()) {
                    elementClicked = true;
                }
                currentSize += le.getSizeY() + paddingTop;
            }
        }
        if (elementClicked) {
            search.setFocused(false);
            this.setFocused(null);
        }
        if (mouseButton == 1) {
            if (GuiUtils.isHover(search.getX(), search.getY(), search.getWidth(), search.getHeight(), (int) mouseX,
                    (int) mouseY)) {
                search.setValue("");
                page = 0;
                define();
            } else
                children().stream()
                        .filter(button -> button instanceof GuiValueButton
                                && GuiUtils.isHover(((Button) button), (int) mouseX, (int) mouseY))
                        .map(b -> (GuiValueButton<Tuple<Runnable, Runnable>>) b).forEach(b -> b.getValue().b.run());
        }
        if (needRedefine) {
            needRedefine = false;
            define();
        }
        return elementClicked;
    }

    public void removeListElement(ListElement elem) {
        elements.remove(elem);
        needRedefine = true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // do nothing
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        GuiUtils.drawGradientRect(graphics, 0, 0, width, height, 0xC0101010, 0xD0101010);
        for (int i = 0; i < visibleElements.length; i++) {
            int currentSize = dSize;
            for (ListElement le : visibleElements[i]) {
                int offsetX = getOffsetX() + sizeX * i - le.getSizeX() / 2;
                le.draw(graphics, offsetX, currentSize, mouseX - offsetX, mouseY - currentSize, partialTicks);
                currentSize += le.getSizeY() + paddingTop;
            }
        }
        // search.render(graphics, mouseX, mouseY, partialTicks); // REMOVED
        GuiUtils.drawCenterString(graphics, font, getStringTitle(), width / 2, 2, 0xFFFFFFFF, 10);
        GuiUtils.drawRightString(graphics, font, I18n.get("gui.act.search") + " : ", search.getX(), search.getY(), Color.ORANGE.getRGB(),
                search.getHeight());
        if (equals(getMinecraft().screen)) {
            for (int i = 0; i < visibleElements.length; i++) {
                int currentSize = dSize;
                for (ListElement le : visibleElements[i]) {
                    int offsetX = getOffsetX() + sizeX * i - le.getSizeX() / 2;
                    le.drawNext(graphics, offsetX, currentSize, mouseX - offsetX, mouseY - currentSize,
                            partialTicks);
                    currentSize += le.getSizeY() + paddingTop;
                }
            }
        }
    }

    public void setNoAdaptativeSize(boolean noAdaptativeSize) {
        this.noAdaptativeSize = noAdaptativeSize;
    }

    public void setPaddingLeft(int paddingLeft) {
        this.paddingLeft = paddingLeft;
    }

    public void setPaddingTop(int paddingTop) {
        this.paddingTop = paddingTop;
    }

    @Override
    public void tick() {
        // search.tick(); // Removed
        for (List<ListElement> lel : visibleElements)
            lel.forEach(ListElement::update);
        super.tick();
    }

    @Override
    protected void generateDev(List<ACTDevInfo> entries, int mouseX, int mouseY) {
        entries.add(devInfo("List", this.elements.size() + " element(s)", (page + 1) + "/" + maxPage));
        super.generateDev(entries, mouseX, mouseY);
    }
}
