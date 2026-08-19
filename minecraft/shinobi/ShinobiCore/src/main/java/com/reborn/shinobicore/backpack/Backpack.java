package com.reborn.shinobicore.backpack;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Single backpack instance.
 *
 * <p>Identified by an immutable {@link #id UUID} stamped on the
 * physical item via PDC and persisted as the YAML key in
 * {@code backpacks.yml}. The contents array is exactly
 * {@link BackpackSize#slots()} long; {@code null} entries are empty
 * slots. Two backpack items with the same id share contents — so
 * picking up a placed backpack and re-equipping it lands you on the
 * exact same inventory you left.
 *
 * <p>Backpack instances live in {@link BackpackManager} keyed by
 * {@link #id}. The lifetime of a backpack is independent of any
 * character or player — backpacks change hands freely.
 */
public final class Backpack {

    private final UUID id;
    private final BackpackSize size;
    private final ItemStack[] contents;

    public Backpack(UUID id, BackpackSize size) {
        this.id = id;
        this.size = size;
        this.contents = new ItemStack[size.slots()];
    }

    public UUID id()              { return id; }
    public BackpackSize size()    { return size; }

    /** Direct access to the backing array — the GUI mutates this in
     *  place during open/close cycles, and the manager's save() reads
     *  it for serialisation. Length is always {@link BackpackSize#slots()}. */
    public ItemStack[] contents() { return contents; }

    public ItemStack get(int slot) {
        return (slot < 0 || slot >= contents.length) ? null : contents[slot];
    }

    public void set(int slot, ItemStack stack) {
        if (slot < 0 || slot >= contents.length) return;
        contents[slot] = stack;
    }

    /** True iff every slot is empty. Useful for "drop empty backpack
     *  on ground" decisions. */
    public boolean isEmpty() {
        for (ItemStack s : contents) {
            if (s != null && !s.getType().isAir() && s.getAmount() > 0) return false;
        }
        return true;
    }
}
