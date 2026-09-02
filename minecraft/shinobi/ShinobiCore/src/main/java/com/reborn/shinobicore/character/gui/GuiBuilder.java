package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Fluent builder for ShinobiCore chest GUIs.
 *
 * <p>Every screen in the plugin shares the same visual language — framed
 * with a decorative border, center-packed buttons around column 4,
 * colour-tiered action items, consistent nav slots — and shares one
 * implementation: this builder.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * GuiBuilder.of(holder, 3)                          // 27-slot chest
 *     .title(GuiTitles.framedWithCharacter("Rencontrer", character))
 *     .border()                                     // perimeter of black panels
 *     .triple(1, giveIcon, askIcon, otherIcon)      // row 1, cols 2/4/6
 *     .back(GuiIcons.backButton())                  // bottom-left
 *     .close(GuiIcons.closeButton())                // bottom-right
 *     .fillEmpty()                                  // remaining nulls → filler
 *     .open(viewer);
 * }</pre>
 *
 * <p>Every mutating method returns {@code this} so call chains stay flat.
 * The final {@link #open(Player)} builds the {@link Inventory}, binds it
 * to the caller-supplied {@link InventoryHolder}, and presents it to the
 * viewer with a subtle "menu opened" sound cue.
 *
 * <h2>Holder contract</h2>
 * The caller still owns the {@link InventoryHolder} instance. After
 * {@code open} returns, {@link HolderInventoryAccess#attach(InventoryHolder, Inventory)}
 * has been called — so the holder's {@code getInventory()} implementation
 * must read through a field this builder populated (see the existing
 * pattern in e.g. the backpack / cinematic / vanish GUIs).
 */
public final class GuiBuilder {

    private final InventoryHolder holder;
    private final int rows;
    private final int size;
    private final ItemStack[] slots;
    private Component title = Component.text("Menu", NamedTextColor.GOLD);
    private boolean opened = false;

    private GuiBuilder(InventoryHolder holder, int rows) {
        this.holder = Objects.requireNonNull(holder, "holder");
        this.rows = Math.max(1, Math.min(6, rows));
        this.size = this.rows * GuiLayout.ROW;
        this.slots = new ItemStack[size];
    }

    /** Start a new builder for a chest of {@code rows} rows (1-6). */
    public static GuiBuilder of(InventoryHolder holder, int rows) {
        return new GuiBuilder(holder, rows);
    }

    /* ---------------------------------------------------------------- title */

    public GuiBuilder title(Component title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    public GuiBuilder title(String framedText) {
        return title(GuiTitles.framed(framedText));
    }

    public GuiBuilder titleWithCharacter(String base, ShinobiCharacter character) {
        return title(GuiTitles.framedWithCharacter(base, character));
    }

    /* ------------------------------------------------------ single-slot ops */

    public GuiBuilder item(int slot, ItemStack item) {
        if (slot >= 0 && slot < size) slots[slot] = item;
        return this;
    }

    /* ------------------------------------------------- intent-based layout */

    /** Place one item in the centre column (col 4) of {@code row}. */
    public GuiBuilder center(int row, ItemStack item) {
        return item(GuiLayout.center(row), item);
    }

    /** Place two items symmetric around col 4 of {@code row} (cols 3, 5). */
    public GuiBuilder pair(int row, ItemStack left, ItemStack right) {
        int[] s = GuiLayout.pair(row);
        return item(s[0], left).item(s[1], right);
    }

    /** Place three items symmetric around col 4 of {@code row} (cols 2, 4, 6). */
    public GuiBuilder triple(int row, ItemStack left, ItemStack middle, ItemStack right) {
        int[] s = GuiLayout.triple(row);
        return item(s[0], left).item(s[1], middle).item(s[2], right);
    }

    /** Place four items symmetric around col 4 of {@code row} (cols 1, 3, 5, 7). */
    public GuiBuilder quad(int row, ItemStack a, ItemStack b, ItemStack c, ItemStack d) {
        int[] s = GuiLayout.quad(row);
        return item(s[0], a).item(s[1], b).item(s[2], c).item(s[3], d);
    }

    /** Place five items tight-centred in {@code row} (cols 2..6). */
    public GuiBuilder five(int row, ItemStack... items) {
        int[] s = GuiLayout.five(row);
        for (int i = 0; i < Math.min(items.length, s.length); i++) {
            item(s[i], items[i]);
        }
        return this;
    }

    /** Write a whole row of items starting at column 0. Items past
     *  column 8 are silently dropped; shorter arrays leave the tail of
     *  the row empty. */
    public GuiBuilder row(int row, ItemStack... items) {
        int base = GuiLayout.rowStart(row);
        for (int i = 0; i < Math.min(items.length, GuiLayout.ROW); i++) {
            item(base + i, items[i]);
        }
        return this;
    }

    /* ------------------------------------------------ navigation shortcuts */

    /** Place a back button at bottom-left. */
    public GuiBuilder back(ItemStack item) {
        return item(GuiLayout.back(rows), item);
    }

    /** Place a close button at bottom-right. */
    public GuiBuilder close(ItemStack item) {
        return item(GuiLayout.close(rows), item);
    }

    /** Place a "home"/primary action button at bottom-center. */
    public GuiBuilder home(ItemStack item) {
        return item(GuiLayout.home(rows), item);
    }

    /* -------------------------------------------------------------- border */

    /**
     * Paint the decorative perimeter with {@link GuiIcons#border()} panels.
     * Call this <em>before</em> placing nav buttons — their slots punch
     * through the border where they land.
     */
    public GuiBuilder border() {
        ItemStack panel = GuiIcons.border();
        for (int s : GuiLayout.border(rows)) slots[s] = panel;
        return this;
    }

    /** Paint just the top row with {@link GuiIcons#border()} — a slim
     *  header strip for GUIs whose interior can't afford a full perimeter. */
    public GuiBuilder borderTop() {
        ItemStack panel = GuiIcons.border();
        for (int c = 0; c < GuiLayout.ROW; c++) slots[c] = panel;
        return this;
    }

    /** Paint just the bottom row with {@link GuiIcons#border()} —
     *  complements {@link #borderTop} when the bottom row is decorative. */
    public GuiBuilder borderBottom() {
        ItemStack panel = GuiIcons.border();
        int base = GuiLayout.rowStart(rows - 1);
        for (int c = 0; c < GuiLayout.ROW; c++) slots[base + c] = panel;
        return this;
    }

    /* -------------------------------------------------------------- filler */

    /** Fill every still-empty slot with {@link GuiIcons#filler()}. */
    public GuiBuilder fillEmpty() {
        ItemStack f = GuiIcons.filler();
        for (int i = 0; i < size; i++) if (slots[i] == null) slots[i] = f;
        return this;
    }

    /* --------------------------------------------------------------- build */

    /**
     * Finish the build: create the inventory, populate it, attach it to
     * the holder via {@link HolderInventoryAccess#attach}, show it to the
     * viewer, and play an "open" sound cue.
     */
    public void open(Player viewer) {
        if (opened) throw new IllegalStateException("GuiBuilder.open called twice");
        opened = true;
        Inventory inv = Bukkit.createInventory(holder, size, title);
        HolderInventoryAccess.attach(holder, inv);
        for (int i = 0; i < size; i++) if (slots[i] != null) inv.setItem(i, slots[i]);
        viewer.openInventory(inv);
        GuiSounds.open(viewer);
    }

    /**
     * Build variant that only returns the inventory without opening it —
     * useful for callers that need to pre-render the GUI and open later,
     * or tests. The holder is still attached.
     */
    public Inventory build() {
        if (opened) throw new IllegalStateException("GuiBuilder already opened");
        opened = true;
        Inventory inv = Bukkit.createInventory(holder, size, title);
        HolderInventoryAccess.attach(holder, inv);
        for (int i = 0; i < size; i++) if (slots[i] != null) inv.setItem(i, slots[i]);
        return inv;
    }

    /* ----------------------------------------------- holder attachment SPI */

    /**
     * How the builder hands the built {@link Inventory} back to the
     * {@link InventoryHolder}. Each GUI class implements this to plug
     * the inventory into its own field.
     *
     * <p>Rationale: {@code InventoryHolder#getInventory()} is read by
     * Bukkit at click-time. For a fresh holder that has just been passed
     * to {@link Bukkit#createInventory(InventoryHolder, int, Component)},
     * the returned inventory must round-trip back to the holder's field
     * before any click can be dispatched. This interface formalises that
     * round-trip so the DSL doesn't need to know each GUI's field name.
     */
    public interface HolderInventoryBound extends InventoryHolder {
        void bindInventory(Inventory inv);
    }

    /** Internal helper that writes {@code inv} into {@code holder} if it
     *  implements {@link HolderInventoryBound}. Silent no-op otherwise. */
    private static final class HolderInventoryAccess {
        static void attach(InventoryHolder holder, Inventory inv) {
            if (holder instanceof HolderInventoryBound b) b.bindInventory(inv);
        }
    }
}
