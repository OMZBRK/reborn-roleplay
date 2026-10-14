package fr.reborn.hud.audit;

import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.InventoryScreen;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.notif.NotifClient;
import fr.reborn.hud.sol.RamasserClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

/**
 * Banc d'essai du lot « inventaire et ramassage » (13/10), <b>inactif en production</b> : {@code REBORN_INV_DEBUG=1}
 * (monde solo de dev, jamais de serveur). Pose des objets au sol de chaque rareté, rejoue les messages du canal
 * {@code reborn:notif} (état de charge, gains, refus, palier) et ouvre la sacoche avec l'étui garni ; capture chaque
 * visuel dans {@code run/screenshots/inv_*.png}, puis ferme le jeu. Comparer à Maquettes/gameplan/02_inventaire.
 */
public final class InventaireDebug {

    private static int t = -1;

    private InventaireDebug() {}

    public static void init() {
        if (System.getenv("REBORN_INV_DEBUG") == null) return;
        ClientTickEvents.END_CLIENT_TICK.register(InventaireDebug::tick);
    }

    private static String objet(String id, int n, String nom, String rarete, String modele) {
        StringBuilder c = new StringBuilder();
        c.append("\"minecraft:custom_data\":{PublicBukkitValues:{\"reborn:rarete\":\"").append(rarete).append("\"}}");
        if (nom != null) c.append(",\"minecraft:custom_name\":\"").append(nom).append('"');
        if (modele != null) c.append(",\"minecraft:item_model\":\"").append(modele).append('"');
        return "{Item:{id:\"" + id + "\",count:" + n + ",components:{" + c + "}},PickupDelay:32767s,Age:-32768s}";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if ("lueur".equals(System.getenv("REBORN_INV_DEBUG"))) {
            lueur(mc);
            return;
        }
        // REBORN_INV_DEBUG=arme : seulement l'arme à la ceinture (mannequins), après la mise en place.
        if (t == 26 && "arme".equals(System.getenv("REBORN_INV_DEBUG"))) t = 381;
        var c = mc.player.connection;
        switch (t) {
            case 20 -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                NotifClient.debugActif = true;
                c.sendCommand("gamerule sendCommandFeedback false");
                c.sendCommand("time set 1000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("kill @e[type=item]");
                c.sendCommand("kill @e[type=minecraft:mannequin]");
                c.sendCommand("clear @s");
                c.sendCommand("tp @s ~ ~ ~ -90 22");
                c.sendCommand("give @s minecraft:iron_sword[custom_name=\"Kunai\"] 1");
                c.sendCommand("give @s minecraft:iron_nugget[custom_name=\"Shuriken\"] 12");
                c.sendCommand("give @s minecraft:bread[custom_name=\"Pilule\"] 3");
                c.sendCommand("give @s minecraft:honey_bottle[custom_name=\"Onguent\"] 1");
                c.sendCommand("give @s minecraft:paper[custom_name=\"Parchemin\"] 4");
            }
            case 30 -> {
                // objets au sol, devant le joueur (repère local ^gauche ^haut ^avant)
                c.sendCommand("summon item ^0.2 ^ ^2.2 " + objet("minecraft:iron_nugget", 3, "Kunai", "COMMUN", null));
                c.sendCommand("summon item ^1.6 ^ ^3.2 " + objet("minecraft:bread", 4, "Ration", "PEU_COMMUN", null));
                c.sendCommand("summon item ^-1.8 ^ ^2.6 " + objet("minecraft:paper", 1, "Parchemin — Suiton", "RARE", null));
                c.sendCommand("summon item ^2.6 ^ ^1.6 " + objet("minecraft:skeleton_skull", 1, "Masque ANBU", "EPIQUE", null));
                c.sendCommand("summon item ^-0.6 ^ ^4.4 " + objet("minecraft:paper", 1, "Parchemin — Katon", "LEGENDAIRE", null));
                c.sendCommand("summon item ^-2.6 ^ ^4.0 " + objet("minecraft:chest", 1, "Coffre de mission", "PEU_COMMUN", null));
            }
            case 40 -> NotifClient.recevoir("{\"t\":\"poids\",\"cur\":11.6,\"max\":20,\"palier\":0,\"libres\":4}");
            case 70, 98, 118, 198, 214, 318, 348, 378, 450, 476, 502 -> {
                mc.gui.hud.getChat().clearMessages(false);
                mc.gui.toastManager().clear();
            }
            case 60 -> RamasserClient.debugCible = proche(mc, 2.6, 1.6);
            case 80 -> shot(mc, "inv_04_ramassage");
            case 84 -> {
                NotifClient.recevoir("{\"t\":\"poids\",\"cur\":25.9,\"max\":20,\"palier\":2,\"libres\":2}");
                RamasserClient.debugCible = auCentre(mc);
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"refus\",\"kj\":\"重\",\"gras\":\"Trop lourd\",\"texte\":\"2,4 kg de trop\"}");
            }
            case 100 -> shot(mc, "inv_04b_trop_lourd");
            case 104 -> {
                RamasserClient.debugCible = null;
                NotifClient.recevoir("{\"t\":\"poids\",\"cur\":45.2,\"max\":38,\"palier\":2,\"libres\":2}");
                NotifClient.recevoir("{\"t\":\"gain\",\"q\":1,\"nom\":\"Coffre de mission\",\"rar\":\"PEU_COMMUN\",\"mat\":\"minecraft:chest\"}");
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"palier\",\"kj\":\"重\",\"gras\":\"Tu es surchargé\",\"texte\":\"tu ralentis\"}");
            }
            case 120 -> shot(mc, "inv_02_surcharge");
            case 190 -> {   // pile de notifications (les précédentes se sont éteintes)
                for (int i = 0; i < 3; i++) {
                    NotifClient.recevoir("{\"t\":\"gain\",\"q\":1,\"nom\":\"Kunai\",\"rar\":\"COMMUN\",\"mat\":\"minecraft:iron_nugget\"}");
                }
                NotifClient.recevoir("{\"t\":\"gain\",\"q\":1,\"nom\":\"Parchemin — Katon : Gōkakyū\",\"rar\":\"EPIQUE\","
                    + "\"mat\":\"minecraft:paper\"}");
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"refus\",\"kj\":\"袋\",\"gras\":\"Plus assez de place\",\"texte\":\"libère une case\"}");
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"refus\",\"kj\":\"重\",\"gras\":\"Trop lourd\",\"texte\":\"2,4 kg de trop\"}");
            }
            case 200 -> shot(mc, "inv_03_notifications");
            case 206 -> {
                NotifClient.recevoir("{\"t\":\"gain\",\"q\":2,\"nom\":\"Kunai\",\"rar\":\"COMMUN\",\"mat\":\"minecraft:iron_nugget\",\"suite\":\"1 reste au sol\"}");
                NotifClient.recevoir("{\"t\":\"perte\",\"q\":1,\"nom\":\"Ration\",\"rar\":\"PEU_COMMUN\",\"mat\":\"minecraft:bread\",\"suite\":\"lâchée\"}");
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"info\",\"kj\":\"袋\",\"gras\":\"Sac ninja équipé\",\"texte\":\"+27 cases\"}");
                NotifClient.recevoir("{\"t\":\"msg\",\"ton\":\"etat\",\"kj\":\"止\",\"gras\":\"Impossible en KO\"}");
            }
            case 216 -> shot(mc, "inv_03b_notifications_suite");
            case 290 -> {
                NotifClient.recevoir("{\"t\":\"poids\",\"cur\":8.4,\"max\":20,\"palier\":0,\"libres\":4}");
                InventoryData.setLocal(sacoche(20.0, 8.4, "SACOCHE", 9));
                InventoryScreen.debugOnglet(true);
                mc.setScreenAndShow(new InventoryScreen());
            }
            case 320 -> shot(mc, "inv_01_sacoche_equipement");
            case 322 -> {
                mc.setScreenAndShow(null);
                NotifClient.recevoir("{\"t\":\"poids\",\"cur\":45.2,\"max\":38,\"palier\":2,\"libres\":2}");
                InventoryData.setLocal(sacoche(38.0, 45.2, "SAC", 27));
                mc.setScreenAndShow(new InventoryScreen());
            }
            case 350 -> shot(mc, "inv_01b_sacoche_119");
            case 352 -> {
                mc.setScreenAndShow(null);
                InventoryScreen.DEBUG_VERROU = "KO";
                InventoryScreen.debugOnglet(false);
                mc.setScreenAndShow(new InventoryScreen());
            }
            case 380 -> shot(mc, "inv_01c_lecture_seule");
            case 382 -> {
                mc.setScreenAndShow(null);
                InventoryScreen.DEBUG_VERROU = null;
                NotifClient.recevoir("{\"t\":\"poids\",\"cur\":8.4,\"max\":20,\"palier\":0,\"libres\":4}");
                c.sendCommand("kill @e[type=item]");
                c.sendCommand("kill @e[type=minecraft:mannequin]");
                c.sendCommand("tp @s ~ ~ ~ -90 4");
                InventoryData.clear();   // le joueur local ne porte rien : il ne cache pas les mannequins
                for (int i = 0; i < CANDIDATS.length; i++) {
                    c.sendCommand("summon minecraft:mannequin ^" + (-0.7 - 1.1 * i) + " ^ ^2.6 {Rotation:[180f,0f],NoGravity:1b}");
                }
            }
            case 400 -> mannequins(mc);
            case 460 -> shot(mc, "inv_05_arme_cote_gauche");
            case 462 -> c.sendCommand("execute as @e[type=minecraft:mannequin] at @s run tp @s ~ ~ ~ 90 0");
            case 486 -> shot(mc, "inv_05b_arme_face");
            case 488 -> c.sendCommand("execute as @e[type=minecraft:mannequin] at @s run tp @s ~ ~ ~ -90 0");
            case 512 -> shot(mc, "inv_05c_arme_dos");
            case 516 -> mc.stop();
            default -> { }
        }
    }

    /**
     * {@code REBORN_INV_DEBUG=lueur} (15/10) : lueur de rareté (halo pixel art au sol, pixels qui scintillent dès Rare) sur les 5 raretés,
     * un Épique caché derrière un muret (ne doit pas luire), en <b>survie</b> puis en <b>créatif</b>, sans puis avec un
     * objet visé ; en créatif la visée est réelle (vérifie que F est actif en créatif) → {@code run/screenshots/lueur_*.png}.
     */
    /** Tangage retenu pour la visée réelle en créatif (balayage de 10° à 34°, premier objet visé). */
    private static Float pitchVisee;

    private static void lueur(Minecraft mc) {
        var c = mc.player.connection;
        if (t >= 20 && t < 232) {   // regard imposé (caméra du mod comprise : le tp seul ne la tourne pas)
            float pitch = 30f;
            if (t >= 172) {
                if (pitchVisee == null && RamasserClient.cible() != null) pitchVisee = mc.player.getXRot();
                pitch = pitchVisee != null ? pitchVisee : Math.min(34f, 10f + (t - 172));
            }
            mc.player.setYRot(-90f);
            mc.player.setXRot(pitch);
            mc.player.yRotO = -90f;
            mc.player.xRotO = pitch;
            if (fr.reborn.hud.camera.RebornCamera.INSTANCE.isEnabled()) fr.reborn.hud.camera.RebornCamera.INSTANCE.initOrientation(-90f, pitch);
        }
        switch (t) {
            case 20 -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                NotifClient.debugActif = true;
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("kill @e[type=item]");
                c.sendCommand("tp @s ~ ~ ~ -90 30");
            }
            case 26 -> {
                // muret provisoire (retiré à la fin) : l'objet caché derrière ne doit pas luire. Coordonnées relatives
                // sans rotation (~) : le banc suppose un sol plat et dégagé devant le joueur (6 blocs).
                c.sendCommand("fill ~6 ~ ~-1 ~6 ~1 ~1 minecraft:stone_bricks keep");
            }
            case 32 -> {
                // de gauche à droite à l'écran (joueur tourné vers l'est) : Commun → Légendaire ; le Rare dans l'axe de la
                // caméra d'épaule (décalée d'un demi-bloc), pour la visée réelle
                c.sendCommand("summon item ~3.2 ~0.05 ~-1.9 " + objet("minecraft:iron_nugget", 3, "Kunai", "COMMUN", null));
                c.sendCommand("summon item ~3.2 ~0.05 ~-0.7 " + objet("minecraft:bread", 4, "Ration", "PEU_COMMUN", null));
                c.sendCommand("summon item ~3.2 ~0.05 ~0.5 " + objet("minecraft:paper", 1, "Parchemin — Suiton", "RARE", null));
                c.sendCommand("summon item ~3.2 ~0.05 ~1.7 " + objet("minecraft:skeleton_skull", 1, "Masque ANBU", "EPIQUE", null));
                c.sendCommand("summon item ~3.2 ~0.05 ~2.9 " + objet("minecraft:diamond_sword", 1, "Kubikiribōchō", "LEGENDAIRE", null));
                c.sendCommand("summon item ~7.5 ~0.05 ~0 " + objet("minecraft:emerald", 1, "Caché", "EPIQUE", null));
            }
            case 40 -> NotifClient.recevoir("{\"t\":\"poids\",\"cur\":8.4,\"max\":20,\"palier\":0,\"libres\":4}");
            case 60, 108, 126, 166, 196, 226 -> {
                mc.gui.hud.getChat().clearMessages(false);
                mc.gui.toastManager().clear();
            }
            case 90 -> shot(mc, "lueur_01_survie");
            case 96 -> RamasserClient.debugCible = proche(mc, -0.5, 3.2);
            case 112 -> shot(mc, "lueur_02_survie_vise_rare");
            case 114 -> RamasserClient.debugCible = proche(mc, 1.9, 3.2);
            case 130 -> shot(mc, "lueur_03_survie_vise_commun");
            case 132 -> {
                RamasserClient.debugCible = null;
                c.sendCommand("gamemode creative");
            }
            case 170 -> shot(mc, "lueur_04_creatif");
            case 200 -> {
                ItemEntity v = RamasserClient.cible();
                org.slf4j.LoggerFactory.getLogger("reborn-inv-debug").info("[inv-debug] creatif : visee reelle = {} (tangage {})",
                    v == null ? "aucune" : v.getItem().getHoverName().getString(), pitchVisee);
                shot(mc, "lueur_05_creatif_vise");
            }
            case 230 -> shot(mc, "lueur_06_creatif_scintillement");
            case 232 -> {
                c.sendCommand("gamemode survival");
                c.sendCommand("kill @e[type=item]");
                c.sendCommand("fill ~6 ~ ~-1 ~6 ~1 ~1 minecraft:air replace minecraft:stone_bricks");
            }
            case 240 -> mc.stop();
            default -> { }
        }
    }

    /**
     * Placements candidats de l'arme à l'étui (ancrage bassin), un par mannequin, de gauche à droite à l'écran ; le
     * premier est le défaut du code ({@code CosmeticFeatureRenderer.defautPour(ARME)}).
     */
    static float[][] CANDIDATS = {
        {0.30f, 0f, 0.10f, 0f, 90f, -12f, 0.85f},
        {0.30f, 0f, 0.10f, 0f, 90f, -22f, 0.85f},
        {0.30f, -0.04f, 0.14f, 0f, 90f, -12f, 0.75f},
        {0.30f, 0f, 0.10f, 0f, 90f, 0f, 0.85f},
    };
    private static final String[] MODELES = {"minecraft:iron_sword", "minecraft:golden_sword", "minecraft:diamond_sword",
        "minecraft:netherite_sword"};

    /** Pose une arme à l'étui sur chaque mannequin (diffusion simulée reborn:cosmetics), un placement par mannequin. */
    private static void mannequins(Minecraft mc) {
        java.util.List<net.minecraft.world.entity.decoration.Mannequin> ms = new java.util.ArrayList<>(
            mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Mannequin.class, mc.player.getBoundingBox().inflate(10)));
        Vec3 left = Vec3.directionFromRotation(0, mc.player.getYRot() - 90);
        ms.sort((a, b) -> Double.compare(b.position().dot(left), a.position().dot(left)));
        for (int i = 0; i < ms.size() && i < CANDIDATS.length; i++) {
            float[] k = CANDIDATS[i];
            String m = MODELES[i];
            fr.reborn.hud.cosmetic.CosmeticTransform t = new fr.reborn.hud.cosmetic.CosmeticTransform(
                fr.reborn.hud.cosmetic.CosmeticTransform.Anchor.PELVIS, k[0], k[1], k[2], k[3], k[4], k[5], k[6]);
            fr.reborn.hud.cosmetic.RemoteCosmetics.apply(ms.get(i).getUUID() + "\n{\"ARME\":{\"mat\":\"" + m
                + "\",\"model\":\"" + m + "\",\"t\":\"" + t.serialize() + "\"}}");
            org.slf4j.LoggerFactory.getLogger("reborn-inv-debug").info("[inv-debug] mannequin {} {} -> {} {} remote={}",
                i, ms.get(i).getUUID(), m, t.serialize(), fr.reborn.hud.cosmetic.RemoteCosmetics.get(ms.get(i).getUUID()));
        }
        org.slf4j.LoggerFactory.getLogger("reborn-inv-debug").info("[inv-debug] {} mannequins", ms.size());
    }

    /** L'objet au sol visible le plus près du centre de l'écran (la vue de la caméra épaule varie d'un lancement à l'autre). */
    private static ItemEntity auCentre(Minecraft mc) {
        double cx = mc.getWindow().getWidth() / 2.0, cy = mc.getWindow().getHeight() / 2.0;
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(8))) {
            double[] p = RamasserClient.projeter(mc, e.position().add(0, 0.6, 0));
            if (p == null) continue;
            double d = Math.hypot(p[0] - cx, p[1] - cy);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /** L'objet au sol le plus proche d'un point exprimé dans le repère local du joueur (gauche, avant). */
    private static ItemEntity proche(Minecraft mc, double gauche, double avant) {
        Vec3 fwd = Vec3.directionFromRotation(0, mc.player.getYRot());
        Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
        Vec3 p = mc.player.position().add(fwd.scale(avant)).add(left.scale(gauche));
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(8))) {
            double d = e.position().distanceToSqr(p);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static InventoryData.Snapshot sacoche(double max, double cur, String tier, int cases) {
        SlotItem[] bag = new SlotItem[cases];
        bag[0] = meta("minecraft:iron_sword", 1, "Tantō", 1.5, "PEU_COMMUN", true);
        bag[1] = meta("minecraft:arrow", 20, "Shuriken", 0.05, "COMMUN", false);
        bag[2] = meta("minecraft:bread", 4, "Ration", 0.4, "COMMUN", false);
        bag[3] = new SlotItem("minecraft:paper", null, 1, "Parchemin — Suiton", 0.3, null, "RARE", null, null);
        bag[4] = meta("minecraft:leather", 1, "Sac ninja", 2.0, "PEU_COMMUN", false);
        if (cases > 9) {
            bag[9] = new SlotItem("minecraft:paper", null, 1, "Parchemin — Katon", 0.3, null, "LEGENDAIRE", null, null);
            bag[10] = meta("minecraft:iron_ingot", 12, "Lingot", 1.5, "COMMUN", false);
            bag[11] = meta("minecraft:chest", 1, "Coffre de mission", 6.0, "PEU_COMMUN", false);
        }
        Map<CosmeticSlot, SlotItem> eq = new EnumMap<>(CosmeticSlot.class);
        eq.put(CosmeticSlot.BANDEAU, meta("minecraft:iron_ingot", 1, "Bandeau de Konoha", 0.1, "PEU_COMMUN", false));
        eq.put(CosmeticSlot.MASQUE, meta("minecraft:skeleton_skull", 1, "Masque ANBU", 0.4, "RARE", false));
        eq.put(CosmeticSlot.ARME, meta("minecraft:iron_sword", 1, "Ninjato", 2.0, "RARE", true));
        SlotItem bagItem = meta("minecraft:leather", 1, "Sacoche", 0.6, "COMMUN", false);
        InventoryData.Snapshot s = new InventoryData.Snapshot(tier, 9, cases, max, cur, bagItem, bag, eq);
        return s.avec(max, cur, null, cur / max > 1.15 ? 2 : cur / max > 1 ? 1 : 0);
    }

    private static SlotItem meta(String mat, int n, String nom, double poids, String rar, boolean arme) {
        SlotItem s = new SlotItem(mat, null, n, nom, poids, null, rar, null, null);
        s.arme = arme;
        return s;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
