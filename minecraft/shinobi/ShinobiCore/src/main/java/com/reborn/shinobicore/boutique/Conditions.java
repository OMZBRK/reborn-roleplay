package com.reborn.shinobicore.boutique;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.Entreprise;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Conditions d'un article (BOUTIQUES.md §4, décision 7) : grade, fonction, entreprise non remplis → <b>grisé avec la
 * raison</b> (on peut l'essayer, pas l'acheter) ; autre clan, autre village, autre sexe, pièce unique d'un autre
 * personnage → <b>caché</b>. Syntaxe : {@code grade>=chunin}, {@code grade=jonin}, {@code fonction=anbu} (permission
 * {@code reborn.fonction.anbu}), {@code entreprise=<id>}, {@code clan=uchiha}, {@code village=konoha},
 * {@code sexe=femme}, {@code personnage=#00042}.
 */
final class Conditions {

    /** Résultat : caché, ou verrou (titre + raison), ou libre (verrou null). */
    record Verdict(boolean cache, String verrou, String raison) {
        static final Verdict LIBRE = new Verdict(false, null, null);
    }

    private Conditions() {}

    static Verdict evaluer(ShinobiCore plugin, Player p, ShinobiCharacter c, List<String> conds) {
        if (conds == null || conds.isEmpty()) return Verdict.LIBRE;
        Verdict grise = null;
        for (String raw : conds) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();
            String op = s.contains(">=") ? ">=" : s.contains("=") ? "=" : null;
            if (op == null) continue;
            int i = s.indexOf(op);
            String cle = norm(s.substring(0, i)), val = s.substring(i + op.length()).trim();
            switch (cle) {
                case "clan" -> { if (!egal(c.clan(), val)) return new Verdict(true, null, null); }
                case "village" -> { if (!egal(c.village(), val)) return new Verdict(true, null, null); }
                case "sexe", "genre" -> { if (!egal(c.sexe(), val) && !egal(c.sexe(), val + "e")) return new Verdict(true, null, null); }
                case "personnage", "perso" -> {
                    int n = PlayerIds.parse(val);
                    if (n < 0 || !c.hasPlayerId() || c.playerId() != n) return new Verdict(true, null, null);
                }
                case "grade" -> {
                    Rank voulu = Rank.from(val);
                    Rank r = c.rank();
                    boolean ok = ">=".equals(op) ? r.ordinal() >= voulu.ordinal() : r == voulu;
                    if (!ok && grise == null) {
                        String t = ">=".equals(op) && voulu != Rank.KAGE
                                ? "Réservé aux " + voulu.displayName() + " et au-dessus" : "Réservé aux " + voulu.displayName();
                        grise = new Verdict(false, t, "Ton grade : " + r.displayName());
                    }
                }
                case "fonction" -> {
                    if (!p.hasPermission("reborn.fonction." + norm(val)) && grise == null) {
                        grise = new Verdict(false, "Réservé à la fonction " + titre(val), "Ce n'est pas ta fonction");
                    }
                }
                case "entreprise" -> {
                    boolean membre = false;
                    String nom = val;
                    var es = plugin.nourriture() == null ? null : plugin.nourriture().entreprises();
                    if (es != null) {
                        Entreprise e = es.trouver(val);
                        if (e != null) { nom = e.nom(); membre = e.membre(c.id()) != null; }
                    }
                    if (!membre && grise == null) grise = new Verdict(false, "Réservé à « " + nom + " »", "Tu n'en fais pas partie");
                }
                default -> { }
            }
        }
        return grise != null ? grise : Verdict.LIBRE;
    }

    /** Une condition de grade ou de fonction pour la fiche staff (« grade ≥ Chūnin »). */
    static String court(List<String> conds) {
        if (conds == null || conds.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String s : conds) { if (!b.isEmpty()) b.append(", "); b.append(s.replace(">=", " ≥ ").replace("=", " ")); }
        return b.toString();
    }

    private static boolean egal(String a, String b) { return norm(a).equals(norm(b)); }

    static String norm(String s) {
        return s == null ? "" : Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String titre(String s) {
        String n = s.trim();
        return n.length() <= 4 ? n.toUpperCase(Locale.ROOT) : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
