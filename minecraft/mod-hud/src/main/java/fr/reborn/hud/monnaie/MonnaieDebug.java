package fr.reborn.hud.monnaie;

import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.notif.NotifHud;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Banc d'essai de la monnaie et de la banque, <b>inactif en production</b> ({@code REBORN_MONNAIE_DEBUG=1}, monde solo
 * de dev, jamais de serveur ; point de vue {@code REBORN_MONNAIE_POS="x y z lacet tangage"}, jamais noté dans le dépôt) :
 * rejoue les messages de {@code reborn:monnaie} et {@code reborn:banque} et capture chaque visuel dans
 * {@code run/screenshots/monnaie_*.png}, puis ferme le jeu. Les envois du mod sont interceptés (aucun serveur).
 *
 * <p>Le pack de dev {@code run/resourcepacks/reborn_rouleaux_dev} (copie des visuels du pack Nexo, dont la bourse) est
 * activé le temps du banc. <b>Le monde est remis en état</b> : barre d'action, emplacement choisi, mode de jeu, heure,
 * météo, packs ; objets au sol et mannequins posés par le banc retirés.
 */
public final class MonnaieDebug {

    private static int t = -1;
    private static final List<ItemStack> BARRE = new ArrayList<>();
    private static int slot;
    private static long heure = -1;
    private static String mode = "survival";
    private static boolean pluie, packAjoute;
    private static final String PACK = "file/reborn_rouleaux_dev";
    private static final int ATTENTE = 200;

    private MonnaieDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_MONNAIE_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(MonnaieDebug::tick);
    }

    private static String bourse(String modele, long n, String nom) {
        return "minecraft:paper[minecraft:item_model=\"reborn:" + modele + "\",minecraft:custom_data={PublicBukkitValues:{\"reborn:ryo\":"
            + n + "L}},minecraft:item_name=\"Bourse\",minecraft:max_stack_size=1" + (nom != null ? ",minecraft:custom_name=\"" + nom + "\"" : "") + "]";
    }

    private static final String GUICHET = """
        {"t":"guichet","ouvrir":true,"onglet":0,"banque":"Banque de Konoha","titulaire":"Ren Hyūga","numero":"00042",
         "guichetier":"Hana Kurenai","allie":false,"fraisAllie":5,"compte":8600,"bourse":1240,"plafond":20000,"palier":2,
         "paliers":[{"n":1,"nom":"Livret","cout":0,"cases":2,"plafond":5000},{"n":2,"nom":"Compte","cout":2000,"cases":6,"plafond":20000},
                    {"n":3,"nom":"Compte scellé","cout":8000,"cases":12,"plafond":60000},{"n":4,"nom":"Chambre forte","cout":25000,"cases":18,"plafond":0}],
         "coffre":[{"mat":"minecraft:iron_sword","count":1,"name":"Ninjatō","weight":2,"rarity":"PEU_COMMUN","arme":true},
                   {"mat":"minecraft:leather","count":1,"name":"Tenue","weight":1,"rarity":"COMMUN"},
                   {"mat":"minecraft:bread","count":4,"name":"Ration sèche","weight":0.3,"rarity":"COMMUN"},
                   {"mat":"minecraft:arrow","count":20,"name":"Shuriken","weight":0.1,"rarity":"COMMUN"},null,null],
         "sacoche":[{"ref":"H8","item":{"mat":"minecraft:paper","model":"reborn:bourse_garnie","count":1,"name":"Bourse","weight":0,"rarity":"COMMUN","ryo":1240}},
                    {"ref":"B0","item":{"mat":"minecraft:paper","count":1,"name":"Parchemin — Suiton","weight":0.3,"rarity":"RARE"},"refus":"Ne va pas au coffre — un parchemin reste volable"},
                    {"ref":"B1","item":{"mat":"minecraft:bread","count":4,"name":"Ration sèche","weight":0.3,"rarity":"COMMUN"}}],
         "historique":[{"le":0,"d":-500,"t":"virement à Aki U."}],"solde":{"montant":500,"semaine":"2026-S42"},
         "contacts":[{"ref":"c:00000000-0000-0000-0000-000000000017","nom":"Aki Uchiha · N° 00017"}],
         "virement":{"pourcent":1,"min":5,"autre":5,"plafondJour":20000,"deja":0},
         "proches":[{"uuid":"00000000-0000-0000-0000-000000000017","nom":"Aki Uchiha"}]}
        """;

    private static final String ECHANGE = """
        ,"echange":{"id":3,"autre":"Aki Uchiha","moi":{"ryo":1500,"source":"bourse","confirme":true,"objets":[]},
          "lui":{"ryo":0,"source":"bourse","confirme":false,"objets":[{"mat":"minecraft:iron_sword","count":1,"name":"Tantō","weight":1.2,"rarity":"Peu commun","arme":true}]},
          "attenteSceau":false,"guichetier":"Hana Kurenai","frais":30,"pourcent":2,"recevoir":"compte"}}
        """;

    private static String guichet(int onglet, boolean echange) {
        String g = GUICHET.trim().replace("\"onglet\":0", "\"onglet\":" + onglet);
        if (echange) g = g.substring(0, g.length() - 1) + ECHANGE.trim();
        return g;
    }

    private static final String FOUILLE = """
        {"t":"fouille","cible":"Kenta Inuzuka","etat":"à terre","uuid":"$UUID","bourse":7800,"reste":2340,"plafond":true,"pourcent":30,
         "extraSlots":9,
         "hotbar":[null,null,null,null,null,null,null,null,{"mat":"minecraft:paper","model":"reborn:bourse_lourde","count":1,"name":"Bourse","weight":0,"rarity":"COMMUN","ryo":7800}],
         "bag":[{"mat":"minecraft:iron_sword","count":1,"name":"Tantō","weight":1.2,"rarity":"PEU_COMMUN","arme":true},
                {"mat":"minecraft:arrow","count":20,"name":"Shuriken","weight":0.1,"rarity":"COMMUN"},
                {"mat":"minecraft:bread","count":4,"name":"Ration sèche","weight":0.3,"rarity":"COMMUN"},
                {"mat":"minecraft:paper","count":1,"name":"Parchemin — Suiton","weight":0.3,"rarity":"RARE"},
                {"mat":"minecraft:leather","count":1,"name":"Tenue","weight":1,"rarity":"COMMUN"},null,null,null,null]}
        """;

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t < 20 && mc.player.isDeadOrDying() && t % 5 == 0) {
            mc.player.respawn();
            mc.setScreenAndShow(null);
        }
        // Après le téléport : on laisse charger le monde (≈ 9 s) avant la première capture.
        if (t > 21 && t < ATTENTE) { if (t % 40 == 0) vol(mc, true); return; }
        int u = t >= ATTENTE ? t - ATTENTE + 82 : t;
        switch (u) {
            case 20 -> {
                releverMonde(mc);
                echelleAvant = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeGui();
                cameraAvant = fr.reborn.hud.camera.RebornCamera.INSTANCE.mode();
                fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(fr.reborn.hud.camera.RebornCamera.Mode.FIRST);
                MonnaieClient.debugEnvoi = s -> { };
                RebornSession.debugForceRp = true;
                c.sendCommand("gamemode creative");
                vol(mc, true);
                c.sendCommand("kill @e[type=minecraft:item,distance=..64]");
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                String pos = System.getenv("REBORN_MONNAIE_POS");
                c.sendCommand(pos != null && !pos.isBlank() ? "tp @s " + pos.trim() : "tp @s ~ ~ ~ ~ 6");
                for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
                c.sendCommand("item replace entity @s hotbar.8 with " + bourse("bourse_garnie", 1240, null) + " 1");
                mc.player.getInventory().setSelectedSlot(0);
                VitalsFeed.update(82, 100, 64, 100);
                MonnaieClient.recevoir("{\"t\":\"bourse\",\"n\":1240}");
                RbCoins.debug(320);
                packDev(mc, true);
            }
            case 21 -> vol(mc, true);
            // 01 — la bourse dans la sacoche (survol de la case : info-bulle) + « Sur toi » sous la barre
            case 90 -> mc.setScreenAndShow(new fr.reborn.hud.menu.inventory.InventoryScreen());
            case 96, 100, 104 -> survolerCase(mc, "H8");
            case 106 -> shot(mc, "monnaie_01_bourse_sacoche");
            // 02 — « Combien veux-tu poser ? » par-dessus la sacoche
            case 108 -> {
                var inv = mc.gui.screen();
                mc.setScreenAndShow(CombienScreen.poser(inv, 1240).avecValeur(250));
            }
            case 118 -> shot(mc, "monnaie_02_poser");
            // 03 — la bourse posée au sol : invite « [F] Ramasser Bourse · 250 ryo »
            case 119 -> {
                mc.setScreenAndShow(null);
                vol(mc, false);
                c.sendCommand("execute at @s positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ ~ 38");
            }
            case 128 -> {
                fr.reborn.hud.notif.NotifClient.debugActif = true;
                c.sendCommand("execute at @s positioned ^ ^ ^2 positioned over motion_blocking_no_leaves run summon minecraft:item ~ ~ ~ {PickupDelay:32767,Age:-32768,Item:{id:\"minecraft:paper\",count:1,components:{"
                    + "\"minecraft:item_model\":\"reborn:bourse_garnie\",\"minecraft:custom_name\":\"Bourse · 250 ryo\","
                    + "\"minecraft:custom_data\":{PublicBukkitValues:{\"reborn:ryo\":250L}}}}}");
                NotifHud.message("info", null, "−250 ryo", "posés au sol", null);
            }
            case 141 -> shot(mc, "monnaie_03_bourse_au_sol");
            // 04 — menu R : « Tendre Bourse · 990 ryo », « Donner des ryo »
            case 142 -> {
                vol(mc, true);
                c.sendCommand("kill @e[type=minecraft:item,distance=..16]");
                c.sendCommand("tp @s ~ ~ ~ ~ 18");
                c.sendCommand("item replace entity @s hotbar.0 with " + bourse("bourse_garnie", 990, null) + " 1");
                c.sendCommand("item replace entity @s hotbar.8 with minecraft:air");
                mc.player.getInventory().setSelectedSlot(0);
                MonnaieClient.recevoir("{\"t\":\"bourse\",\"n\":990}");
                InteractionMode.INSTANCE.toggle();
            }
            case 151 -> InteractionMode.INSTANCE.debugOpen("Ren_Hyuga", InteractionMenus.forPlayer("Ren_Hyuga"), 0);
            case 157 -> shot(mc, "monnaie_04_menu_tendre");
            // 05 à 10 — le guichet : chaque onglet
            case 158 -> {
                InteractionMode.INSTANCE.debugClose();
                InteractionMode.INSTANCE.toggle();
                MonnaieClient.recevoir("{\"t\":\"bourse\",\"n\":1240}");
                GuichetScreen.debutMontant = "500";
                MonnaieClient.recevoirBanque(guichet(0, false));
            }
            case 170 -> shot(mc, "monnaie_05_guichet_deposer");
            case 172 -> { GuichetScreen.debutMontant = "1000"; mc.setScreenAndShow(new GuichetScreen().onglet(1)); }
            case 180 -> shot(mc, "monnaie_06_guichet_retirer");
            case 182 -> {
                GuichetScreen.debutMontant = "500";
                GuichetScreen.debutCible = "Aki Uchiha · N° 00017";
                GuichetScreen.debutMotif = "remboursement du ramen";
                mc.setScreenAndShow(new GuichetScreen().onglet(2));
            }
            case 190 -> shot(mc, "monnaie_07_guichet_virement");
            case 192 -> mc.setScreenAndShow(new GuichetScreen().onglet(3));
            case 200 -> shot(mc, "monnaie_08_echange_choisir");
            case 202 -> { MonnaieClient.recevoirBanque(guichet(3, true)); mc.setScreenAndShow(new GuichetScreen().onglet(3)); }
            case 212 -> shot(mc, "monnaie_09_echange_garanti");
            case 214 -> {
                MonnaieClient.recevoirBanque(guichet(4, false).replace("\"historique\"", "\"refus\":{\"ref\":\"B0\",\"texte\":\"Ce parchemin ne va pas au coffre — il reste volable\"},\"historique\""));
                mc.setScreenAndShow(new GuichetScreen().onglet(4));
                NotifHud.message("refus", null, "Ce parchemin ne va pas au coffre", "il reste volable", null);
            }
            case 220, 224 -> survolerSacocheGuichet(mc);
            case 228 -> shot(mc, "monnaie_10_coffre_paliers");
            // 11 — fouille : 3 s immobile (filet au-dessus de la barre)
            case 230 -> {
                mc.setScreenAndShow(null);
                MonnaieClient.recevoir("{\"t\":\"fouille_prog\",\"nom\":\"Kenta\",\"s\":3}");
            }
            case 250 -> shot(mc, "monnaie_11_fouille_filet");
            // 12 — la sacoche du corps en mode fouille ; 13 — « Prendre dans la bourse »
            case 252 -> MonnaieClient.recevoir(FOUILLE.replace("$UUID", mc.player.getUUID().toString()));
            case 262 -> shot(mc, "monnaie_12_fouille_sacoche");
            case 264 -> {
                if (mc.gui.screen() instanceof fr.reborn.hud.menu.inventory.InventoryScreen inv) {
                    var v = FouilleVue.lire(com.google.gson.JsonParser.parseString(FOUILLE.replace("$UUID", "")).getAsJsonObject());
                    CombienScreen cs = new CombienScreen(inv, "vol", "Prendre dans la bourse", "Kenta porte 7 800 ryo", v.reste(),
                        "Prendre", "2 340 au plus dans cette scène", n -> { });
                    try {
                        var fx = inv.getClass().getDeclaredField("trayX");
                        var fy = inv.getClass().getDeclaredField("trayY");
                        fx.setAccessible(true);
                        fy.setAccessible(true);
                        cs.ancre(fx.getInt(inv) + 246 + 26, fy.getInt(inv) + 18);
                    } catch (ReflectiveOperationException ignored) { }
                    mc.setScreenAndShow(cs.avecValeur(2000));
                }
            }
            case 274 -> shot(mc, "monnaie_13_fouille_prendre");
            // 14 — coffre posé : « Combien veux-tu ranger ? » par-dessus un coffre
            case 276 -> {
                mc.setScreenAndShow(null);
                var menu = net.minecraft.world.inventory.ChestMenu.threeRows(0, mc.player.getInventory());
                var cs = new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu, mc.player.getInventory(),
                    net.minecraft.network.chat.Component.literal("Coffre"));
                mc.setScreenAndShow(cs);
            }
            case 280 -> mc.setScreenAndShow(new CombienScreen(mc.gui.screen(), "ranger", "Ranger dans le coffre",
                "Combien veux-tu ranger ?", 1240, "Ranger", null, n -> { }).avecValeur(1000));
            case 290 -> shot(mc, "monnaie_14_coffre_ranger");
            // 15 — Poste de garde : section Économie dépliée ; 16 — formulaire « Donner »
            case 292 -> { mc.setScreenAndShow(null); fr.reborn.hud.staff.GardeEconomieDebug.ouvrir(mc, false); }
            case 306 -> shot(mc, "monnaie_15_staff_economie");
            case 308 -> fr.reborn.hud.staff.GardeEconomieDebug.ouvrir(mc, true);
            case 318 -> shot(mc, "monnaie_16_staff_donner");
            // 17 — Échap : « Sur toi · 1 240 ryo » (ÉCHOPPE) et « 320 RBCoins » (RÉCOMPENSES)
            case 320 -> { mc.setScreenAndShow(null); mc.setScreenAndShow(new net.minecraft.client.gui.screens.PauseScreen(true)); }
            case 340 -> shot(mc, "monnaie_17_echap");
            case 342 -> {
                mc.setScreenAndShow(null);
                remettreMonde(mc);
            }
            case 380 -> c.sendCommand("gamemode " + mode);
            case 384 -> mc.stop();
            default -> { }
        }
    }

    /** Pose la souris au centre d'une case de la sacoche ouverte (lu par réflexion : banc seulement). */
    private static void survolerCase(Minecraft mc, String ref) {
        if (!(mc.gui.screen() instanceof fr.reborn.hud.menu.inventory.InventoryScreen inv)) return;
        try {
            var fx = inv.getClass().getDeclaredField("hotbarX0");
            var fy = inv.getClass().getDeclaredField("hotbarY");
            fx.setAccessible(true);
            fy.setAccessible(true);
            int i = Integer.parseInt(ref.substring(1));
            curseur(mc, fx.getInt(inv) + i * 25 + 11, fy.getInt(inv) + 11);
        } catch (ReflectiveOperationException | NumberFormatException ignored) { }
    }

    private static void survolerSacocheGuichet(Minecraft mc) {
        int W = 310;
        int x0 = (mc.getWindow().getGuiScaledWidth() - W) / 2;
        int h = 153 + 12 + 29 + 22 + 25 + 52;
        int y0 = Math.max(6, (mc.getWindow().getGuiScaledHeight() - h) / 2 - 8);
        float cx = x0 + W / 2f;
        float sy = y0 + 153 + 12 + 29 + 2;
        float gx = cx - (3 * 25 - 3) / 2f;
        curseur(mc, gx + 25 + 11, sy + 11 + 11);
    }

    private static void curseur(Minecraft mc, float guiX, float guiY) {
        double k = mc.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), guiX * k, guiY * k);
        try {   // fenêtre sans le focus : la position GLFW n'arrive pas, on la pose dans le MouseHandler
            var fx = mc.mouseHandler.getClass().getDeclaredField("xpos");
            var fy = mc.mouseHandler.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, guiX * k);
            fy.setDouble(mc.mouseHandler, guiY * k);
        } catch (ReflectiveOperationException ignored) { }
    }

    /** Active (ou retire) le pack de dev qui contient les visuels provisoires de la bourse. */
    private static void packDev(Minecraft mc, boolean on) {
        try {
            var repo = mc.getResourcePackRepository();
            repo.reload();
            if (!repo.isAvailable(PACK)) return;
            Collection<String> sel = new ArrayList<>(repo.getSelectedIds());
            if (on && !sel.contains(PACK)) { sel.add(PACK); packAjoute = true; }
            else if (!on && packAjoute) { sel.remove(PACK); packAjoute = false; }
            else return;
            repo.setSelected(sel);
            mc.reloadResourcePacks();
        } catch (RuntimeException ignored) {
            // pas de pack de dev : la bourse s'affiche sans son modèle
        }
    }

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

    private static void remettreMonde(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand("kill @e[type=minecraft:item,distance=..40]");
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
        MonnaieClient.clear();
        MonnaieClient.debugEnvoi = null;
        VitalsFeed.clear();
        fr.reborn.hud.notif.NotifClient.debugActif = false;
        RebornSession.debugForceRp = false;
        packDev(mc, false);
        if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
        if (cameraAvant != null) fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(cameraAvant);
    }

    private static fr.reborn.hud.camera.RebornCamera.Mode cameraAvant;

    private static int echelleAvant = -1;

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
