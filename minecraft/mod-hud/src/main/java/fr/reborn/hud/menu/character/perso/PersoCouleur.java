package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.function.IntConsumer;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/**
 * Sélecteur de couleur libre (cheveux, yeux) de l'étape Apparence, dans le kit laque et or : carré saturation ×
 * luminosité, barre de teinte, aperçu, champ hexadécimal. Le carré est une texture recalculée seulement quand la
 * teinte change (128×128), la barre de teinte une texture faite une fois : rien n'est calculé par pixel à chaque
 * image. Chaque changement est appliqué tout de suite au personnage (via {@code applique}).
 *
 * <p><b>Public depuis le 18/10</b> (teinture du bandeau, {@code menu/cosmetic/TeintureScreen}) : réutilisable hors de la
 * création de personnage (titre, couleur de départ, rappel à chaque changement) ; l'API n'a pas changé, seulement sa
 * visibilité.
 */
public final class PersoCouleur {

    public static final int W = 236, H = 166;
    private static final int SQ = 100, BAR = 12;
    private static final Identifier SV = Identifier.fromNamespaceAndPath("reborn", "perso/couleur_sv"),
        TEINTE = Identifier.fromNamespaceAndPath("reborn", "perso/couleur_teinte");
    private static DynamicTexture svTex;
    private static NativeImage svImg;
    private static float svTeinte = -1;
    private static boolean teintePrete;

    private final String titre;
    private final int initial;
    private final IntConsumer applique;
    private float h, s, v;
    private String hex;
    private int drag;           // 1 carré, 2 teinte
    public int x, y;            // coin (GUI, repère de l'écran)

    public PersoCouleur(String titre, int argb, IntConsumer applique) {
        this.titre = titre;
        this.initial = argb & 0xFFFFFF;
        this.applique = applique;
        float[] hsv = SkinSpec.argbToHsv(argb);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        hex = String.format(Locale.ROOT, "%06X", initial);
    }

    public int couleur() { return SkinSpec.hsvToArgb(h, s, v) & 0xFFFFFF; }

    public void annuler() { applique.accept(initial); }

    /* ------------------------------------------------------------------ saisie */

    /** Vrai si le clic est pris (dans le panneau) ; {@code fermer[0]} = vrai si OK. */
    public boolean clic(double mx, double my, boolean[] fermer) {
        if (!dans(mx, my, x, y, W, H)) return false;
        if (dans(mx, my, x + 12, y + 30, SQ, SQ)) { drag = 1; glisser(mx, my); return true; }
        if (dans(mx, my, x + 12 + SQ + 8, y + 30, BAR, SQ)) { drag = 2; glisser(mx, my); return true; }
        int[] ok = rectOk();
        if (dans(mx, my, ok[0], ok[1], ok[2], ok[3])) fermer[0] = true;
        return true;
    }

    public boolean glisser(double mx, double my) {
        if (drag == 1) {
            s = (float) Math.max(0, Math.min(1, (mx - x - 12) / (SQ - 1)));
            v = (float) Math.max(0, Math.min(1, 1 - (my - y - 30) / (SQ - 1)));
        } else if (drag == 2) {
            h = (float) Math.max(0, Math.min(0.9999, (my - y - 30) / (SQ - 1)));
        } else {
            return false;
        }
        majHex();
        applique.accept(couleur());
        return true;
    }

    public boolean relacher() {
        boolean b = drag != 0;
        drag = 0;
        return b;
    }

    public boolean glisse() { return drag != 0; }

    public boolean car(char c) {
        if (Character.digit(c, 16) < 0) return false;
        if (hex.length() >= 6) hex = "";
        hex += Character.toUpperCase(c);
        if (hex.length() == 6) {
            int rgb = Integer.parseInt(hex, 16);
            float[] hsv = SkinSpec.argbToHsv(0xFF000000 | rgb);
            h = hsv[0];
            s = hsv[1];
            v = hsv[2];
            applique.accept(rgb);
        }
        return true;
    }

    public void effacer() { if (!hex.isEmpty()) hex = hex.substring(0, hex.length() - 1); }

    private void majHex() { hex = String.format(Locale.ROOT, "%06X", couleur()); }

    private int[] rectOk() { return new int[]{x + W - 12 - B_W[B_OUI], y + H - 28, B_W[B_OUI], 20}; }

    /* ------------------------------------------------------------------ dessin */

    public void dessiner(GuiGraphicsExtractor g, Font f, int mx, int my, long now) {
        textures();
        // panneau laqué, double filet or, losanges aux coins
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xC0000000);
        g.fill(x, y, x + W, y + H, R_OR[3]);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFF140B0D);
        g.fill(x + 3, y + 3, x + W - 3, y + H - 3, R_OR[1]);
        g.fill(x + 4, y + 4, x + W - 4, y + H - 4, 0xFF1A0F12);
        losange(g, x + 2, y + 2);
        losange(g, x + W - 5, y + 2);
        losange(g, x + 2, y + H - 5);
        losange(g, x + W - 5, y + H - 5);
        etiq(g, f, titre, x + 12, y + 16, OR_CLAIR);
        g.fill(x + 12, y + 23, x + W - 12, y + 24, alpha(R_OR[3], 0.8f));
        // carré S × V et barre de teinte (textures)
        int qx = x + 12, qy = y + 30, bx = qx + SQ + 8;
        g.fill(qx - 1, qy - 1, qx + SQ + 1, qy + SQ + 1, R_OR[3]);
        g.blit(RenderPipelines.GUI_TEXTURED, SV, qx, qy, 0, 0, SQ, SQ, 128, 128, 128, 128);
        g.fill(bx - 1, qy - 1, bx + BAR + 1, qy + SQ + 1, R_OR[3]);
        g.blit(RenderPipelines.GUI_TEXTURED, TEINTE, bx, qy, 0, 0, BAR, SQ, 1, 128, 1, 128);
        // repères
        int sx = qx + Math.round(s * (SQ - 1)), sy = qy + Math.round((1 - v) * (SQ - 1));
        int rc = v > 0.6f ? 0xFF120A0C : 0xFFF5E9D0;
        g.fill(sx - 3, sy, sx - 1, sy + 1, rc);
        g.fill(sx + 2, sy, sx + 4, sy + 1, rc);
        g.fill(sx, sy - 3, sx + 1, sy - 1, rc);
        g.fill(sx, sy + 2, sx + 1, sy + 4, rc);
        int hy = qy + Math.round(h * (SQ - 1));
        g.fill(bx - 3, hy - 1, bx + BAR + 3, hy + 2, R_OR[6]);
        g.fill(bx - 2, hy, bx + BAR + 2, hy + 1, 0xFF120A0C);
        // aperçu, ancienne couleur, hex
        int px = bx + BAR + 12, pw = x + W - 12 - px;
        etiq(g, f, "APERCU", px, qy + 3, OR_ETIQ);
        g.fill(px - 1, qy + 9, px + pw + 1, qy + 41, R_OR[3]);
        g.fill(px, qy + 10, px + pw / 2, qy + 40, opaque(couleur()));
        g.fill(px + pw / 2, qy + 10, px + pw, qy + 40, opaque(initial));
        g.fill(px, qy + 10, px + pw, qy + 11, alpha(0xFFFFFFFF, 0.25f));
        inter(g, f, inter("nouvelle"), px + pw / 4f, qy + 47, 7, DOUX, 1);
        inter(g, f, inter("avant"), px + pw * 3 / 4f, qy + 47, 7, DOUX, 1);
        etiq(g, f, "CODE", px, qy + 62, OR_ETIQ);
        g.fill(px - 1, qy + 68, px + pw + 1, qy + 84, R_OR[3]);
        g.fill(px, qy + 69, px + pw, qy + 83, 0xFF0C0608);
        String t = "#" + hex + ((now / 500) % 2 == 0 && hex.length() < 6 ? "_" : "");
        inter(g, f, interGras(t), px + 5, qy + 76, 9, CREME, 0);
        inter(g, f, inter("Clavier : 6 chiffres"), px, qy + 92, 7, DOUX, 0);
        int[] ok = rectOk();
        bouton(g, f, B_OUI, dans(mx, my, ok[0], ok[1], ok[2], ok[3]) ? 1 : 0, ok[0], ok[1], "VALIDER");
        inter(g, f, inter("Échap : annuler · Entrée : valider"), x + 12, y + H - 17, 7, DOUX, 0);
    }

    private void textures() {
        var tm = Minecraft.getInstance().getTextureManager();
        if (!teintePrete) {
            NativeImage im = new NativeImage(1, 128, true);
            for (int j = 0; j < 128; j++) im.setPixel(0, j, SkinSpec.hsvToArgb(j / 128f, 1f, 1f));
            tm.register(TEINTE, new DynamicTexture(() -> "reborn-teinte", im));
            teintePrete = true;
        }
        if (svTex == null) {
            svImg = new NativeImage(128, 128, true);
            svTex = new DynamicTexture(() -> "reborn-sv", svImg);
            tm.register(SV, svTex);
        }
        if (Math.abs(svTeinte - h) > 1e-4f) {
            svTeinte = h;
            for (int j = 0; j < 128; j++) {
                for (int i = 0; i < 128; i++) svImg.setPixel(i, j, SkinSpec.hsvToArgb(h, i / 127f, 1f - j / 127f));
            }
            svTex.upload();
        }
    }
}
