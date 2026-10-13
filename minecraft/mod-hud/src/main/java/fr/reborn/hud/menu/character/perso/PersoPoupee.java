package fr.reborn.hud.menu.character.perso;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Personnage « poupée » : le modèle joueur dessiné en projection orthographique par des blits affines de sa texture
 * de skin (une face de boîte = un quad), triés du fond vers l'avant, avec l'éclairage d'interface de la maquette
 * (dessus 100 %, face 86 %, flancs 74 / 62 %). Sert aux personnages SECONDAIRES (voisins de la sélection, vignettes de
 * tenues et de coiffures, vues du dossier, reflet dans l'eau) : un seul rendu d'entité par image reste réservé au
 * personnage principal (le renderer « image dans l'image » des entités n'a qu'une texture par image).
 *
 * <p>Repère : texels du skin, pieds en y = 0, le modèle regarde +z (vers l'écran) ; lacet > 0 : flanc droit de
 * l'écran visible.
 */
final class PersoPoupee {

    private PersoPoupee() {}

    /** Boîte : min (x, y, z), taille (w, h, d), origine UV, gonflement (seconde couche). */
    private record Boite(float x, float y, float z, int w, int h, int d, int u, int v, float g) {}

    private record Face(float depth, float ox, float oy, float ux, float uy, float vx, float vy, int u, int v, int w,
                        int h, float lum, boolean flipV) {}

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

    /**
     * Dessine la poupée, pieds en (cx, piedsY) GUI, {@code s} GUI par texel. {@code reflet} : renversée sous les pieds
     * (reflet dans l'eau). {@code argb} : teinte multipliée (alpha = opacité).
     */
    static void dessiner(GuiGraphicsExtractor g, Identifier skin, boolean slim, float cx, float piedsY, float s,
                         float lacet, float tangage, int argb, boolean reflet) {
        if (skin == null || s <= 0) return;
        double th = Math.toRadians(lacet), ph = Math.toRadians(tangage);
        float ct = (float) Math.cos(th), st = (float) Math.sin(th), cp = (float) Math.cos(ph), sp = (float) Math.sin(ph);
        List<Face> faces = new ArrayList<>(40);
        for (Boite b : slim ? FINE : LARGE) {
            float x0 = b.x() - b.g(), y0 = b.y() - b.g(), z0 = b.z() - b.g();
            float x1 = b.x() + b.w() + b.g(), y1 = b.y() + b.h() + b.g(), z1 = b.z() + b.d() + b.g();
            int u = b.u(), v = b.v(), w = b.w(), h = b.h(), d = b.d();
            float bias = b.g() > 0 ? 0.02f : 0f;
            // dessus : V vers l'avant
            ajouter(faces, 0, 1, 0, x0, y1, z0, (x1 - x0) / w, 0, 0, 0, 0, (z1 - z0) / d, u + d, v, w, d, 1.0f,
                ct, st, cp, sp, s, cx, piedsY, reflet, bias);
            // flanc droit du perso (x min) : U vers l'avant
            ajouter(faces, -1, 0, 0, x0, y1, z0, 0, 0, (z1 - z0) / d, 0, -(y1 - y0) / h, 0, u, v + d, d, h, 0.74f,
                ct, st, cp, sp, s, cx, piedsY, reflet, bias);
            // face
            ajouter(faces, 0, 0, 1, x0, y1, z1, (x1 - x0) / w, 0, 0, 0, -(y1 - y0) / h, 0, u + d, v + d, w, h, 0.86f,
                ct, st, cp, sp, s, cx, piedsY, reflet, bias);
            // flanc gauche (x max) : U vers l'arrière
            ajouter(faces, 1, 0, 0, x1, y1, z1, 0, 0, -(z1 - z0) / d, 0, -(y1 - y0) / h, 0, u + d + w, v + d, d, h, 0.62f,
                ct, st, cp, sp, s, cx, piedsY, reflet, bias);
            // dos
            ajouter(faces, 0, 0, -1, x1, y1, z0, -(x1 - x0) / w, 0, 0, 0, -(y1 - y0) / h, 0, u + 2 * d + w, v + d, w, h, 0.55f,
                ct, st, cp, sp, s, cx, piedsY, reflet, bias);
        }
        faces.sort((a, c) -> Float.compare(a.depth(), c.depth()));
        int al = argb >>> 24;
        int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, bb = argb & 0xFF;
        for (Face f : faces) {
            int col = (al << 24) | (Math.round(r * f.lum()) << 16) | (Math.round(gg * f.lum()) << 8) | Math.round(bb * f.lum());
            g.pose().pushMatrix();
            g.pose().mul(new Matrix3x2f(f.ux(), f.uy(), f.vx(), f.vy(), f.ox(), f.oy()));
            if (f.flipV()) {
                g.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, f.u(), f.v() + f.h(), f.w(), f.h(), f.w(), -f.h(), 64, 64, col);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, f.u(), f.v(), f.w(), f.h(), 64, 64, col);
            }
            g.pose().popMatrix();
        }
    }

    /** Face d'origine O, vecteurs par texel U (largeur) et V (hauteur) en espace modèle. */
    private static void ajouter(List<Face> out, float nx, float ny, float nz, float ox, float oy, float oz,
                                float ux, float uy, float uz, float vx, float vy, float vz, int u, int v, int w, int h,
                                float lum, float ct, float st, float cp, float sp, float s, float cx, float py,
                                boolean reflet, float bias) {
        float nzr = -nx * st + nz * ct;
        if (ny * sp + nzr * cp <= 1e-3f) return;
        float[] o = proj(ox, oy, oz, ct, st, cp, sp, s, cx, py, reflet);
        float[] pu = vec(ux, uy, uz, ct, st, cp, sp, s, reflet);
        float[] pv = vec(vx, vy, vz, ct, st, cp, sp, s, reflet);
        // profondeur du centre de la face (vers le spectateur)
        float mx = ox + (ux * w + vx * h) / 2f, my = oy + (uy * w + vy * h) / 2f, mz = oz + (uz * w + vz * h) / 2f;
        float zr = -mx * st + mz * ct;
        float depth = my * sp + zr * cp + bias;
        float l = lum;
        if (ny > 0) l = 1.0f;
        // Le pipeline GUI élimine les quads retournés : une face à l'envers (reflet) est redessinée à l'endroit en
        // partant du bord opposé, texture lue de bas en haut.
        boolean flip = pu[0] * pv[1] - pu[1] * pv[0] < 0;
        if (flip) {
            o[0] += pv[0] * h;
            o[1] += pv[1] * h;
            pv[0] = -pv[0];
            pv[1] = -pv[1];
        }
        out.add(new Face(depth, o[0], o[1], pu[0], pu[1], pv[0], pv[1], u, v, w, h, l, flip));
    }

    private static float[] proj(float x, float y, float z, float ct, float st, float cp, float sp, float s, float cx,
                                float py, boolean reflet) {
        float xr = x * ct + z * st, zr = -x * st + z * ct;
        float sy = -y * cp + zr * sp;
        return new float[]{cx + s * xr, py + s * (reflet ? -sy : sy)};
    }

    private static float[] vec(float x, float y, float z, float ct, float st, float cp, float sp, float s, boolean reflet) {
        float xr = x * ct + z * st, zr = -x * st + z * ct;
        float sy = -y * cp + zr * sp;
        return new float[]{s * xr, s * (reflet ? -sy : sy)};
    }
}
