package fr.reborn.hud.interaction;

import fr.reborn.hud.cosmetic.RemoteCosmetics;
import fr.reborn.hud.ko.CorpsClient;
import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.monnaie.MonnaieClient;
import fr.reborn.hud.nameplate.Nameplates;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Banc d'essai du menu R (GAMEPLAN §14, 24/10), <b>inactif en production</b> ({@code REBORN_INTERACTION_DEBUG=1}, monde
 * solo de dev, jamais de serveur) : menu d'une personne consciente (porter et fouiller avec son accord, volume de sa
 * voix, sous-menu Staff réservé au staff), demandes reçues (« veut te porter », « veut te fouiller »), porté de plein gré
 * (« Espace · descendre »), fouille consentie en lecture seule, plaque d'un inconnu masqué →
 * {@code run/screenshots/interaction_*.png}, puis ferme le jeu.
 *
 * <p>Le monde est remis en état : mannequin retiré, mode de jeu, heure, météo et pack de dev rendus à la fin.
 */
public final class InteractionDebug {

    private static int t = -1;
    private static long heure = -1;
    private static String mode = "survival";
    private static boolean pluie, packAjoute;
    private static fr.reborn.hud.camera.RebornCamera.Mode camAvant;
    private static final String PACK = "file/reborn_boutique_dev";
    private static final String NOM = "Ren_Hyuga";

    private InteractionDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_INTERACTION_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(InteractionDebug::tick);
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
        if (t > 20 && t < 400) vol(mc, true);
        switch (t) {
            case 20 -> {
                if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
                var srv = mc.getSingleplayerServer();
                if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
                RebornSession.debugForceRp = true;
                c.sendCommand("gamemode creative");
                c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                c.sendCommand("tp @s ~ ~ ~ ~ 8");
                for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
                packDev(mc, true);
            }
            case 60 -> {
                // un « autre joueur » à 3,4 blocs, décalé à droite (la caméra épaule cache ce qui est droit devant)
                double yaw = Math.toRadians(mc.player.getYRot());
                double dx = -Math.sin(yaw), dz = Math.cos(yaw);
                double x = mc.player.getX() + dx * 3.4 - dz * 1.5, z = mc.player.getZ() + dz * 3.4 + dx * 1.5;
                float face = (float) Math.toDegrees(Math.atan2(-(mc.player.getX() - x), mc.player.getZ() - z));
                c.sendCommand(String.format(java.util.Locale.ROOT,
                    "summon minecraft:mannequin %.2f %.2f %.2f {NoGravity:1b,Rotation:[%.1ff,0f]}", x, mc.player.getY(), z, face));
            }
            case 100 -> {
                VoiceState.DEBUG_VOLUMES.put(uuid(), 0.75);
                im.toggle();
            }
            case 102 -> im.debugOpen(NOM, InteractionMenus.forPlayer(NOM), indexDe("Porter (avec"));
            case 112 -> shot(mc, "interaction_01_menu_porter_fouiller");
            case 114 -> im.debugOpen(NOM, InteractionMenus.forPlayer(NOM), indexDe("Volume"));
            case 124 -> shot(mc, "interaction_02_menu_volume");
            case 126 -> {
                TablistData.DEBUG_STAFF = true;
                im.debugOpen(NOM, InteractionMenus.forPlayer(NOM), indexDe("Staff"));
            }
            case 136 -> shot(mc, "interaction_03_menu_staff");
            case 138 -> {
                im.debugClose();
                TablistData.DEBUG_STAFF = null;
                CorpsClient.debugReceive("{\"t\":\"demande\",\"de\":\"" + uuid() + "\",\"nom\":\"Ren Hyūga\",\"quoi\":\"porter\",\"s\":15}");
            }
            case 160 -> shot(mc, "interaction_04_demande_porter");
            case 162 -> CorpsClient.debugReceive("{\"t\":\"demande\",\"de\":\"" + uuid() + "\",\"nom\":\"Ren Hyūga\",\"quoi\":\"fouiller\",\"s\":15}");
            case 184 -> shot(mc, "interaction_05_demande_fouiller");
            case 186 -> {
                CorpsClient.debugEffacerDemande();
                CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"dl\":[],\"me\":{\"c\":1,\"v\":1}}");
            }
            case 196 -> shot(mc, "interaction_06_porte_descendre");
            case 198 -> {
                CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[],\"dl\":[],\"me\":{}}");
                MonnaieClient.debugEnvoi = m -> { };
                Entity m = mannequin(mc);   // la personne fouillée s'affiche à gauche de l'écran
                MonnaieClient.recevoir(FOUILLE.replace("$UUID", (m != null ? m.getUUID() : uuid()).toString()));
            }
            case 214 -> shot(mc, "interaction_07_fouille_lecture_seule");
            case 216 -> {
                mc.setScreenAndShow(null);
                MonnaieClient.debugEnvoi = null;
                Nameplates.debugAvatars = true;
                camAvant = fr.reborn.hud.camera.RebornCamera.INSTANCE.mode();
                fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(fr.reborn.hud.camera.RebornCamera.Mode.FIRST);
                regarder(mc);
            }
            case 228 -> shot(mc, "interaction_08_plaque_connu");
            case 230 -> {
                diffuser(mc, "{\"MASQUE\":{\"mat\":\"minecraft:paper\",\"model\":\"reborn:masque_hannya\"}}");
                Nameplates.debugInconnu = true;
                regarder(mc);
            }
            case 246 -> shot(mc, "interaction_09_masque_inconnu");
            case 250 -> {
                Nameplates.debugAvatars = Nameplates.debugInconnu = false;
                if (camAvant != null) fr.reborn.hud.camera.RebornCamera.INSTANCE.setMode(camAvant);
                RemoteCosmetics.clearAll();
                VoiceState.DEBUG_VOLUMES.clear();
                c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
                if (heure >= 0) c.sendCommand("time set " + heure);
                c.sendCommand(pluie ? "weather rain" : "weather clear");
                RebornSession.debugForceRp = false;
                packDev(mc, false);
            }
            case 400 -> { vol(mc, false); c.sendCommand("gamemode " + mode); }
            case 410 -> mc.stop();
            default -> { }
        }
    }

    private static final String FOUILLE = """
        {"t":"fouille","cible":"Ren Hyūga","etat":"debout","uuid":"$UUID","bourse":1240,"reste":0,"plafond":true,"pourcent":0,
         "lecture":true,"extraSlots":9,
         "hotbar":[{"mat":"minecraft:iron_sword","count":1,"name":"Tantō","weight":1.2,"rarity":"PEU_COMMUN","arme":true},null,null,null,null,null,null,null,
                   {"mat":"minecraft:paper","model":"reborn:bourse_lourde","count":1,"name":"Bourse","weight":0,"rarity":"COMMUN","ryo":1240}],
         "bag":[{"mat":"minecraft:arrow","count":12,"name":"Shuriken","weight":0.1,"rarity":"COMMUN"},
                {"mat":"minecraft:bread","count":2,"name":"Ration sèche","weight":0.3,"rarity":"COMMUN"},
                {"mat":"minecraft:paper","count":1,"name":"Laissez-passer","weight":0.1,"rarity":"PEU_COMMUN"},null,null,null,null,null,null]}
        """;

    private static UUID uuid() {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + NOM).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static int indexDe(String debut) {
        List<InteractionItem> it = InteractionMenus.forPlayer(NOM);
        for (int i = 0; i < it.size(); i++) if (it.get(i).label().startsWith(debut)) return i;
        return -1;
    }

    private static Entity mannequin(Minecraft mc) {
        Entity best = null;
        double d = 64;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!"minecraft:mannequin".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) continue;
            double x = e.distanceToSqr(mc.player);
            if (x < d) { d = x; best = e; }
        }
        return best;
    }

    /** Le viseur sur la poitrine du mannequin (la plaque n'apparaît que si on le regarde vraiment). */
    private static void regarder(Minecraft mc) {
        Entity m = mannequin(mc);
        if (m == null) return;
        double dx = m.getX() - mc.player.getX(), dz = m.getZ() - mc.player.getZ();
        double dy = m.getY() + 1.3 - mc.player.getEyeY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s ~ ~ ~ %.1f %.1f", yaw, pitch));
    }

    /** Diffusion reborn:cosmetics simulée vers le mannequin. */
    private static void diffuser(Minecraft mc, String json) {
        Entity m = mannequin(mc);
        if (m != null) RemoteCosmetics.apply(m.getUUID() + "\n" + json);
    }

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
            // pas de pack de dev : le masque s'affiche sans son modèle
        }
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
