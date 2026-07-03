package fr.reborn.hud.menu.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;

        section("Souris");

        // Sensibilité — vanilla stocke 0..1 où 1 = "HYPERSPEED" (×2).
        int sensPct = (int) Math.round(o.getMouseSensitivity().getValue() * 200);
        row("Sensibilité", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                sensPct, 0, 200, "%",
                v -> { o.getMouseSensitivity().setValue(v / 200.0); o.write(); }));

        row("Inverser l'axe Y", "Haut/bas de la souris inversés",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getInvertYMouse().getValue(),
                v -> { o.getInvertYMouse().setValue(v); o.write(); }));

        section("Déplacement");

        row("Saut automatique", "Grimpe les blocs sans appuyer sur Saut",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getAutoJump().getValue(),
                v -> { o.getAutoJump().setValue(v); o.write(); }));

        row("Sprint (bascule)", "Maintenir plutôt que basculer si désactivé",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getSprintToggled().getValue(),
                v -> { o.getSprintToggled().setValue(v); o.write(); }));

        row("Accroupi (bascule)", null,
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getSneakToggled().getValue(),
                v -> { o.getSneakToggled().setValue(v); o.write(); }));

        spacer(4);
    }
}
