package fr.reborn.hud.menu.esc;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banc d'essai du menu Échap, <b>inactif en production</b> : {@code REBORN_ESC_DEBUG=reprendre|echap|boutons|tout}
 * (monde solo de dev, jamais de serveur). Ouvre l'écran pause, simule le clic sur REPRENDRE (ou la touche Échap,
 * ou chaque bouton puis le retour du sous-écran), attend la fin de l'animation et écrit « [EscDebug] OK … » dans
 * le journal ; « [EscDebug] ECHEC … » sinon. Ferme le jeu à la fin (« [EscDebug] FIN »).
 */
public final class EscDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine;

    private EscDebug() {}

    public static void init() {
        String m = System.getenv("REBORN_ESC_DEBUG");
        if (m == null || m.isBlank()) return;
        boolean tout = "tout".equals(m);
        for (int n = 0; n < 3; n++) {
            if (tout || "reprendre".equals(m)) reprendre(n);
            if (tout || "echap".equals(m)) echap(n);
        }
        if (tout || "reprendre".equals(m)) {
            // clics répétés pendant la fermeture, Échap pendant la fermeture, réouverture pendant la fermeture
            ouvrir();
            pas(25, mc -> { clic(mc, 0); clic(mc, 0); }, "double clic REPRENDRE");
            pas(3, mc -> clic(mc, 0), "3e clic pendant la fermeture");
            attendreFerme(20, "clics répétés");
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic REPRENDRE");
            pas(3, EscDebug::echapTouche, "Échap pendant la fermeture");
            attendreFerme(20, "Échap pendant la fermeture");
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic REPRENDRE");
            // Échap déjà à l'écran : pauseGame ne rouvre rien, la fermeture doit aller au bout
            pas(3, mc -> mc.pauseGame(false), "pauseGame pendant la fermeture");
            attendreFerme(20, "pauseGame pendant la fermeture");
            // réouverture dès la fin de la fermeture (≈ 300 ms), puis nouvelle fermeture
            ouvrir();
            pas(25, mc -> clic(mc, 0), "clic REPRENDRE");
            pas(7, mc -> mc.pauseGame(false), "réouverture juste après la fermeture");
            pas(2, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "réouverture juste après la fermeture"), "vérif");
            pas(25, mc -> clic(mc, 0), "clic REPRENDRE");
            attendreFerme(20, "après réouverture");
        }
        if (tout || "boutons".equals(m)) {
            boutons();
        }
        pas(20, mc -> {
            System.out.println("[EscDebug] FIN");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(EscDebug::tick);
    }

    private static void reprendre(int n) {
        ouvrir();
        pas(25, mc -> clic(mc, 0), "clic REPRENDRE #" + n);
        attendreFerme(20, "REPRENDRE #" + n);
    }

    private static void echap(int n) {
        ouvrir();
        pas(25, EscDebug::echapTouche, "touche Échap #" + n);
        attendreFerme(10, "Échap #" + n);
    }

    private static void boutons() {
        String[] noms = {"INTERFACE", "PARAMETRES", "SIGNALER", "DECONNEXION"};
        for (int i = 1; i <= noms.length; i++) {
            int b = i;
            ouvrir();
            pas(25, mc -> clic(mc, b), "clic " + noms[b - 1]);
            pas(15, mc -> verifie(!(mc.gui.screen() instanceof PauseScreen) && mc.gui.screen() != null,
                noms[b - 1] + " ouvre " + nom(mc.gui.screen())), "vérif");
            pas(5, EscDebug::echapTouche, "retour (Échap)");
            pas(15, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "retour de " + noms[b - 1] + " → " + nom(mc.gui.screen())),
                "vérif retour");
            pas(5, mc -> clic(mc, 0), "clic REPRENDRE");
            attendreFerme(20, "REPRENDRE après " + noms[b - 1]);
        }
        // Boutique (devanture ; écran vide depuis le 15/10)
        ouvrir();
        pas(25, mc -> clicUnites(mc, 236 + 88, 60 + 60), "clic BOUTIQUE");
        pas(15, mc -> verifie(!(mc.gui.screen() instanceof PauseScreen) && mc.gui.screen() != null,
            "BOUTIQUE ouvre " + nom(mc.gui.screen())), "vérif");
        pas(5, EscDebug::echapTouche, "retour (Échap)");
        pas(15, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "retour de BOUTIQUE → " + nom(mc.gui.screen())), "vérif retour");
        pas(5, EscDebug::echapTouche, "Échap");
        attendreFerme(10, "Échap après BOUTIQUE");
    }

    // ── construction ──

    private static void ouvrir() {
        pas(20, mc -> mc.pauseGame(false), "ouvre Échap");
        pas(2, mc -> verifie(mc.gui.screen() instanceof PauseScreen, "écran pause ouvert"), "vérif");
    }

    private static void attendreFerme(int ticks, String quoi) {
        pas(ticks, mc -> verifie(mc.gui.screen() == null, quoi + " → écran " + nom(mc.gui.screen())), "vérif fermeture");
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[EscDebug] " + nom);
            action.accept(mc);
        });
    }

    // ── actions ──

    private static String nom(Screen s) { return s == null ? "aucun" : s.getClass().getSimpleName(); }

    private static void verifie(boolean ok, String quoi) {
        System.out.println("[EscDebug] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void echapTouche(Minecraft mc) {
        Screen s = mc.gui.screen();
        if (s != null) s.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
    }

    /** Clic gauche sur la plaque n° {@code i} du menu (centre, en unités de scène). */
    public static void clic(Minecraft mc, int i) { clicUnites(mc, 41 + 66, 230 + i * 20 + 7); }

    /** Clic gauche en unités de scène (aussi utilisé par le banc des touches, {@code keybind/TouchesDebug}). */
    public static void clicUnites(Minecraft mc, int ux, int uy) {
        Screen s = mc.gui.screen();
        if (s == null) { System.out.println("[EscDebug] (pas d'écran pour cliquer)"); return; }
        var w = mc.getWindow();
        int pw = w.getWidth(), ph = w.getHeight();
        int k = Math.max(1, Math.min(pw / EchapArt.SAFE_W, ph / EchapArt.SAFE_H));
        int lw = -Math.floorDiv(-pw, k), lh = -Math.floorDiv(-ph, k);
        int ox = Math.floorDiv(lw - EchapArt.VW, 2), oy = Math.floorDiv(lh - EchapArt.VH, 2);
        double gs = Math.max(1, w.getGuiScale());
        double gx = (ux + ox) * (double) k / gs, gy = (uy + oy) * (double) k / gs;
        s.mouseClicked(new MouseButtonEvent(gx, gy, new MouseButtonInfo(0, 0)), false);
        s.mouseReleased(new MouseButtonEvent(gx, gy, new MouseButtonInfo(0, 0)));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
