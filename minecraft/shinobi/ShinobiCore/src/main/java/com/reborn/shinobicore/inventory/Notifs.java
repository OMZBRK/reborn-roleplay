package com.reborn.shinobicore.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Canal générique {@code reborn:notif} (S2C, JSON UTF-8 brut sans préfixe) : la pile de notifications en bas à droite
 * du mod (NotifHud) et l'état de charge du personnage. Un client sans le mod n'a pas le canal : rien n'est envoyé
 * (Bukkit n'expédie qu'aux clients qui l'ont déclaré), et les messages ne tombent plus en barre d'action.
 *
 * <ul>
 *   <li>{@code {"t":"gain","q":3,"nom":"Kunai","rar":"PEU_COMMUN","mat":"minecraft:paper","model":"nexo:kunai",
 *       "suite":"1 reste au sol"}} : « +3 Kunai » (cumulé côté client sur 1,5 s) ;</li>
 *   <li>{@code {"t":"perte","q":1,"nom":"Ration","suite":"lâchée",…}} : « −1 Ration · lâchée » ;</li>
 *   <li>{@code {"t":"msg","ton":"refus|info|palier|etat","kj":"袋","gras":"Plus assez de place",
 *       "texte":"libère une case"}} : une ligne ;</li>
 *   <li>{@code {"t":"poids","cur":45.2,"max":38,"palier":2,"libres":3,"verrou":"KO"}} : état (pas une ligne) ;</li>
 *   <li>{@code {"t":"arme","degainee":true}} : l'arme vient d'être dégainée / rengainée.</li>
 * </ul>
 */
public final class Notifs {

    public static final String CHANNEL = "reborn:notif";

    private static Plugin plugin;

    private Notifs() {}

    public static void start(Plugin owner) {
        plugin = owner;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(owner, CHANNEL)) m.registerOutgoingPluginChannel(owner, CHANNEL);
    }

    /** Une ligne simple : kanji (sceau), mot en gras, suite en clair. ton : refus (vermillon), info, palier (ambre), etat. */
    public static void msg(Player p, String ton, String kanji, String gras, String texte) {
        StringBuilder b = new StringBuilder(96).append("{\"t\":\"msg\",\"ton\":\"").append(esc(ton)).append('"');
        if (kanji != null) b.append(",\"kj\":\"").append(esc(kanji)).append('"');
        if (gras != null) b.append(",\"gras\":\"").append(esc(gras)).append('"');
        if (texte != null) b.append(",\"texte\":\"").append(esc(texte)).append('"');
        envoyer(p, b.append('}').toString());
    }

    /** « +q Nom » (gain) ou « −q Nom » (perte) avec l'icône de l'objet ; suite optionnelle. */
    public static void objet(Player p, boolean gain, ItemStack s, int q, String suite) {
        if (s == null) return;
        String mat = s.getType().getKey().toString(), model = null;
        if (s.hasItemMeta()) {
            ItemMeta m = s.getItemMeta();
            try { if (m.hasItemModel() && m.getItemModel() != null) model = m.getItemModel().toString(); } catch (Throwable ignored) { }
        }
        StringBuilder b = new StringBuilder(128).append("{\"t\":\"").append(gain ? "gain" : "perte").append('"')
            .append(",\"q\":").append(q)
            .append(",\"nom\":\"").append(esc(RareteItems.nom(s))).append('"')
            .append(",\"rar\":\"").append(RareteItems.of(s).name()).append('"')
            .append(",\"mat\":\"").append(esc(mat)).append('"');
        if (model != null) b.append(",\"model\":\"").append(esc(model)).append('"');
        if (suite != null) b.append(",\"suite\":\"").append(esc(suite)).append('"');
        envoyer(p, b.append('}').toString());
    }

    /** État de charge : poids, palier (0-3), cases libres, verrou (raison ou null). */
    public static void poids(Player p, double cur, double max, int palier, int libres, String verrou) {
        StringBuilder b = new StringBuilder(96).append("{\"t\":\"poids\"")
            .append(",\"cur\":").append(String.format(Locale.ROOT, "%.2f", cur))
            .append(",\"max\":").append(String.format(Locale.ROOT, "%.2f", max))
            .append(",\"palier\":").append(palier)
            .append(",\"libres\":").append(libres);
        if (verrou != null) b.append(",\"verrou\":\"").append(esc(verrou)).append('"');
        envoyer(p, b.append('}').toString());
    }

    public static void arme(Player p, boolean degainee) {
        envoyer(p, "{\"t\":\"arme\",\"degainee\":" + degainee + "}");
    }

    public static void envoyer(Player p, String json) {
        if (plugin == null || p == null || !p.isOnline()) return;
        try {
            p.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // client sans le mod : canal non déclaré
        }
    }

    /** Le client a-t-il le mod (canal déclaré) ? */
    public static boolean aLeMod(Player p) {
        return p != null && p.getListeningPluginChannels().contains(CHANNEL);
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }
}
