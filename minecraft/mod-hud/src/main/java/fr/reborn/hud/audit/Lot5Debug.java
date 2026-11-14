package fr.reborn.hud.audit;

import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.combat.ChakraJump;
import fr.reborn.hud.combat.CombatInputPayload;
import fr.reborn.hud.combat.CombatPayload;
import fr.reborn.hud.combat.CooldownState;
import fr.reborn.hud.combat.SautApercu;
import fr.reborn.hud.combat.SautArc;
import fr.reborn.hud.combat.SautChargeLoi;
import fr.reborn.hud.combat.SautChargeLoi.Regles;
import fr.reborn.hud.combat.SautHud;
import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Banc du <b>saut chargé</b> (lot 5, étape A), <b>inactif en production</b> : {@code REBORN_LOT5_DEBUG=saut} (monde solo de
 * dev, jamais de serveur). Serveur simulé côté client avec la même loi que le plugin ({@link SautChargeLoi}) et les mêmes
 * octets que {@code CombatChannel} (GO, NON, réglages) ; horloge de la charge figée, touches simulées ; le vol, lui, est
 * <b>réel</b> : le joueur part, vole et se pose dans le monde, tick par tick, par le vrai mixin.
 *
 * <p>Couloir de 11 × 118 blocs bâti sur un sol d'herbe à nous (dégagé sur 46 de haut), regard au sud. Séries :
 * <ul>
 *   <li><b>charge</b> : filet vide, 25 %, 58 %, plein, aperçu à sa vraie distance, puis le départ et la réception ;</li>
 *   <li><b>justesse</b> : dix sauts (charges et visées différentes, un mur, un toit) : l'arrivée réelle doit tomber à moins
 *       d'un bloc de l'arrivée prédite ; à plat, la portée prédite égale celle de la loi pure ;</li>
 *   <li><b>GO tardif</b> : non joué, aucune recharge, le serveur est prévenu (kind 11) ;</li>
 *   <li><b>trajectoire et barre</b> (10/11, 6e tour, {@link SautApercu}, {@link SautHud}) : 1re personne à mi-charge
 *       et pleine charge, 3e personne (épaule) à mi-charge et pleine charge, au pied d'un mur à 10 blocs, sous le Byakugan, de nuit ; vue imposée à chaque capture, grains comptés
 *       → {@code saut_v6_<scène>.png} ;</li>
 *   <li><b>refus</b> : pas assez de chakra (réponse du serveur), étourdi (état du serveur), en 1920 × 1080 puis 1366 × 768.</li>
 * </ul>
 * {@code REBORN_LOT5_DEBUG=saut_styles} : barre et ligne seulement. Captures {@code run/screenshots/saut_*.png}, bilans
 * « [lot5] OK / ECHEC », puis le jeu se ferme.
 */
public final class Lot5Debug {

    private record Etape(int delai, Predicate<Minecraft> action, String nom) { }

    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs, attente;
    private static long h;
    private static boolean touches;
    private static long debutSrv = -1;
    private static String mode = "creative", difficulte = "normal";
    private static long heure = -1;
    private static boolean pluie;
    private static int ox, oy, oz;
    private static RebornCamera.Mode cameraAvant;
    /** Caméra voulue par la série en cours, reposée à chaque départ ({@link #placer}). */
    private static RebornCamera.Mode cameraVoulue = RebornCamera.Mode.SHOULDER;
    /** Portée de visée des blocs avant le banc (remise à la fin ; -1 = inconnue). */
    private static double porteeAvant = -1;
    private static final ArrayDeque<byte[]> FILE = new ArrayDeque<>();
    private static String refusServeur;   // null = le serveur accepte ; "chakra" = NON chakra
    /* GO tardif : le serveur simulé garde sa réponse, le banc la livre après la fenêtre du mod */
    private static boolean retarderGo, goIgnoreRecu;
    private static byte[] goRetarde;

    /* justesse : prédiction posée au GO, comparée à la réception */
    private static double predX, predY, predZ, predDist, purDist;
    private static boolean predMur;
    private static final List<String> BILAN = new ArrayList<>();

    private Lot5Debug() {}

    public static void init() {
        String v = String.valueOf(System.getenv("REBORN_LOT5_DEBUG"));
        // « saut » : tout le banc ; « saut_styles » : les captures de la barre et de la ligne seulement
        boolean complet = "saut".equalsIgnoreCase(v);
        if (!complet && !"saut_styles".equalsIgnoreCase(v)) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            // horloge du monde arrêtée (26.2 : la règle s'appelle advance_time, doDaylightCycle est refusé)
            c.sendCommand("gamerule advance_time false");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            var ab = mc.player.getAbilities();
            ab.flying = false;
            mc.player.onUpdateAbilities();
            cameraAvant = RebornCamera.INSTANCE.mode();
            camera(mc, RebornCamera.Mode.SHOULDER);
            System.out.println("[lot5] origine " + ox + " " + oy + " " + oz + ", distance de rendu "
                + mc.options.getEffectiveRenderDistance());
        }, "préparation");
        pas(4, Lot5Debug::batir, "couloir");
        // le plateau du banc Byakugan (y 164 à 189, autour de la même origine) ombrait tout le couloir : sol noir sous un
        // ciel de jour. On le retire (le banc Byakugan le rebâtit à chaque passage), puis on attend que la lumière du ciel
        // soit recalculée et arrivée au client
        pas(2, Lot5Debug::degagerCiel, "ciel dégagé");
        pas(40, mc -> { }, "moteur de lumière");
        attendre(Lot5Debug::lumiereCielPleine, 600, "lumière du ciel à 15 sur le couloir et ses abords");
        pas(1, mc -> {
            verifie(lumiereCielPleine(mc), "lumière du ciel à 15 sur le couloir et ses abords");
            System.out.println("[lot5] lumière du ciel : " + lumiereCiel(mc));
        }, "bilan lumière");
        pas(60, mc -> {
            figer(Util.getMillis());
            ChakraJump.debugTouches = () -> touches;
            ChakraJump.debugServeur = Lot5Debug::serveur;
            SautHud.banc = true;
            // le contour vanilla du bloc visé (fil de fer noir) n'a rien à faire sur les captures : portée de visée nulle
            var portee = mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
            if (portee != null) { porteeAvant = portee.getBaseValue(); portee.setBaseValue(0.0); }
            // les réglages arrivent par les octets du plugin : on vérifie le décodage
            ChakraJump.INSTANCE.recevoir(CombatPayload.TYPE_SAUT_REGLES, regles(Regles.DEFAUT));
            verifie(ChakraJump.INSTANCE.serveurConnu(), "réglages reçus du serveur");
            Regles r = ChakraJump.INSTANCE.regles();
            verifie(r.chargeMs() == 1200 && Math.abs(r.seuil() - 0.1125) < 1e-6 && Math.abs(r.vyMax() - 3.0) < 1e-6
                && Math.abs(r.porteeMax() - 96) < 1e-6 && r.rechargeMaxMs() == 30000, "réglages décodés : " + r);
            double plat = SautChargeLoi.portee(SautChargeLoi.vitesseDepart(1, 0, 0, r), r);
            verifie(plat >= 58, String.format(Locale.ROOT, "pleine charge à plat : %.1f blocs", plat));
        }, "serveur simulé, horloge figée");

        if (complet) series();
        // ── 3. trajectoire en poussière et barre de charge (10/11, 6e tour), vue imposée à chaque capture :
        //    1re personne à mi-charge et pleine charge, 3e personne (épaule) à mi-charge et pleine charge, au pied d'un mur à 10 blocs (regard à 18° : à 6°,
        //    l'arrivée tombe derrière la tête), sous le Byakugan, de nuit → saut_v6_<scène>.png
        Regles d = Regles.DEFAUT;
        long ms50 = d.chargeMs() / 2;
        apercu(ms50, 6f, "1p_050", false, RebornCamera.Mode.FIRST);
        apercu(d.chargeMs(), 4f, "3p_100", false, RebornCamera.Mode.SHOULDER);
        apercu(d.chargeMs(), 6f, "1p_100", false, RebornCamera.Mode.FIRST);
        apercu(ms50, 6f, "3p_050", false, RebornCamera.Mode.SHOULDER);
        pas(1, mc -> mur(mc, true), "mur à 10 blocs");
        apercu(600, 18f, "mur_10", true, RebornCamera.Mode.SHOULDER);
        pas(1, mc -> mur(mc, false), "mur retiré");
        pas(1, mc -> ByakuganClient.update(true, 50f), "Byakugan ouvert");
        // (le premier passage compile le filtre : il peut se poser après la seconde de fondu)
        attendre(mc -> ByakuganClient.filtreAffiche() && ByakuganClient.niveau() > 0.99f, 100, "fondu du Byakugan");
        pas(1, mc -> verifie(ByakuganClient.filtreAffiche() && ByakuganClient.niveau() > 0.99f, "Byakugan : filtre posé"),
            "Byakugan posé");
        apercu(ms50, 6f, "byakugan_050", false, RebornCamera.Mode.SHOULDER);
        pas(1, mc -> ByakuganClient.update(false, 0f), "Byakugan fermé");
        pas(20, mc -> { }, "fondu de sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(30, mc -> { }, "nuit tombée");
        apercu(d.chargeMs(), 4f, "nuit_100", false, RebornCamera.Mode.SHOULDER);
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(30, mc -> { }, "jour revenu");

        if (complet) refusSeries();
        // ── remise en état ────────────────────────────────────────────────────────────────────────────────────────
        pas(5, mc -> {
            ChakraJump.debugTouches = null;
            ChakraJump.debugServeur = null;
            ChakraJump.horloge = Util::getMillis;
            ChakraJump.INSTANCE.oublierServeur();
            SautHud.banc = false;
            EtatClient.clear();
            CooldownState.INSTANCE.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            var portee = mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
            if (portee != null && porteeAvant >= 0) portee.setBaseValue(porteeAvant);
            var c = mc.player.connection;
            c.sendCommand("gamerule advance_time true");
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            c.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> {
            System.out.println("[lot5] ===== justesse (prédit / réel) =====");
            for (String l : BILAN) System.out.println("[lot5] " + l);
            System.out.println("[lot5] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot5Debug::tick);
        // images comptées par une couche HUD du banc, dessinée en dernier : la collecte du monde (COLLECT_SUBMITS) peut
        // tourner sans que l'image arrive dans la cible lue par la capture (au 09/11, saut_charge_100 était la copie
        // exacte de saut_charge_058, filet à 58 % compris)
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "lot5-banc"), (g, dt) -> images++);
    }

    /** Images rendues (couche HUD du banc), et leur nombre à la fin de l'étape d'avant. */
    private static int images, imagesEtape;
    /** Capture demandée par une étape : prise seulement après {@value #IMAGES_AVANT_CAPTURE} images de plus. */
    private static String capture;
    private static int imagesCapture;
    private static final int IMAGES_AVANT_CAPTURE = 3;

    /* ================================================================ séries */

    /** 1. La charge, filet vide → plein, puis le départ et la réception ; 2. justesse ; GO tardif. */
    private static void series() {
        // ── 1. la charge ─────────────────────────────────────────────────────────────────────────────────────────
        placer(0, 6f);
        pas(2, mc -> { touches = true; cumul = 0; }, "Maj + Espace");
        pas(8, mc -> {
            verifie(ChakraJump.INSTANCE.isCharging(), "la charge démarre au sol");
            verifie(debutSrv >= 0, "le serveur a reçu le début de charge (kind 8)");
            shot(mc, "saut_charge_000");
        }, "capture filet vide");
        charge(300, "saut_charge_025");
        charge(400, "saut_charge_058");
        charge(500, "saut_charge_100");
        pas(1, mc -> {
            SautArc a = ChakraJump.INSTANCE.arc();
            verifie(a.valide() && !a.mur(), String.format(Locale.ROOT, "aperçu plein : %.1f blocs", a.distance()));
            purDist = SautChargeLoi.portee(ChakraJump.INSTANCE.vitesseApercu(), ChakraJump.INSTANCE.regles());
            verifie(Math.abs(a.distance() - purDist) <= 1.0,
                String.format(Locale.ROOT, "à plat, aperçu %.2f contre loi pure %.2f", a.distance(), purDist));
        }, "aperçu plein");
        relacher("plein, à plat");
        // en vol, la caméra d'épaule, regard à 6°, met l'arrivée droit derrière le corps : le joueur baisse les yeux vers
        // elle (la caméra est libre en vol), l'arrivée passe au-dessus de sa tête à l'écran
        attendre(mc -> ChakraJump.INSTANCE.enVol() && ChakraJump.INSTANCE.volTicks() >= 8, 60, "en vol");
        pas(0, mc -> RebornCamera.INSTANCE.initOrientation(0f, 24f), "regard vers l'arrivée");
        attendre(mc -> ChakraJump.INSTANCE.enVol() && ChakraJump.INSTANCE.volTicks() >= 12, 60, "mi-vol");
        pas(0, mc -> shot(mc, "saut_vol_58"), "capture en vol");
        atterrir("plein, à plat", true);
        pas(4, mc -> shot(mc, "saut_reception_58"), "capture réception");

        // ── 2. justesse : arrivée prédite contre arrivée réelle ───────────────────────────────────────────────────
        int[][] series = {{135, 6}, {300, 6}, {600, 6}, {900, 6}, {600, -20}, {1200, -28}, {1200, -45}, {900, -60}, {400, -35}};
        for (int[] s : series) sautMesure(s[0], s[1], s[0] + " ms, visée " + (-s[1]) + "°");
        // un mur à 10 blocs : on tombe à son pied
        pas(1, mc -> mur(mc, true), "mur à 10 blocs");
        sautMesure(600, 6, "600 ms dans le mur");
        pas(1, mc -> mur(mc, false), "mur retiré");
        // un toit : on se pose dessus
        pas(1, mc -> toit(mc, true), "toit");
        sautMesure(600, -20, "600 ms sur le toit");
        pas(1, mc -> toit(mc, false), "toit retiré");
        // un GO arrivé après la fenêtre du mod (3 s) : non joué, aucune recharge, le serveur est prévenu (il rembourse)
        placer(0, 6f);
        pas(2, mc -> { touches = true; retarderGo = true; goIgnoreRecu = false; }, "Maj + Espace (GO retardé)");
        pas(2, mc -> { avance(600); ChakraJump.INSTANCE.debugApercu(); }, "charge 600 ms");
        pas(1, mc -> { touches = false; }, "relâche, GO retenu");
        pas(3, mc -> avance(3500), "3,5 s sans réponse");
        pas(3, mc -> {
            verifie(goRetarde != null && ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS, "attente close sans réponse");
            FILE.add(goRetarde);
            goRetarde = null;
            retarderGo = false;
        }, "GO livré en retard");
        pas(4, mc -> {
            verifie(goIgnoreRecu, "GO tardif : le serveur est prévenu (kind 11)");
            verifie(!ChakraJump.INSTANCE.enVol() && mc.player.onGround(), "GO tardif : pas de bond");
            verifie(CooldownState.INSTANCE.remainingMs(CooldownState.Ability.CHAKRA_JUMP, System.currentTimeMillis()) == 0,
                "GO tardif : aucune recharge");
        }, "bilan GO tardif");

    }

    /** 4. Refus, en 1920 × 1080 puis 1366 × 768. */
    private static void refusSeries() {
        refus("1920");
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(25, mc -> { }, "fenêtre posée");
        placer(0, 6f);
        pas(2, mc -> { CooldownState.INSTANCE.clear(); touches = true; cumul = 0; }, "Maj + Espace");
        charge(700, "saut_charge_058_1366");
        pas(1, mc -> { touches = false; }, "relâche");
        attendre(mc -> !ChakraJump.INSTANCE.enVol() && ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS
            && ChakraJump.INSTANCE.finVolMs() >= 0, 200, "réception");
        refus("1366");

    }

    /** Le joueur au départ du couloir, regard au sud, caméra d'épaule inclinée de {@code tangage}. */
    private static void placer(double mx, float tangage) {
        pas(1, mc -> {
            touches = false;
            CooldownState.INSTANCE.clear();
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %d %.2f 0 %.1f", ox + 0.5 - mx, oy, oz + 0.5, tangage));
            camera(mc, cameraVoulue);
            RebornCamera.INSTANCE.initOrientation(0f, tangage);
        }, "au départ");
        attendre(mc -> mc.player.onGround() && Math.abs(mc.player.getZ() - (oz + 0.5)) < 0.01, 40, "posé au départ");
        pas(6, mc -> RebornCamera.INSTANCE.initOrientation(0f, tangage), "caméra");
    }

    private static long cumul;

    private static void charge(long ms, String capture) {
        pas(1, mc -> { avance(ms); cumul += ms; ChakraJump.INSTANCE.debugApercu(); }, "charge +" + ms + " ms");
        pas(8, mc -> {
            double attendu = Math.min(1.0, cumul / 1200.0);
            verifie(ChakraJump.INSTANCE.isCharging() && Math.abs(ChakraJump.INSTANCE.charge() - attendu) < 0.01,
                String.format(Locale.ROOT, "%s : charge %.3f (attendu %.3f), phase %s", capture, ChakraJump.INSTANCE.charge(), attendu,
                    ChakraJump.INSTANCE.phase()));
            shot(mc, capture);
        }, "capture " + capture);
    }

    private static void relacher(String nom) {
        pas(1, mc -> { touches = false; }, "relâche " + nom);
        attendre(mc -> ChakraJump.INSTANCE.enVol(), 20, "GO reçu");
        pas(0, mc -> {
            SautArc a = ChakraJump.INSTANCE.arc();
            predX = a.x();
            predY = a.y();
            predZ = a.z();
            predDist = a.distance();
            predMur = a.mur();
            purDist = SautChargeLoi.portee(ChakraJump.INSTANCE.vitesseApercu(), ChakraJump.INSTANCE.regles());
        }, "prédiction au GO");
    }

    private static void atterrir(String nom, boolean aPlat) {
        attendre(mc -> ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS && ChakraJump.INSTANCE.finVolMs() >= 0, 260, "réception");
        pas(2, mc -> {
            var p = mc.player;
            double ecart = Math.hypot(p.getX() - predX, p.getZ() - predZ);
            double dy = Math.abs(p.getY() - predY);
            double reel = Math.hypot(p.getX() - (ox + 0.5), p.getZ() - (oz + 0.5));
            String l = String.format(Locale.ROOT, "%-24s prédit %6.2f blocs (y %+.2f%s), réel %6.2f (y %+.2f), écart %.2f, fin : %s%s",
                nom, predDist, predY - oy, predMur ? ", mur" : "", reel, p.getY() - oy, ecart, ChakraJump.INSTANCE.finRaison(),
                aPlat ? String.format(Locale.ROOT, ", loi pure %.2f", purDist) : "");
            BILAN.add(l);
            verifie(ecart <= 1.0 && dy <= 0.6 && "réception".equals(ChakraJump.INSTANCE.finRaison()), "arrivée sur la prédiction : " + l);
            if (aPlat) verifie(Math.abs(predDist - purDist) <= 1.0, String.format(Locale.ROOT, "à plat, prédit %.2f contre loi pure %.2f", predDist, purDist));
        }, "bilan " + nom);
    }

    private static void sautMesure(long ms, float tangage, String nom) {
        placer(0, tangage);
        pas(2, mc -> { touches = true; }, "Maj + Espace");
        pas(2, mc -> { avance(ms); ChakraJump.INSTANCE.debugApercu(); }, "charge " + ms + " ms");
        relacher(nom);
        atterrir(nom, tangage >= 0 && !nom.contains("mur") && !nom.contains("toit"));
    }

    /**
     * Capture de la trajectoire et de la barre de charge pendant une charge de {@code ms}, vue {@code vue} imposée
     * (rien ne part : la charge est annulée ensuite). L'arrivée doit égaler celle de la loi pure à plat, ou tomber au pied
     * du mur (8 à 11 blocs) ; les grains posés sont comptés au journal.
     */
    private static void apercu(long ms, float tangage, String scene, boolean auMur, RebornCamera.Mode vue) {
        String capture = "saut_v6_" + scene;
        pas(1, mc -> camera(mc, vue), "vue " + vue);
        placer(0, tangage);
        pas(2, mc -> { touches = true; }, "Maj + Espace");
        pas(4, mc -> { avance(ms); ChakraJump.INSTANCE.debugApercu(); }, "charge " + ms + " ms");
        pas(8, mc -> {
            SautArc a = ChakraJump.INSTANCE.arc();
            double pur = SautChargeLoi.portee(ChakraJump.INSTANCE.vitesseApercu(), ChakraJump.INSTANCE.regles());
            boolean juste = auMur ? a.distance() >= 8 && a.distance() <= 11 : Math.abs(a.distance() - pur) <= 1.0;
            verifie(a.valide() && juste && SautApercu.ligne() > 0.99f && SautApercu.grains() > 0,
                String.format(Locale.ROOT, "%s : arrivée à %.1f blocs (%s)%s, charge %.2f, ligne %.2f, grains %d + anneau %d",
                    capture, a.distance(), auMur ? "attendu 8 à 11" : String.format(Locale.ROOT, "loi pure %.1f", pur),
                    a.mur() ? ", au pied du mur" : "", ChakraJump.INSTANCE.charge(), SautApercu.ligne(),
                    SautApercu.grainsLigne(), SautApercu.grains() - SautApercu.grainsLigne()));
            verifie(mc.options.getCameraType().isFirstPerson() == (cameraVoulue == RebornCamera.Mode.FIRST),
                capture + " : vue " + mc.options.getCameraType() + " (voulue " + cameraVoulue + ")");
            shot(mc, capture);
        }, "capture " + capture);
        pas(2, mc -> { mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }, "écran ouvert : la charge tombe");
        pas(2, mc -> {
            verifie(ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS && debutSrv < 0, "écran ouvert : charge annulée (kind 10)");
            mc.setScreenAndShow(null);
            touches = false;
        }, "écran fermé");
    }

    private static void refus(String fenetre) {
        // pas assez de chakra : le serveur refuse dès le début de la charge
        placer(0, 6f);
        pas(1, mc -> { refusServeur = "chakra"; CooldownState.INSTANCE.clear(); touches = true; }, "serveur sans chakra");
        pas(3, mc -> {
            verifie(ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS && "Pas assez de chakra".equals(ChakraJump.INSTANCE.refusTexte()),
                "refus du serveur affiché : " + ChakraJump.INSTANCE.refusTexte());
            verifie(CooldownState.INSTANCE.remainingMs(CooldownState.Ability.CHAKRA_JUMP, System.currentTimeMillis()) == 0,
                "aucune recharge fantôme après un refus");
            shot(mc, "saut_refus_chakra_" + fenetre);
        }, "capture refus chakra");
        pas(2, mc -> { touches = false; refusServeur = null; avance(2000); }, "fin du refus");
        // étourdi : l'état du serveur refuse la mobilité, rien ne charge
        pas(1, mc -> EtatClient.recevoir("{\"seq\":9,\"p\":\"ETOURDI\",\"c\":{\"vital\":\"VALIDE\",\"ctrl\":\"ETOURDI\",\"cadre\":\"MONDE\"},"
            + "\"cap\":{\"mob\":0,\"sau\":0,\"bou\":0},\"raison\":{\"mob\":\"Étourdi\",\"sau\":\"Étourdi\"}}"), "état : étourdi");
        pas(2, mc -> { touches = true; }, "Maj + Espace");
        pas(3, mc -> {
            verifie(ChakraJump.INSTANCE.phase() == ChakraJump.Phase.REPOS && debutSrv < 0, "étourdi : rien ne charge, rien n'est envoyé");
            verifie("Étourdi".equals(ChakraJump.INSTANCE.refusTexte()), "raison de l'état : " + ChakraJump.INSTANCE.refusTexte());
            shot(mc, "saut_refus_etourdi_" + fenetre);
        }, "capture refus étourdi");
        pas(2, mc -> { touches = false; EtatClient.clear(); avance(2000); }, "fin de l'état");
    }

    /* ================================================================ décor */

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        for (int z = oz - 6; z <= oz + 112; z += 24) {
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 5, oy, z, ox + 5, oy + 46, Math.min(oz + 112, z + 23)));
        }
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 5, oy - 1, oz - 6, ox + 5, oy - 1, oz + 112));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 6, oy - 1, oz - 6, ox - 6, oy, oz + 112));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox + 6, oy - 1, oz - 6, ox + 6, oy, oz + 112));
        c.sendCommand("kill @e[type=minecraft:item,distance=..140]");
        c.sendCommand("kill @e[type=minecraft:bat,distance=..160]");
    }

    /**
     * Retire tout ce qui flotte dans la couche du plateau Byakugan au-dessus du couloir et de ses abords (129 × 241 blocs),
     * par carreaux de 16 × 16, seulement là où la carte des hauteurs du serveur y trouve un bloc.
     */
    private static void degagerCiel(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var lvl = srv.overworld();
        int bas = Lot5ByakDebug.P - 1, haut = Lot5ByakDebug.P + 24, n = 0;
        for (int x = ox - 64; x <= ox + 64; x += 16) {
            for (int z = oz - 64; z <= oz + 176; z += 16) {
                int x1 = Math.min(ox + 64, x + 15), z1 = Math.min(oz + 176, z + 15);
                boolean occupe = false;
                for (int xx = x; xx <= x1 && !occupe; xx += 2) {
                    for (int zz = z; zz <= z1 && !occupe; zz += 2) {
                        if (!lvl.hasChunk(xx >> 4, zz >> 4)) continue;
                        occupe = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, xx, zz) > bas;
                    }
                }
                if (!occupe) continue;
                mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x, bas, z, x1, haut, z1));
                n++;
            }
        }
        // au-dessus du couloir même (dégagé jusqu'à oy + 46) : restes d'autres bancs (pistes dans le ciel au-dessus du
        // point d'apparition…) ou surplomb du relief ; une colonne ouverte sur le ciel suffit à donner 15 au sol
        int dessus = oy + 47;
        for (int z = oz - 6; z <= oz + 112; z += 24) {
            int z1 = Math.min(oz + 112, z + 23), hMax = 0;
            for (int x = ox - 5; x <= ox + 5; x++) {
                for (int zz = z; zz <= z1; zz++) {
                    int hh = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, zz);
                    if (hh > dessus && hh > hMax) {
                        hMax = hh;
                        System.out.println("[lot5] au-dessus du couloir : " + lvl.getBlockState(new net.minecraft.core.BlockPos(x, hh - 1, zz))
                            + " en " + x + " " + (hh - 1) + " " + zz);
                    }
                }
            }
            for (int y = dessus; y < hMax; y += 100) {
                mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 5, y, z, ox + 5,
                    Math.min(hMax - 1, y + 99), z1));
            }
        }
        mc.player.connection.sendCommand(String.format("kill @e[type=!minecraft:player,x=%d,y=%d,z=%d,dx=129,dy=120,dz=241]",
            ox - 64, oy - 2, oz - 64));
        System.out.println("[lot5] ciel : " + n + " carreau(x) de 16 × 16 dégagé(s) entre y " + bas + " et " + haut);
    }

    /** Points témoins : le sol du couloir à 0, 30, 60, 100 blocs, et la surface des abords à 24 et 40 blocs de côté. */
    private static final int[][] TEMOINS = {{0, 0}, {0, 30}, {0, 60}, {0, 100}, {-24, 40}, {24, 40}, {-40, 80}, {40, 80}};

    private static int lumiereTemoin(Minecraft mc, int[] t) {
        int x = ox + t[0], z = oz + t[1];
        int y = t[0] == 0 ? oy : mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        return mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new net.minecraft.core.BlockPos(x, y, z));
    }

    private static boolean lumiereCielPleine(Minecraft mc) {
        for (int[] t : TEMOINS) if (lumiereTemoin(mc, t) < 15) return false;
        return true;
    }

    private static String lumiereCiel(Minecraft mc) {
        StringBuilder b = new StringBuilder();
        for (int[] t : TEMOINS) b.append(String.format("(%d, %d) = %d  ", t[0], t[1], lumiereTemoin(mc, t)));
        return b.toString();
    }

    private static void mur(Minecraft mc, boolean on) {
        mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:%s", ox - 5, oy, oz + 10, ox + 5, oy + 7, oz + 10,
            on ? "stone_bricks" : "air"));
        if (!on) regazonner(mc);
    }

    /**
     * L'herbe couverte par le mur ou le toit passe en terre : des traits bruns restaient dans le couloir sur toutes
     * les captures suivantes. On la remet après chaque retrait.
     */
    private static void regazonner(Minecraft mc) {
        mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block replace minecraft:dirt",
            ox - 5, oy - 1, oz - 6, ox + 5, oy - 1, oz + 112));
    }

    /** Un toit de 4 blocs de haut là où l'arc d'une charge de 600 ms visée à 20° redescend à 5 blocs. */
    private static int zToitPose = 30;

    private static void toit(Minecraft mc, boolean on) { toit(mc, on, -20f); }

    private static void toit(Minecraft mc, boolean on, float tangage) {
        Regles r = Regles.DEFAUT;
        double[] v = SautChargeLoi.vitesseDepart(SautChargeLoi.charge(600, r), 0, tangage, r);
        double[] pos = {0, 0, 0};
        int zToit = zToitPose;
        if (on) {
            for (int i = 0; i < 400; i++) {
                SautChargeLoi.pas(pos, v, r);
                if (v[1] < 0 && pos[1] <= 5.5) { zToit = (int) Math.floor(pos[2]); break; }
            }
            zToitPose = zToit;
        }
        mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:%s", ox - 4, oy, oz + zToit - 3, ox + 4, oy + 3, oz + zToit + 6,
            on ? "spruce_planks" : "air"));
        if (!on) regazonner(mc);
    }

    /* ================================================================ serveur simulé (ShinobiCombat SautCharge) */

    private static void serveur(CombatInputPayload in) {
        Regles r = ChakraJump.INSTANCE.regles();
        switch (in.kind()) {
            case CombatInputPayload.KIND_SAUT_DEBUT -> {
                if ("chakra".equals(refusServeur)) { FILE.add(non((byte) 3, 0)); debutSrv = -1; return; }
                debutSrv = h;
            }
            case CombatInputPayload.KIND_SAUT_ANNULE -> debutSrv = -1;
            case CombatInputPayload.KIND_SAUT_RELACHE -> {
                if (debutSrv < 0) { FILE.add(non((byte) 10, 0)); return; }
                double c = SautChargeLoi.charge(Math.max(r.seuilMs(), h - debutSrv), r);
                debutSrv = -1;
                double[] v = SautChargeLoi.vitesseDepart(c, in.a(), in.b(), r);
                var mc = Minecraft.getInstance();
                var pl = mc.player;
                double borne = SautChargeLoi.porteeVue(mc.options.getEffectiveRenderDistance(), r);
                byte dr = SautChargeLoi.bornerArrivee(v, borne, r, SautArc.relief(mc.level, pl.getX(), pl.getY(), pl.getZ()))
                    ? (byte) 1 : 0;
                byte[] rep = go(v, c, SautChargeLoi.rechargeMs(c, r), dr, borne);
                if (retarderGo) goRetarde = rep; else FILE.add(rep);
            }
            case CombatInputPayload.KIND_SAUT_GO_IGNORE -> goIgnoreRecu = true;
            default -> { }
        }
    }

    private static byte[] go(double[] v, double c, long recharge, byte dr, double borne) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(b)) {
            o.writeByte(1);
            o.writeFloat((float) v[0]);
            o.writeFloat((float) v[1]);
            o.writeFloat((float) v[2]);
            o.writeFloat((float) c);
            o.writeInt((int) recharge);
            o.writeByte(dr);
            o.writeFloat((float) borne);
        } catch (IOException ignored) { }
        return b.toByteArray();
    }

    private static byte[] non(byte raison, int reste) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(b)) {
            o.writeByte(0);
            o.writeByte(raison);
            o.writeInt(reste);
        } catch (IOException ignored) { }
        return b.toByteArray();
    }

    private static byte[] regles(Regles r) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream o = new DataOutputStream(b)) {
            o.writeByte(1);
            o.writeInt((int) r.chargeMs());
            for (double d : new double[] {r.seuil(), r.vitesseMin(), r.vitessePente(), r.elanMin(), r.elanPente(), r.vyMax(),
                r.trainee(), r.coutMinPct(), r.coutMaxPct()}) o.writeFloat((float) d);
            o.writeInt((int) r.rechargeMinMs());
            o.writeInt((int) r.rechargeMaxMs());
            o.writeFloat((float) r.porteeMax());
            o.writeFloat((float) r.viseeMaxDeg());
        } catch (IOException ignored) { }
        return b.toByteArray();
    }

    /* ================================================================ outils */

    private static void figer(long now) {
        h = now;
        ChakraJump.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    /**
     * Mode de caméra Reborn et perspective du jeu ensemble : la perspective n'est posée par {@code RebornCamera.tickView}
     * que depuis la boucle des touches, qui ne tourne pas toujours (au 10/11, un banc entier est resté en 1re personne).
     */
    private static void camera(Minecraft mc, RebornCamera.Mode m) {
        cameraVoulue = m;
        RebornCamera.INSTANCE.setMode(m);
        mc.options.setCameraType(m == RebornCamera.Mode.FIRST ? net.minecraft.client.CameraType.FIRST_PERSON
            : net.minecraft.client.CameraType.THIRD_PERSON_BACK);
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot5] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        ETAPES.add(new Etape(delai, mc -> { action.accept(mc); return true; }, nom));
    }

    /** Étape qui attend une condition (au plus {@code max} ticks, puis échec et on continue). */
    private static void attendre(Predicate<Minecraft> cond, int max, String nom) {
        ETAPES.add(new Etape(1, mc -> {
            if (cond.test(mc)) { attente = 0; return true; }
            if (++attente > max) { attente = 0; verifie(false, "attente trop longue : " + nom); return true; }
            return false;
        }, "attente : " + nom));
    }

    /**
     * Demande une capture : elle part quand {@value #IMAGES_AVANT_CAPTURE} images de plus sont dessinées (la lecture d'une
     * capture fige le rendu un instant, l'image suivante pouvait encore être l'ancienne) ; aucune étape ne passe avant.
     */
    private static void shot(Minecraft mc, String name) {
        capture = name;
        imagesCapture = images;
    }

    private static void prendre(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, capture + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println("[lot5] capture " + capture + " (" + mc.getFps() + " i/s, image " + images + ")");
        capture = null;
        imagesEtape = images;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        byte[] r = FILE.poll();
        if (r != null) ChakraJump.INSTANCE.recevoir(CombatPayload.TYPE_SAUT, r);
        t++;
        if (capture != null) {
            if (images - imagesCapture >= IMAGES_AVANT_CAPTURE) prendre(mc);
            return;
        }
        if (t == 0) prochaine = 60 + ETAPES.get(0).delai();
        if (etape >= ETAPES.size() || t < prochaine) return;
        // au moins deux images rendues depuis l'étape d'avant : fenêtre en arrière-plan, le jeu ralentit ses images et une
        // capture prise sur les ticks seuls montrait l'état précédent (filet de la pleine charge resté à 58 %)
        if (attente == 0 && images - imagesEtape < 2) return;
        Etape e = ETAPES.get(etape);
        if (attente == 0) System.out.println("[lot5] " + e.nom());
        if (!e.action().test(mc)) return;
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + ETAPES.get(etape).delai();
    }
}
