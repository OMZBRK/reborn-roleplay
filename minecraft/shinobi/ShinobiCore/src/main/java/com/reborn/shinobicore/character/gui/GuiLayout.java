package com.reborn.shinobicore.character.gui;

/**
 * Static helpers for computing chest-GUI slot indices by layout intent
 * (center, pair, triple, border, back, close, ...). Every ShinobiCore GUI
 * routes through these so a single tweak here shifts the visual language
 * across the whole plugin.
 *
 * <h2>Row convention</h2>
 * A standard chest is 9 columns wide; sizes are always multiples of 9.
 * Rows are 0-indexed from the top: row 0 is the header strip, row
 * <em>(rowCount - 1)</em> is the controls strip.
 *
 * <h2>Symmetry</h2>
 * Every "place N buttons in a row" helper picks a symmetric column
 * pattern around column 4 so the row reads visually centered regardless
 * of button count. Callers don't pick slot numbers; they pick
 * <em>intent</em> — "put three buttons in row 1" — and the math lives
 * here.
 */
public final class GuiLayout {

    /** Width of a chest row in slots. */
    public static final int ROW = 9;

    private GuiLayout() {}

    /* --------------------------------------------------------- size helpers */

    /** Total slot count for a chest with {@code rows} visible rows. */
    public static int size(int rows) { return Math.max(1, rows) * ROW; }

    /** First slot index of a given row (the leftmost column). */
    public static int rowStart(int row) { return row * ROW; }

    /** Last slot index of a given row (column 8). */
    public static int rowEnd(int row)   { return row * ROW + (ROW - 1); }

    /* -------------------------------------------------- center-pack helpers */

    /** The middle column of {@code row} (column 4). */
    public static int center(int row) { return row * ROW + 4; }

    /** Two slots symmetric around column 4 — columns 3 and 5.
     *  Good for a "this or that" dialog. */
    public static int[] pair(int row) {
        int base = row * ROW;
        return new int[] { base + 3, base + 5 };
    }

    /** Three slots symmetric around column 4 — columns 2, 4, 6.
     *  Good for Accept / Neutral / Decline layouts. */
    public static int[] triple(int row) {
        int base = row * ROW;
        return new int[] { base + 2, base + 4, base + 6 };
    }

    /** Four slots symmetric around column 4 — columns 1, 3, 5, 7. */
    public static int[] quad(int row) {
        int base = row * ROW;
        return new int[] { base + 1, base + 3, base + 5, base + 7 };
    }

    /** Five slots symmetric around column 4 — columns 2, 3, 4, 5, 6.
     *  Tight center-packed 5-button row (used e.g. by ToggleGui). */
    public static int[] five(int row) {
        int base = row * ROW;
        return new int[] { base + 2, base + 3, base + 4, base + 5, base + 6 };
    }

    /**
     * Generic "center {@code count} items in {@code row}" helper.
     *
     * <p>Picks a start column so the run is symmetric around column 4.
     * For {@code count} 1..9 this yields an exact centered placement;
     * {@code count} > 9 is clamped to a full 9-wide row. Returned
     * indices are in left-to-right order.
     *
     * <p>This is the generic backstop for layouts that need to stay
     * centered even when the item count is dynamic (encyclopedia
     * categories, ability lists, etc).
     */
    public static int[] centeredRow(int row, int count) {
        if (count <= 0) return new int[0];
        int c = Math.min(count, ROW);
        int start = (ROW - c) / 2;
        int base = row * ROW;
        int[] out = new int[c];
        for (int i = 0; i < c; i++) out[i] = base + start + i;
        return out;
    }

    /* --------------------------------------------------- navigation slots */

    /** Where a back-arrow lives — bottom row, column 0. */
    public static int back(int rows)  { return rowStart(rows - 1); }

    /** Where a close button lives — bottom row, column 8. */
    public static int close(int rows) { return rowEnd(rows - 1); }

    /** Where a "home"/primary action lives — bottom row, center column. */
    public static int home(int rows)  { return center(rows - 1); }

    /* ------------------------------------------------- border / perimeter */

    /**
     * Slot indices that form the decorative perimeter of a chest with
     * {@code rows} rows. For 1-row GUIs this is just the two corners
     * (cols 0 and 8); for multi-row GUIs it's the full outline (top row,
     * bottom row, left column, right column).
     *
     * <p>Callers paint these slots with a dark panel for a "framed"
     * appearance, then punch nav buttons through the frame where needed.
     */
    public static int[] border(int rows) {
        if (rows <= 1) return new int[] { 0, ROW - 1 };
        int size = size(rows);
        int count = ROW * 2 + (rows - 2) * 2;  // top + bottom + side columns
        int[] out = new int[count];
        int idx = 0;
        // Top row.
        for (int c = 0; c < ROW; c++) out[idx++] = c;
        // Middle-row side columns.
        for (int r = 1; r < rows - 1; r++) {
            out[idx++] = r * ROW;
            out[idx++] = r * ROW + (ROW - 1);
        }
        // Bottom row.
        for (int c = 0; c < ROW; c++) out[idx++] = (rows - 1) * ROW + c;
        // Keep result bounded in-range.
        for (int i = 0; i < out.length; i++) {
            if (out[i] < 0 || out[i] >= size) out[i] = 0;
        }
        return out;
    }
}
