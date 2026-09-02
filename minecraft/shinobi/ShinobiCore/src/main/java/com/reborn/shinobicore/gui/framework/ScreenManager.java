package com.reborn.shinobicore.gui.framework;

import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.event.KoEnterEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ONE listener behind every framework screen. Owns the whole GUI
 * lifecycle so individual screens never register listeners:
 *
 * <ul>
 *   <li>open / refresh (render + sounds);</li>
 *   <li>click + drag cancellation for any {@link View} holder;</li>
 *   <li>reserved actions: close, back, page:prev / page:next (clamped
 *       through {@link Screen#pages});</li>
 *   <li>routing tagged clicks to {@link Screen#onAction}, untagged and
 *       bottom-inventory clicks to {@link Screen#onRawClick};</li>
 *   <li>force-close on character switch and KO.</li>
 * </ul>
 */
public final class ScreenManager implements Listener {

    /** Dernier clic-bouton par joueur : [slot, timestampMs] (anti double-fire). */
    private final Map<UUID, long[]> lastActionClick = new HashMap<>();

    /** Open a screen with an optional initial state bag. */
    public View open(Player viewer, Screen screen, Map<String, Object> state) {
        View view = new View(screen, state);
        view.bindManager(this); // cette View "appartient" à ce ScreenManager
        int rows = Math.max(1, Math.min(6, screen.rows(view)));
        Inventory inv = Bukkit.createInventory(view,
                rows * GuiLayout.ROW, screen.title(viewer, view));
        view.bind(inv);
        clampPage(viewer, view);
        screen.render(viewer, view, inv);
        viewer.openInventory(inv);
        GuiSounds.open(viewer);
        return view;
    }

    /** Re-render an open view in place (same inventory + title). */
    public void refresh(Player viewer, View view) {
        Inventory inv = view.getInventory();
        if (inv == null) return;
        clampPage(viewer, view);
        inv.clear();
        view.screen().render(viewer, view, inv);
    }

    /** Close the viewer's GUI if it belongs to {@code screen}. */
    public void closeIfScreen(Player viewer, Screen screen) {
        if (viewer.getOpenInventory().getTopInventory()
                .getHolder() instanceof View v && v.screen() == screen) {
            viewer.closeInventory();
        }
    }

    private void clampPage(Player viewer, View view) {
        int pages = Math.max(1, view.screen().pages(viewer, view));
        if (view.page() >= pages) view.setPage(pages - 1);
    }

    /* --------------------------------------------------------------- events */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        // Chaque ScreenManager ne traite QUE les Views qu'il a ouvertes : ShinobiCore
        // et ShinobiAbilities ont chacun leur instance du framework, toutes deux
        // enregistrées. Sans ce garde, un clic dans une GUI est dispatché 2× (double-fire).
        if (view.manager() != null && view.manager() != this) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Screen screen = view.screen();

        // Clicks in the player's own inventory go raw (shelf deposits…).
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(view.getInventory())) {
            screen.onRawClick(p, view, event);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        String action = clicked == null ? null : Ui.actionOf(clicked);
        if (action == null) {
            screen.onRawClick(p, view, event);
            return;
        }

        // Filet défensif contre un double-clic fantôme Bukkit (collect-to-cursor) :
        // ignore un double-clic ou le même bouton recliqué < 200ms par le même joueur.
        if (event.getClick() == ClickType.DOUBLE_CLICK) return;
        int slot = event.getRawSlot();
        long now = System.currentTimeMillis();
        long[] last = lastActionClick.get(p.getUniqueId());
        if (last != null && last[0] == slot && now - last[1] < 200L) return;
        lastActionClick.put(p.getUniqueId(), new long[]{slot, now});

        switch (action) {
            case Ui.ACTION_CLOSE -> {
                p.closeInventory();
                GuiSounds.navigate(p);
            }
            case Ui.ACTION_BACK -> {
                GuiSounds.navigate(p);
                screen.onBack(p, view);
            }
            case Ui.ACTION_PAGE_PREV -> {
                view.setPage(view.page() - 1);
                GuiSounds.navigate(p);
                refresh(p, view);
            }
            case Ui.ACTION_PAGE_NEXT -> {
                view.setPage(view.page() + 1);
                GuiSounds.navigate(p);
                refresh(p, view);
            }
            default -> screen.onAction(p, view, action, Ui.valueOf(clicked), event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (view.manager() != null && view.manager() != this) return;
        if (event.getWhoClicked() instanceof Player p) {
            // Screens with item-moving areas may deliberately un-cancel.
            view.screen().onRawDrag(p, view, event);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof View view)) return;
        if (view.manager() != null && view.manager() != this) return;
        if (event.getPlayer() instanceof Player p) {
            view.screen().onClose(p, view);
        }
    }

    /* ------------------------------------------------------- force closes */

    private static void closeAnyView(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof View) {
            p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCharacterSwitch(CharacterSwitchEvent event) {
        closeAnyView(event.player());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKoEnter(KoEnterEvent event) {
        closeAnyView(event.player());
    }
}
