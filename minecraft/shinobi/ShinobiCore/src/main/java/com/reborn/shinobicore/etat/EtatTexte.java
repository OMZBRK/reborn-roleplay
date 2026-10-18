package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.etat.Cadre;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Principal;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.ata.AtaManager;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>Un seul constructeur</b> du résumé d'état lisible (docs/technique/ETATS_JOUEUR.md §2.4 n° 13, §7.4) : plaques du
 * mode staff ({@code ModeStaff.etat}) et fiche du Poste de garde ({@code StaffPanel.stateOf}) disaient deux vérités.
 * Désormais : l'état principal résolu par le service, puis ce que le staff veut voir en plus (ATA et repos, duel,
 * prison, gel, muet, écran noir, animation).
 */
public final class EtatTexte {

    private EtatTexte() { }

    /**
     * @param detail vrai pour la fiche (repos de l'ATA, minutes de prison), faux pour les plaques (court)
     */
    public static String resume(ShinobiCore plugin, UUID id, ShinobiCharacter c, boolean detail) {
        List<String> parts = new ArrayList<>();
        EtatServiceImpl s = plugin.etats();
        boolean enLigne = Bukkit.getPlayer(id) != null;
        if (s != null && enLigne) {
            Etat e = s.calculer(id);
            if (e.principal() != Principal.LIBRE && e.principal() != Principal.HORS_JEU
                    && e.principal() != Principal.MODE_STAFF && e.principal() != Principal.PRISON) {
                parts.add(e.principal().libelle());
            }
            if (e.cadre() == Cadre.DUEL) parts.add("Duel");
            if (e.cadre() == Cadre.PRISON) {
                var pe = s.peine(id);
                parts.add(detail && pe != null ? "Prison · " + (pe.resteMs() / 60_000 + 1) + " min" : "Prison");
            }
            if (e.a("menottes")) parts.add("Menotté");
        } else {
            KoState ko = plugin.ko() != null ? plugin.ko().getKo(id) : null;
            if (ko != null) parts.add(ko.isDowned() ? "À terre" : "Inconscient");
        }
        if (c != null && plugin.ata() != null) {
            AtaManager.View v = plugin.ata().view(c.id());
            if (v != null) parts.add(!detail ? "ATA" : "ATA " + (v.level() == AtaManager.Level.PLEINE ? "pleine" : "allégée")
                    + " · repos " + v.restMinutes() + "/" + v.requiredMinutes());
        }
        var o = plugin.outilsStaff();
        if (o != null && o.gels().gele(id)) parts.add("Gelé");
        if (detail && plugin.staffPanel() != null && plugin.staffPanel().mutedUntil(id) > System.currentTimeMillis())
            parts.add("Muet");
        if (o != null && o.effets().enNoir(id)) parts.add("Écran noir");
        if (o != null && o.animation().actif(id)) parts.add("Animation");
        if (c == null && enLigne) parts.add("Sans personnage");
        return String.join(" · ", parts);
    }
}
