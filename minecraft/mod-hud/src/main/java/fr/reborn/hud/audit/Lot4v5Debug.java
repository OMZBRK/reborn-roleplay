package fr.reborn.hud.audit;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.keybind.TouchesConflits;
import fr.reborn.hud.keybind.TouchesMigration;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.screens.ConfigShellScreen.Onglet;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.widget.KeybindButton;
import fr.reborn.hud.nourriture.NourritureHud;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banc du <b>retrait de R</b> (migration 5 des touches, lot 4 « Concentration », CADRAGE §5.4), <b>inactif en
 * production</b> : {@code REBORN_LOT4_DEBUG=v5} (monde solo de dev, jamais de serveur).
 * <ol>
 *   <li>Migration réelle du démarrage : bilan de la touche Interaction et du drapeau {@code touchesVersion} relus (le
 *       banc se lance de préférence avec {@code options.txt} sur R et {@code touchesVersion} à 4).</li>
 *   <li>Règle pure {@link TouchesMigration#retraitR} : client neuf, R au défaut, R personnalisée, Concentration sans
 *       touche, R déjà déliée.</li>
 *   <li>Migration rejouée sur les vraies liaisons ({@link TouchesMigration#debugMigrer}, drapeau remis à 4) : options
 *       neuves (R déliée, drapeau à 5 et écrit sur le disque), idempotence (drapeau à 5 : rien ne bouge), R
 *       personnalisée (gardée), Concentration sans touche (R gardée, elle ouvre la Concentration) ; « Réinitialiser la
 *       section » (le défaut R ne revient pas) ; BÉPO simulé (Interaction posée sur US L, qui affiche « r »).</li>
 *   <li>Captures : Paramètres › Commandes › Monde sans la ligne Interaction, avec une touche Interaction personnalisée,
 *       avec Concentration sans touche ; parcours d'un vrai joueur dans le même écran (il lie Concentration, puis Échap
 *       sur Interaction : aide puis ligne à jour sans redémarrage) ; invite « Tendre » et rappel « Concentration » sur
 *       la touche réelle.</li>
 * </ol>
 * → {@code run/screenshots/lot4v5_*.png}, « [lot4v5] OK / ECHEC » ; liaisons, drapeau et {@code options.txt} remis
 * comme au départ, puis le jeu se ferme (« [lot4v5] FIN »).
 */
public final class Lot4v5Debug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    private static InputConstants.Key interactionAvant = InputConstants.UNKNOWN, concentrationAvant = InputConstants.UNKNOWN;
    private static int versionAvant;
    private static String mode = "survival";

    private static final InputConstants.Key R = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R),
        Y = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y),
        T = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T),
        L = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L),
        AUCUNE = InputConstants.UNKNOWN;

    private Lot4v5Debug() {}

    public static void init() {
        if (!"v5".equals(System.getenv("REBORN_LOT4_DEBUG"))) return;

        pas(5, mc -> {
            RebornSession.debugForceRp = true;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var c = mc.player.connection;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
            interactionAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.INTERACTION);
            concentrationAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.CONCENTRATION);
            versionAvant = RebornPrefs.INSTANCE.touchesVersion;
            System.out.println("[lot4v5] au démarrage : touchesVersion=" + versionAvant + " · Interaction="
                + interactionAvant.getName() + " · Concentration=" + concentrationAvant.getName());
            verifie(versionAvant == TouchesMigration.VERSION && TouchesMigration.VERSION == 5,
                "démarrage : drapeau à la version 5 (" + versionAvant + ")");
            verifie(concentrationAvant.equals(AUCUNE) || !interactionAvant.equals(R),
                "démarrage : Interaction plus sur R quand Concentration a une touche (" + interactionAvant.getName() + ")");
            verifie(disque().contains("\"touchesVersion\": 5"), "démarrage : drapeau 5 écrit dans reborn_prefs.json");
        }, "préparation");

        // ───────── règle pure
        pas(2, mc -> {
            int r = GLFW.GLFW_KEY_R, y = GLFW.GLFW_KEY_Y, tt = GLFW.GLFW_KEY_T;
            verifie(TouchesMigration.retraitR(r, r, y), "règle : options neuves (R au défaut, Concentration sur Y) → R déliée");
            verifie(!TouchesMigration.retraitR(tt, r, y), "règle : Interaction personnalisée (T) → gardée");
            verifie(!TouchesMigration.retraitR(r, r, -1), "règle : Concentration sans touche → R gardée");
            verifie(!TouchesMigration.retraitR(-1, r, y), "règle : Interaction déjà déliée → rien");
            verifie(!TouchesMigration.retraitR(-1001, r, y), "règle : Interaction sur un bouton de souris → gardée");
            verifie(TouchesMigration.retraitR(r, r, r), "règle : Concentration mise sur R → l'ancienne R déliée (pas de double bascule)");
            // claviers où la touche US R n'affiche pas « r » : la version 2 a pu poser Interaction sur celle qui l'affiche
            int l = GLFW.GLFW_KEY_L, o = GLFW.GLFW_KEY_O, s = GLFW.GLFW_KEY_S;
            verifie(TouchesMigration.retraitR(l, r, y, BEPO), "règle BÉPO : Interaction sur US L (affiche « r ») = défaut → déliée");
            verifie(TouchesMigration.retraitR(r, r, y, BEPO), "règle BÉPO : Interaction restée sur US R → déliée");
            verifie(!TouchesMigration.retraitR(tt, r, y, BEPO), "règle BÉPO : Interaction personnalisée (T) → gardée");
            verifie(!TouchesMigration.retraitR(l, r, -1, BEPO), "règle BÉPO : Concentration sans touche → gardée");
            verifie(TouchesMigration.retraitR(o, r, y, DVORAK), "règle Dvorak : Interaction sur US O (affiche « r ») → déliée");
            verifie(TouchesMigration.retraitR(s, r, y, COLEMAK), "règle Colemak : Interaction sur US S (affiche « r ») → déliée");
            verifie(!TouchesMigration.retraitR(l, r, y, k -> us(k)), "règle QWERTY : Interaction sur L = personnalisée → gardée");
        }, "règle pure");

        // ───────── migration rejouée sur les vraies liaisons
        pas(2, mc -> {
            lier(R, Y);
            RebornPrefs.INSTANCE.touchesVersion = 4;
            TouchesMigration.debugMigrer(mc);
            verifie(HudKeybinds.INTERACTION.isUnbound(), "options neuves : R déliée (" + nom(HudKeybinds.INTERACTION) + ")");
            verifie(RebornPrefs.INSTANCE.touchesVersion == 5 && disque().contains("\"touchesVersion\": 5"),
                "options neuves : drapeau à 5, écrit sur le disque");
            verifie(HudKeybinds.concentrationEffective() == HudKeybinds.CONCENTRATION, "options neuves : invites sur Concentration");
        }, "migration : options neuves");
        pas(2, mc -> {
            lier(R, Y);
            TouchesMigration.debugMigrer(mc);
            verifie(R.equals(KeyMappingHelper.getBoundKeyOf(HudKeybinds.INTERACTION)),
                "idempotente : drapeau déjà à 5, R remise par le joueur reste sur R");
        }, "migration : idempotence");
        pas(2, mc -> {
            lier(T, Y);
            RebornPrefs.INSTANCE.touchesVersion = 4;
            TouchesMigration.debugMigrer(mc);
            verifie(T.equals(KeyMappingHelper.getBoundKeyOf(HudKeybinds.INTERACTION)),
                "Interaction personnalisée (T) : gardée (" + nom(HudKeybinds.INTERACTION) + ")");
        }, "migration : Interaction personnalisée");
        pas(2, mc -> {
            lier(R, AUCUNE);
            RebornPrefs.INSTANCE.touchesVersion = 4;
            TouchesMigration.debugMigrer(mc);
            verifie(R.equals(KeyMappingHelper.getBoundKeyOf(HudKeybinds.INTERACTION)),
                "Concentration sans touche : R gardée (" + nom(HudKeybinds.INTERACTION) + ")");
            verifie(HudKeybinds.concentrationEffective() == HudKeybinds.INTERACTION,
                "Concentration sans touche : R ouvre la Concentration (invites sur R)");
        }, "migration : Concentration sans touche");
        pas(2, mc -> {
            lier(L, Y);
            RebornPrefs.INSTANCE.touchesVersion = 4;
            TouchesMigration.debugMigrer(mc, BEPO);
            verifie(HudKeybinds.INTERACTION.isUnbound(),
                "BÉPO : Interaction posée sur US L (« r ») par la version 2 → déliée (" + nom(HudKeybinds.INTERACTION) + ")");
        }, "migration : BÉPO");
        pas(2, mc -> {
            lier(T, Y);
            TouchesMigration.debugReinitialiser(List.of(HudKeybinds.CONCENTRATION, HudKeybinds.INTERACTION), BEPO);
            verifie(HudKeybinds.INTERACTION.isUnbound() && !HudKeybinds.CONCENTRATION.isUnbound(),
                "Réinitialiser la section en BÉPO : Interaction déliée, pas reposée sur US L (" + nom(HudKeybinds.INTERACTION)
                    + ", " + nom(HudKeybinds.CONCENTRATION) + ")");
        }, "réinitialiser la section : BÉPO");
        pas(2, mc -> {
            lier(T, Y);
            TouchesMigration.reinitialiser(List.of(HudKeybinds.CONCENTRATION, HudKeybinds.INTERACTION));
            verifie(HudKeybinds.INTERACTION.isUnbound() && !HudKeybinds.CONCENTRATION.isUnbound(),
                "Réinitialiser la section : Concentration à son défaut, Interaction déliée (" + nom(HudKeybinds.INTERACTION)
                    + ", " + nom(HudKeybinds.CONCENTRATION) + ")");
        }, "réinitialiser la section");

        // ───────── captures : Commandes › Monde
        commandes(AUCUNE, Y, "lot4v5_01_commandes_monde");
        commandes(T, Y, "lot4v5_02_commandes_interaction_perso");
        commandes(R, AUCUNE, "lot4v5_03_commandes_concentration_sans_touche");

        // ───────── parcours d'un vrai joueur dans le même écran (aucun invaliderCache du banc) : il lie Concentration
        // sur Y depuis sa case, l'aide d'Interaction change ; puis Échap sur la case Interaction, la ligne part
        pas(3, mc -> {
            lier(R, AUCUNE);
            mc.setScreenAndShow(new ConfigShellScreen(null, Onglet.COMMANDES, 2));
        }, "Commandes › Monde, Concentration sans touche");
        pas(10, mc -> {
            verifie(appuyer(mc, HudKeybinds.CONCENTRATION, GLFW.GLFW_KEY_Y), "case Concentration trouvée");
            verifie(Y.equals(KeyMappingHelper.getBoundKeyOf(HudKeybinds.CONCENTRATION)), "Concentration liée sur Y par sa case");
        }, "le joueur lie Concentration sur Y");
        pas(10, mc -> {
            verifie(boutons(mc, HudKeybinds.INTERACTION) == 1, "ligne Interaction toujours là (R gardée)");
            shot(mc, "lot4v5_06_concentration_liee_dans_l_ecran");
        }, "capture : aide d'Interaction à jour");
        pas(4, mc -> verifie(appuyer(mc, HudKeybinds.INTERACTION, GLFW.GLFW_KEY_ESCAPE), "case Interaction trouvée"),
            "le joueur retire Interaction (Échap)");
        pas(10, mc -> {
            verifie(HudKeybinds.INTERACTION.isUnbound(), "Interaction déliée par Échap");
            verifie(boutons(mc, HudKeybinds.INTERACTION) == 0, "ligne Interaction partie sans redémarrage");
            shot(mc, "lot4v5_07_interaction_retiree_dans_l_ecran");
        }, "capture : ligne Interaction partie");

        // ───────── captures : invite « Tendre » et rappel « Concentration » sur la touche réelle
        pas(2, mc -> {
            lier(AUCUNE, Y);
            mc.setScreenAndShow(null);
            var c = mc.player.connection;
            c.sendCommand("item replace entity @s hotbar.0 with minecraft:mushroom_stew[custom_name=\"Ramen shōyu\"] 1");
            mc.player.getInventory().setSelectedSlot(0);
            var fw = mc.gameRenderer.mainCamera().forwardVector();
            double n = Math.max(0.01, Math.hypot(fw.x(), fw.z()));
            double dx = fw.x() / n, dz = fw.z() / n;
            double x = mc.player.getX() + dx * 2.2 - dz * 0.8, z = mc.player.getZ() + dz * 2.2 + dx * 0.8;
            float lacet = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
            c.sendCommand(String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {NoGravity:1b,Rotation:[%.1ff,0f]}", x, mc.player.getY(), z, lacet));
        }, "plat en main, mannequin");
        pas(30, mc -> NourritureHud.debugCible = mannequin(mc), "cible de l'invite");
        pas(10, mc -> {
            verifie(NourritureHud.debugCible != null, "mannequin trouvé pour l'invite");
            System.out.println("[lot4v5] rappels " + RappelsTouches.bilan());
            shot(mc, "lot4v5_04_invite_tendre_touche_y");
        }, "capture invite (Y)");
        pas(2, mc -> lier(R, AUCUNE), "Concentration sans touche, R gardée");
        pas(10, mc -> {
            System.out.println("[lot4v5] rappels (R gardée) " + RappelsTouches.bilan());
            shot(mc, "lot4v5_05_invite_tendre_r_gardee");
        }, "capture invite (R gardée)");

        // ───────── remise en état
        pas(4, mc -> {
            NourritureHud.debugCible = null;
            HudKeybinds.INTERACTION.setKey(interactionAvant);
            HudKeybinds.CONCENTRATION.setKey(concentrationAvant);
            KeyMapping.resetMapping();
            TouchesConflits.invalider();
            ControlsTab.invaliderCache();
            mc.options.save();
            RebornPrefs.INSTANCE.touchesVersion = versionAvant;
            RebornPrefs.INSTANCE.save();
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            c.sendCommand("item replace entity @s hotbar.0 with minecraft:air");
            c.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> {
            System.out.println("[lot4v5] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4v5Debug::tick);
    }

    /** Clavier simulé (ce qu'affiche chaque touche, noms GLFW US) : BÉPO, Dvorak, Colemak, réduits aux touches utiles. */
    private static final java.util.function.IntFunction<String> BEPO = k ->
        k == GLFW.GLFW_KEY_R ? "o" : k == GLFW.GLFW_KEY_L ? "r" : k == GLFW.GLFW_KEY_O ? "l" : us(k);
    private static final java.util.function.IntFunction<String> DVORAK = k ->
        k == GLFW.GLFW_KEY_R ? "p" : k == GLFW.GLFW_KEY_O ? "r" : k == GLFW.GLFW_KEY_P ? "l" : us(k);
    private static final java.util.function.IntFunction<String> COLEMAK = k ->
        k == GLFW.GLFW_KEY_R ? "p" : k == GLFW.GLFW_KEY_S ? "r" : k == GLFW.GLFW_KEY_P ? ";" : us(k);

    /** Ce qu'affiche une lettre sur un QWERTY US. */
    private static String us(int k) {
        return k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z ? String.valueOf((char) ('a' + k - GLFW.GLFW_KEY_A)) : null;
    }

    /** Cases de touche de l'écran ouvert pour cette liaison. */
    private static List<KeybindButton> casesDe(Minecraft mc, KeyMapping km) {
        List<KeybindButton> l = new ArrayList<>();
        var ecran = mc.gui.screen();
        if (ecran == null) return l;
        for (var w : ecran.children()) if (w instanceof KeybindButton b && b.mapping() == km) l.add(b);
        return l;
    }

    private static int boutons(Minecraft mc, KeyMapping km) {
        return casesDe(mc, km).size();
    }

    /** Clic sur la case puis la touche, comme un joueur (KeybindButton.assign). */
    private static boolean appuyer(Minecraft mc, KeyMapping km, int glfw) {
        List<KeybindButton> l = casesDe(mc, km);
        if (l.isEmpty()) return false;
        KeybindButton b = l.get(0);
        b.onPress(null);
        b.keyPressed(new KeyEvent(glfw, GLFW.glfwGetKeyScancode(glfw), 0));
        return true;
    }

    private static void commandes(InputConstants.Key interaction, InputConstants.Key concentration, String nom) {
        pas(3, mc -> {
            lier(interaction, concentration);
            mc.setScreenAndShow(new ConfigShellScreen(null, Onglet.COMMANDES, 2));
        }, "Commandes › Monde (" + interaction.getName() + ", " + concentration.getName() + ")");
        pas(14, mc -> shot(mc, nom), "capture " + nom);
    }

    /** Lie Interaction et Concentration (sans enregistrer), conflits et listes des Paramètres refaits. */
    private static void lier(InputConstants.Key interaction, InputConstants.Key concentration) {
        HudKeybinds.INTERACTION.setKey(interaction);
        HudKeybinds.CONCENTRATION.setKey(concentration);
        KeyMapping.resetMapping();
        TouchesConflits.invalider();
        ControlsTab.invaliderCache();
    }

    private static String nom(KeyMapping km) {
        return KeyMappingHelper.getBoundKeyOf(km).getName();
    }

    private static String disque() {
        try {
            return java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("reborn_prefs.json"));
        } catch (Exception e) {
            return "";
        }
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

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4v5] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4v5] " + nom);
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
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
