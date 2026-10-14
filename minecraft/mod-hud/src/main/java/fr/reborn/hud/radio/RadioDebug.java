package fr.reborn.hud.radio;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.HudEditScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.UUID;

/**
 * Banc de captures de la radio, <b>inactif en production</b> ({@code REBORN_RADIO_DEBUG=1}, monde solo de dev, jamais
 * de serveur) : rejoue les messages {@code reborn:radio} (escouade Kagerō de la maquette, paroles, invitation, à terre,
 * écoute staff) et capture l'écran N, l'élément HUD « Escouade », l'éditeur de HUD, le menu R (Radio, Oreillette) dans
 * {@code run/screenshots/radio_*.png}, puis ferme le jeu. Point de vue : {@code REBORN_RADIO_POS="x y z lacet tangage"}
 * (jamais de coordonnées dans le dépôt). Le skin RP prêté au joueur local et les réglages touchés sont rendus à la fin.
 */
public final class RadioDebug {

    private static int t = -1;
    private static boolean toutAvant;
    private static String mode = "survival";
    private static int echelleAvant = -1;

    static final UUID SAKURA = UUID.fromString("00000000-0000-4000-8000-00000000b002"),
            REN = UUID.fromString("00000000-0000-4000-8000-00000000b003"),
            KENTA = UUID.fromString("00000000-0000-4000-8000-00000000b004"),
            YUI = UUID.fromString("00000000-0000-4000-8000-00000000b005");

    private RadioDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_RADIO_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(RadioDebug::tick);
    }

    private static String moi(Minecraft mc) { return mc.player.getUUID().toString(); }

    private static String membre(UUID id, String nom, String court, String etat, String ore, boolean createur, boolean moi) {
        return "{\"id\":\"" + id + "\",\"perso\":\"" + id + "\",\"nom\":\"" + nom + "\",\"court\":\"" + court + "\",\"etat\":\""
                + etat + "\",\"ore\":\"" + ore + "\",\"createur\":" + createur + ",\"moi\":" + moi + "}";
    }

    /** L'escouade de la maquette v3/01 : Aki (moi, créateur), Sakura, Ren à terre, Kenta et Yui hors ligne. */
    private static String etat(Minecraft mc, boolean createur, boolean hache, String extra) {
        UUID me = mc.player.getUUID();
        return "{\"t\":\"etat\",\"ore\":\"ok\",\"num\":12,\"h\":" + hache + extra + ",\"esc\":{\"id\":\"e1\",\"nom\":\"Kagerō\",\"n\":5,\"max\":8,"
                + "\"createur\":" + createur + ",\"membres\":["
                + membre(me, "Aki Uchiha", "Aki U.", hache ? "a_terre" : "en_ligne", "ok", true, true) + ','
                + membre(SAKURA, "Sakura Haruno", "Sakura H.", "en_ligne", "ok", false, false) + ','
                + membre(REN, "Ren Hyūga", "Ren H.", "a_terre", "ok", false, false) + ','
                + membre(KENTA, "Kenta Inuzuka", "Kenta I.", "hors_ligne", "", false, false) + ','
                + membre(YUI, "Yui Nara", "Yui N.", "hors_ligne", "", false, false) + "]}}";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        var im = InteractionMode.INSTANCE;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t < 20 && mc.player.isDeadOrDying() && t % 5 == 0) {
            mc.player.respawn();
            mc.setScreenAndShow(null);
        }
        switch (t) {
            case 20 -> {
                if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
                // Échelle GUI 2 en 1080p, comme les maquettes (rendue à la fin).
                echelleAvant = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeGui();
                toutAvant = RebornPrefs.INSTANCE.radioToutEscouade;
                RebornPrefs.INSTANCE.radioToutEscouade = false;
                RebornSession.debugForceRp = true;
                c.sendCommand("gamemode creative");
                vol(mc);
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                String pos = System.getenv("REBORN_RADIO_POS");
                c.sendCommand(pos != null && !pos.isBlank() ? "tp @s " + pos.trim() : "tp @s ~ ~ ~ ~ 4");
                VitalsFeed.update(82, 100, 64, 100);
                skins(mc);
            }
            case 21, 30, 45, 90, 150 -> vol(mc);
            // ------------------------------------------------------------ écran N
            case 40 -> {
                mc.gui.hud.getChat().clearMessages(false);
                RadioClient.debugReceive(etat(mc, true, false, ""));
                RadioScreen s = new RadioScreen();
                s.debugSurvol = 3;
                mc.setScreenAndShow(s);
            }
            case 52 -> shot(mc, "radio_01_ecran_n_survol_exclure");
            case 54 -> { if (mc.gui.screen() instanceof RadioScreen s) { s.debugSurvol = 102; s.debugArmer("dissoudre"); } }
            case 62 -> shot(mc, "radio_02_ecran_n_dissoudre");
            case 64 -> {
                RadioClient.debugReceive(etat(mc, false, false, ""));
                if (mc.gui.screen() instanceof RadioScreen s) { s.debugSurvol = -1; s.debugArmer(null); }
            }
            case 72 -> shot(mc, "radio_03_ecran_n_membre");
            case 74 -> {
                RadioClient.debugReceive(etat(mc, true, false, ""));
                if (mc.gui.screen() instanceof RadioScreen s) s.debugRenommer("Kagerō no Kaze");
            }
            case 82 -> shot(mc, "radio_04_ecran_n_renommer");
            case 84 -> {
                mc.setScreenAndShow(null);
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"neuve\",\"num\":12}");
                mc.setScreenAndShow(new RadioScreen());
            }
            case 92 -> shot(mc, "radio_05_ecran_n_sans_escouade");
            // ------------------------------------------------------------ HUD « Escouade »
            case 94 -> {
                mc.setScreenAndShow(null);
                RadioClient.debugReceive(etat(mc, true, false, ""));
            }
            case 100 -> shot(mc, "radio_06_hud_silence");
            case 102 -> RadioClient.debugReceive("{\"t\":\"parle\",\"src\":\"s-sakura\",\"id\":\"" + SAKURA + "\",\"nom\":\"Sakura H.\",\"h\":false}");
            case 110 -> shot(mc, "radio_07_hud_sakura_parle");
            case 112 -> RadioHud.debugEmet = true;
            case 120 -> shot(mc, "radio_08_hud_j_emets");
            case 122 -> {
                RadioHud.debugEmet = false;
                RebornPrefs.INSTANCE.radioToutEscouade = true;
                RadioClient.debugReceive("{\"t\":\"parle\",\"src\":\"s-ren\",\"id\":\"" + REN + "\",\"nom\":\"Ren H.\",\"h\":true}");
            }
            case 130 -> shot(mc, "radio_09_hud_toute_escouade");
            case 132 -> {
                RebornPrefs.INSTANCE.radioToutEscouade = false;
                RadioClient.debugReceive("{\"t\":\"tait\",\"src\":\"s-sakura\"}");
                RadioClient.debugReceive("{\"t\":\"tait\",\"src\":\"s-ren\"}");
            }
            case 175 -> {
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"ok\",\"num\":7}");
                RadioClient.debugReceive("{\"t\":\"invit\",\"de\":\"Aki U.\",\"esc\":\"Kagerō\",\"creation\":false,\"s\":10}");
            }
            case 190 -> shot(mc, "radio_10_invitation");
            case 192 -> {
                RadioClient.debugReceive("{\"t\":\"invit_fin\"}");
                // à terre : je suis haché, Sakura aussi m'arrive hachée
                RadioClient.debugReceive(etat(mc, true, true, ""));
                RadioClient.debugReceive("{\"t\":\"parle\",\"src\":\"s-sakura2\",\"id\":\"" + SAKURA + "\",\"nom\":\"Sakura H.\",\"h\":true}");
                RadioHud.debugEmet = true;
            }
            case 200 -> shot(mc, "radio_11_a_terre_hache");
            case 202 -> {
                RadioHud.debugEmet = false;
                RadioClient.debugReceive("{\"t\":\"tait\",\"src\":\"s-sakura2\"}");
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"aucune\",\"staff\":true}");
                RadioClient.debugReceive("{\"t\":\"ecoute\",\"esc\":\"Kagerō\",\"reste\":581}");
                RadioClient.debugReceive("{\"t\":\"parle\",\"src\":\"s-sakura3\",\"id\":\"" + SAKURA + "\",\"nom\":\"Sakura H.\",\"h\":false}");
            }
            case 212 -> shot(mc, "radio_12_staff_ecoute");
            case 214 -> {
                RadioClient.debugReceive("{\"t\":\"tait\",\"src\":\"s-sakura3\"}");
                RadioClient.debugReceive("{\"t\":\"ecoute_fin\"}");
                RadioClient.debugEffacerParoles();
                // le voleur porte l'oreillette de Ren : il parle « à sa place »
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"ok\",\"num\":3,\"volee\":true,\"proprio\":\"Ren Hyūga\","
                    + "\"proprioId\":\"" + REN + "\",\"canal\":\"Kagerō\"}");
                RadioHud.debugEmet = true;
            }
            case 222 -> shot(mc, "radio_12b_oreillette_volee");
            case 224 -> {
                RadioHud.debugEmet = false;
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"aucune\"}");
            }
            // ------------------------------------------------------------ éditeur de HUD
            case 230 -> {
                mc.gui.hud.getChat().clearMessages(false);
                HudEditScreen h = new HudEditScreen(null);
                mc.setScreenAndShow(h);
            }
            case 233 -> { if (mc.gui.screen() instanceof HudEditScreen h) h.debugSelectionner(HudElement.ESCOUADE); }
            case 244 -> shot(mc, "radio_13_editeur_hud");
            // ------------------------------------------------------------ menu R
            case 246 -> {
                mc.setScreenAndShow(null);
                RadioClient.debugReceive(etat(mc, true, false, ""));
                im.toggle();
            }
            case 248 -> im.debugOpen("Ren_Hyuga", InteractionMenus.forPlayer("Ren_Hyuga"), indexDe(InteractionMenus.forPlayer("Ren_Hyuga"), "Radio"));
            case 258 -> shot(mc, "radio_14_menu_r_ajouter");
            case 260 -> {
                im.debugClose();
                RadioClient.debugReceive("{\"t\":\"etat\",\"ore\":\"neuve\",\"num\":12}");
                im.toggle();
            }
            case 262 -> im.debugOpen("Ren_Hyuga", InteractionMenus.forPlayer("Ren_Hyuga"), indexDe(InteractionMenus.forPlayer("Ren_Hyuga"), "Radio"));
            case 272 -> shot(mc, "radio_15_menu_r_creer");
            case 274 -> {
                im.debugClose();
                im.toggle();
            }
            case 276 -> {
                var items = InteractionMenus.forBody("Ren_Hyuga", new fr.reborn.hud.ko.CorpsClient.Body(1, 0));
                im.debugOpen("Ren_Hyuga", items, indexDe(items, "Oreillette"));
            }
            case 286 -> shot(mc, "radio_16_menu_r_oreillette");
            case 288 -> {
                im.debugClose();
                RadioClient.clear();
                RebornPrefs.INSTANCE.radioToutEscouade = toutAvant;
                RebornSkins.clear(mc.player.getUUID());
                RebornSession.debugForceRp = false;
                if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
            }
            case 300 -> c.sendCommand("gamemode " + mode);
            case 306 -> mc.stop();
            default -> { }
        }
    }

    private static int indexDe(java.util.List<fr.reborn.hud.interaction.InteractionItem> l, String label) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).label().equals(label)) return i;
        return 0;
    }

    private static void vol(Minecraft mc) {
        var a = mc.player.getAbilities();
        a.mayfly = true;
        a.flying = true;
        mc.player.onUpdateAbilities();
    }

    /** Têtes RP de la maquette : Aki (le joueur local), Sakura, Ren, Kenta, Yui, et les têtes d'exemple de l'éditeur. */
    private static void skins(Minecraft mc) {
        RebornSkins.applySpec(mc.player.getUUID(), look(false, 3, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha"));
        RebornSkins.applySpec(SAKURA, look(true, 2, "Cheveux_Femme", 0xFFE58FB0, 0xFF3FA060, "Complet_Femme1"));
        RebornSkins.applySpec(REN, look(false, 1, "Cheveux_4", 0xFF2A2440, 0xFFE4DDF0, "Complet_Taijutsuka"));
        RebornSkins.applySpec(KENTA, look(false, 7, "Cheveux_Base", 0xFF6A3A22, 0xFF3A2A1A, ""));
        RebornSkins.applySpec(YUI, look(true, 1, "Cheveux_Femme", 0xFFE2C070, 0xFF5A70C0, ""));
        RebornSkins.applySpec(RadioHud.EXEMPLES[0], look(false, 3, "Cheveux_Sasuke", 0xFF1A1414, 0xFF1E1A16, "Complet_Uchiha"));
        RebornSkins.applySpec(RadioHud.EXEMPLES[1], look(true, 2, "Cheveux_Femme", 0xFFE58FB0, 0xFF3FA060, "Complet_Femme1"));
        RebornSkins.applySpec(RadioHud.EXEMPLES[2], look(false, 1, "Cheveux_4", 0xFF2A2440, 0xFFE4DDF0, "Complet_Taijutsuka"));
    }

    private static SkinSpec look(boolean female, int skin, String hair, int hairColor, int eye, String outfit) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.slim = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = eye;
        l.outfitId = outfit;
        return l;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
