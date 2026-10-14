package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Onglet Outils du volet : la liste des outils (comme les sections repliées de la fiche : icône, titre, compteur, une
 * ligne d'aperçu) puis chaque outil dans le volet avec « < Outils » : Annonces, Séisme (Admin), Chat staff,
 * Commandes, l'<b>Infirmerie</b> (Blessés, Zones, Hôpitaux, Réglages du KO — {@link GardeScreen}, la carte s'ouvre dans
 * le volet élargi) et les <b>modes</b> (mode staff : aucune mécanique RP ; mode animation : jouer pour un event).
 */
class GardeOutils {

    /** {id, icône, titre} ; null = séparateur avec titre {@code ["", "", "TITRE"]}. */
    static final String[][] LISTE = {
            {"", "", "Communication"}, {"annonces", "annonce", "Annonces"}, {"chat", "chat", "Chat staff"},
            {"seisme", "seisme", "Séisme"},
            {"", "", "Infirmerie"}, {"blesses", "infirmerie", "Blessés"}, {"zones", "zones", "Zones d'entraînement et de repos"},
            {"hopitaux", "hopital", "Hôpitaux et lits"}, {"reglages", "reglages", "Réglages du KO"},
            {"", "", "Modes et aide"}, {"modes", "bouclier", "Mode staff et mode animation"}, {"cmds", "commandes", "Commandes"}};

    protected final StaffScreen s;
    private EditBox chatBox, cmdBox;

    GardeOutils(StaffScreen s) { this.s = s; }

    void init() {
        chatBox = s.box(chatBox, "Écrire au staff…", 200);
        cmdBox = s.box(cmdBox, "Chercher : ata, zone, météo…", 40);
    }

    String titre(String id) {
        for (String[] l : LISTE) if (l[0].equals(id)) return l[2];
        return id;
    }

    int hauteurOutils() { return s.outil == null ? 0 : "cmds".equals(s.outil) ? 38 : 16; }

    boolean pleineHauteur() { return s.outil != null && ("chat".equals(s.outil) || "cmds".equals(s.outil) || "blesses".equals(s.outil)); }

    String pied() {
        if (s.outil == null) return "Clic : ouvrir l'outil";
        return switch (s.outil) {
            case "chat" -> null;
            case "cmds" -> "Clic : copier la commande, à coller dans le tchat";
            default -> "Échap : retour aux outils";
        };
    }

    /** Zone fixe : « < Outils » + titre ; recherche des commandes. */
    void outils(JsonObject snap, int y) {
        if (s.outil == null) return;
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        boolean hov = s.hover(ix - 3, y - 2, 40, 12);
        pz.icone("gauche", ix - 2, y + 0.5f, 8, hov ? CREME : GRIS);
        float w = pz.texte("Outils", ix + 7, y + 4.5f, 13, hov ? CREME : GRIS, false);
        s.hits.add(new StaffScreen.Hit(ix - 3, y - 2, Math.round(w) + 12, 12, () -> { s.outil = null; s.scroll = 0; s.formulaire.fermer(); RebornSounds.uiClick(); }, null, null));
        pz.texte("·", ix + 11 + w, y + 4.5f, 13, GRIS_2, false);
        s.txt(titre(s.outil), ix + 17 + w, y + 4.5f, iw - 17 - w, 13, CREME, true);
        if ("cmds".equals(s.outil)) s.champ(cmdBox, ix, y + 16, iw, "chercher");
        pz.filetH(ix, ix + iw, y + (hauteurOutils() - 0.5f), FILET);
    }

    int corps(JsonObject snap, int y) {
        if (s.outil == null) return liste(snap, y);
        return switch (s.outil) {
            case "annonces" -> s.annoncesVue.annonces(snap, y + 2);
            case "seisme" -> s.annoncesVue.seisme(snap, y + 2);
            case "chat" -> chat(snap, y);
            case "cmds" -> commandes(snap, y);
            case "modes" -> modes(snap, y + 2);
            default -> outilKo(snap, y);
        };
    }

    /** Outils de l'Infirmerie (surchargés par {@link GardeScreen}). */
    protected int outilKo(JsonObject snap, int y) {
        if (s instanceof GardeScreen g) return g.outilKo(snap, y);
        return s.vide(y, "Infirmerie indisponible.", null);
    }

    /** Compteur et aperçu d'un outil de la liste. */
    protected Object[] resume(String id, JsonObject snap) {
        return switch (id) {
            case "annonces" -> new Object[]{0, "À tout le serveur ou à un joueur, avec aperçu"};
            case "chat" -> {
                JsonArray c = StaffScreen.arr(snap, "chat");
                String last = c.isEmpty() ? "Aucun message" : StaffScreen.str(c.get(c.size() - 1).getAsJsonObject(), "name") + " : "
                        + StaffScreen.str(c.get(c.size() - 1).getAsJsonObject(), "text");
                yield new Object[]{StaffScreen.num(snap, "staffOnline"), last};
            }
            case "seisme" -> new Object[]{0, StaffScreen.grade(snap) >= 3 ? "Secousse autour de toi ou partout" : "Réservé aux Admins"};
            case "modes" -> new Object[]{0, StaffScreen.bool(snap, "modeAnim") ? "Mode animation actif"
                    : StaffScreen.bool(snap, "modeStaff") ? "Mode staff actif : aucune mécanique RP" : "Hors mode staff"};
            case "cmds" -> new Object[]{0, "Aide-mémoire, grade requis, copier en un clic"};
            default -> s instanceof GardeScreen g ? g.resumeKo(id) : new Object[]{0, ""};
        };
    }

    private int liste(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        for (String[] l : LISTE) {
            if (l[0].isEmpty()) {
                y += 4;
                pz.capitales(l[2], ix, y + 4, 11, GRIS_2);
                y += 10;
                continue;
            }
            Object[] r = resume(l[0], snap);
            int n = (int) r[0];
            int h = 25;
            boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
            if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            pz.icone(l[1], ix, y + 7, 8, hov ? OR : GRIS);
            float lw = s.txt(l[2], ix + 13, y + 7.5f, iw - 40, 14, CREME, false);
            if (n > 0) pz.texte(String.valueOf(n), ix + 16 + lw, y + 7.5f, 12, "blesses".equals(l[0]) ? ROUGE : OR, true);
            s.txt((String) r[1], ix + 13, y + 17, iw - 26, 12, GRIS_2, false);
            pz.icone("droite", ix + iw - 7, y + 8.5f, 7, GRIS_2);
            String id = l[0];
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> ouvrir(id), null, null));
            y += h;
            pz.filetH(ix + 13, ix + iw, y - 0.5f, SEP);
        }
        return y + 2;
    }

    protected void ouvrir(String id) {
        s.outil = id;
        s.scroll = "chat".equals(id) ? Integer.MAX_VALUE / 2 : 0;
        if ("chat".equals(id)) s.setFocused(chatBox);
        if ("cmds".equals(id)) s.setFocused(cmdBox);
        RebornSounds.uiClick();
    }

    /* ---------------------------------------------------------------- chat staff */

    private int chat(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        JsonArray lines = StaffScreen.arr(snap, "chat");
        if (lines.isEmpty()) y = s.vide(y, "Aucun message pour l'instant.", "Écris au staff ci-dessous, ou en jeu avec # au début d'un message.");
        int start = Math.max(0, lines.size() - 60);
        for (int i = start; i < lines.size(); i++) {
            JsonObject m = lines.get(i).getAsJsonObject();
            boolean sys = StaffScreen.bool(m, "sys");
            float x = ix;
            x += pz.texte(StaffScreen.str(m, "time"), x, y + 4, 11, GRIS_3, false) + 4;
            String auteur = sys ? "Système" : StaffScreen.str(m, "name");
            pz.texte(auteur, x, y + 4, 12, sys ? ROUGE : StaffScreen.gradeColor(StaffScreen.num(m, "grade")), true);
            y += 9;
            for (String l : s.wrap(StaffScreen.str(m, "text"), iw, 13)) { pz.texte(l, ix, y + 4, 13, sys ? GRIS : CREME, false); y += 9; }
            y += 4;
        }
        // saisie, posée au bas du volet (hors du corps qui défile)
        return y;
    }

    /** Saisie du chat au pied du volet (appelée par l'écran après le corps). */
    void piedChat() {
        if (!"outils".equals(s.tab) || !"chat".equals(s.outil) || s.formulaire.actif()) return;
        int y = s.py1() - 23, ix = s.ix(), iw = s.iw();
        s.pz.filetH(ix, ix + iw, y - 4, FILET);
        int bw = 44;
        s.champ(chatBox, ix, y, iw - bw - 4, null);
        s.bouton(ix + iw - bw, y, bw, "Envoyer", true, this::envoyerChat, false);
    }

    private void envoyerChat() {
        String m = chatBox.getValue().trim();
        if (m.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "chat");
        o.addProperty("m", m);
        StaffClient.send(o);
        chatBox.setValue("");
        s.scroll = Integer.MAX_VALUE / 2;
    }

    boolean entree() {
        if ("outils".equals(s.tab) && "chat".equals(s.outil) && chatBox.isFocused()) { envoyerChat(); return true; }
        return false;
    }

    /* ---------------------------------------------------------------- commandes */

    private int commandes(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        int g = StaffScreen.grade(snap);
        String q = cmdBox.getValue().trim().toLowerCase();
        String cat = null;
        int shown = 0;
        for (JsonElement e : StaffScreen.arr(snap, "commands")) {
            JsonObject c = e.getAsJsonObject();
            String cmd = StaffScreen.str(c, "cmd"), desc = StaffScreen.str(c, "desc");
            if (!q.isEmpty() && !(cmd + " " + desc).toLowerCase().contains(q)) continue;
            if (!StaffScreen.str(c, "cat").equals(cat)) {
                cat = StaffScreen.str(c, "cat");
                y += shown > 0 ? 4 : 0;
                pz.capitales(cat, ix, y + 4, 11, GRIS_2);
                y += 10;
            }
            shown++;
            int need = StaffScreen.num(c, "grade");
            boolean ok = need <= g;
            int h = 21;
            boolean hov = ok && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
            if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            String gl = StaffScreen.gradeName(need) + (need < 4 ? "+" : "");
            float gw = pz.largeur(gl, 11, false) + 6;
            int gc = ok ? StaffScreen.gradeColor(need) : GRIS_3;
            pz.cadre(ix + iw - gw, y + 2, gw, 9, GardeArt.sombre(gc, 0.6f));
            pz.texteC(gl, ix + iw - gw / 2f, y + 6.5f, 11, gc, false);
            s.txt(cmd, ix, y + 6.5f, iw - gw - 6, 13, ok ? CREME : GRIS_3, true);
            s.txt(desc, ix, y + 15.5f, iw, 12, ok ? GRIS : GRIS_3, false);
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, ok ? () -> copier(cmd) : null, null,
                    "Réservé au grade " + StaffScreen.gradeName(need) + " et plus."));
            y += h;
            pz.filetH(ix, ix + iw, y - 0.5f, SEP);
        }
        if (shown == 0) y = s.vide(y, q.isEmpty() ? "Aucune commande." : "Aucune commande ne correspond à « " + q + " ».", null);
        return y + 2;
    }

    private void copier(String cmd) {
        Minecraft.getInstance().keyboardHandler.setClipboard(cmd.startsWith("#") ? "#" : cmd);
        StaffClient.toast(cmd + " · copiée, colle-la dans le tchat (Ctrl+V)");
        RebornSounds.uiClick();
    }

    /* ---------------------------------------------------------------- modes */

    private int modes(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        boolean staff = StaffScreen.bool(snap, "modeStaff"), anim = StaffScreen.bool(snap, "modeAnim");
        boolean animOk = StaffScreen.bool(snap, "animPossible");
        // mode staff
        pz.icone("bouclier", ix, y + 1, 8, staff ? OK : GRIS);
        pz.texte("Mode staff", ix + 13, y + 5, 15, CREME, true);
        pz.texteD(staff ? "actif" : "inactif", ix + iw, y + 5, 12, staff ? OK : GRIS_2, false);
        y += 13;
        for (String l : List.of("Invisible aux joueurs, vol, invulnérable, plaques « N° · nom · état » au-dessus des têtes.",
                "Aucune mécanique RP : ni coup, ni technique, ni menu d'interaction, ni duel, ni ramassage, ni objet, ni bloc RP, ni monture, ni tchat RP (tes messages vont au chat staff). Personne ne peut te cibler.")) {
            for (String li : s.wrap(l, iw - 13, 12)) { pz.texte(li, ix + 13, y + 3, 12, GRIS_2, false); y += 9; }
            y += 2;
        }
        y += 3;
        int bw = s.largeurBouton(staff ? "Couper le mode staff" : "Passer en mode staff");
        s.bouton(ix + iw - bw, y, bw, staff ? "Couper le mode staff" : "Passer en mode staff", !staff, () -> {
            StaffClient.send("modestaff");
            RebornSounds.uiClick();
        }, StaffScreen.grade(snap) < 1);
        y += 25;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 8;
        // mode animation
        pz.icone("masque", ix, y + 1, 8, anim ? ANIM : GRIS);
        pz.texte("Mode animation", ix + 13, y + 5, 15, CREME, true);
        pz.texteD(anim ? "actif" : "inactif", ix + iw, y + 5, 12, anim ? ANIM : GRIS_2, false);
        y += 13;
        for (String l : List.of("Animateur ou MJ : tu incarnes un personnage pour un event. Visible des joueurs, mécaniques RP permises.",
                "Tout est journalisé (commandes, tchat, coups). Les autres staffs te voient « Animation ». Commande : /modeanim.")) {
            for (String li : s.wrap(l, iw - 13, 12)) { pz.texte(li, ix + 13, y + 3, 12, GRIS_2, false); y += 9; }
            y += 2;
        }
        y += 3;
        String lab = anim ? "Couper le mode animation" : "Passer en mode animation";
        bw = s.largeurBouton(lab);
        s.refusVerrou = "Réservé aux animateurs et MJ (permission reborn.staff.animation).";
        s.bouton(ix + iw - bw, y, bw, lab, !anim, () -> { StaffClient.send("modeanim"); RebornSounds.uiClick(); }, !animOk && !anim);
        s.refusVerrou = null;
        return y + 23;
    }
}
