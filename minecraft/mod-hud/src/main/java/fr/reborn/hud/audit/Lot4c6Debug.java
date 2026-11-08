package fr.reborn.hud.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.concentration.ConcentrationCanal;
import fr.reborn.hud.concentration.HaloPlaque;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.concentration.Plaques;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.recit.RecitHud;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.staff.GardePlaquesDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Banc de captures de la <b>couleur du halo</b> des plaques (lot 4, planche c6), <b>inactif en production</b> :
 * {@code REBORN_LOT4_DEBUG=c6} (monde solo de dev, jamais de serveur). Serveur simulé côté client comme le banc de
 * l'étape B ({@link Lot4bDebug}) : le message {@code texte} porte le champ {@code halo} (absent = ivoire-or).
 *
 * <p>Scène : quatre stèles en rang, à 8 blocs l'une de l'autre — ivoire-or (halo absent), bleu chakra {@code 6FB4FF},
 * rouge sang {@code D9443F}, aucun halo. Horloges figées.
 * <ol>
 *   <li>1920 × 1080, jour puis nuit : chaque stèle lue à 4 blocs, sous l'effet d'écran.</li>
 *   <li>Outil « Plaques » : éditeur au repos (rangée « Halo », ivoire-or), préréglage bleu chakra (aperçu en direct),
 *       « Personnalisée » ouverte (rose cerisier {@code F29CC2}, 4 récentes), fiche d'une plaque bleu chakra (ligne
 *       « Halo »).</li>
 *   <li>1366 × 768 : éditeur au repos, sélecteur ouvert, une stèle lue de jour.</li>
 * </ol>
 * → {@code run/screenshots/lot4c6_*.png}, bilans « [lot4c6] OK / ECHEC » au journal ; le jeu se ferme (« [lot4c6] FIN »).
 */
public final class Lot4c6Debug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival", difficulte = "normal";
    private static long h;
    private static int ox, oy, oz;
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    private static int[] recentesAvant;

    private record Pl(String id, String titre, int dx0, String halo, String nom, int code) { }

    private static final String TEXTE = "Le <or>gardien</> veille encore ici, la <rouge>lame</> au poing ; ses <ombre>ombres</> "
        + "passent, <gris>dit le vent</>, sans un bruit.";
    private static final String TEXTE_ED = "Ici repose le <or>premier gardien</> de la porte Est. Il a tenu seul jusqu'à l'aube, "
        + "la <rouge>lame</> brisée. Que les <ombre>ombres</> ne passent jamais ce seuil.";
    private static final Map<String, Pl> PL = new LinkedHashMap<>();

    static {
        PL.put("P1A", new Pl("P1A", "Stèle ivoire", 1, null, "ivoire", HaloPlaque.DEFAUT));
        PL.put("P2B", new Pl("P2B", "Salle du serment", 9, "6FB4FF", "chakra", 0x6FB4FF));
        PL.put("P3C", new Pl("P3C", "Stèle du sang", 17, "D9443F", "sang", 0xD9443F));
        PL.put("P4D", new Pl("P4D", "Borne muette", 25, "aucun", "aucun", HaloPlaque.AUCUN));
    }

    private Lot4c6Debug() {}

    public static void init() {
        if (!"c6".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT4_DEBUG")))) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            volant = true;
            vol(mc, true);
            var pr = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE;
            pr.ensureLoaded();
            recentesAvant = pr.haloRecentes;
        }, "préparation");
        pas(10, Lot4c6Debug::batir, "scène");
        pas(30, mc -> {
            figer(Util.getMillis());
            ConcentrationCanal.debugServeur = Lot4c6Debug::serveur;
            System.out.println("[lot4c6] origine " + ox + " " + oy + " " + oz);
            // dérivation des préréglages (comparée à la planche c6-11)
            for (int c : HaloPlaque.PRESETS) {
                if (c < 0) continue;
                HaloPlaque.Palette p = HaloPlaque.Palette.depuis(c);
                System.out.printf("[lot4c6] palette %06X : coeur %06X lueur %06X voile %06X etincelle %06X%n", c, p.coeur(),
                    p.lueur(), p.voile(), p.etincelle());
            }
            HaloPlaque.Palette ch = HaloPlaque.Palette.depuis(0x6FB4FF);
            verifie(ch.coeur() == 0xE9F4FF, "bleu chakra : cœur #E9F4FF comme la planche (" + Integer.toHexString(ch.coeur()) + ")");
            verifie(HaloPlaque.Palette.relever(0x202020) != 0x202020, "une lueur sombre est relevée à 55 %");
            verifie(HaloPlaque.code(null) == HaloPlaque.DEFAUT && HaloPlaque.code("aucun") == HaloPlaque.AUCUN
                && HaloPlaque.code("6fb4ff") == 0x6FB4FF && HaloPlaque.code("FFCC7A") == HaloPlaque.DEFAUT, "codes du protocole");
        }, "horloges figées, serveur simulé");

        serieLecture("1080", "jour", true);
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        serieLecture("1080", "nuit", true);
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(10, mc -> { }, "jour revenu");
        serieOutil("1080");

        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(20, mc -> { }, "fenêtre posée");
        serieLecture("1366", "jour", false);
        serieOutil("1366");

        pas(10, mc -> {
            ModeConcentration.reinitialiser();
            ModeConcentration.horloge = Util::getMillis;
            Plaques.horloge = Util::getMillis;
            Recit.horloge = Util::getMillis;
            ConcentrationCanal.debugServeur = null;
            InteractionMode.INSTANCE.debugClose();
            GardePlaquesDebug.fermer(mc);
            fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.haloRecentes = recentesAvant;
            var c = mc.player.connection;
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 4, oy, oz - 4, ox + 30, oy + 5, oz + 16));
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[lot4c6] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4c6Debug::tick);
    }

    /* ================================================================ scène */

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 4, oy, oz - 4, ox + 30, oy + 8, oz + 16));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 4, oy - 1, oz - 4, ox + 30, oy - 1, oz + 16));
        for (Pl p : PL.values()) {
            int x = ox + p.dx0(), z = oz + 6;
            c.sendCommand(String.format("setblock %d %d %d minecraft:stone_bricks", x, oy, z));
            c.sendCommand(String.format("setblock %d %d %d minecraft:chiseled_stone_bricks", x, oy + 1, z));
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:dirt_path", x - 1, oy - 1, z - 7, x, oy - 1, z - 1));
            // lanterne à gauche de chaque stèle (la nuit)
            c.sendCommand(String.format("setblock %d %d %d minecraft:oak_fence", x - 2, oy, z));
            c.sendCommand(String.format("setblock %d %d %d minecraft:lantern", x - 2, oy + 1, z));
        }
    }

    /** Joueur à {@code d} blocs de la stèle {@code id} (face nord), regard au sud, un bloc à gauche (caméra à l'épaule). */
    private static void devant(Minecraft mc, String id, double d) {
        Pl p = PL.get(id);
        a(mc, ox + p.dx0() + 2.0, oz + 6 - d, 0f);
    }

    private static void a(Minecraft mc, double x, double z, float yaw) {
        mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %d %.2f %.1f 6", x, oy, z, yaw));
    }

    /* ================================================================ serveur simulé */

    private static void serveur(JsonObject in) {
        String a = in.has("a") ? in.get("a").getAsString() : "";
        switch (a) {
            case "on" -> {
                FILE.add("{\"v\":1,\"t\":\"etat\",\"on\":true}");
                FILE.add(ancres().toString());
            }
            case "lire" -> {
                String id = in.get("id").getAsString();
                Pl p = PL.get(id);
                if (p == null) { FILE.add("{\"v\":1,\"t\":\"texte_refus\",\"id\":\"" + id + "\"}"); return; }
                JsonObject o = new JsonObject();
                o.addProperty("v", 1);
                o.addProperty("t", "texte");
                o.addProperty("id", id);
                o.addProperty("rev", 1);
                o.add("segs", segs(TEXTE));
                if (p.halo() != null) o.addProperty("halo", p.halo());   // absent = ivoire-or
                FILE.add(o.toString());
            }
            default -> { }
        }
    }

    private static JsonObject ancres() {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "ancres");
        JsonArray l = new JsonArray();
        for (Pl p : PL.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p.id());
            x.addProperty("rev", 1);
            x.addProperty("k", "bloc");
            x.addProperty("x", ox + p.dx0());
            x.addProperty("y", oy);
            x.addProperty("z", oz + 6);
            JsonArray b = new JsonArray();
            b.add(1);
            b.add(2);
            b.add(1);
            x.add("b", b);
            x.addProperty("p", 6);
            l.add(x);
        }
        o.add("l", l);
        return o;
    }

    private static JsonArray segs(String balise) {
        JsonArray a = new JsonArray();
        for (Recit.Segment s : Recit.segments(balise)) {
            JsonArray x = new JsonArray();
            x.add(s.texte());
            x.add(s.couleur());
            a.add(x);
        }
        return a;
    }

    /* ================================================================ lecture */

    private static void serieLecture(String res, String moment, boolean toutes) {
        String pre = "lot4c6_" + res + "_" + moment + "_halo_";
        for (Pl p : PL.values()) {
            if (!toutes && !"P2B".equals(p.id())) continue;
            pas(2, mc -> devant(mc, p.id(), 4), "devant " + p.id() + " à 4 blocs");
            pas(15, Lot4c6Debug::entrer, "entrée");
            pas(14, mc -> avance(300), "fondus finis (texte arrivé)");
            pas(6, mc -> {
                verifie(p.id().equals(Plaques.lue()), res + " " + moment + " : " + p.id() + " lue (" + Plaques.bilan() + ")");
                int code = Plaques.haloDe("plaque:" + p.id());
                verifie(code == p.code(), res + " " + moment + " : halo de " + p.id() + " = " + p.nom() + " (code " + code + ")");
                verifie(RecitHud.ancree(), res + " " + moment + " : texte ancré devant l'objet");
                shot(mc, pre + p.nom());
            }, "capture " + p.nom());
            pas(1, mc -> sortir(), "sortie");
        }
    }

    /* ================================================================ outil « Plaques » */

    private static final String SNAP = "{\"t\":\"snap\",\"grade\":3,\"modeStaff\":true,\"modeAnim\":false,\"animPossible\":true,"
        + "\"staffOnline\":1,\"players\":[],\"alerts\":[],\"chat\":[],\"commands\":[]}";

    private static void serieOutil(String res) {
        String pre = "lot4c6_" + res + "_outil_";
        // devant la stèle bleu chakra, décalé à gauche : la stèle au centre gauche de l'écran, le volet à droite
        // en 1366, le volet prend une plus grande part de l'écran : la stèle est ramenée vers le centre
        pas(2, mc -> { if ("1366".equals(res)) a(mc, ox + 9 + 3.0, oz + 0.5, 24f); else a(mc, ox + 9 + 4.5, oz + 1, 14f); },
            "devant P2B, à gauche");
        pas(10, mc -> {
            GardePlaquesDebug.serveur(Lot4c6Debug::serveurStaff);
            GardePlaquesDebug.ouvrir(mc, SNAP);
            GardePlaquesDebug.recentes(0x7FD0C8, 0xFF7A5C, 0xBEA0FF, 0xF29CC2);
        }, "Poste de garde › Outils › Plaques");
        pas(12, mc -> GardePlaquesDebug.editeur(mc, TEXTE_ED, "Stèle du premier gardien", 6, ancreP2B(), "Bloc 1 × 2 · stèle",
            new double[]{ox + 9, oy, oz + 6, ox + 10, oy + 2, oz + 7}), "éditeur rempli");
        pas(16, mc -> {
            System.out.println("[lot4c6] " + GardePlaquesDebug.bilan(mc));
            shot(mc, pre + "editeur_repos");
        }, "capture éditeur au repos");
        pas(2, mc -> GardePlaquesDebug.halo(mc, 0x6FB4FF, false, 0, false), "préréglage bleu chakra");
        pas(12, mc -> shot(mc, pre + "editeur_chakra"), "capture éditeur bleu chakra");
        pas(2, mc -> GardePlaquesDebug.halo(mc, HaloPlaque.AUCUN, false, 0, false), "aucun halo");
        pas(12, mc -> shot(mc, pre + "editeur_aucun"), "capture éditeur aucun halo");
        pas(2, mc -> GardePlaquesDebug.halo(mc, 0, true, 0xF29CC2, true), "Personnalisée ouverte, rose cerisier");
        pas(16, mc -> shot(mc, pre + "editeur_perso_ouvert"), "capture sélecteur ouvert");
        // une plaque qui a déjà un halo personnalisé : « Modifier le texte » puis « Personnalisée » (le vrai chemin, sans debug())
        pas(2, mc -> GardePlaquesDebug.halo(mc, 0x6FB4FF, false, 0, false), "une autre plaque d'abord (état à ne pas garder)");
        pas(2, mc -> GardePlaquesDebug.haloModifier(mc, 0x123ABC), "plaque en #123ABC, Personnalisée ouverte");
        pas(16, mc -> {
            String e = GardePlaquesDebug.haloEtat(mc);
            System.out.println("[lot4c6] " + e);
            verifie(e.contains("hex=#123ABC") && e.contains("change=false"), res + " : le sélecteur montre le halo de la plaque, rien de changé");
            verifie(e.contains("val=0.737") && e.contains("h=0.627"), res + " : curseurs sur la couleur de la plaque (" + e + ")");
            shot(mc, pre + "editeur_perso_modifiee");
        }, "capture sélecteur d'une plaque déjà personnalisée");
        pas(2, mc -> {
            GardePlaquesDebug.haloTeinte(mc, 0.58f);
            int c = GardePlaquesDebug.haloCouleur(mc);
            verifie(HaloPlaque.Palette.relever(c) == c && c != 0, res + " : un clic sur la teinte garde la luminosité (" + Integer.toHexString(c) + ")");
        }, "clic sur la barre de teinte");
        // sans ancre (objet en main, ancre pas encore choisie) : l'aperçu de repli montre le halo choisi
        pas(2, mc -> {
            GardePlaquesDebug.editeur(mc, TEXTE_ED, "Stèle du premier gardien", 6, null, "", null);
            GardePlaquesDebug.halo(mc, 0xD9443F, false, 0, false);
        }, "éditeur sans ancre, rouge sang");
        pas(16, mc -> shot(mc, pre + "editeur_repli_sang"), "capture aperçu de repli, rouge sang");
        pas(2, mc -> GardePlaquesDebug.halo(mc, HaloPlaque.AUCUN, false, 0, false), "éditeur sans ancre, aucun halo");
        pas(12, mc -> shot(mc, pre + "editeur_repli_aucun"), "capture aperçu de repli, aucun halo");
        pas(2, mc -> {
            GardePlaquesDebug.liste(mc);
            GardePlaquesDebug.fiche(mc, "P2B", true);
            GardePlaquesDebug.defiler(mc, 0);
        }, "fiche P2B + aperçu");
        pas(14, mc -> shot(mc, pre + "fiche_halo"), "capture fiche");
        pas(2, mc -> GardePlaquesDebug.fermer(mc), "volet fermé");
    }

    private static JsonObject ancreP2B() {
        JsonObject a = new JsonObject();
        a.addProperty("k", "bloc");
        a.addProperty("x", ox + 9);
        a.addProperty("y", oy);
        a.addProperty("z", oz + 6);
        JsonArray b = new JsonArray();
        b.add(1);
        b.add(2);
        b.add(1);
        a.add("b", b);
        return a;
    }

    private static void serveurStaff(JsonObject in) {
        String a = in.get("a").getAsString();
        System.out.println("[lot4c6] staff → " + in);
        if ("plaques".equals(a)) GardePlaquesDebug.recevoir(listeStaff().toString());
    }

    private static JsonObject listeStaff() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("t", "plaques");
        o.addProperty("grade", 3);
        o.addProperty("anim", false);
        o.addProperty("peutCreer", true);
        o.addProperty("temporaire", false);
        o.addProperty("peutRestaurer", true);
        o.addProperty("voirTout", true);
        o.addProperty("rayon", 64);
        o.addProperty("texteMax", 700);
        JsonArray l = new JsonArray();
        var eye = mc.player.getEyePosition();
        int i = 0;
        for (Pl p : PL.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p.id());
            x.addProperty("titre", p.titre());
            String plat = Recit.segments(TEXTE).stream().map(Recit.Segment::texte).reduce("", String::concat);
            x.addProperty("texte", plat);
            x.add("segs", segs(TEXTE));
            x.addProperty("k", "bloc");
            x.addProperty("monde", "world");
            x.addProperty("x", ox + p.dx0());
            x.addProperty("y", oy);
            x.addProperty("z", oz + 6);
            JsonArray b = new JsonArray();
            b.add(1);
            b.add(2);
            b.add(1);
            x.add("b", b);
            double dx = Math.max(Math.max(ox + p.dx0() - eye.x, 0), eye.x - (ox + p.dx0() + 1));
            double dz = Math.max(Math.max(oz + 6 - eye.z, 0), eye.z - (oz + 7));
            x.addProperty("dist", Math.round(Math.sqrt(dx * dx + dz * dz) * 10) / 10.0);
            x.addProperty("p", 6);
            x.addProperty("actif", true);
            x.addProperty("expire", 0);
            x.addProperty("temporaire", false);
            x.addProperty("rev", 2);
            if (p.halo() != null) x.addProperty("halo", p.halo());
            x.addProperty("par", i % 2 == 0 ? "Omar" : "Kaede");
            x.addProperty("le", "07/11 18:40");
            x.addProperty("modifPar", "Omar");
            x.addProperty("modifLe", "08/11 10:12");
            x.addProperty("lectures", 37 - i * 6);
            x.addProperty("moi", i % 2 == 0);
            x.addProperty("peutModifier", true);
            l.add(x);
            i++;
        }
        o.add("l", l);
        o.add("corbeille", new JsonArray());
        return o;
    }

    /* ================================================================ outils */

    private static void entrer(Minecraft mc) {
        avance(300);
        boolean ok = ModeConcentration.entrer();
        verifie(ok, "entrée en concentration (refus : " + ModeConcentration.dernierRefus() + ")");
    }

    private static void sortir() {
        ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE);
        avance(600);
    }

    private static void figer(long now) {
        h = now;
        ModeConcentration.horloge = () -> h;
        Plaques.horloge = () -> h;
        Recit.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4c6] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4c6] " + nom);
            action.accept(mc);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (volant) vol(mc, true);
        String r = FILE.poll();
        if (r != null) ConcentrationCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
