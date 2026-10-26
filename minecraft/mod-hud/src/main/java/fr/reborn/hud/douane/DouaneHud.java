package fr.reborn.hud.douane;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.reborn.hud.douane.DouaneArt.*;

/**
 * HUD de la douane, fidèle aux maquettes J1 à J5 du 24/10 (DA §6.9 : subtil, petit ; aucun kanji). Dessiné en unités
 * menu ({@link DaKit#echelleMenu}) : la même taille à l'écran quelle que soit l'échelle d'interface.
 *
 * <ul>
 *   <li><b>Ligne du haut</b> : bande fine et nette (fond sombre uni, bords francs), picto de la barrière et « DOUANE »
 *       en petites capitales d'or, séparateur, texte Inter 7, filet d'or d'1 px en pied — « En attente · 3ᵉ dans la
 *       file », hall fermé, « Un examinateur t'appelle », « Entretien en cours » ; « QUESTION » à la place de
 *       « DOUANE » quand une question est montrée ;</li>
 *   <li><b>rappel des règles</b> : une seconde ligne plus pâle, 30 s à l'arrivée au hall, rouverte par {@code /douane} ;</li>
 *   <li><b>attente neutre</b> : écran opaque de nuit froide (jamais le monde derrière), barrière fermée, texte centré ;
 *       hall fermé : la ligne seule, en haut ;</li>
 *   <li><b>appel</b> : fondu au noir (0,5 s), téléportation par le serveur, fondu retour ;</li>
 *   <li><b>Bienvenue sur Reborn</b> : nuit chaude, barrière levée, titre d'or et filet ;</li>
 *   <li><b>résultat</b> : nuit froide, barrière fermée en gris, aucun or vif, compte à rebours du retour au launcher ;</li>
 *   <li><b>mode examinateur</b> : indicateur discret en haut à droite (comme le mode animation).</li>
 * </ul>
 */
final class DouaneHud {

    private DouaneHud() {}

    private static final Plume PZ = new Plume();
    /** Rappel du hall : seulement ce qui vaut quels que soient les réglages du serveur (délai de reprise compris). */
    private static final String[] REGLES = {"Reste courtois", "teste ton micro", "on vient t'appeler ici",
            "absent trop longtemps : place perdue"};

    /** Un morceau de la ligne : texte, gras, couleur. */
    private record Seg(String t, boolean g, int col) { }

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        int km = DaKit.echelleMenu();
        var win = mc.getWindow();
        float sw = win.getWidth() / (float) km, sh = win.getHeight() / (float) km;
        boolean cache = mc.gui.hud.isHidden();
        Plume pz = PZ;
        pz.police(mc.font);
        DaKit.unites(g, km);
        try {
            // 1. attente neutre (imposée : ni F1 ni mode photo ne la lèvent)
            float aAtt = alphaAttente(now);
            if (aAtt > 0f) attente(g, pz, sw, sh, km, aAtt);
            // 2. fondu de l'appel
            float aNoir = alphaAppel(now);
            if (aNoir > 0f) {
                LotGui l = LotGui.ouvrir(g);
                l.fill(0, 0, Math.round(sw) + 1, Math.round(sh) + 1, DaKit.argb(0x040203, aNoir));
                l.fermer();
            }
            // 3. lignes du haut
            if (!cache || aAtt > 0f) lignes(g, pz, sw, now, km);
            // 4. Bienvenue, 5. résultat (par-dessus tout)
            if (DouaneClient.bienvenueAt > 0) bienvenue(g, pz, sw, sh, km, now);
            if (DouaneClient.resultatAt > 0) resultat(g, pz, sw, sh, km, now);
            // 6. mode examinateur
            if (DouaneClient.modeExaminateur() && !cache && !(mc.gui.screen() instanceof fr.reborn.hud.staff.StaffScreen)) {
                indicateurMode(g, pz, sw);
            }
        } finally {
            DaKit.finUnites(g);
        }
    }

    /* ================================================================ attente neutre */

    private static float alphaAttente(long now) {
        if (DouaneClient.attenteTexte == null) return 0f;
        long fin = DouaneClient.attenteFinAt;
        if (fin == 0) return 1f;
        float a = 1f - (now - fin) / (float) DouaneClient.FONDU_ATTENTE_MS;
        if (a <= 0f) { DouaneClient.attenteTexte = null; DouaneClient.attenteFinAt = 0; return 0f; }
        return a;
    }

    private static void attente(GuiGraphicsExtractor g, Plume pz, float sw, float sh, int km, float a) {
        nuit(g, true, sw, sh, a);
        String t = DouaneClient.attenteTexte;
        DouaneClient.Ligne l = DouaneClient.ligne;
        float cx = sw / 2f, cy = sh / 2f;
        int px = Math.max(1, Math.round(1.5f * km));
        sprite(g, FERMEE, cx, cy - 8, px, km, DaKit.blanc(a * 0.75f));
        // hall fermé : le texte est déjà dans la ligne du haut (J1, encart) ; sinon, centré sous la barrière
        boolean dejaEnHaut = l != null && t != null && (t.equals(l.texte()) || "ferme".equals(l.etat()) && t.startsWith("DOUANE"));
        if (!dejaEnHaut && t != null && !t.isBlank()) {
            List<String> ls = pz.lignes(sansPrefixe(t), Math.min(320, sw * 0.6f), 16, false, 3);
            for (int i = 0; i < ls.size(); i++) pz.texteC(ls.get(i), cx, cy + 9 + i * 11, 16, DaKit.alpha(CREME_DOUX, a), false);
        }
        pz.vider(g);
    }

    /* ================================================================ appel : fondu au noir et retour */

    private static float alphaAppel(long now) {
        long at = DouaneClient.appelAt;
        if (at == 0) return 0f;
        float f = DouaneClient.appelFonduMs;
        long arrive = DouaneClient.appelArriveAt;
        if (arrive == 0) return Math.min(1f, (now - at) / f);
        long retour = Math.max(arrive, at + (long) f) + 120;
        if (now < retour) return Math.min(1f, (now - at) / f);
        float a = 1f - (now - retour) / f;
        if (a <= 0f) { DouaneClient.appelAt = 0; return 0f; }
        return a;
    }

    /* ================================================================ lignes du haut */

    private static void lignes(GuiGraphicsExtractor g, Plume pz, float sw, long now, int km) {
        float cx = sw / 2f, y = 13;
        float maxTexte = Math.min(Math.max(220, sw * 0.62f), 760);
        DouaneClient.Ligne l = DouaneClient.ligne;
        String q = DouaneClient.question;
        float h = 0;
        boolean rappelPossible = false;
        if (DouaneClient.appelAt > 0) {
            float f = DouaneClient.appelFonduMs;
            float prog = DouaneClient.appelArriveAt == 0 ? Math.max(0f, 1f - (now - DouaneClient.appelAt) / f) : 0f;
            h = bande(g, pz, "DOUANE", OR, List.of(new Seg(DouaneClient.appelTexte, true, CREME)), cx, y, maxTexte, 1f, prog, km);
        } else if (q != null) {
            float a = Math.min(1f, (now - DouaneClient.questionAt) / 250f);
            h = bande(g, pz, "QUESTION", OR, List.of(new Seg(q, false, CREME)), cx, y, maxTexte, a, -1, km);
        } else if (l != null) {
            float a = Math.min(1f, (now - l.at()) / 250f);
            h = bande(g, pz, "DOUANE", OR, segs(l), cx, y, maxTexte, a, -1, km);
            rappelPossible = "attente".equals(l.etat());
        }
        if (h > 0) y += h + 3;
        // rappel des règles du hall : 30 s, plus pâle, sans picto ni titre ni filet
        long r = DouaneClient.rappelAt;
        if (rappelPossible && r > 0 && now - r < 30_000L) {
            float a = Math.min(Math.min(1f, (now - r) / 300f), Math.min(1f, (30_000L - (now - r)) / 600f));
            List<Seg> s = new ArrayList<>();
            for (int i = 0; i < REGLES.length; i++) {
                if (i > 0) s.add(new Seg("  ·  ", false, GRIS));
                s.add(new Seg(REGLES[i], false, CREME_DOUX));
            }
            y += rappel(pz, s, cx, y, a) + 3;
        }
        // notification courte
        String t = DouaneClient.toast;
        if (t != null) {
            long age = now - DouaneClient.toastAt;
            if (age > 3900) DouaneClient.toast = null;
            else {
                float a = Math.min(Math.min(1f, age / 200f), Math.min(1f, (3900 - age) / 400f));
                rappel(pz, List.of(new Seg(t, false, DouaneClient.toastErreur ? 0xFFE89080 : CREME)), cx, y, a);
            }
        }
        pz.vider(g);
    }

    /** « DOUANE · En attente · 3ᵉ » → [En attente] · [3ᵉ dans la file] (+ convocation si le serveur l'envoie). */
    private static List<Seg> segs(DouaneClient.Ligne l) {
        List<Seg> out = new ArrayList<>();
        String[] parts = sansPrefixe(l.texte()).split(" · ");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.add(new Seg("  ·  ", false, GRIS));
            if (i > 0 && "attente".equals(l.etat()) && l.rang() > 0 && p.matches("\\d+\\S*")) p = p + " dans la file";
            out.add(new Seg(p, out.isEmpty(), out.isEmpty() ? CREME : i == 1 ? CREME : CREME_DOUX));
        }
        if ("attente".equals(l.etat()) && l.convocation() != null && !l.convocation().isBlank()) {
            out.add(new Seg("  ·  ", false, GRIS));
            out.add(new Seg("convocation " + l.convocation().replace(":", " h "), false, CREME_DOUX));
        }
        if ("ferme".equals(l.etat())) {
            // la voix n'est coupée que si le serveur l'exige (douane.voix.*) : seul le tchat l'est toujours
            out.add(new Seg("  ·  ", false, GRIS));
            out.add(new Seg("tchat coupé", false, CREME_DOUX));
        }
        return out;
    }

    private static String sansPrefixe(String t) {
        if (t == null) return "";
        String s = t.trim();
        if (s.toUpperCase(Locale.ROOT).startsWith("DOUANE · ")) s = s.substring("DOUANE · ".length());
        return s;
    }

    /**
     * Bande du haut centrée en cx, haut en y (unités menu) ; {@code prog} : part restante d'un compte à rebours (filet
     * d'or qui se résorbe), -1 = filet plein. Renvoie la hauteur.
     */
    private static float bande(GuiGraphicsExtractor g, Plume pz, String titre, int col, List<Seg> segs, float cx, float y,
                               float maxTexte, float a, float prog, int km) {
        if (a <= 0f) return 0;
        float sc = DaKit.facteurPetit();
        float tw = pz.largeurArcade(titre, sc);
        float tete = 8 + PICTO[2] + 4.5f + tw + 6 + 0.5f + 6.5f;
        // une seule ligne si possible ; un texte long (question) passe sur deux lignes
        float wt = 0;
        for (Seg s : segs) wt += pz.largeur(s.t(), 14, s.g());
        List<List<Seg>> lignes = new ArrayList<>();
        if (wt <= maxTexte || segs.size() != 1) lignes.add(segs);
        else for (String li : pz.lignes(segs.get(0).t(), maxTexte, 14, segs.get(0).g(), 2))
            lignes.add(List.of(new Seg(li, segs.get(0).g(), segs.get(0).col())));
        float wl = 0;
        for (List<Seg> li : lignes) {
            float w = 0;
            for (Seg s : li) w += pz.largeur(s.t(), 14, s.g());
            wl = Math.max(wl, w);
        }
        float w = tete + wl + 8;
        float h = 16 + (lignes.size() - 1) * 9.5f;
        float x = Math.round(cx - w / 2f), y0 = Math.round(y);
        pz.px(x + 1, y0 + 1.5f, x + w + 1, y0 + h + 1.5f, DaKit.argb(0x000000, 0.24f * a));
        pz.px(x, y0, x + w, y0 + h, DaKit.argb(VOILE, 0.78f * a));
        pz.h(x, x + w, y0, DaKit.argb(0xFFF0DC, 0.08f * a));
        float cy = y0 + h / 2f;
        float xx = x + 8;
        float ix = xx;
        xx += PICTO[2] + 4.5f;
        pz.arcade(titre, xx, cy - 3.5f * sc - 0.25f, DaKit.alpha(col, a), sc);
        xx += tw + 6;
        pz.v(xx, cy - 4, cy + 4, DaKit.argb(0xFFF0DC, 0.16f * a));
        xx += 6.5f;
        float ly = y0 + 8 - 0.25f;
        for (int i = 0; i < lignes.size(); i++) {
            float lx = xx;
            for (Seg s : lignes.get(i)) lx += pz.texte(s.t(), lx, ly + i * 9.5f, 14, DaKit.alpha(s.col(), a), s.g());
        }
        // filet d'or d'1 px en pied ; compte à rebours : il se résorbe
        if (prog < 0) pz.h(x, x + w, y0 + h - 1, DaKit.alpha(col, 0.82f * a));
        else {
            pz.h(x, x + w, y0 + h - 1, DaKit.alpha(col, 0.22f * a));
            pz.h(x, x + (w - 1) * prog, y0 + h - 1, DaKit.alpha(col, 0.9f * a));
        }
        pz.vider(g);
        // picto de la barrière (texture blanche teintée d'or), 1 texel = 1 unité
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BARRIERE, Math.round(ix),
                Math.round(cy - PICTO[3] / 2f), PICTO[0], PICTO[1], PICTO[2], PICTO[3], ATLAS_W, ATLAS_H, DaKit.alpha(col, a));
        return h;
    }

    /** Seconde ligne, plus discrète (rappel, notification) : voile plus léger, Inter 6,5, sans picto ni filet. */
    private static float rappel(Plume pz, List<Seg> segs, float cx, float y, float a) {
        if (a <= 0f) return 0;
        float w = 14;
        for (Seg s : segs) w += pz.largeur(s.t(), 13, s.g());
        float h = 12, x = Math.round(cx - w / 2f), y0 = Math.round(y);
        pz.px(x, y0, x + w, y0 + h, DaKit.argb(VOILE, 0.55f * a));
        float xx = x + 7;
        for (Seg s : segs) xx += pz.texte(s.t(), xx, y0 + 6 - 0.25f, 13, DaKit.alpha(s.col(), a), s.g());
        return h;
    }

    /* ================================================================ Bienvenue sur Reborn */

    private static void bienvenue(GuiGraphicsExtractor g, Plume pz, float sw, float sh, int km, long now) {
        // opaque jusqu'à « fin » (le serveur ouvre la sélection juste après) : jamais le bureau entre les deux
        if (!DouaneClient.bienvenueVisible(now)) { DouaneClient.bienvenueAt = 0; return; }
        long age = now - DouaneClient.bienvenueAt;
        long fondu = DouaneClient.bienvenueFonduAt();
        float a = Math.min(1f, age / 300f);
        if (now > fondu) a = Math.min(a, Math.max(0f, 1f - (now - fondu) / (float) DouaneClient.FONDU_BIENVENUE_MS));
        nuit(g, false, sw, sh, a);
        float cx = sw / 2f, cy = sh / 2f;
        int px = Math.max(1, Math.round(1.5f * km));
        sprite(g, LEVEE, cx, cy - 65, px, km, DaKit.blanc(a));
        // titre d'or en ArcadePix ×2, ombre portée
        String titre = DouaneClient.bienvenueTitre;
        float tw = pz.largeurArcade(titre, 2f);
        pz.arcade(titre, cx - tw / 2f + 1, cy - 47 + 1, DaKit.argb(0x0A0406, 0.9f * a), 2f);
        pz.vider(g);
        pz.arcade(titre, cx - tw / 2f, cy - 47, DaKit.alpha(OR, a), 2f);
        filet(pz, cx, cy - 20, 125, DaKit.alpha(OR, a));
        pz.texteC("Ta douane est validée. Le passage est ouvert.", cx, cy - 2, 18, DaKit.alpha(CREME_DOUX, a), false);
        pz.texteC(DouaneClient.bienvenueDate, cx, cy + 14, 13, DaKit.alpha(GRIS, a), false);
        pz.vider(g);
    }

    /** Filet d'1 px à losange central. */
    private static void filet(Plume pz, float cx, float y, float demi, int col) {
        pz.h(cx - demi, cx - 4.5f, y, col);
        pz.h(cx + 4.5f, cx + demi, y, col);
        pz.losange(cx, y, Math.max(2, DaKit.echelleGui() * 2), col);
    }

    /* ================================================================ résultat négatif ou différé */

    private static void resultat(GuiGraphicsExtractor g, Plume pz, float sw, float sh, int km, long now) {
        // tenu jusqu'à l'expulsion par le serveur (déconnexion), avec une sécurité de s + 10 s
        if (!DouaneClient.resultatVisible(now)) { DouaneClient.resultatAt = 0; return; }
        long age = now - DouaneClient.resultatAt;
        long ms = DouaneClient.resultatS * 1000L;
        float a = Math.min(1f, age / 300f);
        nuit(g, true, sw, sh, a);
        float cx = sw / 2f, cy = sh / 2f;
        int px = Math.max(1, Math.round(1.5f * km));
        sprite(g, FERMEE, cx, cy - 120, px, km, DaKit.blanc(a));
        String r = DouaneClient.resultat == null ? "" : DouaneClient.resultat;
        String titre = switch (r) {
            case "REJECTED" -> "Refusé";
            case "PENDING" -> "Douane terminée";
            default -> "À revoir";
        };
        int col = switch (r) {
            case "REJECTED" -> VERM;
            case "PENDING" -> CREME_DOUX;
            default -> AMBRE;
        };
        // le texte du serveur, tel quel : 1re phrase en sous-titre, la suite plus bas
        String texte = DouaneClient.resultatTexte == null ? "" : DouaneClient.resultatTexte.trim();
        String[] phrases = texte.split("(?<=[.!?…])\\s+", 2);
        String sous = "PENDING".equals(r) ? phrases.length > 1 ? phrases[1] : texte : phrases[0];
        String suite = "PENDING".equals(r) ? "" : phrases.length > 1 ? phrases[1] : "";
        float scP = DaKit.facteurPetit();
        float dw = pz.largeurArcade("DOUANE", scP);
        pz.arcade("DOUANE", cx - dw / 2f, cy - 108.5f, DaKit.alpha(GRIS, a), scP);
        pz.texteC(titre, cx, cy - 84, 34, DaKit.alpha(col, a), true);
        float y = cy - 63;
        for (String li : pz.lignes(sous, 300, 18, false, 2)) { pz.texteC(li, cx, y, 18, DaKit.alpha(CREME_DOUX, a), false); y += 12; }
        y += 4;
        filet(pz, cx, y, 110, DaKit.alpha(FILET_TERNE, a));
        y += 16;
        String motif = DouaneClient.resultatMotif;
        boolean aMotif = motif != null && !motif.isBlank();
        if (aMotif) {
            // champ facultatif (absent du protocole v1) : le motif tel quel, comme la maquette J5
            pz.capitalesC("Motif de l'examinateur", cx, y, 11, DaKit.alpha(GRIS, a));
            y += 15;
            for (String li : pz.lignes("« " + motif.trim() + " »", 270, 16, false, 4)) {
                pz.texteC(li, cx, y, 16, DaKit.alpha(CREME, a), false);
                y += 12;
            }
            y += 10;
        } else if (!suite.isBlank()) {
            // protocole v1 : « Le commentaire de l'examinateur est dans ton launcher. », tel quel
            for (String li : pz.lignes(suite, 300, 16, false, 3)) {
                pz.texteC(li, cx, y, 16, DaKit.alpha(CREME, a), false);
                y += 12;
            }
            y += 12;
        }
        String[] apres = switch (r) {
            case "REJECTED" -> new String[]{"Nouvelle candidature possible dans 24 h.", "Elle repart de zéro."};
            case "NEEDS_REVISION" -> new String[]{"Ton écrit est gardé.", "Nouvelle séance dès demain, à réserver dans ton launcher."};
            default -> new String[0];
        };
        if (apres.length > 0) {
            pz.capitalesC("Et maintenant", cx, y, 11, DaKit.alpha(GRIS, a));
            y += 15;
            for (String li : apres) { pz.texteC(li, cx, y, 16, DaKit.alpha(CREME_DOUX, a), false); y += 12; }
            y += 3;
        }
        long reste = Math.max(0, (ms - age + 999) / 1000);
        y += 6;
        // serveur en retard sur le compte : on attend l'expulsion sans afficher « 0 s »
        pz.texteC(reste > 0 ? "Retour au launcher dans " + reste + " s" : "Retour au launcher…", cx, y, 13, DaKit.alpha(GRIS, a), false);
        float prog = Math.max(0f, 1f - age / (float) ms);
        pz.h(cx - 55, cx + 55, y + 9, DaKit.argb(0x786E64, a));
        pz.h(cx - 55, cx - 55 + 110 * prog, y + 9, DaKit.alpha(TERNE_CLAIR, a));
        pz.vider(g);
    }

    /* ================================================================ mode examinateur */

    /** Indicateur discret en haut à droite : pastille d'or et un mot, Inter 7 sur rien (comme le mode animation). */
    private static void indicateurMode(GuiGraphicsExtractor g, Plume pz, float sw) {
        String s = "Mode examinateur · journalisé";
        float w = pz.largeur(s, 14, false);
        float x = sw - 8 - w;
        pz.px(x - 6, 7, x - 3, 10, DaKit.alpha(OR, 0.85f));
        pz.texte(s, x + 0.5f, 9, 14, DaKit.argb(0x0A0608, 0.7f), false);
        pz.vider(g);
        pz.texte(s, x, 8.5f, 14, DaKit.alpha(DaKit.CREME_VAL, 0.8f), false);
        pz.vider(g);
    }
}
