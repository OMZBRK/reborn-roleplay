package fr.reborn.hud.animation;

import fr.reborn.hud.config.RebornPrefs;
import net.minecraft.client.Minecraft;

/**
 * ⚠️ STUB 26.1 (phase 2) — l'intégration PlayerAnimator/Emotecraft est
 * neutralisée le temps du port du cœur UI de mod-hud. L'API publique est
 * conservée (les menus/keybinds compilent) mais inerte : aucune animation
 * n'est appliquée. À ré-implémenter avec player-animation-lib 26.1 +
 * emotecraft 3.3.0 une fois le reste validé.
 */
public final class MovementAnimations {

    public static final MovementAnimations INSTANCE = new MovementAnimations();

    /** Styles de marche : {label, fichier}. Conservés pour le menu. */
    private static final String[][] WALK_STYLES = {
        {"Défaut", "walk_default.emotecraft"},
        {"Tremblante", "walk_trembling.emotecraft"},
        {"Timide", "walk_timid.emotecraft"},
        {"Arrogante", "walk_arrogant.emotecraft"},
        {"Désespérée", "walk_hopeless.emotecraft"},
    };

    private int selectedWalk = 0;
    private boolean narutoTest = false;

    private MovementAnimations() {}

    /** false en phase 2 (feature inerte). */
    public boolean isAvailable() { return false; }

    public int walkStyleCount() { return WALK_STYLES.length; }
    public String walkStyleName(int i) { return WALK_STYLES[i][0]; }
    public int selectedWalk() { return selectedWalk; }

    public void setWalkStyle(int i) {
        selectedWalk = Math.max(0, Math.min(WALK_STYLES.length - 1, i));
        RebornPrefs.INSTANCE.walkStyle = selectedWalk;
        RebornPrefs.INSTANCE.save();
    }

    public void cycleWalkStyle() { setWalkStyle((selectedWalk + 1) % WALK_STYLES.length); }
    public void toggleNarutoTest() { narutoTest = !narutoTest; }
    public boolean narutoTest() { return narutoTest; }

    /** no-op (phase 2). */
    public void register() {}

    /** no-op (phase 2). */
    public void tick(Minecraft mc) {}
}
