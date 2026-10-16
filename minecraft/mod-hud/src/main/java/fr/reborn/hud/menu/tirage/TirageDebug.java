package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Banc du test de la Feuille v2, <b>inactif en production</b> ({@code REBORN_TIRAGE_DEBUG}, monde solo de dev, aucun
 * serveur : les natures sont imposées, la canalisation est simulée).
 * <ul>
 *   <li>{@code =1} : confirmation (capture), puis un <b>film</b> par nature (Katon, Suiton, Doton, Raiton, Fūton) et
 *       un pour la double nature (Suiton déjà éveillé + Katon) : caméra épaule → glissement → canalisation →
 *       réaction → révélation → « Valider » → retour à l'épaule. L'horloge de l'écran avance de 50 ms par tick client
 *       capturé ({@link TirageHorloge}) : une capture par tick = un film régulier à 20 i/s
 *       ({@code run/screenshots/tfilm_<cas>_<nnn>.png}). Puis « renoncer » (Échap pendant la canalisation) et un mur
 *       devant le personnage (le plan choisit un autre angle). Lacet du joueur journalisé à chaque tick
 *       ({@code [tirage-banc]}) : il ne doit plus bouger.</li>
 *   <li>{@code =bug} : ancien journal du bug de caméra (lacet / orbite pendant les signes).</li>
 * </ul>
 * Le jeu se ferme tout seul ; heure, météo, caméra et blocs posés sont rendus. {@code REBORN_TIRAGE_SKIN=slim:<id>}
 * (ou {@code wide:<id>}) impose le skin du bras du gros plan (v3), par ex.
 * {@code slim:minecraft:textures/entity/player/slim/makena.png} ; sinon c'est celui du joueur.
 */
public final class TirageDebug {

    private record Cas(String nom, String nature, String autres, boolean annuler, boolean mur) {}

    private static final List<Cas> CAS = new ArrayList<>();
    private static int t = -1, local = -1, idx = 0, image = 0, fin = -1;
    private static long ouvert, revel = -1;
    private static String mode;
    private static float yawDepart;
    private static int dernieresImages = -1;
    private static int mx, my, mz;

    private TirageDebug() {}

    static boolean actif() { return mode != null && !mode.isBlank(); }

    public static void init() {
        mode = System.getenv("REBORN_TIRAGE_DEBUG");
        if (mode == null || mode.isBlank()) return;
        String peau = System.getenv("REBORN_TIRAGE_SKIN");
        if (peau != null && !peau.isBlank()) {
            TirageBras.debugSlim = peau.startsWith("slim:");
            TirageBras.debugSkin = net.minecraft.resources.Identifier.parse(peau.replaceFirst("^(slim|wide):", ""));
        }
        String seuls = System.getenv("REBORN_TIRAGE_CAS");
        for (Cas c : List.of(
                new Cas("katon", "KATON", null, false, false),
                new Cas("suiton", "SUITON", null, false, false),
                new Cas("doton", "DOTON", null, false, false),
                new Cas("raiton", "RAITON", null, false, false),
                new Cas("futon", "FUTON", null, false, false),
                new Cas("double", "KATON", "SUITON", false, false),
                new Cas("renoncer", "RAITON", null, true, false),
                new Cas("mur", "FUTON", null, false, true))) {
            if (seuls == null || seuls.contains(c.nom())) CAS.add(c);
        }
        ClientTickEvents.END_CLIENT_TICK.register(TirageDebug::tick);
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if ("bug".equals(mode)) { bug(mc); return; }
        if ("anim".equals(mode)) { anim(mc); return; }
        if (t == 20) {
            fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            cmd(mc, "gamerule send_command_feedback false");
            cmd(mc, "gamerule sendCommandFeedback false");
            cmd(mc, "time set 1000");
            cmd(mc, "weather clear");
            cmd(mc, "item replace entity @s weapon.mainhand with minecraft:paper");
            mc.player.setXRot(8f);
            RebornCamera.INSTANCE.initOrientation(mc.player.getYRot(), 12f);
        }
        if (t == 50) mc.gui.hud.getChat().clearMessages(false);
        if (t == 60) mc.setScreenAndShow(TirageScreen.confirm("Uchiha"));
        if (t == 75) shot(mc, "tirage_01_confirmer");
        if (t == 80) mc.setScreenAndShow(null);
        if (t == 100) {
            TirageHorloge.imposer(1_000_000L);
            local = 0;
        }
        if (local < 0) return;
        if (idx >= CAS.size()) {
            if (fin < 0) {
                fin = t;
                TirageHorloge.liberer();
            }
            if (t == fin + 20) mc.stop();
            return;
        }
        Cas c = CAS.get(idx);
        TirageScreen ts = mc.gui.screen() instanceof TirageScreen s ? s : null;
        if (local == 0) {
            image = 0;
            if (c.mur()) murDevant(mc, true);
        }
        if (local == 4) {
            yawDepart = mc.player.getYRot();
            TirageScreen s = new TirageScreen(c.nature(), c.autres() == null ? null : "|" + c.autres());
            mc.setScreenAndShow(s);
            ouvert = TirageHorloge.now();
        }
        // une capture par image réellement rendue (quand une capture ralentit le jeu, plusieurs ticks passent pour une
        // image : on ne capture ni n'avance l'horloge deux fois sur la même image)
        if (local == 1 || (local > 1 && TirageHorloge.images != dernieresImages)) {
            dernieresImages = TirageHorloge.images;
            shot(mc, String.format(Locale.ROOT, "tfilm_%s_%03d", c.nom(), image++));
            TirageHorloge.avancer(50);
        }
        if (ts != null) {
            System.out.println(String.format(Locale.ROOT, "[tirage-banc] %s t=%d img=%d phase=%s lacet=%.2f (départ %.2f) camYaw=%.1f mode=%s",
                c.nom(), local, image - 1, ts.phaseNom(), mc.player.getYRot(), yawDepart, RebornCamera.INSTANCE.camYaw(),
                RebornCamera.INSTANCE.mode()));
            // événements à l'horloge de l'écran (pas aux ticks, qui s'accumulent quand une capture ralentit le jeu)
            long e = TirageHorloge.now() - ouvert;
            if (e >= 1000) ts.debugTenir = !c.annuler() || e < 2000;
            if (c.annuler() && e >= 2400) ts.onClose();
            if (ts.phase() == TirageScreen.Phase.RESULT && revel < 0) revel = TirageHorloge.now();   // révélation
            if (revel >= 0 && TirageHorloge.now() >= revel + 1900) ts.onClose();                   // « Valider »
        }
        local++;
        boolean fini = ts == null && !CameraTirage.INSTANCE.active() && local > 10;
        if (fini) {
            System.out.println(String.format(Locale.ROOT, "[tirage-banc] %s fini : lacet=%.2f (départ %.2f) camYaw=%.1f mode=%s",
                c.nom(), mc.player.getYRot(), yawDepart, RebornCamera.INSTANCE.camYaw(), RebornCamera.INSTANCE.mode()));
            if (c.mur()) murDevant(mc, false);
            idx++;
            local = 0;
            revel = -1;
        }
    }

    /** Une colonne de pierre devant et à droite du personnage (là où se poserait le plan), puis retirée. */
    private static void murDevant(Minecraft mc, boolean poser) {
        if (poser) {
            double r = Math.toRadians(mc.player.getYRot());
            double fx = -Math.sin(r), fz = Math.cos(r), dx = -Math.cos(r), dz = -Math.sin(r);
            mx = (int) Math.floor(mc.player.getX() + fx * 1.9 + dx * 1.15);
            my = (int) Math.floor(mc.player.getY() + 0.01);
            mz = (int) Math.floor(mc.player.getZ() + fz * 1.9 + dz * 1.15);
        }
        // une colonne de 3 blocs là où se poserait le plan par défaut ; posée seulement dans l'air, retirée seule
        cmd(mc, "fill " + mx + " " + my + " " + mz + " " + mx + " " + (my + 2) + " " + mz
            + (poser ? " minecraft:stone_bricks replace minecraft:air" : " minecraft:air replace minecraft:stone_bricks"));
    }

    /** {@code =anim} : essais de pose, {@code run/anim_essai_<k>.json} joués tour à tour sous le plan du test. */
    private static void anim(Minecraft mc) {
        if (t == 20) {
            fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            cmd(mc, "time set 1000");
            cmd(mc, "item replace entity @s weapon.mainhand with minecraft:paper");
        }
        if (t == 40) mc.setScreenAndShow(new TirageScreen("KATON", null));
        if (t < 60) return;
        int k = (t - 60) / 40, r = (t - 60) % 40;
        java.nio.file.Path f = mc.gameDirectory.toPath().resolve("anim_essai_" + k + ".json");
        if (!java.nio.file.Files.exists(f)) { mc.stop(); return; }
        if (r == 0) TirageAnimations.INSTANCE.debugJouer(mc.player, f);
        if (r == 30) shot(mc, "tanim_" + k);
    }

    private static void bug(Minecraft mc) {
        if (t == 20) {
            fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        }
        if (t == 60) mc.setScreenAndShow(TirageScreen.banc("KATON", null, 0));
        if (t >= 60 && t <= 100) {
            System.out.println(String.format(Locale.ROOT, "[tirage-bug] t=%d joueurYaw=%.1f camYaw=%.1f mode=%s",
                t, mc.player.getYRot(), RebornCamera.INSTANCE.camYaw(), RebornCamera.INSTANCE.mode()));
        }
        if (t == 110) mc.stop();
    }
}
