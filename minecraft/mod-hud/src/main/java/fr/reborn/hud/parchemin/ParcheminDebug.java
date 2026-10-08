package fr.reborn.hud.parchemin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.List;
import java.util.Random;

/**
 * Banc d'essai des parchemins, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_PARCHEMIN_DEBUG=1}
 * (client de dev). Ouvre la bibliothèque, la lecture d'un rouleau (rang C en cours, rang S en fin de parcours) et le
 * réglage staff, capture chaque écran dans run/screenshots/, journalise la simulation des tirages, puis ferme le jeu.
 */
public final class ParcheminDebug {

    private static int ticks = -1;

    private ParcheminDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_PARCHEMIN_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ParcheminDebug::tick);
    }

    private static Technique find(String id) {
        for (Technique t : Technique.DEMO) if (t.id().equals(id)) return t;
        return Technique.DEMO.get(0);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        switch (ticks) {
            case 30 -> {
                mc.player.connection.sendCommand("time set 6000");
                for (int i = 0; i < 3; i++) {
                    double[] r = i == 0 ? Tirage.PUBLIQUE : i == 1 ? Tirage.RESERVEE : Tirage.PRIVEE;
                    Tirage.Stats s = Tirage.simulate(Technique.DEMO, r, null, 6, 10_000, 42);
                    System.out.printf("[parchemins] modèle %d : D %.2f C %.2f B %.3f A %.4f S %.5f par tirage, A>=1 %.2f%%, S>=1 %.3f%%%n",
                            i, s.perDraw()[0], s.perDraw()[1], s.perDraw()[2], s.perDraw()[3], s.perDraw()[4], s.atLeastA(), s.atLeastS());
                }
            }
            case 40 -> {
                List<Technique> drawn = List.of(find("kawarimi"), find("konoha_senpu"), find("katon_hosenka"),
                        find("tsubame_gaeshi"), find("katon_gokakyu"), find("raiton_chidori"));
                BibliothequeScreen s = new BibliothequeScreen("Bibliothèque de l'Académie", drawn, 6, "2 h 41");
                mc.setScreenAndShow(s);
            }
            case 50 -> with(mc, BibliothequeScreen.class, s -> s.debugHover = firstFilled(s));
            case 70 -> shot(mc);                                                          // bibliothèque + fiche
            case 75 -> with(mc, BibliothequeScreen.class, s -> { s.take(firstFilled(s)); s.debugHover = -1; });
            case 85 -> shot(mc);                                                          // un rouleau pris
            case 90 -> mc.setScreenAndShow(new LectureScreen(find("katon_gokakyu"), 1, "dans 14 h", false));
            case 115 -> shot(mc);                                                         // lecture rang C
            case 120 -> mc.setScreenAndShow(new LectureScreen(find("rasengan"), 20, "maintenant", true));
            case 145 -> shot(mc);                                                         // lecture rang S terminée
            case 150 -> mc.setScreenAndShow(new LectureScreen(find("raiton_chidori"), 5, "maintenant", true));
            case 175 -> shot(mc);                                                         // lecture rang A, prête
            case 180 -> {
                BibliothequeStaffScreen s = new BibliothequeStaffScreen("Bibliothèque de l'Académie");
                mc.setScreenAndShow(s);
                s.debugCustom("raiton_chidori", 1.0);
            }
            case 195 -> shot(mc);                                                         // réglages staff
            case 200 -> with(mc, BibliothequeStaffScreen.class, s -> { s.setPage(1); s.setFilter(3); });
            case 215 -> shot(mc);                                                         // techniques, filtre Ninjutsu
            case 220 -> with(mc, BibliothequeStaffScreen.class, s -> s.setPage(2));
            case 235 -> shot(mc);                                                         // simulation
            case 245 -> mc.stop();
            default -> { }
        }
    }

    private static int firstFilled(BibliothequeScreen s) {
        return s.firstFilled();
    }

    private interface Act<T> { void run(T s); }

    private static <T> void with(Minecraft mc, Class<T> c, Act<T> a) {
        if (c.isInstance(mc.gui.screen())) a.run(c.cast(mc.gui.screen()));
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
