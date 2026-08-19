package com.reborn.shinobicore.gui.framework;

import com.reborn.shinobicore.gui.GuiHolder;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One open instance of a {@link Screen}: the inventory holder plus a
 * small typed state bag (target character id, branch, selected slot…)
 * and the shared pagination cursor. Created by {@link ScreenManager};
 * screens never construct it themselves.
 */
public final class View implements GuiHolder {

    private final Screen screen;
    private final Map<String, Object> state = new HashMap<>();
    private Inventory inventory;
    private int page;
    /** Le ScreenManager qui a ouvert cette View (ShinobiCore et ShinobiAbilities
     *  ont chacun leur instance du framework ; chaque instance ne doit traiter
     *  QUE ses propres Views, sinon les clics sont dispatchés en double). */
    private ScreenManager manager;

    View(Screen screen, Map<String, Object> initialState) {
        this.screen = screen;
        if (initialState != null) state.putAll(initialState);
    }

    public Screen screen() { return screen; }

    void bind(Inventory inv) { this.inventory = inv; }
    void bindManager(ScreenManager m) { this.manager = m; }
    ScreenManager manager() { return manager; }

    @Override
    public Inventory getInventory() { return inventory; }

    /* --------------------------------------------------------- pagination */

    public int page() { return page; }

    public void setPage(int page) { this.page = Math.max(0, page); }

    /* -------------------------------------------------------------- state */

    public View set(String key, Object value) {
        if (value == null) state.remove(key);
        else state.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) state.get(key);
    }

    public String string(String key) {
        Object v = state.get(key);
        return v == null ? null : v.toString();
    }

    public UUID uuid(String key) {
        Object v = state.get(key);
        if (v instanceof UUID id) return id;
        if (v instanceof String s) {
            try { return UUID.fromString(s); }
            catch (IllegalArgumentException ignore) { return null; }
        }
        return null;
    }

    public int integer(String key, int def) {
        Object v = state.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public boolean flag(String key) {
        Object v = state.get(key);
        return v instanceof Boolean b && b;
    }
}
