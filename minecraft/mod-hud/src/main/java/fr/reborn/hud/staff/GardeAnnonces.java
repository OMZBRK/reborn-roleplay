package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Onglet « Annonces et effets » du Poste de garde : envoyer une <b>annonce</b> (à tout le serveur ou à un joueur, par
 * N° ou pseudo) avec aperçu de la ligne telle que les joueurs la verront, et déclencher un <b>séisme</b> (Admin).
 * Chaque formulaire est une carte de washi : libellés alignés à gauche, choix en puces, envoi à droite ; l'annonce
 * globale et le séisme demandent une confirmation. Protocole : {@code annonce}, {@code act announce}, {@code seisme}
 * (PLUGINS_REFERENCE §4 bis.2).
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
        titre = s.box(titre, "Facultatif : « Annonce », « Hokage »…", 32);
        message = s.box(message, "Le message que les joueurs liront", 160);
        cible = s.box(cible, "N° (#00042) ou pseudo", 32);
        seismeMsg = s.box(seismeMsg, "Facultatif : « La terre tremble… »", 120);
    }

    int corps(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = StaffScreen.grade(snap);
        int x = s.vx0(), w = s.vx1() - s.vx0() - 2;
        int labW = 64, cx = x + 10 + labW, cw = w - 20 - labW;

        // ── annonce ──
        boolean perso = "perso".equals(portee);
        int h = 30 + 18 + (perso ? 20 : 0) + 20 + 20 + 18 + 36 + 24;
        GardeArt.feuille(ctx, x, y, w, h);
        int cy = y + 8;
        DaKit.arcX1(ctx, s.police(), DaKit.arcade("Annonce"), x + 10, cy, DaKit.ENCRE_ROUGE, 0, false);
        s.txt8(ctx, "Une ligne discrète en haut de l'écran des joueurs. Ton nom n'y apparaît pas (il est au journal).",
                x + 10, cy + 11, w - 20, StaffScreen.INK_SOFT);
        cy += 30;
        cy = puces(ctx, "Portée", PORTEE, portee, v -> portee = v, x, cx, cy, cw, false);
        if (perso) cy = champ(ctx, "Joueur", cible, x, cx, cy, cw);
        cy = champ(ctx, "Titre", titre, x, cx, cy, cw);
        cy = champ(ctx, "Message", message, x, cx, cy, cw);
        cy = puces(ctx, "Durée", DUREE, duree, v -> duree = v, x, cx, cy, cw, false);
        // aperçu
        lib(ctx, "Aperçu", x, cy + 4);
        apercu(ctx, cx, cy, cw, perso);
        cy += 36;
        boolean pret = !message.getValue().isBlank() && (!perso || !cible.getValue().isBlank());
        int need = perso ? 1 : 2;
        String lab = perso ? "Envoyer au joueur" : "Envoyer à tous…";
        int bw = s.bwG(lab);
        s.refusVerrou = g < need ? "Réservé au grade " + StaffScreen.gradeName(need) + " et plus." : "Écris d'abord le message" + (perso ? " et le joueur." : ".");
        s.bouton(ctx, x + w - 10 - bw, cy, bw, lab, perso ? "watch" : "punish", this::envoyer, !pret || g < need);
        s.refusVerrou = null;
        y += h + 10;

        // ── séisme ──
        boolean admin = g >= 3;
        int h2 = 30 + 18 * 3 + 20 + 24 + (admin ? 0 : 0);
        GardeArt.feuille(ctx, x, y, w, h2);
        cy = y + 8;
        DaKit.arcX1(ctx, s.police(), DaKit.arcade("Séisme"), x + 10, cy, DaKit.ENCRE_ROUGE, 0, false);
        s.txt8(ctx, admin ? "Secoue la caméra des joueurs autour de toi (ou de tout le serveur). Le grondement vient du serveur."
                : "Réservé aux Admins.", x + 10, cy + 11, w - 20, admin ? StaffScreen.INK_SOFT : StaffScreen.LACQUER_LO);
        cy += 30;
        cy = puces(ctx, "Rayon", RAYON, rayon, v -> rayon = v, x, cx, cy, cw, !admin);
        cy = puces(ctx, "Force", FORCE, force, v -> force = v, x, cx, cy, cw, !admin);
        cy = puces(ctx, "Durée", SDUREE, sDuree, v -> sDuree = v, x, cx, cy, cw, !admin);
        cy = champ(ctx, "Message", seismeMsg, x, cx, cy, cw);
        int sw = s.bwG("Déclencher…");
        s.refusVerrou = "Réservé au grade Admin et plus.";
        s.bouton(ctx, x + w - 10 - sw, cy, sw, "Déclencher…", "punish", this::seisme, !admin);
        s.refusVerrou = null;
        y += h2 + 10;

        // ── mode staff ──
        s.txt8(ctx, "Le mode staff (plaques au-dessus des têtes, invisibilité) se bascule en bas de la colonne de gauche.",
                x, y, w, 0xFF8C7A5C);
        return y + 14;
    }

    private void lib(GuiGraphicsExtractor ctx, String label, int x, int y) {
        DaKit.texteEchelle(ctx, s.police(), RebornFont.body(label), x + 10, y, StaffScreen.INK_SOFT, DaKit.S8);
    }

    private int champ(GuiGraphicsExtractor ctx, String label, EditBox b, int x, int cx, int cy, int cw) {
        lib(ctx, label, x, cy + 4);
        s.placer(ctx, b, cx, cy, cw);
        return cy + 20;
    }

    private int puces(GuiGraphicsExtractor ctx, String label, String[][] choix, String actuel,
                      java.util.function.Consumer<String> set, int x, int cx, int cy, int cw, boolean verrou) {
        lib(ctx, label, x, cy + 3);
        int[] lw = new int[choix.length];
        for (int i = 0; i < lw.length; i++) lw[i] = s.bw(choix[i][1]);
        int[][] f = StaffScreen.flux(lw, cw, 4);
        for (int i = 0; i < choix.length; i++) {
            String id = choix[i][0];
            s.button(ctx, cx + f[i][0], cy + 1 + f[i][1] * 15, lw[i], choix[i][1], id.equals(actuel) ? "punish" : "plain",
                    () -> { set.accept(id); RebornSounds.uiClick(); }, verrou);
        }
        return cy + (f[f.length - 1][1] + 1) * 15 + 3;
    }

    /** Aperçu de la ligne d'annonce (mêmes proportions que le HUD), sur une bande de nuit. */
    private void apercu(GuiGraphicsExtractor ctx, int x, int y, int w, boolean perso) {
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(x, y, x + w, y + 30, 0xFF1A1418);
        l.fill(x, y, x + w, y + 1, 0xFF3A2C24);
        l.fermer();
        String t = message.getValue().isBlank() ? "Ton message apparaîtra ici." : message.getValue();
        String ti = titre.getValue().trim();
        Component cTitre = ti.isEmpty() ? null : DaKit.arcade(ti);
        float wTitre = cTitre == null ? 0 : DaKit.largeurFin(s.police(), cTitre) + 8;
        int maxT = Math.max(40, Math.round(w - 40 - wTitre));
        String tf = s.fit(t, Math.round(maxT / DaKit.S8));
        Component c = RebornFont.body(tf);
        float tw = s.police().width(c) * DaKit.S8;
        float total = 16 + wTitre + tw;
        float x0 = x + w / 2f - total / 2f;
        int sy = y + 10;
        LotGui m = LotGui.ouvrir(ctx);
        m.fill(Math.round(x0), sy, Math.round(x0) + 10, sy + 10, perso ? DaKit.OR_ETEINT : 0xFF5A0E18);
        m.fill(Math.round(x0) + 1, sy + 1, Math.round(x0) + 9, sy + 9, perso ? 0xFF1A1012 : StaffScreen.LACQUER);
        m.fill(Math.round(x0 + 16), sy + 13, Math.round(x0 + total), sy + 14, DaKit.OR_ETEINT);
        m.fermer();
        DaKit.texteEchelle(ctx, s.police(), Component.literal(perso ? "伝" : "告"), x0 + 1, sy + 1, DaKit.CREME_KJ, 1f);
        float tx = x0 + 16;
        if (cTitre != null) { DaKit.arcFin(ctx, s.police(), cTitre, tx, sy + 3.5f, DaKit.OR_CLAIR, 0, false); tx += wTitre; }
        DaKit.texteEchelle(ctx, s.police(), c, tx, sy + 2, message.getValue().isBlank() ? 0xFF8C826E : DaKit.CREME_VAL, DaKit.S8);
        if (!tf.equals(t)) s.tip(x, y, w, 30, t);
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
        s.formulaire.ouvrir("Annonce à tout le serveur ?", (ti.isEmpty() ? "" : ti + " · ") + m, "Envoyer à tous", true, null, List.of(), v -> {
            JsonObject o = new JsonObject();
            o.addProperty("a", "annonce");
            o.addProperty("m", m);
            if (!ti.isEmpty()) o.addProperty("titre", ti);
            o.addProperty("s", sec);
            StaffClient.send(o);
            message.setValue("");
        });
    }

    /** UUID d'un joueur en ligne par pseudo ou nom RP (liste du Poste de garde). */
    private String uuidDe(String q) {
        JsonObject snap = StaffClient.snapshot();
        for (var e : StaffScreen.arr(snap, "players")) {
            JsonObject p = e.getAsJsonObject();
            if (StaffScreen.str(p, "name").equalsIgnoreCase(q) || StaffScreen.str(p, "perso").equalsIgnoreCase(q)) return StaffScreen.str(p, "uuid");
        }
        return null;
    }

    private void seisme() {
        String r = rayon, f = force, d = sDuree, m = seismeMsg.getValue().trim();
        String portee = "0".equals(r) ? "tout le serveur" : "les joueurs à " + r + " blocs de toi";
        s.formulaire.ouvrir("Déclencher un séisme ?", "Secousse " + labelDe(FORCE, f).toLowerCase() + " de " + d + " s pour " + portee + ".",
                "Déclencher", true, null, List.of(), v -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("a", "seisme");
                    o.addProperty("r", Integer.parseInt(r));
                    o.addProperty("f", Integer.parseInt(f));
                    o.addProperty("s", Integer.parseInt(d));
                    if (!m.isEmpty()) o.addProperty("m", m);
                    StaffClient.send(o);
                });
    }

    private static String labelDe(String[][] t, String id) {
        for (String[] r : t) if (r[0].equals(id)) return r[1];
        return id;
    }

    /* banc */
    void debugRemplir(boolean perso, String ti, String m, String c) {
        portee = perso ? "perso" : "global";
        titre.setValue(ti);
        message.setValue(m);
        cible.setValue(c);
    }

    void debugEnvoyer() { envoyer(); }
}
