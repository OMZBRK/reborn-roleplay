package fr.reborn.hud.audit;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Compte des éléments déposés dans l'état GUI d'une image (DA §6, règle 7), <b>inactif en production</b> : utilisé
 * seulement par les bancs ({@link ResponsiveAudit} sous {@code REBORN_AUDIT=1}). Lecture par réflexion du
 * {@code GuiRenderState} : éléments (quads, blits, glyphes), textes, couches de l'algorithme d'empilement, PiP.
 */
public final class GuiCompte {

    private GuiCompte() {}

    public record Compte(int elements, int textes, int couches, int pips) {
        @Override
        public String toString() {
            return "elements=" + elements + " textes=" + textes + " couches=" + couches + " pip=" + pips;
        }
    }

    private static Field fEtat, fStrata, fUp, fEl, fGl, fTx, fPip;

    public static Compte compter(GuiGraphicsExtractor g) {
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
            return new Compte(e, t, c, p);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return new Compte(-1, -1, -1, -1);
        }
    }

    private static int taille(Object l) { return l instanceof List<?> li ? li.size() : 0; }
}
