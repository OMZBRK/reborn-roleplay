package com.reborn.shinobicore.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * The per-store backend switch. A store author asks which backend the
 * operator configured for their store name and extends the matching
 * base class:
 *
 * <pre>{@code
 * // config.yml (of the OWNING plugin):
 * // storage:
 * //   backend:
 * //     missions: sql     # or yaml (the default)
 * if (DataStores.backend(plugin, "missions") == DataStores.Backend.SQL) { ... }
 * }</pre>
 *
 * Existing stores (jinchūriki, academy) stay YAML and are not switched
 * retroactively — there is no data migration at this seam.
 */
public final class DataStores {

    public enum Backend { YAML, SQL }

    private DataStores() {}

    /** Configured backend for {@code storeName}; YAML unless the owning
     *  plugin's config says {@code storage.backend.<storeName>: sql}. */
    public static Backend backend(JavaPlugin plugin, String storeName) {
        String raw = plugin.getConfig()
                .getString("storage.backend." + storeName, "yaml");
        return "sql".equalsIgnoreCase(raw.trim().toLowerCase(Locale.ROOT))
                ? Backend.SQL : Backend.YAML;
    }
}
