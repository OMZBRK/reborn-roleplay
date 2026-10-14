package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.inventory.Notifs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Canal {@code reborn:nourriture} (S2C, JSON UTF-8 brut sans préfixe, champs optionnels) — docs/technique/
 * PLUGINS_REFERENCE.md §4 ter :
 * <ul>
 *   <li>{@code {"t":"prep","nom":"Ramen shōyu","ic":"vapeur","dur":15,"fait":0}} / {@code {"t":"prep_fin","ok":true}} :
 *       préparation au fourneau (une ligne + un filet au-dessus de la barre d'action) ;</li>
 *   <li>{@code {"t":"repas","nom":…,"ic":"bol","dur":45,"fait":12.5,"vit":2,"pause":28,"froid":false}} /
 *       {@code {"t":"repas_fin","ok":true}} : repas en cours (vit = vitesse, 2 assis ; pause = secondes figées) ;</li>
 *   <li>{@code {"t":"offre","de":"Teuchi","quoi":"un Ramen shōyu","etat":"chaud","mat":…,"model":…,"s":10}} /
 *       {@code {"t":"offre_fin"}} : on me tend quelque chose (F prendre · Retour refuser) ;</li>
 *   <li>{@code {"t":"attente","a":"Ren","quoi":"120 ryo","s":10}} / {@code {"t":"attente_fin"}} : ce que je tends ;</li>
 *   <li>{@code {"t":"main","nom":"Ramen shōyu","chaud":420}} / {@code {"t":"main"}} : le plat tenu en main (chaud :
 *       secondes restantes, 0 = froid ; vide = plus de plat en main).</li>
 * </ul>
 * La satiété elle-même passe par {@code reborn:vitals} (champs ajoutés pour les clients qui écoutent ce canal), les
 * effets par {@code reborn:modifs}. Déclarer ce canal (côté mod) vaut « je comprends les vitals étendus ».
 */
public final class NourritureCanal {

    public static final String CHANNEL = "reborn:nourriture";

    private static Plugin plugin;

    private NourritureCanal() {}

    static void start(Plugin owner) {
        plugin = owner;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(owner, CHANNEL)) m.registerOutgoingPluginChannel(owner, CHANNEL);
    }

    /** Le client a-t-il le mod récent (canal déclaré) ? */
    public static boolean ecoute(Player p) {
        return p != null && p.getListeningPluginChannels().contains(CHANNEL);
    }

    static void envoyer(Player p, J j) {
        if (plugin == null || p == null || !p.isOnline() || !ecoute(p)) return;
        try {
            p.sendPluginMessage(plugin, CHANNEL, j.fin().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // canal non déclaré
        }
    }

    /** Ligne de notification en bas à droite avec un petit picto (pas de kanji, DA §3) : {@code "ic"}. */
    static void notif(Player p, String ton, String ic, String gras, String texte) {
        J j = new J().s("t", "msg").s("ton", ton).s("ic", ic).s("gras", gras).s("texte", texte);
        Notifs.envoyer(p, j.fin());
    }

    /** Petit constructeur JSON (valeurs simples). */
    static final class J {
        private final StringBuilder b = new StringBuilder(96).append('{');

        J s(String k, String v) {
            if (v == null) return this;
            sep().append('"').append(k).append("\":\"").append(esc(v)).append('"');
            return this;
        }

        J n(String k, double v) {
            sep().append('"').append(k).append("\":");
            if (Math.abs(v - Math.rint(v)) < 1e-9) b.append((long) Math.rint(v));
            else b.append(String.format(Locale.ROOT, "%.2f", v));
            return this;
        }

        J b(String k, boolean v) {
            sep().append('"').append(k).append("\":").append(v);
            return this;
        }

        private StringBuilder sep() {
            if (b.length() > 1) b.append(',');
            return b;
        }

        String fin() { return b + "}"; }
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }
}
