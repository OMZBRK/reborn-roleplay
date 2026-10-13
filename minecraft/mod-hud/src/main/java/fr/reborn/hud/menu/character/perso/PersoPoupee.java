package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Personnage « poupée » : le modèle joueur (boîtes et seconde couche du skin) rendu UNE fois, en logiciel, dans une
 * image à la résolution de l'écran (z-buffer, test alpha, éclairage d'interface des entités de Minecraft), puis
 * affiché en UN blit tant que le skin, l'angle et l'échelle ne changent pas (cache : {@link PersoSkins#poupee}).
 * Sert aux personnages SECONDAIRES (voisins de la sélection, reflets, vignettes de tenues et de coiffures, vues du
 * dossier) avec le même rendu que le personnage principal (rendu d'entité) : mêmes lumières
 * ({@code ENTITY_IN_UI} : deux directionnelles + 40 % d'ambiante), même projection orthographique.
 *
 * <p>Repère : texels du skin, pieds en y = 0, le modèle regarde +z (vers l'écran) ; lacet > 0 : flanc droit du
 * personnage visible.
 */
final class PersoPoupee {

    private PersoPoupee() {}

    /** Boîte : min (x, y, z), taille (w, h, d), origine UV, gonflement (seconde couche). */
    private record Boite(float x, float y, float z, int w, int h, int d, int u, int v, float g) {}

    private static List<Boite> modele(boolean slim) {
        int aw = slim ? 3 : 4;
        List<Boite> b = new ArrayList<>();
        b.add(new Boite(-4, 24, -4, 8, 8, 8, 0, 0, 0));
        b.add(new Boite(-4, 12, -2, 8, 12, 4, 16, 16, 0));
        b.add(new Boite(-4 - aw, 12, -2, aw, 12, 4, 40, 16, 0));
        b.add(new Boite(4, 12, -2, aw, 12, 4, 32, 48, 0));
        b.add(new Boite(-4, 0, -2, 4, 12, 4, 0, 16, 0));
        b.add(new Boite(0, 0, -2, 4, 12, 4, 16, 48, 0));
        b.add(new Boite(-4, 24, -4, 8, 8, 8, 32, 0, 0.5f));
        b.add(new Boite(-4, 12, -2, 8, 12, 4, 16, 32, 0.25f));
        b.add(new Boite(-4 - aw, 12, -2, aw, 12, 4, 40, 32, 0.25f));
        b.add(new Boite(4, 12, -2, aw, 12, 4, 48, 48, 0.25f));
        b.add(new Boite(-4, 0, -2, 4, 12, 4, 0, 32, 0.25f));
        b.add(new Boite(0, 0, -2, 4, 12, 4, 0, 48, 0.25f));
        return b;
    }

    private static final List<Boite> LARGE = modele(false), FINE = modele(true);

    /* Lumières ENTITY_IN_UI de Minecraft (INVENTORY_DIFFUSE_LIGHT_0/1), exprimées dans le repère écran (x à droite,
       y en haut, z vers le spectateur) : le rendu d'entité en interface retourne le modèle de 180° autour de z. */
    private static final float[] L0 = norm(-0.2f, 1f, 1f), L1 = norm(0.2f, 1f, 0f);

    private static float[] norm(float x, float y, float z) {
        float n = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / n, y / n, z / n};
    }

    /** Image rendue : texture, taille en pixels, ancre des pieds (pixels), pixels par texel. */
    record Image(Identifier id, int w, int h, float ax, float ay, float p) {}

    /* ------------------------------------------------------------------ affichage */

    /**
     * Dessine la poupée, pieds en (cx, piedsY) GUI, {@code s} GUI par texel. {@code sRef} : échelle de l'image en cache
     * (≠ s pendant un glissement : l'image est étirée au lieu d'être recalculée à chaque image). {@code reflet} :
     * renversée sous les pieds (reflet dans l'eau). {@code argb} : teinte multipliée (alpha = opacité).
     */
    static void dessiner(GuiGraphicsExtractor g, PersoSkins skins, Identifier skin, boolean slim, float cx, float piedsY,
                         float s, float sRef, float lacet, float tangage, int argb, boolean reflet) {
        if (skin == null || s <= 0 || (argb >>> 24) == 0) return;
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        Image im = skins.poupee(skin, slim, lacet, tangage, sRef * gs);
        if (im == null) return;
        float k = s / sRef / gs;                       // GUI par pixel de l'image
        g.pose().pushMatrix();
        g.pose().translate(PersoArt.cale(cx), PersoArt.cale(piedsY));
        g.pose().scale(k, k);
        if (reflet) {
            // rangée j de l'image → y = ay − j (miroir autour de la ligne des pieds) ; région de texture négative
            g.blit(RenderPipelines.GUI_TEXTURED, im.id(), Math.round(-im.ax()), Math.round(im.ay() - im.h()), 0, im.h(),
                im.w(), im.h(), im.w(), -im.h(), im.w(), im.h(), argb);
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, im.id(), Math.round(-im.ax()), Math.round(-im.ay()), 0, 0, im.w(), im.h(),
                im.w(), im.h(), im.w(), im.h(), argb);
        }
        g.pose().popMatrix();
    }

    static void dessiner(GuiGraphicsExtractor g, PersoSkins skins, Identifier skin, boolean slim, float cx, float piedsY,
                         float s, float lacet, float tangage, int argb) {
        dessiner(g, skins, skin, slim, cx, piedsY, s, s, lacet, tangage, argb, false);
    }

    /* ------------------------------------------------------------------ rendu logiciel (une fois) */

    /**
     * Rend le modèle dans une image ARGB : {@code p} pixels par texel ; le résultat a ses pieds en (ax, ay). Coût :
     * quelques dizaines de milliers de pixels testés, une fois par (skin, angle, échelle).
     */
    static NativeImage rendre(int[] skin, boolean slim, float lacet, float tangage, float p, float[] ancre) {
        double th = Math.toRadians(lacet), ph = Math.toRadians(tangage);
        float ct = (float) Math.cos(th), st = (float) Math.sin(th), cp = (float) Math.cos(ph), sp = (float) Math.sin(ph);
        List<Boite> boites = slim ? FINE : LARGE;
        // emprise projetée
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Boite b : boites) {
            for (int c = 0; c < 8; c++) {
                float x = (c & 1) == 0 ? b.x() - b.g() : b.x() + b.w() + b.g();
                float y = (c & 2) == 0 ? b.y() - b.g() : b.y() + b.h() + b.g();
                float z = (c & 4) == 0 ? b.z() - b.g() : b.z() + b.d() + b.g();
                float xr = x * ct + z * st, zr = -x * st + z * ct;
                float sx = xr * p, sy = (-y * cp + zr * sp) * p;
                minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            }
        }
        int ox = (int) Math.floor(minX) - 1, oy = (int) Math.floor(minY) - 1;
        int w = (int) Math.ceil(maxX) + 2 - ox, h = (int) Math.ceil(maxY) + 2 - oy;
        float ax = -ox, ay = -oy;
        int[] out = new int[w * h];
        float[] zb = new float[w * h];
        java.util.Arrays.fill(zb, -Float.MAX_VALUE);
        float[] pr = {ct, st, cp, sp, p, ax, ay};
        for (Boite b : boites) {
            float x0 = b.x() - b.g(), y0 = b.y() - b.g(), z0 = b.z() - b.g();
            float x1 = b.x() + b.w() + b.g(), y1 = b.y() + b.h() + b.g(), z1 = b.z() + b.d() + b.g();
            int u = b.u(), v = b.v(), bw = b.w(), bh = b.h(), d = b.d();
            boolean couche = b.g() > 0;
            float sxW = (x1 - x0) / bw, sy = (y1 - y0) / bh, sz = (z1 - z0) / d;
            // dessus : V vers l'avant
            face(out, zb, w, h, skin, pr, 0, 1, 0, x0, y1, z0, sxW, 0, 0, 0, 0, sz, u + d, v, bw, d, couche);
            // dessous (visible en contre-plongée seulement)
            face(out, zb, w, h, skin, pr, 0, -1, 0, x0, y0, z0, sxW, 0, 0, 0, 0, sz, u + d + bw, v, bw, d, couche);
            // flanc droit du perso (x min) : U vers l'avant
            face(out, zb, w, h, skin, pr, -1, 0, 0, x0, y1, z0, 0, 0, sz, 0, -sy, 0, u, v + d, d, bh, couche);
            // face
            face(out, zb, w, h, skin, pr, 0, 0, 1, x0, y1, z1, sxW, 0, 0, 0, -sy, 0, u + d, v + d, bw, bh, couche);
            // flanc gauche (x max) : U vers l'arrière
            face(out, zb, w, h, skin, pr, 1, 0, 0, x1, y1, z1, 0, 0, -sz, 0, -sy, 0, u + d + bw, v + d, d, bh, couche);
            // dos
            face(out, zb, w, h, skin, pr, 0, 0, -1, x1, y1, z0, -sxW, 0, 0, 0, -sy, 0, u + 2 * d + bw, v + d, bw, bh, couche);
        }
        NativeImage img = new NativeImage(w, h, true);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) img.setPixel(i, j, out[j * w + i]);
        ancre[0] = ax;
        ancre[1] = ay;
        return img;
    }

    /** Une face : origine O, vecteurs par texel U (largeur) et V (hauteur) en espace modèle, région (u, v, fw, fh). */
    private static void face(int[] out, float[] zb, int w, int h, int[] skin, float[] pr, float nx, float ny, float nz,
                             float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz,
                             int u, int v, int fw, int fh, boolean couche) {
        float ct = pr[0], st = pr[1], cp = pr[2], sp = pr[3], p = pr[4], ax = pr[5], ay = pr[6];
        // normale dans le repère écran (x droite, y haut, z vers le spectateur)
        float nxr = nx * ct + nz * st, nzr = -nx * st + nz * ct;
        float nsy = ny * cp - nzr * sp, nsz = ny * sp + nzr * cp;
        if (nsz <= 1e-3f) return;
        float lum = Math.min(1f, 0.4f + 0.6f * (Math.max(0f, nxr * L0[0] + nsy * L0[1] + nsz * L0[2])
            + Math.max(0f, nxr * L1[0] + nsy * L1[1] + nsz * L1[2])));
        // projection : écran x = xr·p, écran y (bas) = (−y·cp + zr·sp)·p ; profondeur = y·sp + zr·cp
        float oxr = ox * ct + oz * st, ozr = -ox * st + oz * ct;
        float px = ax + oxr * p, py = ay + (-oy * cp + ozr * sp) * p, pd = oy * sp + ozr * cp;
        float uxr = ux * ct + uz * st, uzr = -ux * st + uz * ct;
        float pux = uxr * p, puy = (-uy * cp + uzr * sp) * p, pud = uy * sp + uzr * cp;
        float vxr = vx * ct + vz * st, vzr = -vx * st + vz * ct;
        float pvx = vxr * p, pvy = (-vy * cp + vzr * sp) * p, pvd = vy * sp + vzr * cp;
        float det = pux * pvy - puy * pvx;
        if (Math.abs(det) < 1e-5f) return;
        // emprise du quad
        float xa = px, xb = px + pux * fw, xc = px + pvx * fh, xd = px + pux * fw + pvx * fh;
        float ya = py, yb = py + puy * fw, yc = py + pvy * fh, yd = py + puy * fw + pvy * fh;
        int i0 = Math.max(0, (int) Math.floor(Math.min(Math.min(xa, xb), Math.min(xc, xd))));
        int i1 = Math.min(w - 1, (int) Math.ceil(Math.max(Math.max(xa, xb), Math.max(xc, xd))));
        int j0 = Math.max(0, (int) Math.floor(Math.min(Math.min(ya, yb), Math.min(yc, yd))));
        int j1 = Math.min(h - 1, (int) Math.ceil(Math.max(Math.max(ya, yb), Math.max(yc, yd))));
        float inv = 1f / det;
        float eps = 0.02f;
        float biais = couche ? 0.01f : 0f;
        for (int j = j0; j <= j1; j++) {
            float dy = j + 0.5f - py;
            for (int i = i0; i <= i1; i++) {
                float dx = i + 0.5f - px;
                float a = (dx * pvy - dy * pvx) * inv;
                if (a < -eps || a > fw + eps) continue;
                float b = (pux * dy - puy * dx) * inv;
                if (b < -eps || b > fh + eps) continue;
                int ta = Math.min(fw - 1, Math.max(0, (int) a)), tb = Math.min(fh - 1, Math.max(0, (int) b));
                int tu = u + ta, tv = v + tb;
                if (tu < 0 || tu >= 64 || tv < 0 || tv >= 64) continue;
                int c = skin[tv * 64 + tu];
                int al = c >>> 24;
                if (al < (couche ? 26 : 8)) continue;
                float dep = pd + a * pud + b * pvd + biais;
                int k = j * w + i;
                if (dep <= zb[k]) continue;
                int r = Math.round(((c >> 16) & 0xFF) * lum), g = Math.round(((c >> 8) & 0xFF) * lum),
                    bl = Math.round((c & 0xFF) * lum);
                if (al < 250 && (out[k] >>> 24) != 0) {
                    int o = out[k];
                    float t = al / 255f;
                    r = Math.round(((o >> 16) & 0xFF) * (1 - t) + r * t);
                    g = Math.round(((o >> 8) & 0xFF) * (1 - t) + g * t);
                    bl = Math.round((o & 0xFF) * (1 - t) + bl * t);
                    al = 255;
                } else if (!couche) {
                    al = 255;
                }
                out[k] = (al << 24) | (r << 16) | (g << 8) | bl;
                zb[k] = dep;
            }
        }
    }
}
