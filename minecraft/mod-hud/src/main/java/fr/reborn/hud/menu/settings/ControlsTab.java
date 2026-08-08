package fr.reborn.hud.menu.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Options;

/**
 * Onglet Contrôles — réglages souris/déplacement câblés sur {@code mc.options}.
 * L'ancienne table de touches (affichage figé, aucun rebind) a été retirée ;
 * le rebind complet des commandes vit dans l'onglet Minecraft (redirection
 * vanilla unique).
 */
public class ControlsTab extends SectionedTab {

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

        spacer(4);
    }
}
