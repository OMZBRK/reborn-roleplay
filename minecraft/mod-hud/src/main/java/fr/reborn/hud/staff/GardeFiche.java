package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fiche d'un joueur dans le Poste de garde : <b>carte d'identité fixe</b> (visage, N°, grade, nom RP, compte, marqueurs
 * gelé / écran noir / étiquettes, six repères chiffrés), <b>sous-onglets</b> (Actions · Sanctions · Notes · Comptes
 * liés · Journal ›) et leur contenu qui défile.
 *
 * <p>Actions rangées par familles (Déplacement, Surveillance, Soins, Mise en scène, Personnage) dans des panneaux de
 * washi en deux colonnes ; les actions qui demandent un paramètre ouvrent un formulaire ({@link GardeFormulaire}), les
 * destructives exigent de retaper le N°. Notes et étiquettes staff rattachées au N° (canal {@code notes} /
 * {@code note_add} / {@code note_del}, PLUGINS_REFERENCE §4 bis.2).
 */
final class GardeFiche {

    static final String[][] SOUS = {{"actions", "Actions"}, {"sanctions", "Sanctions"}, {"notes", "Notes"},
            {"comptes", "Comptes liés"}, {"journal", "Journal"}};
    static final String[][] ETIQUETTES = {{"", "Aucune"}, {"surveillé", "Surveillé"}, {"avertissement", "Avertissement"},
            {"event", "Event"}, {"comportement", "Comportement"}};

    /** Une action de la fiche : libellé, op du protocole, grade minimal, style, nature (direct, form, danger). */
    private record Action(String label, String op, int grade, String style, String nature, String aide) { }

    private final StaffScreen s;
    String sous = "actions";
    private EditBox noteTexte;
    private String noteEtiq = "";
    private String notesDemandees;

    GardeFiche(StaffScreen s) { this.s = s; }

    void init() {
        noteTexte = s.box(noteTexte, "Ajouter une note (visible du staff seulement)…", 500);
    }

    void ouvrir() {
        sous = "actions";
        notesDemandees = null;
    }

    int hauteurOutils() { return 58 + 6 + StaffScreen.H_BTN + 8; }

    /* ================================================================ zone fixe : carte + sous-onglets */

    void outils(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonObject p = StaffClient.profile();
        int x = s.vx0() + 6, w = s.vx1() - s.vx0() - 6, h = 58;
        s.fuda(ctx, x, y, w, h, StaffScreen.GOLD);
        if (p == null || !s.profileUuid.equals(StaffScreen.str(p, "uuid"))) {
            s.txt(ctx, "Chargement de la fiche…", x + 20, y + 24, w - 30, StaffScreen.INK_SOFT);
            return;
        }
        String uuid = StaffScreen.str(p, "uuid");
        s.head(ctx, uuid, x + 16, y + 13, 32);
        int tx = x + 58, xr = x + w - 8;
        int sc = StaffScreen.num(p, "score");
        int rx = xr;
        if (sc > 0) rx -= s.badge(ctx, xr, y + 4, "SCORE " + sc, sc >= 100) + StaffScreen.GAP;
        s.identite(ctx, p, tx, y + 5, rx - tx, true);
        marqueurs(ctx, p, tx, y + 17, xr - tx);
        String st = StaffScreen.str(p, "state");
        String[][] faits = {
                {"PV", p.has("hp") ? StaffScreen.num(p, "hp") + " / " + StaffScreen.num(p, "hpMax") : "—"},
                {"Chakra", p.has("chakra") ? StaffScreen.num(p, "chakra") + " / " + StaffScreen.num(p, "chakraMax") : "—"},
                {"Ryo", p.has("ryo") ? milliers(StaffScreen.num(p, "ryo")) : "—"},
                {"État", st.isEmpty() ? "En forme" : st},
                {"Temps de jeu", p.has("playtime") ? (StaffScreen.num(p, "playtime") / 60) + " h " + String.format("%02d", StaffScreen.num(p, "playtime") % 60) : "—"},
                {"Ping", p.has("ping") ? StaffScreen.num(p, "ping") + " ms" : "hors ligne"}};
        int colW = (xr - tx) / 3;
        for (int i = 0; i < faits.length; i++) {
            int fx = tx + (i % 3) * colW, fy = y + 30 + (i / 3) * 12;
            int lw = s.txt7(ctx, faits[i][0], fx, fy + 1, colW / 2, StaffScreen.INK_SOFT);
            int vx = fx + Math.max(lw + 4, Math.min(52, colW / 2));
            s.txt8(ctx, faits[i][1], vx, fy, fx + colW - StaffScreen.GAP - vx,
                    i == 3 && !st.isEmpty() ? StaffScreen.LACQUER_LO : StaffScreen.INK);
        }
        // sous-onglets
        int by = y + h + 6, bx = s.vx0();
        for (String[] so : SOUS) {
            String lab = so[1] + compte(p, so[0]) + ("journal".equals(so[0]) ? " >" : "");
            String id = so[0];
            bx += s.button(ctx, bx, by, lab, id.equals(sous) ? "punish" : "plain", () -> choisir(id, p), false) + 4;
        }
    }

    private static String milliers(int v) {
        return String.format("%,d", v).replace(',', ' ').replace(' ', ' ');
    }

    private String compte(JsonObject p, String id) {
        return switch (id) {
            case "sanctions" -> StaffScreen.num(p, "recordTotal") > 0 ? " (" + StaffScreen.num(p, "recordTotal") + ")" : "";
            case "notes" -> {
                JsonObject n = StaffClient.notes(StaffScreen.str(p, "pid"));
                int c = n != null ? StaffScreen.arr(n, "entries").size() : StaffScreen.num(p, "notes");
                yield c > 0 ? " (" + c + ")" : "";
            }
            case "comptes" -> StaffScreen.arr(p, "alts").isEmpty() ? "" : " (" + StaffScreen.arr(p, "alts").size() + ")";
            default -> "";
        };
    }

    private void choisir(String id, JsonObject p) {
        if ("journal".equals(id)) {
            s.journalVue.filtrerPid(StaffScreen.str(p, "pid"), StaffScreen.name(p));
            s.switchTab("journal");
            return;
        }
        if (!id.equals(sous)) RebornSounds.uiClick();
        sous = id;
        s.scroll = 0;
    }

    /** Pastilles d'état : gelé, écran noir, mode staff, puis étiquettes des notes (coupées par « +N »). */
    private void marqueurs(GuiGraphicsExtractor ctx, JsonObject p, int x, int y, int maxW) {
        List<String[]> m = new ArrayList<>();
        if (StaffScreen.bool(p, "gele") || StaffScreen.bool(p, "frozen")) {
            String par = StaffScreen.str(p, "gelePar"), motif = StaffScreen.str(p, "geleMotif");
            m.add(new String[]{"gelé", "glace", "Gelé" + (par.isEmpty() ? "" : " par " + par) + (motif.isEmpty() ? "" : " : " + motif)});
        }
        if (StaffScreen.bool(p, "noir")) m.add(new String[]{"écran noir", "encre", "Écran noir en cours"});
        if (StaffScreen.bool(p, "modeStaff")) m.add(new String[]{"mode staff", "or", "En mode staff"});
        if (StaffScreen.bool(p, "watching")) m.add(new String[]{"observé", "vert", "Tu l'observes"});
        JsonArray et = StaffScreen.arr(p, "etiquettes");
        JsonObject n = StaffClient.notes(StaffScreen.str(p, "pid"));
        if (n != null) {
            et = new JsonArray();
            for (JsonElement e : StaffScreen.arr(n, "entries")) {
                String t = StaffScreen.str(e.getAsJsonObject(), "etiquette");
                if (!t.isEmpty() && !et.contains(e.getAsJsonObject().get("etiquette"))) et.add(t);
            }
        }
        for (JsonElement e : et) m.add(new String[]{e.getAsString(), "rouge", "Étiquette staff : " + e.getAsString()});
        if (m.isEmpty()) {
            s.txt7(ctx, StaffScreen.bool(p, "online") || p.has("ping") ? "Aucun marqueur" : "Hors ligne", x, y + 2, maxW, 0xFF8C7A5C);
            return;
        }
        int cx = x;
        for (int i = 0; i < m.size(); i++) {
            String[] t = m.get(i);
            Component c = RebornFont.body(t[0]);
            int tw = Math.round(s.police().width(c) * DaKit.S7) + 6;
            int reste = m.size() - i - 1;
            if (cx + tw > x + maxW - (reste > 0 ? 22 : 0)) {
                String plus = "+" + (m.size() - i);
                StringBuilder tout = new StringBuilder();
                for (int j = i; j < m.size(); j++) tout.append(j > i ? " · " : "").append(m.get(j)[0]);
                DaKit.texteEchelle(ctx, s.police(), RebornFont.body(plus), cx, y + 2, StaffScreen.INK_SOFT, DaKit.S7);
                s.tip(cx, y, 20, 10, tout.toString());
                break;
            }
            int col = switch (t[1]) {
                case "glace" -> StaffScreen.GLACE;
                case "encre" -> StaffScreen.INK;
                case "or" -> StaffScreen.PID;
                case "vert" -> StaffScreen.OK;
                default -> StaffScreen.LACQUER;
            };
            DrawHelpers.outlinedRect(ctx, cx, y, tw, 10, 0, col);
            DaKit.texteEchelle(ctx, s.police(), c, cx + 3, y + 2, col, DaKit.S7);
            s.tip(cx, y, tw, 10, t[2]);
            cx += tw + 4;
        }
    }

    /* ================================================================ corps (sous-onglet) */

    int corps(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonObject p = StaffClient.profile();
        if (p == null || !s.profileUuid.equals(StaffScreen.str(p, "uuid"))) return y + 10;
        return switch (sous) {
            case "sanctions" -> sanctions(ctx, snap, p, y);
            case "notes" -> notes(ctx, snap, p, y);
            case "comptes" -> comptes(ctx, p, y);
            default -> actions(ctx, snap, p, y);
        };
    }

    /* ---------------------------------------------------------------- actions */

    private List<Object[]> familles(JsonObject p) {
        boolean gele = StaffScreen.bool(p, "gele") || StaffScreen.bool(p, "frozen");
        boolean noir = StaffScreen.bool(p, "noir");
        boolean obs = StaffScreen.bool(p, "watching");
        List<Object[]> f = new ArrayList<>();
        f.add(new Object[]{"Déplacement", List.of(
                new Action("Aller à lui", "tp", 1, "plain", "direct", "Te téléporte à lui (ta position est gardée pour « Revenir »)."),
                new Action("Revenir", "back", 1, "plain", StaffScreen.bool(p, "back") ? "direct" : "off", "Te ramène où tu étais avant ton dernier tp, ramener ou observer."),
                new Action("Le ramener", "bring", 2, "plain", "direct", "Le téléporte à toi (sa position est gardée pour « Le renvoyer »)."),
                new Action("Le renvoyer", "return", 2, "plain", StaffScreen.bool(p, "retour") ? "direct" : "off", "Le renvoie où il était avant d'être ramené."))});
        f.add(new Object[]{"Surveillance", List.of(
                new Action(obs ? "Fin d'observation" : "Observer", "watch", 2, "watch", "direct", "Mode spectateur collé à lui, invisible."),
                new Action("Voir l'inventaire", "invsee", 2, "plain", "direct", "Ouvre sa barre, sa sacoche, son sac et son équipement en lecture (prendre = Admin)."),
                gele ? new Action("Dégeler", "unfreeze", 2, "watch", "direct", "Rend ses mouvements.")
                        : new Action("Geler…", "freeze", 2, "plain", "form", "Bloque ses mouvements, même après déconnexion ou redémarrage."),
                noir ? new Action("Lever l'écran noir", "blind", 2, "watch", "direct", "Rend la vue au joueur.")
                        : new Action("Écran noir…", "blind", 2, "plain", "form", "Fondu au noir sur son écran, avec un texte facultatif."))});
        f.add(new Object[]{"Soins", List.of(
                new Action("Réveiller", "revive", 2, "plain", "direct", "Le relève s'il est à terre ou inconscient."),
                new Action("Lever l'ATA", "lift_ata", 2, "plain", "direct", "Retire l'ATA en cours."))});
        f.add(new Object[]{"Mise en scène", List.of(
                new Action("Annonce perso…", "announce", 1, "plain", "form", "Une ligne discrète en haut de son écran (scène, convocation)."),
                new Action("Emote…", "emote", 2, "plain", "form", "Force une emote (ou l'arrête)."),
                new Action("Tenue…", "outfit", 2, "plain", "form", "Force une tenue Nexo (ou rend la sienne)."),
                new Action("Séisme…", "shake", 3, "plain", "form", "Secoue sa caméra quelques secondes (lui seul)."))});
        f.add(new Object[]{"Personnage", List.of(
                new Action("Remettre à zéro…", "perso_reset", 3, "punish", "danger", "Remet le personnage à zéro (stats, techniques, inventaire). Irréversible."),
                new Action("Supprimer…", "perso_suppr", 3, "punish", "danger", "Supprime le personnage. Son N° ne sera jamais réutilisé. Irréversible."))});
        return f;
    }

    @SuppressWarnings("unchecked")
    private int actions(GuiGraphicsExtractor ctx, JsonObject snap, JsonObject p, int y) {
        int g = StaffScreen.grade(snap);
        List<Object[]> fam = familles(p);
        int x0 = s.vx0(), W = s.vx1() - s.vx0();
        int cols = W >= 360 ? 2 : 1;
        int colW = (W - (cols - 1) * StaffScreen.GAP) / cols;
        int inner = colW - 16;
        int[] colY = new int[cols];
        for (int c = 0; c < cols; c++) colY[c] = y;
        for (Object[] f : fam) {
            List<Action> acts = (List<Action>) f[1];
            int[] lw = new int[acts.size()];
            for (int i = 0; i < lw.length; i++) lw[i] = s.bwG(acts.get(i).label());
            int[][] fl = StaffScreen.flux(lw, inner, 4);
            int rows = fl[fl.length - 1][1] + 1;
            int ph = 22 + rows * (StaffScreen.H_BTN_G + 4) + 4;
            int c = 0;
            for (int k = 1; k < cols; k++) if (colY[k] < colY[c]) c = k;
            int px = x0 + c * (colW + StaffScreen.GAP), py = colY[c];
            boolean danger = "Personnage".equals(f[0]);
            GardeArt.panneau(ctx, px, py, colW - 3, ph - 3);
            if (danger) ctx.fill(px + 2, py + 2, px + colW - 5, py + 4, StaffScreen.LACQUER);
            DaKit.etiq(ctx, s.police(), DaKit.etiq(s.police(), (String) f[0]), px + 8, py + 10, danger ? StaffScreen.LACQUER : DaKit.ENCRE_ROUGE);
            for (int i = 0; i < acts.size(); i++) {
                Action a = acts.get(i);
                int bx = px + 8 + fl[i][0], by = py + 18 + fl[i][1] * (StaffScreen.H_BTN_G + 4);
                boolean off = "off".equals(a.nature());
                boolean locked = g < a.grade() || off;
                if (off) s.refusVerrou = "Rien à annuler pour l'instant.";
                else if (g < a.grade()) s.refusVerrou = "Réservé au grade " + StaffScreen.gradeName(a.grade()) + " et plus.";
                s.bouton(ctx, bx, by, lw[i], a.label(), a.style(), () -> lancer(a, p), locked);
                s.refusVerrou = null;
                s.tip(bx, by, lw[i], StaffScreen.H_BTN_G, a.aide() + (g < a.grade() ? " (" + StaffScreen.gradeName(a.grade()) + "+)" : ""));
            }
            colY[c] += ph + StaffScreen.GAP;
        }
        int end = y;
        for (int v : colY) end = Math.max(end, v);
        return end;
    }

    private void lancer(Action a, JsonObject p) {
        String pid = StaffScreen.str(p, "pid"), uuid = StaffScreen.str(p, "uuid"), nom = StaffScreen.name(p);
        String qui = (pid.isEmpty() ? "" : "N° " + pid + " · ") + nom;
        switch (a.nature()) {
            case "direct" -> envoyer(a.op(), p, Map.of());
            case "danger" -> s.formulaire.ouvrir(a.label().replace("…", "") + " ?",
                    qui + " — " + a.aide(), "perso_suppr".equals(a.op()) ? "Supprimer" : "Remettre à zéro", true,
                    pid.isEmpty() ? StaffScreen.str(p, "name") : pid, List.of(), v -> envoyer(a.op(), p, v));
            default -> {
                switch (a.op()) {
                    case "freeze" -> s.formulaire.ouvrir("Geler " + nom, "Il ne pourra plus bouger, même après une déconnexion ou un redémarrage, jusqu'au dégel.",
                            "Geler", false, null, List.of(GardeFormulaire.Champ.texte("m", "Motif", "Facultatif : vu par le staff", false)),
                            v -> envoyer("freeze", p, v));
                    case "blind" -> s.formulaire.ouvrir("Écran noir — " + nom, "Fondu au noir sur son écran. Le tchat reste lisible.",
                            "Plonger dans le noir", false, null, List.of(
                                    GardeFormulaire.Champ.texte("m", "Texte", "Facultatif : « Vous perdez connaissance… »", false),
                                    GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"0", "Jusqu'à levée"}, {"10", "10 s"}, {"30", "30 s"}, {"120", "2 min"}, {"600", "10 min"}})),
                            v -> envoyer("blind", p, v));
                    case "announce" -> s.formulaire.ouvrir("Annonce à " + nom, "Une ligne discrète en haut de son écran. Ton nom n'est pas montré au joueur.",
                            "Envoyer", false, null, List.of(
                                    GardeFormulaire.Champ.texte("m", "Message", "Ex. : « Le Hokage te convoque. »", true),
                                    GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"8", "8 s"}, {"5", "5 s"}, {"15", "15 s"}, {"30", "30 s"}})),
                            v -> envoyer("announce", p, v));
                    case "emote" -> s.formulaire.ouvrir("Forcer une emote — " + nom, null, "Jouer", false, null, List.of(
                                    GardeFormulaire.Champ.texte("v", "Emote", "Id de l'emote, ou « stop »", true)),
                            v -> envoyer("emote", p, v));
                    case "outfit" -> s.formulaire.ouvrir("Forcer une tenue — " + nom, null, "Habiller", false, null, List.of(
                                    GardeFormulaire.Champ.texte("v", "Tenue", "Id Nexo, « main » (objet en main) ou « retirer »", true)),
                            v -> envoyer("outfit", p, v));
                    case "shake" -> s.formulaire.ouvrir("Séisme sur " + nom, "Secoue sa caméra seulement (le son vient du serveur).", "Secouer", false, null, List.of(
                                    GardeFormulaire.Champ.choix("v", "Force", new String[][]{{"5", "Moyenne"}, {"3", "Légère"}, {"8", "Forte"}}),
                                    GardeFormulaire.Champ.choix("s", "Durée", new String[][]{{"4", "4 s"}, {"2", "2 s"}, {"8", "8 s"}})),
                            v -> envoyer("shake", p, v));
                    default -> envoyer(a.op(), p, Map.of());
                }
            }
        }
    }

    /** Banc : ouvre l'action {@code op} de la fiche affichée (formulaire, confirmation…). */
    @SuppressWarnings("unchecked")
    void debugAction(String op) {
        JsonObject p = StaffClient.profile();
        if (p == null) return;
        for (Object[] f : familles(p)) for (Action a : (List<Action>) f[1]) if (a.op().equals(op)) { lancer(a, p); return; }
    }

    private void envoyer(String op, JsonObject p, Map<String, String> v) {
        if ("perso_reset".equals(op) || "perso_suppr".equals(op)) { commandePerso(op, p); return; }
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", StaffScreen.str(p, "uuid"));
        if (!StaffScreen.str(p, "pid").isEmpty()) o.addProperty("pid", StaffScreen.str(p, "pid"));
        for (Map.Entry<String, String> e : v.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if ("s".equals(e.getKey()) || "v".equals(e.getKey()) && e.getValue().chars().allMatch(Character::isDigit)) {
                try { o.addProperty(e.getKey(), Integer.parseInt(e.getValue())); continue; } catch (NumberFormatException ignored) { }
            }
            o.addProperty(e.getKey(), e.getValue());
        }
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /**
     * Remise à zéro / suppression : pas d'{@code act} côté serveur, ce sont les commandes {@code /persoreset} et
     * {@code /persosuppr} (Admin, PLUGINS_REFERENCE §2.1), qui demandent « confirmer » sous 60 s. La confirmation a
     * déjà été faite ici (N° retapé) : on envoie la demande puis la confirmation.
     */
    private void commandePerso(String op, JsonObject p) {
        String pid = StaffScreen.str(p, "pid");
        String ref = pid.isEmpty() ? StaffScreen.str(p, "name") : "#" + pid;
        String cmd = ("perso_reset".equals(op) ? "persoreset " : "persosuppr ") + ref;
        if (StaffClient.demo) {
            JsonObject o = new JsonObject();
            o.addProperty("a", "act");
            o.addProperty("op", op);
            StaffClient.send(o);
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(cmd);
        mc.player.connection.sendCommand(cmd + " confirmer");
        RebornSounds.confirm();
    }

    /* ---------------------------------------------------------------- sanctions : casier + infractions */

    private int sanctions(GuiGraphicsExtractor ctx, JsonObject snap, JsonObject p, int y) {
        int x = s.vx0(), w = s.vx1() - s.vx0();
        y = s.section(ctx, "Casier", y);
        String resume = StaffScreen.num(p, "recordTotal") + " infraction(s) · " + StaffScreen.num(p, "recordActive") + " active(s)";
        s.txt8(ctx, resume, x + 2, y, w, StaffScreen.INK);
        y += 12;
        JsonArray rec = StaffScreen.arr(p, "record");
        if (rec.isEmpty()) { s.txt8(ctx, "Casier vierge.", x + 2, y, w, StaffScreen.INK_SOFT); y += 14; }
        int colW = 64, stW = 40;
        for (JsonElement e : rec) {
            JsonObject r = e.getAsJsonObject();
            boolean act = StaffScreen.bool(r, "active");
            ctx.fill(x, y, x + w, y + 15, StaffScreen.L_FOND);
            ctx.fill(x, y + 14, x + w, y + 15, StaffScreen.L_FILET);
            ctx.fill(x, y, x + 2, y + 14, act ? StaffScreen.LACQUER : 0xFF7A6E5C);
            s.txt7(ctx, StaffScreen.str(r, "when"), x + 6, y + 4, colW - 8, StaffScreen.INK_SOFT);
            s.txt8(ctx, StaffScreen.str(r, "text"), x + 6 + colW, y + 4, w - colW - stW - 14, StaffScreen.INK);
            s.txt7(ctx, StaffScreen.str(r, "status"), x + w - stW, y + 4, stW - 4, act ? StaffScreen.LACQUER : StaffScreen.INK_SOFT);
            y += 17;
        }
        y = s.section(ctx, "Sanctionner — choisis l'infraction", y + 6);
        JsonArray offs = StaffScreen.arr(p, "offenses");
        int cols = w >= 420 ? 3 : 2, gap = StaffScreen.GAP, cw = (w - gap * (cols - 1)) / cols, ch = 38;
        for (int i = 0; i < offs.size(); i++) {
            JsonObject o = offs.get(i).getAsJsonObject();
            int ox = x + (i % cols) * (cw + gap), oy = y + (i / cols) * (ch + gap);
            boolean locked = StaffScreen.bool(o, "locked");
            boolean hov = s.hover(ox, oy, cw, ch) && !locked && s.cliquable(oy, ch);
            if (hov) oy -= 1;
            GardeArt.carte(ctx, ox, oy, cw, ch, hov);
            int tone = locked ? 0xFF7A6E5C : switch (StaffScreen.str(o, "tone")) { case "warn" -> 0xFF8A5A0C; case "mute" -> 0xFFB45309; default -> StaffScreen.LACQUER; };
            ctx.fill(ox + 1, oy + 1, ox + cw - 1, oy + 3, tone);
            ctx.fill(ox + 1, oy + 1, ox + cw - 1, oy + 2, 0x30FFFFFF);
            if (locked) ctx.fill(ox + cw - 9, oy + 7, ox + cw - 5, oy + 11, 0xFF9A8A70);
            s.txtG(ctx, StaffScreen.str(o, "name"), ox + 6, oy + 6, cw - 18, locked ? StaffScreen.ETEINT : StaffScreen.INK);
            int st = StaffScreen.num(o, "strikes");
            s.txt7(ctx, st == 0 ? "Aucun strike" : st + " strike" + (st > 1 ? "s" : ""), ox + 6, oy + 17, cw - 12, st == 0 ? StaffScreen.INK_SOFT : StaffScreen.LACQUER);
            String next = locked ? StaffScreen.str(o, "next") + " · " + StaffScreen.str(o, "lockGrade") : "Prochaine : " + StaffScreen.str(o, "next");
            String nf = s.fit(next, Math.round((cw - 16) / DaKit.S7));
            int pw = Math.round(s.police().width(RebornFont.body(nf)) * DaKit.S7) + 6;
            DrawHelpers.outlinedRect(ctx, ox + 6, oy + 26, pw, 10, 0, tone);
            DaKit.texteEchelle(ctx, s.police(), RebornFont.body(nf), ox + 9, oy + 28, tone, DaKit.S7);
            final JsonObject off = o;
            if (s.cliquable(oy, ch)) s.hits.add(new StaffScreen.Hit(ox, oy, cw, ch, () -> {
                if (locked) { StaffClient.toast("Réservé au grade " + StaffScreen.str(off, "lockGrade") + "."); RebornSounds.deny(); return; }
                s.confirm = off;
                s.flags.clear();
                s.sealAt = 0;
                s.noteBox.setValue("");
                RebornSounds.uiClick();
            }));
        }
        return y + ((offs.size() + cols - 1) / cols) * (ch + gap) + 4;
    }

    /* ---------------------------------------------------------------- notes et étiquettes */

    private int notes(GuiGraphicsExtractor ctx, JsonObject snap, JsonObject p, int y) {
        String pid = StaffScreen.str(p, "pid");
        int x = s.vx0(), w = s.vx1() - s.vx0();
        if (pid.isEmpty()) return s.vide(ctx, y, "Pas de N° : aucun personnage actif.", "Les notes sont rattachées au N° du personnage.");
        if (!pid.equals(notesDemandees)) {
            notesDemandees = pid;
            JsonObject o = new JsonObject();
            o.addProperty("a", "notes");
            o.addProperty("pid", pid);
            StaffClient.send(o);
        }
        // ajout : étiquette en puces (en flux), texte, « Ajouter »
        int[] lw = new int[ETIQUETTES.length];
        for (int i = 0; i < lw.length; i++) lw[i] = s.bw(ETIQUETTES[i][1]);
        int ex = x + 64;
        int[][] fl = StaffScreen.flux(lw, w - 2 - 72, 4);
        int rangs = fl[fl.length - 1][1] + 1;
        int ch = 8 + rangs * 16 + 24;
        GardeArt.feuille(ctx, x, y, w - 2, ch);
        DaKit.etiq(ctx, s.police(), DaKit.etiq(s.police(), "Étiquette"), x + 8, y + 10, StaffScreen.INK_SOFT);
        for (int i = 0; i < ETIQUETTES.length; i++) {
            String id = ETIQUETTES[i][0];
            s.button(ctx, ex + fl[i][0], y + 4 + fl[i][1] * 16, ETIQUETTES[i][1], id.equals(noteEtiq) ? "punish" : "plain",
                    () -> { noteEtiq = id; RebornSounds.uiClick(); }, false);
        }
        int fy = y + 8 + rangs * 16, aw = s.bwG("Ajouter");
        s.placer(ctx, noteTexte, x + 8, fy, w - 2 - 16 - aw - StaffScreen.GAP);
        s.refusVerrou = "Écris d'abord la note.";
        s.bouton(ctx, x + w - 2 - 8 - aw, fy - 1, aw, "Ajouter", "watch", () -> ajouter(pid), noteTexte.getValue().isBlank());
        s.refusVerrou = null;
        y += ch + 8;
        JsonObject n = StaffClient.notes(pid);
        if (n == null) return s.vide(ctx, y, "Chargement des notes…", null);
        JsonArray es = StaffScreen.arr(n, "entries");
        if (es.isEmpty()) return s.vide(ctx, y, "Aucune note sur ce N°.", "Les notes et étiquettes ne sont vues que du staff et suivent le N° (même après un changement de nom).");
        for (JsonElement e : es) {
            JsonObject t = e.getAsJsonObject();
            List<String> lignes = s.wrap(StaffScreen.str(t, "texte"), w - 24);
            int h = 16 + lignes.size() * 10 + 4;
            ctx.fill(x, y, x + w, y + h, StaffScreen.L_FOND);
            ctx.fill(x, y + h - 1, x + w, y + h, StaffScreen.L_FILET);
            String etq = StaffScreen.str(t, "etiquette");
            ctx.fill(x, y, x + 2, y + h - 1, etq.isEmpty() ? StaffScreen.GOLD : StaffScreen.LACQUER);
            int cx = x + 8;
            cx += s.txt7(ctx, StaffScreen.str(t, "date"), cx, y + 5, 60, StaffScreen.INK_SOFT) + 8;
            String auteur = StaffScreen.str(t, "auteur");
            int ag = StaffScreen.num(t, "auteurGrade");
            int sw = StaffScreen.bool(t, "suppr") ? s.bw("Supprimer") + 6 : 0;
            int etW = 0;
            if (!etq.isEmpty()) {
                Component c = RebornFont.body(etq);
                etW = Math.round(s.police().width(c) * DaKit.S7) + 6;
            }
            cx += s.txtG(ctx, auteur, cx, y + 3, Math.max(30, x + w - 8 - sw - etW - 6 - cx), StaffScreen.gradeColor(ag)) + 6;
            if (!etq.isEmpty()) {
                DrawHelpers.outlinedRect(ctx, cx, y + 3, etW, 10, 0, StaffScreen.LACQUER);
                DaKit.texteEchelle(ctx, s.police(), RebornFont.body(etq), cx + 3, y + 5, StaffScreen.LACQUER, DaKit.S7);
            }
            if (sw > 0) {
                String id = StaffScreen.str(t, "id");
                s.button(ctx, x + w - sw, y + 2, "Supprimer", "plain", () -> s.formulaire.ouvrir("Supprimer la note ?",
                        "« " + StaffScreen.str(t, "texte") + " »", "Supprimer", true, null, List.of(), v -> supprimer(pid, id)), false);
            }
            for (int k = 0; k < lignes.size(); k++) {
                ctx.text(s.police(), RebornFont.body(lignes.get(k)), x + 8, y + 16 + k * 10, StaffScreen.INK, false);
            }
            y += h + 3;
        }
        return y;
    }

    private void ajouter(String pid) {
        String t = noteTexte.getValue().trim();
        if (t.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "note_add");
        o.addProperty("pid", pid);
        o.addProperty("texte", t);
        if (!noteEtiq.isEmpty()) o.addProperty("etiquette", noteEtiq);
        StaffClient.send(o);
        noteTexte.setValue("");
        RebornSounds.confirm();
    }

    private void supprimer(String pid, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "note_del");
        o.addProperty("pid", pid);
        o.addProperty("id", id);
        StaffClient.send(o);
    }

    /** Entrée dans le champ de note : ajoute la note. */
    boolean entree() {
        JsonObject p = StaffClient.profile();
        if (noteTexte != null && noteTexte.isVisible() && noteTexte.isFocused() && p != null) {
            ajouter(StaffScreen.str(p, "pid"));
            return true;
        }
        return false;
    }

    /* ---------------------------------------------------------------- comptes liés */

    private int comptes(GuiGraphicsExtractor ctx, JsonObject p, int y) {
        JsonArray alts = StaffScreen.arr(p, "alts");
        if (alts.isEmpty()) return s.vide(ctx, y, "Aucun autre compte vu avec la même IP.", null);
        int x = s.vx0(), w = s.vx1() - s.vx0();
        s.txt8(ctx, "Comptes vus avec la même adresse IP que ce joueur :", x, y, w, StaffScreen.INK_SOFT);
        y += 13;
        for (JsonElement e : alts) {
            JsonObject a = e.getAsJsonObject();
            String altId = StaffScreen.str(a, "uuid");
            int vw = s.bw("Ouvrir la fiche");
            GardeArt.carte(ctx, x, y, w - 2, 20, false);
            int nw = s.txtG(ctx, StaffScreen.str(a, "name"), x + 8, y + 6, w / 3, StaffScreen.INK);
            s.txt8(ctx, "IP partagée · vu " + StaffScreen.str(a, "seen"), x + 16 + nw, y + 7, w - nw - vw - 36, StaffScreen.INK_SOFT);
            s.button(ctx, x + w - 2 - 6 - vw, y + 4, "Ouvrir la fiche", "plain", () -> s.openProfile(altId), false);
            y += 24;
        }
        return y;
    }
}
