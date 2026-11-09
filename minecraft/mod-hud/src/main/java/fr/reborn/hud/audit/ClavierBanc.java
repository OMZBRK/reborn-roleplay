package fr.reborn.hud.audit;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.keybind.HudKeybinds;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Bancs du lot 4 : l'état physique des touches, simulé. Seule la lecture GLFW de {@link ModeConcentration} est remplacée
 * ({@link ModeConcentration.Clavier}) : la liaison, le maintien, les refus et les sorties suivent le vrai chemin. Sert aussi
 * parce que la fenêtre de dev est souvent à l'arrière-plan (focus vrai ici).
 */
public final class ClavierBanc implements ModeConcentration.Clavier {

    public static final ClavierBanc I = new ClavierBanc();

    private final boolean[] touches = new boolean[512], boutons = new boolean[16];
    private boolean focus = true;

    private ClavierBanc() {}

    /** Pose le clavier simulé, rien de tenu, fenêtre active. */
    public static void poser() {
        I.tout(false);
        I.focus = true;
        ModeConcentration.clavier = I;
    }

    public static void retirer() {
        ModeConcentration.clavier = ModeConcentration.CLAVIER_REEL;
    }

    /** La touche qui ouvre réellement la Concentration, tenue ou relâchée. */
    public static void tenir(boolean oui) {
        if (ModeConcentration.clavier != I) poser();
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(HudKeybinds.concentrationEffective());
        if (k.getType() == InputConstants.Type.MOUSE) I.bouton(k.getValue(), oui);
        else I.touche(k.getValue(), oui);
    }

    public void touche(int glfw, boolean oui) {
        if (glfw >= 0 && glfw < touches.length) touches[glfw] = oui;
    }

    public void bouton(int glfw, boolean oui) {
        if (glfw >= 0 && glfw < boutons.length) boutons[glfw] = oui;
    }

    public void focus(boolean oui) { focus = oui; }

    /** Tout tenu (une touche non liée ne doit rien ouvrir pour autant) ou tout relâché. */
    public void tout(boolean oui) {
        java.util.Arrays.fill(touches, oui);
        java.util.Arrays.fill(boutons, oui);
    }

    @Override public boolean touche(int glfw) { return glfw >= 0 && glfw < touches.length && touches[glfw]; }

    @Override public boolean bouton(int glfw) { return glfw >= 0 && glfw < boutons.length && boutons[glfw]; }

    @Override public boolean fenetreActive() { return focus; }
}
