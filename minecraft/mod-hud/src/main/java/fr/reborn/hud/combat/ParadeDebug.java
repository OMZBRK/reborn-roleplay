package fr.reborn.hud.combat;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.keybind.HudKeybinds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Banc de la garde et de la parade (18/10), <b>inactif en production</b> : ne fait rien sauf si
 * {@code REBORN_PARADE_DEBUG=1} (client de dev, solo, sans plugin). La touche Parade est tenue par le banc (vrai chemin
 * {@link CombatInput#tick}) ; deux mannequins jouent le rôle des « autres joueurs » (l'un de face, l'autre de profil) et
 * reçoivent les animations par le chemin de réception réseau ({@link CombatAnimations#playByEntityId}). Captures et films
 * (monde à 5 ticks/s, une image par tick = film à 20 i/s en temps de jeu) dans {@code run/screenshots/parade_*.png} / {@code pfilm_<cas>_<nnn>.png} ; l'état du
 * layer combat est journalisé ({@code [parade-banc]}). Garde tenue (épaule, face, profil), relâche, parade réussie,
 * reprise de la marche, puis le jeu se ferme.
 */
public final class ParadeDebug {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/parade-banc");
    private static int ticks = -1;
    private static final List<Integer> autres = new ArrayList<>();
    private static String film, filmPrec;
    private static int image;
    private static float lacet;
    private static int X, Y, Z;
    /** {@code =video} : temps réel, pas de film image par image (la fenêtre est filmée de l'extérieur, ffmpeg). */
    private static boolean video;

    private ParadeDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_PARADE_DEBUG");
        if (!"1".equals(v) && !"video".equals(v)) return;
        video = "video".equals(v);
        ClientTickEvents.END_CLIENT_TICK.register(ParadeDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        if (ticks > 35) mc.gui.hud.getChat().clearMessages(false);
        if (film != null && !film.equals(filmPrec)) {
            LOG.info("[parade-banc] marque {} {}", film, System.currentTimeMillis());
            if (video) filmPrec = film;
        }
        if (film != null && !video) filmer(mc);
        switch (ticks) {
            case 20 -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                c.sendCommand("gamerule send_command_feedback false");
                c.sendCommand("gamerule sendCommandFeedback false");
                c.sendCommand("time set 6000");
                c.sendCommand("effect give @s night_vision 60 0 true");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode creative");
                c.sendCommand("kill @e[tag=parade_test]");
                // aire dégagée dans le ciel au-dessus du point d'apparition (rendue à la fin)
                X = (int) Math.floor(mc.player.getX());
                Y = (int) Math.floor(mc.player.getY()) + 90;
                Z = (int) Math.floor(mc.player.getZ());
                c.sendCommand("fill " + (X - 7) + " " + (Y - 1) + " " + (Z - 7) + " " + (X + 7) + " " + (Y - 1) + " " + (Z + 9)
                    + " minecraft:polished_andesite");
                c.sendCommand("fill " + (X - 7) + " " + Y + " " + (Z - 7) + " " + (X + 7) + " " + (Y + 5) + " " + (Z + 9)
                    + " minecraft:air");
            }
            case 24 -> {
                c.sendCommand("tp @s " + X + ".5 " + Y + " " + Z + ".5 0 0");
                mc.player.getInventory().setSelectedSlot(8);   // mains nues (garde à mains nues)
            }
            case 28 -> {
                lacet = mc.player.getYRot();
                fr.reborn.hud.camera.RebornCamera.INSTANCE.initOrientation(lacet, 0f);
                // « autres joueurs » : de face (gauche) et de profil (droite)
                c.sendCommand("execute rotated 0 0 run summon mannequin ^2.2 ^ ^4.5 {Rotation:[180f,0f],Tags:[\"parade_test\"]}");
                c.sendCommand("execute rotated 0 0 run summon mannequin ^-2.2 ^ ^4.5 {Rotation:[90f,0f],Tags:[\"parade_test\"]}");
            }
            case 40 -> {
                autres.clear();
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof Mannequin && e.distanceToSqr(mc.player) < 64) autres.add(e.getId());
                }
                diag(mc, "repos");
            }
            // monde au ralenti (5 ticks/s) : plusieurs images rendues par tick, films et captures à jour
            case 44 -> { if (!video) c.sendCommand("tick rate 5"); }
            case 50 -> shot(mc, "parade_00_repos_epaule");
            // ---- garde tenue (touche Parade maintenue) ----
            case 54 -> {
                film = "garde_epaule";
                HudKeybinds.PARRY.setDown(true);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_ON);
            }
            case 56, 60 -> diag(mc, "garde+" + (ticks - 54));
            case 66 -> { film = null; diag(mc, "garde tenue"); shot(mc, "parade_01_garde_epaule"); }
            case 70 -> vue(lacet + 180f, 12f);
            case 80 -> { diag(mc, "garde face"); shot(mc, "parade_02_garde_face"); }
            case 82 -> vue(lacet + 90f, 6f);
            case 92 -> shot(mc, "parade_03_garde_profil");
            // ---- relâche : retour en fondu ----
            case 94 -> {
                film = "relache_profil";
                HudKeybinds.PARRY.setDown(false);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_OFF);
            }
            case 96 -> diag(mc, "relache+2");
            case 106 -> { film = null; diag(mc, "relache+12"); shot(mc, "parade_04_relache_profil"); }
            // ---- parade réussie : C pressée juste avant l'impact, le serveur annonce la parade ----
            case 112 -> {
                film = "parade_profil";
                HudKeybinds.PARRY.setDown(true);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_ON);
            }
            case 115 -> {
                // simule TYPE_PARRY (rôle 0 = paré) reçu du serveur + l'anim 7 diffusée aux autres
                CombatState.INSTANCE.onParry(0 /* paré */, System.currentTimeMillis());
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_PARRY);
            }
            case 117 -> { diag(mc, "parade+2"); shot(mc, "parade_05_parade_profil"); }
            case 119 -> {
                // tape courte : relâche juste après l'impact (le coup de garde doit aller au bout)
                HudKeybinds.PARRY.setDown(false);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_OFF);
            }
            case 121 -> diag(mc, "parade relache+2");
            case 136 -> { film = null; diag(mc, "parade fin"); }
            case 138 -> vue(lacet + 180f, 12f);
            case 146 -> {
                film = "parade_face";
                HudKeybinds.PARRY.setDown(true);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_ON);
            }
            case 149 -> {
                CombatState.INSTANCE.onParry(0 /* paré */, System.currentTimeMillis());
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_PARRY);
            }
            case 151 -> shot(mc, "parade_06_parade_face");
            case 164 -> { shot(mc, "parade_07_garde_apres_parade_face"); diag(mc, "garde apres parade"); }
            case 166 -> {
                HudKeybinds.PARRY.setDown(false);
                for (int id : autres) CombatAnimations.INSTANCE.playByEntityId(id, CombatAnimations.ANIM_BLOCK_OFF);
            }
            case 168, 170, 172, 176 -> diag(mc, "relache apres parade+" + (ticks - 166));
            case 178 -> { film = null; HudKeybinds.FREELOOK.setDown(false); RebornCamera.INSTANCE.initOrientation(lacet, 0f); }
            // ---- reprise de la marche après la garde (épaule) ----
            case 182 -> { film = "marche_epaule"; mc.options.keyUp.setDown(true); }
            case 196 -> shot(mc, "parade_08_marche_apres_garde");
            case 198 -> { HudKeybinds.PARRY.setDown(true); }
            case 206 -> { shot(mc, "parade_09_garde_en_marche"); diag(mc, "garde en marche"); }
            case 208 -> HudKeybinds.PARRY.setDown(false);
            case 222 -> { film = null; mc.options.keyUp.setDown(false); diag(mc, "fin"); }
            case 226 -> {
                if (!video) c.sendCommand("tick rate 20");
                c.sendCommand("kill @e[tag=parade_test]");
                c.sendCommand("effect clear @s night_vision");
                c.sendCommand("tp @s " + X + ".5 " + (Y - 90) + " " + Z + ".5");
                c.sendCommand("fill " + (X - 7) + " " + (Y - 1) + " " + (Z - 7) + " " + (X + 7) + " " + (Y - 1) + " " + (Z + 9)
                    + " minecraft:air");
            }
            case 232 -> mc.stop();
            default -> { }
        }
    }

    /** Caméra épaule en regard libre (Alt tenu) : l'orbite tourne autour du perso sans le faire pivoter. */
    private static void vue(float yaw, float pitch) {
        HudKeybinds.FREELOOK.setDown(true);
        RebornCamera.INSTANCE.initOrientation(yaw, pitch);
    }

    private static void diag(Minecraft mc, String quand) {
        StringBuilder sb = new StringBuilder("[parade-banc] ").append(quand)
            .append(" | bloque=").append(CombatInput.INSTANCE.isBlocking())
            .append(" | soi: ").append(CombatAnimations.INSTANCE.diag(mc.player));
        for (int id : autres) {
            if (mc.level.getEntity(id) instanceof net.minecraft.world.entity.Avatar a) {
                sb.append(" | autre ").append(id).append(": ").append(CombatAnimations.INSTANCE.diag(a));
            }
        }
        LOG.info(sb.toString());
    }

    private static void filmer(Minecraft mc) {
        if (!film.equals(filmPrec)) { filmPrec = film; image = 0; }
        Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "pfilm_%s_%03d.png", film, image++),
            mc.gameRenderer.mainRenderTarget(), 2, m -> { });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
