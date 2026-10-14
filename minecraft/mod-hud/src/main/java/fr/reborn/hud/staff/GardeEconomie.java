package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Section « Économie » de la fiche joueur du Poste de garde (maquette 06, MONNAIE_ET_BANQUE.md §11, 15/10) : <b>une
 * ligne, pas un écran</b>. Aperçu « Bourse 1 240 · compte 8 600 · coffre 4/6 » ; dépliée : bourse, compte et palier,
 * coffre, solde de la semaine, dernier mouvement, gel ; puis des mots — Donner… · Retirer… · Voir le coffre · Geler le
 * compte · Journal économie ›. Les formulaires s'ouvrent dans le volet ; tout passe par {@code /ryo} et {@code /banque}
 * (motif obligatoire, journalisé côté serveur).
 */
final class GardeEconomie {

    private GardeEconomie() {}

    private static String fmt(long n) { return String.format("%,d", n).replace(',', ' '); }

    private static JsonObject eco(JsonObject p) {
        return p != null && p.has("eco") && p.get("eco").isJsonObject() ? p.getAsJsonObject("eco") : null;
    }

    static String apercu(JsonObject p) {
        JsonObject e = eco(p);
        if (e == null) return "…";
        return "Bourse " + fmt(StaffScreen.num(e, "bourse")) + " · compte " + fmt(StaffScreen.num(e, "compte")) + " · coffre "
            + StaffScreen.num(e, "occupees") + "/" + StaffScreen.num(e, "cases") + (e.has("gele") ? " · gelé" : "");
    }

    private static void cmd(String c) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(c);
    }

    private static String ref(JsonObject p) {
        String pid = StaffScreen.str(p, "pid");
        return pid.isEmpty() ? StaffScreen.str(p, "name") : "#" + pid;
    }

    private static String q(String s) { return "\"" + s.replace("\"", "'") + "\""; }

    static int contenu(StaffScreen s, JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        JsonObject e = eco(p);
        int x = s.ix() + 13, w = s.iw() - 13;
        if (e == null) { pz.texte("Pas de personnage actif.", x, y + 4, 12, GRIS_2, false); return y + 10; }
        float vx = x + 44;
        y = ligne(pz, s, "Bourse", fmt(StaffScreen.num(e, "bourse")) + " ryo", "sur lui", x, vx, w, y);
        String pl = "palier " + StaffScreen.num(e, "palier") + (StaffScreen.num(e, "plafond") > 0 ? " · plafond " + fmt(StaffScreen.num(e, "plafond")) : " · sans plafond");
        y = ligne(pz, s, "Compte", fmt(StaffScreen.num(e, "compte")) + " ryo", pl, x, vx, w, y);
        String co = StaffScreen.str(e, "coffre");
        y = ligne(pz, s, "Coffre", StaffScreen.num(e, "occupees") + " / " + StaffScreen.num(e, "cases"), co.isEmpty() ? "vide" : co, x, vx, w, y);
        y = ligne(pz, s, "Solde", fmt(StaffScreen.num(e, "solde")) + " / semaine", StaffScreen.str(e, "grade")
            + (StaffScreen.bool(e, "soldeRecue") ? " · reçue dimanche" : ""), x, vx, w, y);
        if (e.has("dernier")) {
            long d = StaffScreen.num(e, "dernier");
            y = ligne(pz, s, "Dernier", (d >= 0 ? "+" : "−") + fmt(Math.abs(d)), StaffScreen.str(e, "dernierTexte"), x, vx, w, y);
        }
        if (e.has("gele")) y = ligne(pz, s, "Gelé", "oui", StaffScreen.str(e, "gele"), x, vx, w, y);
        if (e.has("testament")) y = ligne(pz, s, "Testament", StaffScreen.str(e, "testament"), "", x, vx, w, y);
        y += 3;
        pz.filetH(x, x + w, y, SEP);
        y += 8;
        float cx = x;
        String nom = StaffScreen.name(p);
        cx += s.motAction("Donner…", cx, y, true, () -> formulaire(s, p, "donner", nom)) + 9;
        cx += s.motAction("Retirer…", cx, y, false, () -> formulaire(s, p, "retirer", nom)) + 9;
        cx += s.motAction("Voir le coffre", cx, y, false, () -> cmd("banque coffre " + ref(p))) + 9;
        boolean gele = e.has("gele");
        s.motAction(gele ? "Dégeler le compte" : "Geler le compte", cx, y, false, () -> gel(s, p, !gele, nom));
        y += 13;
        s.motAction("Journal économie ›", x, y, true, () -> {
            String pid = StaffScreen.str(p, "pid");
            if (pid.isEmpty()) { StaffClient.toast("Pas de N° : aucun personnage actif."); return; }
            s.journalVue.filtrerPid(pid, nom);
            s.switchTab("journal");
        });
        return y + 8;
    }

    private static int ligne(GardeArt.Pinceau pz, StaffScreen s, String lab, String val, String detail, float x, float vx, int w, int y) {
        pz.texte(lab, x, y + 4, 12, GRIS, false);
        float vw = pz.texte(val, vx, y + 4, 13, OR_CLAIR, true);
        if (detail != null && !detail.isEmpty()) s.txt(detail, vx + vw + 6, y + 4, x + w - (vx + vw + 6), 12, GRIS_2, false);
        return y + 11;
    }

    private static void formulaire(StaffScreen s, JsonObject p, String op, String nom) {
        s.formulaire.ouvrir("noter", (op.equals("donner") ? "Donner des ryo à " : "Retirer des ryo à ") + nom,
            "Sur la bourse (joueur en ligne) ou le compte. Motif obligatoire.", op.equals("donner") ? "Donner" : "Retirer", false, null,
            List.of(GardeFormulaire.Champ.texte("m", "Montant", "500", true),
                GardeFormulaire.Champ.choix("o", "Sur", new String[][]{{"compte", "compte"}, {"bourse", "bourse"}}, 0),
                GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "récompense mission C (Tora)", true)),
            List.of("Journalisé (catégorie Économie), visible des autres staffs."),
            v -> cmd("ryo " + op + " " + ref(p) + " " + v.get("m").replaceAll("[^0-9]", "") + " " + v.get("o") + " " + q(v.get("motif"))));
    }

    private static void gel(StaffScreen s, JsonObject p, boolean geler, String nom) {
        s.formulaire.ouvrir("geler", (geler ? "Geler le compte de " : "Dégeler le compte de ") + nom,
            geler ? "Plus de dépôt, retrait, virement ni coffre tant que le gel dure." : "Le compte redevient utilisable au guichet.",
            geler ? "Geler" : "Dégeler", geler, null,
            List.of(GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "enquête sur un vol", true)),
            List.of("Journalisé (catégorie Économie)."),
            v -> cmd("banque " + (geler ? "geler " : "degeler ") + ref(p) + " " + q(v.get("motif"))));
    }
}
