package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/** Éléments d'interface communs aux deux écrans : perches, indicateur sonore, fil des sceaux, question, registre. */
final class PersoUi {

    private PersoUi() {}

    static final int YP = 96;
    static final String[] ETAPES = {"VILLAGE", "CLAN", "SEXE", "NOM", "AGE", "TAILLE", "APPARENCE", "SCEAU"};

    /** Perches de bambou : gauche de 0 à xd (embout à droite), droite de xg à w (embout à gauche). */
    static void perches(GuiGraphicsExtractor g, int xd, int xg, int w) {
        if (xd > 0) {
            int lg = Math.min(300, xd + 2);
            blit(g, PERCHE, xd - lg, YP - 1, 300 - lg, 0, lg, 8, 300, 16);
        }
        if (xg < w) {
            int ld = Math.min(300, w + 2 - xg);
            blit(g, PERCHE, xg, YP - 1, 0, 8, ld, 8, 300, 16);
        }
    }

    /**
     * Indicateur sonore en haut à gauche : croche + titre de la piste + égaliseur (animé si la musique joue) ;
     * seconde ligne « Voix off » (onde animée pendant que la voix parle). Renvoie le rectangle cliquable de la voix.
     */
    static int[] chip(GuiGraphicsExtractor g, Font f, int x, int y, String titre, boolean voix, boolean musique,
                      boolean parle, boolean coupee, float a) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        blit(g, CHIP, x, y, 0, 0, 16, 16, 32, 16, argb(0xFFFFFF, a));
        Component c = inter(titre);
        float w = inter(g, f, c, x + 20, y + 7, 8, alpha(0x96F5E9D0, a), 0);
        int bx = Math.round(x + 20 + w + 6);
        int[] hh = {3, 6, 4, 7};
        for (int i = 0; i < 4; i++) {
            int h = musique ? 2 + Math.round(5 * (0.5f + 0.5f * (float) Math.sin(t * (3.1f + i * 1.3f) + i * 1.7f))) : hh[i];
            if (!musique) h = 1;
            g.fill(bx + i * 3, y + 11 - h, bx + i * 3 + 2, y + 11, alpha(R_OR[i % 2 == 1 ? 4 : 5], a));
        }
        if (!voix) return null;
        int vy = y + 18;
        blit(g, CHIP, x, vy, 16, 0, 16, 16, 32, 16, argb(0xFFFFFF, a));
        if (parle && !coupee) {
            // onde animée par-dessus l'icône cuite
            g.fill(x + 2, vy + 2, x + 12, vy + 12, alpha(0xFF0F090B, a));
            for (int i = 0; i < 5; i++) {
                int h = 2 + Math.round(6 * (0.5f + 0.5f * (float) Math.sin(t * (9f + i * 2.3f) + i)));
                g.fill(x + 3 + i * 2, vy + 7 - h / 2, x + 4 + i * 2, vy + 7 - h / 2 + h, alpha(OR, a));
            }
        }
        float w2 = inter(g, f, inter(coupee ? "Voix coupée" : "Voix off"), x + 20, vy + 7, 8,
            alpha(coupee ? 0x80C2B59A : 0x96F5E9D0, a), 0);
        if (coupee) g.fill(x + 1, vy + 7, x + 13, vy + 8, alpha(0xFFA0182B, a));
        return new int[]{x, vy, Math.round(20 + w2), 14};
    }

    /** Fil des huit sceaux (étapes de la création). */
    static void progression(GuiGraphicsExtractor g, Font f, int cx, int etape, float a) {
        progression(g, f, cx, etape, a, null);
    }

    /** Fil d'étapes ; {@code verrous[i]} : petit cadenas au coin du sceau i (valeur reprise de la candidature, J9). */
    static void progression(GuiGraphicsExtractor g, Font f, int cx, int etape, float a, boolean[] verrous) {
        int al = argb(0xFFFFFF, a);
        blit(g, PROGRESSION, cx - 280, 0, 0, 0, 560, 32, 560, 32, al);
        if ((al >>> 24) == 0) return;
        // les huit sceaux (un lot), puis leurs libellés (un lot de textes) : chaque libellé ne touche que son sceau
        LotBlit sceaux = LotBlit.ouvrir(g, SCEAUX, 240, 102);
        for (int i = 0; i < 8; i++) {
            int etat = i < etape ? 0 : i == etape ? 1 : 2;
            sceaux.blit(cx - 217 + 62 * i - 15, 10, i * 30, etat * 34, 30, 34, al);
        }
        sceaux.fermer();
        LotGui.Textes libs = LotGui.textes(g);
        for (int i = 0; i < 8; i++) {
            int x = cx - 217 + 62 * i;
            int etat = i < etape ? 0 : i == etape ? 1 : 2;
            int col = etat == 0 ? 0xFFB89A6A : etat == 1 ? CREME : 0xFF7A6A56;
            arcFin(libs, g, f, arcade(ETAPES[i]), x, etat == 1 ? 44 : 41, alpha(col, a), 1, etat == 1);
        }
        libs.fermer();
        if (verrous == null || a < 0.5f) return;
        for (int i = 0; i < 8 && i < verrous.length; i++) {
            if (verrous[i]) cadenas(g, cx - 217 + 62 * i + 9, 8);
        }
    }

    /**
     * Question de l'étape (ArcadePix ×1,5) lue par la voix : la partie dite est crème et ombrée, le reste attend à
     * 37 % ; onde or à gauche, animée pendant que la voix parle.
     */
    static void question(GuiGraphicsExtractor g, Font f, int cx, int y, String texte, int dits, boolean parle,
                         boolean onde, float a) {
        String t = sansAccents(texte);
        Component tout = arcade(t);
        float s = 1.5f;
        float w = f.width(tout) * s;
        float x0 = Math.round(cx - w / 2f);
        texteEchelle(g, f, tout, x0, y, alpha(0x5FF5E9D0, a), s);
        if (dits > 0) {
            Component dit = arcade(t.substring(0, Math.min(t.length(), dits)));
            texteEchelle(g, f, dit, x0 + 1, y + 1, alpha(0xFF3D3A34, a), s);
            texteEchelle(g, f, dit, x0, y, alpha(CREME, a), s);
        }
        if (!onde) return;
        float tt = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        int[] hh = {3, 6, 8, 5, 2};
        int ox = Math.round(x0 - 18), oy = y + 3;
        for (int i = 0; i < 5; i++) {
            int h = parle ? 2 + Math.round(6 * (0.5f + 0.5f * (float) Math.sin(tt * (8f + i * 2f) + i * 1.3f))) : hh[i];
            g.fill(ox + i * 2, oy + 4 - h / 2, ox + i * 2 + 1, oy + 4 - h / 2 + h, alpha(OR, a));
        }
    }

    /** Une ligne du registre : texte, emblème (index de l'atlas, −1 = aucun), nuancier (couleurs, ou null). */
    record Ligne(String texte, int embleme, int[] nuancier) {}

    static final String[] LIGNES_REG = {"VILLAGE", "CLAN", "SEXE", "NOM", "ÂGE", "TAILLE", "APPARENCE"};

    /** Le registre qui se remplit : lignes remplies (puce or), ligne en cours (filet rouge, or clair), à venir (—). */
    static void registre(GuiGraphicsExtractor g, Font f, int x, int y, Ligne[] valeurs, int enCours, float a) {
        registre(g, f, x, y, valeurs, enCours, a, null);
    }

    /** Registre ; {@code verrous[k]} : la ligne k finit par un cadenas au lieu de la puce (reprise de la candidature, J9). */
    static void registre(GuiGraphicsExtractor g, Font f, int x, int y, Ligne[] valeurs, int enCours, float a,
                         boolean[] verrous) {
        int X = x + 14, CV = 74, WL = 212;
        float yy = y + 46;
        // aplats (repère, nuanciers) et textes du registre : un lot chacun ; aucun texte ne touche un aplat, un emblème
        // ou une puce posés entre-temps
        LotGui aplats = LotGui.ouvrir(g);
        LotGui.Textes txt = LotGui.textes(g);
        for (int k = 0; k < 7; k++) {
            Ligne v = valeurs[k];
            boolean cur = k == enCours;
            etiq(txt, g, f, LIGNES_REG[k], X + (cur ? 6 : 0), yy, alpha(cur ? OR_CLAIR : v != null ? OR_ETIQ : 0xFF6A5A44, a));
            if (cur) {
                aplats.fill(X, Math.round(yy) - 6, X + 2, Math.round(yy) + 6, alpha(ROUGE, a));
                aplats.fill(X, Math.round(yy) - 6, X + 2, Math.round(yy) - 5, alpha(0xFFE26474, a));
            }
            if (v != null) {
                float vx = X + CV;
                if (v.embleme() >= 0) {
                    embleme(g, v.embleme(), vx - 1, yy - 8, 16, alpha(cur ? OR_CLAIR : CREME, a));
                    vx += 20;
                }
                if (v.nuancier() != null) {
                    for (int j = 0; j < v.nuancier().length; j++) {
                        int sx = Math.round(vx + j * 13), sy = Math.round(yy) - 6;
                        int c = v.nuancier()[j];
                        aplats.fill(sx - 1, sy, sx + 11, sy + 12, alpha(R_OR[2], a));
                        aplats.fill(sx, sy + 1, sx + 10, sy + 11, alpha(opaque(c), a));
                        aplats.fill(sx, sy + 1, sx + 10, sy + 2, alpha(opaque(mix(c, 0xFFFFFF, 0.3f)), a));
                    }
                    vx += v.nuancier().length * 13 + 4;
                }
                if (v.texte() != null && !v.texte().isEmpty()) {
                    // ajusté à la place restante avant la puce (taille 9 → 7, puis coupé)
                    float dispo = X + WL - 10 - vx, t = 9f;
                    String texte = v.texte();
                    while (largeur(f, inter(texte), t) > dispo && t > 7f) t -= 0.5f;
                    while (largeur(f, inter(texte), t) > dispo && texte.length() > 2) {
                        texte = texte.substring(0, texte.length() - (texte.endsWith("…") ? 2 : 1)).strip() + "…";
                    }
                    inter(txt, g, f, inter(texte), vx, yy, t, alpha(cur ? OR_CLAIR : CREME, a), 0);
                }
                if (verrous != null && k < verrous.length && verrous[k]) cadenas(g, X + WL - 7, Math.round(yy) - 4);
                else if (!cur) losange(g, X + WL - 4, Math.round(yy) - 1);
            } else {
                inter(txt, g, f, inter("—"), X + CV, yy, 9, alpha(0xFF5A4E42, a), 0);
            }
            yy += 24;
        }
        aplats.fermer();
        txt.fermer();
    }
}
