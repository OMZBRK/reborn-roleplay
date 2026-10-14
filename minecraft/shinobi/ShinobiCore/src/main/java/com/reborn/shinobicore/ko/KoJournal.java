package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * Trace de chaque transition d'état du KO (douleur, agonie, inconscient, réveil, hôpital), de chaque portage et de
 * chaque exécution : une entrée {@code KO} au journal universel ({@code logs/ko-AAAA-MM-JJ.log}) et une entrée
 * {@code ko} au journal du Poste de garde
 * ({@code StaffPanel.log}). Point d'entrée unique, pour que le journal universel puisse s'y brancher.
 */
public final class KoJournal {

    private KoJournal() {}

    /**
     * @param who    le joueur concerné (victime de la transition, ou acteur d'une action)
     * @param type   libellé court (« Douleur », « Agonie », « Exécution »…)
     * @param detail détails lisibles ; {@link #who} pour nommer d'autres joueurs
     */
    public static void log(ShinobiCore plugin, Player who, String type, String detail) {
        if (plugin == null || who == null) return;
        Location l = who.getLocation();
        String where = l.getWorld() != null
                ? l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() : "?";
        // Journal universel (14/10) : logs/ko-AAAA-MM-JJ.log (remplace ko-log.txt), catégorie KO.
        com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.KO,
                type.toUpperCase(java.util.Locale.ROOT).replace(' ', '_')).acteur(who)
                .detail(detail == null ? "" : detail).lieu(l).ecrire();
        try {
            if (plugin.staffPanel() != null) {
                plugin.staffPanel().logSansJournal(who, "ko", type, (detail == null ? "" : detail) + " · " + where);
            }
        } catch (RuntimeException ignored) {
            // le journal du Poste de garde ne doit jamais casser une transition de KO
        }
    }

    /** {@code "#00042 Ren Hyūga (Mika_)"} — N° et nom RP du personnage actif, puis le pseudo. */
    public static String who(ShinobiCore plugin, Player p) {
        if (p == null) return "?";
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        return c == null ? p.getName() : PlayerIds.prefix(c) + c.name() + " (" + p.getName() + ")";
    }
}
