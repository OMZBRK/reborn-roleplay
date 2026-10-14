package fr.reborn.hud.ko;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel du KO, <b>inactif en production</b> : ne fait rien sauf si la variable d'environnement
 * {@code REBORN_KO_DEBUG=1} est définie (client de dev, {@code gradlew runClient}). Dans un monde solo, simule les
 * paquets du serveur (à terre, KO, épuisement, hôpital, ATA, repos, rétabli, défaite) et prend une capture de
 * chaque étape dans run/screenshots/, puis ferme le jeu.
 */
public final class KoDebug {

    private static int ticks = -1;

    private KoDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_KO_DEBUG");
        if ("mort".equals(v)) ClientTickEvents.END_CLIENT_TICK.register(KoDebug::mort);
        else if ("douleur".equals(v)) ClientTickEvents.END_CLIENT_TICK.register(KoDebug::douleur);
        else if ("1".equals(v)) ClientTickEvents.END_CLIENT_TICK.register(KoDebug::tick);
    }

    /** Exécutions possédées simulées (sous-menu « Exécuter »). */
    private static final String EX = "\"ex\":[{\"id\":\"coup_de_grace\",\"n\":\"Coup de grâce\"},"
            + "{\"id\":\"lame_dans_le_dos\",\"n\":\"Lame dans le dos\"}]";

    /** Rejoue un paquet {@code reborn:corps} : corps {@code b}, personnes en douleur {@code dl}, mon état {@code me}. */
    private static void corps(String b, String dl, String me) {
        CorpsClient.debugReceive("{\"t\":\"bodies\",\"b\":[" + b + "],\"dl\":[" + dl + "]," + EX + ",\"me\":{" + me + "}}");
    }

    /** Mannequins du banc (les plus proches d'abord). */
    private static java.util.List<net.minecraft.world.entity.Entity> mannequins(Minecraft mc) {
        java.util.List<net.minecraft.world.entity.Entity> out = new java.util.ArrayList<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.decoration.Mannequin) out.add(e);
        }
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        return out;
    }

    /** Fait disparaître les mannequins du banc sans nuage de mort (sous le monde). */
    private static void gone(net.minecraft.client.multiplayer.ClientPacketListener c) {
        c.sendCommand("execute as @e[tag=ko_test] run tp @s ~ -300 ~");
    }

    private static String u(net.minecraft.world.entity.Entity e) { return "{\"u\":\"" + e.getUUID() + "\"}"; }

    /** Plaque de nom RP simulée pour un mannequin du banc (TablistData). */
    private static void nom(net.minecraft.world.entity.Entity e) {
        // Nameplates.debugAvatars donne « Ren Hyūga » aux mannequins (sans toucher aux données du tab, qui allumeraient
        // les vitals et la caméra épaule du serveur)
    }

    private static net.minecraft.world.entity.Entity premier(Minecraft mc, boolean passager) {
        for (var e : mannequins(mc)) if (!passager || e.isPassenger()) return e;
        return null;
    }

    /**
     * {@code REBORN_KO_DEBUG=douleur} (v2 du 14/10, style unique dérivé de l'écran « inconscient ») : douleur (entrée,
     * ressenti, vue d'un autre avec le glyphe au nom et l'invite « [F] Exécuter », menu R « Animation d'exécution »),
     * exécution à la touche (anneau exécuteur, victime), porté en douleur (victime, autre), agonie (vue d'un autre,
     * transition, reptation en 3e personne sans HUD, vue victime), inconscient (avant et après les 5 min), corps KO
     * avec RPK (invite, confirmation, anneau), menus R d'un corps. Captures nommées douleur_*, agonie_*, ko_*.
     */
    private static void douleur(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        var im = fr.reborn.hud.interaction.InteractionMode.INSTANCE;
        // le banc ne garde aucun message de tchat (retours de commandes) dans les captures
        if (ticks > 45 && ticks % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 1000");
                c.sendCommand("weather clear");
                // posé au sol (pas de chute), insensible pendant le banc
                c.sendCommand("execute positioned over motion_blocking_no_leaves run tp @s ~ ~ ~ -90 12");
                c.sendCommand("gamemode survival");
                c.sendCommand("effect give @s resistance infinite 255 true");
                gone(c);
                c.sendCommand("clear @s");
                fr.reborn.hud.nameplate.Nameplates.debugAvatars = true;
            }
            case 60 -> c.sendCommand("execute rotated ~ 0 run summon mannequin ^ ^ ^3 {Rotation:[90f,0f],Tags:[\"ko_test\"]}");
            case 70 -> { corps("", "", "\"d\":1"); KoClient.event(KoClient.EV_DOULEUR); }
            case 82 -> named(mc, "douleur_1_entree");
            case 140 -> named(mc, "douleur_2_ressenti_a");
            case 163 -> named(mc, "douleur_2_ressenti_b");
            case 187 -> named(mc, "douleur_2_ressenti_c");
            case 205 -> {
                var m = premier(mc, false);
                if (m != null) { nom(m); corps("", u(m), ""); ExecuterClient.debugCible = m; }
                ExecuterClient.debugLethal = false;
                ExecuterClient.debugArme = false;
            }
            case 225 -> named(mc, "douleur_3_vue_autre_invite_F");
            case 227 -> {
                ExecuterClient.debugCible = null;
                im.debugOpen("Ren_Hyuga", fr.reborn.hud.interaction.InteractionMenus.forPain("Ren_Hyuga"), 2);
            }
            case 237 -> named(mc, "douleur_4_menu_R_animation");
            case 239 -> { im.debugClose(); ExecuterClient.debugAnneau = 0.45f; ExecuterClient.debugLethal = false; }
            case 252 -> named(mc, "douleur_5_execution_executeur");
            case 254 -> {
                ExecuterClient.debugAnneau = -1f;
                corps("", "", "\"d\":1,\"x\":{\"r\":1,\"e\":30,\"d\":60,\"k\":60,\"l\":0}");
            }
            case 264 -> named(mc, "douleur_6_execution_victime");
            case 266 -> {
                corps("", "", "\"d\":1,\"c\":1,\"s\":0.45");
                c.sendCommand("ride @s mount @e[tag=ko_test,limit=1]");
            }
            case 296 -> named(mc, "douleur_7_porte_victime");
            case 298 -> {
                c.sendCommand("ride @s dismount");
                gone(c);
                corps("", "", "");
            }
            case 306 -> c.sendCommand("execute rotated ~ 0 run summon mannequin ^ ^ ^3.4 {Rotation:[90f,0f],Tags:[\"ko_test\"],"
                    + "Passengers:[{id:\"minecraft:mannequin\",Rotation:[0f,0f],Tags:[\"ko_test\"]}]}");
            case 316 -> {
                var m = premier(mc, true);
                if (m != null) { nom(m); corps("", u(m), ""); }
                c.sendCommand("tp @s ~ ~ ~ -90 -14");
            }
            case 332 -> named(mc, "douleur_8_porte_vue_autre");
            case 334 -> {
                gone(c);
                c.sendCommand("tp @s ~ ~ ~ -90 20");
                corps("", "", "");
            }
            case 342 -> c.sendCommand("execute rotated ~ 0 run summon mannequin ^ ^ ^2.4 {Rotation:[0f,0f],pose:\"swimming\",hide_description:1b,Tags:[\"ko_test\"]}");
            case 352 -> {
                var m = premier(mc, false);
                if (m != null) { nom(m); corps("{\"u\":\"" + m.getUUID() + "\",\"ph\":1,\"rpk\":0}", "", ""); }
            }
            case 368 -> named(mc, "agonie_1_vue_autre");
            case 370 -> {
                gone(c);
                corps("", "", "");
                c.sendCommand("setblock ~ ~1 ~ minecraft:barrier");
                KoClient.event(KoClient.EV_A_TERRE);
                KoClient.update(ko(1, 0, 38, 45, false));
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            }
            case 384 -> named(mc, "agonie_2_transition");
            case 440 -> named(mc, "agonie_3_ramper_3e_personne");
            case 442 -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            case 460 -> named(mc, "agonie_4_vue_victime");
            case 462 -> {
                c.sendCommand("fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:air replace minecraft:barrier");
                c.sendCommand("tp @s ~ ~0.2 ~");
                KoClient.event(KoClient.EV_KO);
                KoClient.update(ko(2, 0, 170, 300, false));
            }
            case 474 -> named(mc, "ko_1_transition");
            case 540 -> named(mc, "ko_2_inconscient");
            case 542 -> KoClient.update(ko(2, 0, 0, 300, true));
            case 556 -> named(mc, "ko_3_inconscient_hopital");
            case 558 -> {
                KoClient.update(null);
                c.sendCommand("execute rotated ~ 0 run summon mannequin ^ ^ ^2.4 {Rotation:[0f,0f],pose:\"swimming\",hide_description:1b,Tags:[\"ko_test\"]}");
            }
            case 568 -> {
                var m = premier(mc, false);
                if (m != null) {
                    nom(m);
                    corps("{\"u\":\"" + m.getUUID() + "\",\"ph\":2,\"rpk\":2}", "", "");
                    ExecuterClient.debugCible = m;
                }
                ExecuterClient.debugLethal = true;
                ExecuterClient.debugArme = false;
            }
            case 600 -> named(mc, "ko_4_vue_autre_invite_mort_rp");
            case 602 -> ExecuterClient.debugArme = true;
            case 610 -> named(mc, "ko_5_confirmation");
            case 612 -> { ExecuterClient.debugCible = null; ExecuterClient.debugAnneau = 0.6f; ExecuterClient.debugLethal = true; }
            case 622 -> named(mc, "ko_6_execution_mort_rp");
            case 624 -> {
                ExecuterClient.debugAnneau = -1f;
                ExecuterClient.debugLethal = false;
                im.debugOpen("Ren_Hyuga", fr.reborn.hud.interaction.InteractionMenus.forBody("Ren_Hyuga",
                        new CorpsClient.Body(2, 0)), 4);
            }
            case 634 -> named(mc, "ko_7_menu_R_demander_un_rpk");
            case 636 -> im.debugOpen("Ren_Hyuga", fr.reborn.hud.interaction.InteractionMenus.forBody("Ren_Hyuga",
                    new CorpsClient.Body(2, 2)), 5);
            case 646 -> named(mc, "ko_8_menu_R_rpk_valide");
            case 648 -> {
                im.debugClose();
                gone(c);
                fr.reborn.hud.nameplate.Nameplates.debugAvatars = false;
            }
            case 660 -> mc.stop();
            default -> { }
        }
    }

    public static KoPayload ko(int phase, int cause, int left, int total, boolean hosp) {
        return new KoPayload((byte) phase, (byte) cause, (short) left, (short) total, hosp,
                (byte) 0, 0f, (short) 0, (short) 0, false, false);
    }

    public static KoPayload ata(int level, float rest, int min, int req, boolean resting, boolean fear) {
        return new KoPayload((byte) 0, (byte) 0, (short) 0, (short) 0, false,
                (byte) level, rest, (short) min, (short) req, resting, fear);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 13000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("tp @s ~ ~ ~ -90 5");
                c.sendCommand("kill @e[tag=ko_test]");
            }
            case 60 -> c.sendCommand("summon mannequin ^1 ^ ^4 {NoGravity:1b,Rotation:[120f,0f],Tags:[\"ko_test\"]}");
            case 100 -> { KoClient.event(KoClient.EV_A_TERRE); KoClient.update(ko(1, 0, 38, 45, false)); }
            case 112 -> shot(mc);                                   // carte « À terre »
            case 190 -> shot(mc);                                   // à terre : plaque + vignette
            case 200 -> { KoClient.event(KoClient.EV_KO); KoClient.update(ko(2, 0, 170, 300, true)); }
            case 212 -> shot(mc);                                   // carte « K.O. »
            case 290 -> shot(mc);                                   // inconscient + /hopital
            case 300 -> KoClient.update(ko(2, 1, 95, 180, false));
            case 380 -> shot(mc);                                   // épuisement de chakra (bleu)
            case 390 -> { KoClient.event(KoClient.EV_HOPITAL); KoClient.update(ata(2, 0.05f, 1, 20, false, true)); }
            case 402 -> shot(mc);                                   // carte « Hôpital »
            case 490 -> shot(mc);                                   // ATA pleine + peur
            case 500 -> KoClient.update(ata(2, 0.6f, 12, 20, true, true));
            case 560 -> shot(mc);                                   // repos en cours
            case 570 -> { KoClient.event(KoClient.EV_RETABLI); KoClient.update(ata(0, 0f, 0, 0, false, true)); }
            case 582 -> shot(mc);                                   // carte « Rétabli » + peur résiduelle
            case 640 -> { KoClient.event(KoClient.EV_DEFAITE); KoClient.update(null); }
            case 652 -> shot(mc);                                   // carte « Défaite »
            case 700 -> c.sendCommand("kill @e[tag=ko_test]");
            case 720 -> mc.stop();
            default -> { }
        }
    }

    /**
     * {@code REBORN_KO_DEBUG=mort} : scène de mort (yeux qui se ferment, question, survol, vue cinéma) puis menu
     * d'interaction (curseur, joueur avec sous-menu, corps KO, sur soi). Captures nommées mort_* et interaction_*.
     */
    private static void mort(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var c = mc.player.connection;
        var im = fr.reborn.hud.interaction.InteractionMode.INSTANCE;
        switch (ticks) {
            case 40 -> {
                c.sendCommand("time set 13000");
                c.sendCommand("weather clear");
                c.sendCommand("gamemode survival");
                c.sendCommand("tp @s ~ ~ ~ -90 5");
                c.sendCommand("kill @e[tag=ko_test]");
                c.sendCommand("clear @s");
            }
            case 70 -> mc.gui.hud.getChat().clearMessages(false);
            case 80 -> mc.setScreenAndShow(new DeathScreen("Ren Hyūga", 2));
            case 96 -> named(mc, "mort_1_paupieres");
            case 150 -> named(mc, "mort_2_question");
            case 152 -> { if (mc.gui.screen() instanceof DeathScreen d) d.debugHover = 0; }
            case 160 -> named(mc, "mort_3_survol_oui");
            case 162 -> { if (mc.gui.screen() instanceof DeathScreen d) { d.debugHover = 1; } }
            case 170 -> named(mc, "mort_4_survol_non");
            case 172 -> { if (mc.gui.screen() instanceof DeathScreen d) { d.debugHover = -1; d.debugAnswer(true); } }
            case 250 -> named(mc, "mort_5_cinema");
            case 255 -> { if (mc.gui.screen() instanceof DeathScreen d) d.restore(); mc.setScreenAndShow(null); }
            case 275 -> { im.toggle(); }
            case 285 -> named(mc, "interaction_1_curseur");
            case 287 -> im.debugOpen("Kazuki_Uchiha", fr.reborn.hud.interaction.InteractionMenus.forPlayer("Kazuki_Uchiha"), 8);
            case 297 -> named(mc, "interaction_2_joueur_sous_menu");
            case 299 -> im.debugOpen("Ren_Hyuga", fr.reborn.hud.interaction.InteractionMenus.forBody("Ren_Hyuga", new CorpsClient.Body(2, 2)), 1);
            case 309 -> named(mc, "interaction_3_corps");
            case 311 -> im.debugOpen("Moi", fr.reborn.hud.interaction.InteractionMenus.forSelf(), 3);
            case 321 -> named(mc, "interaction_4_soi");
            case 323 -> im.debugOpen("Bloc", fr.reborn.hud.interaction.InteractionMenus.forBlock(mc.player.blockPosition()), -1);
            case 333 -> named(mc, "interaction_5_bloc");
            case 335 -> im.debugClose();
            case 340 -> c.sendCommand("kill @e[tag=ko_test]");
            case 350 -> mc.stop();
            default -> { }
        }
    }

    private static void named(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
