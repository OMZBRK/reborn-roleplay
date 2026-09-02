package fr.reborn.hud.menu.settings;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.widget.KeybindButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * Onglet Contrôles — réglages souris/déplacement câblés sur {@code mc.options}
 * <b>et</b> les commandes Reborn (re-bindables inline via {@link KeybindButton}).
 * Les binds Reborn vivent aussi dans l'écran vanilla « Commandes » sous la
 * catégorie « Reborn » (cf. {@code RebornKeyCategory}) ; on les remonte ici pour
 * qu'ils soient accessibles sans quitter les paramètres Reborn.
 */
public class ControlsTab extends SectionedTab {

    /** Largeur du bouton de touche, borné pour rester lisible sur colonne étroite. */
    private static final int KEY_BTN_W = 150;

    @SuppressWarnings("unused")
    private final Screen parent;

    public ControlsTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void build() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        section("Souris");

        // Sensibilité — vanilla stocke 0..1 où 1 = "HYPERSPEED" (×2).
        int sensPct = (int) Math.round(o.sensitivity().get() * 200);
        row("Sensibilité", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                sensPct, 0, 200, "%",
                v -> { o.sensitivity().set(v / 200.0); o.save(); }));

        row("Inverser l'axe Y", "Haut/bas de la souris inversés",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.invertMouseY().get(),
                v -> { o.invertMouseY().set(v); o.save(); }));

        section("Déplacement");

        row("Saut automatique", "Grimpe les blocs sans appuyer sur Saut",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.autoJump().get(),
                v -> { o.autoJump().set(v); o.save(); }));

        row("Sprint (bascule)", "Maintenir plutôt que basculer si désactivé",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.toggleSprint().get(),
                v -> { o.toggleSprint().set(v); o.save(); }));

        row("Accroupi (bascule)", null,
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.toggleCrouch().get(),
                v -> { o.toggleCrouch().set(v); o.save(); }));

        section("Commandes Reborn");

        // Chaque bind Reborn : label à gauche, bouton de re-bind à droite.
        // Clic sur le bouton → écoute la prochaine touche (Échap = dé-bind).
        for (KeyMapping km : HudKeybinds.REBORN_KEYS) {
            String label = Component.translatable(km.getName()).getString();
            int btnW = Math.min(KEY_BTN_W, controlW());
            row(label, null, (cx, cy, cw) ->
                new KeybindButton(cx + cw - btnW, cy, btnW, 24, km));
        }

        spacer(4);
    }
}
