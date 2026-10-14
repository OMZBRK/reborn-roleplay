package com.reborn.shinobiradio;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:radio} (S → C, JSON UTF-8 brut sans préfixe de longueur ; champs optionnels, inconnus ignorés).
 * Protocole : docs/technique/PLUGINS_REFERENCE.md §4 quater.
 * <ul>
 *   <li>{@code etat} : mon oreillette, mon escouade (membres et leur état), mes drapeaux (haché, muet, coupé par le
 *       staff) — à la connexion et à chaque changement ;</li>
 *   <li>{@code parle} / {@code tait} : quelqu'un parle sur mon escouade (source Plasmo Voice, tête, nom, haché) ;</li>
 *   <li>{@code invit} / {@code invit_fin} : on m'ajoute (Entrée accepter · Retour refuser, 10 s) ;</li>
 *   <li>{@code ecoute} / {@code ecoute_fin} : staff seulement, écoute en cours (escouade, secondes restantes).</li>
 * </ul>
 * Les réponses repartent par commandes ({@code /radio …}) : le serveur revérifie tout.
 */
final class Canal {

    static final String ID = "reborn:radio";

    private final Plugin plugin;

    Canal(Plugin plugin) {
        this.plugin = plugin;
    }

    void ouvrir() {
        var m = plugin.getServer().getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, ID)) m.registerOutgoingPluginChannel(plugin, ID);
    }

    void fermer() {
        var m = plugin.getServer().getMessenger();
        if (m.isOutgoingChannelRegistered(plugin, ID)) m.unregisterOutgoingPluginChannel(plugin, ID);
    }

    /** Le client a-t-il déclaré le canal (mod Reborn à jour) ? */
    static boolean ecoute(Player p) {
        return p != null && p.getListeningPluginChannels().contains(ID);
    }

    void envoyer(Player p, String json) {
        if (p == null || !p.isOnline()) return;
        try { p.sendPluginMessage(plugin, ID, json.getBytes(StandardCharsets.UTF_8)); } catch (RuntimeException ignored) { }
    }

    /* ------------------------------------------------------------------ JSON minimal */

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append(' ');
                default -> { if (c >= 0x20) b.append(c); }
            }
        }
        return b.toString();
    }

    /** {@code "k":"v"} */
    static String kv(String k, String v) { return '"' + k + "\":\"" + esc(v) + '"'; }
}
