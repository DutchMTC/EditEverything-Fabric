package com.dutchmtc.ee.gui.modifier;

import com.dutchmtc.ee.utils.ContainerItemData;
import com.dutchmtc.ee.utils.ItemUtils.ContainerData;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import org.apache.commons.lang3.math.Fraction;

/** Native inventory interactions over local copies; never attached to the player's real menu. */
final class ContainerEditorMenu extends AbstractContainerMenu {
    static final int WIDTH = 256, GRID_TOP = 34;
    final ContainerData original;
    final SimpleContainer contents, inventory;
    final int inventoryTop, gridLeft, rows, pageSize;
    private int page;

    ContainerEditorMenu(ContainerData source, Inventory playerInventory) {
        super(null, -1);
        original = source.copy();
        contents = new SimpleContainer(source.stacks().size());
        inventory = new SimpleContainer(36);
        for (int i = 0; i < contents.getContainerSize(); i++)
            contents.items.set(i, source.stacks().get(i).copy());
        for (int i = 0; i < 36; i++) inventory.items.set(i, playerInventory.getItem(i).copy());
        gridLeft = (WIDTH - source.size().sizeX() * 18) / 2 + 1;
        rows = Math.min(3, source.size().sizeY());
        pageSize = rows * source.size().sizeX();
        inventoryTop = GRID_TOP + rows * 18 + 18;
        for (int i = 0; i < contents.getContainerSize(); i++) {
            final int index = i;
            addSlot(
                    new Slot(
                            contents,
                            i,
                            gridLeft + (i % source.size().sizeX()) * 18,
                            GRID_TOP + ((i / source.size().sizeX()) % rows) * 18) {
                        @Override
                        public boolean isActive() {
                            return index / pageSize == page;
                        }

                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return !original.bundle() || bundleLimit(index, stack) > 0;
                        }

                        @Override
                        public int getMaxStackSize(ItemStack stack) {
                            return original.bundle()
                                    ? bundleLimit(index, stack)
                                    : super.getMaxStackSize(stack);
                        }
                    });
        }
        addStandardInventorySlots(inventory, (WIDTH - 162) / 2 + 1, inventoryTop);
    }

    int height() {
        return inventoryTop + 76 + 36;
    }

    ContainerData result() {
        NonNullList<ItemStack> stacks =
                NonNullList.withSize(contents.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) stacks.set(i, contents.getItem(i).copy());
        return new ContainerData(original.size(), stacks, original.bundle());
    }

    int page() {
        return page;
    }

    int pageCount() {
        int size = contents.getContainerSize();
        if (original.bundle()) {
            int last = 0;
            for (int i = 0; i < size; i++) if (!contents.getItem(i).isEmpty()) last = i + 1;
            size = Math.min(size, Math.max(last + 1, (page + 1) * pageSize));
        }
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    void setPage(int next) {
        resetQuickCraft();
        page = Math.max(0, Math.min(pageCount() - 1, next));
    }

    private int bundleLimit(int excluded, ItemStack stack) {
        if (!BundleContents.canItemBeInBundle(stack)) return 0;
        var others = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < contents.getContainerSize(); i++)
            if (i != excluded) others.add(contents.getItem(i));
        var bundle = ContainerItemData.bundle(others);
        var weight = bundle.weight().result();
        if (weight.isEmpty() || weight.get().compareTo(Fraction.ONE) > 0) return 0;
        return Math.max(
                0, bundle.asMutable().tryInsert(stack.copyWithCount(stack.getMaxStackSize())));
    }

    String validationReason() {
        if (!original.bundle()) return null;
        for (var stack : contents.items)
            if (!stack.isEmpty() && !BundleContents.canItemBeInBundle(stack))
                return "This item cannot be stored in a bundle. Remove it before applying.";
        var weight = ContainerItemData.bundle(contents.items).weight().result();
        if (weight.isEmpty() || weight.get().compareTo(Fraction.ONE) > 0)
            return "The bundle is over its capacity. Remove some items before applying.";
        return null;
    }

    String placementReason(Slot slot) {
        ItemStack held = getCarried();
        if (!original.bundle() || held.isEmpty() || slot.index >= contents.getContainerSize())
            return null;
        if (!BundleContents.canItemBeInBundle(held))
            return "This item cannot be stored in a bundle.";
        return slot.getMaxStackSize(held) == 0
                ? "The bundle is full. Remove some items first."
                : null;
    }

    String fullness() {
        var weight = ContainerItemData.bundle(contents.items).weight().result();
        return weight.isEmpty()
                ? "Over capacity"
                : (int) Math.ceil(weight.get().doubleValue() * 100) + "% full";
    }

    boolean changed() {
        if (!getCarried().isEmpty()) return true;
        for (int i = 0; i < contents.getContainerSize(); i++)
            if (!ItemStack.matches(contents.getItem(i), original.stacks().get(i))) return true;
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        /* Navigation must not drop/return a draft cursor. */
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (input == ContainerInput.SWAP) {
            if (slot < 0 || slot >= slots.size() || button < 0 || button >= 9) return;
            Slot target = slots.get(slot),
                    hotbar = slots.get(contents.getContainerSize() + 27 + button);
            if (target == hotbar) return;
            ItemStack a = target.getItem(), b = hotbar.getItem();
            if ((!b.isEmpty() && (!target.mayPlace(b) || b.getCount() > target.getMaxStackSize(b)))
                    || (!a.isEmpty() && a.getCount() > hotbar.getMaxStackSize(a))) return;
            target.set(b);
            hotbar.set(a);
            return;
        }
        if (input == ContainerInput.THROW) {
            if (getCarried().isEmpty() && slot >= 0 && slot < slots.size())
                slots.get(slot).remove(button == 0 ? 1 : slots.get(slot).getItem().getCount());
            return;
        }
        // Outside clicks never spawn real dropped items or silently discard a held draft.
        if (slot == SLOT_CLICKED_OUTSIDE && input != ContainerInput.QUICK_CRAFT) return;
        super.clicked(slot, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        int boundary = contents.getContainerSize();
        if (!moveItemStackTo(
                stack,
                index < boundary ? boundary : 0,
                index < boundary ? slots.size() : boundary,
                index < boundary)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }
}
