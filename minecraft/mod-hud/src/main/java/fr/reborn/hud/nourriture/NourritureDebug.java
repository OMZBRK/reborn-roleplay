package fr.reborn.hud.nourriture;

import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.notif.NotifHud;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Banc d'essai de la nourriture, <b>inactif en production</b> ({@code REBORN_NOURRITURE_DEBUG=1}, monde solo de dev,
 * jamais de serveur) : rejoue les messages de {@code reborn:nourriture}, {@code reborn:modifs}, {@code reborn:notif} et
 * la satiété des vitals (cuisinier qui prépare, plat prêt, invite « Tendre », menu d'interaction, on me tend, j'attends,
 * manger, repas en pause, plat froid, effets, régénération en pause, Affamé, FAIM, Fatigue, ration, onglet Vivres de
 * l'échoppe) et capture chaque visuel dans {@code run/screenshots/nourriture_*.png}, puis ferme le jeu.
 *
 * <p><b>Le monde est remis en état</b> : barre d'action, emplacement choisi, orientation, heure et météo sont relevés au
 * début et rendus à la fin ; le mannequin posé pour l'invite est retiré.
 */
public final class NourritureDebug {

    private static int t = -1;
    private static final List<ItemStack> BARRE = new ArrayList<>();
    private static int slot;
    private static long heure = -1;
    private static String mode = "survival";
    private static boolean pluie;

    private NourritureDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_NOURRITURE_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(NourritureDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        var im = InteractionMode.INSTANCE;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);   // retours de commandes hors capture
        // Le pseudo de dev est tiré au hasard : il peut retomber sur un joueur mort lors d'un ancien banc.
        if (t < 20 && mc.player.isDeadOrDying() && t % 5 == 0) {
            mc.player.respawn();
            mc.setScreenAndShow(null);
        }
        switch (t) {
            case 20 -> {
                releverMonde(mc);
                RebornSession.debugForceRp = true;
                // Le joueur de dev (nouveau pseudo à chaque lancement) apparaît en l'air : en créatif le temps du banc,
                // sinon il tombe et meurt. Nettoyage d'un banc précédent : objets au sol et mannequins sous l'apparition.
                c.sendCommand("gamemode creative");
                vol(mc, true);
                c.sendCommand("kill @e[type=minecraft:item,distance=..96]");
                c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                // Point de vue : REBORN_NOURRITURE_POS="x y z lacet tangage" (jamais de coordonnées dans le dépôt).
                String pos = System.getenv("REBORN_NOURRITURE_POS");
                c.sendCommand(pos != null && !pos.isBlank() ? "tp @s " + pos.trim() : "tp @s ~ ~ ~ ~-35 18");
                c.sendCommand("item replace entity @s hotbar.0 with minecraft:bundle[custom_name=\"Ingrédients : ramen shōyu\"] 1");
                c.sendCommand("item replace entity @s hotbar.1 with minecraft:mushroom_stew[custom_name=\"Ramen shōyu\",max_stack_size=16] 2");
                for (int i = 2; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
                mc.player.getInventory().setSelectedSlot(0);
                VitalsFeed.update(82, 100, 64, 100);
                VitalsFeed.debugSatiete(0.70f, 0f, 0, 0);
            }
            case 40 -> {
                mc.gui.hud.getChat().clearMessages(false);
                NourritureClient.debugReceive("{\"t\":\"prep\",\"nom\":\"Ramen shōyu\",\"ic\":\"chaud\",\"dur\":15,\"fait\":6}");
            }
            case 60 -> shot(mc, "nourriture_01_preparer");
            case 62 -> {
                NourritureClient.debugReceive("{\"t\":\"prep_fin\",\"ok\":true}");
                NotifHud.objet(true, 2, "Ramen shōyu", "COMMUN", "minecraft:mushroom_stew", null, "chaud");
            }
            case 70 -> shot(mc, "nourriture_02_plats_prets");
            case 21, 30, 45 -> vol(mc, true);
            case 72 -> {
                mc.player.getInventory().setSelectedSlot(1);
                {
                    // devant la caméra (la caméra épaule ne suit pas forcément le lacet du joueur), à hauteur des pieds
                    var cam = mc.gameRenderer.mainCamera();
                    var fw = cam.forwardVector();
                    double n = Math.max(0.01, Math.hypot(fw.x(), fw.z()));
                    double dx = fw.x() / n, dz = fw.z() / n;
                    double x = mc.player.getX() + dx * 2.2 - dz * 0.8, z = mc.player.getZ() + dz * 2.2 + dx * 0.8;
                    float lacet = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
                    c.sendCommand(String.format(java.util.Locale.ROOT,
                        "summon minecraft:mannequin %.2f %.2f %.2f {NoGravity:1b,Rotation:[%.1ff,0f]}", x, mc.player.getY(), z, lacet));
                }
                NourritureClient.debugReceive("{\"t\":\"main\",\"nom\":\"Ramen shōyu\",\"ic\":\"ramen\",\"chaud\":598}");
            }
            case 82 -> NourritureHud.debugCible = mannequin(mc);
            case 92 -> shot(mc, "nourriture_03_invite_tendre");
            case 94 -> {
                NourritureHud.debugCible = null;
                NotifHud.message("info", null, "+120 ryo", "de Ren Hyūga", "ryo");
                im.toggle();
            }
            case 96 -> im.debugOpen("Ren_Hyuga", InteractionMenus.forPlayer("Ren_Hyuga"), 0);
            case 106 -> shot(mc, "nourriture_04_menu_tendre");
            case 108 -> {
                im.debugClose();
                NourritureClient.debugReceive("{\"t\":\"main\"}");
                NourritureClient.debugReceive("{\"t\":\"offre\",\"de\":\"Teuchi\",\"quoi\":\"un Ramen shōyu\",\"etat\":\"chaud\","
                    + "\"mat\":\"minecraft:mushroom_stew\",\"s\":10}");
            }
            case 128 -> shot(mc, "nourriture_05_on_me_tend");
            case 130 -> {
                NourritureClient.debugReceive("{\"t\":\"offre_fin\"}");
                NourritureClient.debugReceive("{\"t\":\"attente\",\"a\":\"Ren Hyūga\",\"quoi\":\"un Ramen shōyu\",\"s\":10}");
            }
            case 140 -> shot(mc, "nourriture_06_je_tends");
            case 142 -> {
                NourritureClient.debugReceive("{\"t\":\"attente_fin\"}");
                NourritureClient.debugReceive("{\"t\":\"main\",\"nom\":\"Ramen shōyu\",\"ic\":\"ramen\",\"chaud\":420}");
                VitalsFeed.debugSatiete(0.64f, 0.36f, 0, 0);
                NourritureClient.debugReceive("{\"t\":\"repas\",\"nom\":\"Ramen shōyu\",\"ic\":\"ramen\",\"dur\":45,\"fait\":18,\"vit\":1}");
            }
            case 156 -> shot(mc, "nourriture_07_manger");
            case 158 -> {
                VitalsFeed.debugSatiete(0.64f, 0.36f, 28, 0);
                NourritureClient.debugReceive("{\"t\":\"repas\",\"nom\":\"Ramen shōyu\",\"ic\":\"ramen\",\"dur\":45,\"fait\":18,\"vit\":1,\"pause\":28}");
            }
            case 166 -> shot(mc, "nourriture_08_repas_en_pause");
            case 168 -> {
                NourritureClient.debugReceive("{\"t\":\"repas_fin\",\"ok\":false}");
                NourritureClient.debugReceive("{\"t\":\"main\",\"nom\":\"Ramen shōyu\",\"ic\":\"ramen\",\"chaud\":0}");
                VitalsFeed.debugSatiete(0.40f, 0f, 0, 0);
            }
            case 176 -> shot(mc, "nourriture_09_plat_froid");
            case 178 -> {
                NourritureClient.debugReceive("{\"t\":\"main\"}");
                c.sendCommand("item replace entity @s hotbar.1 with minecraft:bowl[custom_name=\"Bol vide\"] 1");
                VitalsFeed.update(86, 100, 64, 100);
                VitalsFeed.debugSatiete(1.0f, 0f, 0, VitalsFeed.F_REGEN);
                NourritureClient.debugModifs("{\"mods\":[{\"id\":\"regeneration\",\"nom\":\"Régénération\",\"ic\":\"regeneration\","
                    + "\"b\":1,\"val\":\"×2\",\"reste\":300,\"duree\":300}]}");
                NotifHud.message("info", null, "Rassasié", "100 %", "satiete_prolongee");
                NotifHud.message("info", null, "Régénération ×2", "5 min", "regeneration");
            }
            case 190 -> shot(mc, "nourriture_10_effet_regeneration");
            case 192 -> NourritureClient.debugModifs("{\"mods\":[{\"id\":\"regeneration\",\"nom\":\"Régénération\",\"ic\":\"regeneration\","
                + "\"b\":1,\"val\":\"×2\",\"reste\":252,\"duree\":300},{\"id\":\"satiete_prolongee\",\"nom\":\"Satiété prolongée\","
                + "\"ic\":\"satiete_prolongee\",\"b\":1,\"val\":\"−50 %\",\"reste\":1060,\"duree\":1200},{\"id\":\"pilule_soldat\","
                + "\"nom\":\"Pilule du soldat\",\"ic\":\"pilule\",\"b\":1,\"val\":\"+40 %\",\"reste\":165,\"duree\":180}]}");
            case 260 -> shot(mc, "nourriture_11_plusieurs_effets");
            case 262 -> {
                VitalsFeed.debugSatiete(0.95f, 0f, 24, 0);
                NourritureClient.debugModifs("{\"mods\":[{\"id\":\"regeneration\",\"nom\":\"Régénération\",\"ic\":\"regeneration\","
                    + "\"b\":1,\"val\":\"×2\",\"reste\":250,\"duree\":300,\"pause\":1}]}");
            }
            case 270 -> shot(mc, "nourriture_12_regeneration_en_pause");
            case 272 -> {
                NourritureClient.debugModifs("{\"mods\":[]}");
                VitalsFeed.debugSatiete(0.18f, 0f, 0, VitalsFeed.F_AFFAME);
            }
            case 280 -> shot(mc, "nourriture_13_affame");
            case 282 -> VitalsFeed.debugSatiete(0f, 0f, 0, VitalsFeed.F_FAIM);
            case 290 -> shot(mc, "nourriture_14_faim");
            case 292 -> {
                VitalsFeed.debugSatiete(0.95f, 0f, 0, 0);
                NourritureClient.debugModifs("{\"mods\":[{\"id\":\"fatigue\",\"nom\":\"Fatigue\",\"ic\":\"fatigue\",\"b\":0,"
                    + "\"val\":\"pas de sprint\",\"reste\":292,\"duree\":300}]}");
            }
            case 300 -> shot(mc, "nourriture_15_fatigue_contrecoup");
            case 302 -> {
                NourritureClient.debugModifs("{\"mods\":[]}");
                VitalsFeed.debugSatiete(0.16f, 0f, 0, VitalsFeed.F_AFFAME);
                c.sendCommand("item replace entity @s hotbar.0 with minecraft:bread[custom_name=\"Ration sèche\"] 2");
                c.sendCommand("item replace entity @s hotbar.1 with minecraft:air");
                mc.player.getInventory().setSelectedSlot(0);
                NotifHud.objet(true, 2, "Ration sèche", "COMMUN", "minecraft:bread", null, "30 ryo");
            }
            case 312 -> shot(mc, "nourriture_16_ration_achetee");
            case 314 -> {
                VitalsFeed.debugSatiete(0.24f, 0.17f, 0, 0);
                NourritureClient.debugReceive("{\"t\":\"repas\",\"nom\":\"Ration sèche\",\"ic\":\"ration\",\"dur\":20,\"fait\":7,\"vit\":2}");
            }
            case 324 -> shot(mc, "nourriture_17_ration_assis");
            case 326 -> {
                NourritureClient.debugReceive("{\"t\":\"repas_fin\",\"ok\":true}");
                fr.reborn.hud.menu.shop.ShopData.update("{\"ryo\":340,\"price\":500,\"owned\":[],\"appearance\":\"\",\"maps\":[],"
                    + "\"vivres\":[{\"id\":\"ration_seche\",\"name\":\"Ration sèche ×2\",\"price\":30},"
                    + "{\"id\":\"panier_ramen_shoyu\",\"name\":\"Ingrédients : ramen shōyu\",\"price\":30},"
                    + "{\"id\":\"panier_ramen_miso\",\"name\":\"Ingrédients : ramen miso\",\"price\":30},"
                    + "{\"id\":\"panier_dango\",\"name\":\"Ingrédients : dango\",\"price\":20},"
                    + "{\"id\":\"panier_the_vert\",\"name\":\"Ingrédients : thé vert\",\"price\":10},"
                    + "{\"id\":\"panier_bento\",\"name\":\"Ingrédients : bento\",\"price\":40}]}");
                fr.reborn.hud.menu.widget.ShopScreen s = new fr.reborn.hud.menu.widget.ShopScreen(null);
                mc.setScreenAndShow(s);
            }
            case 330 -> {
                try {
                    var m = fr.reborn.hud.menu.widget.ShopScreen.class.getDeclaredMethod("switchTab", int.class);
                    m.setAccessible(true);
                    if (mc.gui.screen() instanceof fr.reborn.hud.menu.widget.ShopScreen s) m.invoke(s, 2);
                } catch (ReflectiveOperationException ignored) {
                    // onglet introuvable : capture de l'onglet par défaut
                }
            }
            case 350 -> shot(mc, "nourriture_18_echoppe_vivres");
            case 352 -> {
                mc.setScreenAndShow(null);
                remettreMonde(mc);
            }
            case 376 -> c.sendCommand("gamemode " + mode);
            case 380 -> mc.stop();
            default -> { }
        }
    }

    private static Entity mannequin(Minecraft mc) {
        Entity best = null;
        double d = 9;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!"minecraft:mannequin".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) continue;
            double x = e.distanceToSqr(mc.player);
            if (x < d) { d = x; best = e; }
        }
        return best;
    }

    /** Relève la barre d'action, l'emplacement choisi, le mode de jeu, l'heure et la météo (monde solo). */
    private static void releverMonde(Minecraft mc) {
        BARRE.clear();
        for (int i = 0; i < 9; i++) BARRE.add(mc.player.getInventory().getItem(i).copy());
        slot = mc.player.getInventory().getSelectedSlot();
        if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
        var srv = mc.getSingleplayerServer();
        if (srv != null) {
            heure = srv.overworld().getOverworldClockTime();
            pluie = srv.overworld().isRaining();
        }
    }

    /** Rend tout ce que le banc a changé. */
    private static void remettreMonde(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
        c.sendCommand("tp @s ~ ~ ~ ~35 0");
        vol(mc, false);
        var srv = mc.getSingleplayerServer();
        if (srv != null) {
            var uuid = mc.player.getUUID();
            List<ItemStack> copie = new ArrayList<>(BARRE);
            int s = slot;
            srv.execute(() -> {
                var sp = srv.getPlayerList().getPlayer(uuid);
                if (sp == null) return;
                for (int i = 0; i < 9 && i < copie.size(); i++) sp.getInventory().setItem(i, copie.get(i));
                sp.getInventory().setSelectedSlot(s);
            });
        }
        if (heure >= 0) c.sendCommand("time set " + heure);
        c.sendCommand(pluie ? "weather rain" : "weather clear");
        NourritureClient.clear();
        VitalsFeed.clear();
        RebornSession.debugForceRp = false;
    }

    /** Vol créatif (le point d'apparition du monde de dev est au-dessus du vide). */
    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
