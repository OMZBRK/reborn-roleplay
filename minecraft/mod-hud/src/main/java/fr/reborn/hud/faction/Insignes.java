package fr.reborn.hud.faction;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Accès public à l'atlas des insignes ({@code faction/insignes.png}) pour les autres écrans (Poste de garde › Outils ›
 * Factions) : un insigne de section ou l'emblème réel d'un village, net (rangée à la taille en pixels écran), dessiné
 * <b>tout de suite</b> (à appeler après avoir vidé son pinceau, sous le ciseau courant).
 */
public final class Insignes {

    private static final String[] NOMS = {"anbu", "police", "medical", "academie", "missions", "garde", "conseil",
            "bandeau", "lune", "konoha", "suna", "kiri", "kumo", "iwa", "ame"};
    private static final int[] TAILLES = {8, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32, 36, 40, 44, 48, 54, 60,
            64, 72, 80, 96};
    private static final int W, H;

    static {
        int h = 0, w = 0;
        for (int p : TAILLES) { h += p + 2; w = Math.max(w, NOMS.length * (p + 2)); }
        W = w;
        H = h;
    }

    private Insignes() {}

    /** Insigne {@code nom} (repli : missions), coin (x, y) et côté {@code taille} en GUI, teinte argb. */
    public static void poser(GuiGraphicsExtractor ctx, String nom, float x, float y, int taille, int col) {
        int i = 4;
        for (int j = 0; j < NOMS.length; j++) if (NOMS[j].equals(nom)) i = j;
        int k = FactionArt.echelle();
        int p = Math.round(taille * k), q = TAILLES[TAILLES.length - 1], yq = 0, acc = 0;
        for (int t : TAILLES) if (t >= p) { q = t; break; }
        for (int t : TAILLES) { if (t == q) yq = acc; acc += t + 2; }
        DaKit.debutPx(ctx);
        LotBlit b = LotBlit.ouvrir(ctx, DaKit.tex("faction", "insignes"), W, H);
        if (q == p) b.blit(Math.round(x * k), Math.round(y * k), i * (q + 2), yq, p, p, col);
        else b.etire(Math.round(x * k), Math.round(y * k), p, p, i * (q + 2), yq, q, q, col);
        b.fermer();
        DaKit.finPx(ctx);
    }
}
