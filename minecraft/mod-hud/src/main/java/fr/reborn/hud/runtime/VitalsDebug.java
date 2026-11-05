package fr.reborn.hud.runtime;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.HudEditScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banc des vitals compacts (Game Plan n° 18), <b>inactif en production</b> ({@code REBORN_VITALS_DEBUG=1}, monde solo de
 * dev, jamais de serveur). Horloge des vitals figée ({@link VitalsMode#debugMaintenant}) : chaque image du morph est
 * prise à son temps exact. Jour puis nuit : compact plein, valeurs partielles, vie ≤ 25 % (creux, crête), faim ≤ 25 %,
 * ouverture (60, 120, 160 ms), bloc en combat, repli (90, 210 ms), retour au compact ; éditeur de HUD (Vitals RP
 * choisi), Paramètres › Interface › HUD, « Vitals toujours détaillés » activé. Puis un essai des vrais signaux (coup
 * porté, soin, chute de chakra) avec l'horloge réelle, consigné en {@code [vitals-banc]}. Captures
 * {@code run/screenshots/vitals_*.png}, puis le jeu se ferme.
 *
 * <p>Le monde est remis en état : heure, mode de jeu, vol ; le réglage n'est jamais enregistré.
 */
public final class VitalsDebug {

    private record Pas(int attente, Consumer<Minecraft> action) {}

    private static final List<Pas> PAS = new ArrayList<>();
    private static int t = -1, i, prochain;
    /** Images rendues (HUD) : un pas attend au moins 3 images depuis le précédent (au chargement, plusieurs ticks
     *  passent par image et la capture montrerait l'état d'avant). */
    private static int images, imagesPas;
    private static long horloge;
    private static String mode = "survival";
    private static long heure = -1;

    private VitalsDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_VITALS_DEBUG"))) return;
        construire();
        ClientTickEvents.END_CLIENT_TICK.register(VitalsDebug::tick);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "vitals-banc"), (ctx, tc) -> images++);
    }

    private static void pas(int attente, Consumer<Minecraft> action) { PAS.add(new Pas(attente, action)); }

    private static void shot(String nom) {
        pas(4, mc -> Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { }));
    }

    private static void valeurs(int hp, int ck, float st) {
        VitalsFeed.update(hp, 100, ck, 100);
        VitalsFeed.debugSatiete(st, 0f, 0, 0);
    }

    /** Repos : compact, minuteurs vides, horloge figée. */
    private static void repos() {
        VitalsMode.remettre();
        VitalsMode.debugPouls = -1f;
        VitalsMode.debugFiger(VitalsMode.Etat.COMPACT, 0);
    }

    private static void etats(String suffixe) {
        pas(2, mc -> { valeurs(100, 100, 0.92f); repos(); });
        shot("vitals_01_compact_plein" + suffixe);
        pas(2, mc -> { valeurs(62, 35, 0.70f); repos(); });
        shot("vitals_02_compact_partiel" + suffixe);
        pas(2, mc -> { valeurs(18, 40, 0.70f); repos(); VitalsMode.debugPouls = 0f; });
        shot("vitals_03_vie_basse_creux" + suffixe);
        pas(2, mc -> VitalsMode.debugPouls = 1f);
        shot("vitals_04_vie_basse_crete" + suffixe);
        pas(2, mc -> { valeurs(88, 70, 0.18f); repos(); });
        shot("vitals_05_faim" + suffixe);
        // Ouverture (combat) : 60, 120, 160 ms, puis le bloc.
        pas(2, mc -> { valeurs(74, 64, 0.70f); repos(); VitalsMode.combat(horloge); });
        pas(2, mc -> VitalsMode.debugFiger(VitalsMode.Etat.OUVERTURE, 60));
        shot("vitals_06_ouverture_060" + suffixe);
        pas(1, mc -> VitalsMode.debugFiger(VitalsMode.Etat.OUVERTURE, 120));
        shot("vitals_07_ouverture_120" + suffixe);
        pas(1, mc -> VitalsMode.debugFiger(VitalsMode.Etat.OUVERTURE, 160));
        shot("vitals_08_ouverture_160" + suffixe);
        pas(1, mc -> VitalsMode.debugFiger(VitalsMode.Etat.COMPLET, 0));
        shot("vitals_09_combat_bloc" + suffixe);
        // Repli après 8 s de calme : 90, 210 ms, puis le compact (valeurs d'après le combat).
        pas(2, mc -> { valeurs(41, 22, 0.66f); repos(); VitalsMode.debugFiger(VitalsMode.Etat.REPLI, 90); });
        shot("vitals_10_repli_090" + suffixe);
        pas(1, mc -> VitalsMode.debugFiger(VitalsMode.Etat.REPLI, 210));
        shot("vitals_11_repli_210" + suffixe);
        pas(1, mc -> repos());
        shot("vitals_12_compact_retour" + suffixe);
    }

    private static void construire() {
        pas(20, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) heure = srv.overworld().getOverworldClockTime();
            RebornSession.debugForceRp = true;
            VitalsMode.debugSansSignaux = true;
            horloge = System.currentTimeMillis();
            VitalsMode.debugMaintenant = horloge;
            c.sendCommand("gamemode creative");
            vol(mc, true);
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            String pos = System.getenv("REBORN_VITALS_POS");   // « x y z lacet tangage » (jamais dans le dépôt)
            c.sendCommand(pos != null && !pos.isBlank() ? "tp @s " + pos.trim() : "tp @s ~ ~ ~ ~ -8");
        });
        pas(80, mc -> mc.gui.hud.getChat().clearMessages(false));
        etats("");
        // Nuit.
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"));
        pas(20, mc -> mc.gui.hud.getChat().clearMessages(false));
        etats("_nuit");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"));
        pas(10, mc -> mc.gui.hud.getChat().clearMessages(false));

        // Éditeur de HUD, Vitals RP choisi : toujours le bloc complet (sa boîte).
        pas(2, mc -> { valeurs(82, 64, 0.70f); repos(); mc.setScreenAndShow(new HudEditScreen(null, HudElement.VITALS)); });
        pas(10, mc -> { });
        shot("vitals_13_editeur");
        pas(2, mc -> mc.setScreenAndShow(null));
        pas(4, mc -> System.out.println("[vitals-banc] après l'éditeur : " + VitalsMode.etat()));
        pas(4, mc -> { repos(); System.out.println("[vitals-banc] repos : " + VitalsMode.etat()); });

        // Paramètres › Interface › HUD : la ligne, désactivée puis activée.
        pas(2, mc -> mc.setScreenAndShow(new ConfigShellScreen(null, ConfigShellScreen.Onglet.INTERFACE, 0)));
        pas(10, mc -> { });
        shot("vitals_14_parametres_hud");
        pas(2, mc -> {
            RebornPrefs.INSTANCE.vitalsToujoursDetailles = true;   // jamais enregistré
            mc.setScreenAndShow(new ConfigShellScreen(null, ConfigShellScreen.Onglet.INTERFACE, 0));
        });
        pas(10, mc -> { });
        shot("vitals_15_parametres_hud_active");
        pas(2, mc -> mc.setScreenAndShow(null));
        pas(4, mc -> { });
        shot("vitals_16_toujours_detailles");
        pas(2, mc -> {
            RebornPrefs.INSTANCE.vitalsToujoursDetailles = false;
            System.out.println("[vitals-banc] réglage coupé : " + VitalsMode.etat() + " (attendu REPLI ou COMPACT après le pas)");
        });

        // Vrais signaux, horloge réelle.
        pas(10, mc -> {
            VitalsMode.debugMaintenant = 0L;
            VitalsMode.debugSansSignaux = false;
            valeurs(82, 64, 0.70f);
            VitalsMode.remettre();
        });
        pas(10, mc -> System.out.println("[vitals-banc] repos réel : " + VitalsMode.etat()));
        pas(1, mc -> fr.reborn.hud.combat.CombatState.INSTANCE.onHit(-1, 4f, System.currentTimeMillis()));
        pas(2, mc -> System.out.println("[vitals-banc] coup porté → " + VitalsMode.etat()));
        pas(6, mc -> System.out.println("[vitals-banc] coup porté +300 ms → " + VitalsMode.etat()));
        shot("vitals_17_reel_coup_porte");
        pas(2, mc -> { VitalsMode.remettre(); System.out.println("[vitals-banc] remis : " + VitalsMode.etat()); });
        pas(4, mc -> VitalsFeed.update(90, 100, 64, 100));   // soin de 8 %
        pas(6, mc -> System.out.println("[vitals-banc] soin +8 % → " + VitalsMode.etat()));
        pas(60, mc -> System.out.println("[vitals-banc] soin +3 s → " + VitalsMode.etat() + " (attendu COMPACT)"));
        pas(2, mc -> VitalsFeed.update(90, 100, 50, 100));   // chakra −14 % sans déplacement : technique
        pas(6, mc -> System.out.println("[vitals-banc] chakra −14 % → " + VitalsMode.etat()));
        pas(60, mc -> System.out.println("[vitals-banc] chakra +3 s → " + VitalsMode.etat() + " (attendu COMPLET : combat 8 s)"));
        // Correctifs de la revue : maximum qui change, seuil absolu de chakra, endurance, soin pendant la régénération,
        // repli interrompu.
        pas(2, mc -> { VitalsMode.remettre(); VitalsFeed.update(90, 100, 64, 100); });
        pas(4, mc -> VitalsFeed.update(90, 100, 64, 200));   // point en Chakra : le ratio passe de 64 à 32 %
        pas(6, mc -> System.out.println("[vitals-banc] max chakra ×2 → " + VitalsMode.etat() + " (attendu COMPACT)"));
        pas(2, mc -> { VitalsMode.remettre(); VitalsFeed.update(90, 100, 16000, 16000); });
        pas(4, mc -> VitalsFeed.update(90, 100, 15600, 16000));   // technique E (400 sur 16000 = 2,5 %)
        pas(6, mc -> System.out.println("[vitals-banc] chakra −400/16000 → " + VitalsMode.etat() + " (attendu OUVERTURE ou COMPLET)"));
        pas(2, mc -> { VitalsMode.remettre(); VitalsFeed.update(90, 100, 64, 100); });
        pas(4, mc -> fr.reborn.hud.combat.CombatState.INSTANCE.onStamina(82f, 100f, System.currentTimeMillis()));
        pas(6, mc -> System.out.println("[vitals-banc] endurance −18 (taïjutsu) → " + VitalsMode.etat() + " (attendu COMPLET)"));
        pas(2, mc -> {
            fr.reborn.hud.combat.CombatState.INSTANCE.onStamina(100f, 100f, System.currentTimeMillis());
            VitalsMode.remettre();
            VitalsFeed.update(60, 100, 64, 100);
            VitalsFeed.debugSatiete(0.95f, 0f, 0, VitalsFeed.F_REGEN);
        });
        pas(4, mc -> VitalsFeed.update(65, 100, 64, 100));   // soin de 5 % pendant la régénération
        pas(6, mc -> System.out.println("[vitals-banc] soin +5 % en régénération → " + VitalsMode.etat() + " (attendu COMPLET)"));
        pas(2, mc -> {
            VitalsFeed.debugSatiete(0.70f, 0f, 0, 0);
            VitalsMode.remettre();
            long n = VitalsMode.maintenant();
            VitalsMode.debugFiger(VitalsMode.Etat.REPLI, 30);
            VitalsMode.phases(n);
            float b0 = VitalsMode.barres(), h0 = VitalsMode.tete(), t0 = VitalsMode.textes();
            VitalsMode.combat(n);
            VitalsMode.avancer(mc, n);
            VitalsMode.phases(n);
            System.out.println("[vitals-banc] repli interrompu à 30 ms : barres " + b0 + "→" + VitalsMode.barres()
                + ", tête " + h0 + "→" + VitalsMode.tete() + ", textes " + t0 + "→" + VitalsMode.textes() + " ("
                + VitalsMode.etat() + ", attendu sans saut)");
        });
        pas(2, mc -> { VitalsMode.remettre(); VitalsMode.debugArme = true; });
        pas(6, mc -> System.out.println("[vitals-banc] dégainé → " + VitalsMode.etat()));
        pas(200, mc -> System.out.println("[vitals-banc] dégainé +10 s → " + VitalsMode.etat() + " (attendu COMPLET)"));
        pas(2, mc -> {
            VitalsMode.debugArme = false;
            VitalsMode.remettre();
            VitalsFeed.update(20, 100, 64, 100);   // vie basse, signaux réels : pulsation libre
        });
        pas(10, mc -> { VitalsMode.remettre(); });
        pas(10, mc -> { });
        shot("vitals_18_reel_vie_basse");

        // Remise en état.
        pas(4, mc -> {
            var c = mc.player.connection;
            vol(mc, false);
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand("gamemode " + mode);
            VitalsFeed.clear();
            VitalsMode.remettre();
            RebornSession.debugForceRp = false;
            System.out.println("[vitals-banc] fin");
        });
        pas(10, Minecraft::stop);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 0) { i = 0; prochain = PAS.isEmpty() ? Integer.MAX_VALUE : PAS.get(0).attente(); }
        // Le vol créatif retombe parfois au chargement : on le remet tant que le banc tourne.
        if (t % 20 == 0 && VitalsMode.debugSansSignaux) vol(mc, true);
        if (t < prochain || i >= PAS.size()) return;
        if (images - imagesPas < 3 && t < prochain + 40) return;
        imagesPas = images;
        try {
            PAS.get(i).action().accept(mc);
        } catch (RuntimeException e) {
            System.out.println("[vitals-banc] ECHEC au pas " + i + " : " + e);
        }
        i++;
        if (i < PAS.size()) prochain = t + PAS.get(i).attente();
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }
}
