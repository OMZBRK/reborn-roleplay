package fr.reborn.hud.allure;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Barre d'endurance du <b>sprint</b> (RedLine : « sous le personnage, au-dessus de la barre d'action, n'apparaît qu'en
 * sprint ») dans la règle 9 de la DA (HUD en jeu subtil) : un filet de 2 pixels écran sur un voile nuit, rien d'autre
 * (plus de kanji depuis le 18/10, DA règle 10). Visible pendant le sprint puis pendant la récupération, fondu d'entrée et de sortie ; vermillon
 * quand le personnage est essoufflé (plus de sprint avant 30 %). Le trot et la marche n'affichent rien.
 */
public final class AllureHud {

    /** Largeur de la barre (unités GUI) : ~1/9 de l'écran en 1080p. */
    private static final int LARGEUR = 72;
    /** Hauteur au-dessus du bas de l'écran (la barre d'action fait 22). */
    private static final int AU_DESSUS = 30;
    private static final int VERM = 0xFFE0563C;

    private static float alpha;
    private static float affiche = 1f;
    private static long dernierRendu;

    private AllureHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        // Préparer / manger occupe la place du filet de sprint (on ne sprinte pas en mangeant).
        if (fr.reborn.hud.nourriture.NourritureClient.filetActif()) return;
        Allure a = Allure.INSTANCE;
        long now = System.currentTimeMillis();
        float dt = dernierRendu == 0 ? 0f : Math.min(0.1f, (now - dernierRendu) / 1000f);
        dernierRendu = now;

        boolean sprint = a.sprinte();
        boolean recup = a.endurance() < 0.995f && now - a.dernierSprintMs() < 20_000L;
        boolean voulu = sprint || a.essouffle() || recup;
        alpha = voulu ? Math.min(1f, alpha + dt * 6f) : Math.max(0f, alpha - dt * 2.5f);
        affiche += (a.endurance() - affiche) * Math.min(1f, dt * 10f);
        if (alpha <= 0.01f) return;

        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        float x0 = (gw - LARGEUR) / 2f;
        float y = gh - AU_DESSUS;

        boolean souffle = a.essouffle();
        int col = souffle || affiche < 0.2f ? VERM : DaKit.OR;
        if (souffle) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(now / 160.0);
            col = DaKit.alpha(col, pulse);
        }

        // filet de 2 px écran : voile nuit + remplissage
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X0 = Math.round(x0 * k), X1 = Math.round((x0 + LARGEUR) * k), Y = Math.round(y * k);
        g.fill(X0 - 1, Y - 1, X1 + 1, Y + 3, DaKit.argb(0x0A0608, 0.6f * alpha));
        int fx = X0 + Math.round((X1 - X0) * Math.max(0f, Math.min(1f, affiche)));
        if (fx > X0) {
            g.fill(X0, Y, fx, Y + 2, DaKit.alpha(col, alpha));
            g.fill(fx - 1, Y - 1, fx, Y + 3, DaKit.alpha(DaKit.CREME, 0.8f * alpha));   // tête de la jauge
        }
        // repère de relance (30 %) pendant l'essoufflement
        if (souffle) {
            int rx = X0 + Math.round((X1 - X0) * 0.3f);
            g.fill(rx, Y - 1, rx + 1, Y + 3, DaKit.alpha(DaKit.CREME, 0.7f * alpha));
        }
        DaKit.finPx(g);
    }
}
