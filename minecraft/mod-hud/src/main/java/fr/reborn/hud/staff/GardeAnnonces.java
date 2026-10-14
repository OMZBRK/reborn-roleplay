package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Outils « Annonces » et « Séisme » du volet : annonce à tout le serveur ou à un joueur (N° ou pseudo), avec un aperçu
 * de la ligne telle que les joueurs la verront ; séisme (Admin). L'annonce globale et le séisme demandent une
 * confirmation dans le volet. Protocole : {@code annonce}, {@code act announce}, {@code seisme} (PLUGINS_REFERENCE
 * §4 bis.2).
 */
final class GardeAnnonces {

    private static final String[][] PORTEE = {{"global", "Tout le serveur"}, {"perso", "Un joueur"}};
    private static final String[][] DUREE = {{"5", "5 s"}, {"8", "8 s"}, {"15", "15 s"}, {"30", "30 s"}};
    private static final String[][] RAYON = {{"30", "30 blocs"}, {"100", "100 blocs"}, {"0", "Tout le serveur"}};
    private static final String[][] FORCE = {{"3", "Légère"}, {"5", "Moyenne"}, {"8", "Forte"}};
    private static final String[][] SDUREE = {{"2", "2 s"}, {"4", "4 s"}, {"8", "8 s"}};

    private final StaffScreen s;
    private EditBox titre, message, cible, seismeMsg;
    private String portee = "global", duree = "8", rayon = "30", force = "5", sDuree = "4";

    GardeAnnonces(StaffScreen s) { this.s = s; }

    void init() {
        titre = s.box(titre, "Facultatif : Annonce, Hokage…", 32);
        message = s.box(message, "Le message que les joueurs liront", 160);
        cible = s.box(cible, "#00042 ou pseudo", 32);
        seismeMsg = s.box(seismeMsg, "Facultatif : La terre tremble…", 120);
    }

    private int choix(String label, String[][] ch, String actuel, java.util.function.Consumer<String> set, int y, boolean verrou) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte(label, ix, y + 3, 12, GRIS, false);
        float ox = ix + 36;
        for (String[] c : ch) {
            float w = pz.largeur(c[1], 13, true);
            if (ox + w > ix + iw) { ox = ix + 36; y += 12; }
            if (verrou) ox += pz.texte(c[1], ox, y + 3, 13, GRIS_3, false) + 9;
            else ox += s.mot(c[1], ox, y + 3, 13, c[0].equals(actuel), () -> set.accept(c[0])) + 9;
        }
        return y + 15;
    }

    private int champ(String label, EditBox b, int y) {
        s.pz.texte(label, s.ix(), y + 3, 12, GRIS, false);
        s.champ(b, s.ix(), y + 8, s.iw(), null);
        return y + 31;
    }

    int annonces(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int g = StaffScreen.grade(snap);
        int ix = s.ix(), iw = s.iw();
        boolean perso = "perso".equals(portee);
        for (String l : s.wrap("Une ligne discrète en haut de l'écran des joueurs. Ton nom n'y apparaît pas (il est au journal).", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 5;
        y = choix("Portée", PORTEE, portee, v -> portee = v, y, false);
        if (perso) y = champ("Joueur", cible, y);
        y = champ("Titre", titre, y);
        y = champ("Message", message, y);
        y = choix("Durée", DUREE, duree, v -> duree = v, y, false);
        // aperçu de la ligne des joueurs (mêmes proportions que le HUD)
        pz.texte("Aperçu", ix, y + 3, 12, GRIS, false);
        y += 8;
        pz.fond(ix, y, ix + iw, y + 22, 0xFF0A0708);
        pz.cadre(ix, y, iw, 22, FILET);
        String t = message.getValue().isBlank() ? "Ton message apparaîtra ici." : message.getValue();
        String ti = titre.getValue().trim();
        float wt = ti.isEmpty() ? 0 : pz.largeur(DaKit.sansAccents(ti), 11, true) + 6;
        String tf = s.fit(t, iw - 22 - wt, 13, false);
        float tw = pz.largeur(tf, 13, false);
        float x0 = ix + iw / 2f - (8 + wt + tw) / 2f;
        pz.fond(Math.round(x0), y + 8, Math.round(x0) + 4, y + 12, perso ? OR_DOUX : LAQUE);
        float tx = x0 + 8;
        if (!ti.isEmpty()) { pz.capitales(ti, tx, y + 10, 11, OR); tx += wt; }
        pz.texte(tf, tx, y + 10, 13, message.getValue().isBlank() ? GRIS_2 : CREME, false);
        pz.filetH(x0 + 8, x0 + 8 + wt + tw, y + 16, OR_DOUX);
        y += 28;
        boolean pret = !message.getValue().isBlank() && (!perso || !cible.getValue().isBlank());
        int need = perso ? 1 : 2;
        String lab = perso ? "Envoyer au joueur" : "Envoyer à tous…";
        int bw = s.largeurBouton(lab);
        s.refusVerrou = g < need ? "Réservé au grade " + StaffScreen.gradeName(need) + " et plus." : "Écris d'abord le message" + (perso ? " et le joueur." : ".");
        s.bouton(ix + iw - bw, y, bw, lab, true, this::envoyer, !pret || g < need);
        s.refusVerrou = null;
        return y + 23;
    }

    int seisme(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int g = StaffScreen.grade(snap);
        int ix = s.ix(), iw = s.iw();
        boolean admin = g >= 3;
        for (String l : s.wrap(admin ? "Secoue la caméra des joueurs autour de toi, ou de tout le serveur. Le grondement vient du serveur."
                : "Réservé aux Admins.", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, admin ? GRIS_2 : ROUGE, false);
            y += 9;
        }
        y += 5;
        y = choix("Rayon", RAYON, rayon, v -> rayon = v, y, !admin);
        y = choix("Force", FORCE, force, v -> force = v, y, !admin);
        y = choix("Durée", SDUREE, sDuree, v -> sDuree = v, y, !admin);
        y = champ("Message", seismeMsg, y);
        int bw = s.largeurBouton("Déclencher…");
        s.refusVerrou = "Réservé au grade Admin et plus.";
        s.bouton(ix + iw - bw, y, bw, "Déclencher…", true, this::seisme, !admin);
        s.refusVerrou = null;
        return y + 23;
    }

    private void envoyer() {
        String m = message.getValue().trim(), ti = titre.getValue().trim();
        if (m.isEmpty()) return;
        int sec = Integer.parseInt(duree);
        if ("perso".equals(portee)) {
            String c = cible.getValue().trim();
            if (c.isEmpty()) return;
            JsonObject o = new JsonObject();
            o.addProperty("a", "act");
            o.addProperty("op", "announce");
            String d = c.startsWith("#") ? c.substring(1) : c;
            if (d.chars().allMatch(Character::isDigit)) o.addProperty("pid", d);
            else {
                String uuid = uuidDe(c);
                if (uuid == null) { StaffClient.toast("Aucun joueur en ligne ne s'appelle « " + c + " »."); RebornSounds.deny(); return; }
                o.addProperty("t", uuid);
            }
            o.addProperty("m", m);
            o.addProperty("s", sec);
            StaffClient.send(o);
            message.setValue("");
            RebornSounds.confirm();
            return;
        }
        s.formulaire.ouvrir("annonce", "Annonce à tout le serveur ?", (ti.isEmpty() ? "" : ti + " · ") + m, "Envoyer à tous", true, null,
                List.of(), List.of("Tous les joueurs la verront, inscrite au journal."), v -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("a", "annonce");
                    o.addProperty("m", m);
                    if (!ti.isEmpty()) o.addProperty("titre", ti);
                    o.addProperty("s", sec);
                    StaffClient.send(o);
                    message.setValue("");
                });
    }

    private String uuidDe(String q) {
        for (var e : StaffScreen.arr(StaffClient.snapshot(), "players")) {
            JsonObject p = e.getAsJsonObject();
            if (StaffScreen.str(p, "name").equalsIgnoreCase(q) || StaffScreen.str(p, "perso").equalsIgnoreCase(q)) return StaffScreen.str(p, "uuid");
        }
        return null;
    }

    private void seisme() {
        String r = rayon, f = force, d = sDuree, m = seismeMsg.getValue().trim();
        String portee = "0".equals(r) ? "tout le serveur" : "les joueurs à " + r + " blocs de toi";
        String lf = "Moyenne";
        for (String[] x : FORCE) if (x[0].equals(f)) lf = x[1];
        s.formulaire.ouvrir("seisme", "Déclencher un séisme ?", "Secousse " + lf.toLowerCase() + " de " + d + " s pour " + portee + ".",
                "Déclencher", true, null, List.of(), List.of(), v -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("a", "seisme");
                    o.addProperty("r", Integer.parseInt(r));
                    o.addProperty("f", Integer.parseInt(f));
                    o.addProperty("s", Integer.parseInt(d));
                    if (!m.isEmpty()) o.addProperty("m", m);
                    StaffClient.send(o);
                });
    }

    void debugRemplir(boolean perso, String ti, String m, String c) {
        portee = perso ? "perso" : "global";
        titre.setValue(ti);
        message.setValue(m);
        cible.setValue(c);
    }

    void debugEnvoyer() { envoyer(); }
}
