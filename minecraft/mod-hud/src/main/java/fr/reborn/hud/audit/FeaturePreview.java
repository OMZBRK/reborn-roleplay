package fr.reborn.hud.audit;

import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoDebug;
import fr.reborn.hud.ko.VolonteHud;
import fr.reborn.hud.menu.stats.StatsScreen;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.staff.GardeScreen;
import fr.reborn.hud.staff.StaffClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Aperçu avant publication, <b>inactif en production</b> ({@code REBORN_FEATURE_PREVIEW=1}, monde solo de dev) :
 * rejoue les messages serveur du lot en cours et capture chaque visuel dans run/screenshots/preview_*.png.
 * Lot actuel : Volonté facultative, inconscience → /hopital, menu du corps (RPK, exécutions), scène de mort.
 */
public final class FeaturePreview {

    private static int t = -1;

    private FeaturePreview() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_FEATURE_PREVIEW"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(FeaturePreview::tick);
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static final String START = "{\"t\":\"start\",\"keys\":[0,1,0,2,1],"
            + "\"times\":[1100,1820,2560,3300,4020],\"win\":190,\"left\":2}";

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        switch (t) {
            case 30 -> {
                RebornSession.debugForceRp = true;
                VitalsFeed.update(12, 100, 40, 100);
                cmd(mc, "time set 13000");
                cmd(mc, "weather clear");
                cmd(mc, "gamemode survival");
                cmd(mc, "tp @s ~ ~ ~ -90 5");
                cmd(mc, "kill @e[type=mannequin]");
                mc.gui.hud.getChat().clearMessages(false);
            }
            // À terre, sans la Volonté (facultative) : pas d'invite
            case 60 -> { KoClient.event(KoClient.EV_A_TERRE); KoClient.update(KoDebug.ko(1, 0, 38, 45, false)); }
            case 100 -> shot(mc, "preview_01_a_terre_sans_volonte");
            // À terre, avec la technique Volonté : invite Espace
            case 105 -> VolonteHud.debugReceive("{\"t\":\"avail\",\"left\":2}");
            case 115 -> shot(mc, "preview_02_a_terre_avec_volonte");
            // Inconscient, passé les 5 min : plus de compteur, /hopital proposé
            case 120 -> { VolonteHud.clear(); KoClient.update(KoDebug.ko(2, 0, 0, 300, true)); }
            case 150 -> shot(mc, "preview_03_inconscient_hopital");
            // Menu d'interaction sur un corps
            case 160 -> {
                KoClient.update(null);
                cmd(mc, "summon mannequin ^1.5 ^ ^3 {Rotation:[150f,0f],Tags:[\"ko_test\"]}");
                fr.reborn.hud.ko.CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"ex\":["
                        + "{\"id\":\"coup_de_grace\",\"n\":\"Coup de grâce\"},{\"id\":\"etranglement\",\"n\":\"Étranglement\"}]}");
            }
            case 175 -> fr.reborn.hud.interaction.InteractionMode.INSTANCE.debugOpen("Ren Hyūga",
                    fr.reborn.hud.interaction.InteractionMenus.forBody("Mika_", new fr.reborn.hud.ko.CorpsClient.Body(1, 0)), 5);
            case 185 -> shot(mc, "preview_04_menu_corps_avant_rpk");
            case 190 -> fr.reborn.hud.interaction.InteractionMode.INSTANCE.debugOpen("Ren Hyūga",
                    fr.reborn.hud.interaction.InteractionMenus.forBody("Mika_", new fr.reborn.hud.ko.CorpsClient.Body(2, 2)), 5);
            case 200 -> shot(mc, "preview_05_menu_corps_rpk_validee");
            // Corps laissé au sol : habillé de l'apparence Reborn
            case 205 -> {
                fr.reborn.hud.interaction.InteractionMode.INSTANCE.debugClose();
                fr.reborn.hud.skin.SkinSpec l = new fr.reborn.hud.skin.SkinSpec();
                l.skinColor = fr.reborn.hud.skin.SkinSpec.skinRamp(0.3f);
                l.hairId = "Cheveux_Sasuke";
                l.hairColor = 0xFF1A1414;
                l.outfitId = "Complet_Uchiha";
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.client.entity.ClientMannequin) {
                        fr.reborn.hud.ko.CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"ex\":[],\"cp\":[{\"m\":\""
                                + e.getUUID() + "\",\"a\":" + com.google.gson.JsonParser.parseString(
                                        new com.google.gson.Gson().toJson(l.serialize())) + "}]}");
                    }
                }
            }
            case 220 -> shot(mc, "preview_06_corps_apparence_reborn");
            // Scène de mort : les yeux se ferment, noir, « Assister à votre réelle fin ? », puis vue cinéma
            case 230 -> fr.reborn.hud.ko.CorpsClient.debugReceive("{\"t\":\"mort\",\"nom\":\"Ren Hyūga\",\"min\":4}");
            case 245 -> shot(mc, "preview_07_mort_paupieres");
            case 290 -> shot(mc, "preview_08_mort_noir");
            case 330 -> shot(mc, "preview_09_mort_question");
            case 335 -> { if (mc.gui.screen() instanceof fr.reborn.hud.ko.DeathScreen d) d.debugAnswer(true); }
            case 385 -> shot(mc, "preview_10_mort_scene_cinema");
            case 395 -> { fr.reborn.hud.ko.CorpsClient.debugReceive("{\"t\":\"fin\"}"); cmd(mc, "kill @e[tag=ko_test]"); }
            case 410 -> mc.stop();
            default -> { }
        }
    }

    private static void inject(String json) {
        try {
            var m = StaffClient.class.getDeclaredMethod("receive", String.class);
            m.setAccessible(true);
            m.invoke(null, json);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static final String SNAP = """
            {"t":"snap","grade":2,"gradeName":"Modérateur","staffOnline":3,
             "alerts":[
              {"id":"kill-7","kind":"mort","uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","rank":"Genin","village":"konoha","online":true,
               "motif":"Mise à mort RP demandée par Kazuki Uchiha","ctx":["world 640 70 -120","valider ou refuser"],"score":0},
              {"id":"aide-2","kind":"aide","uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","rank":"Chūnin","online":true,
               "motif":"Appel à l'aide · à terre","ctx":["world 700 66 -180","il y a 2 min"],"score":0}],
             "players":[],"chat":[
              {"time":"21:02","grade":0,"name":"Système","text":"Mort RP à trancher : Ren Hyūga (demandée par Raiden_FR).","sys":true}],
             "journal":[]}
            """;
}
