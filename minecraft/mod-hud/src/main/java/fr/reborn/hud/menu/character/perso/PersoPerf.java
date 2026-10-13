package fr.reborn.hud.menu.character.perso;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Mesure du coût des écrans perso, <b>inactive en production</b> (seulement sous {@code REBORN_PERSO_DEBUG=perf}) :
 * temps d'extraction (CPU, par image), intervalle entre deux images (→ FPS réels), et, une image sur 30, le nombre
 * d'éléments que l'écran a déposés dans l'état GUI (quads, textes, couches de l'algorithme d'empilement).
 */
final class PersoPerf {

    private PersoPerf() {}

    static boolean actif;
    private static long t0, derniere;
    private static long somExtract, somIntervalle, nb, nbIntervalle;
    private static int images;
    static int elements, textes, couches, pips;

    static void reset() {
        somExtract = somIntervalle = nb = nbIntervalle = 0;
        derniere = 0;
        elements = textes = couches = pips = 0;
    }

    static void debut() {
        if (!actif) return;
        t0 = System.nanoTime();
        if (derniere != 0) {
            somIntervalle += t0 - derniere;
            nbIntervalle++;
        }
        derniere = t0;
    }

    static void fin(GuiGraphicsExtractor g) {
        if (!actif) return;
        somExtract += System.nanoTime() - t0;
        nb++;
        if (++images % 30 == 0) compter(g);
    }

    /** Appelé hors écran perso (monde seul) pour la référence. */
    static void image() {
        if (!actif) return;
        long t = System.nanoTime();
        if (derniere != 0) {
            somIntervalle += t - derniere;
            nbIntervalle++;
        }
        derniere = t;
    }

    static String bilan() {
        double ext = nb == 0 ? 0 : somExtract / (double) nb / 1e6;
        double fr = nbIntervalle == 0 ? 0 : somIntervalle / (double) nbIntervalle / 1e6;
        return String.format(java.util.Locale.ROOT, "fps=%.0f image=%.2fms extraction=%.2fms elements=%d textes=%d couches=%d pip=%d",
            fr == 0 ? 0 : 1000 / fr, fr, ext, elements, textes, couches, pips);
    }

    private static Field fEtat, fStrata, fUp, fEl, fGl, fTx, fPip;

    private static void compter(GuiGraphicsExtractor g) {
        try {
            if (fEtat == null) {
                fEtat = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
                fEtat.setAccessible(true);
                Class<?> st = fEtat.getType();
                fStrata = st.getDeclaredField("strata");
                fStrata.setAccessible(true);
                Class<?> node = Class.forName(st.getName() + "$Node");
                fUp = node.getDeclaredField("up");
                fEl = node.getDeclaredField("elementStates");
                fGl = node.getDeclaredField("glyphStates");
                fTx = node.getDeclaredField("textStates");
                fPip = node.getDeclaredField("picturesInPictureStates");
                for (Field f : new Field[]{fUp, fEl, fGl, fTx, fPip}) f.setAccessible(true);
            }
            Object etat = fEtat.get(g);
            int e = 0, t = 0, c = 0, p = 0;
            for (Object n0 : (List<?>) fStrata.get(etat)) {
                for (Object n = n0; n != null; n = fUp.get(n)) {
                    c++;
                    e += taille(fEl.get(n)) + taille(fGl.get(n));
                    t += taille(fTx.get(n));
                    p += taille(fPip.get(n));
                }
            }
            elements = e;
            textes = t;
            couches = c;
            pips = p;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            elements = -1;
        }
    }

    private static int taille(Object l) { return l instanceof List<?> li ? li.size() : 0; }
}
