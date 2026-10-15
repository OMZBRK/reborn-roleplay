package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Migration ponctuelle des touches (carte réduite du 15/10, docs/technique/MOD_HUD.md §2). Les joueurs existants ont
 * un {@code options.txt} avec les anciens défauts : une seule fois (drapeau {@link RebornPrefs#touchesVersion}), chaque
 * touche dont la valeur sauvegardée est <b>encore l'ancien défaut</b> reçoit le nouveau ; une touche personnalisée par
 * le joueur n'est jamais touchée. Les touches Reborn supprimées disparaissent simplement (Minecraft ignore les lignes
 * inconnues d'{@code options.txt}).
 *
 * <ul>
 *   <li>Menu OST (mod-ost) : M → N (N est libérée par la radio, M = carte) ;</li>
 *   <li>Dash : V → G (V = réglages de Plasmo Voice, G est libérée par la galerie) ;</li>
 *   <li>Emotecraft « fastchoose » : B → aucune (B = radio ; les émotes sont dans le menu « . ») ;</li>
 *   <li>Minecraft « Actions rapides » : G → aucune, « Interactions sociales » : P → aucune (conflits avec le dash et le
 *       mode photo ; Reborn n'utilise ni l'un ni l'autre) ;</li>
 *   <li>Vue : la touche vanilla « Changer de vue » (F5) pilote la caméra Reborn (cf. {@link HudKeybinds#vueReborn}),
 *       rien à déplacer.</li>
 * </ul>
 * Le raccourci « couper le micro » de Plasmo Voice (M, conflit avec la carte) est migré à part, quand Plasmo Voice est
 * prêt ({@code voice/RadioVoix}, drapeau {@link RebornPrefs#touchesPv}).
 *
 * <p><b>Version 2 — disposition du clavier</b> (15/10) : les défauts de Minecraft sont des <i>positions</i> de touche
 * (noms US) ; en AZERTY la touche « M » de la carte s'affichait « , », « . » s'affichait « : ». Pour chaque touche Reborn
 * (et le menu OST) restée sur son défaut, {@link #cible} cherche la touche qui <b>affiche</b> la même lettre sur le
 * clavier du joueur ({@code glfwGetKeyName}) et l'y place si elle est libre. AZERTY : Carte → « m » (US « ; »),
 * Animations → la touche gravée « ; . » (US « , », affichée « ; », « . » n'existant qu'avec Maj), Dégainer reste sur la
 * même position (affichée « W ») car « z » est Avancer (ZQSD). QWERTZ : Dégainer → « z » (US « Y »). QWERTY : rien. Les
 * touches non imprimables (Ctrl, Maj, Alt, F1…, Entrée) ne dépendent pas de la disposition.
 */
public final class TouchesMigration {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/touches");

    /** Version de la carte des touches ; on la monte si une nouvelle migration s'ajoute. */
    public static final int VERSION = 2;

    private TouchesMigration() {}

    static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(TouchesMigration::migrer);
    }

    /** Une touche à migrer : nom de la {@link KeyMapping}, ancien défaut, nouveau ({@code -1} = aucune). */
    private record Regle(String nom, int ancien, int nouveau) { }

    /** Conflits vanilla d'abord (libèrent G et P), puis les déplacements. */
    private static final Regle[] REGLES = {
        new Regle("key.quickActions", GLFW.GLFW_KEY_G, -1),
        new Regle("key.socialInteractions", GLFW.GLFW_KEY_P, -1),
        new Regle("key.emotecraft.fastchoose", GLFW.GLFW_KEY_B, -1),
        new Regle("key.reborn-ost.open_menu", GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_N),
        new Regle("key.reborn-hud.dash", GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_G),
    };

    static void migrer(Minecraft mc) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        prefs.ensureLoaded();
        if (prefs.touchesVersion >= VERSION || mc.options == null) return;
        int changees = 0;
        if (prefs.touchesVersion < 1) changees += anciensDefauts();
        changees += clavier(mc, k -> GLFW.glfwGetKeyName(k, 0));
        if (changees > 0) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
        prefs.touchesVersion = VERSION;
        prefs.save();
    }

    /** Version 1 : anciens défauts → nouvelle carte. */
    private static int anciensDefauts() {
        int changees = 0;
        for (Regle r : REGLES) {
            KeyMapping km = KeyMapping.get(r.nom());
            if (km == null) continue;                                  // mod absent : rien à faire
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
            if (k.getType() != InputConstants.Type.KEYSYM || k.getValue() != r.ancien()) continue;   // personnalisée
            InputConstants.Key cible = r.nouveau() < 0 ? InputConstants.UNKNOWN
                : InputConstants.Type.KEYSYM.getOrCreate(r.nouveau());
            if (r.nouveau() >= 0 && occupee(cible, km)) {
                LOG.info("touche {} : {} déjà prise ailleurs, laissée sur {}", r.nom(), cible.getName(), k.getName());
                continue;
            }
            km.setKey(cible);
            changees++;
            LOG.info("touche {} : {} -> {}", r.nom(), k.getName(), r.nouveau() < 0 ? "aucune" : cible.getName());
        }
        return changees;
    }

    /** Version 2 : les touches Reborn restées sur leur défaut passent sur la touche qui affiche la même lettre. */
    static int clavier(Minecraft mc, IntFunction<String> nom) {
        LOG.info("clavier : Q affiche « {} », « ; » affiche « {} », Y affiche « {} »",
            nom.apply(GLFW.GLFW_KEY_Q), nom.apply(GLFW.GLFW_KEY_SEMICOLON), nom.apply(GLFW.GLFW_KEY_Y));
        List<KeyMapping> cles = new ArrayList<>(HudKeybinds.REBORN_KEYS);
        cles.add(KeyMapping.get("key.reborn-ost.open_menu"));
        int changees = 0;
        for (KeyMapping km : cles) {
            if (km == null || !km.isDefault()) continue;                 // personnalisée : jamais touchée
            InputConstants.Key def = km.getDefaultKey();
            if (def.getType() != InputConstants.Type.KEYSYM) continue;
            Integer c = cible(def.getValue(), nom);
            if (c == null) continue;
            InputConstants.Key t = InputConstants.Type.KEYSYM.getOrCreate(c);
            if (occupee(t, km)) {
                LOG.info("clavier : {} reste sur {} ({} est déjà prise)", km.getName(), def.getName(), t.getName());
                continue;
            }
            km.setKey(t);
            changees++;
            LOG.info("clavier : {} {} -> {} (affiche « {} »)", km.getName(), def.getName(), t.getName(), nom.apply(c));
        }
        return changees;
    }

    /** Touches imprimables de GLFW (noms US) : espace, ponctuation, chiffres, lettres, « world 1/2 ». */
    private static final int[] IMPRIMABLES;
    static {
        List<Integer> l = new ArrayList<>();
        for (int k : new int[]{32, 39, 59, 61, 91, 92, 93, 96, 161, 162}) l.add(k);
        for (int k = 44; k <= 57; k++) l.add(k);
        for (int k = 65; k <= 90; k++) l.add(k);
        IMPRIMABLES = l.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Ce qu'affiche une touche imprimable sur un clavier QWERTY US (la lettre « voulue » d'un défaut). */
    static String nomUs(int k) {
        if (k >= 65 && k <= 90) return String.valueOf((char) ('a' + k - 65));
        if (k >= 48 && k <= 57) return String.valueOf((char) k);
        return switch (k) {
            case 39 -> "'"; case 44 -> ","; case 45 -> "-"; case 46 -> "."; case 47 -> "/"; case 59 -> ";";
            case 61 -> "="; case 91 -> "["; case 92 -> "\\"; case 93 -> "]"; case 96 -> "`";
            default -> null;
        };
    }

    /**
     * Touche à utiliser pour un défaut {@code defaut} (nom US) sur le clavier décrit par {@code nom} (ce qu'affiche
     * chaque touche), ou {@code null} s'il n'y a rien à changer. Pure : testée par le banc sur des dispositions simulées.
     */
    public static Integer cible(int defaut, IntFunction<String> nom) {
        String voulu = nomUs(defaut);
        if (voulu == null) return null;                                  // non imprimable : indépendant du clavier
        if (voulu.equalsIgnoreCase(nom.apply(defaut))) return null;      // affiche déjà la bonne lettre
        for (int k : IMPRIMABLES) if (voulu.equalsIgnoreCase(nom.apply(k))) return k;
        // AZERTY : « . » n'existe qu'avec Maj, sur la touche gravée « ; . » (US « , ») → on prend celle-là
        if (".".equals(voulu) && "a".equalsIgnoreCase(nom.apply(GLFW.GLFW_KEY_Q))) {
            for (int k : IMPRIMABLES) if (";".equals(nom.apply(k))) return k;
        }
        return null;
    }

    /**
     * Une autre touche (hors {@code sauf}) est-elle déjà liée à {@code k} ? Les raccourcis de débogage (F3 + touche,
     * catégorie DEBUG : F3+G, F3+N…) ne comptent pas, ils ne se déclenchent qu'avec F3.
     */
    private static boolean occupee(InputConstants.Key k, KeyMapping sauf) {
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (km == sauf || KeyMapping.Category.DEBUG.equals(km.getCategory())) continue;
            if (KeyMappingHelper.getBoundKeyOf(km).equals(k)) return true;
        }
        return false;
    }
}
