package com.reborn.shinobicore.gui.framework;

import com.reborn.shinobicore.api.ScreenRouter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Base class of every framework GUI screen.
 *
 * <h2>The contract — what a new GUI implements</h2>
 * <ol>
 *   <li>{@link #title} + {@link #rows} — chest shape.</li>
 *   <li>{@link #render} — paint items; clickable ones are built (or
 *       tagged) through {@link Ui} so they carry an action.</li>
 *   <li>{@link #onAction} — react to those actions.</li>
 * </ol>
 * Everything else is shared plumbing in {@link ScreenManager}: click
 * cancellation, drag cancellation, close button, back button,
 * pagination (override {@link #pages}), open/refresh sounds, and
 * force-close on character switch / KO. Adding a screen never means
 * writing another listener.
 *
 * <p>Screens are stateless singletons; per-open data lives in the
 * {@link View}'s state bag.
 */
public abstract class Screen {

    protected final ScreenRouter screenRouter;

    protected Screen(ScreenRouter screenRouter) {
        this.screenRouter = screenRouter;
    }

    /** Chest title for this view (built once at open). */
    public abstract Component title(Player viewer, View view);

    /** Chest rows, 1-6 (may depend on the view's state). */
    public abstract int rows(View view);

    /** Paint the inventory. Called on open and on every refresh. */
    public abstract void render(Player viewer, View view, Inventory inv);

    /**
     * A tagged button was clicked. {@code value} is the button's
     * payload (may be null). The click event is provided for
     * left/right/shift distinctions — it is already cancelled.
     */
    public abstract void onAction(Player viewer, View view, String action,
                                  String value, InventoryClickEvent event);

    /** Page count for the shared footer pagination. ≤ 1 hides nav. */
    public int pages(Player viewer, View view) {
        return 1;
    }

    /** Back-arrow target. Default: the hub. */
    public void onBack(Player viewer, View view) {
        screenRouter.openHub(viewer);
    }

    /**
     * Click that hit an untagged item, or any click in the viewer's own
     * inventory while this screen is open. Default no-op; item-moving
     * screens (the learning shelf) override this. The event arrives
     * already cancelled — un-cancel deliberately if vanilla behaviour
     * is wanted.
     */
    public void onRawClick(Player viewer, View view, InventoryClickEvent event) {
    }

    /**
     * Drag gesture while this screen is open. The event arrives already
     * cancelled — item-moving screens (treatment drop strip) un-cancel
     * deliberately when every affected slot is theirs to allow. Default:
     * stays cancelled.
     */
    public void onRawDrag(Player viewer, View view,
                          org.bukkit.event.inventory.InventoryDragEvent event) {
    }

    /**
     * The view's inventory just closed (ESC, close button, forced).
     * Default no-op; screens with in-flight state (refundable drop
     * strips, spinners) override to clean up. The inventory is still
     * readable through {@code view.getInventory()}.
     */
    public void onClose(Player viewer, View view) {
    }

    /** Convenience: re-render this view in place. */
    protected final void refresh(Player viewer, View view) {
        screenRouter.screens().refresh(viewer, view);
    }
}
