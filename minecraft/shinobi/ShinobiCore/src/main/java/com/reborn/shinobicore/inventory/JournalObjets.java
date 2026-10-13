package com.reborn.shinobicore.inventory;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.PlayerIds;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Journal des objets : ramassé, lâché, disparu, détruit, donné, rangé (cases cachées), arme équipée / dégainée.
 * <ul>
 *   <li>fichier tournant {@code plugins/ShinobiCore/logs/inventaire-AAAA-MM-JJ.log} (une ligne par action) ;</li>
 *   <li>Poste de garde (type {@code objet}) pour les objets {@code Rare} et plus, et les uniques
 *       ({@code inventaire.journal.poste-de-garde-des}).</li>
 * </ul>
 * Le personnage est noté avec son N° ({@code #00042}, Player ID) devant le pseudo, pour retrouver un objet perdu ou une
 * duplication.
 */
public final class JournalObjets {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss");

    private JournalObjets() {}

    /** action : RAMASSE, LACHE, DISPARU, DETRUIT, DONNE, RANGE, ARME… ; p peut être null (disparition). */
    public static void noter(ShinobiCore plugin, Player p, String action, ItemStack s, int q, Location ou, String detail) {
        if (plugin == null || s == null) return;
        InventaireConfig cfg = InventaireConfig.get(plugin);
        RpRarity r = RareteItems.of(s);
        boolean unique = RareteItems.unique(s);
        String qui = qui(plugin, p);
        String objet = q + "× " + RareteItems.nom(s) + " [" + r.name() + (unique ? ", unique" : "") + "]";
        String lieu = ou == null || ou.getWorld() == null ? "" : ou.getWorld().getName() + " " + ou.getBlockX() + " "
            + ou.getBlockY() + " " + ou.getBlockZ();
        if (cfg.journalFichier) {
            ecrire(plugin, LocalTime.now().format(HEURE) + " | " + qui + " | " + action + " | " + objet
                + (lieu.isEmpty() ? "" : " | " + lieu) + (detail == null || detail.isEmpty() ? "" : " | " + detail));
        }
        if ((unique || r.auMoins(cfg.journalGardeMin)) && p != null && plugin.staffPanel() != null) {
            try {
                plugin.staffPanel().log(p, "objet", action.toLowerCase(java.util.Locale.ROOT), objet
                    + (lieu.isEmpty() ? "" : " · " + lieu) + (detail == null || detail.isEmpty() ? "" : " · " + detail));
            } catch (RuntimeException ignored) {
                // le journal du Poste de garde est un plus, jamais bloquant
            }
        }
    }

    private static String qui(ShinobiCore plugin, Player p) {
        if (p == null) return "—";
        String tag = PlayerIds.tag(p);
        var c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        return (tag.isEmpty() ? "" : tag + " ") + p.getName() + (c != null ? " (" + c.name() + ")" : "");
    }

    private static synchronized void ecrire(ShinobiCore plugin, String ligne) {
        File dir = new File(plugin.getDataFolder(), "logs");
        if (!dir.exists() && !dir.mkdirs()) return;
        File f = new File(dir, "inventaire-" + LocalDate.now() + ".log");
        try (PrintWriter w = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, true))) {
            w.println(ligne);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture du journal d'inventaire", e);
        }
    }
}
