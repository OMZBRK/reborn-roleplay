package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nom court d'une touche pour les rappels du bas-droite (lot 2, brief §4.3, maquette c3) : « Clic G », « Clic D »,
 * « Clic M », « Souris 4 », « Maj », « Ctrl », « Alt », « Alt Gr », « Espace », « Entrée », « Retour », « Tab »,
 * « Échap », « Pav 1 », « F7 »… ; une touche imprimable s'affiche telle que le clavier du joueur la nomme (en AZERTY,
 * Animations = « ; », MOD_HUD §2) ; une touche non liée n'a pas de nom (la ligne se masque). Les boutons de la souris
 * et la barre d'espace ont un pictogramme (case de 22 px, LISEZMOI v2).
 *
 * <p>Chaque ligne garde un {@link Nom} : il ne se recalcule que si la liaison change ({@link KeyMapping#saveString()},
 * le nom interne de la touche, est le même objet tant qu'elle ne change pas) — rien n'est alloué à chaque image. Les
 * six copies de {@code touche(KeyMapping)} des invites (duel, faction, radio…) ne sont pas reprises ici (hors lot).
 */
public final class NomTouche {

    /** Contenu de la case : du texte, ou un pictogramme (souris : bouton gauche, droit, molette ; barre d'espace). */
    public static final int TEXTE = 0, SOURIS_G = 1, SOURIS_D = 2, SOURIS_M = 3, ESPACE = 4;

    /** Banc : nom imposé d'une touche ({@code saveString} → nom affiché), ex. « ; » d'un clavier AZERTY. */
    public static final Map<String, String> DEBUG_NOMS = new ConcurrentHashMap<>();
    /** Génération des noms : {@link #invalider()} fait tout recalculer (banc, changement de disposition). */
    private static int generation;

    private NomTouche() {}

    /** Oublie les noms calculés (le banc vient de changer {@link #DEBUG_NOMS}). */
    public static void invalider() { generation++; }

    /** Nom d'une liaison, gardé par la ligne qui l'affiche. */
    public static final class Nom {
        private String vu;
        private int gen = -1;
        private String texte;
        private int picto;

        /** Relit la liaison (sans rien allouer si elle n'a pas changé) ; faux si elle est absente ou non liée. */
        public boolean lire(KeyMapping km) {
            if (km == null || km.isUnbound()) return false;
            String s = km.saveString();
            if (s != vu || gen != generation) {
                vu = s;
                gen = generation;
                calculer(km, this);
            }
            return texte != null;
        }

        /** Nom affiché (« H », « Maj », « Clic M »…). */
        public String texte() { return texte; }

        /** {@link #TEXTE} ou le pictogramme de la case. */
        public int picto() { return picto; }
    }

    private static void calculer(KeyMapping km, Nom n) {
        n.picto = TEXTE;
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
        String impose = DEBUG_NOMS.get(k.getName());
        if (impose != null) {
            n.texte = impose;
            return;
        }
        int v = k.getValue();
        if (k.getType() == InputConstants.Type.MOUSE) {
            switch (v) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> { n.texte = "Clic G"; n.picto = SOURIS_G; }
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> { n.texte = "Clic D"; n.picto = SOURIS_D; }
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> { n.texte = "Clic M"; n.picto = SOURIS_M; }
                default -> n.texte = "Souris " + (v + 1);
            }
            return;
        }
        String special = k.getType() == InputConstants.Type.KEYSYM ? special(v) : null;
        if (special != null) {
            n.texte = special;
            if (v == GLFW.GLFW_KEY_SPACE) n.picto = ESPACE;
            return;
        }
        // imprimable : le nom que le clavier du joueur lui donne (glfwGetKeyName, comme les Commandes de Minecraft)
        String s = km.getTranslatedKeyMessage().getString();
        n.texte = s == null || s.isBlank() ? "?" : s.toUpperCase(Locale.ROOT);
    }

    /** Touches nommées en français court ; null pour une touche imprimable. */
    private static String special(int v) {
        if (v >= GLFW.GLFW_KEY_F1 && v <= GLFW.GLFW_KEY_F25) return "F" + (v - GLFW.GLFW_KEY_F1 + 1);
        if (v >= GLFW.GLFW_KEY_KP_0 && v <= GLFW.GLFW_KEY_KP_9) return "Pav " + (v - GLFW.GLFW_KEY_KP_0);
        return switch (v) {
            case GLFW.GLFW_KEY_SPACE -> "Espace";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Maj";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Alt Gr";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Win";
            case GLFW.GLFW_KEY_ENTER -> "Entrée";
            case GLFW.GLFW_KEY_BACKSPACE -> "Retour";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ESCAPE -> "Échap";
            case GLFW.GLFW_KEY_UP -> "Haut";
            case GLFW.GLFW_KEY_DOWN -> "Bas";
            case GLFW.GLFW_KEY_LEFT -> "Gauche";
            case GLFW.GLFW_KEY_RIGHT -> "Droite";
            case GLFW.GLFW_KEY_INSERT -> "Inser";
            case GLFW.GLFW_KEY_DELETE -> "Suppr";
            case GLFW.GLFW_KEY_HOME -> "Début";
            case GLFW.GLFW_KEY_END -> "Fin";
            case GLFW.GLFW_KEY_PAGE_UP -> "Pg préc";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Pg suiv";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Verr Maj";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Verr Num";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Arrêt défil";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Impr";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_MENU -> "Menu";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Pav ,";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Pav /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Pav *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Pav -";
            case GLFW.GLFW_KEY_KP_ADD -> "Pav +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Pav Entrée";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Pav =";
            default -> null;
        };
    }
}
