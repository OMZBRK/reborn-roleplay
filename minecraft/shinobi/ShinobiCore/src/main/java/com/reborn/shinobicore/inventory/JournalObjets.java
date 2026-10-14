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
 *   <li>journal universel ({@link com.reborn.shinobicore.journal.Journal}) : {@code logs/inventaire-AAAA-MM-JJ.log}
 *       (et {@code objet-…} pour les objets obtenus), une ligne par action ;</li>
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
        String objet = q + "× " + RareteItems.nom(s) + " [" + r.name() + (unique ? ", unique" : "") + "]";
        String lieu = ou == null || ou.getWorld() == null ? "" : ou.getWorld().getName() + " " + ou.getBlockX() + " "
            + ou.getBlockY() + " " + ou.getBlockZ();
        if (cfg.journalFichier) {
            // Journal universel (14/10) : même fichier qu'avant (logs/inventaire-AAAA-MM-JJ.log), format commun ;
            // un objet obtenu (toute source : staff, échoppe, parchemin…) va dans la catégorie OBJET.
            boolean obtenu = "DONNE".equals(action);
            var e = com.reborn.shinobicore.journal.Journal.entree(obtenu
                    ? com.reborn.shinobicore.api.JournalService.Categorie.OBJET
                    : com.reborn.shinobicore.api.JournalService.Categorie.INVENTAIRE, obtenu ? "OBTENU" : action);
            if (p != null) e.acteur(p);
            e.cibleTexte(RareteItems.nom(s)).detail(objet + (detail == null || detail.isEmpty() ? "" : " · " + detail))
                .lieu(ou).ecrire();
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
}
