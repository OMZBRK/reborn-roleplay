package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Banc d'essai visuel du Poste de garde, <b>inactif en production</b> : ne fait rien sauf si
 * {@code REBORN_STAFF_DEBUG=1} (client de dev, {@code gradlew runClient}). Injecte un état d'exemple (comme s'il
 * venait du serveur), ouvre chaque onglet, prend une capture dans run/screenshots/, puis ferme le jeu.
 */
public final class StaffDebug {

    private static int ticks = -1;

    private StaffDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_STAFF_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(StaffDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        String me = mc.player.getUUID().toString();
        switch (ticks) {
            case 40 -> mc.player.connection.sendCommand("time set 13000");
            case 60 -> {
                inject(SNAP.replace("$ME", me));
                inject(PROFILE.replace("$ME", me));
                mc.setScreenAndShow(new GardeScreen("alerts"));
            }
            case 90 -> shot(mc);                                                   // Alertes
            case 95 -> click(mc, "players");
            case 110 -> shot(mc);                                                  // Joueurs
            case 115 -> profile(mc, me);
            case 135 -> shot(mc);                                                  // Fiche
            case 140 -> scroll(mc, -12);
            case 155 -> shot(mc);                                                  // Fiche (bas : casier, infractions)
            case 160 -> confirm(mc);
            case 180 -> shot(mc);                                                  // Confirmation
            case 185 -> click(mc, "chat");
            case 200 -> shot(mc);                                                  // Chat staff
            case 205 -> click(mc, "journal");
            case 220 -> shot(mc);                                                  // Journal
            case 225 -> click(mc, "cmds");
            case 240 -> shot(mc);                                                  // Commandes
            case 260 -> mc.stop();
            default -> { }
        }
    }

    /** Audit responsive : Poste de garde ouvert sur {@code tab} (fiche du joueur si {@code profile}). */
    public static void openDemo(Minecraft mc, String tab, boolean profile) {
        String me = mc.player.getUUID().toString();
        inject(SNAP.replace("$ME", me));
        inject(PROFILE.replace("$ME", me));
        GardeScreen s = new GardeScreen("alerts");
        mc.setScreenAndShow(s);
        s.debugTab(tab);
        if (profile) s.debugProfile(me);
    }

    private static void inject(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        try {
            var m = StaffClient.class.getDeclaredMethod("receive", String.class);
            m.setAccessible(true);
            m.invoke(null, o.toString());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void click(Minecraft mc, String tab) {
        if (mc.gui.screen() instanceof StaffScreen s) s.debugTab(tab);
    }

    private static void profile(Minecraft mc, String uuid) {
        if (mc.gui.screen() instanceof StaffScreen s) s.debugProfile(uuid);
    }

    private static void scroll(Minecraft mc, double amount) {
        if (mc.gui.screen() instanceof StaffScreen s) s.mouseScrolled(0, 0, 0, amount);
    }

    private static void confirm(Minecraft mc) {
        if (mc.gui.screen() instanceof StaffScreen s) s.debugConfirm(3);
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }

    private static final String SNAP = """
            {"t":"snap","grade":2,"gradeName":"Modérateur","staffOnline":4,
             "alerts":[
              {"id":"ag-1","kind":"agression","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","rank":"Genin","village":"konoha","online":true,
               "motif":"Combat hors terrain d'entraînement ×4","ctx":["3 victimes différentes","world 812 64 -233","à l'instant"],"score":157},
              {"id":"aide-2","kind":"aide","uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","rank":"Genin","online":true,
               "motif":"Appel à l'aide · à terre","ctx":["world 640 70 -120","il y a 2 min"],"score":0},
              {"id":"rep-3","kind":"signalement","uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","rank":"Chūnin","online":true,
               "motif":"Signalé par 2 joueurs · HRP au chat","ctx":["world 700 66 -180","il y a 6 min"],"score":60}],
             "players":[
              {"uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","rank":"Genin","village":"konoha","state":"ATA pleine · repos 3/20"},
              {"uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","rank":"Genin","village":"konoha","state":"À terre"},
              {"uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","rank":"Chūnin","village":"konoha","state":""},
              {"uuid":"$ME","name":"Aya","perso":"Aya Haruno","rank":"Chūnin","village":"konoha","state":""}],
             "chat":[
              {"time":"17:41","grade":1,"name":"Aya","text":"Ren est à terre dans la forêt est, j'y vais.","sys":false},
              {"time":"17:42","grade":0,"name":"Système","text":"Alerte : Kazuki Uchiha (Raiden_FR) · combat hors terrain ×4 (score 157).","sys":true},
              {"time":"17:43","grade":2,"name":"Hiro","text":"Je le prends, c'est la deuxième fois cette semaine.","sys":false},
              {"time":"17:44","grade":4,"name":"Omz","text":"Ok, applique l'escalade et marque les preuves.","sys":false}],
             "journal":[
              {"time":"17:40","name":"Aya","grade":1,"kind":"soin","type":"Paume de soin","detail":"Ren Hyūga"},
              {"time":"17:38","name":"Aya","grade":1,"kind":"tp","type":"TP vers","detail":"Ren Hyūga (Mika_)"},
              {"time":"17:31","name":"Omz","grade":4,"kind":"ko","type":"ATA levée","detail":"Daisuke Sarutobi"},
              {"time":"17:20","name":"Hiro","grade":2,"kind":"ko","type":"Réveil forcé","detail":"Kazuki Uchiha (Raiden_FR)"},
              {"time":"16:58","name":"Omz","grade":4,"kind":"monde","type":"Zone créée","detail":"repos « ichiraku »"},
              {"time":"15:47","name":"Hiro","grade":2,"kind":"sanction","type":"Avertissement","detail":"Raiden_FR · Combat d'arène"}],
             "commands":[
              {"cat":"Joueurs et modération","cmd":"/garde","desc":"Ouvrir le Poste de garde","grade":1},
              {"cat":"Joueurs et modération","cmd":"/tp <joueur>","desc":"Aller à un joueur","grade":1},
              {"cat":"Joueurs et modération","cmd":"/vanish","desc":"Devenir invisible","grade":2},
              {"cat":"Joueurs et modération","cmd":"/pardon <joueur>","desc":"Lever un bannissement","grade":3},
              {"cat":"KO, ATA et soins","cmd":"/ata lever <joueur>","desc":"Lever l'ATA","grade":2},
              {"cat":"KO, ATA et soins","cmd":"/hopital set <village|defaut>","desc":"Placer le lit de réveil d'un village","grade":3},
              {"cat":"Serveur","cmd":"/sc reload","desc":"Recharger ShinobiCore","grade":4}]}
            """;

    private static final String PROFILE = """
            {"t":"profile","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","rank":"Genin","village":"konoha","online":true,
             "state":"ATA pleine · repos 3/20","hp":412,"hpMax":1380,"chakra":6200,"chakraMax":16000,"ryo":12450,"playtime":2472,"ping":38,
             "frozen":false,"watching":false,"score":157,
             "alts":[{"uuid":"$ME","name":"Raiden_alt","seen":"il y a 3 h"}],
             "record":[{"when":"05/10 · 21:14","text":"Mute 1 jour · Insultes · par Hiro","status":"actif","active":true},
                       {"when":"01/10 · 18:40","text":"Avertissement · Combat d'arène · par Omz","status":"noté","active":false}],
             "recordTotal":2,"recordActive":1,
             "offenses":[
              {"id":"hrp","name":"HRP","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"metagaming","name":"Metagaming","strikes":1,"next":"Mute 6 h","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"powergaming","name":"Powergaming","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"combat_arene","name":"Combat d'arène","strikes":1,"next":"Ban 1 jour","tone":"ban","locked":false,"lockGrade":"Modérateur"},
              {"id":"nlr","name":"NLR non respecté","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"fear_pain","name":"Fear / Pain RP ignoré","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"insultes","name":"Insultes","strikes":1,"next":"Mute 1 jour","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"spam","name":"Spam","strikes":0,"next":"Mute 15 min","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"triche","name":"Triche","strikes":0,"next":"Ban 30 jours","tone":"ban","locked":true,"lockGrade":"Admin"}],
             "msgs":[{"time":"06/10 à 17:48","text":"viens au marché on règle ça maintenant"},
                     {"time":"06/10 à 17:49","text":"le RP on s'en fiche, on se bat"},
                     {"time":"06/10 à 17:51","text":"gg ez, au suivant"}]}
            """;
}
