package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.mudra.MudraCanal;
import fr.reborn.hud.mudra.MudraClient;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Banc de la <b>barre de mudras</b> (14/11), <b>inactif en production</b> : {@code REBORN_LOT6_DEBUG=mudra} (monde solo de
 * dev, jamais de serveur). Serveur ShinobiCore simulé côté client au format du canal {@code reborn:mudra} (règles,
 * ouverture libre et guidée, fin, refus) ; la touche passe par le vrai {@link MudraClient#basculer()}, les signes par le
 * vrai {@link MudraClient#signe(int)} ; horloge du mod figée et avancée à la main (images reproductibles).
 *
 * <p>Captures {@code run/screenshots/mudra_*.png} : entrée en cascade, barre posée, signe composé, temps presque écoulé,
 * réussite, mode guidé (signe attendu, avancement, mauvais signe), refus, pose du personnage vue de face, 1366 × 768.
 * Bilans « [mudra] OK / ECHEC », puis le jeu se ferme.
 */
public final class Lot6MudraDebug {

    private record Etape(int delai, Predicate<Minecraft> action, String nom) { }

    private static final List<Etape> ETAPES = new ArrayList<>();
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    private static int t = -1, etape, prochaine, echecs, attente, images, imagesEtape;
    private static final int IMAGES_ENTRE_ETAPES = 3;
    private static long h;
    private static String mode = "creative";
    private static RebornCamera.Mode cameraAvant;
    private static CameraType vueAvant;

    /* serveur simulé */
    private static int ouvertures, signes, fermetures, session, dernierSigne = -1, derniereSession = -1;
    private static String reponse = "libre";

    private Lot6MudraDebug() {}

    public static void init() {
        String env = String.valueOf(System.getenv("REBORN_LOT6_DEBUG"));
        if (!"mudra".equalsIgnoreCase(env) && !"mudra_pose".equalsIgnoreCase(env)) return;
        // mudra_pose : la pose du personnage seulement (de face, puis de dos), pour la régler
        boolean complet = "mudra".equalsIgnoreCase(env);

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            c.sendCommand("tp @s ~ ~ ~ 0 4");
            cameraAvant = RebornCamera.INSTANCE.mode();
            vueAvant = mc.options.getCameraType();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            RebornCamera.INSTANCE.initOrientation(0f, 4f);
            h = Util.getMillis();
            MudraClient.horloge = () -> h;
            MudraCanal.debugServeur = Lot6MudraDebug::serveur;
        }, "préparation");

        if (!complet) pas(20, mc -> MudraCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"actif\":true,\"max\":12}"), "règles");
        if (complet) {
        /* ---- touche et règles */
        pas(20, mc -> {
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(HudKeybinds.MUDRAS);
            verifie(HudKeybinds.MUDRAS.getDefaultKey().getValue() == GLFW.GLFW_KEY_U, "touche par défaut : U");
            System.out.println("[mudra] touche liée : " + k.getName());
            verifie(MudraClient.indexTouche(GLFW.GLFW_KEY_1) == 0 && MudraClient.indexTouche(GLFW.GLFW_KEY_0) == 9
                && MudraClient.indexTouche(GLFW.GLFW_KEY_EQUAL) == 11 && MudraClient.indexTouche(GLFW.GLFW_KEY_A) == -1,
                "rangée des chiffres : 12 touches, les autres ignorées");
            MudraClient.basculer();
            verifie(ouvertures == 0 && !MudraClient.ouverte(), "sans règles du serveur, la touche ne fait rien");
            verifie(!MudraClient.capte(), "barre fermée : la rangée des chiffres reste au jeu");
            MudraCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"actif\":true,\"max\":12}");
            verifie(MudraClient.systeme(), "règles reçues : système actif");
            MudraCanal.recevoir("{\"v\":2,\"t\":\"regles\",\"actif\":false}");
            verifie(MudraClient.systeme(), "message d'une autre version ignoré");
        }, "touche et règles");

        /* ---- mode libre */
        pas(2, mc -> { reponse = "libre"; MudraClient.basculer(); }, "appui : demande d'ouverture");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> {
            verifie(ouvertures == 1, "une seule demande d'ouverture envoyée");
            verifie(MudraClient.capte(), "barre ouverte : la rangée des chiffres est prise");
            h += 150;
        }, "entrée en cascade (150 ms)");
        pas(2, mc -> shot(mc, "mudra_entree"), "capture");
        pas(1, mc -> h += 650, "barre posée");
        pas(2, mc -> shot(mc, "mudra_barre"), "capture");
        pas(1, mc -> {
            h += 700;
            MudraClient.signe(2);
            verifie(signes == 1 && dernierSigne == 2 && derniereSession == session, "signe envoyé (Tigre, bonne session)");
            h += 60;
            MudraClient.signe(3);
            verifie(signes == 1, "un signe à moins de 120 ms du précédent n'est pas envoyé");
            h += 70;
        }, "signe composé : Tigre");
        pas(2, mc -> shot(mc, "mudra_signe"), "capture");
        pas(1, mc -> h += 3000, "le temps file");
        pas(2, mc -> shot(mc, "mudra_temps"), "capture");
        pas(1, mc -> {
            FILE.add("{\"v\":1,\"t\":\"fin\",\"n\":" + (session + 7) + ",\"res\":\"ok\"}");
        }, "fin d'une autre session");
        pas(3, mc -> {
            verifie(MudraClient.ouverte(), "fin d'une autre session ignorée");
            FILE.add("{\"v\":1,\"t\":\"fin\",\"n\":" + session + ",\"res\":\"ok\"}");
        }, "fin : réussite");
        attendre(mc -> !MudraClient.ouverte(), 40, "barre refermée");
        pas(1, mc -> {
            verifie(!MudraClient.capte(), "barre refermée : la rangée des chiffres est rendue");
            h += 130;
        }, "sortie (130 ms)");
        pas(2, mc -> shot(mc, "mudra_fin"), "capture");

        /* ---- mode guidé */
        pas(2, mc -> {
            h += 2500;
            session++;
            FILE.add("{\"v\":1,\"t\":\"ouvre\",\"n\":" + session + ",\"mode\":\"guide\",\"temps\":3500,\"gain\":3500,\"max\":3,"
                + "\"suite\":[2,5,0],\"titre\":\"Boule de feu\"}");
        }, "ouverture guidée par le serveur");
        attendre(mc -> MudraClient.ouverte(), 40, "barre guidée ouverte");
        pas(1, mc -> h += 800, "barre guidée posée");
        pas(2, mc -> shot(mc, "mudra_guide"), "capture");
        pas(1, mc -> {
            h += 600;
            MudraClient.signe(2);
            h += 130;
        }, "bon signe");
        pas(2, mc -> shot(mc, "mudra_guide_2"), "capture");
        pas(1, mc -> {
            h += 500;
            int avant = signes;
            MudraClient.signe(7);
            verifie(signes == avant + 1, "mauvais signe envoyé quand même (le serveur tranche)");
            FILE.add("{\"v\":1,\"t\":\"fin\",\"n\":" + session + ",\"res\":\"rate\",\"m\":\"Mauvais signe.\"}");
        }, "mauvais signe");
        attendre(mc -> !MudraClient.ouverte(), 40, "barre refermée sur l'échec");
        pas(1, mc -> h += 120, "sortie de l'échec");
        pas(2, mc -> shot(mc, "mudra_rate"), "capture");

        /* ---- refus, secours, extinction */
        pas(2, mc -> { h += 2500; reponse = "refus"; MudraClient.basculer(); }, "demande refusée");
        pas(4, mc -> {
            verifie(!MudraClient.ouverte(), "refus : la barre reste fermée");
            h += 200;
        }, "refus affiché");
        pas(2, mc -> shot(mc, "mudra_refus"), "capture");
        pas(2, mc -> { h += 2500; reponse = "court"; MudraClient.basculer(); }, "ouverture courte");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> h += 1000 + 1300, "échéance passée sans réponse du serveur");
        attendre(mc -> !MudraClient.ouverte(), 40, "barre refermée seule");
        pas(2, mc -> { h += 2500; reponse = "libre"; MudraClient.basculer(); }, "ouverture");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> {
            int avant = fermetures;
            MudraClient.basculer();
            verifie(!MudraClient.ouverte() && fermetures == avant + 1, "second appui : barre refermée, fermeture envoyée");
        }, "second appui");
        pas(2, mc -> { h += 2500; MudraClient.basculer(); }, "ouverture");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> {
            MudraCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"actif\":false,\"max\":12}");
            verifie(!MudraClient.ouverte() && !MudraClient.systeme(), "système éteint par le serveur : barre refermée");
            MudraCanal.recevoir("{\"v\":1,\"t\":\"regles\",\"actif\":true,\"max\":12}");
        }, "extinction par le serveur");

        }

        /* ---- pose vue de face, puis de dos */
        pas(2, mc -> {
            h += 2500;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            MudraClient.basculer();
        }, "vue de face");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> { h += 800; MudraClient.signe(4); h += 120; }, "signe : Dragon");
        pas(12, mc -> shot(mc, "mudra_pose"), "capture de la pose");
        pas(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), "vue de dos");
        pas(8, mc -> shot(mc, "mudra_pose_dos"), "capture de la pose, de dos");
        pas(1, mc -> {
            MudraClient.fermer();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            mc.options.setCameraType(vueAvant);
        }, "retour à la vue d'épaule");

        if (complet) {
        /* ---- 1366 × 768 */
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(10, mc -> { h += 2500; MudraClient.basculer(); }, "ouverture");
        attendre(mc -> MudraClient.ouverte(), 40, "barre ouverte");
        pas(1, mc -> h += 800, "barre posée");
        pas(12, mc -> shot(mc, "mudra_768"), "capture");
        pas(1, mc -> MudraClient.fermer(), "fermeture");
        pas(2, mc -> { mc.getWindow().setWindowed(1920, 1080); mc.resizeGui(); }, "fenêtre 1920 × 1080");
        }

        pas(10, mc -> {
            MudraClient.horloge = Util::getMillis;
            MudraCanal.debugServeur = null;
            MudraClient.clear();
            RebornCamera.INSTANCE.setMode(cameraAvant);
            mc.options.setCameraType(vueAvant);
            mc.player.connection.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
            System.out.println("[mudra] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");

        ClientTickEvents.END_CLIENT_TICK.register(Lot6MudraDebug::tick);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "lot6-mudra-banc"), (g, dt) -> images++);
    }

    /* ================================================================ serveur simulé */

    private static void serveur(JsonObject o) {
        String a = o.has("a") ? o.get("a").getAsString() : "";
        switch (a) {
            case "ouvrir" -> {
                ouvertures++;
                if ("refus".equals(reponse)) {
                    FILE.add("{\"v\":1,\"t\":\"refus\",\"m\":\"Entravé : pas de mudra.\"}");
                } else {
                    session++;
                    int temps = "court".equals(reponse) ? 1000 : 5000;
                    FILE.add("{\"v\":1,\"t\":\"ouvre\",\"n\":" + session + ",\"mode\":\"libre\",\"temps\":" + temps
                        + ",\"gain\":700,\"max\":12}");
                }
            }
            case "signe" -> {
                signes++;
                dernierSigne = o.get("s").getAsInt();
                derniereSession = o.get("n").getAsInt();
            }
            case "fermer" -> fermetures++;
            default -> { }
        }
    }

    /* ================================================================ outils */

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[mudra] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        ETAPES.add(new Etape(delai, mc -> { action.accept(mc); return true; }, nom));
    }

    private static void attendre(Predicate<Minecraft> cond, int max, String nom) {
        ETAPES.add(new Etape(1, mc -> {
            if (cond.test(mc)) { attente = 0; return true; }
            if (++attente > max) { attente = 0; verifie(false, "attente trop longue : " + nom); return true; }
            return false;
        }, "attente : " + nom));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println("[mudra] capture " + name);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        String r = FILE.poll();
        if (r != null) MudraCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + ETAPES.get(0).delai();
        if (etape >= ETAPES.size() || t < prochaine) return;
        if (attente == 0 && images - imagesEtape < IMAGES_ENTRE_ETAPES) return;
        Etape e = ETAPES.get(etape);
        if (attente == 0) System.out.println("[mudra] " + e.nom());
        if (!e.action().test(mc)) return;
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + ETAPES.get(etape).delai();
    }
}
